#!/usr/bin/env bash
#
# Compila el SPA de Angular y lo publica en AWS Amplify Hosting (despliegue manual, sin
# conectar un repositorio). Amplify entrega HTTPS, que Entra External ID exige en toda
# redirect URI que no sea localhost.
#
# La app ya existe (se creo una vez con create-app + create-branch, ver docs/06). Este
# script solo sube una version nueva.
#
# Uso:  ./infra/amplify/publicar-frontend.sh
set -euo pipefail
export AWS_PAGER="" AWS_DEFAULT_REGION="${AWS_REGION:-us-east-1}"

APP_ID="${AMPLIFY_APP_ID:-d1pem9n2nc75cz}"
RAMA="${AMPLIFY_RAMA:-main}"
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
FRONT="${RAIZ}/pedidos360-frontend"
ZIP="$(mktemp -d)/spa.zip"
if command -v cygpath >/dev/null 2>&1; then ZIP="$(cygpath -m "${ZIP}")"; fi

echo "==> 1/4 Compilando el SPA (configuracion production)"
npm --prefix "${FRONT}" run build >/dev/null

echo "==> 2/4 Empaquetando dist/"
python - "${FRONT}/dist/pedidos360-frontend/browser" "${ZIP}" <<'PY'
import os, sys, zipfile
origen, destino = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(destino, 'w', zipfile.ZIP_DEFLATED) as z:
    for raiz, _, archivos in os.walk(origen):
        for a in archivos:
            ruta = os.path.join(raiz, a)
            z.write(ruta, os.path.relpath(ruta, origen).replace(os.sep, '/'))
PY

echo "==> 3/4 Subiendo a Amplify"
read -r JOB URL < <(aws amplify create-deployment --app-id "${APP_ID}" --branch-name "${RAMA}" \
  --query '[jobId,zipUploadUrl]' --output text)
# curl de Git Bash falla con URLs prefirmadas tan largas; Python sube el zip sin problemas.
python - "${URL}" "${ZIP}" <<'PY'
import sys, urllib.request
url, zip_ = sys.argv[1], sys.argv[2]
req = urllib.request.Request(url, data=open(zip_, 'rb').read(), method='PUT',
                             headers={'Content-Type': 'application/zip'})
print('    subida HTTP', urllib.request.urlopen(req).status)
PY
aws amplify start-deployment --app-id "${APP_ID}" --branch-name "${RAMA}" --job-id "${JOB}" >/dev/null

echo "==> 4/4 Esperando el despliegue (job ${JOB})"
for _ in $(seq 1 40); do
  ESTADO="$(aws amplify get-job --app-id "${APP_ID}" --branch-name "${RAMA}" --job-id "${JOB}" \
    --query 'job.summary.status' --output text)"
  [[ "${ESTADO}" == SUCCEED || "${ESTADO}" == FAILED ]] && break
  sleep 5
done
echo "    ${ESTADO}: https://${RAMA}.${APP_ID}.amplifyapp.com"
