# Pedidos360 — Backend

Microservicios Spring Boot del sistema Pedidos360, protegidos con JWT emitido por
**Microsoft Entra External ID** y pensados para correr en instancias **EC2** detras de
**AWS API Gateway**.

> DSY1107 Desarrollo Cloud Native I — Evaluaciones Parciales 1, 2 y 3

## Modulos

| Modulo | Puerto | Que hace |
|---|---|---|
| `pedidos360-common-security` | — | libreria compartida: validacion del JWT, roles, CORS y errores Problem Detail |
| `pedidos360-common-messaging` | — | libreria compartida: topologia de RabbitMQ, publicador de eventos y politica de ACK/DLQ |
| `bff-service` | 8080 | Backend For Frontend: vistas agregadas (`tablero`) e identidad (`perfil`) |
| `ms-pedidos` | 8081 | pedidos, detalle, totales y maquina de estados |
| `ms-productos` | 8082 | catalogo y control de stock |
| `ms-clientes` | 8083 | fichas de clientes y direcciones de despacho |
| `ms-notificaciones` | 8084 | consume los eventos de RabbitMQ: avisos al cliente y tickets de pedidos |
| `ms-mensajeria` | 8085 | administrador del broker: crea y elimina colas, exchanges y bindings |

## Requisitos

- JDK 17 (probado con Amazon Corretto / Oracle JDK 17.0.12)
- No hace falta instalar Maven: el repositorio trae el wrapper (`./mvnw`)

## Compilar y probar

```bash
./mvnw clean verify
```

Esto compila los seis artefactos ejecutables y corre la bateria completa de pruebas
(validacion de token, autorizacion por rol, persistencia, reglas de negocio y la politica de
confirmacion de mensajes: ACK, reintento y DLQ).

## Ejecutar en local

El perfil por defecto es `local`: **H2 en memoria** con datos de ejemplo, sin dependencias
externas. Cada servicio en una terminal:

Primero el broker, que si es una dependencia real:

```bash
docker compose -f infra/local/docker-compose.yml up -d rabbitmq   # consola en :15672
```

Luego cada servicio en una terminal:

```bash
./mvnw -pl ms-productos      spring-boot:run   # http://localhost:8082
./mvnw -pl ms-clientes       spring-boot:run   # http://localhost:8083
./mvnw -pl ms-pedidos        spring-boot:run   # http://localhost:8081
./mvnw -pl bff-service       spring-boot:run   # http://localhost:8080
./mvnw -pl ms-notificaciones spring-boot:run   # http://localhost:8084
./mvnw -pl ms-mensajeria     spring-boot:run   # http://localhost:8085
```

La mensajeria (colas, eventos, DLQ y el API del administrador) esta documentada en
[`docs/08-mensajeria-rabbitmq.md`](docs/08-mensajeria-rabbitmq.md).

Documentacion interactiva de cada servicio en `/swagger-ui.html`.

Aunque la base sea local, **los tokens se validan de verdad** contra el JWKS del tenant
configurado. Para que el login funcione hay que exportar los datos del IDaaS:

```bash
export ENTRA_TENANT_ID=<tu-tenant-id>
export ENTRA_ISSUER=https://<subdominio>.ciamlogin.com/$ENTRA_TENANT_ID/v2.0
export ENTRA_API_AUDIENCE=api://pedidos360-api
```

El issuer exacto se lee del documento de descubrimiento del tenant:

```bash
curl -s https://<subdominio>.ciamlogin.com/<tenant-id>/v2.0/.well-known/openid-configuration | jq -r .issuer
```

## Configuracion

Todo se parametriza por variables de entorno; el repositorio no contiene ningun secreto.

| Variable | Descripcion | Ejemplo |
|---|---|---|
| `ENTRA_ISSUER` | issuer v2.0 del tenant | `https://<subdominio>.ciamlogin.com/<tid>/v2.0` |
| `ENTRA_JWKS_URI` | JWK Set (opcional; se deriva del issuer) | `https://<subdominio>.ciamlogin.com/<tid>/discovery/v2.0/keys` |
| `ENTRA_TENANT_ID` | tenant aceptado (claim `tid`) | `2222...` |
| `RABBITMQ_HOST` | host del broker | `localhost` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | credenciales del broker | `pedidos360` |
| `MENSAJERIA_EXCHANGE` | exchange de eventos | `pedidos360.eventos` |
| `MENSAJERIA_MAX_INTENTOS` | intentos antes de la DLQ | `3` |
| `ENTRA_API_AUDIENCE` | audiencia aceptada (claim `aud`) | `api://pedidos360-api` |
| `ENTRA_REQUIRED_SCOPES` | scopes minimos (claim `scp`) | `Pedidos.Read` |
| `CORS_ALLOWED_ORIGINS` | origen del SPA | `http://localhost:4200` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | conexion a PostgreSQL (perfil `aws`) | — |
| `MS_PEDIDOS_URL` / `MS_PRODUCTOS_URL` / `MS_CLIENTES_URL` | ubicacion de los microservicios | `http://localhost:8081` |

## Perfiles

| Perfil | Base de datos | Esquema |
|---|---|---|
| `local` (por defecto) | H2 en memoria, modo PostgreSQL | `ddl-auto: create-drop` + datos de ejemplo |
| `aws` | Amazon RDS PostgreSQL con `sslmode=require` | migraciones Flyway + `ddl-auto: validate` |

```bash
SPRING_PROFILES_ACTIVE=aws DB_HOST=mi-rds.rds.amazonaws.com \
DB_NAME=pedidos360_productos DB_USER=pedidos360 DB_PASSWORD=*** \
java -jar ms-productos/target/ms-productos-1.0.0.jar
```

## Como se valida el token

`Pedidos360SecurityAutoConfiguration` arma un `NimbusJwtDecoder` que descarga las llaves
publicas del IDaaS y encadena estos validadores:

1. `JwtTimestampValidator` — `exp` y `nbf` con 60 segundos de tolerancia de reloj.
2. `JwtIssuerValidator` — el `iss` debe ser exactamente el tenant configurado.
3. `AudienceValidator` — el `aud` debe incluir la audiencia de esta API.
4. `TenantValidator` — el `tid` debe ser el de la organizacion.
5. `RequiredScopeValidator` — el `scp` debe traer los scopes minimos.

Despues, `EntraIdAuthoritiesConverter` transforma `roles` en `ROLE_*` y `scp` en `SCOPE_*`,
y cada endpoint declara lo que exige con `@PreAuthorize`.

Respuestas de error:

| Situacion | Codigo | Cuerpo |
|---|---|---|
| Sin token, firma invalida, vencido, issuer/audiencia incorrectos | 401 | Problem Detail + `WWW-Authenticate` |
| Token valido sin el rol requerido | 403 | Problem Detail `Acceso denegado` |
| Datos invalidos | 400 | Problem Detail con el mapa `errores` |
| Recurso inexistente | 404 | Problem Detail |
| Regla de negocio (stock, transicion invalida) | 409 | Problem Detail |
| Microservicio aguas abajo caido | 502 | Problem Detail |

## Despliegue en EC2

Ver [`../infra/ec2`](../infra/ec2): plantilla de unidad systemd, `user-data.sh` que resuelve
los secretos desde AWS Systems Manager Parameter Store y `desplegar.sh` para publicar los
jar. La definicion del API Gateway esta en
[`../infra/api-gateway`](../infra/api-gateway).
