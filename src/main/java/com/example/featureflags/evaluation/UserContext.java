package com.example.featureflags.evaluation;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * The user a flag is being evaluated for.
 *
 * @param id         stable user id; used for targeting and to pick the rollout bucket
 * @param attributes anything rules can match on, e.g. {"plan": "premium", "country": "IN"}
 */
public record UserContext(
        @NotBlank(message = "user id is required")
        String id,

        Map<String, String> attributes
) {

    public UserContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static UserContext of(String id) {
        return new UserContext(id, Map.of());
    }

    /** {@code "userId"} always means the id; anything else is looked up in the attributes. */
    public String attribute(String name) {
        if ("userId".equals(name)) {
            return id;
        }
        return attributes.get(name);
    }
}
