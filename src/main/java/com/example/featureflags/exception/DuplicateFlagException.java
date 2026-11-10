package com.example.featureflags.exception;

public class DuplicateFlagException extends RuntimeException {

    public DuplicateFlagException(String key) {
        super("A flag with key '" + key + "' already exists");
    }
}
