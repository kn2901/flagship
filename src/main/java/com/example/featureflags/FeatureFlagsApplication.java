package com.example.featureflags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FeatureFlagsApplication {

    public static void main(String[] args) {
        // Run in UTC regardless of the machine's zone. All timestamps are stored and returned in UTC,
        // and it avoids PostgreSQL rejecting legacy zone names such as "Asia/Calcutta" on connect.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(FeatureFlagsApplication.class, args);
    }
}
