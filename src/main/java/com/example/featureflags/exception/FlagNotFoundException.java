package com.example.featureflags.exception;

public class FlagNotFoundException extends RuntimeException {

    public FlagNotFoundException(String key) {
        super("No flag with key '" + key + "'");
    }
}
