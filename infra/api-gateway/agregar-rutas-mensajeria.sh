#!/usr/bin/env bash
#
# Agrega al API Gateway existente las rutas de los microservicios de la EP3:
# ms-notificaciones (8084) y ms-mensajeria (8085).
#
# No recrea el API: reutiliza su autorizador JWT y su CORS, de modo que las rutas nuevas
# quedan protegidas igual que las anteriores.
#
# Uso:
#   export API_ID=j9w6eomnw5
#   export BACKEND_HOST=54.156.41.241
#   ./agregar-rutas-mensajeria.sh
set -euo pipefail
export AWS_PAGER="" MSYS_NO_PATHCONV=1

API_ID="${API_ID:?Falta API_ID}"
BACKEND_HOST="${BACKEND_HOST:?Falta BACKEND_HOST}"
REGION="${AWS_REGION:-us-east-1}"

echo "==> 1/4 Buscando el autorizador JWT del API ${API_ID}"
AUTH_ID="$(aws apigatewayv2 get-authorizers --region "${REGION}" --api-id "${API_ID}" \
  --query 'Items[?Name==`entra-external-id`].AuthorizerId | [0]' --output text)"
if [[ -z "${AUTH_ID}" || "${AUTH_ID}" == "None" ]]; then
  echo "No se encontro el autorizador entra-external-id en el API ${API_ID}" >&2
  exit 1
fi
echo "    AuthorizerId=${AUTH_ID}"

echo "==> 2/4 Creando una integracion por microservicio"
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

INT_NOTIFICACIONES="$(crear_integracion 8084)"
INT_MENSAJERIA="$(crear_integracion 8085)"
echo "    notificaciones=${INT_NOTIFICACIONES} mensajeria=${INT_MENSAJERIA}"

echo "==> 3/4 Creando las rutas (todas con validacion JWT)"
crear_ruta() {
  local clave="$1" integracion="$2"
  printf '    %-52s' "${clave}"
  aws apigatewayv2 create-route \
    --region "${REGION}" \
    --api-id "${API_ID}" \
    --route-key "${clave}" \
    --target "integrations/${integracion}" \
    --authorization-type JWT \
    --authorizer-id "${AUTH_ID}" \
    --query 'AuthorizationType' --output text
}

# ms-notificaciones: consulta de lo que generaron los consumidores
crear_ruta 'GET /api/v1/notificaciones'                          "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/pedido/{numero}'          "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/documentos'               "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/documentos/pedido/{numero}' "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/auditoria'                "${INT_NOTIFICACIONES}"
crear_ruta 'GET /api/v1/notificaciones/auditoria/pedido/{numero}' "${INT_NOTIFICACIONES}"

# ms-mensajeria: administracion del broker
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

echo "==> 4/4 Resumen"
TOTAL="$(aws apigatewayv2 get-routes --region "${REGION}" --api-id "${API_ID}" \
  --query 'length(Items)' --output text)"
URL="$(aws apigatewayv2 get-api --region "${REGION}" --api-id "${API_ID}" \
  --query 'ApiEndpoint' --output text)"

cat <<RESUMEN

==========================================================
 Rutas de mensajeria agregadas
==========================================================
 ApiId     : ${API_ID}
 Endpoint  : ${URL}
 Rutas     : 17 nuevas (${TOTAL} en total)
 Backend   : ${BACKEND_HOST} (8084 y 8085)

 Con auto-deploy en el stage \$default quedan publicadas al instante.

 Prueba rapida:
   curl -i ${URL}/api/v1/notificaciones          # 401 sin token
   curl -i -H "Authorization: Bearer \$TOKEN" ${URL}/api/v1/mensajeria/topologia
==========================================================
RESUMEN
