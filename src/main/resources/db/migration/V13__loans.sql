-- Prestamos que el usuario hace a otras personas: a quien, cuanto, cuando lo devuelve.
--
-- Un prestamo no es un movimiento. Prestar no es gastar ni cobrar es ingresar: si el
-- prestamo entrara como egreso, el analisis del mes diria que se gasto en «Juan» lo que en
-- realidad sigue siendo tuyo, y la devolucion inflaria los ingresos del mes en que llegue.
-- Por eso vive aparte, como los presupuestos y los fijos, y no escribe ninguna transaccion.

CREATE TABLE loans (
    id_loan       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint         NOT NULL REFERENCES users (id_user) ON DELETE CASCADE,
    -- A quien se le presto. Texto libre y no un catalogo de contactos: casi siempre son
    -- pocas personas y conocidas, y un catalogo pediria darlas de alta antes de prestar.
    borrower_name varchar(70)    NOT NULL,
    -- Lo que salio de tu bolsillo.
    principal     numeric(12, 2) NOT NULL,
    -- Lo que se gana encima, como un monto fijo y no como una tasa: entre personas se
    -- acuerda «me devuelves 1100», no un porcentaje mensual. Cero es un prestamo sin
    -- interes, el de apoyar a alguien, y es el caso mas comun.
    interest      numeric(12, 2) NOT NULL DEFAULT 0,
    currency      varchar(3)     NOT NULL DEFAULT 'PEN',
    -- El dia en que se entrego el dinero. Es fecha y no instante: la hora no aporta nada y
    -- obligaria a decidir en que zona se interpreta.
    lent_on       date           NOT NULL,
    notes         varchar(255),
    -- Dar el prestamo por cerrado sin que se haya devuelto todo: se perdono el resto o no se
    -- va a recuperar. Se cierra en lugar de borrarse para conservar lo que si se devolvio,
    -- y un prestamo cerrado deja de avisar.
    closed_on     date,
    created_at    timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_loans_principal_positive CHECK (principal > 0),
    CONSTRAINT chk_loans_interest_non_negative CHECK (interest >= 0),
    CONSTRAINT chk_loans_currency CHECK (currency IN ('PEN', 'USD'))
);

CREATE INDEX idx_loans_user ON loans (user_id);

-- Plan de devolucion: lo que se acordo, no lo que paso. Un prestamo que se devuelve de una
-- vez tiene una sola cuota. La suma de las cuotas es siempre capital mas interes; lo
-- comprueba el servicio, porque la base no puede expresar una restriccion entre filas.
CREATE TABLE loan_installments (
    id_loan_installment bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    loan_id             bigint         NOT NULL REFERENCES loans (id_loan) ON DELETE CASCADE,
    -- Orden de la cuota dentro del plan, desde 1. Es el que decide a que cuota se aplica
    -- cada pago.
    number              integer        NOT NULL,
    due_date            date           NOT NULL,
    amount              numeric(12, 2) NOT NULL,
    CONSTRAINT chk_loan_installments_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_loan_installments_number CHECK (number >= 1),
    CONSTRAINT uq_loan_installments_number UNIQUE (loan_id, number)
);

-- La tarea de avisos busca cuotas por fecha de vencimiento entre todos los usuarios.
CREATE INDEX idx_loan_installments_due_date ON loan_installments (due_date);

-- Lo que de verdad se devolvio. Los pagos no se atan a una cuota: se aplican en orden a la
-- mas antigua que siga pendiente. Asi un pago parcial o uno adelantado se registran igual,
-- sin pedir al usuario que decida a que cuota corresponde, y el estado de cada cuota se
-- calcula al leer en lugar de guardarse y poder desincronizarse.
CREATE TABLE loan_payments (
    id_loan_payment bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    loan_id         bigint         NOT NULL REFERENCES loans (id_loan) ON DELETE CASCADE,
    paid_on         date           NOT NULL,
    amount          numeric(12, 2) NOT NULL,
    note            varchar(140),
    created_at      timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_loan_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_loan_payments_loan ON loan_payments (loan_id);

-- --- Avisos ---------------------------------------------------------------------------------

-- Encendido por defecto, igual que los otros dos: quien acepto el permiso del telefono
-- espera enterarse de que le toca cobrar.
ALTER TABLE notification_preferences
    ADD COLUMN loan_due boolean NOT NULL DEFAULT true;

-- Tres avisos por cuota: cuando entra en los tres dias previos, el dia que vence y, si sigue
-- sin cubrirse, al dia siguiente.
ALTER TABLE notification_log
    DROP CONSTRAINT chk_notification_log_kind;

ALTER TABLE notification_log
    ADD CONSTRAINT chk_notification_log_kind CHECK (kind IN (
        'RECURRING_DUE_TOMORROW', 'RECURRING_DUE_TODAY',
        'BUDGET_NEAR_LIMIT', 'BUDGET_OVER_LIMIT',
        'LOAN_DUE_SOON', 'LOAN_DUE_TODAY', 'LOAN_OVERDUE'));
