# Streaming y analítica con Kafka (EP5 y EP6)

Cómo quedó integrado Kafka en Pedidos360: qué se publica, quién lo consume, por qué los dos
tópicos tienen políticas distintas y cómo se supervisa la plataforma.

RabbitMQ no se tocó. Los dos conviven, y la diferencia entre ambos es la idea central de
esta etapa.

---

## 1. Qué problema resuelve

RabbitMQ ya resolvía el trabajo asíncrono: confirmar un pedido publica un mensaje y el
documento y el aviso se generan aparte. Eso es una **tarea que hay que ejecutar**, y cuando
se ejecuta, se acabó: el mensaje se consume y desaparece de la cola.

Lo que faltaba es otra cosa. Nadie podía responder cuánto se vendió hoy, qué cliente compra
más o qué productos están por quebrar stock, sin ir a consultar la base de datos de pedidos
y la de productos y cruzarlas a mano. Y si mañana aparece un tercer interesado en esos
mismos datos, habría que volver a tocar el microservicio que los produce.

Kafka cambia el planteamiento: el pedido no emite una orden de trabajo, emite un **hecho**.
"El pedido PED-2026-00042 se confirmó por $238.000". El hecho se guarda en un tópico y queda
ahí, disponible para quien quiera leerlo, ahora o la semana que viene. La analítica lo lee
para sus métricas; el archivo lo lee para guardar la historia; mañana un tercer consumidor
puede sumarse sin pedirle permiso a nadie y sin quitarle el mensaje a los demás.

| | RabbitMQ | Kafka |
|---|---|---|
| Qué transporta | una tarea que hay que ejecutar | un hecho que ya ocurrió |
| Al consumirse | el mensaje se va de la cola | el registro sigue en el tópico |
| Quién lo lee | un consumidor | todos los grupos que quieran |
| Sumar un interesado | hay que declarar cola y binding | se agrega un grupo y listo |
| En este proyecto | avisos y documentos | métricas, stock y archivo |

---

## 2. La arquitectura del clúster

```
   docker compose -f infra/kafka/docker-compose.yml up -d

   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
   │     zk1     │  │     zk2     │  │     zk3     │   quorum: 2 de 3
   └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
          └────────────────┼────────────────┘
          ┌────────────────┼────────────────┐
   ┌──────┴──────┐  ┌──────┴──────┐  ┌──────┴──────┐
   │   kafka1    │  │   kafka2    │  │   kafka3    │
   │   :9092     │  │   :9093     │  │   :9094     │
   └─────────────┘  └─────────────┘  └─────────────┘
          réplicas: 3   ·   min.insync.replicas: 2

   docker compose -f infra/kafka-ui/docker-compose.yml up -d   →  :8088
```

**Por qué tres Zookeeper y no dos.** Zookeeper decide por mayoría. Con dos nodos la mayoría
sigue siendo dos: si cae uno, el que queda no puede formar quorum y el clúster se queda sin
coordinación. Con tres, la mayoría es dos y se tolera la caída de uno. Dos nodos no es "casi
tres": es peor que uno, porque duplica la probabilidad de falla sin comprar tolerancia.

**Por qué tres brokers.** Es el mínimo para usar factor de replicación 3 con
`min.insync.replicas=2`. Esa combinación es la que permite seguir aceptando escrituras con
un broker caído: quedan dos copias al día, que es justo el mínimo exigido. Con dos brokers
habría que bajar a `min.insync=1`, y una sola copia confirmada deja de ser garantía.

**Kafka UI va en su propio compose.** Es una herramienta de operación, no parte del sistema:
se levanta y se baja sin tocar el clúster. La pauta además lo pide como archivo aparte.

---

## 3. Los dos tópicos

Comparten particiones y réplicas, pero no la política de limpieza, porque **no guardan lo
mismo**.

