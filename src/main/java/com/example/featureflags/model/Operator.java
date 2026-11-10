package com.example.featureflags.model;

/**
 * How a condition compares a user attribute with the condition's values.
 *
 * <p>Every operator except {@link #NOT_IN} matches when the attribute matches ANY of the values,
 * e.g. {@code country IN [IN, LK]}. {@code NOT_IN} matches when it matches NONE of them.
 * Number operators compare numerically and never match text that isn't a number.
 */
public enum Operator {
    /** Equal to one of the values (exact, case-sensitive). */
    IN,
    /** Equal to none of the values. */
    NOT_IN,
    /** Contains one of the values, e.g. email CONTAINS "@acme.com". */
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    /** Number comparisons, e.g. {@code accountAgeDays GREATER_THAN 30}. Uses the first value only. */
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL
}
