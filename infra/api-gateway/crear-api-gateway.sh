#!/usr/bin/env bash
#
# Crea desde cero el HTTP API de Pedidos360 en AWS: autorizador JWT de Microsoft Entra
# External ID, CORS, una integracion por microservicio y una ruta explicita por endpoint.
#
# Es equivalente a importar pedidos360-http-api.yaml, pero paso a paso: sirve para mostrar
# en la presentacion como se arma cada pieza y para recrear el API si algo queda mal.
#
# Uso:
#   export ENTRA_TENANT_ID=<directory-tenant-id>
#   export API_AUDIENCE=api://pedidos360-api
#   export API_CLIENT_ID=<client-id-del-registro-de-la-api>   # opcional, ver abajo
#   export BACKEND_HOST=54.156.41.241                          # IP elastica del EC2
#
# Si el API ya existe y solo faltan las rutas de mensajeria, usar
# agregar-rutas-mensajeria.sh en vez de este script.
#   ./crear-api-gateway.sh
set -euo pipefail
# Git Bash convierte los argumentos que empiezan con "/" en rutas de Windows; se desactiva.
export AWS_PAGER="" MSYS_NO_PATHCONV=1

: "${ENTRA_TENANT_ID:?Falta ENTRA_TENANT_ID}"
: "${API_AUDIENCE:?Falta API_AUDIENCE}"
: "${BACKEND_HOST:?Falta BACKEND_HOST}"
ORIGEN_SPA="${ORIGEN_SPA:-http://localhost:4200}"
REGION="${AWS_REGION:-us-east-1}"
NOMBRE_API="${NOMBRE_API:-pedidos360-api}"

# El issuer de Entra External ID usa el TENANT ID como subdominio, no el nombre del tenant.
# Se puede sobrescribir con ENTRA_ISSUER; el valor exacto esta en
# https://<tenant>.ciamlogin.com/<tenant-id>/v2.0/.well-known/openid-configuration
ISSUER="${ENTRA_ISSUER:-https://${ENTRA_TENANT_ID}.ciamlogin.com/${ENTRA_TENANT_ID}/v2.0}"

# Entra emite el aud como el App ID URI o como el client ID de la API segun la version del
# token; se aceptan ambos identificadores de ESTA api y ninguno mas.
AUDIENCIAS="\"${API_AUDIENCE}\""
if [[ -n "${API_CLIENT_ID:-}" ]]; then
  AUDIENCIAS="${AUDIENCIAS},\"${API_CLIENT_ID}\""
fi

SP="$(mktemp -d)"
trap 'rm -rf "${SP}"' EXIT
# En Git Bash el aws.exe de Windows no entiende /tmp: se pasa a una ruta C:/... equivalente.
if command -v cygpath >/dev/null 2>&1; then
  SP="$(cygpath -m "${SP}")"
fi

# Origenes permitidos: el del SPA publicado y localhost para desarrollo, sin repetir.
ORIGENES="\"${ORIGEN_SPA}\""
if [[ "${ORIGEN_SPA}" != "http://localhost:4200" ]]; then
  ORIGENES="${ORIGENES},\"http://localhost:4200\""
fi

cat > "${SP}/cors.json" <<JSON
{
  "AllowOrigins": [${ORIGENES}],
  "AllowMethods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
  "AllowHeaders": ["authorization", "content-type", "x-correlation-id"],
  "ExposeHeaders": ["x-correlation-id"],
  "MaxAge": 1800
}
JSON

cat > "${SP}/jwt.json" <<JSON
{
  "Issuer": "${ISSUER}",
  "Audience": [${AUDIENCIAS}]
}
JSON

echo "==> 1/5 Creando el HTTP API con CORS"
API_ID="$(aws apigatewayv2 create-api \
  --region "${REGION}" \
  --name "${NOMBRE_API}" \
  --protocol-type HTTP \
  --cors-configuration "file://${SP}/cors.json" \
  --query 'ApiId' --output text)"
echo "    ApiId=${API_ID}"

