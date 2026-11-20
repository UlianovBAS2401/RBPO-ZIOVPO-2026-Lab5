package ru.utility.accounting.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessExpirationSeconds;
    private final long refreshExpirationSeconds;

    public JwtService(
            @Value("${jwt.secret:c2VjdXJlLWtleS1mb3Itand0LWFjY2Vzcy1yZWZyZXNoLXRva2Vucy11dGlsaXR5LWFjY291bnRpbmc=}") String secret,
            @Value("${jwt.access-token-expiration-seconds:900}") long accessExpirationSeconds,
            @Value("${jwt.refresh-token-expiration-seconds:604800}") long refreshExpirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    public String generateAccessToken(String username, String role, Instant issuedAt, Instant expiresAt) {
        String jti = UUID.randomUUID().toString();
        var builder = Jwts.builder()
                .id(jti)
                .subject(username)
                .claim("type", "access")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256);

        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }

    public String generateRefreshToken(String username, Instant issuedAt, Instant expiresAt) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .id(jti)
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateAccessToken(String token) {
        Claims claims = parseClaims(token);
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            throw new JwtException("Неверный тип токена: ожидается 'access', получен: " + type);
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new JwtException("Отсутствует обязательный claim 'sub'");
        }
        if (claims.getId() == null || claims.getId().isBlank()) {
            throw new JwtException("Отсутствует обязательный claim 'jti'");
        }
        return claims;
    }

    public Claims validateRefreshToken(String token) {
        Claims claims = parseClaims(token);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new JwtException("Неверный тип токена: ожидается 'refresh', получен: " + type);
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new JwtException("Отсутствует обязательный claim 'sub'");
        }
        if (claims.getId() == null || claims.getId().isBlank()) {
            throw new JwtException("Отсутствует обязательный claim 'jti'");
        }
        return claims;
    }

    public Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("Токен пуст или отсутствует");
        }
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
