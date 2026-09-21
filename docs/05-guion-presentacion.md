# Guion de la presentacion (EP2)

La presentacion dura **entre 5 y 10 minutos** y es **individual**: cada estudiante
fundamenta el diseno y responde preguntas. Este guion cubre los siete elementos que la
pauta exige mostrar, en un orden que sigue el camino de una peticion.

**Antes de empezar, deja abierto:** el portal de Entra, la consola de API Gateway, el SPA
publicado, una terminal con `$URL` y `$TOKEN` exportados, y DevTools en la pestana Network.

---

## Minuto 0–1 · El problema y la arquitectura

Una lamina con el diagrama de [`02-arquitectura.md`](02-arquitectura.md).

> "Pedidos360 es un sistema de gestion de pedidos. El frontend es un SPA de Angular que no
> guarda credenciales: delega la identidad en Microsoft Entra External ID. Toda llamada
> pasa por AWS API Gateway, que valida el JWT antes de entrar a la VPC, y los cuatro
> servicios Spring Boot en EC2 vuelven a validarlo. La persistencia es PostgreSQL en RDS,
> con una base por microservicio."

Deja instalada la idea que vas a repetir al final: **el token se valida tres veces**.

---

## Minuto 1–2 · El tenant del IDaaS y sus usuarios *(indicador 10%)*

1. Portal de Entra → **Overview** del tenant externo: se ve el nombre y el tenant ID.
2. **Identity → Users**: los tres usuarios de prueba.
3. **Enterprise applications → pedidos360-api → Users and groups**: cada usuario con un rol
   distinto.

> "Es un tenant **externo**, no uno de trabajo, porque necesito que los usuarios puedan
> registrarse por si mismos. Los roles no estan en mi base de datos: los administra el
> IDaaS y viajan dentro del token."

---

## Minuto 2–3 · La aplicacion y el flujo de usuario *(indicadores 10% + 10%)*

1. **App registrations → pedidos360-api → Expose an API**: el Application ID URI y los dos
   scopes. Menciona que ese URI es la `audience` que se valida despues.
2. **Manifest**: los tres app roles.
3. **App registrations → pedidos360-spa → Authentication**: plataforma SPA y las redirect
   URIs. Senala que *Implicit grant* esta **desmarcado**.
4. **External Identities → User flows → pedidos360-registro-e-ingreso**: los proveedores, los
   atributos que se piden y la aplicacion asociada.

> "El registro de la API expone los scopes y define los roles; el del SPA los consume. El
> flujo de usuario es lo que permite que alguien cree su cuenta sin que yo intervenga."

---

## Minuto 3–4 · Login real y PKCE *(indicador 15%)*

1. Abre el SPA → **Ingresar con Microsoft Entra ID**.
2. Si alcanza el tiempo, **crea una cuenta nueva en vivo** con *Create one*: es la prueba
   mas contundente del flujo de usuario.
3. Vuelve al SPA ya autenticado y abre **Perfil**: claims, roles y scopes, y al lado lo que
   resolvio el BFF del mismo token.
4. DevTools → Network → peticion a `/authorize`. Muestra `response_type=code`,
   `code_challenge`, `code_challenge_method=S256`, `state` y `nonce`.

> "Es Authorization Code con PKCE. El SPA genero un code verifier, envio solo su hash con
> S256, y al canjear el codigo devuelve el verifier original. Un codigo interceptado no
> sirve sin el. No uso el flujo implicito, que ya esta desaconsejado para SPAs."

---

## Minuto 4–6 · El API Manager *(indicadores 13% + 7% + 20%)*

1. Consola de API Gateway → **Routes**: las 22 rutas, agrupadas por microservicio.
   Muestra que `/api/v1/pedidos` integra al puerto 8081 y `/api/v1/productos` al 8082.
2. **Authorization**: el autorizador JWT con el issuer y la audience, y la columna que
   confirma que esta aplicado a todas las rutas menos `/health`.
