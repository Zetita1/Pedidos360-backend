#!/usr/bin/env bash
# Prueba el flujo completo de streaming contra el cluster de Kafka.
#
#   bash infra/local/probar-streaming.sh
#
# Publica directamente en los topicos, sin pasar por los microservicios productores, para
# poder ejercitar tambien los casos que la aplicacion nunca generaria: un evento al que le
# falta un dato obligatorio y un cuerpo que no es JSON.
#
# Lo que deberia ocurrir:
#   - los cuatro eventos validos de pedidos actualizan las metricas y quedan archivados
#   - el movimiento de stock actualiza la proyeccion; el que cruza el umbral genera un aviso
#   - el evento sin numero de pedido va al DLT en el primer intento, sin reintentar
#   - el cuerpo que no es JSON tambien va al DLT, y ningun consumidor se cae
#
# Requiere el cluster arriba (infra/kafka/docker-compose.yml) y los microservicios
# ms-analitica y ms-notificaciones corriendo.
set -euo pipefail

BROKER="${BROKER:-kafka1:29092}"
CONTENEDOR="${CONTENEDOR:-pedidos360-kafka1}"
TOPICO_PEDIDOS="${TOPICO_PEDIDOS:-pedidos360.pedidos.eventos}"
TOPICO_STOCK="${TOPICO_STOCK:-pedidos360.inventario.stock}"

# Marca de tiempo comun: los ids de evento la llevan para poder repetir el script sin que los
# consumidores descarten todo por idempotencia.
CORRIDA="$(date +%s)"
AHORA="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

titulo() {
  echo
  echo "=============================================================="
  echo "  $1"
  echo "=============================================================="
}

# Publica una linea "clave|valor" en un topico.
publicar() {
  local topico="$1"
  local clave="$2"
  local cuerpo="$3"
  printf '%s|%s\n' "$clave" "$cuerpo" \
    | docker exec -i "$CONTENEDOR" kafka-console-producer \
        --bootstrap-server "$BROKER" \
        --topic "$topico" \
        --property parse.key=true \
        --property key.separator='|' 2>/dev/null
}

hecho_pedido() {
  local n="$1" tipo="$2" pedido="$3" total="$4" anterior="$5" nuevo="$6"
  cat <<JSON
{"eventoId":"demo-${CORRIDA}-${n}","tipo":"${tipo}","pedidoNumero":"${pedido}","pedidoId":${n},"clienteId":9${n},"clienteNombre":"Comercial Demo ${n} Ltda","total":${total},"cantidadLineas":2,"estadoAnterior":${anterior},"estadoNuevo":"${nuevo}","motivo":null,"usuarioOid":"demo-oid","ocurridoEn":"${AHORA}"}
JSON
}

movimiento_stock() {
  local n="$1" producto="$2" antes="$3" ahora="$4" motivo="$5"
  cat <<JSON
{"eventoId":"demo-stock-${CORRIDA}-${n}","productoId":${producto},"sku":"SKU-DEMO-${producto}","nombre":"Producto demo ${producto}","categoria":"Demo","precio":39990,"stockAnterior":${antes},"stockActual":${ahora},"motivo":"${motivo}","ocurridoEn":"${AHORA}"}
JSON
}

titulo "1. Hechos de pedidos validos (clave = numero de pedido)"
# Los cuatro hechos del mismo pedido comparten clave, asi que caen en la misma particion y se
# leen en el orden en que se publicaron. Es la unica garantia de orden que da Kafka.
publicar "$TOPICO_PEDIDOS" "PED-DEMO-${CORRIDA}" "$(hecho_pedido 1 CREADO "PED-DEMO-${CORRIDA}" 238000 null BORRADOR)"
echo "+ CREADO       PED-DEMO-${CORRIDA}"
publicar "$TOPICO_PEDIDOS" "PED-DEMO-${CORRIDA}" "$(hecho_pedido 2 CONFIRMADO "PED-DEMO-${CORRIDA}" 238000 '"BORRADOR"' CONFIRMADO)"
echo "+ CONFIRMADO   PED-DEMO-${CORRIDA}"
publicar "$TOPICO_PEDIDOS" "PED-DEMO-${CORRIDA}" "$(hecho_pedido 3 ESTADO_CAMBIADO "PED-DEMO-${CORRIDA}" 238000 '"CONFIRMADO"' EN_PREPARACION)"
echo "+ EN_PREPARACION  PED-DEMO-${CORRIDA}"
# Otro pedido, con otra clave: probablemente cae en otra particion y se procesa en paralelo.
publicar "$TOPICO_PEDIDOS" "PED-OTRO-${CORRIDA}" "$(hecho_pedido 4 ANULADO "PED-OTRO-${CORRIDA}" 51000 '"CONFIRMADO"' ANULADO)"
echo "+ ANULADO      PED-OTRO-${CORRIDA}"

