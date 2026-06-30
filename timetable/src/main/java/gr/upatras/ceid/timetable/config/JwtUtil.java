package gr.upatras.ceid.timetable.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** Το δημόσια γνωστό dev default — ΠΟΤΕ δεν επιτρέπεται με ενεργό 'prod' profile. */
    private static final String DEV_DEFAULT_SECRET = "ceid-timetable-dev-secret-key-32-chars-min";

    @Value("${jwt.secret}")
    private String secret;

    @Autowired
    private Environment environment;

    private static final long EXPIRY_MS = 24 * 60 * 60 * 1000L; // 24h

    /**
     * Fail-fast (profile-aware): αν τρέχουμε με ενεργό 'prod' profile ΚΑΙ το dev default
     * secret, σταμάτα την εκκίνηση — αλλιώς θα υπογράφαμε JWT με δημόσια γνωστό κλειδί
     * (token forgery / privilege escalation σε ADMIN). Σε dev/test: μόνο warn, γιατί τα
     * tests μπουτάρουν με το dev config και ένα hard fail θα τα έσπαγε όλα.
     */
    @PostConstruct
    void validateJwtSecret() {
        if (!DEV_DEFAULT_SECRET.equals(secret)) {
            return; // δόθηκε custom secret — OK
        }
        boolean prodActive = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p -> p.toLowerCase().contains("prod"));
        if (prodActive) {
            throw new IllegalStateException(
                "Refusing to start: the well-known dev JWT secret is in use under a 'prod' profile. "
                + "Set the JWT_SECRET environment variable to a strong, unique value before deploying.");
        }
        log.warn("JWT secret is the public dev default — acceptable for dev/test ONLY. "
            + "Set JWT_SECRET to a strong value in production.");
    }

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
