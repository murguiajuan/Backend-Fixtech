package com.fixtech.security;

import com.fixtech.config.JwtProperties;
import com.fixtech.entity.Usuario;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_TIPO = "tipo";
    private static final String CLAIM_CORREO = "correo";
    private static final String CLAIM_ROL = "rol";
    private static final String TIPO_ACCESS = "ACCESS";
    private static final String TIPO_REFRESH = "REFRESH";

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtDecoder accessDecoder;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        SecretKey key = new SecretKeySpec(
                properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        this.encoder = NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validacionesBase =
                JwtValidators.createDefaultWithIssuer(properties.getIssuer());

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        jwtDecoder.setJwtValidator(validacionesBase);
        this.decoder = jwtDecoder;

        NimbusJwtDecoder accessJwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        accessJwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                validacionesBase, new TipoTokenValidator(TIPO_ACCESS)));
        this.accessDecoder = accessJwtDecoder;
    }

    public String generarAccessToken(Usuario usuario) {
        return generar(usuario, TIPO_ACCESS, Duration.ofMinutes(properties.getAccessTokenMinutos()));
    }

    public String generarRefreshToken(Usuario usuario) {
        return generar(usuario, TIPO_REFRESH, Duration.ofDays(properties.getRefreshTokenDias()));
    }

    public Jwt decodificarAccess(String token) {
        return accessDecoder.decode(token);
    }

    public Jwt decodificarRefresh(String token) {
        Jwt jwt = decoder.decode(token);
        if (!TIPO_REFRESH.equals(jwt.getClaimAsString(CLAIM_TIPO))) {
            throw new JwtException("El token no es del tipo esperado");
        }
        return jwt;
    }

    public long expiracionAccessSegundos() {
        return Duration.ofMinutes(properties.getAccessTokenMinutos()).toSeconds();
    }

    private String generar(Usuario usuario, String tipo, Duration duracion) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion))
                .subject(usuario.getId().toString())
                .claim(CLAIM_TIPO, tipo);

        if (TIPO_ACCESS.equals(tipo)) {
            claims.claim(CLAIM_CORREO, usuario.getCorreo())
                    .claim(CLAIM_ROL, usuario.getRol().name());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static final class TipoTokenValidator implements OAuth2TokenValidator<Jwt> {

        private final String tipoEsperado;

        private TipoTokenValidator(String tipoEsperado) {
            this.tipoEsperado = tipoEsperado;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (tipoEsperado.equals(token.getClaimAsString(CLAIM_TIPO))) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token", "El token no es del tipo esperado", null);
            return OAuth2TokenValidatorResult.failure(error);
        }
    }
}
