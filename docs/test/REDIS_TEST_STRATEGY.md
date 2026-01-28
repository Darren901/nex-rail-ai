# Test Configuration - application-test.yml

## Redis Configuration Strategy for Integration Tests

The integration tests (annotated with `@SpringBootTest` and `@ActiveProfiles("test")`) load configuration from `src/test/resources/application-test.yml`. Currently, this file points Redis to `localhost:6379`.

### Why this is a potential issue:
1.  **Environment Dependency**: If a local Redis server is not running on the machine executing tests (e.g., CI/CD pipeline, another developer's machine), the tests will fail during context initialization because `SystemConfigService` attempts to connect to Redis in its `@PostConstruct` method.
2.  **Test Isolation**: Tests sharing a local Redis instance might interfere with each other or with a running development instance.

### Solution: Embedded Redis or Testcontainers

To make tests self-contained and robust:

1.  **Testcontainers (Recommended)**: Use Testcontainers to spin up a Dockerized Redis instance for tests. This mirrors production behavior most closely.
2.  **Embedded Redis**: Use a library like `embedded-redis` to run a lightweight Redis server within the JVM during tests.

### Implementation Plan (Using Testcontainers):

1.  Add `org.testcontainers:junit-jupiter` and `org.testcontainers:redis` (or generic) dependencies to `pom.xml`.
2.  Create a base test class (e.g., `BaseIntegrationTest`) that:
    -   Defines a static `GenericContainer` for Redis.
    -   Uses `@DynamicPropertySource` to inject the dynamic host/port into Spring properties (`spring.data.redis.host`, `spring.data.redis.port`).
3.  Make integration tests (`AdminApiIntegrationTest`, `ScheduleTaskIntegrationTest`, `ThsrSearchIntegrationTest`) extend this base class.

This ensures that whenever tests run, a fresh Redis instance is available, and `SystemConfigService` can successfully initialize.

### Alternate (Simpler) Fix if Testcontainers is overkill:
If `SystemConfigService` is mocked in most integration tests (using `@MockBean` or `@MockitoBean`), the real `init()` method might not run, or Redis interactions are intercepted. However, `ScheduleTaskIntegrationTest` and `ThsrSearchIntegrationTest` seem to load the full context or at least rely on `SystemConfigService` working (even if mocked in some places, the context load might trigger beans that use Redis).

**Verification**:
Check if `SystemConfigService` is mocked in the failing tests.
- `ScheduleTaskIntegrationTest`: Has `@MockitoBean private SystemConfigService systemConfigService;`. This REPLACES the real bean. So `init()` (PostConstruct) of the *real* bean won't run. The mock handles calls.
- `AdminApiIntegrationTest`: Uses `@Autowired private SystemConfigService systemConfigService;`. **This uses the REAL bean.** This test WILL try to connect to Redis.
- `ThsrSearchIntegrationTest`: Uses `@MockBean private RateLimitService rateLimitService;` (which uses Redis), but `LineService` is Autowired. `SystemConfigService` isn't explicitly mentioned but might be in the context.

**Conclusion**:
`AdminApiIntegrationTest` uses the real `SystemConfigService`, which tries to connect to Redis on startup. This WILL fail if localhost:6379 is down.

**Fix**:
Modify `application-test.yml` or the test setup to handle this. Since we want to avoid complex infrastructure changes right now, a quick fix is to **Mock SystemConfigService in AdminApiIntegrationTest** as well, OR use `Testcontainers`.

Given the instruction "Update tests to provide an embedded/ephemeral Redis or conditionally skip Redis-dependent init", Testcontainers is the robust path.
