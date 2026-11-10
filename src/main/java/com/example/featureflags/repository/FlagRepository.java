package com.example.featureflags.repository;

import com.example.featureflags.model.Flag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Spring Data writes the SQL for these methods from their names. */
public interface FlagRepository extends JpaRepository<Flag, Long> {

    Optional<Flag> findByKey(String key);

    boolean existsByKey(String key);

    /** Flags whose expiry date has passed. */
    List<Flag> findByExpiresAtLessThanEqual(Instant now);
}
