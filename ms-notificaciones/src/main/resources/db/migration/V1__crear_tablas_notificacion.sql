-- Esquema inicial de notificaciones y documentos (Amazon RDS PostgreSQL).
create table notificacion (
    id            bigserial primary key,
    evento_id     varchar(80)  not null unique,
    tipo          varchar(20)  not null,
    pedido_numero varchar(30)  not null,
    pedido_id     bigint,
    cliente_id    bigint,
    destinatario  varchar(150),
    asunto        varchar(150) not null,
    mensaje       varchar(600) not null,
    creado_en     timestamp    not null
);

create index idx_notificacion_pedido on notificacion (pedido_numero);
create index idx_notificacion_creado_en on notificacion (creado_en desc);

create table documento (
    id             bigserial primary key,
    evento_id      varchar(80) not null unique,
    folio          varchar(30) not null unique,
    pedido_numero  varchar(30) not null,
    pedido_id      bigint,
    cliente_id     bigint,
    cliente_nombre varchar(150),
    total          numeric(14,2) not null,
    contenido      text        not null,
    creado_en      timestamp   not null
);

create index idx_documento_pedido on documento (pedido_numero);

create table registro_auditoria (
    id              bigserial primary key,
    evento_id       varchar(80)  not null unique,
    operacion       varchar(40)  not null,
    pedido_numero   varchar(30)  not null,
    cliente_id      bigint,
    usuario_oid     varchar(64),
    usuario_nombre  varchar(150),
    estado_anterior varchar(30),
    monto           numeric(14,2),
    motivo          varchar(300),
    ocurrido_en     timestamp    not null,
    registrado_en   timestamp    not null
);

create index idx_auditoria_pedido on registro_auditoria (pedido_numero);
create index idx_auditoria_ocurrido_en on registro_auditoria (ocurrido_en desc);
