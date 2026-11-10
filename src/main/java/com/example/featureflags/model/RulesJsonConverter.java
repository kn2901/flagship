package com.example.featureflags.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores an environment's ordered list of rules as one JSON column.
 *
 * <p>Why JSON instead of rules/conditions tables? Rules are always read and written as a whole
 * (the evaluator needs the full ordered list), so three joined tables would only add queries.
 */
@Converter
public class RulesJsonConverter implements AttributeConverter<List<TargetingRule>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<TargetingRule>> RULE_LIST = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<TargetingRule> rules) {
        return JSON.writeValueAsString(rules == null ? List.of() : rules);
    }

    @Override
    public List<TargetingRule> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(JSON.readValue(json, RULE_LIST));
    }
}
