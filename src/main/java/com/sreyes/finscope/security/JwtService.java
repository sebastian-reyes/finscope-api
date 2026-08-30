package com.sreyes.finscope.security;

import com.sreyes.finscope.model.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Servicio de emisión de tokens de acceso.
 * El identificador del usuario viaja en el `subject` del token, que es lo que después
 * permite aislar los datos de cada cuenta sin consultar la base de datos en cada petición.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

  private final JwtEncoder jwtEncoder;
  private final JwtProperties jwtProperties;

  /**
   * Emite un token de acceso para el usuario indicado.
   *
   * @param user usuario autenticado
   * @return el token de acceso firmado
   */
  public String issueAccessToken(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(jwtProperties.issuer())
        .audience(List.of(jwtProperties.audience()))
        .issuedAt(now)
        .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
        .subject(String.valueOf(user.getId()))
        .claim("email", user.getEmail())
        .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /**
   * Indica cuántos segundos permanece válido un token de acceso recién emitido.
   *
   * @return la validez del token de acceso en segundos
   */
  public long accessTokenExpiresInSeconds() {
    return jwtProperties.accessTokenTtl().get(ChronoUnit.SECONDS);
  }
}
