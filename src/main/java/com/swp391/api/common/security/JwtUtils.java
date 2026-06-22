package com.swp391.api.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long expirationMillis;

    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.expiration}") long expirationMillis) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    // -------------------------------------------------------------------------
    // Generate
    // -------------------------------------------------------------------------

    public String generateToken(String subject, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // -------------------------------------------------------------------------
    // Validate & Parse
    // -------------------------------------------------------------------------

    /**
     * Validates signature and expiry. Returns true if valid.
     */
    public boolean validateToken(String token) {
        return validateJwtToken(token);
    }

    // Alias used by JwtAuthenticationFilter
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(authToken);
            return true;
        } catch (Exception e) {
            System.err.println("Token không hợp lệ hoặc đã hết hạn: " + e.getMessage());
        }
        return false;
    }

    /**
     * Extracts the subject (email) from the token.
     */
    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    // Alias used by JwtAuthenticationFilter
    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Extracts the role claim from the token.
     */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // Alias used by JwtAuthenticationFilter
    public String getRoleFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}