3. **CORS**: origenes, metodos y cabeceras acotados. Di por que no usaste `*`.

> "Cada recurso se enruta a su microservicio duenio. El BFF no es un cuello de botella:
> queda solo para las vistas agregadas, como el tablero, que en una sola llamada junta lo
> que serian tres."

---

## Minuto 6–8 · Evidencia de las rutas *(indicador 15%)*

En la terminal, `./probar-rutas.sh`, o estos cuatro comandos si prefieres control:

```bash
curl -i $URL/health                                              # 200 publico
curl -i $URL/api/v1/productos                                    # 401 del gateway
curl -i -H "Authorization: Bearer $TOKEN" $URL/api/v1/productos  # 200 + JSON
curl -i -H "Authorization: Bearer $TOKEN_CLIENTE" $URL/api/v1/clientes  # 403 del backend
```

> "El **401 lo pone el API Gateway**: el token no sirve y la peticion nunca llega al
> backend. El **403 lo pone el microservicio**: el token es valido, pero el rol no alcanza.
> El gateway resuelve *quien eres*; el backend resuelve *que puedes hacer*."

Cierra con un recorrido corto por la interfaz con dos usuarios distintos: con
`Pedidos.Admin` aparecen *Nuevo producto* y *Anular*; con `Pedidos.Cliente` no aparecen, y
si fuerzas la URL `/pedidos` caes en la pantalla 403.

---

## Minuto 8–10 · Todo desplegado e integrado, y preguntas

1. Consola de EC2: la instancia corriendo. `systemctl status` de los cuatro servicios si
   tienes la terminal abierta.
2. RDS: la instancia de PostgreSQL.
3. El SPA publicado en su URL real, funcionando contra el gateway.

> "Resumiendo: el token se valida en tres capas. Puede parecer redundante, pero si alguien
> alcanza el EC2 sin pasar por el gateway, sigue necesitando un token valido. Ninguna capa
> confia en que la anterior ya reviso."

### Preguntas probables y como responderlas

| Pregunta | Respuesta corta |
|---|---|
| ¿Por que un BFF si ya hay API Gateway? | El gateway enruta y valida identidad; el BFF **compone**. El tablero seria tres llamadas desde el SPA y es una sola. |
| ¿Por que validar el token si el gateway ya lo hizo? | Porque el EC2 es alcanzable dentro de la VPC. Defensa en profundidad. |
| ¿Por que External ID y no un tenant normal? | Porque necesitaba auto-registro; los user flows solo existen en tenants externos. |
| ¿Donde estan los roles? | En el IDaaS, en el claim `roles` del token. El backend los traduce a autoridades de Spring Security con `@PreAuthorize`. |
| ¿Que pasa si el token expira? | MSAL lo renueva en silencio con el refresh token; si no puede, el interceptor recibe 401 y se pide login otra vez. |
| ¿Por que PKCE y no un client secret? | Un SPA no puede guardar secretos: el codigo es publico. PKCE reemplaza el secreto por un valor efimero por transaccion. |
| ¿Como evitas sobreventa de stock? | El descuento usa bloqueo pesimista sobre la fila del producto, y el producto tiene version optimista. |
| ¿Por que una base por microservicio? | Para que cada servicio sea duenio de su esquema. `ms-pedidos` guarda ids ajenos pero consulta las APIs, no hace JOIN cruzado. |

---

## Lista de verificacion previa

- [ ] Los cuatro servicios responden `200` en `/actuator/health` desde el EC2
- [ ] `./probar-rutas.sh` pasa completo
- [ ] Los tres usuarios de prueba inician sesion y traen su rol en el token
- [ ] El SPA publicado apunta al `apiBaseUrl` del gateway (no a localhost)
- [ ] Tienes `$URL`, `$TOKEN` y `$TOKEN_CLIENTE` exportados en la terminal
- [ ] Sesiones abiertas en el portal de Entra y en la consola de AWS
- [ ] Ensayaste una vez completo y cabe en 10 minutos
