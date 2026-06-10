package gr.upatras.ceid.timetable.config;

import gr.upatras.ceid.timetable.entity.User;
import gr.upatras.ceid.timetable.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepo) {
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.valid(token)) {
                String username = jwtUtil.username(token);

                // Επικύρωση ότι ο χρήστης υπάρχει ΚΑΙ είναι ενεργός.
                // Έτσι ένα token που εκδόθηκε πριν την απενεργοποίηση/διαγραφή
                // του λογαριασμού παύει αμέσως να ισχύει.
                Optional<User> userOpt = userRepo.findByUsername(username);
                if (userOpt.isPresent() && Boolean.TRUE.equals(userOpt.get().getActive())) {
                    // Ο ρόλος λαμβάνεται από τη ΒΔ (πηγή αλήθειας), όχι μόνο από το token.
                    String role = userOpt.get().getRole().name();
                    var auth = new UsernamePasswordAuthenticationToken(
                        username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(req, res);
    }
}