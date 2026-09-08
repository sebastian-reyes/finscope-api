package com.sreyes.finscope.util.patch;

import java.util.function.Consumer;
import lombok.experimental.UtilityClass;

/**
 * Ayuda para aplicar modificaciones parciales sobre una entidad ya guardada.
 * Esta clase no debe ser instanciada.
 *
 * Las peticiones de modificación traen informados solo los campos que cambian, así que
 * cada uno se copia bajo la misma condición. Escrita campo a campo, esa condición se repite
 * tantas veces como campos tenga la entidad y esconde la lista de lo que se puede cambiar
 * detrás de la lógica de cuándo se cambia. Recogida aquí, cada servicio vuelve a enumerar
 * sus campos y nada más.
 *
 * Un nulo significa «no lo toques», nunca «bórralo»: no hay forma de vaciar un campo
 * mandando nulo, y por eso los campos opcionales que de verdad se puedan limpiar
 * necesitarán su propia decisión explícita el día que existan.
 */
@UtilityClass
public final class Patches {

  /**
   * Copia el valor recibido sobre la entidad solo si viene informado.
   *
   * @param value  valor recibido en la petición, puede ser nulo
   * @param setter operación que asienta el valor sobre la entidad
   * @param <T>    tipo del campo a modificar
   */
  public static <T> void setIfPresent(T value, Consumer<T> setter) {
    if (value != null) {
      setter.accept(value);
    }
  }
}
