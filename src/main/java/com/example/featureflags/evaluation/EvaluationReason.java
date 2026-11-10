package com.example.featureflags.evaluation;

/** Why an evaluation came out the way it did. Very useful when debugging "why don't I see the feature?". */
public enum EvaluationReason {
    /** The flag's switch is off in this environment. */
    FLAG_OFF,
    /** The flag's expiry date has passed, so it is off everywhere. */
    FLAG_EXPIRED,
    /** The user is on the flag's list of targeted users. */
    TARGETED_USER,
    /** The user matched a rule and their bucket is inside that rule's percentage. */
    RULE_MATCH,
    /** The user matched a rule but their bucket is outside that rule's percentage. */
    RULE_MATCH_NOT_IN_ROLLOUT,
    /** No rule matched; the user's bucket is inside the default rollout percentage. */
    IN_ROLLOUT,
    /** No rule matched; the user's bucket is outside the default rollout percentage. */
    NOT_IN_ROLLOUT
}
