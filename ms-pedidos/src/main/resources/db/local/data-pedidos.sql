-- Pedido de ejemplo para el perfil local (H2 en memoria).
insert into pedido (numero, cliente_id, cliente_rut, cliente_razon_social, usuario_oid, usuario_nombre,
                    estado, direccion_despacho, observacion, neto, iva, total, creado_en, actualizado_en)
values ('PED-2026-00001', 1, '76123456-7', 'Comercial Andes SpA', 'oid-demo', 'Vendedor Demo',
        'CONFIRMADO', 'Av. Providencia 1234, Providencia, Santiago', 'Pedido de demostracion',
        1229980.00, 233696.20, 1463676.20, current_timestamp, current_timestamp);

insert into detalle_pedido (pedido_id, producto_id, sku, nombre_producto, cantidad, precio_unitario)
values (1, 1, 'NB-001', 'Notebook Pro 14', 1, 899990.00),
       (1, 2, 'MN-002', 'Monitor 27 4K', 1, 329990.00);