titulo "2. Movimientos de stock (clave = id de producto, topico compactado)"
publicar "$TOPICO_STOCK" "801" "$(movimiento_stock 1 801 40 25 'Venta demo')"
echo "+ producto 801: 40 -> 25 unidades"
# Cruza el umbral critico (10 por omision): el consumidor debe generar el aviso solo aqui.
publicar "$TOPICO_STOCK" "801" "$(movimiento_stock 2 801 25 4 'Venta demo que cruza el umbral')"
echo "+ producto 801: 25 -> 4 unidades  (deberia generar aviso)"
publicar "$TOPICO_STOCK" "801" "$(movimiento_stock 3 801 4 2 'Venta demo bajo el umbral')"
echo "+ producto 801: 4 -> 2 unidades   (no deberia repetir el aviso)"

titulo "3. Casos que deben terminar en el topico de descarte"
# Sin numero de pedido: la validacion lo rechaza y el manejador lo trata como no recuperable,
# asi que va al DLT en el primer intento en vez de gastar tres.
publicar "$TOPICO_PEDIDOS" "SIN-NUMERO" \
  "{\"eventoId\":\"demo-malo-${CORRIDA}-1\",\"tipo\":\"CREADO\",\"pedidoNumero\":\"\",\"clienteId\":1,\"total\":1000,\"cantidadLineas\":1,\"estadoNuevo\":\"BORRADOR\",\"ocurridoEn\":\"${AHORA}\"}"
echo "- evento sin numero de pedido      -> deberia ir al DLT"
# Cuerpo ilegible: lo atrapa ErrorHandlingDeserializer y viaja al DLT tal como llego.
publicar "$TOPICO_PEDIDOS" "ILEGIBLE" 'esto no es json'
echo "- cuerpo que no es JSON            -> deberia ir al DLT"

echo
echo "Esperando a que los consumidores procesen..."
sleep 12

titulo "4. Avance de los grupos de consumidores"
# Tres grupos sobre el mismo topico de pedidos: cada uno lleva su propio avance.
for grupo in pedidos360-analitica-ventas pedidos360-proyeccion-stock pedidos360-archivo-pedidos; do
  echo "--- $grupo"
  docker exec "$CONTENEDOR" kafka-consumer-groups \
    --bootstrap-server "$BROKER" --describe --group "$grupo" 2>/dev/null \
    | grep -E 'TOPIC|pedidos360' || echo "   (sin avance registrado)"
  echo
done

titulo "5. Contenido de los topicos de descarte"
for dlt in "${TOPICO_PEDIDOS}.dlt" "${TOPICO_STOCK}.dlt"; do
  total=$(docker exec "$CONTENEDOR" bash -c \
    "kafka-run-class kafka.tools.GetOffsetShell --bootstrap-server $BROKER --topic $dlt 2>/dev/null | awk -F: '{s+=\$3} END {print s+0}'")
  echo "--- $dlt : $total registros"
done

echo
echo "Ultimos descartes con el motivo que registro Kafka:"
docker exec "$CONTENEDOR" timeout 10 kafka-console-consumer \
  --bootstrap-server "$BROKER" \
  --topic "${TOPICO_PEDIDOS}.dlt" \
  --from-beginning --max-messages 4 \
  --property print.key=true \
  --property print.headers=true 2>/dev/null \
  | sed 's/,kafka_dlt-exception-stacktrace[^,]*//' \
  | cut -c1-320 || echo "(sin registros en el DLT)"

titulo "6. Que revisar para dar la prueba por buena"
cat <<'TEXTO'
  En el log de ms-analitica:
    "Metricas del <fecha> actualizadas por CONFIRMADO del pedido PED-DEMO-..."
    "Aviso generado: el producto SKU-DEMO-801 quedo en 4 unidades (umbral 10)"
  En el log de ms-notificaciones:
    "Hecho CONFIRMADO del pedido PED-DEMO-... archivado desde ...-N offset M"
  En los dos:
    "Registro derivado a pedidos360.pedidos.eventos.dlt ..." por los dos casos invalidos

  En Kafka UI (http://localhost:8088):
    los dos topicos con 3 particiones y 3 replicas, y el lag de los tres grupos en cero.
TEXTO
