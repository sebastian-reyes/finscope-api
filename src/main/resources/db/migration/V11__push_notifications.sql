-- Avisos al telefono: suscripciones de Web Push, preferencias y registro de lo enviado.
--
-- Hasta aqui FinScope solo avisaba de un fijo por vencer si el usuario abria la aplicacion.
-- Justo el alquiler que se olvida es el de los dias en que no se abre. Web Push permite que
-- el aviso llegue con la aplicacion cerrada: el navegador entrega una suscripcion, la API la
-- guarda y, cuando toca, manda el mensaje cifrado al servicio de push de ese navegador.

-- Una fila por dispositivo, no por usuario: el mismo usuario puede tener el movil y el
-- ordenador, y cada uno se suscribe por su cuenta.
CREATE TABLE push_subscriptions (
    id_push_subscription bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              bigint        NOT NULL REFERENCES users (id_user) ON DELETE CASCADE,
    -- La direccion a la que se manda el mensaje. Es unica porque identifica al navegador,
    -- no al usuario: si en el mismo telefono entra otra cuenta y se suscribe, la fila pasa a
    -- ser suya en lugar de repetirse, y la cuenta anterior deja de recibir avisos ahi.
    endpoint             varchar(1000) NOT NULL,
    -- Clave publica y secreto que genero el navegador. Sin ellos no se puede cifrar el
    -- mensaje, y sin cifrar el servicio de push lo rechaza.
    p256dh               varchar(120)  NOT NULL,
    auth                 varchar(40)   NOT NULL,
    created_at           timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_success_at      timestamp,
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);

-- Que avisos quiere cada usuario. La fila no existe hasta que cambia algo: sin fila, todos
-- estan encendidos, que es lo que espera quien acaba de aceptar el permiso del telefono.
CREATE TABLE notification_preferences (
    user_id       bigint  PRIMARY KEY REFERENCES users (id_user) ON DELETE CASCADE,
    recurring_due boolean NOT NULL DEFAULT true,
    budget_limit  boolean NOT NULL DEFAULT true
);

-- Lo que ya se aviso. La tarea corre cada hora y tiene que poder repetirse sin repetir
-- avisos: un reinicio, un despliegue a mitad o dos instancias a la vez no deben mandar dos
-- veces que manana vence el alquiler. La unicidad es la que lo garantiza: el aviso se
-- apunta ANTES de mandarse y solo se manda si el apunte entro.
CREATE TABLE notification_log (
    id_notification_log bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             bigint      NOT NULL REFERENCES users (id_user) ON DELETE CASCADE,
    kind                varchar(30) NOT NULL,
    -- El fijo o el presupuesto del que se aviso.
    reference_id        bigint      NOT NULL,
    -- Para que ocurrencia: la fecha de vencimiento de un fijo (2026-09-05) o el mes de un
    -- presupuesto (2026-09). El mismo alquiler vuelve a avisarse el mes siguiente.
    period_key          varchar(10) NOT NULL,
    sent_at             timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_log UNIQUE (user_id, kind, reference_id, period_key),
    CONSTRAINT chk_notification_log_kind CHECK (kind IN (
        'RECURRING_DUE_TOMORROW', 'RECURRING_DUE_TODAY',
        'BUDGET_NEAR_LIMIT', 'BUDGET_OVER_LIMIT'))
);

-- Para purgar lo viejo sin recorrer la tabla entera.
CREATE INDEX idx_notification_log_sent_at ON notification_log (sent_at);
