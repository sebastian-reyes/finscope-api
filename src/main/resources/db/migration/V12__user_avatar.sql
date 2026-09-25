-- Imagen de perfil elegida por el usuario.
--
-- No es una foto: es una de las ilustraciones que trae el cliente (un zorro, un gato, una
-- llama...). Subir fotos obligaria a guardar archivos, recortarlos y servirlos, y para algo
-- que solo ve la propia persona no compensa. Vive en la fila y no en el navegador porque es
-- parte de quien eres en la aplicacion: tiene que verse igual en todos tus dispositivos.
--
-- Como el icono de las categorias (V10), se guarda el nombre de la ilustracion y la base
-- solo comprueba su forma, no que exista: el catalogo es cosa del cliente, y un nombre que
-- no conozca se pinta como si no hubiera ninguno.
--
-- Nulo significa lo de siempre: las iniciales del nombre, o del correo si no lo hay.

ALTER TABLE users
    ADD COLUMN avatar varchar(20) NULL,
    ADD CONSTRAINT ck_users_avatar CHECK (avatar ~ '^[a-z]+(-[a-z]+)*$');
