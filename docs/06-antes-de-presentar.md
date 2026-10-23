# Antes de presentar — runbook

Lo que hay que revisar el día de la presentación, en orden. Pensado para **5 minutos**.

---

## Valores fijos (no cambian)

### Azure — Microsoft Entra External ID

| Dato | Valor |
|---|---|
| Tenant | `pedidos360duocuc.onmicrosoft.com` |
| Tenant ID | `9d265688-dbb5-4a46-9b9a-8f6765928aa0` |
| Client ID SPA | `4763762f-79ea-47ad-9966-bda5e049df68` |
| Client ID API | `e25c9a50-e056-4401-a2ef-f2d8fdefb77b` |
| App ID URI | `api://pedidos360-api` |
| Issuer | `https://9d265688-dbb5-4a46-9b9a-8f6765928aa0.ciamlogin.com/9d265688-dbb5-4a46-9b9a-8f6765928aa0/v2.0` |
| Flujo de usuario | `pedidos360-registro-e-ingreso` |
| Usuario de prueba | `sofi.troncoso@duocuc.cl` · `Pedidos.Admin` |
| Cuenta del portal | `joshuamardones2@gmail.com` (cuenta personal, **no** la de Duoc) |

### AWS — Learner Lab (cuenta `286145611273`, `us-east-1`)

| Recurso | Valor |
|---|---|
| **URL del API Gateway** | `https://j9w6eomnw5.execute-api.us-east-1.amazonaws.com` |
| API ID | `j9w6eomnw5` |
| Authorizer JWT | `entra-external-id` (`8gbv89`) |
| Instancia EC2 | `i-0f8db5d98822339d6` · `pedidos360-backend` · t3.medium |
| **IP elástica** | `54.156.41.241` (no cambia aunque la instancia se detenga) |
| Security group | `sg-02cd650528012d232` · 22 desde mi IP, 8080-8083 abiertos |
| Llave SSH | `~/.ssh/pedidos360-lab.pem` |
| **Frontend publicado** | `https://main.d1pem9n2nc75cz.amplifyapp.com` · Amplify app `d1pem9n2nc75cz`, rama `main` |
| Orígenes CORS | `https://main.d1pem9n2nc75cz.amplifyapp.com` y `http://localhost:4200` |

> CloudFront no está permitido en el Learner Lab (ni OAC ni OAI): por eso el SPA se publica en
> **Amplify Hosting**, que también entrega HTTPS, requisito de Entra para redirect URIs.

Los cuatro microservicios corren en la instancia como servicios de **systemd habilitados**:
arrancan solos cada vez que la instancia se enciende.

---

## Rutina antes de entrar (5 min)

### 1. Iniciar el lab de AWS Academy

Portal de AWS Academy → **Start Lab** → esperar el punto **verde** junto a "AWS" → abrir la
consola.

### 2. Encender la instancia si está detenida

El Learner Lab detiene las instancias al cerrar la sesión. En la consola:

**EC2 → Instancias → `pedidos360-backend`** → si dice *Detenida* → **Estado de la instancia →
Iniciar**. Tarda ~1 minuto, y los servicios suben solos ~40 segundos después.

La IP elástica sigue asociada, así que **el API Gateway no requiere ningún cambio**.

### 3. Verificar que todo responde

```bash
curl https://j9w6eomnw5.execute-api.us-east-1.amazonaws.com/health
```

Debe devolver `{"status":"UP",...}`. Si da error 503 o timeout, los servicios aún están
arrancando: esperar 30 segundos y reintentar.

### 4. Abrir el frontend publicado

`https://main.d1pem9n2nc75cz.amplifyapp.com` → iniciar sesión. Está en Amplify, así que no hay
que levantar nada: no depende de la sesión del lab.

Respaldo local, si hiciera falta:

```bash
npm --prefix pedidos360-frontend start
```

### Volver a publicar el frontend (solo si se cambia código)

```bash
./infra/amplify/publicar-frontend.sh
```

---

## Evidencia en vivo

```bash
URL=https://j9w6eomnw5.execute-api.us-east-1.amazonaws.com

curl -i $URL/health                                               # 200, público
curl -i $URL/api/v1/productos                                     # 401 del gateway
curl -i -H "Authorization: Bearer $TOKEN" $URL/api/v1/productos   # 200 + JSON
```

El `$TOKEN` se copia desde DevTools → Network → cualquier llamada a `execute-api` →
cabecera `Authorization`, sin el prefijo `Bearer `.

Recorrido completo de las 22 rutas:

```bash
export URL=https://j9w6eomnw5.execute-api.us-east-1.amazonaws.com
export TOKEN=<token>
./infra/api-gateway/probar-rutas.sh
```

---

## Si algo falla

| Síntoma | Qué hacer |
|---|---|
| `/health` da 503 o timeout | La instancia está detenida o los servicios aún arrancan (paso 2) |
| El login redirige a un error de Microsoft | La URL no está registrada como redirect URI en `pedidos360-spa`; valen solo la de Amplify y `http://localhost:4200` |
| Todo da 401 con sesión iniciada | Cerrar sesión en el SPA y volver a entrar para renovar el token |
| El navegador marca error de CORS | El SPA se abrió desde un origen que no es Amplify ni `http://localhost:4200` |

### Solo si cambiara la dirección del backend

No debería pasar gracias a la IP elástica. Si por algún motivo cambia, desde una terminal con
las credenciales del lab en `~/.aws/credentials`:

```bash
export AWS_PAGER="" MSYS_NO_PATHCONV=1
API_ID=j9w6eomnw5
NUEVO_HOST=<nueva-ip>

for id in $(aws apigatewayv2 get-integrations --api-id $API_ID --query 'Items[].IntegrationId' --output text); do
  uri=$(aws apigatewayv2 get-integration --api-id $API_ID --integration-id $id --query IntegrationUri --output text)
  nueva=$(echo "$uri" | sed -E "s#http://[^:/]+#http://$NUEVO_HOST#")
  aws apigatewayv2 update-integration --api-id $API_ID --integration-id $id --integration-uri "$nueva" >/dev/null
  echo "$uri  ->  $nueva"
done
```

Con auto-deploy en el stage `$default`, el cambio se publica al instante.

> Las credenciales del CLI del Learner Lab caducan al terminar cada sesión: si se necesitan,
> copiarlas de nuevo desde **AWS Details → AWS CLI → Show**.

---

## Lista de verificación final

- [ ] Lab de AWS Academy iniciado (punto verde)
- [ ] Instancia `pedidos360-backend` en estado *En ejecución*
- [ ] `curl $URL/health` devuelve 200
- [ ] `curl $URL/api/v1/productos` devuelve 401
- [ ] Login en `https://main.d1pem9n2nc75cz.amplifyapp.com` y el tablero carga datos
- [ ] Crear un pedido y ver el aviso en la vista **Notificaciones** (prueba la mensajeria)
- [ ] La vista **Mensajeria** lista las colas con sus consumidores
- [ ] Capturas del 403 y del Perfil a mano, por si falla la demo en vivo
- [ ] Portal de Entra abierto en el tenant **Pedidos360**
- [ ] Consola de API Gateway abierta en **Rutas** y **Autorización**
