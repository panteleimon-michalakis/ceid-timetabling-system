package gr.upatras.ceid.timetable.config;

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
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

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

                // ── Ανάγνωση: όλοι οι συνδεδεμένοι ──────────────────────
                .requestMatchers(HttpMethod.GET, "/api/**").authenticated()

                // ── Μαθήματα + Αίθουσες: mutations μόνο ADMIN ────────────
                .requestMatchers(HttpMethod.POST,   "/api/courses/**", "/api/rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/courses/**", "/api/rooms/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/courses/**", "/api/rooms/**").hasRole("ADMIN")

                // ── Καθηγητές ─────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST,   "/api/teachers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/teachers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/teachers/**").hasAnyRole("ADMIN", "TEACHER")

                // ── Ωρολόγια — ADMIN-only actions (πιο ειδικά πρώτα) ─────
                // Δημοσίευση / Απόσυρση: μόνο ADMIN
                .requestMatchers(HttpMethod.PUT, "/api/timetables/*/publish").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/timetables/*/unpublish").hasRole("ADMIN")
                // Δημιουργία νέου ωρολογίου: μόνο ADMIN
                .requestMatchers(HttpMethod.POST, "/api/timetables").hasRole("ADMIN")
                // Solver / auto-schedule: μόνο ADMIN
                .requestMatchers(HttpMethod.POST, "/api/timetables/*/solve").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/timetables/*/auto-schedule").hasRole("ADMIN")
                // Διαγραφή ωρολογίου: μόνο ADMIN
                .requestMatchers(HttpMethod.DELETE, "/api/timetables/*").hasRole("ADMIN")

                // ── Ωρολόγια — ADMIN + TEACHER (αναθέσεις drag & drop) ───
                // Διαγραφή assignment: ADMIN + TEACHER
                .requestMatchers(HttpMethod.DELETE, "/api/timetables/assignments/**").hasAnyRole("ADMIN", "TEACHER")
                // Δημιουργία / Μετακίνηση assignment: ADMIN + TEACHER
                .requestMatchers(HttpMethod.POST,   "/api/timetables/**").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.PUT,    "/api/timetables/**").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.DELETE, "/api/timetables/**").hasAnyRole("ADMIN", "TEACHER")

                // ── Χρονοθυρίδες: mutations μόνο ADMIN ───────────────────
                .requestMatchers(HttpMethod.POST,   "/api/timeslots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/timeslots/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/timeslots/**").hasRole("ADMIN")

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
        config.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "http://localhost:3000"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}