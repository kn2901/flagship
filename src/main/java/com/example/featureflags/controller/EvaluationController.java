package com.example.featureflags.controller;

import com.example.featureflags.dto.EvaluationRequest;
import com.example.featureflags.dto.EvaluationResponse;
import com.example.featureflags.evaluation.UserContext;
import com.example.featureflags.model.Environment;
import com.example.featureflags.service.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The API an application calls at runtime to ask "is this feature on for this user?". */
@RestController
@RequestMapping("/api/evaluate")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** Full version: send the user's attributes so attribute rules (plan, country, ...) can apply. */
    @PostMapping
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluationRequest request) {
        return evaluationService.evaluate(request.flagKey(), request.environment(), request.user());
    }

    /** Quick version for curl and browsers: user id only, so only id targeting and rollouts apply. */
    @GetMapping
    public EvaluationResponse evaluate(@RequestParam String flagKey,
                                       @RequestParam Environment environment,
                                       @RequestParam String userId) {
        return evaluationService.evaluate(flagKey, environment, UserContext.of(userId));
    }
}
