package com.example.featureflags.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Injecting the clock (instead of calling Instant.now()) lets tests move time forward. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
