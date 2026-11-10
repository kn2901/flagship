package com.example.featureflags.cache;

import com.example.featureflags.evaluation.FlagSnapshot;
import com.example.featureflags.repository.FlagRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Reads a flag from the database (the source of truth) and turns it into a snapshot. */
@Component
public class FlagSnapshotLoader {

    private final FlagRepository flags;

    public FlagSnapshotLoader(FlagRepository flags) {
        this.flags = flags;
    }

    /**
     * REQUIRES_NEW because this also runs from an after-commit hook, where the finished
     * transaction can no longer be used for reads.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<FlagSnapshot> load(String flagKey) {
        return flags.findByKey(flagKey).map(FlagSnapshot::from);
    }
}