| | `pedidos360.pedidos.eventos` | `pedidos360.inventario.stock` |
|---|---|---|
| Qué guarda | historia: todo lo que pasó | estado: cómo está cada producto |
| Particiones | 3 | 3 |
| Réplicas | 3 (`min.insync.replicas=2`) | 3 (`min.insync.replicas=2`) |
| `cleanup.policy` | `delete` | `compact` |
| Retención | 7 días (`retention.ms=604800000`) | sin vencimiento |
| Clave de particionado | número de pedido | id de producto |
| Quién lo produce | `ms-pedidos` | `ms-productos` |

**Por qué `delete` en pedidos.** Son hechos del pasado, y el pasado no se reemplaza: que un
pedido se confirme no borra que antes se creó. Los dos registros importan y en ese orden. Lo
que sí tiene sentido es que caduquen, y siete días alcanzan para que cualquier consumidor
que estuvo caído se ponga al día.

**Por qué `compact` en stock.** Aquí solo interesa el último valor de cada producto. Que el
SKU-7 pasara por 40, 25 y 12 unidades no le sirve a nadie; lo que se necesita es que tiene
12. Kafka conserva el último registro de cada clave y descarta los anteriores, así que el
tópico funciona como una tabla: un consumidor nuevo lo lee desde el principio y reconstruye
el inventario completo sin consultarle nada a `ms-productos`.

**Por qué tres particiones.** Es el límite de paralelismo: en un grupo, cada partición la
atiende un solo consumidor, así que tres particiones permiten tres consumidores trabajando a
la vez. Con una, por más instancias que se levanten, consume una sola.

**Por qué esas claves.** Kafka garantiza orden *dentro* de una partición, no en el tópico.
Al usar el número de pedido como clave, todos los hechos del mismo pedido caen en la misma
partición y se leen en el orden en que ocurrieron. Sin eso, una anulación podría procesarse
antes que su confirmación. En stock la clave es el id de producto, que además es lo que hace
funcionar la compactación: compacta por clave.

---

## 4. Diseño de los eventos

Los dos DTO viven en `pedidos360-common-streaming` y son `record` validados con Jakarta
Validation. El criterio al diseñarlos fue uno: **el evento tiene que alcanzar por sí solo**.
Si el consumidor tiene que llamar de vuelta al productor para completar un dato, el
desacoplamiento se perdió.

`PedidoStreamEvento` lleva:

| Campo | Para qué está |
|---|---|
| `eventoId` | idempotencia: Kafka entrega al menos una vez y el consumidor descarta repetidos |
| `tipo` | `CREADO`, `CONFIRMADO`, `ANULADO`, `ESTADO_CAMBIADO` |
| `pedidoNumero` | es la clave de particionado, y lo que ordena la historia |
| `clienteId` y `clienteNombre` | el ranking de clientes se arma sin consultar `ms-clientes` |
| `total` y `cantidadLineas` | la métrica del día se calcula sin consultar `ms-pedidos` |
| `estadoAnterior` y `estadoNuevo` | permiten distinguir una anulación de una confirmación |
| `motivo` | por qué cambió, cuando lo hay |
| `usuarioOid` | quién lo provocó, para la trazabilidad |
| `ocurridoEn` | cuándo pasó de verdad, no cuándo se leyó |

`StockStreamEvento` lleva `productoId`, `sku`, `nombre`, `categoria`, `precio`,
`stockAnterior`, `stockActual`, `motivo` y `ocurridoEn`. Que viajen **los dos valores de
stock** es deliberado: con el anterior y el actual, el consumidor sabe si el umbral crítico
se acaba de cruzar o si ya venía cruzado, y así el aviso se genera una vez y no en cada
venta.

---

## 5. Dónde está cada cosa

### `pedidos360-common-streaming` (módulo compartido)
Nombres de tópicos y grupos, declaración de los tópicos, los DTO, el productor, el validador
y la política de errores. Lo comparten los cinco microservicios, así que nadie escribe el
nombre de un tópico a mano.

### `ms-pedidos` y `ms-productos` (productores)
Publican el hecho después de guardar en base de datos. La publicación nunca rompe la
operación: si el clúster no responde, queda el error en el log y el pedido igual se guardó.

