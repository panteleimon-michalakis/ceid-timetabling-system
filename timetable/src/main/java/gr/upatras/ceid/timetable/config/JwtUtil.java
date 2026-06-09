package gr.upatras.ceid.timetable.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private static final long EXPIRY_MS = 24 * 60 * 60 * 1000L; // 24h

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(String username, String role) {
        return Jwts.builder()
            .subject(username)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
            .signWith(key())
            .compact();
    }

    public String username(String token) {
        return claims(token).getSubject();
    }

    public String role(String token) {
        return claims(token).get("role", String.class);
    }

    public boolean valid(String token) {
        try { claims(token); return true; }
        catch (Exception e) { return false; }
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key()).build()
            .parseSignedClaims(token).getPayload();
    }
}
