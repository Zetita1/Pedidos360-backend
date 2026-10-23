#!/usr/bin/env bash
#
# Instala RabbitMQ con el plugin de gestion en la instancia EC2 (Amazon Linux 2023) y deja
# creado el usuario de la aplicacion.
#
# El broker escucha solo en localhost: los microservicios corren en la misma instancia y
# ningun puerto de RabbitMQ queda expuesto a internet.
#
# Uso (desde el computador, con la llave del laboratorio):
#   ./instalar-rabbitmq.sh <host-ec2> <ruta-llave.pem> [usuario] [password]
set -euo pipefail

HOST="${1:?Indica el host o IP de la instancia EC2}"
LLAVE="${2:?Indica la ruta de la llave .pem}"
USUARIO_APP="${3:-pedidos360}"
CLAVE_APP="${4:-pedidos360}"
USUARIO_SSH="${USUARIO_EC2:-ec2-user}"

ssh -i "${LLAVE}" "${USUARIO_SSH}@${HOST}" "sudo bash -s" <<REMOTO
set -euo pipefail

echo "==> 1/5 Instalando Erlang y RabbitMQ"
dnf install -y erlang rabbitmq-server >/dev/null

echo "==> 2/5 Limitando el broker a localhost"
mkdir -p /etc/rabbitmq
cat > /etc/rabbitmq/rabbitmq.conf <<'CONF'
# Los microservicios corren en esta misma instancia: el broker no se expone a la red.
listeners.tcp.local = 127.0.0.1:5672
management.tcp.ip   = 127.0.0.1
management.tcp.port = 15672
CONF

echo "==> 3/5 Habilitando el plugin de gestion"
rabbitmq-plugins enable --offline rabbitmq_management >/dev/null

echo "==> 4/5 Arrancando el servicio"
systemctl enable --now rabbitmq-server
sleep 10

echo "==> 5/5 Creando el usuario de la aplicacion"
if rabbitmqctl list_users | grep -q "^${USUARIO_APP}\b"; then
  rabbitmqctl change_password "${USUARIO_APP}" "${CLAVE_APP}"
else
  rabbitmqctl add_user "${USUARIO_APP}" "${CLAVE_APP}"
fi
rabbitmqctl set_user_tags "${USUARIO_APP}" administrator
rabbitmqctl set_permissions -p / "${USUARIO_APP}" ".*" ".*" ".*"
rabbitmqctl delete_user guest 2>/dev/null || true

rabbitmq-diagnostics -q check_running && echo "RabbitMQ en ejecucion"
REMOTO

cat <<RESUMEN

==========================================================
 RabbitMQ instalado en ${HOST}
==========================================================
 AMQP     : localhost:5672   (solo dentro de la instancia)
 Gestion  : localhost:15672  (solo dentro de la instancia)
 Usuario  : ${USUARIO_APP}

 Para ver la consola web desde el computador, abrir un tunel SSH:
   ssh -i ${LLAVE} -L 15672:localhost:15672 ${USUARIO_SSH}@${HOST}
   y luego entrar a http://localhost:15672

 Guardar las credenciales en Parameter Store (las lee user-data.sh):
   aws ssm put-parameter --name /pedidos360/rabbitmq/usuario  --value ${USUARIO_APP} --type String --overwrite
   aws ssm put-parameter --name /pedidos360/rabbitmq/password --value '<clave>' --type SecureString --overwrite
==========================================================
RESUMEN