echo "==> 2/5 Creando el autorizador JWT"
echo "    issuer   : ${ISSUER}"
echo "    audience : [${AUDIENCIAS}]"
AUTH_ID="$(aws apigatewayv2 create-authorizer \
  --region "${REGION}" \
  --api-id "${API_ID}" \
  --name entra-external-id \
  --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration "file://${SP}/jwt.json" \
  --query 'AuthorizerId' --output text)"
echo "    AuthorizerId=${AUTH_ID}"

echo "==> 3/5 Creando una integracion por microservicio"
# Sin variable de ruta en la URI: overwrite:path reenvia la ruta original completa, asi
# GET /api/v1/pedidos/7 llega tal cual a http://host:8081/api/v1/pedidos/7.
crear_integracion() {
  local puerto="$1"
  aws apigatewayv2 create-integration \
    --region "${REGION}" \
    --api-id "${API_ID}" \
    --integration-type HTTP_PROXY \
    --integration-method ANY \
    --payload-format-version 1.0 \
    --integration-uri "http://${BACKEND_HOST}:${puerto}" \
    --request-parameters 'overwrite:path=$request.path' \
    --query 'IntegrationId' --output text
}

INT_BFF="$(crear_integracion 8080)"
INT_PEDIDOS="$(crear_integracion 8081)"
INT_PRODUCTOS="$(crear_integracion 8082)"
INT_CLIENTES="$(crear_integracion 8083)"
INT_NOTIFICACIONES="$(crear_integracion 8084)"
INT_MENSAJERIA="$(crear_integracion 8085)"
# /health apunta directo al actuator del BFF, sin reescribir la ruta.
INT_HEALTH="$(aws apigatewayv2 create-integration \
  --region "${REGION}" \
  --api-id "${API_ID}" \
  --integration-type HTTP_PROXY \
  --integration-method GET \
  --payload-format-version 1.0 \
  --integration-uri "http://${BACKEND_HOST}:8080/actuator/health" \
  --query 'IntegrationId' --output text)"
echo "    bff=${INT_BFF} pedidos=${INT_PEDIDOS} productos=${INT_PRODUCTOS} clientes=${INT_CLIENTES}"
echo "    notificaciones=${INT_NOTIFICACIONES} mensajeria=${INT_MENSAJERIA} health=${INT_HEALTH}"

echo "==> 4/5 Creando las rutas (todas con validacion JWT salvo /health)"
crear_ruta() {
  local clave="$1" integracion="$2" protegida="${3:-si}"
  local args=(--region "${REGION}" --api-id "${API_ID}"
              --route-key "${clave}" --target "integrations/${integracion}")
  if [[ "${protegida}" == "si" ]]; then
    args+=(--authorization-type JWT --authorizer-id "${AUTH_ID}")
  else
    args+=(--authorization-type NONE)
  fi
  printf '    %-40s' "${clave}"
  aws apigatewayv2 create-route "${args[@]}" --query 'AuthorizationType' --output text
}

# BFF: vistas agregadas e identidad
crear_ruta 'GET /api/bff/perfil'                    "${INT_BFF}"
crear_ruta 'GET /api/bff/tablero'                   "${INT_BFF}"

# ms-pedidos
crear_ruta 'GET /api/v1/pedidos'                    "${INT_PEDIDOS}"
crear_ruta 'POST /api/v1/pedidos'                   "${INT_PEDIDOS}"
crear_ruta 'GET /api/v1/pedidos/mis-pedidos'        "${INT_PEDIDOS}"
crear_ruta 'GET /api/v1/pedidos/resumen'            "${INT_PEDIDOS}"
crear_ruta 'GET /api/v1/pedidos/{id}'               "${INT_PEDIDOS}"
crear_ruta 'POST /api/v1/pedidos/{id}/confirmar'    "${INT_PEDIDOS}"
crear_ruta 'PUT /api/v1/pedidos/{id}/estado'        "${INT_PEDIDOS}"
crear_ruta 'PUT /api/v1/pedidos/{id}/anular'        "${INT_PEDIDOS}"

