package gr.upatras.ceid.timetable.repository;

import gr.upatras.ceid.timetable.entity.ConstraintWeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConstraintWeightConfigRepository
        extends JpaRepository<ConstraintWeightConfig, Long> {

    boolean existsByConstraintKey(String constraintKey);

    Optional<ConstraintWeightConfig> findByConstraintKey(String constraintKey);
}
