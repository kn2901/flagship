package com.example.featureflags.controller;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateEnvironmentRequest;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.model.Environment;
import com.example.featureflags.service.FlagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin API: what a dashboard would call to manage flags.
 *
 * <p>The optional {@code X-Actor} header names who made a change, for the audit log.
 * (In a real deployment this would come from the logged-in user, not a header.)
 */
@RestController
@RequestMapping("/api/flags")
public class FlagController {

    static final String ACTOR_HEADER = "X-Actor";
    static final String ANONYMOUS = "anonymous";

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlagResponse create(@Valid @RequestBody CreateFlagRequest request,
                               @RequestHeader(value = ACTOR_HEADER, defaultValue = ANONYMOUS) String actor) {
        return flagService.create(request, actor);
    }

    @GetMapping
    public List<FlagResponse> list() {
        return flagService.list();
    }

    @GetMapping("/{key}")
    public FlagResponse get(@PathVariable String key) {
        return flagService.get(key);
    }

    @PatchMapping("/{key}")
    public FlagResponse update(@PathVariable String key,
                               @Valid @RequestBody UpdateFlagRequest request,
                               @RequestHeader(value = ACTOR_HEADER, defaultValue = ANONYMOUS) String actor) {
        return flagService.update(key, request, actor);
    }

    @PatchMapping("/{key}/environments/{environment}")
    public FlagResponse updateEnvironment(@PathVariable String key,
                                          @PathVariable Environment environment,
                                          @Valid @RequestBody UpdateEnvironmentRequest request,
                                          @RequestHeader(value = ACTOR_HEADER, defaultValue = ANONYMOUS) String actor) {
        return flagService.updateEnvironment(key, environment, request, actor);
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String key,
                       @RequestHeader(value = ACTOR_HEADER, defaultValue = ANONYMOUS) String actor) {
        flagService.delete(key, actor);
    }
}
