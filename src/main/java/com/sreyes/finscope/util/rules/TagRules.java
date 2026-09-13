package com.sreyes.finscope.util.rules;

import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.repository.TagRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reglas de los tags que necesita más de un servicio.
 * Esta clase no debe ser instanciada.
 *
 * Vive aparte porque los tags se escriben desde dos sitios —al registrar o corregir un
 * movimiento y al dar de alta o modificar un movimiento fijo— y tienen que significar lo
 * mismo en los dos: el catálogo es uno solo. Si cada servicio tuviera su copia de la
 * normalización, `Casa` escrito en un fijo podría acabar conviviendo con el `casa` de una
 * transacción y el desglose por tag repartiría el mismo contexto en dos.
 *
 * El alta contra el catálogo recibe el repositorio como parámetro en lugar de inyectarse.
 * Es lo que permite que la regla tenga una sola copia sin obligar a los servicios que ya la
 * usaban a cambiar lo que reciben al construirse.
 */
@UtilityClass
public final class TagRules {

  /**
   * Normaliza los tags de una petición recortando los espacios sobrantes, descartando los
   * vacíos y eliminando los repetidos sin distinguir mayúsculas, de modo que `Casa` y `casa`
   * no acaben conviviendo en el mismo movimiento ni en la misma plantilla. Se conserva la
   * primera grafía recibida y el orden de llegada.
   *
   * @param names nombres de tag recibidos, puede ser nulo
   * @return nombres de tag listos para persistirse
   */
  public static List<String> normalize(List<String> names) {
    if (names == null) {
      return List.of();
    }
    Map<String, String> distinct = new LinkedHashMap<>();
    names.stream()
        .filter(name -> name != null && !name.isBlank())
        .map(String::trim)
        .forEach(name -> distinct.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
    return List.copyOf(distinct.values());
  }

  /**
   * Ordena alfabéticamente unos tags, sin distinguir mayúsculas, para que el cliente los
   * reciba siempre en el mismo orden.
   *
   * @param names tags a ordenar, nulo si no hay ninguno
   * @return los tags ordenados
   */
  public static List<String> sorted(List<String> names) {
    return names == null
        ? List.of()
        : names.stream().sorted(Comparator.comparing(String::toLowerCase)).toList();
  }

  /**
   * Resuelve el identificador de cada nombre de tag, dando de alta en el catálogo del
   * usuario los que todavía no existan.
   * El alta se intenta para todos y la base de datos descarta los que ya estaban, así que
   * después basta con leer el catálogo una vez para tener los identificadores de los tags
   * nuevos y de los reutilizados.
   * Cuando el usuario escribe un tag que ya tiene con otra grafía, se reutiliza el
   * existente: `casa` sobre un `Casa` previo no crea un tag nuevo ni renombra el anterior.
   *
   * @param tagRepository repositorio del catálogo de tags
   * @param userId        identificador del usuario propietario
   * @param names         nombres de tag ya normalizados
   * @return identificadores de los tags correspondientes
   */
  public static Mono<List<Long>> resolveIds(TagRepository tagRepository, Long userId,
                                            List<String> names) {
    List<String> lowerNames = names.stream()
        .map(name -> name.toLowerCase(Locale.ROOT))
        .toList();
    return Flux.fromIterable(names)
        .concatMap(name -> tagRepository.insertIfAbsent(userId, name))
        .then(tagRepository.findByUserIdAndLowerNameIn(userId, lowerNames)
            .map(Tag::getId)
            .collectList());
  }
}
