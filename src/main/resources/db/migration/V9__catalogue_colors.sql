-- Color elegido para las categorias y los tags.
--
-- Hasta aqui el color de una ficha no se guardaba: el cliente lo deducia del nombre, asi que
-- no podia elegirse y ademas cambiaba al renombrar. Ahora el usuario puede fijarlo, y tiene
-- que verse igual en todos sus dispositivos, por eso vive en la fila y no en el navegador.
--
-- La columna admite dos formas y nada mas:
--   * `preset-0` .. `preset-7`: una de las ocho fichas de la paleta. No es un color sino una
--     posicion; el cliente la resuelve contra el color principal y el tema de cada momento,
--     asi que sigue a la paleta del usuario y al modo oscuro.
--   * `#rrggbb` en minusculas: un color libre, que se pinta tal cual y del que el cliente
--     decide la tinta por contraste.
-- Nulo significa lo de siempre: el color se deduce del nombre. Las filas que ya existian se
-- quedan asi y se ven exactamente igual que antes.
--
-- La restriccion repite la regla del contrato a proposito: alli se decide que error ve el
-- usuario, aqui se impide que un valor que ningun cliente sabe pintar llegue a guardarse.

ALTER TABLE categories
    ADD COLUMN color varchar(9) NULL,
    ADD CONSTRAINT ck_categories_color CHECK (color ~ '^(preset-[0-7]|#[0-9a-f]{6})$');

ALTER TABLE tags
    ADD COLUMN color varchar(9) NULL,
    ADD CONSTRAINT ck_tags_color CHECK (color ~ '^(preset-[0-7]|#[0-9a-f]{6})$');
