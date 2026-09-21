#!/usr/bin/env bash
#
# Recorre todas las rutas publicadas en el API Gateway y compara el codigo de respuesta
# con el esperado, con y sin token. Genera la evidencia del indicador "Evidencia el
# funcionamiento de cada ruta" (15%).
#
# Uso:
#   export URL=https://<api-id>.execute-api.us-east-1.amazonaws.com
#   export TOKEN=<access token de un usuario con rol Pedidos.Admin>
#   export TOKEN_CLIENTE=<access token de un usuario con rol Pedidos.Cliente>   # opcional
#   ./probar-rutas.sh
#
# Tambien sirve contra el entorno local apuntando URL a http://localhost:8080 (BFF) o al
# puerto del microservicio que quieras revisar.
set -uo pipefail

: "${URL:?Falta URL, por ejemplo https://abc123.execute-api.us-east-1.amazonaws.com}"
: "${TOKEN:?Falta TOKEN con el access token}"
TOKEN_CLIENTE="${TOKEN_CLIENTE:-}"

verde=$'\e[32m'; rojo=$'\e[31m'; gris=$'\e[90m'; fin=$'\e[0m'
ok=0; fallidas=0

# probar <esperado> <metodo> <ruta> [token] [cuerpo]
probar() {
  local esperado="$1" metodo="$2" ruta="$3" token="${4:-}" cuerpo="${5:-}"
  local args=(-s -o /dev/null -w '%{http_code}' -X "${metodo}" "${URL}${ruta}" -m 20)

  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  [[ -n "${cuerpo}" ]] && args+=(-H 'Content-Type: application/json' -d "${cuerpo}")

  local obtenido
  obtenido="$(curl "${args[@]}")"

  if [[ "${obtenido}" == "${esperado}" ]]; then
    printf '%s  OK  %s %-42s %s esperado %s%s\n' "${verde}" "${metodo}" "${ruta}" "${obtenido}" "${esperado}" "${fin}"
    ok=$((ok + 1))
  else
    printf '%s FAIL %s %-42s %s esperado %s%s\n' "${rojo}" "${metodo}" "${ruta}" "${obtenido}" "${esperado}" "${fin}"
    fallidas=$((fallidas + 1))
  fi
}

echo "${gris}== Ruta publica ==${fin}"
probar 200 GET /health

echo "${gris}== Sin token: el API Gateway debe cortar con 401 ==${fin}"
probar 401 GET /api/bff/perfil
probar 401 GET /api/bff/tablero
probar 401 GET /api/v1/pedidos
probar 401 GET /api/v1/productos
probar 401 GET /api/v1/clientes

echo "${gris}== Token evidentemente invalido: 401 ==${fin}"
probar 401 GET /api/v1/productos "abc.def.ghi"

echo "${gris}== Con token valido: cada ruta llega a su microservicio ==${fin}"
probar 200 GET /api/bff/perfil                 "${TOKEN}"
probar 200 GET /api/bff/tablero                "${TOKEN}"
probar 200 GET /api/v1/pedidos                 "${TOKEN}"
probar 200 GET /api/v1/pedidos/mis-pedidos     "${TOKEN}"
probar 200 GET /api/v1/pedidos/resumen         "${TOKEN}"
probar 200 GET /api/v1/productos               "${TOKEN}"
probar 200 GET /api/v1/productos/categorias    "${TOKEN}"
probar 200 GET /api/v1/clientes                "${TOKEN}"
probar 200 GET /api/v1/clientes/1              "${TOKEN}"
probar 404 GET /api/v1/productos/999999        "${TOKEN}"

echo "${gris}== Escritura con rol Pedidos.Admin ==${fin}"
SKU="EVID-$(date +%s)"
probar 201 POST /api/v1/productos "${TOKEN}" \
  "{\"sku\":\"${SKU}\",\"nombre\":\"Producto de evidencia\",\"precio\":9990,\"stock\":5,\"categoria\":\"Pruebas\"}"

if [[ -n "${TOKEN_CLIENTE}" ]]; then
  echo "${gris}== Rol insuficiente: el backend responde 403 ==${fin}"
  probar 403 GET  /api/v1/clientes    "${TOKEN_CLIENTE}"
  probar 403 POST /api/v1/productos   "${TOKEN_CLIENTE}" \
    '{"sku":"NO-DEBE","nombre":"No permitido","precio":1000,"stock":1,"categoria":"Pruebas"}'
  echo "${gris}== Pero sus propias rutas si funcionan ==${fin}"
  probar 200 GET /api/v1/productos            "${TOKEN_CLIENTE}"
  probar 200 GET /api/v1/pedidos/mis-pedidos  "${TOKEN_CLIENTE}"
else
  echo "${gris}(define TOKEN_CLIENTE para probar tambien los 403 por rol)${fin}"
fi

echo
echo "Resultado: ${ok} correctas, ${fallidas} con diferencia."
[[ "${fallidas}" -eq 0 ]]
