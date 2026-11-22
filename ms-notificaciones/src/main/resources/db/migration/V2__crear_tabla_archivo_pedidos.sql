-- Archivo de los hechos de pedidos que llegan por el topico de Kafka.
--
-- Lo alimenta el grupo de consumidores archivo-pedidos, distinto del que usa ms-analitica:
-- los dos leen el mismo topico y cada uno lleva su propio avance.
--
-- A diferencia de las tablas de notificaciones y documentos, que son resultado de ejecutar
-- una tarea, esta solo conserva: guarda el registro como venia, sin resumirlo.

CREATE TABLE hecho_pedido_archivado (
    id              BIGSERIAL PRIMARY KEY,
    evento_id       VARCHAR(60)    NOT NULL,
    tipo            VARCHAR(40)    NOT NULL,
    pedido_numero   VARCHAR(40)    NOT NULL,
    cliente_id      BIGINT,
    cliente_nombre  VARCHAR(180),
    total           NUMERIC(15, 2),
    estado_anterior VARCHAR(30),
    estado_nuevo    VARCHAR(30),
    motivo          VARCHAR(240),
    usuario_oid     VARCHAR(60),
    topico          VARCHAR(120)   NOT NULL,
    particion       INTEGER        NOT NULL,
    desplazamiento  BIGINT         NOT NULL,
    ocurrido_en     TIMESTAMP      NOT NULL,
    archivado_en    TIMESTAMP      NOT NULL,
    -- Kafka entrega al menos una vez: la restriccion es lo que evita archivar el duplicado.
    CONSTRAINT uk_hecho_pedido_evento UNIQUE (evento_id)
);

CREATE INDEX ix_hecho_pedido_numero ON hecho_pedido_archivado (pedido_numero, ocurrido_en);
CREATE INDEX ix_hecho_pedido_archivado ON hecho_pedido_archivado (archivado_en DESC);
