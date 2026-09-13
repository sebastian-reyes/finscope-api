-- Moneda de cada transaccion.
--
-- Hasta aqui la moneda no existia en ningun sitio: los importes eran numeros sueltos y el
-- simbolo lo ponia el frontend al pintarlos, dando por hecho que todo era soles. Mientras
-- fuera cierto no costaba nada; deja de serlo en cuanto entra un cobro en dolares, porque
-- `100` ya no dice si son cien soles o cien dolares y no hay forma de averiguarlo despues.
--
-- El importe se queda como esta, en la moneda en que ocurrio el movimiento. No se convierte
-- ni se guarda una segunda cifra convertida: una compra de 100 dolares es 100 dolares para
-- siempre, y el dia que el tipo de cambio se mueva no puede cambiar lo que ya paso.
--
-- Por eso `exchange_rate` no participa en ningun calculo: es memoria de con que tipo de
-- cambio se registro, para poder responder «cuanto fueron en soles aquel dia». Los totales
-- no suman monedas distintas, los separan.

ALTER TABLE transactions
    ADD COLUMN currency      varchar(3),
    -- Seis decimales porque un tipo de cambio con dos redondea de mas: 3.755 y 3.7549 no
    -- son el mismo cambio sobre mil dolares.
    ADD COLUMN exchange_rate numeric(12, 6);

-- Todo lo registrado hasta hoy son soles: la aplicacion no permitia otra cosa. Va antes de
-- la restriccion de obligatoriedad para que ninguna fila se quede fuera.
UPDATE transactions
SET currency = 'PEN'
WHERE currency IS NULL;

ALTER TABLE transactions
    ALTER COLUMN currency SET NOT NULL,
    -- El valor por defecto es la moneda base. No lo necesita la aplicacion, que manda
    -- siempre la moneda, pero deja que una insercion a mano no pueda crear una fila sin
    -- decir de que moneda habla.
    ALTER COLUMN currency SET DEFAULT 'PEN';

ALTER TABLE transactions
    -- La lista de monedas vive aqui y en el enum del contrato. Anadir una es ampliar las
    -- dos, que es justo lo que se quiere que cueste: una moneda nueva necesita simbolo y
    -- formato en el cliente, no solo permiso en la base.
    ADD CONSTRAINT chk_transactions_currency
        CHECK (currency IN ('PEN', 'USD')),
    -- La moneda y su tipo de cambio son una pareja y se validan como tal. La moneda base no
    -- se convierte a si misma, asi que llevar un cambio seria tan incoherente como que a una
    -- transaccion en dolares le faltara. La capa de servicio ya lo rechaza; esto es lo que
    -- impide que un estado imposible llegue a existir aunque se escriba desde fuera.
    ADD CONSTRAINT chk_transactions_exchange_rate
        CHECK ((currency = 'PEN' AND exchange_rate IS NULL)
            OR (currency <> 'PEN' AND exchange_rate > 0));

-- Sin indice por moneda a proposito. El filtro por moneda nunca viaja solo: acompana
-- siempre al usuario y al periodo, que ya cubre `idx_transactions_user_date`, y sobre lo
-- que ese indice deja cabe descartar por moneda sin coste apreciable. Un indice mas es un
-- indice que mantener en cada alta.
