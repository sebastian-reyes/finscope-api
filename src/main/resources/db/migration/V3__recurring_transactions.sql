-- Movimientos fijos: alquiler, internet, gimnasio, el sueldo.
--
-- Un fijo es una plantilla, no un movimiento. Igual que un presupuesto, mira hacia
-- delante: dice que todos los meses hay un cargo de mas o menos tanto, no que ese cargo
-- ya ocurriera. Las transacciones siguen siendo solo lo que de verdad paso.
--
-- La plantilla no genera nada sola. Cada mes produce un pendiente que el usuario confirma,
-- y esa confirmacion es la que crea la transaccion. Autogenerar seria mas comodo un dia y
-- mentiroso todos los demas: el recibo de luz no es el mismo cada mes, la fecha real del
-- cargo se corre, y un historial que se inventa movimientos deja de servir para lo unico
-- que sirve, que es saber que paso.

CREATE TABLE recurring_transactions (
    id_recurring        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             bigint         NOT NULL REFERENCES users (id_user) ON DELETE CASCADE,
    -- Al borrar una categoria sus fijos se reasignan a la de reserva, igual que las
    -- transacciones: perder el alquiler por reordenar el catalogo seria desproporcionado.
    -- El cascade esta para el borrado de la cuenta entera, donde categorias y fijos se van
    -- juntos y el orden en que la base los recorre no debe poder bloquear la operacion.
    category_id         bigint         NOT NULL REFERENCES categories (id_category) ON DELETE CASCADE,
    -- El tipo va en la plantilla porque un fijo tambien puede ser un ingreso: el sueldo es
    -- lo mas recurrente que existe y es justo lo que hace falta para saber cuanto queda
    -- libre en el mes.
    transaction_type_id bigint         NOT NULL REFERENCES transaction_types (id_transaction_type),
    description         varchar(70)    NOT NULL,
    -- Lo que se suele pagar, no lo definitivo. Al confirmar se puede corregir, y lo
    -- corregido vive en la transaccion; la plantilla conserva la estimacion.
    amount              numeric(12, 2) NOT NULL,
    -- Dia previsto dentro del mes. Se admite hasta 31 y se recorta al ultimo dia de los
    -- meses cortos al calcular el vencimiento: un cargo del 31 en febrero es el 28.
    day_of_month        integer        NOT NULL,
    -- Cada cuantos meses toca, contando desde el mes de arranque: 1 mensual, 2 bimestral,
    -- 3 trimestral, 12 anual. Un entero en lugar de un enum porque el seguro anual del auto
    -- entra sin anadir ningun valor nuevo al dominio.
    every_months        integer        NOT NULL DEFAULT 1,
    -- Mes desde el que aplica. Sin ancla, un fijo dado de alta en septiembre apareceria
    -- como impagado en enero, y ademas es lo que da sentido a `every_months`: cada dos
    -- meses tiene que ser cada dos meses *contados desde alguno*.
    start_month         integer        NOT NULL,
    start_year          integer        NOT NULL,
    -- Pausar en lugar de borrar. Si cancelas el gimnasio y borras la plantilla, pierdes los
    -- seis meses en que si lo pagaste.
    active              boolean        NOT NULL DEFAULT true,
    CONSTRAINT chk_recurring_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_recurring_day CHECK (day_of_month BETWEEN 1 AND 31),
    CONSTRAINT chk_recurring_every_months CHECK (every_months BETWEEN 1 AND 12),
    CONSTRAINT chk_recurring_start_month CHECK (start_month BETWEEN 1 AND 12),
    CONSTRAINT chk_recurring_start_year CHECK (start_year BETWEEN 1970 AND 9999)
);

-- La pantalla y la tarjeta del inicio siempre parten del usuario y piden todos sus fijos.
CREATE INDEX idx_recurring_user ON recurring_transactions (user_id);

-- Meses que el usuario decide saltarse.
--
-- Sin esta tabla, un mes que no pagas se queda en rojo hasta que el mes acaba y sigue
-- contando como comprometido contra el presupuesto de su categoria, que es justo lo que no
-- es. Se guarda la excepcion y no un estado en la plantilla porque la omision es de un mes
-- concreto y la plantilla vive por encima de los meses.
CREATE TABLE recurring_skips (
    id_recurring_skip bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recurring_id      bigint  NOT NULL REFERENCES recurring_transactions (id_recurring) ON DELETE CASCADE,
    month             integer NOT NULL,
    year              integer NOT NULL,
    CONSTRAINT chk_recurring_skips_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT chk_recurring_skips_year CHECK (year BETWEEN 1970 AND 9999),
    -- Un fijo se omite una vez por mes o ninguna. Es lo que permite que volver a omitir no
    -- haga nada y que deshacer la omision sea un solo borrado.
    CONSTRAINT uq_recurring_skips UNIQUE (recurring_id, month, year)
);

-- Enlace entre el movimiento real y la plantilla que lo origino.
--
-- Es lo unico que permite responder «ya pague el alquiler de septiembre». La alternativa
-- seria adivinarlo comparando importe, categoria y descripcion, y se rompe el primer mes
-- que pagas de mas o corriges el texto.
--
-- Se anula al borrar la transaccion, no se cascadea: si borras el movimiento, el fijo
-- vuelve a estar pendiente, que es exactamente lo que ha pasado.
ALTER TABLE transactions
    ADD COLUMN recurring_id bigint REFERENCES recurring_transactions (id_recurring) ON DELETE SET NULL;

-- El estado de cada fijo se resuelve buscando su movimiento dentro del mes. El indice es
-- parcial porque la inmensa mayoria de las transacciones no vienen de ninguna plantilla.
CREATE INDEX idx_transactions_recurring ON transactions (recurring_id, date)
    WHERE recurring_id IS NOT NULL;
