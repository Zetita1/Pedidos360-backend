#!/usr/bin/env bash
# Despliegue de los seis artefactos de Pedidos360 en una instancia EC2.
#
# Uso: ./desplegar.sh <host-ec2> <ruta-llave.pem>
set -euo pipefail

HOST="${1:?Indica el host o IP de la instancia EC2}"
LLAVE="${2:?Indica la ruta de la llave .pem}"
USUARIO="${USUARIO_EC2:-ec2-user}"
RAIZ="$(cd "$(dirname "$0")/../../pedidos360-backend" && pwd)"

echo "==> Compilando artefactos"
"${RAIZ}/mvnw" -f "${RAIZ}/pom.xml" clean package

declare -A SERVICIOS=(
  [bff-service]=8080
  [ms-pedidos]=8081
  [ms-productos]=8082
  [ms-clientes]=8083
  [ms-notificaciones]=8084
  [ms-mensajeria]=8085
)

for servicio in "${!SERVICIOS[@]}"; do
  jar="$(ls "${RAIZ}/${servicio}/target/${servicio}-"*.jar | grep -v original | head -1)"
  echo "==> Publicando ${servicio} (${jar})"

  ssh -i "${LLAVE}" "${USUARIO}@${HOST}" "sudo mkdir -p /opt/pedidos360/${servicio}"
  scp -i "${LLAVE}" "${jar}" "${USUARIO}@${HOST}:/tmp/${servicio}.jar"
  ssh -i "${LLAVE}" "${USUARIO}@${HOST}" "
    sudo mv /tmp/${servicio}.jar /opt/pedidos360/${servicio}/app.jar &&
    sudo chown -R pedidos360:pedidos360 /opt/pedidos360/${servicio} &&
    sudo systemctl restart ${servicio} || sudo systemctl start ${servicio}
  "
done

echo "==> Verificando health checks"
for puerto in 8080 8081 8082 8083 8084 8085; do
  ssh -i "${LLAVE}" "${USUARIO}@${HOST}" "curl -fsS http://localhost:${puerto}/actuator/health" && echo
done
