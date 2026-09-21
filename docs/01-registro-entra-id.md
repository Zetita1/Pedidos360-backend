# Configuracion del IDaaS: Microsoft Entra External ID

Esta guia cubre los cuatro indicadores de Azure de la EP2 (47% de la nota):

| Indicador | Peso | Seccion |
|---|---|---|
| Crear el tenant con usuarios y roles | 10% | [1](#1-crear-el-tenant-externo) y [6](#6-crear-usuarios-y-asignarles-roles) |
| Registrar y configurar la aplicacion en el tenant | 10% | [2](#2-registrar-la-api) y [3](#3-registrar-el-spa) |
| Flujo de usuario: registro e inicio de sesion | 10% | [4](#4-crear-el-flujo-de-usuario) |
| OIDC Authorization Code con PKCE | 15% | [5](#5-authorization-code--pkce) |

> **Por que External ID y no un tenant de trabajo.** La pauta exige un *flujo de usuario*
> donde "los usuarios puedan crear sus cuentas". El auto-registro y los user flows existen
> solo en los **tenants externos** (Entra External ID, sucesor de Azure AD B2C). En un
> tenant de trabajo las cuentas las crea el administrador y ese indicador no se puede
> demostrar.

---

## 1. Crear el tenant externo

1. Entra al [centro de administracion de Microsoft Entra](https://entra.microsoft.com).
2. **Identity → Overview → Manage tenants → Create**.
3. Elige el tipo **External** (no "Workforce").
4. Completa:
   - Tenant name: `Pedidos360`
   - Domain name: `pedidos360` → queda como `pedidos360.onmicrosoft.com`
   - Location / suscripcion: la de tu cuenta de estudiante.
5. Espera a que termine el aprovisionamiento y **cambia al tenant nuevo** (icono de
   configuracion → Switch tenant).

Anota estos dos datos, los vas a usar en todas partes:

| Dato | Donde se ve | Ejemplo |
|---|---|---|
| **Subdominio** | la parte antes de `.onmicrosoft.com` | `pedidos360` |
| **Directory (tenant) ID** | Overview del tenant | `8f3c…-…-…` |

> **Captura para la presentacion:** la pantalla Overview del tenant recien creado.

---

## 2. Registrar la API

Este registro representa al backend y es el que **expone** los scopes y los roles.

1. **Applications → App registrations → New registration**.
   - Name: `pedidos360-api`
   - Supported account types: *Accounts in this organizational directory only*
   - Redirect URI: ninguno (es una API).
2. **Expose an API → Application ID URI → Add**: acepta `api://<client-id>` o cambialo a
   `api://pedidos360-api`. **Este valor es la `audience`** que validan el API Gateway y el
   backend.
3. En la misma pantalla, **Add a scope** dos veces:

   | Scope | Who can consent | Admin consent display name |
   |---|---|---|
   | `Pedidos.Read` | Admins and users | Consultar catalogo, clientes y pedidos |
   | `Pedidos.Write` | Admins and users | Registrar y actualizar pedidos |

4. **Roles de aplicacion.** En un tenant externo el blade *App roles* puede no estar
   disponible: los roles se crean editando el manifiesto. Ve a **Manifest** y reemplaza
   el arreglo `appRoles` por esto (los GUID ya vienen generados, son unicos):

```json
"appRoles": [
  {
    "allowedMemberTypes": ["User"],
    "description": "Administra el catalogo y los clientes, y puede anular pedidos",
    "displayName": "Administrador de pedidos",
    "id": "ee5b0e51-c932-4eb6-ac37-7725a5adf102",
    "isEnabled": true,
    "value": "Pedidos.Admin"
  },
  {
    "allowedMemberTypes": ["User"],
    "description": "Registra pedidos y avanza su estado",
    "displayName": "Vendedor",
    "id": "b33ab119-0000-4800-97d1-119f81bc96d4",
    "isEnabled": true,
    "value": "Pedidos.Vendedor"
  },
  {
    "allowedMemberTypes": ["User"],
    "description": "Consulta el catalogo y sus propios pedidos",
    "displayName": "Cliente",
    "id": "a244a09c-9448-440a-ad4c-711c27263cc9",
    "isEnabled": true,
    "value": "Pedidos.Cliente"
  }
]
```

   Guarda. El `value` de cada rol es exactamente lo que el backend compara en
   `SecurityRoles` y lo que viaja en el claim `roles`.

> **Captura:** la pantalla *Expose an API* con los dos scopes y el manifiesto con los roles.

---

## 3. Registrar el SPA

1. **App registrations → New registration**.
   - Name: `pedidos360-spa`
   - Supported account types: *Accounts in this organizational directory only*
   - Redirect URI: plataforma **Single-page application (SPA)** → `http://localhost:4200`
2. **Authentication**: agrega tambien la URL productiva (`https://tu-dominio`) y, en
   *Front-channel logout URL*, `http://localhost:4200/login`.
   - Deja **desmarcadas** las casillas de *Implicit grant* (Access tokens / ID tokens). La
     plataforma SPA usa Authorization Code con PKCE; marcarlas seria retroceder al flujo
     implicito, que la pauta penaliza explicitamente.
3. **API permissions → Add a permission → My APIs → `pedidos360-api`** → *Delegated
   permissions* → marca `Pedidos.Read` y `Pedidos.Write` → **Add permissions**.
4. Pulsa **Grant admin consent for Pedidos360** y confirma que las dos permisos queden en
   verde. En tenants externos solo el admin puede consentir.
5. Copia el **Application (client) ID** del SPA.

> **Captura:** *API permissions* con el consentimiento otorgado.

---

## 4. Crear el flujo de usuario

Este es el indicador del 10% y es lo que permite que un usuario **cree su cuenta** desde el
frontend.

1. **Identity → External Identities → User flows → New user flow**.
2. Nombre: `pedidos360-registro-e-ingreso`.
3. Identity providers: **Email with password** (opcionalmente agrega *Email one-time
   passcode*, Google o Facebook si quieres mostrar federacion).
4. User attributes: selecciona los que quieras pedir al registrarse, por ejemplo
   *Display Name*, *Given Name*, *Surname*, *Email Address*. Estos atributos viajan como
   claims y la vista **Perfil** del SPA los muestra.
5. Crea el flujo y entra a el → **Applications → Add application** → selecciona
   `pedidos360-spa`.

   Si te saltas este paso, el login falla: la aplicacion no queda asociada a ningun flujo.

6. Opcional pero vistoso: **Company branding** para poner el logo de Pedidos360 en la
   pantalla de registro.

> **Captura:** el user flow con la aplicacion asociada, y la pantalla de registro real
> mostrando el formulario "Create account".

---

## 5. Authorization Code + PKCE

No hay que activar nada: **MSAL Browser v5 implementa solo Authorization Code con PKCE**;
el flujo implicito fue removido de la libreria. Lo que corresponde es *demostrarlo*.

En el navegador, al pulsar "Ingresar con Microsoft Entra ID", abre DevTools → Network y
mira la peticion a `/oauth2/v2.0/authorize`. En la query string deben aparecer:

| Parametro | Valor esperado | Que demuestra |
|---|---|---|
| `response_type` | `code` | es Authorization Code, no implicit |
| `code_challenge` | cadena base64url | el SPA genero un code verifier |
| `code_challenge_method` | `S256` | el challenge va con SHA-256, no `plain` |
| `state` | GUID | proteccion contra CSRF |
| `nonce` | GUID | proteccion contra replay del id token |
| `scope` | `openid profile offline_access api://pedidos360-api/Pedidos.Read …` | pide los scopes de la API |

Despues, en la peticion `POST /oauth2/v2.0/token` veras `grant_type=authorization_code` y
`code_verifier` — el valor original del challenge. Ese par challenge/verifier **es** PKCE.

> **Captura:** las dos peticiones de DevTools con esos parametros resaltados. Es la
> evidencia mas directa para ese 15%.

---

## 6. Crear usuarios y asignarles roles

Necesitas al menos tres usuarios para demostrar la autorizacion por rol:

| Usuario | Rol | Que debe poder hacer |
|---|---|---|
| `admin@…` | `Pedidos.Admin` | todo, incluido crear productos y anular pedidos |
| `vendedor@…` | `Pedidos.Vendedor` | pedidos y clientes, pero no crear productos ni anular |
| `cliente@…` | `Pedidos.Cliente` | solo catalogo, sus pedidos y su perfil |

Dos formas de crearlos, y conviene mostrar ambas:

- **Autoservicio (lo que pide la pauta):** desde el SPA, pulsa *Ingresar* y luego
  *No account? Create one*. El usuario queda creado en el tenant. Compruebalo en
  **Identity → Users**.
- **Como administrador:** **Identity → Users → New user → Create new user**.

Para asignar el rol:

1. **Identity → Applications → Enterprise applications → `pedidos360-api`**.
2. **Users and groups → Add user/group**.
3. Elige el usuario, luego **Select a role** y marca el rol que corresponda.

> Un usuario **sin rol asignado** puede iniciar sesion, pero toda la API le responde
> **403**. Es un caso excelente para demostrar en vivo que la autorizacion la decide el
> backend y no la interfaz.

> **Captura:** la lista de *Users and groups* con los tres usuarios y sus roles distintos.

---

## 7. Obtener el issuer y el JWK Set exactos

No los adivines: leelos del documento de descubrimiento de tu tenant.

```bash
curl -s https://<subdominio>.ciamlogin.com/<tenant-id>/v2.0/.well-known/openid-configuration | jq '{issuer, jwks_uri, authorization_endpoint, token_endpoint}'
```

Copia **literalmente** los valores de `issuer` y `jwks_uri`: el primero va al API Gateway y
al backend, el segundo es de donde se descargan las llaves publicas para verificar la firma.

---

## 8. Llevar los valores al proyecto

### Frontend — `pedidos360-frontend/src/environments/environment.ts`

```ts
const TENANT_SUBDOMINIO = 'pedidos360';                              // el tuyo
const TENANT_ID = '8f3c...';                                          // Directory (tenant) ID

msal: {
  clientId: '...',                                                    // client ID del SPA
  authority: `https://${TENANT_SUBDOMINIO}.ciamlogin.com/${TENANT_ID}`,
  knownAuthorities: [`${TENANT_SUBDOMINIO}.ciamlogin.com`],
  ...
},
apiScopes: [
  'api://pedidos360-api/Pedidos.Read',
  'api://pedidos360-api/Pedidos.Write',
],
```

`knownAuthorities` es obligatorio: sin el, MSAL rechaza cualquier autoridad que no sea
`login.microsoftonline.com`.

### Backend — variables de entorno

```bash
export ENTRA_ISSUER="https://pedidos360.ciamlogin.com/<tenant-id>/v2.0"   # el del paso 7
export ENTRA_TENANT_ID="<tenant-id>"
export ENTRA_API_AUDIENCE="api://pedidos360-api"
export ENTRA_REQUIRED_SCOPES="Pedidos.Read"
```

El backend deriva el JWK Set del issuer. Si tu `jwks_uri` no sigue el patron
`<issuer sin /v2.0>/discovery/v2.0/keys`, declaralo a mano:

```bash
export ENTRA_JWKS_URI="<el jwks_uri exacto del paso 7>"
```

---

## 9. Comprobar que el token trae lo esperado

Inicia sesion y entra a **Perfil** en el SPA: muestra lado a lado los claims que lee Angular
y los que resuelve el BFF del mismo token. Deberias ver algo como:

```json
{
  "iss": "https://pedidos360.ciamlogin.com/<tenant-id>/v2.0",
  "aud": "api://pedidos360-api",
  "tid": "<tenant-id>",
  "oid": "<id del usuario>",
  "scp": "Pedidos.Read Pedidos.Write",
  "roles": ["Pedidos.Vendedor"]
}
```

| Si falta | Revisa |
|---|---|
| `aud` incorrecto | el SPA esta pidiendo scopes de otra API (paso 3.3) |
| `scp` vacio | no se otorgo el admin consent (paso 3.4) |
| `roles` ausente | el usuario no tiene rol asignado en la enterprise app (paso 6) |
| `iss` distinto | estas usando `login.microsoftonline.com` en vez de `ciamlogin.com` |
