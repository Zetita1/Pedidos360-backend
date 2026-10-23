# Mensajería asíncrona con RabbitMQ (EP3)

Cómo quedó integrado RabbitMQ en Pedidos360: qué se publica, quién lo consume, qué pasa
cuando algo falla y cómo se administra el broker.

---

## 1. Qué problema resuelve

Antes, confirmar un pedido hacía todo de corrido: descontar stock, generar el documento y
avisarle al cliente. Si la generación del ticket fallaba, se caía la confirmación completa.

Ahora el microservicio de pedidos **publica un evento y responde de inmediato**. El trabajo
que no necesita estar en el camino del usuario —avisos y documentos— lo hacen consumidores
en otro microservicio, a su ritmo, y si algo falla se reintenta sin afectar al pedido.

---

## 2. Topología

```
                      exchange: pedidos360.eventos (topic)
                                   │
   pedido.creado ──────────────────┤
   pedido.estado-cambiado ─────────┼──► pedidos360.notificaciones ──► ms-notificaciones
                                   │          │
   pedido.confirmado ──────────────┼──► pedidos360.documentos ──────► ms-notificaciones
                                   │          │
                                   │          ├── rechazo recuperable ──► <cola>.retry (TTL 5 s)
                                   │          │                              │ vuelve
                                   │          │                              ▼
                                   │          │                          <cola> otra vez
                                   │          └── rechazo definitivo ───► pedidos360.eventos.dlx
                                   │                                          │
                                   │                                          ▼
                                   │                                      <cola>.dlq
```

Seis colas en total: dos principales, dos de reintento y dos de mensajes muertos.

| Caso de uso | Cola principal | Reintento | DLQ | Eventos que recibe |
|---|---|---|---|---|
| Notificaciones | `pedidos360.notificaciones` | `.retry` | `.dlq` | `pedido.creado`, `pedido.estado-cambiado` |
| Documentos | `pedidos360.documentos` | `.retry` | `.dlq` | `pedido.confirmado` |

**Todos los nombres salen de `application.yml`**, bajo `pedidos360.mensajeria`. Ninguna clase
de negocio escribe el nombre de una cola: lo pide a `MensajeriaProperties`.

---

## 3. Dónde está cada cosa

### `pedidos360-common-messaging` (módulo compartido)

| Clase | Responsabilidad |
|---|---|
| `MensajeriaProperties` | Nombres de exchanges, colas y routing keys; política de reintentos |
| `TopologiaMensajeriaConfig` | Beans `Queue`, `Exchange` y `Binding`, uno por ruta de mensajería |
| `MensajeriaAutoConfiguration` | Conversión a JSON, publicador y fábrica de listeners con **ACK manual** |
| `PublicadorEventos` / `RabbitPublicadorEventos` | Puerta de salida de los eventos |
| `GestorConsumo` | Política de confirmación: ACK, reintento o DLQ |
| `evento/*` | Los tres eventos de dominio, como `record` inmutables |

### `ms-pedidos` (productor)

`PedidoService` publica tras guardar: pedido creado, confirmado y cambio de estado. Recibe
`PublicadorEventos` por constructor; no conoce RabbitMQ.

### `ms-notificaciones` (consumidor, puerto 8084)

| Clase | Responsabilidad |
|---|---|
| `NotificacionesListener` | Consume la cola de notificaciones (`@RabbitListener` + `@RabbitHandler`) |
| `DocumentosListener` | Consume la cola de documentos |
| `NotificacionService` | Genera los avisos; no sabe de colas |
| `DocumentoService` | Emite el ticket del pedido |
| `NotificacionController` | API de solo lectura para el frontend |

Los consumidores están **agrupados por dominio funcional**: avisos por un lado, documentos
por otro, cada uno con su cola y su DLQ, para que un problema con los tickets no frene los
avisos.

### `ms-mensajeria` (administrador, puerto 8085)

| Clase | Responsabilidad |
|---|---|
| `RabbitAdminService` | Toda la conversación con el broker: crear, purgar, eliminar |
| `ClienteGestionRabbit` | Lista colas y exchanges por la API de gestión |
| `ColaController`, `ExchangeController`, `BindingController` | API REST |
| `MensajeriaDtos` | DTO de entrada con anotaciones de validación |

---

## 4. Qué pasa cuando un mensaje falla

El `GestorConsumo` distingue tres situaciones, y siempre deja rastro en el log:

| Situación | Qué hace | Resultado |
|---|---|---|
| Todo salió bien | `basicAck` | El mensaje sale de la cola |
| Error **recuperable** (la base no responde, un servicio está caído) | Reenvía a `<cola>.retry` con el contador `x-pedidos360-intentos` y confirma el original | Vuelve a la cola principal pasados 5 s |
| Se agotaron los **3 intentos** | `basicNack` sin reencolar | Va a `<cola>.dlq` con el motivo en la cabecera |
| Error **no recuperable** (`EventoNoProcesableException`: evento sin número de pedido, sin líneas) | `basicNack` sin reencolar, sin gastar reintentos | Va directo a `<cola>.dlq` |
| Tipo de mensaje inesperado | `basicNack` sin reencolar | Va a la DLQ y queda registrado |

El ACK es **manual** (`AcknowledgeMode.MANUAL`) y el reencolado automático está desactivado
(`defaultRequeueRejected(false)`): un mensaje nunca vuelve en bucle a la misma cola.

Además, cada consumidor es **idempotente**: guarda el `eventoId` de origen, así que si
RabbitMQ entrega el mismo mensaje dos veces, el aviso no se duplica.

---

## 5. API del administrador

