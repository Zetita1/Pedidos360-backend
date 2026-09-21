# Configuracion del API Manager: AWS API Gateway

Esta guia cubre los indicadores de AWS de la EP2 (40% de la nota):

| Indicador | Peso | Seccion |
|---|---|---|
| Validacion JWT en todas las rutas | 20% | [3](#3-crear-el-autorizador-jwt) y [6](#6-probar-la-validacion-jwt) |
| Rutas hacia el backend | 13% | [4](#4-crear-las-integraciones-y-las-rutas) |
| CORS | 7% | [5](#5-configurar-cors) |

Usamos **HTTP API** (API Gateway v2), no REST API: el HTTP API trae un **autorizador JWT
nativo** que valida issuer, audience, firma y vigencia sin escribir una sola linea de
codigo. Con REST API habria que montar un Lambda authorizer.

---

## 0. Antes de empezar

Necesitas el backend corriendo en EC2 y estos datos a mano:

```bash
export AWS_REGION=us-east-1
export ENTRA_TENANT_SUBDOMINIO=pedidos360
export ENTRA_TENANT_ID=<directory-tenant-id>
export API_AUDIENCE=api://pedidos360-api
export BACKEND_HOST=<dns-publico-de-tu-ec2>
export ORIGEN_SPA=https://<donde-publicas-el-spa>
```

Comprueba que los cuatro servicios responden en la instancia:

```bash
for p in 8080 8081 8082 8083; do curl -s -o /dev/null -w "$p -> %{http_code}\n" http://$BACKEND_HOST:$p/actuator/health; done
```

Deben dar `200`. El security group tiene que permitir entrada a 8080-8083.

> **Nota de seguridad, buena para mencionar en la presentacion.** En esta configuracion el
> EC2 queda accesible desde internet, porque los HTTP API no tienen IP fija para filtrar en
> el security group. Lo correcto en produccion es un **VPC Link** contra un ALB privado. Que
> sea aceptable para la demo se apoya en que **el backend tambien valida el JWT**: aunque
> alguien llegue directo al puerto 8082, sin token valido recibe 401.

---

## 1. Dos caminos: importar o crear paso a paso

**Camino rapido** — importar la definicion completa:

```bash
cd infra/api-gateway
envsubst < pedidos360-http-api.yaml > /tmp/api.yaml
aws apigatewayv2 import-api --body file:///tmp/api.yaml --region "$AWS_REGION"
```

**Camino demostrable** — el script crea cada pieza por separado, que es lo que conviene
mostrar en la presentacion:

```bash
cd infra/api-gateway
./crear-api-gateway.sh
```

Las secciones siguientes explican que hace cada paso, para que puedas hacerlo tambien desde
la consola web y responder preguntas sobre el.

---

## 2. Crear el HTTP API

Consola: **API Gateway → Create API → HTTP API → Build**.

- API name: `pedidos360-api`
- No agregues integraciones todavia.
- Stage: `$default` con **Auto-deploy** activado (asi cada cambio queda publicado solo).

CLI equivalente:

```bash
aws apigatewayv2 create-api --name pedidos360-api --protocol-type HTTP
```

---

## 3. Crear el autorizador JWT

Consola: **Authorization → Manage authorizers → Create → JWT**.

| Campo | Valor |
|---|---|
| Name | `entra-external-id` |
| Identity source | `$request.header.Authorization` |
| Issuer URL | el `issuer` exacto del documento de descubrimiento |
| Audience | `api://pedidos360-api` |

El issuer tiene que ser **identico** al claim `iss` del token, incluido el `/v2.0` final.
Obtenlo asi:

```bash
curl -s https://$ENTRA_TENANT_SUBDOMINIO.ciamlogin.com/$ENTRA_TENANT_ID/v2.0/.well-known/openid-configuration | jq -r .issuer
```

Con eso el gateway valida por si solo, en cada peticion:

- la **firma** del token contra el JWKS del tenant,
- el **issuer** (que lo emitio tu tenant y no otro),
- la **audiencia** (que el token era para esta API y no para otra app),
- la **vigencia** (`exp` / `nbf`).

Si algo falla responde **401** antes de tocar el backend.

---

## 4. Crear las integraciones y las rutas

Se crean **cuatro integraciones**, una por servicio, y luego cada ruta apunta a la suya:

| Integracion | Destino | Tipo |
|---|---|---|
| BFF | `http://$BACKEND_HOST:8080/{proxy}` | HTTP_PROXY |
| Pedidos | `http://$BACKEND_HOST:8081/{proxy}` | HTTP_PROXY |
| Productos | `http://$BACKEND_HOST:8082/{proxy}` | HTTP_PROXY |
| Clientes | `http://$BACKEND_HOST:8083/{proxy}` | HTTP_PROXY |

Y estas son las **22 rutas**. Todas con *Authorization: JWT* salvo `/health`:

| Ruta | Servicio | Para que |
|---|---|---|
| `GET /api/bff/perfil` | BFF | identidad y permisos del usuario |
| `GET /api/bff/tablero` | BFF | indicadores agregados |
| `GET /api/v1/pedidos` | pedidos | listado con filtros |
| `POST /api/v1/pedidos` | pedidos | registrar pedido |
| `GET /api/v1/pedidos/mis-pedidos` | pedidos | pedidos del usuario del token |
| `GET /api/v1/pedidos/resumen` | pedidos | conteo por estado |
| `GET /api/v1/pedidos/{id}` | pedidos | detalle |
| `POST /api/v1/pedidos/{id}/confirmar` | pedidos | confirmar y descontar stock |
| `PUT /api/v1/pedidos/{id}/estado` | pedidos | avanzar estado |
| `PUT /api/v1/pedidos/{id}/anular` | pedidos | anular y reponer stock |
| `GET /api/v1/productos` | productos | catalogo |
| `POST /api/v1/productos` | productos | crear producto |
| `GET /api/v1/productos/categorias` | productos | categorias |
| `GET /api/v1/productos/{id}` | productos | detalle |
| `PUT /api/v1/productos/{id}` | productos | actualizar |
| `DELETE /api/v1/productos/{id}` | productos | dar de baja |
| `GET /api/v1/clientes` | clientes | cartera |
| `POST /api/v1/clientes` | clientes | crear ficha |
| `GET /api/v1/clientes/mi-ficha` | clientes | ficha del usuario del token |
| `GET /api/v1/clientes/{id}` | clientes | detalle |
| `PUT /api/v1/clientes/{id}` | clientes | actualizar |
| `GET /health` | BFF | health check **sin token** |

Dos decisiones que conviene poder justificar:

- **Cada recurso va a su microservicio.** El gateway no manda todo al BFF: `/api/v1/pedidos`
  llega a `ms-pedidos`, `/api/v1/productos` a `ms-productos`. El BFF queda solo para lo que
  es suyo — las vistas agregadas (`tablero`) y la identidad (`perfil`).
- **Las rutas internas de stock no se publican.** `POST /api/v1/productos/{id}/stock/...`
  solo lo llama `ms-pedidos` dentro de la red; exponerlo permitiria a un cliente alterar
  inventario sin pasar por un pedido.

---

## 5. Configurar CORS

Consola: **CORS → Configure**.

| Campo | Valor | Por que |
|---|---|---|
| Access-Control-Allow-Origin | `https://tu-spa` y `http://localhost:4200` | solo tus origenes, nunca `*` |
| Access-Control-Allow-Methods | `GET, POST, PUT, DELETE, OPTIONS` | solo los que usa el SPA |
| Access-Control-Allow-Headers | `authorization, content-type, x-correlation-id` | `authorization` es imprescindible |
| Access-Control-Expose-Headers | `x-correlation-id` | para poder trazar desde el navegador |
| Access-Control-Max-Age | `1800` | cachea el preflight y ahorra llamadas |
| Allow credentials | desactivado | el token va en la cabecera, no en cookies |

La rubrica penaliza "permite mas elementos de los necesarios": `*` en origen o en headers
baja la nota aunque funcione.

Comprobacion del preflight:

```bash
curl -i -X OPTIONS "$URL/api/v1/productos" \
  -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization"
```

Debe responder `204` con `access-control-allow-origin: http://localhost:4200`. Repite con
un `Origin` inventado: **no** debe aparecer la cabecera de allow-origin.

---

## 6. Probar la validacion JWT

Consigue un token real: inicia sesion en el SPA, abre DevTools → Network, toma una llamada
a la API y copia el valor de `Authorization` (sin el prefijo `Bearer `).

```bash
export URL=https://<api-id>.execute-api.$AWS_REGION.amazonaws.com
export TOKEN=<pega-el-access-token>
```

| Caso | Comando | Esperado |
|---|---|---|
| Ruta publica | `curl -i $URL/health` | **200** |
| Sin token | `curl -i $URL/api/v1/productos` | **401** del gateway |
| Token corrupto | `curl -i -H "Authorization: Bearer abc.def.ghi" $URL/api/v1/productos` | **401** |
| Token valido | `curl -i -H "Authorization: Bearer $TOKEN" $URL/api/v1/productos` | **200** + JSON del catalogo |
| Rol insuficiente | con el token del usuario `Pedidos.Cliente`: `curl -i -X POST $URL/api/v1/productos -H "Authorization: Bearer $TOKEN_CLIENTE" -H 'Content-Type: application/json' -d '{"sku":"X-1","nombre":"X","precio":1000,"stock":1,"categoria":"X"}'` | **403** del backend |

Ese ultimo caso es el mas interesante de explicar: el **401 lo pone el gateway** (el token
no sirve) y el **403 lo pone el microservicio** (el token sirve, pero el rol no alcanza). El
gateway valida *identidad*; la *autorizacion* por rol es decision del backend.

Un script que recorre las 22 rutas con y sin token esta en
[`infra/api-gateway/probar-rutas.sh`](../infra/api-gateway/probar-rutas.sh) — sirve como
evidencia para el indicador del 15%.

---

## 7. Apuntar el frontend al gateway

En `pedidos360-frontend/src/environments/environment.production.ts`:

```ts
apiBaseUrl: 'https://<api-id>.execute-api.us-east-1.amazonaws.com',
```

Las rutas (`/api/v1/pedidos`, `/api/bff/tablero`, …) ya estan definidas en `environment.rutas`
y son **las mismas en local y en la nube**: en desarrollo las resuelve
`proxy.conf.json` contra cada puerto, y en produccion las resuelve el API Gateway. Por eso
el codigo del SPA no cambia entre ambos entornos.

Reconstruye y vuelve a publicar:

```bash
npm run build
aws s3 sync dist/pedidos360-frontend/browser s3://<tu-bucket> --delete
```

---

## 8. Errores frecuentes

| Sintoma | Causa habitual |
|---|---|
| Siempre 401 aun con token valido | el issuer del autorizador no calza exacto con el claim `iss` (falta `/v2.0`, o usaste `login.microsoftonline.com`) |
| 401 con mensaje de audiencia | el SPA pidio scopes de otra app: revisa `apiScopes` |
| 500 o 503 desde el gateway | el security group del EC2 no deja entrar a 8080-8083 |
| El navegador bloquea por CORS | falta `authorization` en allow-headers, o el origen no esta en la lista |
| 404 en rutas con `{id}` | la integracion no reenvia el path: revisa `overwrite:path` |
| El preflight OPTIONS devuelve 401 | no pongas el autorizador JWT en las rutas OPTIONS; el CORS del HTTP API las maneja solo |
