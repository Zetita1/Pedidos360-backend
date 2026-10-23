#!/usr/bin/env bash
# User data de la instancia EC2 que aloja el BFF y los microservicios de Pedidos360.
# Instala Java 17, crea el usuario de servicio y resuelve la configuracion sensible desde
# AWS Systems Manager Parameter Store. Ningun secreto queda escrito en el repositorio.
set -euo pipefail

REGION="${REGION:-us-east-1}"
PREFIJO_SSM="/pedidos360/prod"

dnf -y update
dnf -y install java-17-amazon-corretto-headless awscli

id -u pedidos360 >/dev/null 2>&1 || useradd --system --create-home --shell /sbin/nologin pedidos360
mkdir -p /opt/pedidos360 /etc/pedidos360
chown -R pedidos360:pedidos360 /opt/pedidos360

leer_parametro() {
  aws ssm get-parameter --name "${PREFIJO_SSM}/$1" --with-decryption --region "${REGION}" \
    --query 'Parameter.Value' --output text
}

ENTRA_TENANT_ID="$(leer_parametro entra/tenant-id)"
ENTRA_API_AUDIENCE="$(leer_parametro entra/api-audience)"
DB_HOST="$(leer_parametro rds/host)"
DB_USER="$(leer_parametro rds/usuario)"
DB_PASSWORD="$(leer_parametro rds/password)"
CORS_ORIGINS="$(leer_parametro app/cors-origins)"
RABBITMQ_USER="$(leer_parametro rabbitmq/usuario)"
RABBITMQ_PASSWORD="$(leer_parametro rabbitmq/password)"

escribir_env() {
  local servicio="$1" base_datos="$2"
  cat > "/etc/pedidos360/${servicio}.env" <<ENV
ENTRA_ISSUER=https://login.microsoftonline.com/${ENTRA_TENANT_ID}/v2.0
ENTRA_TENANT_ID=${ENTRA_TENANT_ID}
ENTRA_API_AUDIENCE=${ENTRA_API_AUDIENCE}
CORS_ALLOWED_ORIGINS=${CORS_ORIGINS}
DB_HOST=${DB_HOST}
DB_PORT=5432
DB_NAME=${base_datos}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
MS_PEDIDOS_URL=http://localhost:8081
MS_PRODUCTOS_URL=http://localhost:8082
MS_CLIENTES_URL=http://localhost:8083
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=${RABBITMQ_USER}
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}
RABBITMQ_GESTION_URL=http://localhost:15672
ENV
  chmod 600 "/etc/pedidos360/${servicio}.env"
}

escribir_env bff-service pedidos360
escribir_env ms-pedidos pedidos360_pedidos
escribir_env ms-productos pedidos360_productos
escribir_env ms-clientes pedidos360_clientes
escribir_env ms-notificaciones pedidos360_notificaciones
escribir_env ms-mensajeria pedidos360

echo "Instancia preparada. Suba los jar a /opt/pedidos360/<servicio>/app.jar y habilite los servicios."
