package com.sreyes.finscope.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreyes.finscope.service.impl.CategoryServiceImpl;
import com.sreyes.finscope.service.impl.RecurringTransactionServiceImpl;
import com.sreyes.finscope.service.impl.TagServiceImpl;
import com.sreyes.finscope.service.impl.TransactionCommandServiceImpl;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Comprueba sobre el contexto real que las escrituras de varias tablas quedan en una
 * transacción. Las pruebas de cada servicio no pueden verlo: construyen la clase con
 * {@code new} y ahí no hay proxy, así que quitar un {@code @Transactional} —o que Spring dejara
 * de aplicarlo— no rompería ninguna.
 *
 * <p>Levanta la aplicación sin base de datos, igual que el entrenamiento de la cache AOT del
 * Dockerfile: el pool arranca sin conexiones y Flyway queda apagado, así que la dirección de
 * la base no tiene que existir.</p>
 */
@SpringBootTest(properties = {
    "spring.profiles.active=dev",
    "spring.flyway.enabled=false",
    "spring.r2dbc.url=r2dbc:postgresql://localhost:1/sin-base",
    "spring.r2dbc.pool.initial-size=0",
    "finscope.security.jwt.secret=clave-de-prueba-de-al-menos-32-bytes-de-largo",
    "finscope.security.cors.allowed-origins=http://localhost:4200"
})
class TransactionBoundariesTest {

  /**
   * Métodos que escriben en más de una tabla y deben hacerlo todo o nada. El alta, el cambio
   * de correo y el restablecimiento de contraseña no están aquí porque solo envuelven una parte
   * con {@link TransactionalOperator}; eso lo comprueban sus propias pruebas.
   */
  private static final Map<Class<?>, Set<String>> ATOMIC_METHODS = Map.of(
      TransactionCommandServiceImpl.class, Set.of("createTransaction", "updateTransaction"),
      RecurringTransactionServiceImpl.class,
      Set.of("createRecurring", "updateRecurring", "confirmRecurring"),
      CategoryServiceImpl.class, Set.of("deleteCategory"),
      TagServiceImpl.class, Set.of("deleteTag"));

  @Autowired
  private ApplicationContext context;

  @Autowired
  private TransactionAttributeSource transactionAttributeSource;

  @Test
  @DisplayName("Hay un único gestor de transacciones, el reactivo, y su operador")
  void usesTheReactiveTransactionManager() {
    assertThat(context.getBeansOfType(ReactiveTransactionManager.class)).hasSize(1);
    assertThat(context.getBeansOfType(TransactionalOperator.class)).hasSize(1);
  }

  @Test
  @DisplayName("Las escrituras de varias tablas van envueltas en una transacción")
  void wrapsMultiTableWritesInATransaction() {
    ATOMIC_METHODS.forEach((type, names) -> {
      Object bean = context.getBean(type);
      assertThat(AopUtils.isAopProxy(bean)).as(type.getSimpleName() + " proxied").isTrue();
      Set<String> found = Arrays.stream(type.getDeclaredMethods())
          .filter(method -> names.contains(method.getName()))
          .filter(method -> transactionAttributeSource
              .getTransactionAttribute(method, type) != null)
          .map(Method::getName)
          .collect(java.util.stream.Collectors.toSet());
      assertThat(found).as(type.getSimpleName()).isEqualTo(names);
    });
  }
}
