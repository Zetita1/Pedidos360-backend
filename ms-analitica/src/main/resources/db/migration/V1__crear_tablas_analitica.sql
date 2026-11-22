-- Proyecciones que construye ms-analitica al consumir los topicos de Kafka.
--
-- Ninguna de estas tablas es la fuente de verdad: lo son los topicos. Si se pierden, se
-- reconstruyen leyendo los topicos desde el principio con un grupo de consumidores nuevo.

-- Metricas por dia del flujo de hechos de pedidos.
CREATE TABLE metrica_diaria (
    id                  BIGSERIAL PRIMARY KEY,
    fecha               DATE           NOT NULL,
    pedidos_creados     INTEGER        NOT NULL DEFAULT 0,
    pedidos_confirmados INTEGER        NOT NULL DEFAULT 0,
    pedidos_anulados    INTEGER        NOT NULL DEFAULT 0,
    monto_creado        NUMERIC(15, 2) NOT NULL DEFAULT 0,
    monto_confirmado    NUMERIC(15, 2) NOT NULL DEFAULT 0,
    monto_anulado       NUMERIC(15, 2) NOT NULL DEFAULT 0,
    lineas_vendidas     INTEGER        NOT NULL DEFAULT 0,
    actualizado_en      TIMESTAMP      NOT NULL,
    version             BIGINT,
    CONSTRAINT uk_metrica_diaria_fecha UNIQUE (fecha)
);

-- Acumulado por cliente, armado con los datos que trae el propio evento.
CREATE TABLE venta_por_cliente (
    cliente_id          BIGINT PRIMARY KEY,
    cliente_nombre      VARCHAR(180),
    pedidos_confirmados INTEGER        NOT NULL DEFAULT 0,
    monto_total         NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ultima_compra_en    TIMESTAMP,
    version             BIGINT
);

-- Stock vigente por producto, proyectado desde el topico compactado. La clave primaria es la
-- misma clave con la que el registro se particiona en Kafka.
CREATE TABLE estado_stock (
    producto_id    BIGINT PRIMARY KEY,
    sku            VARCHAR(40)   NOT NULL,
    nombre         VARCHAR(180),
    categoria      VARCHAR(80),
    precio         NUMERIC(12, 2),
    stock          INTEGER       NOT NULL,
    stock_anterior INTEGER,
    motivo         VARCHAR(180),
    actualizado_en TIMESTAMP     NOT NULL,
    version        BIGINT
);

CREATE INDEX ix_estado_stock_stock ON estado_stock (stock);

-- Avisos que genera el consumidor cuando un producto cruza el umbral critico.
CREATE TABLE alerta_stock (
    id          BIGSERIAL PRIMARY KEY,
    producto_id BIGINT       NOT NULL,
    sku         VARCHAR(40)  NOT NULL,
    nombre      VARCHAR(180),
    stock       INTEGER      NOT NULL,
    umbral      INTEGER      NOT NULL,
    mensaje     VARCHAR(240) NOT NULL,
    evento_id   VARCHAR(60)  NOT NULL,
    generada_en TIMESTAMP    NOT NULL
);

CREATE INDEX ix_alerta_stock_generada ON alerta_stock (generada_en DESC);

-- Registro de lo ya procesado: sirve de control de duplicados y de trazabilidad.
-- La columna del offset se llama desplazamiento porque OFFSET es palabra reservada en SQL.
CREATE TABLE evento_procesado (
    id             BIGSERIAL PRIMARY KEY,
    evento_id      VARCHAR(60)  NOT NULL,
    topico         VARCHAR(120) NOT NULL,
    particion      INTEGER      NOT NULL,
    desplazamiento BIGINT       NOT NULL,
    clave          VARCHAR(80),
    tipo           VARCHAR(40),
    procesado_en   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_evento_procesado_evento UNIQUE (evento_id)
);

CREATE INDEX ix_evento_procesado_procesado ON evento_procesado (procesado_en DESC);
CREATE INDEX ix_evento_procesado_topico ON evento_procesado (topico);
