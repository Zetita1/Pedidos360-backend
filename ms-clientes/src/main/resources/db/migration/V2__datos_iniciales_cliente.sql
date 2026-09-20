-- Clientes de demostracion.
insert into cliente (rut, razon_social, email, telefono, segmento, calle, numero, comuna, ciudad, region, usuario_oid, activo, creado_en, actualizado_en)
values
 ('76123456-7', 'Comercial Andes SpA', 'contacto@andes.cl', '+56221234567', 'EMPRESA', 'Av. Providencia', '1234', 'Providencia', 'Santiago', 'Metropolitana', null, true, now(), now()),
 ('12345678-5', 'Maria Gonzalez', 'maria.gonzalez@correo.cl', '+56987654321', 'MINORISTA', 'Los Alerces', '45', 'Maipu', 'Santiago', 'Metropolitana', null, true, now(), now()),
 ('98765432-1', 'Distribuidora Sur Ltda', 'ventas@dsur.cl', '+56412223344', 'MAYORISTA', 'Camino Industrial', '900', 'Talcahuano', 'Concepcion', 'Biobio', null, true, now(), now());
