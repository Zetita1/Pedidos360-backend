-- Esquema inicial de pedidos (Amazon RDS PostgreSQL).
create table pedido (
    id                    bigserial primary key,
    numero                varchar(20)   not null unique,
    cliente_id            bigint        not null,
    cliente_rut           varchar(12)   not null,
    cliente_razon_social  varchar(150)  not null,
    usuario_oid           varchar(64)   not null,
    usuario_nombre        varchar(150),
    estado                varchar(20)   not null,
    direccion_despacho    varchar(250),
    observacion           varchar(300),
    neto                  numeric(12,2) not null default 0,
    iva                   numeric(12,2) not null default 0,
    total                 numeric(12,2) not null default 0,
    creado_en             timestamp     not null,
    actualizado_en        timestamp     not null
);

create table detalle_pedido (
    id               bigserial primary key,
    pedido_id        bigint        not null references pedido (id) on delete cascade,
    producto_id      bigint        not null,
    sku              varchar(40)   not null,
    nombre_producto  varchar(150)  not null,
    cantidad         integer       not null check (cantidad > 0),
    precio_unitario  numeric(12,2) not null check (precio_unitario > 0)
);

create index idx_pedido_estado on pedido (estado);
create index idx_pedido_cliente on pedido (cliente_id);
create index idx_pedido_usuario on pedido (usuario_oid);
create index idx_detalle_pedido on detalle_pedido (pedido_id);