### `ms-analitica` (consumidor, puerto 8086)
Dos `@KafkaListener` en dos grupos distintos. Construye las métricas del día, el ranking de
clientes, la proyección de stock y los avisos. Ninguna de esas tablas se escribe por HTTP:
todas se alimentan del flujo.

### `ms-notificaciones` (consumidor, puerto 8084)
Un tercer grupo sobre el tópico de pedidos: archiva cada hecho tal como llegó, con su
tópico, partición y offset de origen.

### `ms-mensajeria` (administrador, puerto 8085)
Crear, configurar, ampliar y eliminar tópicos; listar grupos con su lag y la distribución
del consumo.

---

## 6. Los tres grupos de consumidores

```
   pedidos360.pedidos.eventos
         │
         ├──► grupo pedidos360-analitica-ventas   (ms-analitica)      ──► métricas
         │
         └──► grupo pedidos360-archivo-pedidos    (ms-notificaciones) ──► archivo

   pedidos360.inventario.stock
         │
         └──► grupo pedidos360-proyeccion-stock   (ms-analitica)      ──► proyección + avisos
```

Los dos primeros leen **el mismo tópico**. Kafka reparte las particiones *dentro* de un
grupo, pero entrega el flujo completo a *cada* grupo: por eso ambos ven los cuatro hechos de
un pedido y ninguno le quita nada al otro. Cada grupo lleva su propio avance, así que el
archivo puede caerse, ir atrasado o reprocesarse desde cero sin afectar a las métricas.

Esa es la diferencia de fondo con una cola: en RabbitMQ el mensaje consumido deja de estar
disponible; aquí el registro sigue en el tópico y cada interesado decide hasta dónde lo
leyó.

Cada grupo levanta **tres consumidores**, uno por partición, que es lo que se ve en la
captura `kafka-07`: tres `ms-analitica-0/1/2` con una partición asignada cada uno.

---

## 7. Qué pasa cuando un registro falla

Tres caminos distintos, según qué falló:

| Qué pasó | Qué hace el sistema | Por qué |
|---|---|---|
| Error pasajero (la base no responde) | reintenta 3 veces con espera creciente | volver a intentar tiene sentido |
| El evento es inválido (falta un campo) | al DLT **en el primer intento** | reintentar no lo va a arreglar |
| El cuerpo no es JSON | al DLT tal como llegó | no se puede ni deserializar |

Lo inválido no se reintenta a propósito: gastar tres intentos en un registro que nunca va a
pasar la validación solo retrasa a los que vienen detrás en la misma partición.

El registro descartado llega al DLT con cabeceras que dicen de qué tópico, partición y
offset venía, qué grupo lo descartó y con qué excepción. Eso es lo que se ve en `kafka-09`:
los mismos dos registros inválidos aparecen **dos veces**, una por cada grupo que los
rechazó, lo que confirma que los grupos son independientes.

---

## 8. Administración desde el API

