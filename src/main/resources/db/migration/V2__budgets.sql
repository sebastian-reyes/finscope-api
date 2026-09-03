-- Presupuestos por categoria y mes.
--
-- El presupuesto es el plan; las transacciones son lo que de verdad paso. Se guardan
-- aparte porque son cosas distintas: borrar un movimiento no debe borrar la intencion de
-- gastar, y fijar un presupuesto no crea ningun movimiento.
--
-- La unidad es el mes natural, la misma con la que abre el dashboard, y no un rango
-- libre: un sueldo se piensa por meses y un presupuesto que empezara un martes cualquiera
-- no podria compararse con el del mes anterior.

CREATE TABLE budgets (
    id_budget   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint         NOT NULL REFERENCES users (id_user) ON DELETE CASCADE,
    -- Al borrar la categoria se lleva sus presupuestos por delante. Las transacciones se
    -- reasignan a la de reserva porque no pueden quedarse sin categoria, pero un
    -- presupuesto reasignado se sumaria al que esa categoria ya pudiera tener en el mismo
    -- mes, y eso choca con la unicidad de mas abajo.
    category_id bigint         NOT NULL REFERENCES categories (id_category) ON DELETE CASCADE,
    month       integer        NOT NULL,
    year        integer        NOT NULL,
    amount      numeric(12, 2) NOT NULL,
    CONSTRAINT chk_budgets_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_budgets_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT chk_budgets_year CHECK (year BETWEEN 1970 AND 9999),
    -- Una categoria tiene como mucho un presupuesto por mes. Es lo que permite copiar el
    -- mes anterior sin duplicar nada y lo que hace que el avance sea un solo numero.
    CONSTRAINT uq_budgets_user_category_period UNIQUE (user_id, category_id, month, year)
);

-- La consulta de la pantalla siempre parte del usuario y de un mes concreto.
CREATE INDEX idx_budgets_user_period ON budgets (user_id, year, month);
