-- Verificacion del correo, cambio de correo y restablecimiento de contrasena.
--
-- Hasta aqui el correo era un dato que se escribia una vez al registrarse y del que nadie
-- comprobaba nada. Eso deja dos agujeros: una cuenta cuyo correo tenga una letra de mas no
-- puede recuperar la contrasena --- el enlace se va a una direccion que no es de nadie ---,
-- y quien se equivoca al registrarse no tiene forma de arreglarlo desde la aplicacion.
--
-- Las tres operaciones son la misma pieza vista tres veces: un valor aleatorio que se manda
-- por correo, caduca pronto y vale una sola vez. Por eso viven en una sola tabla con un
-- proposito, y no en tres: la generacion, el hashing, la caducidad y el consumo son
-- identicos, y repartirlos en tres tablas repetiria ese codigo tres veces con tres
-- oportunidades de que una de ellas olvide comprobar algo.
--
-- Del token se guarda solo su hash SHA-256, igual que en `refresh_tokens`: quien pueda leer
-- la tabla no puede fabricar con ella un enlace utilizable. No se usa BCrypt porque aqui no
-- hay nada que ralentizar: el valor lo genera el servidor con 32 bytes de entropia, no lo
-- elige una persona, asi que no hay diccionario contra el que probar.

ALTER TABLE users
    ADD COLUMN email_verified boolean NOT NULL DEFAULT false;

-- Las cuentas que ya existen nacen sin verificar, y es lo correcto: de ninguna de ellas se
-- ha comprobado nunca que su correo exista. No pierden nada --- verificar no condiciona el
-- acceso ---, solo ganan el aviso para hacerlo.

CREATE TABLE account_tokens (
    id_account_token bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint       NOT NULL REFERENCES users (id_user) ON DELETE CASCADE,
    purpose          varchar(30)  NOT NULL,
    token_hash       varchar(64)  NOT NULL,
    -- Solo lo lleva el cambio de correo: es la direccion a la que se movera la cuenta
    -- cuando se confirme. Vive aqui y no en `users` para que el correo de la cuenta siga
    -- siendo el viejo hasta el ultimo momento; una direccion mal escrita se queda en un
    -- token que caduca, no en una cuenta a la que ya no se puede entrar.
    target_email     varchar(255),
    expires_at       timestamp    NOT NULL,
    consumed_at      timestamp,
    created_at       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_account_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_account_tokens_purpose
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE')),
    -- La direccion nueva y el proposito van juntos: un cambio de correo sin direccion no
    -- podria aplicarse, y una direccion en un token de otro proposito no la mirara nadie.
    CONSTRAINT chk_account_tokens_target_email
        CHECK ((purpose = 'EMAIL_CHANGE') = (target_email IS NOT NULL))
);

-- Se consulta de dos formas: por hash al confirmar, que ya cubre el indice unico, y por
-- usuario y proposito al emitir uno nuevo, para invalidar los anteriores. Pedir otro enlace
-- tiene que dejar sin valor al de antes: si no, cada peticion sumaria una llave mas a la
-- misma puerta.
CREATE INDEX idx_account_tokens_user_purpose ON account_tokens (user_id, purpose);

-- Los caducados y los ya usados no se borran al vuelo. Se quedan hasta la siguiente emision
-- del mismo proposito, que es cuando se limpian, para que el rastro de lo ocurrido sobre
-- una cuenta no desaparezca en el mismo instante en que ocurre.