# ms-productos (las rutas internas de stock no se publican)
crear_ruta 'GET /api/v1/productos'                  "${INT_PRODUCTOS}"
crear_ruta 'POST /api/v1/productos'                 "${INT_PRODUCTOS}"
crear_ruta 'GET /api/v1/productos/categorias'       "${INT_PRODUCTOS}"
crear_ruta 'GET /api/v1/productos/{id}'             "${INT_PRODUCTOS}"
crear_ruta 'PUT /api/v1/productos/{id}'             "${INT_PRODUCTOS}"
crear_ruta 'DELETE /api/v1/productos/{id}'          "${INT_PRODUCTOS}"

# ms-clientes
crear_ruta 'GET /api/v1/clientes'                   "${INT_CLIENTES}"
crear_ruta 'POST /api/v1/clientes'                  "${INT_CLIENTES}"
crear_ruta 'GET /api/v1/clientes/mi-ficha'          "${INT_CLIENTES}"
crear_ruta 'GET /api/v1/clientes/{id}'              "${INT_CLIENTES}"
crear_ruta 'PUT /api/v1/clientes/{id}'              "${INT_CLIENTES}"

# ms-notificaciones: avisos y tickets generados al consumir los eventos de RabbitMQ
crear_ruta 'GET /api/v1/notificaciones'                          "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/pedido/{numero}'          "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/documentos'               "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/documentos/pedido/{numero}' "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/auditoria'                "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/auditoria/pedido/{numero}' "${INT_NOTIFICACIONES}"

# ms-mensajeria: administracion de colas, exchanges y bindings
crear_ruta 'GET /api/v1/mensajeria/topologia'                    "${INT_MENSAJERIA}"
crear_ruta 'GET /api/v1/mensajeria/colas'                        "${INT_MENSAJERIA}"
crear_ruta 'POST /api/v1/mensajeria/colas'                       "${INT_MENSAJERIA}"
crear_ruta 'GET /api/v1/mensajeria/colas/{nombre}'               "${INT_MENSAJERIA}"
crear_ruta 'DELETE /api/v1/mensajeria/colas/{nombre}'            "${INT_MENSAJERIA}"
crear_ruta 'POST /api/v1/mensajeria/colas/{nombre}/purgar'       "${INT_MENSAJERIA}"
crear_ruta 'GET /api/v1/mensajeria/exchanges'                    "${INT_MENSAJERIA}"
crear_ruta 'POST /api/v1/mensajeria/exchanges'                   "${INT_MENSAJERIA}"
crear_ruta 'DELETE /api/v1/mensajeria/exchanges/{nombre}'        "${INT_MENSAJERIA}"
crear_ruta 'POST /api/v1/mensajeria/bindings'                    "${INT_MENSAJERIA}"
crear_ruta 'DELETE /api/v1/mensajeria/bindings'                  "${INT_MENSAJERIA}"

# Health check: unica ruta sin token. Se reescribe al actuator del BFF.
crear_ruta 'GET /health'                            "${INT_HEALTH}" no

echo "==> 5/5 Publicando el stage por defecto con auto-deploy"
aws apigatewayv2 create-stage \
  --region "${REGION}" \
  --api-id "${API_ID}" \
  --stage-name '$default' \
  --auto-deploy >/dev/null

URL="$(aws apigatewayv2 get-api --region "${REGION}" --api-id "${API_ID}" \
  --query 'ApiEndpoint' --output text)"

cat <<RESUMEN

==========================================================
 API creado
==========================================================
 ApiId     : ${API_ID}
 Endpoint  : ${URL}
 Issuer    : ${ISSUER}
 Audiencias: [${AUDIENCIAS}]
 Backend   : ${BACKEND_HOST}
 Rutas     : 39 (38 con JWT + /health publico)

 Pruebas rapidas:
   curl -i ${URL}/api/v1/productos            # 401 sin token
   curl -i -H "Authorization: Bearer \$TOKEN" ${URL}/api/v1/productos

 En environment.production.ts:
   apiBaseUrl: '${URL}'
==========================================================
RESUMEN