Base: `/api/v1/mensajeria`. Todas las rutas exigen JWT; administrar exige `Pedidos.Admin`.

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/colas` | Lista las colas con sus mensajes y consumidores |
| `GET` | `/colas/{nombre}` | Estado de una cola |
| `POST` | `/colas` | Crea una cola, opcionalmente con su DLQ |
| `POST` | `/colas/{nombre}/purgar` | Vacía una cola sin eliminarla |
| `DELETE` | `/colas/{nombre}` | Elimina una cola |
| `GET` | `/exchanges` | Lista los exchanges |
| `POST` | `/exchanges` | Crea un exchange (direct, topic, fanout, headers) |
| `DELETE` | `/exchanges/{nombre}` | Elimina un exchange |
| `POST` | `/bindings` | Enlaza una cola a un exchange |
| `DELETE` | `/bindings` | Quita el enlace |
| `GET` | `/topologia` | Topología declarada por la aplicación |

**Validación de entrada.** Los DTO llevan anotaciones: el nombre es obligatorio, de 3 a 120
caracteres y solo admite letras, números, punto, guion y guion bajo; el tipo de exchange debe
ser uno de los cuatro válidos; el TTL no puede ser negativo. Una petición inválida responde
**400** con el detalle, sin tocar el broker.

**Protección de la topología.** Las colas y exchanges que declara la aplicación no se pueden
eliminar desde el API: responde **409**. Se desactiva con
`pedidos360.rabbit-admin.proteger-topologia-del-sistema: false`.

---

## 6. Levantarlo en local

```bash
docker compose -f infra/local/docker-compose.yml up -d rabbitmq
```

Consola del broker: <http://localhost:15672> (`pedidos360` / `pedidos360`).

Después, cada microservicio en su terminal:

```bash
./mvnw -pl ms-pedidos spring-boot:run
./mvnw -pl ms-notificaciones spring-boot:run
./mvnw -pl ms-mensajeria spring-boot:run
```

Las colas se crean solas al arrancar: la topología está declarada en beans.

---

## 7. Demostrar que funciona

### Camino feliz

1. En el frontend, crear un pedido → aparece un aviso de **recepción** en *Notificaciones*.
2. Confirmar el pedido → aparece el **ticket** con su folio.
3. Cambiar el estado → aparece el aviso de **seguimiento**.

### Camino de error (DLQ)

Publicar un evento inválido desde la consola de RabbitMQ (*Exchanges →
`pedidos360.eventos` → Publish message*):

- Routing key: `pedido.creado`
- Headers: `__TypeId__` = `cl.duoc.pedidos360.mensajeria.evento.PedidoCreadoEvento`
- Payload: `{"eventoId":"evt-dlq-1","ocurridoEn":"2026-10-23T12:00:00Z","pedidoNumero":"  ","total":100,"lineas":1}`

El consumidor lo rechaza como no procesable y el mensaje aparece en
`pedidos360.notificaciones.dlq`, con el motivo en el log:

```
ERROR GestorConsumo - Evento no procesable en pedidos360.notificaciones: El evento evt-dlq-1
llego sin numero de pedido. Se envia a pedidos360.notificaciones.dlq
```

El mismo recorrido está automatizado en `infra/local/probar-mensajeria.sh`.

---

## 8. Desplegar en AWS

```bash
# 1. Instalar el broker en la instancia (una sola vez)
./infra/ec2/instalar-rabbitmq.sh <ip-ec2> ~/.ssh/pedidos360-lab.pem

# 2. Guardar las credenciales del broker
aws ssm put-parameter --name /pedidos360/rabbitmq/usuario  --value pedidos360 --type String --overwrite
aws ssm put-parameter --name /pedidos360/rabbitmq/password --value '<clave>' --type SecureString --overwrite

# 3. Subir los seis microservicios
./infra/ec2/desplegar.sh <ip-ec2> ~/.ssh/pedidos360-lab.pem

# 4. Publicar las 15 rutas nuevas en el API Gateway
export API_ID=j9w6eomnw5 BACKEND_HOST=<ip-ec2>
./infra/api-gateway/agregar-rutas-mensajeria.sh
```

RabbitMQ escucha solo en `localhost` dentro de la instancia: no se expone ningún puerto nuevo
a internet. Para ver su consola, túnel SSH:

```bash
ssh -i ~/.ssh/pedidos360-lab.pem -L 15672:localhost:15672 ec2-user@<ip-ec2>
```

---

## 9. Dónde mira la pauta

| Indicador | Peso | Dónde está |
|---|---|---|
| Nombres centralizados | 12% | `MensajeriaProperties` + `application.yml` de cada servicio |
| Beans Queue, Exchange y Binding por caso de uso | 13% | `TopologiaMensajeriaConfig` |
| Configuración separada del negocio | 10% | Módulo `pedidos360-common-messaging`; los servicios solo reciben interfaces |
| Consumidores con `@RabbitListener` por dominio | 15% | `NotificacionesListener`, `DocumentosListener` |
| ACK y manejo de errores | 20% | `GestorConsumo` + `GestorConsumoTest` (6 pruebas) |
| Endpoints REST del administrador | 13% | `ColaController`, `ExchangeController`, `BindingController` |
| Lógica encapsulada en un servicio | 10% | `RabbitAdminService` |
| Validación de la entrada | 7% | `MensajeriaDtos` con anotaciones + `MensajeriaApiTest` |

---

## 10. Pruebas

```bash
./mvnw -pl pedidos360-common-messaging,ms-notificaciones,ms-mensajeria test
```

| Módulo | Pruebas | Qué cubren |
|---|---|---|
| `pedidos360-common-messaging` | 6 | ACK, reintento, DLQ, contador de intentos y fallo al reintentar |
| `ms-notificaciones` | 10 | Avisos, ticket, idempotencia, eventos inválidos y seguridad del API |
| `ms-mensajeria` | 12 | Autorización por rol, validación de entrada, 404, 409 y topología |
