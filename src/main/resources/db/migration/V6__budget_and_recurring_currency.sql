-- Moneda en los presupuestos y en los movimientos fijos.
--
-- Las transacciones ya la llevan desde V5, pero el plan y las plantillas seguian dando por
-- hecho que todo era soles. Con un cargo fijo en dolares eso deja dos agujeros: el fijo
-- entra al historial en la moneda equivocada, y su presupuesto o no existe o cuenta cifras
-- que no se suman entre si.
--
-- El presupuesto pasa a ser de una moneda concreta, y por eso la unicidad cambia: una
-- categoria puede tener un plan en soles y otro en dolares para el mismo mes, porque son
-- dos cantidades distintas y ninguna resume a la otra. Suscripciones puede ser S/ 100 y
-- ademas $ 30 sin que nadie tenga que convertir nada.
--
-- Es ademas lo que resuelve el calculo de lo comprometido. Mientras el presupuesto no tenia
-- moneda, sumar los fijos en dolares que vencen este mes exigia una tasa que todavia no
-- existe --- la del dia en que se confirmen ---. Con la moneda en los dos lados, cada
-- presupuesto suma solo los fijos de su moneda y no hay nada que convertir.
--
-- La plantilla del fijo NO lleva tipo de cambio, y no es un olvido: un fijo se da de alta
-- hoy y se confirma dentro de meses. El cambio con el que se registre es el del dia en que
-- se pague, asi que se pide al confirmar y vive en la transaccion, como en cualquier otro
-- movimiento.

-- --- Presupuestos --------------------------------------------------------------------------

ALTER TABLE budgets
    ADD COLUMN currency varchar(3);

-- Todo lo planeado hasta hoy son soles: la aplicacion no permitia otra cosa.
UPDATE budgets
SET currency = 'PEN'
WHERE currency IS NULL;

ALTER TABLE budgets
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN currency SET DEFAULT 'PEN',
    ADD CONSTRAINT chk_budgets_currency
        CHECK (currency IN ('PEN', 'USD'));

-- La unicidad gana la moneda. Se rehace en lugar de anadirse otra: con la vieja en pie, una
-- categoria seguiria teniendo un solo presupuesto por mes y la moneda no servria de nada.
ALTER TABLE budgets
    DROP CONSTRAINT uq_budgets_user_category_period;

ALTER TABLE budgets
    ADD CONSTRAINT uq_budgets_user_category_period
        UNIQUE (user_id, category_id, month, year, currency);

-- --- Movimientos fijos ---------------------------------------------------------------------

ALTER TABLE recurring_transactions
    ADD COLUMN currency varchar(3);

UPDATE recurring_transactions
SET currency = 'PEN'
WHERE currency IS NULL;

ALTER TABLE recurring_transactions
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN currency SET DEFAULT 'PEN',
    ADD CONSTRAINT chk_recurring_currency
        CHECK (currency IN ('PEN', 'USD'));
