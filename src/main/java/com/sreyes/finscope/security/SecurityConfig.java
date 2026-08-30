package com.sreyes.finscope.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sreyes.finscope.security.ratelimit.RateLimitProperties;
import com.sreyes.finscope.security.ratelimit.RateLimitWebFilter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Configuración de seguridad de la aplicación.
 * La API es sin estado y se protege con tokens firmados que el cliente envía en la cabecera
 * de autorización, por lo que se desactivan CSRF, el formulario de acceso y la autenticación
 * básica: sin cookies de sesión no hay credencial que el navegador adjunte por su cuenta,
 * que es lo que CSRF explota. Solo los endpoints de alta y de obtención de credenciales
 * quedan abiertos; el resto exige un token válido.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class,
    RateLimitProperties.class, LoginAttemptProperties.class})
public class SecurityConfig {

  /**
   * Rutas accesibles sin autenticación, necesarias para poder obtener credenciales.
   * El estado de la aplicación se incluye porque lo consultan el contenedor y la
   * plataforma, que no tienen credenciales; se publica sin desglose para que decir que
   * está viva no sea también decir de qué está hecha.
   */
  private static final String[] PUBLIC_PATHS = {
      "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
      "/actuator/health", "/actuator/health/**"
  };

  /**
   * Margen admitido al comparar la caducidad del token con el reloj del servidor.
   * Cubre la deriva normal entre máquinas sin llegar a prolongar la validez del token.
   */
  private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

  /**
   * Política de contenido de la API. No sirve documentos ni carga recursos, así que puede
   * negarlo todo: si alguna respuesta acabara interpretándose como página, no ejecutaría
   * nada. La aplicación web se sirve aparte y no se ve afectada por esta cabecera.
   */
  private static final String CONTENT_SECURITY_POLICY =
      "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

  /**
   * Funciones del navegador que la API no necesita en ningún caso.
   */
  private static final String PERMISSIONS_POLICY =
      "geolocation=(), camera=(), microphone=(), payment=(), usb=()";

  /**
   * Define la cadena de filtros de seguridad de la aplicación.
   *
   * @param http                    constructor de la configuración de seguridad
   * @param entryPoint              punto de entrada para las peticiones sin credenciales
   * @param accessDenied            manejador de las peticiones sin permisos suficientes
   * @param corsConfigurationSource orígenes autorizados a consumir la API
   * @return la cadena de filtros configurada
   */
  @Bean
  public SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http,
      RestAuthenticationEntryPoint entryPoint,
      RestAccessDeniedHandler accessDenied,
      CorsConfigurationSource corsConfigurationSource) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .headers(headers -> headers
            .frameOptions(frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
            .hsts(hsts -> hsts.includeSubdomains(true).maxAge(Duration.ofDays(365)))
            .referrerPolicy(referrer -> referrer.policy(
                ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
            .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
            .permissionsPolicy(permissions -> permissions.policy(PERMISSIONS_POLICY)))
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
   * Declara qué orígenes pueden consumir la API desde un navegador.
   * La lista sale de la configuración para que desarrollo y producción no compartan un
   * mismo valor permisivo. Sin orígenes configurados no se autoriza ninguno.
   *
   * @param corsProperties orígenes autorizados
   * @return la configuración de CORS aplicable a toda la API
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
    configuration.setAllowedMethods(CorsProperties.ALLOWED_METHODS);
    configuration.setAllowedHeaders(CorsProperties.ALLOWED_HEADERS);
    configuration.setExposedHeaders(CorsProperties.EXPOSED_HEADERS);
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(corsProperties.maxAge());
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * Registra el filtro que limita las peticiones por origen.
   * Se declara aquí, junto al resto de la seguridad, y no como componente descubierto por
   * exploración, para que las pruebas que solo levantan una porción de la aplicación no
   * arrastren un límite que no están probando.
   *
   * @param rateLimitProperties límites configurados
   * @param errorResponseWriter escritor de la respuesta de error
   * @return el filtro de limitación de peticiones
   */
  @Bean
  public RateLimitWebFilter rateLimitWebFilter(RateLimitProperties rateLimitProperties,
                                               ErrorResponseWriter errorResponseWriter) {
    return new RateLimitWebFilter(rateLimitProperties, errorResponseWriter);
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
   * El algoritmo se fija a HS256 para que la cabecera del token no pueda elegir otro, y
   * además de la caducidad se comprueban el emisor, el destinatario y la presencia del
   * sujeto: es el sujeto el que decide a qué datos se accede, así que un token sin él no
   * puede aceptarse aunque su firma sea válida.
   *
   * @param jwtProperties configuración de los tokens
   * @return el validador reactivo de tokens
   */
  @Bean
  public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties jwtProperties) {
    NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
        .withSecretKey(secretKey(jwtProperties))
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
    decoder.setJwtValidator(jwtValidator(jwtProperties));
    return decoder;
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
   * Compone las comprobaciones que debe superar un token para darse por válido.
   *
   * @param jwtProperties configuración de los tokens
   * @return el validador combinado
   */
  private OAuth2TokenValidator<Jwt> jwtValidator(JwtProperties jwtProperties) {
    return new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(CLOCK_SKEW),
        new JwtIssuerValidator(jwtProperties.issuer()),
        new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
            audience -> audience != null && audience.contains(jwtProperties.audience())),
        new JwtClaimValidator<String>(JwtClaimNames.SUB,
            subject -> subject != null && !subject.isBlank()));
  }

  /**
   * Deriva la clave de firma HMAC a partir del secreto configurado.
   *
   * @param jwtProperties configuración de los tokens
   * @return la clave de firma
   */
  private SecretKeySpec secretKey(JwtProperties jwtProperties) {
    return new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8),
        "HmacSHA256");
  }
}
