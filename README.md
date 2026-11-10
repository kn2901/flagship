# Flagship: Feature Flag Service

A simplified [LaunchDarkly](https://launchdarkly.com)-style feature flag service in **Java 21 + Spring Boot 4**.
Developers switch features on or off, per environment and per user, without deploying new code.

Example: a new payment system can be turned on for **10% of users**, **only premium users**,
**only users in India**, or any combination of these, and switched off instantly if something breaks.

## Features

- **Flags with separate settings per environment** (`DEV`, `STAGING`, `PROD`); a new flag starts off everywhere
- **User targeting**: specific user ids always get the feature
- **Attribute rules**: `plan IN [premium] AND country IN [IN]` with 9 operators (`IN`, `NOT_IN`, `CONTAINS`,
  `STARTS_WITH`, `ENDS_WITH`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`)
- **Percentage rollouts** that are deterministic (a user never flickers between on and off), both per rule and as a default
- **Flag expiry**: temporary flags switch off by themselves at a set date
- **Audit log** of every change: who, what, when, with the full before/after state
- **Two-level cache** (in-memory → Redis → PostgreSQL) with pub/sub invalidation across instances
- **Keeps working if Redis goes down**: a circuit breaker sends requests straight to the database

## Architecture

```
                 ┌──────────────── Admin API ────────────────┐
  dashboard ───► │ FlagController → FlagService              │──► PostgreSQL (flags, audit_logs)
                 │   change + audit entry in ONE transaction │
                 └──────────────┬────────────────────────────┘
                                │ after commit: write fresh snapshot to Redis
                                │               + publish "flag X changed"
                                ▼
                     Redis  ff:flag:<key>  +  channel ff:flag-changes
                                │ every instance drops its in-memory copy
                                ▼
                 ┌──────────── Evaluation API (hot path) ────┐
  your app ────► │ EvaluationController → EvaluationService  │
                 │   FlagCache: memory → Redis → database     │
                 │   FlagEvaluator: pure function, no I/O     │
                 └────────────────────────────────────────────┘
```

### How a flag is evaluated

`FlagEvaluator` checks these in order, and the first one that applies decides the result:

| # | Check | Result | `reason` |
|---|-------|--------|----------|
| 1 | Switch off in this environment | off | `FLAG_OFF` |
| 2 | Expiry date has passed | off | `FLAG_EXPIRED` |
| 3 | User id is on the targeted list | on | `TARGETED_USER` |
| 4 | First rule whose conditions **all** match | on if the user's bucket < the rule's % | `RULE_MATCH` / `RULE_MATCH_NOT_IN_ROLLOUT` |
| 5 | Nothing matched | on if the user's bucket < the default % | `IN_ROLLOUT` / `NOT_IN_ROLLOUT` |

The response includes the `reason` (and `ruleIndex`), so "why can't I see the feature?" can be answered from the response alone.

### Deterministic percentage rollouts

```java
bucket = floorMod(first4Bytes(sha256(flagKey + ":" + userId)), 100)   // 0..99
enabled = bucket < rolloutPercentage
```

- **The same user always gets the same answer.** A random number would flip the feature on and off between requests.
- **The flag key is part of the hash**, so the "lucky 10%" is a different group of users for each flag.
- **Raising a percentage only adds users.** At 10%, buckets 0–9 are in; at 20%, buckets 0–19 are. Nobody loses the feature.

Tests check that a 10% rollout over 100,000 users lands between 9.5% and 10.5%, and that raising a percentage never removes a user.

### Caching and consistency

The evaluation path never touches JPA entities or opens a transaction. It reads an immutable `FlagSnapshot`:

1. **In-memory map** on each instance, which serves almost every request
2. **Redis**, shared by all instances, used after a restart or a change
3. **PostgreSQL**, the source of truth, used only on a Redis miss

How it stays correct:

- **Changes reach every instance at once.** After a change **commits**, the service writes the fresh snapshot to Redis
  and publishes the key on `ff:flag-changes`. Every instance then drops its in-memory copy.
  Rolled-back changes are never cached (`@TransactionalEventListener(AFTER_COMMIT)`).
- **Staleness has a limit.** In-memory entries also expire after `local-ttl` (30s), in case a pub/sub message is lost.
- **Request coalescing.** Concurrent cache misses for the same key share one database read.
- **No stale write-back.** A load that started before a change is not allowed to store its result.
- **Negative caching.** Unknown keys are remembered for 5s, so a typo in client code can't flood the database.
- **Redis outages are handled.** Every Redis call has a 200ms timeout. After a failure, a circuit breaker skips Redis for 5s,
  so requests go straight to the database instead of each waiting for a timeout. Flags that change during an outage
  are remembered and cleared from Redis once it is reachable again.

## Measured results

Measured on a 2-core cloud VM that was running both app instances, PostgreSQL, Redis and the load generator at the same time:

| Measurement | Result |
|---|---|
| `POST /api/evaluate` over HTTP, one keep-alive client, 20,000 requests | **p50 0.22 ms · p95 0.48 ms · p99 0.72 ms** |
| Throughput, 10 concurrent connections (autocannon) | ~19,000 requests/s |
| Lookups served from memory during the load test | 516,757 of 516,765 (4 from Redis, 4 from PostgreSQL) |
| A change on instance A, seen by instance B | on the next request (pub/sub), without a database read on B |
| Uncached lookup with Redis stopped | first: 0.76 s (timeout, then the breaker opens); next: ~10 ms from PostgreSQL |

Cache hit counts are exposed at `/actuator/metrics/featureflags.cache.lookups?tag=source:memory` (also `redis`, `database`).

## Running it

**Quick start (nothing to install except Java 21):** uses an H2 file database and the in-memory cache only.

```bash
./mvnw spring-boot:run
```

**With PostgreSQL + Redis (the full setup):**

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker
```

To see cross-instance invalidation, start a second instance with `-Dspring-boot.run.arguments=--server.port=8082`.

## API examples

```bash
# Create a flag (optionally with an expiry date). X-Actor is recorded in the audit log.
curl -X POST localhost:8080/api/flags -H 'Content-Type: application/json' -H 'X-Actor: krishna' \
  -d '{"key": "new-payment-system", "name": "New payment system", "expiresAt": "2027-03-31T00:00:00Z"}'

# PROD: 10% of premium users in India, nobody else
curl -X PATCH localhost:8080/api/flags/new-payment-system/environments/PROD \
  -H 'Content-Type: application/json' -H 'X-Actor: krishna' -d '{
    "enabled": true,
    "rolloutPercentage": 0,
    "targetedUsers": ["qa-tester-1"],
    "rules": [{
      "description": "Premium users in India",
      "conditions": [
        {"attribute": "plan",    "operator": "IN", "values": ["premium"]},
        {"attribute": "country", "operator": "IN", "values": ["IN"]}
      ],
      "rolloutPercentage": 10
    }]
  }'

# Evaluate for a user with attributes
curl -X POST localhost:8080/api/evaluate -H 'Content-Type: application/json' -d '{
  "flagKey": "new-payment-system", "environment": "PROD",
  "user": {"id": "user-42", "attributes": {"plan": "premium", "country": "IN"}}}'
# → {"flagKey":"new-payment-system","environment":"PROD","userId":"user-42",
#    "enabled":false,"reason":"RULE_MATCH_NOT_IN_ROLLOUT","ruleIndex":0}

# Quick check by user id only (id targeting and percentage rollouts)
curl 'localhost:8080/api/evaluate?flagKey=new-payment-system&environment=PROD&userId=qa-tester-1'

# Change the name or expiry, or remove the expiry
curl -X PATCH localhost:8080/api/flags/new-payment-system -H 'Content-Type: application/json' \
  -d '{"removeExpiry": true}'

# History, newest first: one flag (kept even after the flag is deleted) or everything
curl 'localhost:8080/api/flags/new-payment-system/audit-logs'
curl 'localhost:8080/api/audit-logs?page=0&size=50'
```

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/flags` | Create a flag |
| `GET` | `/api/flags`, `/api/flags/{key}` | List or read flags |
| `PATCH` | `/api/flags/{key}` | Change name, description or expiry |
| `PATCH` | `/api/flags/{key}/environments/{env}` | Change on/off, %, targeted users or rules |
| `DELETE` | `/api/flags/{key}` | Delete a flag |
| `POST` / `GET` | `/api/evaluate` | Evaluate a flag for a user |
| `GET` | `/api/flags/{key}/audit-logs`, `/api/audit-logs` | Change history |
| `GET` | `/actuator/health`, `/actuator/metrics` | Health and metrics |

## Tests

```bash
./mvnw test                                    # 53 tests, no external services needed
docker compose up -d && INTEGRATION_TESTS=true ./mvnw test   # + 2 tests against real PostgreSQL and Redis
```

- `FlagEvaluatorTest`: evaluation order, rule priority, a "premium users in India at 10%" distribution, and that raising a percentage never removes a user
- `ConditionMatcherTest`: every operator, plus missing attributes and non-numeric values
- `RolloutHasherTest`: stable buckets, even distribution, independence between flags
- `FlagCacheTest`: caching, negative caching, TTL, eviction, and concurrent misses sharing one load
- `FlagApiTest`: the full HTTP API, including validation, cache invalidation, audit entries and expiry (using a controllable clock)
- `RedisPostgresIntegrationTest`: snapshots written to Redis after commit, and changes announced over pub/sub being picked up

## Design decisions

- **Rules are stored as a JSON column, not in separate tables.** Rules are always read and written as a complete ordered
  list, so tables for rules and conditions would only add joins.
- **The audit entry is written in the same transaction as the change** (`Propagation.MANDATORY`). A change can't be
  saved without its audit entry. Audit rows store the flag key rather than a foreign key, so history survives deletion.
- **Expiry is checked twice.** Evaluation compares `expiresAt` with the clock, so an expired flag is off at once. A
  scheduled job also switches it off in the database, so the dashboard shows the right state and the audit log records it.
- **The service runs in UTC** regardless of the machine's time zone, which also avoids PostgreSQL rejecting legacy names such as `Asia/Calcutta`.
- **Flyway owns the schema.** Hibernate only validates it (`ddl-auto=validate`), and the same migration runs on H2 and PostgreSQL.

## Possible next steps

- API keys per environment (an SDK key can only evaluate; an admin key can change flags)
- A Java client SDK that keeps all flags in memory and receives changes over Server-Sent Events, so evaluation needs no network call
- Batch evaluation (`all flags for this user`) for frontends
- A small React dashboard
- ShedLock for the expiry job when running many instances
