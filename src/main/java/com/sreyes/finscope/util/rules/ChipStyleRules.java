package com.sreyes.finscope.util.rules;

import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * Reglas del aspecto elegido para una categoría o un tag: su color y su icono.
 * Esta clase no debe ser instanciada.
 *
 * Los dos campos funcionan igual. Cada uno admite un valor propio —una ficha de la paleta
 * (`preset-0` a `preset-7`) o un color libre (`#rrggbb`); el nombre de un icono (`basket`)— o
 * `auto`, que devuelve la ficha a lo que el cliente deduce de su nombre. El formato lo comprueba
 * ya la validación de la petición; aquí solo se traduce a lo que se guarda.
 *
 * <p>`auto` no se guarda como texto sino como nulo, que es lo que valían todas las fichas
 * antes de poder elegirse: así una fila vieja y una devuelta a automático son la misma cosa.
 * El resto se guarda en minúsculas porque la restricción de la base solo admite esa grafía, y
 * `#FFAA00` y `#ffaa00` no deben ser dos colores distintos.</p>
 */
@UtilityClass
public final class ChipStyleRules {

  /** Valor del contrato que devuelve el campo a lo deducido del nombre. */
  public static final String AUTO = "auto";

  /**
   * Traduce el valor recibido al que se guarda.
   *
   * @param requested color o icono tal y como llega en la petición, ya validado y no nulo
   * @return el valor a guardar, nulo si se pide el automático
   */
  public static String toStored(String requested) {
    return AUTO.equalsIgnoreCase(requested) ? null : requested.toLowerCase(Locale.ROOT);
  }
}
