-- Token que sustituyo a cada token de refresco al rotarlo.
--
-- Una renovacion cuya respuesta no llega al cliente --- el telefono suspende la aplicacion
-- con la peticion en vuelo, o se corta la red --- deja el token viejo gastado en el servidor
-- y guardado en el cliente. La siguiente vez que este lo presenta, fuera ya del margen de
-- renovacion concurrente, se tomaba por una copia robada y se cerraba la sesion en todos los
-- dispositivos. Le pasaba a quien abria la aplicacion varias veces al dia.
--
-- Con el enlace se distingue ese caso del robo: si el sustituto no se ha usado nunca, nadie
-- mas ha seguido la cadena y la respuesta, simplemente, se perdio. Se anula el sustituto y se
-- emite otro. Si el sustituto ya se uso, el token viejo esta en dos manos y se revoca todo,
-- como hasta ahora.

ALTER TABLE refresh_tokens
    ADD COLUMN replaced_by bigint NULL
        REFERENCES refresh_tokens (id_refresh_token) ON DELETE SET NULL;
