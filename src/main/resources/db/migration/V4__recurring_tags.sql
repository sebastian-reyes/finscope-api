-- Tags de los movimientos fijos.
--
-- Hasta aqui un fijo solo sabia de categorias, y una categoria es una sola: reparte el
-- gasto y por eso no puede hacer de contexto. Ese contexto son los tags, que se solapan
-- —el internet es `casa` y `teletrabajo` a la vez—, y el movimiento que se registra a mano
-- ya los admitia. El fijo no, asi que cada mes el alquiler entraba al historial sin
-- ninguno y lo que se veia por tag dejaba fuera justo el gasto mas previsible del mes.
--
-- Los tags viven en la plantilla y no en cada mes: se escriben una vez, al dar de alta el
-- fijo, y se copian al movimiento cada vez que se confirma. La alternativa —pedirlos al
-- confirmar— seria escribir doce veces al ano lo mismo, y el mes que se olvidara quedaria
-- sin clasificar sin que nada avisara.
--
-- La tabla es de enlace, igual que `transaction_tags`, y apunta al mismo catalogo `tags`
-- del usuario: un tag no significa una cosa en un fijo y otra en un movimiento, y tener
-- dos catalogos que hay que mantener iguales acaba con dos que no lo estan.

CREATE TABLE recurring_tags (
    id_recurring_tag bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recurring_id     bigint NOT NULL REFERENCES recurring_transactions (id_recurring) ON DELETE CASCADE,
    -- Al borrar el tag del catalogo el enlace se va con el, igual que en las
    -- transacciones: el fijo sobrevive, solo deja de estar clasificado por el.
    tag_id           bigint NOT NULL REFERENCES tags (id_tag) ON DELETE CASCADE,
    -- Un fijo lleva cada tag una vez o ninguna. Es lo que permite que guardar la misma
    -- lista dos veces no duplique nada.
    CONSTRAINT uq_recurring_tags UNIQUE (recurring_id, tag_id)
);

-- Borrar un tag del catalogo tiene que poder retirarlo de los fijos que lo llevan sin
-- recorrer la tabla entera, que es la misma razon por la que existe el de transaction_tags.
CREATE INDEX idx_recurring_tags_tag ON recurring_tags (tag_id);
