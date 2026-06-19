package gr.upatras.ceid.timetable.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Persisted, επεξεργάσιμο βάρος ενός solver constraint (S4b-2a).
 *
 * Ένα row ανά κανόνα· το {@code constraintKey} ταιριάζει 1-1 με τα keys του
 * {@link gr.upatras.ceid.timetable.solver.SolverWeights}. Ο
 * {@code ConstraintWeightSeeder} γεμίζει τον πίνακα από το catalog (insert-if-
 * absent). ΠΡΟΣΟΧΗ (S4b-2a): ο solver ΔΕΝ διαβάζει ακόμα από εδώ — το overlay
 * στα live βάρη έρχεται στο S4b-2b.
 */
@Entity
@Table(
    name = "constraint_weight_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_constraint_weight_config_key", columnNames = "constraint_key")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConstraintWeightConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Μοναδικό scope-prefixed key (π.χ. "WEEKLY_ROOM_CONFLICT"). */
    @Column(name = "constraint_key", nullable = false, length = 80)
    private String constraintKey;

    /** WEEKLY | EXAM. */
    @Column(name = "scope", length = 10)
    private String scope;

    /** HARD | SOFT (κλειδωμένο στο UI — βλ. recon §D). */
    @Column(name = "score_level", length = 4)
    private String scoreLevel;

    /** Base coefficient· default = catalog defaultWeight. */
    @Column(name = "weight", nullable = false)
    private int weight;

    /** Ενεργό; (disable = βάρος·0 — guard για HARD στο UI). */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Σύντομο ελληνικό label (UI-facing). */
    @Column(name = "display_name", length = 120)
    private String displayName;

    /** Μία πρόταση: τι ποινικοποιεί ο κανόνας. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Μελλοντική παραμετροποίηση (π.χ. thresholds)· αφήνεται κενό στο 2a. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb")
    private String params;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
