package com.example.featureflags.controller;

import com.example.featureflags.MutableClock;
import com.example.featureflags.cache.FlagCache;
import com.example.featureflags.repository.AuditLogRepository;
import com.example.featureflags.repository.FlagRepository;
import com.example.featureflags.service.FlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Starts the whole application (with an in-memory database) and calls the real HTTP endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FlagApiTest.TestClock.class)
class FlagApiTest {

    static final Instant START = Instant.parse("2026-11-10T10:00:00Z");

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FlagRepository flagRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private FlagCache flagCache;

    @Autowired
    private FlagService flagService;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void startWithAnEmptyDatabase() {
        flagRepository.deleteAll();
        auditLogRepository.deleteAll();
        flagCache.clear();
        clock.set(START);
    }

    private void evaluateAs(String json, boolean expectedEnabled, String expectedReason) throws Exception {
        mockMvc.perform(post("/api/evaluate").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(expectedEnabled))
                .andExpect(jsonPath("$.reason").value(expectedReason));
    }

    private void createFlag(String key) throws Exception {
        mockMvc.perform(post("/api/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"" + key + "\", \"name\": \"Test flag\"}"))
                .andExpect(status().isCreated());
    }

    private void updateProd(String key, String json) throws Exception {
        mockMvc.perform(patch("/api/flags/" + key + "/environments/PROD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void aNewFlagIsOffInEveryEnvironment() throws Exception {
        mockMvc.perform(post("/api/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "new-payment-system", "name": "New payment system",
                                 "description": "Switch checkout to the new provider"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("new-payment-system"))
                .andExpect(jsonPath("$.environments.DEV.enabled").value(false))
                .andExpect(jsonPath("$.environments.STAGING.enabled").value(false))
                .andExpect(jsonPath("$.environments.PROD.enabled").value(false))
                .andExpect(jsonPath("$.environments.PROD.rolloutPercentage").value(100));
    }

    @Test
    void creatingTheSameKeyTwiceIsAConflict() throws Exception {
        createFlag("dark-mode");

        mockMvc.perform(post("/api/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"dark-mode\", \"name\": \"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A flag with key 'dark-mode' already exists"));
    }

    @Test
    void aBadKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"Has Spaces\", \"name\": \"Bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsFlagsSortedByKey() throws Exception {
        createFlag("zebra");
        createFlag("apple");

        mockMvc.perform(get("/api/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].key").value("apple"))
                .andExpect(jsonPath("$[1].key").value("zebra"));
    }

    @Test
    void updatingOneEnvironmentLeavesTheOthersAlone() throws Exception {
        createFlag("dark-mode");

        mockMvc.perform(patch("/api/flags/dark-mode/environments/PROD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true, "rolloutPercentage": 25, "targetedUsers": ["alice", "bob"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments.PROD.enabled").value(true))
                .andExpect(jsonPath("$.environments.PROD.rolloutPercentage").value(25))
                .andExpect(jsonPath("$.environments.PROD.targetedUsers", containsInAnyOrder("alice", "bob")))
                .andExpect(jsonPath("$.environments.DEV.enabled").value(false));
    }

    @Test
    void fieldsLeftOutOfAnUpdateKeepTheirValue() throws Exception {
        createFlag("dark-mode");
        updateProd("dark-mode", "{\"enabled\": true, \"rolloutPercentage\": 25}");
        updateProd("dark-mode", "{\"enabled\": false}");

        mockMvc.perform(get("/api/flags/dark-mode"))
                .andExpect(jsonPath("$.environments.PROD.enabled").value(false))
                .andExpect(jsonPath("$.environments.PROD.rolloutPercentage").value(25));
    }

    @Test
    void aRolloutAbove100IsRejected() throws Exception {
        createFlag("dark-mode");

        mockMvc.perform(patch("/api/flags/dark-mode/environments/PROD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolloutPercentage\": 150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("rolloutPercentage must be between 0 and 100"));
    }

    @Test
    void evaluationFollowsTheFlagSettings() throws Exception {
        createFlag("dark-mode");

        // Off by default.
        mockMvc.perform(get("/api/evaluate?flagKey=dark-mode&environment=PROD&userId=alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("FLAG_OFF"));

        // On in PROD for 0% of users, but alice is targeted.
        updateProd("dark-mode", "{\"enabled\": true, \"rolloutPercentage\": 0, \"targetedUsers\": [\"alice\"]}");

        mockMvc.perform(get("/api/evaluate?flagKey=dark-mode&environment=PROD&userId=alice"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("TARGETED_USER"));
        mockMvc.perform(get("/api/evaluate?flagKey=dark-mode&environment=PROD&userId=carol"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("NOT_IN_ROLLOUT"));

        // DEV was never touched, so it is still off for alice.
        mockMvc.perform(get("/api/evaluate?flagKey=dark-mode&environment=DEV&userId=alice"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("FLAG_OFF"));
    }

    @Test
    void unknownFlagsAndEnvironmentsGiveClearErrors() throws Exception {
        mockMvc.perform(get("/api/flags/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No flag with key 'does-not-exist'"));

        mockMvc.perform(get("/api/evaluate?flagKey=does-not-exist&environment=PROD&userId=alice"))
                .andExpect(status().isNotFound());

        createFlag("dark-mode");
        mockMvc.perform(get("/api/evaluate?flagKey=dark-mode&environment=MOON&userId=alice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("environment must be one of [DEV, STAGING, PROD]"));
    }

    @Test
    void aDeletedFlagIsGone() throws Exception {
        createFlag("dark-mode");
        updateProd("dark-mode", "{\"enabled\": true, \"targetedUsers\": [\"alice\"]}");

        mockMvc.perform(delete("/api/flags/dark-mode")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/flags/dark-mode")).andExpect(status().isNotFound());
    }

    private static final String PREMIUM_INDIA_RULE = """
            {"enabled": true, "rolloutPercentage": 0, "rules": [
              {"description": "Premium users in India",
               "conditions": [
                 {"attribute": "plan",    "operator": "IN", "values": ["premium"]},
                 {"attribute": "country", "operator": "IN", "values": ["IN"]}],
               "rolloutPercentage": 100}]}
            """;

    @Test
    void rulesTargetUsersByTheirAttributes() throws Exception {
        createFlag("new-payment-system");
        updateProd("new-payment-system", PREMIUM_INDIA_RULE);

        mockMvc.perform(get("/api/flags/new-payment-system"))
                .andExpect(jsonPath("$.environments.PROD.rules[0].conditions", hasSize(2)))
                .andExpect(jsonPath("$.environments.PROD.rules[0].conditions[1].attribute").value("country"));

        evaluateAs("""
                {"flagKey": "new-payment-system", "environment": "PROD",
                 "user": {"id": "user-1", "attributes": {"plan": "premium", "country": "IN"}}}
                """, true, "RULE_MATCH");
        evaluateAs("""
                {"flagKey": "new-payment-system", "environment": "PROD",
                 "user": {"id": "user-1", "attributes": {"plan": "premium", "country": "US"}}}
                """, false, "NOT_IN_ROLLOUT");
        evaluateAs("""
                {"flagKey": "new-payment-system", "environment": "PROD", "user": {"id": "user-1"}}
                """, false, "NOT_IN_ROLLOUT");
    }

    @Test
    void invalidRulesAreRejected() throws Exception {
        createFlag("new-payment-system");

        mockMvc.perform(patch("/api/flags/new-payment-system/environments/PROD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules": [{"conditions": [], "rolloutPercentage": 150}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "a rule needs at least one condition; rule rolloutPercentage must be between 0 and 100"));

        mockMvc.perform(patch("/api/flags/new-payment-system/environments/PROD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules": [{"conditions": [{"attribute": "plan", "operator": "LIKE", "values": ["x"]}],
                                            "rolloutPercentage": 10}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluationSeesAChangeImmediatelyDespiteTheCache() throws Exception {
        createFlag("dark-mode");
        updateProd("dark-mode", "{\"enabled\": true}");
        mockMvc.perform(get("/api/evaluate?flagKey=dark-mode&environment=PROD&userId=alice"))
                .andExpect(jsonPath("$.enabled").value(true)); // now cached

        updateProd("dark-mode", "{\"enabled\": false}");

        mockMvc.perform(get("/api/evaluate?flagKey=dark-mode&environment=PROD&userId=alice"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("FLAG_OFF"));
    }

    @Test
    void everyChangeIsAuditedWithWhoDidItAndTheBeforeAndAfterState() throws Exception {
        mockMvc.perform(post("/api/flags").header("X-Actor", "priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"dark-mode\", \"name\": \"Dark mode\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/flags/dark-mode/environments/PROD").header("X-Actor", "rahul")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true, \"rolloutPercentage\": 10}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/flags/dark-mode")).andExpect(status().isNoContent());

        // History is still there after the flag is deleted, newest first.
        mockMvc.perform(get("/api/flags/dark-mode/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].action").value("FLAG_DELETED"))
                .andExpect(jsonPath("$.items[0].actor").value("anonymous"))
                .andExpect(jsonPath("$.items[0].after").value(nullValue()))
                .andExpect(jsonPath("$.items[1].action").value("ENVIRONMENT_UPDATED"))
                .andExpect(jsonPath("$.items[1].environment").value("PROD"))
                .andExpect(jsonPath("$.items[1].actor").value("rahul"))
                .andExpect(jsonPath("$.items[1].before.enabled").value(false))
                .andExpect(jsonPath("$.items[1].before.rolloutPercentage").value(100))
                .andExpect(jsonPath("$.items[1].after.enabled").value(true))
                .andExpect(jsonPath("$.items[1].after.rolloutPercentage").value(10))
                .andExpect(jsonPath("$.items[2].action").value("FLAG_CREATED"))
                .andExpect(jsonPath("$.items[2].actor").value("priya"))
                .andExpect(jsonPath("$.items[2].after.key").value("dark-mode"));

        mockMvc.perform(get("/api/audit-logs?size=2"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void anExpiredFlagIsOffImmediatelyAndTheJobSwitchesItOffAndAuditsIt() throws Exception {
        mockMvc.perform(post("/api/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "diwali-banner", "name": "Diwali banner", "expiresAt": "2026-11-15T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2026-11-15T00:00:00Z"))
                .andExpect(jsonPath("$.expired").value(false));
        updateProd("diwali-banner", "{\"enabled\": true}");
        mockMvc.perform(get("/api/evaluate?flagKey=diwali-banner&environment=PROD&userId=alice"))
                .andExpect(jsonPath("$.enabled").value(true));

        clock.advance(Duration.ofDays(6));

        // Off straight away, even before the job runs (and even though the result was cached).
        mockMvc.perform(get("/api/evaluate?flagKey=diwali-banner&environment=PROD&userId=alice"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("FLAG_EXPIRED"));

        assertThat(flagService.switchOffExpiredFlags()).isEqualTo(1);
        assertThat(flagService.switchOffExpiredFlags()).isZero(); // already off, nothing to do

        mockMvc.perform(get("/api/flags/diwali-banner"))
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.environments.PROD.enabled").value(false));
        mockMvc.perform(get("/api/flags/diwali-banner/audit-logs"))
                .andExpect(jsonPath("$.items[0].action").value("FLAG_EXPIRED"))
                .andExpect(jsonPath("$.items[0].actor").value("system"));
    }

    @Test
    void theExpiryCanBeChangedOrRemoved() throws Exception {
        mockMvc.perform(post("/api/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"experiment\", \"name\": \"Experiment\", \"expiresAt\": \"2026-12-01T00:00:00Z\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/flags/experiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\": \"2027-01-01T00:00:00Z\", \"name\": \"Longer experiment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value("2027-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.name").value("Longer experiment"));

        mockMvc.perform(patch("/api/flags/experiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"removeExpiry\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(nullValue()));
    }

    @Test
    void anExpiryInThePastIsRejected() throws Exception {
        mockMvc.perform(post("/api/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\": \"old\", \"name\": \"Old\", \"expiresAt\": \"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("expiresAt must be in the future"));
    }
}
