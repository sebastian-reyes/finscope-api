package com.sreyes.finscope.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configuración de seguridad de la aplicación.
 * La API es sin estado y se protege con tokens firmados, por lo que se desactivan CSRF,
 * el formulario de acceso y la autenticación básica. Solo los endpoints de alta y de
 * obtención de credenciales quedan abiertos; el resto exige un token válido.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

  /**
   * Rutas accesibles sin autenticación, necesarias para poder obtener credenciales.
   */
  private static final String[] PUBLIC_PATHS = {
      "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout"
  };

  /**
   * Define la cadena de filtros de seguridad de la aplicación.
   *
   * @param http            constructor de la configuración de seguridad
   * @param entryPoint      punto de entrada que responde a las peticiones sin credenciales
   * @param accessDenied    manejador de las peticiones sin permisos suficientes
   * @return la cadena de filtros configurada
   */
  @Bean
  public SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      RestAuthenticationEntryPoint entryPoint,
      RestAccessDeniedHandler accessDenied) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .authorizeExchange(exchanges -> exchanges
            .pathMatchers(PUBLIC_PATHS).permitAll()
            .anyExchange().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }))
        .exceptionHandling(handling -> handling
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(accessDenied))
        .build();
  }

  /**
   * Construye el emisor de tokens firmados con la clave configurada.
   *
   * @param jwtProperties configuración de los tokens
   * @return el emisor de tokens
   */
  @Bean
  public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(jwtProperties)));
  }

  /**
   * Construye el validador de tokens entrantes con la misma clave que los emite.
   *
   * @param jwtProperties configuración de los tokens
   * @return el validador reactivo de tokens
   */
  @Bean
  public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties jwtProperties) {
    return NimbusReactiveJwtDecoder.withSecretKey(secretKey(jwtProperties))
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  /**
   * Algoritmo de hashing de contraseñas.
   *
   * @return el codificador de contraseñas
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Deriva la clave de firma HMAC a partir del secreto configurado.
   *
   * @param jwtProperties configuración de los tokens
   * @return la clave de firma
   */
  private SecretKeySpec secretKey(JwtProperties jwtProperties) {
    return new SecretKeySpec(jwtProperties.secret().getBytes(), "HmacSHA256");
  }
}
