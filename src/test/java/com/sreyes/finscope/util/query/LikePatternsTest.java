package com.sreyes.finscope.util.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de {@link LikePatterns}, centradas en que el texto escrito por el
 * usuario se compare como texto y no como patrón.
 */
class LikePatternsTest {

  @Test
  @DisplayName("Envuelve el término para que case con cualquier texto que lo contenga")
  void wrapsTermInWildcards() {
    assertEquals("%dentista%", LikePatterns.contains("dentista"));
  }

  @Test
  @DisplayName("Escapa los comodines del término para que se busquen literalmente")
  void escapesWildcardsInTerm() {
    assertEquals("%50\\%%", LikePatterns.contains("50%"));
    assertEquals("%a\\_b%", LikePatterns.contains("a_b"));
    assertEquals("%c\\\\d%", LikePatterns.contains("c\\d"));
  }

  @Test
  @DisplayName("Un término vacío casa con cualquier descripción, pero nunca con su ausencia")
  void wrapsEmptyTerm() {
    assertEquals("%%", LikePatterns.contains(""));
  }
}
