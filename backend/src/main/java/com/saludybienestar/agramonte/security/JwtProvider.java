package com.saludybienestar.agramonte.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Component
@Slf4j
public class JwtProvider {

    /**
     * Valor de respaldo de {@code jwt.secret} en application-postgres.yml y
     * application-mysql.yml. Está escrito en el repositorio, así que es público: quien
     * lo tenga puede firmarse un token de cualquier usuario, rol ADMIN incluido.
     */
    static final String SECRETO_DESARROLLO = "dev-secret-change-me-please-32charsmin";

    /** Mínimo que exige HMAC-SHA256 (256 bits); menos y la firma sería más débil que el algoritmo. */
    private static final int LONGITUD_MINIMA = 32;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Avisa al arrancar si los tokens se están firmando con el secreto de desarrollo.
     *
     * <p>No se aborta el arranque a propósito: en local ese valor es el que se usa y
     * está documentado. Lo que no puede pasar es que un servidor arranque con él sin
     * que quede constancia. En el VPS no llega a ocurrir, porque docker-compose.prod.yml
     * declara JWT_SECRET obligatorio, igual que MEDICO_PASSWORD; esto cubre cualquier
     * otra forma de despliegue. Es el mismo criterio que {@code MedicoCuentaInicializador}
     * aplica a la contraseña de demostración del médico.
     */
    @PostConstruct
    void comprobarSecreto() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        if (SECRETO_DESARROLLO.equals(jwtSecret)) {
            log.warn("Los tokens se están firmando con el secreto de desarrollo, que está " +
                    "publicado en el repositorio: cualquiera puede emitir un token válido. " +
                    "Vale para trabajar en local; en un servidor público hay que definir " +
                    "JWT_SECRET (ver .env.prod.example).");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication authentication) {
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Valida la firma y la expiración y devuelve el subject (email) en una sola lectura del token.
     * Devuelve {@link Optional#empty()} si el token es inválido, así el filtro no parsea dos veces.
     */
    public Optional<String> getUsernameIfValid(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}