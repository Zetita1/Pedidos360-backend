# Cluster de RabbitMQ (EP4)

Dos nodos en un mismo cluster, levantados con `docker compose`.

## Levantar

```bash
docker compose -f infra/rabbitmq/docker-compose.yml up -d
```

## Comprobar que el cluster quedo formado

```bash
docker exec pedidos360-rabbit1 rabbitmqctl cluster_status
```

Lo esperado: los dos nodos (`rabbit@rabbit1` y `rabbit@rabbit2`) en *Running Nodes*.

## Consolas

| Nodo | AMQP | Consola |
|---|---|---|
| rabbit1 | `localhost:5672` | <http://localhost:15672> |
| rabbit2 | `localhost:5673` | <http://localhost:15673> |

Usuario y clave por defecto: `pedidos360` / `pedidos360`. Se cambian con las variables
`RABBITMQ_USER` y `RABBITMQ_PASSWORD`, y la cookie de Erlang con `RABBITMQ_ERLANG_COOKIE`.

## Como se conecta la aplicacion

Los microservicios usan `spring.rabbitmq.addresses` con los dos nodos, asi que si uno cae el
cliente reintenta contra el otro:

```
RABBITMQ_ADDRESSES=localhost:5672,localhost:5673
```

## En la nube

Dentro de la instancia EC2, el mismo archivo levanta los dos nodos:

```bash
ssh -i ~/.ssh/pedidos360-lab.pem ec2-user@<ip-ec2>
cd /opt/pedidos360/infra/rabbitmq && docker compose up -d
```

Los puertos quedan publicados solo en la instancia; para ver la consola desde el computador
se usa un tunel SSH:

```bash
ssh -i ~/.ssh/pedidos360-lab.pem -L 15672:localhost:15672 -L 15673:localhost:15673 ec2-user@<ip-ec2>
```
