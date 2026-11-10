package com.example.featureflags.service;

import com.example.featureflags.cache.FlagCache;
import com.example.featureflags.dto.EvaluationResponse;
import com.example.featureflags.evaluation.FlagEvaluator;
import com.example.featureflags.evaluation.FlagSnapshot;
import com.example.featureflags.evaluation.UserContext;
import com.example.featureflags.exception.FlagNotFoundException;
import com.example.featureflags.model.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * The hot path: called on every "is this feature on?" request.
 * Reads from the cache only (no transaction, no entities) and runs the pure evaluator.
 */
@Service
public class EvaluationService {

    private final FlagCache cache;
    private final FlagEvaluator evaluator;
    private final Clock clock;

    public EvaluationService(FlagCache cache, FlagEvaluator evaluator, Clock clock) {
        this.cache = cache;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    public EvaluationResponse evaluate(String flagKey, Environment environment, UserContext user) {
        FlagSnapshot flag = cache.get(flagKey).orElseThrow(() -> new FlagNotFoundException(flagKey));
        FlagEvaluator.Result result = evaluator.evaluate(
                flag, flag.environments().get(environment), user, clock.instant());
        return new EvaluationResponse(flagKey, environment, user.id(),
                result.enabled(), result.reason(), result.ruleIndex());
    }
}
