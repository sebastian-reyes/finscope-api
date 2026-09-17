-- Icono elegido para las categorias y los tags.
--
-- Igual que el color de V9: hasta aqui el cliente deducia el icono buscando palabras dentro
-- del nombre, asi que una categoria llamada `Perro` salia con una etiqueta generica y no
-- habia forma de corregirlo. Ahora puede fijarse, y vive en la fila para verse igual en
-- todos los dispositivos.
--
-- Se guarda el nombre del icono sin prefijo (`basket`, `airplane`), no la clase CSS
-- (`bi-basket`): el catalogo de iconos es cosa del cliente y la base no tiene por que saber
-- con que libreria se pintan. Por lo mismo la restriccion solo comprueba la forma del nombre
-- y no que el icono exista; un nombre que el cliente no conozca se pinta con el deducido.
--
-- Nulo significa lo de siempre: el icono se deduce del nombre.

ALTER TABLE categories
    ADD COLUMN icon varchar(40) NULL,
    ADD CONSTRAINT ck_categories_icon CHECK (icon ~ '^[a-z0-9]+(-[a-z0-9]+)*$');

ALTER TABLE tags
    ADD COLUMN icon varchar(40) NULL,
    ADD CONSTRAINT ck_tags_icon CHECK (icon ~ '^[a-z0-9]+(-[a-z0-9]+)*$');
