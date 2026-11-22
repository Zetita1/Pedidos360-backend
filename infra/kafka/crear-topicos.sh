#!/usr/bin/env bash
# Declara los dos topicos de Pedidos360 con sus particiones, replicas y politicas.
#
#   bash infra/kafka/crear-topicos.sh
#
# La aplicacion tambien los declara al arrancar (NewTopic en pedidos360-common-streaming);
# este script existe para dejarlos listos antes de levantar los microservicios y para poder
# recrearlos o ajustarlos sin reiniciar nada.
#
# Los dos topicos comparten particiones y replicas, pero no la politica de limpieza: cada uno
# atiende un flujo de datos distinto y eso es lo que decide como se conserva.
set -euo pipefail

BROKER="${BROKER:-kafka1:29092}"
CONTENEDOR="${CONTENEDOR:-pedidos360-kafka1}"
PARTICIONES="${PARTICIONES:-3}"
REPLICAS="${REPLICAS:-3}"

TOPICO_PEDIDOS="${TOPICO_PEDIDOS:-pedidos360.pedidos.eventos}"
TOPICO_STOCK="${TOPICO_STOCK:-pedidos360.inventario.stock}"

kafka() {
  docker exec "$CONTENEDOR" "$@"
}

crear_o_actualizar() {
  local topico="$1"
  shift
  if kafka kafka-topics --bootstrap-server "$BROKER" --list | grep -qx "$topico"; then
    echo "~ $topico ya existe: se actualizan sus configuraciones"
    local config
    for config in "$@"; do
      kafka kafka-configs --bootstrap-server "$BROKER" \
        --entity-type topics --entity-name "$topico" \
        --alter --add-config "$config" >/dev/null
    done
  else
    local argumentos=()
    local config
    for config in "$@"; do
      argumentos+=(--config "$config")
    done
    kafka kafka-topics --bootstrap-server "$BROKER" --create \
      --topic "$topico" \
      --partitions "$PARTICIONES" \
      --replication-factor "$REPLICAS" \
      "${argumentos[@]}" >/dev/null
    echo "+ $topico creado con $PARTICIONES particiones y $REPLICAS replicas"
  fi
}

echo "== Topicos de negocio =="

# Flujo 1: historia de hechos de pedidos. Interesa la secuencia completa, no solo el ultimo
# estado, asi que se borra por tiempo (7 dias) y no se compacta.
#   min.insync.replicas=2 -> con acks=all la escritura se confirma al tenerla dos replicas:
#   se puede perder un broker sin perder el mensaje y sin dejar de escribir.
crear_o_actualizar "$TOPICO_PEDIDOS" \
  'cleanup.policy=delete' \
  'retention.ms=604800000' \
  'min.insync.replicas=2' \
  'max.message.bytes=1048576'

# Flujo 2: ultimo stock conocido de cada producto. La historia no aporta: lo que se necesita es
# el valor vigente por clave, asi que se compacta. Kafka conserva el ultimo registro de cada
# producto indefinidamente y el topico queda como una tabla de estado reconstruible.
#   segment.ms y min.cleanable.dirty.ratio bajos para que la compactacion ocurra pronto y se
#   pueda observar en la demo; en produccion se dejarian mas altos.
crear_o_actualizar "$TOPICO_STOCK" \
  'cleanup.policy=compact' \
  'min.insync.replicas=2' \
  'segment.ms=60000' \
  'min.cleanable.dirty.ratio=0.1' \
  'delete.retention.ms=120000'

# Colas de descarte: los mensajes que ningun reintento logro procesar. Se conservan 14 dias,
# mas que el flujo original, porque se revisan a mano.
echo
echo "== Topicos de descarte (DLT) =="
crear_o_actualizar "${TOPICO_PEDIDOS}.dlt" \
  'cleanup.policy=delete' \
  'retention.ms=1209600000' \
  'min.insync.replicas=2'
crear_o_actualizar "${TOPICO_STOCK}.dlt" \
  'cleanup.policy=delete' \
  'retention.ms=1209600000' \
  'min.insync.replicas=2'

echo
echo "== Estado final =="
for topico in "$TOPICO_PEDIDOS" "$TOPICO_STOCK" "${TOPICO_PEDIDOS}.dlt" "${TOPICO_STOCK}.dlt"; do
  echo "--- $topico"
  kafka kafka-topics --bootstrap-server "$BROKER" --describe --topic "$topico"
done
