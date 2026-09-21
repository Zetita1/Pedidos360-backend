# Evidencia del API Gateway

Ejecución real contra AWS el **21-09-2026 02:54**, con un access token emitido por
Microsoft Entra External ID para el usuario `sofi.troncoso@duocuc.cl` (rol `Pedidos.Admin`).

| | |
|---|---|
| API Gateway | `https://j9w6eomnw5.execute-api.us-east-1.amazonaws.com` |
| Backend | EC2 `i-0f8db5d98822339d6` · IP elástica `54.156.41.241` |
| Verificaciones | **31 de 31 correctas** |
| Rutas distintas ejercitadas | **22 de 22** |

## Claims del token utilizado

El token en sí no se incluye; estos son sus claims decodificados.

| Claim | Valor | Lo valida |
|---|---|---|
| `iss` | `https://9d265688-dbb5-4a46-9b9a-8f6765928aa0.ciamlogin.com/9d265688-dbb5-4a46-9b9a-8f6765928aa0/v2.0` | API Gateway y backend |
| `aud` | `e25c9a50-e056-4401-a2ef-f2d8fdefb77b` (client ID de `pedidos360-api`) | API Gateway y backend |
| `ver` | `2.0` | — |
| `azp` | `4763762f-79ea-47ad-9966-bda5e049df68` (el SPA que lo pidió) | — |
| `roles` | `["Pedidos.Admin"]` | backend (`@PreAuthorize`) |
| `scp` | `Pedidos.Read Pedidos.Write` | backend |

> **Hallazgo:** Entra emitió el `aud` como el *client ID* de la API, no como el App ID URI
> `api://pedidos360-api`. Por eso el authorizer y el backend aceptan ambos identificadores de la
> misma API. Con solo el App ID URI configurado, todas las llamadas habrían respondido 401.

## Resultado por ruta


### Ruta publica

| Método | Ruta | Esperado | Obtenido | |
|---|---|:---:|:---:|:---:|
| `GET` | `/health` | 200 | 200 | ✅ |

### Sin token: el API Gateway debe cortar con 401

| Método | Ruta | Esperado | Obtenido | |
|---|---|:---:|:---:|:---:|
| `GET` | `/api/bff/perfil` | 401 | 401 | ✅ |
| `GET` | `/api/bff/tablero` | 401 | 401 | ✅ |
| `GET` | `/api/v1/pedidos` | 401 | 401 | ✅ |
| `GET` | `/api/v1/productos` | 401 | 401 | ✅ |
| `GET` | `/api/v1/clientes` | 401 | 401 | ✅ |

### Token evidentemente invalido: 401

| Método | Ruta | Esperado | Obtenido | |
|---|---|:---:|:---:|:---:|
| `GET` | `/api/v1/productos` | 401 | 401 | ✅ |

### Con token valido: cada ruta llega a su microservicio

| Método | Ruta | Esperado | Obtenido | |
|---|---|:---:|:---:|:---:|
| `GET` | `/api/bff/perfil` | 200 | 200 | ✅ |
| `GET` | `/api/bff/tablero` | 200 | 200 | ✅ |
| `GET` | `/api/v1/pedidos` | 200 | 200 | ✅ |
| `GET` | `/api/v1/pedidos/mis-pedidos` | 200 | 200 | ✅ |
| `GET` | `/api/v1/pedidos/resumen` | 200 | 200 | ✅ |
| `GET` | `/api/v1/productos` | 200 | 200 | ✅ |
| `GET` | `/api/v1/productos/categorias` | 200 | 200 | ✅ |
| `GET` | `/api/v1/clientes` | 200 | 200 | ✅ |
| `GET` | `/api/v1/clientes/1` | 200 | 200 | ✅ |
| `GET` | `/api/v1/productos/999999` | 404 | 404 | ✅ |

### Escritura con rol Pedidos.Admin

| Método | Ruta | Esperado | Obtenido | |
|---|---|:---:|:---:|:---:|
| `POST` | `/api/v1/productos` | 201 | 201 | ✅ |

### Productos

| Método | Ruta | Esperado | Obtenido | |
|---|---|:---:|:---:|:---:|
| `GET` | `/api/v1/productos/1` | 200 | 200 | ✅ |
| `POST` | `/api/v1/productos` | 201 | 201 | ✅ |
| `PUT` | `/api/v1/productos/8` | 200 | 200 | ✅ |
| `DELETE` | `/api/v1/productos/8` | 204 | 204 | ✅ |

### Clientes

| Método | Ruta | Esperado | Obtenido | |
|---|---|:---:|:---:|:---:|
| `POST` | `/api/v1/clientes` | 201 | 201 | ✅ |
| `PUT` | `/api/v1/clientes/4` | 200 | 200 | ✅ |
| `GET` | `/api/v1/clientes/mi-ficha` | 404 | 404 | ✅ |

### Pedidos (ms-pedidos llama a ms-clientes y ms-productos reenviando el token)

| Método | Ruta | Esperado | Obtenido | |
|---|---|:---:|:---:|:---:|
| `POST` | `/api/v1/pedidos` | 201 | 201 | ✅ |
| `GET` | `/api/v1/pedidos/2` | 200 | 200 | ✅ |
| `POST` | `/api/v1/pedidos/2/confirmar` | 200 | 200 | ✅ |
| `PUT` | `/api/v1/pedidos/2/estado` | 200 | 200 | ✅ |
| `PUT` | `/api/v1/pedidos/2/estado` | 409 | 409 | ✅ |
| `PUT` | `/api/v1/pedidos/2/anular?motivo=evidencia` | 200 | 200 | ✅ |


## Qué demuestra cada código

| Código | Quién responde | Significado |
|---|---|---|
| **401** sin token | **API Gateway** (`{"message":"Unauthorized"}`) | El authorizer JWT corta la petición antes de llegar al backend |
| **200 / 201 / 204** | Microservicio correspondiente | El token pasó la validación del gateway **y** la del backend |
| **403** | Microservicio | El token es válido, pero no trae el rol que exige el endpoint (ver captura del usuario sin rol) |
| **404** | Microservicio | El recurso no existe; la ruta sí está publicada |
| **409** | ms-pedidos | Regla de negocio: transición de estado inválida |

`POST /api/v1/pedidos` respondiendo **201** es la prueba más completa: ms-pedidos recibió el token
del gateway, lo validó, y lo reenvió a ms-clientes y ms-productos, que lo volvieron a validar.
Ninguna llamada interna es anónima.

## Cómo reproducirlo

```bash
export URL=https://j9w6eomnw5.execute-api.us-east-1.amazonaws.com
export TOKEN=<access token desde DevTools>
./infra/api-gateway/probar-rutas.sh
```
