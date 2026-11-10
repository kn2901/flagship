package com.example.featureflags.integration;

import com.example.featureflags.cache.FlagCache;
import com.example.featureflags.cache.RedisSharedFlagCache;
import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.UpdateEnvironmentRequest;
import com.example.featureflags.evaluation.FlagSnapshot;
import com.example.featureflags.evaluation.UserContext;
import com.example.featureflags.model.Environment;
import com.example.featureflags.repository.AuditLogRepository;
import com.example.featureflags.repository.FlagRepository;
import com.example.featureflags.service.EvaluationService;
import com.example.featureflags.service.FlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs against a real PostgreSQL and Redis (the "docker" profile). Start them first:
 * <pre>
 *   docker compose up -d
 *   INTEGRATION_TESTS=true ./mvnw test
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("docker")
@TestPropertySource(properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/featureflags")
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TESTS", matches = "true")
class RedisPostgresIntegrationTest {

    private static final String KEY = "it-new-payment-system";

    @Autowired private FlagService flagService;
    @Autowired private EvaluationService evaluationService;
    @Autowired private FlagCache flagCache;
    @Autowired private FlagRepository flagRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JsonMapper json;

    @BeforeEach
    void cleanUp() {
        flagRepository.findByKey(KEY).ifPresent(flagRepository::delete);
        redis.delete("ff:flag:" + KEY);
        flagCache.clear();
    }

    private boolean enabledFor(String userId) {
        return evaluationService.evaluate(KEY, Environment.PROD, UserContext.of(userId)).enabled();
    }

    @Test
    void aChangeIsWrittenToRedisAfterCommit() {
        flagService.create(new CreateFlagRequest(KEY, "New payment system", null, null), "it");
        flagService.updateEnvironment(KEY, Environment.PROD,
                new UpdateEnvironmentRequest(true, 100, null, null), "it");

        String cached = redis.opsForValue().get("ff:flag:" + KEY);
        assertThat(cached).isNotNull();
        FlagSnapshot snapshot = json.readValue(cached, FlagSnapshot.class);
        assertThat(snapshot.environments().get(Environment.PROD).enabled()).isTrue();
        assertThat(redis.getExpire("ff:flag:" + KEY)).isPositive();
        assertThat(enabledFor("alice")).isTrue();
        assertThat(auditLogRepository.findByFlagKeyOrderByIdDesc(KEY,
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aChangeAnnouncedByAnotherInstanceIsPickedUpFromRedis() {
        flagService.create(new CreateFlagRequest(KEY, "New payment system", null, null), "it");
        flagService.updateEnvironment(KEY, Environment.PROD,
                new UpdateEnvironmentRequest(true, 100, null, null), "it");
        assertThat(enabledFor("alice")).isTrue(); // now in this instance's memory

        // Pretend another instance switched the flag off: it writes Redis and announces the change.
        FlagSnapshot switchedOff = new FlagSnapshot(KEY, null, Map.of(Environment.PROD,
                new FlagSnapshot.EnvironmentConfig(false, 100, java.util.Set.of(), java.util.List.of())));
        redis.opsForValue().set("ff:flag:" + KEY, json.writeValueAsString(switchedOff));
        redis.convertAndSend(RedisSharedFlagCache.CHANGES_CHANNEL, KEY);

        await().atMost(Duration.ofSeconds(2)).until(() -> !enabledFor("alice"));
    }
}
