#!/usr/bin/env bash
# Supervision del cluster Kafka desde la linea de comandos: brokers, topicos, grupos y lag.
#
#   bash infra/kafka/inspeccionar-kafka.sh
#
# Las mismas metricas que muestra Kafka UI, pero reproducibles y pegables en un informe.
#
# Como se leen:
#   Leader / Replicas / Isr -> quien atiende cada particion y cuantas replicas estan al dia.
#     Si Isr trae menos entradas que Replicas, hay un broker atrasado o caido.
#   CURRENT-OFFSET -> hasta donde leyo el grupo.
#   LOG-END-OFFSET -> hasta donde escribio el productor.
#   LAG -> la diferencia. Si crece sostenidamente, el consumo no alcanza al ingreso.
set -euo pipefail

BROKER="${BROKER:-kafka1:29092}"
CONTENEDOR="${CONTENEDOR:-pedidos360-kafka1}"

kafka() {
  docker exec "$CONTENEDOR" "$@"
}

titulo() {
  echo
  echo "=============================================================="
  echo "  $1"
  echo "=============================================================="
}

titulo 'Brokers del cluster'
kafka kafka-broker-api-versions --bootstrap-server "$BROKER" \
  | grep -E '^[a-z0-9.-]+:[0-9]+' | sed 's/ (id:/  id:/' | cut -d'(' -f1

titulo 'Nodos Zookeeper'
for nodo in pedidos360-zk1 pedidos360-zk2 pedidos360-zk3; do
  modo=$(docker exec "$nodo" bash -c 'echo srvr | nc localhost 2181 2>/dev/null | grep Mode' || echo 'Mode: sin respuesta')
  echo "$nodo -> $modo"
done

titulo 'Topicos'
kafka kafka-topics --bootstrap-server "$BROKER" --list

titulo 'Detalle de particiones y replicas'
kafka kafka-topics --bootstrap-server "$BROKER" --describe \
  --exclude-internal

titulo 'Configuracion por topico (solo lo definido por el proyecto)'
for topico in $(kafka kafka-topics --bootstrap-server "$BROKER" --list | grep '^pedidos360\.'); do
  echo "--- $topico"
  kafka kafka-configs --bootstrap-server "$BROKER" \
    --entity-type topics --entity-name "$topico" --describe \
    | tr ',' '\n' | grep -E 'cleanup.policy|retention.ms|min.insync.replicas|segment.ms' || true
done

titulo 'Particiones sin replicar (deberia estar vacio)'
kafka kafka-topics --bootstrap-server "$BROKER" --describe --under-replicated-partitions \
  || echo 'Todas las particiones estan replicadas'

titulo 'Grupos de consumidores'
kafka kafka-consumer-groups --bootstrap-server "$BROKER" --list

titulo 'Offsets, lag y distribucion del consumo'
for grupo in $(kafka kafka-consumer-groups --bootstrap-server "$BROKER" --list | grep -v '^$'); do
  echo "--- grupo $grupo"
  kafka kafka-consumer-groups --bootstrap-server "$BROKER" --describe --group "$grupo"
  echo
done
