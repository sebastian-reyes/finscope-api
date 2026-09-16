-- Momento en que un token de refresco se canjeo por otro.
--
-- Hasta aqui `revoked` no distinguia por que un token habia dejado de valer: rotado, cerrado
-- con la sesion o revocado junto al resto de la cuenta. Esa diferencia importa desde que la
-- renovacion tolera la concurrencia. Dos peticiones que refrescan a la vez con el mismo
-- token --- dos pestanas, la aplicacion instalada y el navegador --- son el caso normal, no
-- un robo, pero la segunda llegaba con el token ya revocado, se tomaba por una copia robada
-- y cerraba la sesion en todos los dispositivos.
--
-- Con esta columna, un token revocado hace muy poco POR ROTACION se acepta como carrera. Un
-- token cerrado con la sesion o revocado en bloque la deja a nulo, y ese nunca vuelve a
-- servir.

ALTER TABLE refresh_tokens
    ADD COLUMN rotated_at timestamp NULL;