`ms-mensajeria` expone la plataforma por HTTP, con los mismos roles del resto del sistema
(consultar es de gestión; crear y borrar es de administrador):

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/streaming/topicos` | lista con particiones, réplicas y política |
| GET | `/api/v1/streaming/topicos/cluster` | brokers, controlador y particiones sin replicar |
| GET | `/api/v1/streaming/topicos/{nombre}` | líder, réplicas y offsets de cada partición |
| POST | `/api/v1/streaming/topicos` | crea un tópico |
| PATCH | `/api/v1/streaming/topicos/{n}/configuracion` | cambia `retention.ms`, `cleanup.policy`… |
| POST | `/api/v1/streaming/topicos/{n}/particiones` | amplía las particiones |
| DELETE | `/api/v1/streaming/topicos/{nombre}` | elimina un tópico |
| GET | `/api/v1/streaming/grupos` | grupos con estado, miembros y lag total |
| GET | `/api/v1/streaming/grupos/{grupo}` | avance partición por partición |
| DELETE | `/api/v1/streaming/grupos/{grupo}` | elimina un grupo y su avance |

Dos decisiones de diseño:

- **La topología del sistema está protegida.** Los dos tópicos, sus DLT y los tres grupos no
  se pueden borrar desde el API. Un tópico borrado se lleva la historia con él, y a
  diferencia de una cola vacía, no hay forma de reconstruirlo. La negativa se evalúa antes
  de consultar al clúster.
- **Las particiones solo se amplían.** Kafka no permite reducirlas, y con razón: cambiaría
  la partición que le toca a cada clave y los registros ya escritos quedarían separados de
  los nuevos de la misma clave, perdiendo la garantía de orden.

---

## 9. Levantarlo en local

```bash
docker compose -f infra/kafka/docker-compose.yml up -d
docker compose -f infra/kafka-ui/docker-compose.yml up -d
bash infra/kafka/crear-topicos.sh
```

Kafka UI queda en http://localhost:8088. Los microservicios se levantan como siempre; los
consumidores se conectan solos.

---

## 10. Demostrar que funciona

```bash
bash infra/local/probar-streaming.sh
```

El script publica directamente en los tópicos, sin pasar por los productores, para poder
ejercitar también lo que la aplicación nunca generaría. Hace cuatro cosas:

1. **Cuatro hechos del mismo pedido** con la misma clave: caen en la misma partición y se
   leen en orden.
2. **Tres movimientos de stock** del mismo producto, uno de los cuales cruza el umbral
   crítico.
3. **Un evento sin número de pedido** y **un cuerpo que no es JSON**.
4. Muestra el avance de los tres grupos y el contenido de los tópicos de descarte.

Resultado de la corrida:

```
Metricas del 2026-11-22 actualizadas por CONFIRMADO del pedido PED-DEMO-...
Stock del producto SKU-DEMO-801 proyectado en 25 unidades
Aviso generado: el producto SKU-DEMO-801 quedo en 4 unidades (umbral 10)
Stock del producto SKU-DEMO-801 proyectado en 2 unidades        ← no repite el aviso
Hecho CONFIRMADO del pedido PED-DEMO-... archivado desde ...-1 offset 3
Registro invalido descartado: evento=demo-malo-... motivo=pedidoNumero no debe estar vacío
Registro derivado a pedidos360.pedidos.eventos.dlt ... (clave ILEGIBLE): Listener failed
```

El aviso de stock aparece **una sola vez**, al cruzar el umbral, y no vuelve a aparecer
cuando el producto sigue bajo. Eso es el comportamiento reactivo que pide la pauta: el
sistema no consultó nada, reaccionó a la llegada de un registro.

---

## 11. Supervisión: offsets, lag y distribución

Por CLI:

```bash
bash infra/kafka/inspeccionar-kafka.sh
docker exec pedidos360-kafka1 kafka-consumer-groups \
  --bootstrap-server kafka1:29092 --describe --group pedidos360-analitica-ventas
