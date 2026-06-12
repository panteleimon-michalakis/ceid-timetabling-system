package gr.upatras.ceid.timetable.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    // Comma-separated origins. Σε παραγωγή: ορίζεται μέσω env var CORS_ORIGINS.
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                // ── Δημόσια endpoints ─────────────────────────────────────
                .requestMatchers("/api/auth/**").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()

                // Τεκμηρίωση API (Swagger UI / OpenAPI JSON)

                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()


                // ── Διαχείριση χρηστών ────────────────────────────────────
                // Αλλαγή δικού μου κωδικού: κάθε συνδεδεμένος χρήστης.
                .requestMatchers(HttpMethod.PUT, "/api/users/me/password").authenticated()
                // Όλα τα υπόλοιπα /api/users/**: μόνο ADMIN (πριν το γενικό GET!)
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                // ── Ωρολόγια — ADMIN-only actions (πιο ειδικά πρώτα) ─────
                .requestMatchers(HttpMethod.PUT, "/api/timetables/*/publish").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/timetables/*/unpublish").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/timetables").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/timetables/*/solve").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/timetables/*/auto-schedule").hasRole("ADMIN")
                // Μαζική δημιουργία exam slots: μόνο ADMIN
                .requestMatchers(HttpMethod.POST, "/api/timetables/generate-exam-slots").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/timetables/*/generate-exam-slots").hasRole("ADMIN")
                // Μαζικό καθάρισμα αναθέσεων ενός προγράμματος: μόνο ADMIN
                .requestMatchers(HttpMethod.DELETE, "/api/timetables/*/assignments").hasRole("ADMIN")
                // Διαγραφή ωρολογίου: μόνο ADMIN
                .requestMatchers(HttpMethod.DELETE, "/api/timetables/*").hasRole("ADMIN")

                // ── Ωρολόγια — ADMIN + TEACHER (αναθέσεις drag & drop) ───
                .requestMatchers(HttpMethod.DELETE, "/api/timetables/assignments/**").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.POST,   "/api/timetables/**").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.PUT,    "/api/timetables/**").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.DELETE, "/api/timetables/**").hasAnyRole("ADMIN", "TEACHER")

                // ── Μαθήματα + Αίθουσες: mutations μόνο ADMIN ────────────
                .requestMatchers(HttpMethod.POST,   "/api/courses/**", "/api/rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/courses/**", "/api/rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/courses/**", "/api/rooms/**").hasRole("ADMIN")

                // ── Καθηγητές ─────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST,   "/api/teachers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/teachers/**").hasRole("ADMIN")
                // TEACHER επιτρέπεται PUT, αλλά ο controller ελέγχει ownership
                // (μπορεί να επεξεργαστεί ΜΟΝΟ τον δικό του φάκελο/διαθεσιμότητα).
                .requestMatchers(HttpMethod.PUT,    "/api/teachers/**").hasAnyRole("ADMIN", "TEACHER")

                // ── Χρονοθυρίδες: mutations μόνο ADMIN ───────────────────
                .requestMatchers(HttpMethod.POST,   "/api/timeslots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/timeslots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/timeslots/**").hasRole("ADMIN")

                // ── Ανάγνωση: όλοι οι συνδεδεμένοι ──────────────────────
                .requestMatchers(HttpMethod.GET, "/api/**").authenticated()

                // ── Οτιδήποτε άλλο: συνδεδεμένος χρήστης ────────────────
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
            Arrays.stream(allowedOrigins.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isBlank())
                  .toList()
        );
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}