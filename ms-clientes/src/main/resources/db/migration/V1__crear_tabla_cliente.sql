-- Esquema inicial de clientes (Amazon RDS PostgreSQL).
create table cliente (
    id              bigserial primary key,
    rut             varchar(12)  not null unique,
    razon_social    varchar(150) not null,
    email           varchar(150) not null,
    telefono        varchar(20),
    segmento        varchar(20)  not null,
    calle           varchar(150),
    numero          varchar(20),
    comuna          varchar(80),
    ciudad          varchar(80),
    region          varchar(80),
    usuario_oid     varchar(64),
    activo          boolean      not null default true,
    creado_en       timestamp    not null,
    actualizado_en  timestamp    not null
);

create index idx_cliente_usuario_oid on cliente (usuario_oid);
create index idx_cliente_activo on cliente (activo);
