-- Datos de ejemplo para el perfil local (H2 en memoria).
insert into producto (sku, nombre, descripcion, precio, stock, categoria, activo, creado_en, actualizado_en, version) values
 ('NB-001', 'Notebook Pro 14', 'Notebook 14 pulgadas 16GB RAM 512GB SSD', 899990.00, 25, 'Computacion', true, current_timestamp, current_timestamp, 0),
 ('MN-002', 'Monitor 27 4K', 'Monitor IPS 27 pulgadas resolucion 4K', 329990.00, 40, 'Computacion', true, current_timestamp, current_timestamp, 0),
 ('TC-003', 'Teclado mecanico RGB', 'Teclado mecanico switches rojos', 59990.00, 120, 'Accesorios', true, current_timestamp, current_timestamp, 0),
 ('MS-004', 'Mouse inalambrico', 'Mouse ergonomico 2.4GHz', 19990.00, 200, 'Accesorios', true, current_timestamp, current_timestamp, 0),
 ('IM-005', 'Impresora multifuncional', 'Impresora tinta continua wifi', 149990.00, 15, 'Oficina', true, current_timestamp, current_timestamp, 0),
 ('SL-006', 'Silla ergonomica', 'Silla de escritorio con soporte lumbar', 189990.00, 8, 'Oficina', true, current_timestamp, current_timestamp, 0);
