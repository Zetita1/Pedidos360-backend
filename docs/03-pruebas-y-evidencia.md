# Pruebas y evidencia para la evaluacion

## Como correr todo

```bash
# Backend: compila los cuatro artefactos y corre las 56 pruebas
cd pedidos360-backend
./mvnw clean verify

# Frontend: compila el bundle y corre las pruebas del guard y los avisos
cd ../pedidos360-frontend
npm ci --legacy-peer-deps
npm run build
npm run test:ci
```

## Que cubre cada prueba

| Prueba | Modulo | Que demuestra |
|---|---|---|
| `JwtValidacionTest` | common-security | audiencia ajena, tenant distinto, scope faltante, token vencido, conversion de `roles`/`scp` a autoridades y derivacion del JWK Set para tenants de trabajo y externos |
| `BffTokenValidacionTest` | bff-service | el BFF valida **firma real** (token RSA firmado en la prueba), issuer, audiencia, scope, vigencia y rol; 401 vs 403 |
| `ProductoApiSecurityTest` | ms-productos | sin token 401, sin rol 403, admin 201, validacion 400, health publico |
| `ProductoRepositoryTest` | ms-productos | persistencia, filtros del catalogo y control de stock |
| `ClienteApiSecurityTest` | ms-clientes | autorizacion por rol, validacion de rut y correo, 404 de `mi-ficha` |
| `PedidoApiTest` | ms-pedidos | alta con precios del catalogo, confirmacion que descuenta stock, anulacion que lo repone, transiciones invalidas |
| `PedidoDominioTest` | ms-pedidos | calculo de neto/IVA/total y maquina de estados |
| `role.guard.spec.ts` | frontend | el guard deja pasar con rol, redirige a `/sin-permisos` sin el, y manda cada rol a su inicio |
| `notificaciones.service.spec.ts` | frontend | cola de avisos al usuario |

## Evidencia manual sugerida para la presentacion

### 1. Login y obtencion de tokens

1. Abrir `http://localhost:4200` -> redirige a `/login`.
2. Pulsar **Ingresar con Microsoft Entra ID** -> redirect al IDaaS -> vuelta al SPA.
3. Ir a **Perfil**: se ven los claims del token y lo que resuelve el BFF del mismo token.
4. En DevTools -> Network, revisar cualquier llamada a `/api/bff/...`: debe llevar la
   cabecera `Authorization: Bearer eyJ...` puesta por el `MsalInterceptor`.

### 2. El backend rechaza tokens invalidos

```bash
# Sin token -> 401 + WWW-Authenticate
curl -i http://localhost:8082/api/v1/productos

# Token inventado -> 401 (falla la firma contra el JWKS del IDaaS)
curl -i -H "Authorization: Bearer token.falso.aqui" http://localhost:8082/api/v1/productos

# Token valido pero sin el rol del endpoint -> 403 con Problem Detail
curl -i -H "Authorization: Bearer $TOKEN_CLIENTE" http://localhost:8083/api/v1/clientes

# Health sin token -> 200 (ruta publica declarada)
curl -i http://localhost:8080/actuator/health
```

Respuesta esperada del 401:

```json
{
  "type": "https://pedidos360.cl/errors/token-invalido",
  "title": "Token no valido",
  "status": 401,
  "detail": "La audiencia del token no corresponde a este recurso...",
  "instance": "/api/v1/productos",
  "error": "invalid_token",
  "timestamp": "2026-09-20T10:15:00-03:00"
}
```

### 3. Autorizacion por rol en la interfaz

| Usuario de prueba | Rol asignado | Que deberia ver |
|---|---|---|
| `admin@tu-tenant` | `Pedidos.Admin` | Tablero, Pedidos, Clientes, Catalogo con boton *Nuevo producto* y *Anular* |
| `vendedor@tu-tenant` | `Pedidos.Vendedor` | Lo mismo sin *Nuevo producto* ni *Anular* |
| `cliente@tu-tenant` | `Pedidos.Cliente` | Solo Catalogo, Mis pedidos y Perfil; `/pedidos` redirige a `/sin-permisos` |
| Usuario sin rol | ninguno | Entra, pero toda la API responde 403 |

### 4. Reglas de negocio

- Confirmar un pedido descuenta el stock en `ms-productos` (ver el catalogo antes y despues).
- Anular un pedido confirmado repone ese stock.
- Pedir mas unidades que el stock disponible devuelve **409** con el mensaje del producto.
- Saltarse un estado (por ejemplo BORRADOR -> ENTREGADO) devuelve **409**.

## Mapeo con la pauta de evaluacion

| Indicador | Donde esta resuelto |
|---|---|
| MSAL configurado y operativo, login y logout funcionan | `core/auth/msal.config.ts`, `auth.service.ts`, `login.component.ts` |
| Guards y MsalInterceptor sin fallas | `app.routes.ts` (MsalGuard + `rolGuard`), `app.config.ts` (MsalInterceptor por DI) |
| Se obtienen los tokens para consumir el API Gateway | `protectedResourceMap` en `msal.config.ts` apuntando a `environment.apiBaseUrl` |
| Se leen roles y scopes desde los claims | `auth.service.ts` (`roles`, `scopes`), vista **Perfil** |
| El BFF valida issuer y audience | `Pedidos360SecurityAutoConfiguration.tokenValidator` + `AudienceValidator` |
| Verifica firma y vigencia del token | `NimbusJwtDecoder.withJwkSetUri(...)` + `JwtTimestampValidator` |
| Autorizacion por rol | `@PreAuthorize` con `SecurityRoles` en cada controlador |
| Codigos de error adecuados | `Pedidos360AuthenticationEntryPoint` (401), `Pedidos360AccessDeniedHandler` (403), `ApiExceptionHandler` (400/404/409/502) |
| Integracion con base de datos cloud | entidades JPA, repositorios Spring Data, perfil `aws` con RDS y migraciones Flyway |
| `.gitignore` por tecnologia | `pedidos360-backend/.gitignore`, `pedidos360-frontend/.gitignore` |

Los indicadores de la **EP2** (configuracion en la nube) se cubren en
[`04-aws-api-gateway.md`](04-aws-api-gateway.md) y [`01-registro-entra-id.md`](01-registro-entra-id.md);
el recorrido de la defensa esta en [`05-guion-presentacion.md`](05-guion-presentacion.md).
