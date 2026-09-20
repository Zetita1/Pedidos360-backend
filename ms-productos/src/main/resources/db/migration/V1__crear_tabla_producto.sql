-- Esquema inicial del catalogo de productos (Amazon RDS PostgreSQL).
create table producto (
    id              bigserial primary key,
    sku             varchar(40)   not null unique,
    nombre          varchar(150)  not null,
    descripcion     varchar(500),
    precio          numeric(12,2) not null check (precio > 0),
    stock           integer       not null check (stock >= 0),
    categoria       varchar(60)   not null,
    activo          boolean       not null default true,
    creado_en       timestamp     not null,
    actualizado_en  timestamp     not null,
    version         bigint        not null default 0
);

create index idx_producto_categoria on producto (categoria);
create index idx_producto_activo on producto (activo);