```

```
GROUP                       TOPIC                       PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG  CONSUMER-ID
pedidos360-analitica-ventas pedidos360.pedidos.eventos  0          14              14              0    ms-analitica-0-1ba0148e…
pedidos360-analitica-ventas pedidos360.pedidos.eventos  1          10              10              0    ms-analitica-1-0c177f9c…
pedidos360-analitica-ventas pedidos360.pedidos.eventos  2          4               4               0    ms-analitica-2-4f8f4d6d…
```

Qué dice cada número y qué problema anticipa:

- **Current offset**: hasta dónde confirmó el grupo. **End offset**: hasta dónde escribió el
  productor. **Lag**: la diferencia, lo que queda por procesar.
- Un **lag que sube sostenidamente** dice que el consumo no alcanza al ingreso: hay que
  sumar consumidores, y si ya hay uno por partición, hay que ampliar particiones.
- Un **lag alto concentrado en una sola partición** dice que la clave quedó mal elegida y el
  reparto está desbalanceado.
- Una **partición sin consumidor asignado** dice que el grupo tiene menos consumidores que
  particiones.
- **URP (particiones sin replicar) distinto de cero** dice que el clúster perdió
  redundancia: sigue atendiendo, pero ya no tiene todas las copias al día.

---

## 12. Evidencia

| Captura | Qué muestra |
|---|---|
| `kafka-01-brokers.png` | 3 brokers, 45 de 45 réplicas en sincronía, 0 fuera de sincronía |
| `kafka-02-topicos.png` | los dos tópicos con 3 particiones y 3 réplicas |
| `kafka-03-grupos.png` | los tres grupos, estables, con 3 miembros cada uno |
| `kafka-04-topico-pedidos.png` | réplicas y líder de cada partición, política `DELETE` |
| `kafka-05-config-retencion.png` | `retention.ms`, `cleanup.policy`, `min.insync.replicas` |
| `kafka-06-topico-stock-compactado.png` | el segundo tópico con política `COMPACT` |
| `kafka-07-grupo-lag-particiones.png` | offsets, lag y qué consumidor atiende cada partición |
| `kafka-08-mensajes-topico.png` | los mensajes con su clave, repartidos en las 3 particiones |
| `kafka-09-dlt-descartes.png` | los descartes, duplicados por los dos grupos que los rechazaron |

---

## 13. Dónde mira la pauta

### EP5 — encargo (12%)

| Indicador | Vale | Dónde está |
|---|---|---|
| Configuración del cliente Kafka | 12% | `application.yml` de los 5 microservicios |
| Productores y consumidores sobre los dos tópicos | 12% | `ms-pedidos`, `ms-productos`, `ms-analitica` |
| Microservicio administrador de tópicos | 6% | `KafkaAdminService` y `TopicoController` |
| Productores con DTO y serializers | 14% | `pedidos360-common-streaming` |
| Consumidores con `@KafkaListener` | 14% | `VentasListener`, `StockListener`, `ArchivoPedidosListener` |
| Manejo de errores | 12% | `GestorErroresStream` y `ValidadorEventos` |
| Parámetros de administración de tópicos | 12% | `crear-topicos.sh` y `StreamingProperties` |
| Consumer groups | 12% | tres grupos, uno en otro microservicio |
| Operaciones de inspección | 6% | `inspeccionar-kafka.sh` y el API de grupos |

### EP6 — presentación (18%)

| Indicador | Vale | Con qué se responde |
|---|---|---|
| Arquitectura Kafka: 3 Zookeeper, 3 brokers | 10% | sección 2 y `kafka-01` |
| Justificar 2 tópicos, 3 particiones, 3 réplicas | 10% | sección 3 y `kafka-02` |
| Kafka UI y administración | 5% | `kafka-04` a `kafka-09` |
| Flujo de producción y consumo | 12% | secciones 5 y 6 |
| Diseño de los eventos | 6% | sección 4 |
| Reacción en tiempo real | 12% | sección 10 |
| Particiones, réplicas, retention, consumer groups | 15% | secciones 3, 6 y 11 |
| Offsets, lag y distribución del consumo | 15% | sección 11 y `kafka-07` |
| Casos de uso que exigen streaming | 15% | secciones 1 y 6 |

---

## 14. Pruebas

109 pruebas en verde (eran 84 antes de esta etapa):

| Módulo | Qué cubre lo nuevo |
|---|---|
| `pedidos360-common-streaming` | validación, descarte de inválidos, cálculo del delta de stock |
| `ms-analitica` | métricas, idempotencia ante registro repetido, aviso solo al cruzar el umbral |
| `ms-mensajeria` | roles, validación de entrada y protección de la topología |

```bash
./mvnw test
```

Dos defectos que encontraron estas pruebas y se corrigieron:

- Spring Kafka rechaza un `JsonSerializer` configurado por código **y** por propiedades a la
  vez. Había que sacar `spring.json.*` del YAML.
- La publicación podía fallar en el hilo de la petición al crear el productor, devolviendo
  un 500 por un pedido que ya estaba guardado. Ahora el fallo queda en el log y no sube.
