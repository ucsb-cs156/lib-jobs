# lib-jobs

Shared asynchronous jobs system for [ucsb-cs156](https://github.com/ucsb-cs156)
course projects:

- a **Maven library** (Spring Boot auto-configured starter) providing the
  `Job` entity, `JobService`, admin REST endpoints, and async executor wiring
- an **npm package** (`@ucsb-cs156/jobs-components`) providing the admin UI
  components (planned; see rollout plan)

Extracted from the homegrown jobs system previously duplicated in
[proj-courses](https://github.com/ucsb-cs156/proj-courses),
[proj-frontiers](https://github.com/ucsb-cs156/proj-frontiers),
[proj-scaffold](https://github.com/ucsb-cs156/proj-scaffold), and
[proj-happycows](https://github.com/ucsb-cs156/proj-happycows);
[proj-dining](https://github.com/ucsb-cs156/proj-dining) will be the first
fresh installation.

See [docs/DESIGN.md](docs/DESIGN.md) for the full design, including the drift
survey of the existing implementations, decoupling decisions, publishing setup
(JitPack + npmjs), and the phased rollout plan.

## Installation

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.ucsb-cs156</groupId>
  <artifactId>lib-jobs</artifactId>
  <version>v0.1.0</version>
</dependency>
```

The consuming app must provide two things:

1. **A `JobUserProvider` bean** — a small bridge over the app's existing
   `CurrentUserService`, used to stamp `createdById`/`createdByEmail` on jobs:

   ```java
   @Bean
   public JobUserProvider jobUserProvider(CurrentUserService currentUserService) {
     return new JobUserProvider() {
       @Override
       public Long getCurrentUserId() {
         return currentUserService.getUser().getId();
       }

       @Override
       public String getCurrentUserEmail() {
         return currentUserService.getUser().getEmail();
       }
     };
   }
   ```

2. **Method security** — the admin endpoints use
   `@PreAuthorize("hasRole('ROLE_ADMIN')")`, so the app's security config must
   have `@EnableMethodSecurity` (all five ucsb-cs156 apps already do).

Everything else is auto-configured: the `jobs` table entity, `JobsRepository`,
`JobService`, the `/api/jobs` admin controller, `JobRateLimit`, and a
`jobsExecutor` task executor (single-threaded by default, so jobs run one at a
time in submission order). Delete any `@EnableAsync`/`@EnableScheduling`/
executor-bean configuration from the application class — the library provides
them.

**Schema:** apps using Liquibase add one include to their master changelog
(the changelog ships inside the jar):

```json
{"include": {"file": "db/migration/lib-jobs/changelog-master.json"}}
```

Apps using Hibernate `ddl-auto` need nothing. Apps migrating an *existing*
`jobs` table should write their own alter-table changesets instead — see
DESIGN.md §3.7.

## Writing a job

```java
public class TestJob implements JobContextConsumer {
  @Override
  public void accept(JobContext c) throws Exception {
    for (int i = 0; i < 3; i++) {
      c.log("Hello World! i=" + i);
    }
  }
}
```

Launch it from an app controller or `@Scheduled` method:

```java
@PostMapping("/launch/testjob")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public Job launchTestJob() {
  return jobService.runAsJob(new TestJob());
}
```

The returned `Job` row updates live: `status` moves `queued` → `running` →
`complete` (or `error`, or `cancelled` — see below), and each `c.log(...)`
call inserts a new `job_logs` row and commits in its own transaction
(REQUIRES_NEW), so admins can watch progress from the jobs UI while the job
runs — independent of the job body's all-or-nothing transaction.

### Cancelling a job

`POST /api/jobs/{id}/cancel` requests cancellation. A **queued** job (not yet
picked up by the executor) is killed immediately — nothing is executing yet,
so this is fully honest. A **running** job is marked `cancelling`; job bodies
need no code changes to support this, since every `c.log(...)` call already
checks for it after persisting its message, and throws to stop the job the
next time it's called (`status` becomes the terminal `cancelled`). Escape
hatch for a log point that must not be interrupted:
`c.logNoCancelCheck(...)` — same log-and-persist behavior, skips the check.

Hard-kill (`Thread.interrupt()`) is deliberately not used — see DESIGN.md §9
for why. Cooperative cancellation only helps a job that reaches another
`c.log(...)` checkpoint — a job genuinely blocked forever (e.g. an HTTP call
with no timeout) never will, and will keep occupying the (single-threaded, by
default) executor no matter how many times it's cancelled. There is no
in-process fix for that case beyond restarting the app; consuming apps should
configure timeouts on any blocking calls a job body makes, so a job body can
never hang indefinitely in the first place.

### Startup recovery

Any job still `queued`, `running`, or `cancelling` when the app starts is
guaranteed orphaned: `jobsExecutor`'s in-memory queue does not survive a
restart, so nothing is actually executing (or about to execute, or checking
for cancellation on) that job anymore. On every startup, each such job is
automatically marked with the terminal `interrupted` status — no wiring
required, this runs via a `@EventListener(ApplicationReadyEvent.class)`
method already registered on the auto-configured `JobService` bean.

Jobs may optionally declare a *scope* (an association with one app-domain
object, e.g. a course) by overriding `getScopeType()`/`getScopeId()`; the
repository can then list or delete jobs by scope. See DESIGN.md §3.4.

## Configuration properties

| Property | Default | Meaning |
|---|---|---|
| `app.jobs.rate-limit-ms` | `200` | `JobRateLimit.sleep()` delay between external API calls |
| `app.jobs.core-pool-size` | `1` | `jobsExecutor` core threads |
| `app.jobs.max-pool-size` | `1` | `jobsExecutor` max threads |
| `app.jobs.queue-capacity` | unbounded | `jobsExecutor` queue size |

Any library bean can be overridden by defining a bean of the same type (or the
name `jobsExecutor`) in the app.

## Development

```bash
mvn test                        # tests + jacoco (100% required); runs against
                                # the shipped Liquibase changelog with
                                # ddl-auto=validate (schema drift fails fast)
mvn verify                      # + jacoco check + format check
mvn git-code-format:format-code # fix formatting (google-java-format)
mvn pitest:mutationCoverage     # mutation tests (100% required)
mvn spring-boot:run             # boot the src/test TestApplication
```

CI uses the org's shared reusable workflows
([ucsb-cs156/workflows](https://github.com/ucsb-cs156/workflows)) with the
same numbering as the app repos; javadoc, jacoco, and pitest reports publish
to the gh-pages site at <https://ucsb-cs156.github.io/lib-jobs>.

Releases: tag `vX.Y.Z` on `main`; JitPack builds the Maven artifact on demand.
