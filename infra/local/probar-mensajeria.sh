#!/usr/bin/env bash
#
# Evidencia de la mensajeria: publica eventos reales en el exchange de Pedidos360 y muestra
# como quedan las colas. Incluye un evento invalido, que debe terminar en la DLQ.
#
# Requiere RabbitMQ arriba (infra/local/docker-compose.yml) y ms-notificaciones corriendo.
#
# Uso:  ./infra/local/probar-mensajeria.sh
set -euo pipefail

BROKER="${RABBITMQ_GESTION_URL:-http://localhost:15672}"
USUARIO="${RABBITMQ_USER:-pedidos360}"
CLAVE="${RABBITMQ_PASSWORD:-pedidos360}"
EXCHANGE="${MENSAJERIA_EXCHANGE:-pedidos360.eventos}"
EXCHANGE_COMANDOS="${MENSAJERIA_EXCHANGE_COMANDOS:-pedidos360.comandos}"
PEDIDO="${1:-PED-DEMO-$(date +%H%M%S)}"

python - "${BROKER}" "${USUARIO}" "${CLAVE}" "${EXCHANGE}" "${PEDIDO}" "${EXCHANGE_COMANDOS}" <<'PY'
import base64
import json
import sys
import time
import urllib.request

broker, usuario, clave, exchange, pedido, exchange_comandos = sys.argv[1:7]
auth = base64.b64encode(f"{usuario}:{clave}".encode()).decode()
EVENTOS = "cl.duoc.pedidos360.mensajeria.evento."


def llamar(ruta, cuerpo=None):
    url = f"{broker}{ruta}"
    datos = json.dumps(cuerpo).encode() if cuerpo else None
    metodo = "POST" if cuerpo else "GET"
    pedido_http = urllib.request.Request(
        url,
        data=datos,
        method=metodo,
        headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"},
    )
    return json.load(urllib.request.urlopen(pedido_http))


def publicar(titulo, tipo, routing_key, payload, destino=None):
    destino = destino or exchange
    cuerpo = {
        "properties": {"content_type": "application/json", "headers": {"__TypeId__": tipo}},
        "routing_key": routing_key,
        "payload": json.dumps(payload),
        "payload_encoding": "string",
    }
    respuesta = llamar(f"/api/exchanges/%2F/{destino}/publish", cuerpo)
    estado = "enrutado" if respuesta.get("routed") else "SIN COLA DESTINO"
    print(f"  {titulo:<42} {destino:<22} {routing_key:<24} {estado}")


print(f"==> 1/3 Publicando eventos del pedido {pedido} en {exchange}")

publicar(
    "Pedido creado",
    EVENTOS + "PedidoCreadoEvento",
    "pedido.creado",
    {
        "eventoId": f"evt-{pedido}-creado",
        "ocurridoEn": "2026-10-23T12:00:00Z",
        "pedidoNumero": pedido,
        "pedidoId": 1,
        "clienteId": 10,
        "clienteNombre": "Comercial Andes SpA",
        "clienteEmail": "contacto@comercialandes.cl",
        "total": 238000,
        "lineas": 2,
    },
)

publicar(
    "Pedido confirmado (genera el ticket)",
    EVENTOS + "PedidoConfirmadoEvento",
    "pedido.confirmado",
    {
        "eventoId": f"evt-{pedido}-confirmado",
        "ocurridoEn": "2026-10-23T12:05:00Z",
        "pedidoNumero": pedido,
        "pedidoId": 1,
        "clienteId": 10,
        "clienteNombre": "Comercial Andes SpA",
        "clienteEmail": "contacto@comercialandes.cl",
        "total": 238000,
        "detalles": [
            {
                "sku": "NB-001",
                "descripcion": "Notebook Pro 14",
                "cantidad": 2,
                "precioUnitario": 100000,
                "subtotal": 200000,
            }
        ],
    },
)

publicar(
    "Cambio de estado",
    EVENTOS + "PedidoEstadoCambiadoEvento",
    "pedido.estado-cambiado",
    {
        "eventoId": f"evt-{pedido}-estado",
        "ocurridoEn": "2026-10-23T12:10:00Z",
        "pedidoNumero": pedido,
        "pedidoId": 1,
        "clienteId": 10,
        "clienteEmail": "contacto@comercialandes.cl",
        "estadoAnterior": "CONFIRMADO",
        "estadoNuevo": "DESPACHADO",
        "motivo": "Salio con el transportista",
    },
)

publicar(
    "Pedido anulado (exchange DIRECT, auditoria)",
    EVENTOS + "PedidoAnuladoEvento",
    "auditoria.pedido-anulado",
    {
        "eventoId": f"evt-{pedido}-anulado",
        "ocurridoEn": "2026-10-23T12:20:00Z",
        "pedidoNumero": pedido,
        "pedidoId": 1,
        "clienteId": 10,
        "usuarioOid": "oid-vendedor-demo",
        "usuarioNombre": "SofiaTroncoso",
        "estadoAnterior": "DESPACHADO",
        "total": 238000,
        "motivo": "Solicitud del cliente",
    },
    exchange_comandos,
)

print("\n==> 2/3 Publicando eventos invalidos (deben terminar en la DLQ)")
publicar(
    "Pedido creado SIN numero de pedido",
    EVENTOS + "PedidoCreadoEvento",
    "pedido.creado",
    {
        "eventoId": f"evt-{pedido}-invalido",
        "ocurridoEn": "2026-10-23T12:15:00Z",
        "pedidoNumero": "   ",
        "pedidoId": 2,
        "clienteId": 10,
        "clienteNombre": "Sin pedido",
        "clienteEmail": "x@y.cl",
        "total": 100,
        "lineas": 1,
    },
)

publicar(
    "Anulacion SIN responsable",
    EVENTOS + "PedidoAnuladoEvento",
    "auditoria.pedido-anulado",
    {
        "eventoId": f"evt-{pedido}-anulado-invalido",
        "ocurridoEn": "2026-10-23T12:25:00Z",
        "pedidoNumero": pedido,
        "pedidoId": 1,
        "clienteId": 10,
        "usuarioOid": "",
        "estadoAnterior": "DESPACHADO",
        "total": 238000,
        "motivo": "Sin autor",
    },
    exchange_comandos,
)

time.sleep(5)

print("\n==> 3/3 Estado de las colas")
print(f"  {'COLA':<36} {'MENSAJES':>9} {'CONSUMIDORES':>13}")
for cola in sorted(llamar("/api/queues/%2F"), key=lambda c: c["name"]):
    if cola["name"].startswith("pedidos360"):
        print(
            f"  {cola['name']:<36} {cola.get('messages', '-'):>9} "
            f"{cola.get('consumers', '-'):>13}"
        )

print(
    "\nLo esperado: las tres colas principales en 0 (todo consumido) y un mensaje en\n"
    "pedidos360.notificaciones.dlq y otro en pedidos360.auditoria.dlq: los dos\n"
    "invalidos, descartados sin gastar reintentos."
)
PY
