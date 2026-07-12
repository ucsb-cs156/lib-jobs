# lib-jobs: Shared Async Jobs System for ucsb-cs156 Projects

**Status:** Draft for review (Phill Conrad)
**Date:** 2026-07-12
**Origin:** Claude Code session in proj-courses, 2026-07-11/12, analyzing the
homegrown jobs system vs. Spring Batch and then vs. extraction into a shared library.

## 1. Problem statement

Five ucsb-cs156 projects need a lightweight async job system with persistent
status, a live human-readable log, an admin REST API, and an admin UI:

| Repo | Backend jobs core | Frontend jobs UI | Notes |
|---|---|---|---|
| [proj-courses](https://github.com/ucsb-cs156/proj-courses) | yes | yes | + `JobRateLimit`, + paginated endpoint |
| [proj-frontiers](https://github.com/ucsb-cs156/proj-frontiers) | yes | yes | most evolved core; **couples core to `Course` entity** |
| [proj-scaffold](https://github.com/ucsb-cs156/proj-scaffold) | yes | yes | matches frontiers minus `Course`; `@Async("jobExecutor")` |
| [proj-happycows](https://github.com/ucsb-cs156/proj-happycows) | yes | yes | oldest variant; no `JobContextFactory`; own `PagedJobsTable` |
| [proj-dining](https://github.com/ucsb-cs156/proj-dining) | **no** | **no** | greenfield; will need jobs eventually |

The four existing copies were forked from a common ancestor and have drifted
(details in Appendix A). Every fix or feature lands in one repo and not the
others. A Spring Batch migration was considered and rejected (see Appendix B);
the chosen direction is to extract the existing design into:

1. a **Maven library** (Spring Boot auto-configured starter), published via JitPack
2. an **npm package** of the jobs UI components (phase 2)

proj-dining serves as the *installation* acid test: adopting the jobs system
there must require only a dependency, a few properties, one small interface
implementation, and (frontend) a route registration.

## 2. Goals / non-goals

**Goals**
- One canonical implementation of the jobs core, tested once (jacoco + pitest in this repo).
- Apps keep writing concrete jobs exactly as today: implement `JobContextConsumer`,
  build via a factory bean, launch from a controller or `@Scheduled` method.
- Adoption in a fresh repo = 1 dependency + properties + 1 interface impl.
- Union of the best features currently scattered across the forks
  (transaction wrapping, `jobName`, pagination, rate limiting).

**Non-goals**
- Not a batch-processing framework: no chunking, no restart-from-checkpoint,
  no retry policies. (Could be added later behind the same API if ever needed.)
- Not extracting the apps' concrete jobs, launch endpoints, or `ScheduledJobs` —
  those stay app-specific.
- Phase 1 does not extract the wider shared frontend kit (`OurTable`,
  `useBackend`, layouts) — see §6 for how the frontend package handles this.

## 3. Backend library design

### 3.1 Coordinates and layout

- Repo: `ucsb-cs156/lib-jobs` (this repo). Root `pom.xml` builds the jar so
  JitPack works with no configuration; `frontend/` holds the npm package.
- Dependency (via JitPack):
  `com.github.ucsb-cs156:lib-jobs:v1.0.0`
  plus the JitPack repository in each consumer's `pom.xml`.
- Java package: `edu.ucsb.cs156.jobs` (project-neutral).
- Spring Boot version: match the apps (currently Spring Boot 3.x across the
  org); declare Spring dependencies as `provided`/optional where appropriate so
  the app's BOM wins.

**Why JitPack over the alternatives:** GitHub Packages requires a PAT even for
public *reads* — unacceptable friction for student setups. Maven Central
(`io.github.ucsb-cs156`) adds signing/portal ceremony for little benefit at
this scale. JitPack builds straight from a git tag with zero infrastructure.

### 3.2 Classes

All under `edu.ucsb.cs156.jobs`:

| Class | Role | Source of truth for behavior |
|---|---|---|
| `Job` (entity, table `jobs`) | id, `createdById` (Long), `createdByEmail` (String), `jobName`, `status`, `log` (TEXT), `createdAt`, `updatedAt`, `metadata` (nullable TEXT) | union; see §3.3, §3.4 |
| `JobsRepository` | `JpaRepository<Job, Long>` + `findAllByOrderByIdDesc()` + paging | courses |
| `JobContextConsumer` | `void accept(JobContext c) throws Exception`; `default String getJobName()` → simple class name; `default String getMetadata()` → null | frontiers (generalized) |
| `JobContext` | `log(String)` appends to `job.log` and saves | identical everywhere |
| `JobContextFactory` | creates `JobContext` (exists for test seams) | courses/frontiers/scaffold |
| `JobService` | `runAsJob(...)` creates entity then calls `self.runJobAsync(...)`; async method wraps job body in `TransactionTemplate` | **frontiers** (most correct: keeps Hibernate session open, all-or-nothing DB semantics for the job body while still persisting job metadata on failure) |
| `JobsController` | `/api/jobs` GET all / GET paginated / GET by id / GET logs / DELETE one / DELETE all; all `@PreAuthorize("hasRole('ROLE_ADMIN')")` | courses (superset incl. `/paginated`) |
| `JobRateLimit` | configurable `sleep()` between external API calls | courses; property `app.jobs.rate-limit-ms`, default 200, bean always available |
| `JobUserProvider` (interface) | `Long getCurrentUserId(); String getCurrentUserEmail();` — implemented by each app | new; see §3.3 |
| `JobsAutoConfiguration` | wires everything; see §3.5 | new |

Launch endpoints (e.g. `/api/jobs/launch/updateCourses`) remain in each app's
own controller, since they depend on app-specific factories and parameters.

### 3.3 Decoupling from the app's `User` entity

Today every fork's `Job` has `@ManyToOne User createdBy`, tying the core to the
app's `User` entity and `CurrentUserService`. The library instead **denormalizes**:

- `Job.createdById` (Long) and `Job.createdByEmail` (String) — plain columns, no FK.
- `JobService` populates them via the app-implemented `JobUserProvider` bean
  (each app's implementation is a 5-line bridge over its existing
  `CurrentUserService`).

Rationale: the admin UI only ever displays the creator's email; a JPA join buys
nothing but coupling. Generics over the entity (`Job<U extends ...>`) fight JPA
and are rejected.

Migration note: keep the column name `created_by_id` so existing data survives;
drop the FK constraint; `created_by_email` is nullable and stays blank for
historical rows (UI must tolerate null).

### 3.4 Decoupling from frontiers' `Course`

proj-frontiers added `default Course getCourse()` to `JobContextConsumer` and a
`@ManyToOne Course course` on `Job`, used to filter the jobs table per course
(`JobTabComponent`). The library cannot know about `Course`.

**Recommended:** a nullable free-form `metadata` TEXT column on `Job`, populated
from `JobContextConsumer.getMetadata()` (frontiers returns e.g.
`{"courseId": 17}`). Frontiers filters client-side or via a `LIKE` query in its
own app-level repository fragment. Scale (hundreds of rows) makes JSON-blob
querying a non-issue.

**Alternative (if metadata feels too loose):** frontiers keeps an app-level side
table `job_courses(job_id, course_id)` written by its own wrapper around
`runAsJob`. Cleaner relationally, more moving parts.

**Open question for Phill / frontiers maintainers:** which of the two. Design
proceeds with `metadata` unless vetoed.

### 3.5 Auto-configuration (the "starter" part)

`JobsAutoConfiguration`, registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

- `@EntityScan("edu.ucsb.cs156.jobs")` + `@EnableJpaRepositories("edu.ucsb.cs156.jobs")`
  (scoped to library packages only, so the app's own scanning is untouched).
- `@ComponentScan` (or explicit `@Bean`s) for `JobService`, `JobsController`,
  `JobContextFactory`, `JobRateLimit`.
- `@EnableAsync` + `@EnableScheduling`.
- Beans: `ThreadPoolTaskExecutor` named **`jobsExecutor`** (properties
  `app.jobs.core-pool-size`, `app.jobs.max-pool-size`, `app.jobs.queue-capacity`,
  with sensible defaults) wrapped in `DelegatingSecurityContextAsyncTaskExecutor`
  so the launching admin's security context propagates. `JobService` uses
  `@Async("jobsExecutor")`.
- All beans `@ConditionalOnMissingBean` so an app can override any piece.

Consequence for consumers: apps **delete** their `@EnableAsync`,
`@EnableScheduling`, and executor bean definitions from the application class
(e.g. `CoursesApplication`). Apps that had a differently-named executor
(scaffold's `jobExecutor`) migrate to `jobsExecutor`.

Security: `JobsController` uses `@PreAuthorize("hasRole('ROLE_ADMIN')")`, which
all five apps' security configs already support. Document this as a requirement.

### 3.6 Testing and quality gates

The library repo carries the full test suite for everything it ships,
at the same standards as the app repos (jacoco 100% line/branch on library
code, pitest with the org's usual mutation thresholds). This *removes* the
equivalent test burden from all five app repos — after migration they delete
their `JobService`/`JobContext`/`JobsController`/entity tests.

Include one `TestJob` (sleeps/logs N times — same as the existing per-repo
`TestJob`s) in `src/test`, plus an example in the README, so apps have a
reference implementation.

### 3.7 Schema migration (per consuming app)

The apps use Hibernate `ddl-auto` in the usual org configuration, so *added*
columns (`job_name`, `created_by_email`, `metadata`) appear automatically.
Manual steps per app:

1. Drop the FK constraint on `jobs.created_by_id` (one SQL statement on prod
   Postgres via dokku; H2 dev databases just get recreated).
2. Frontiers only: migrate `jobs.course_id` data into `metadata` (or the side
   table), then drop the column.
3. No backfill of `created_by_email` (nullable; UI tolerates null).

## 4. Versioning, publishing, CI

- **Releases:** git tag `vX.Y.Z` on `main` → GitHub Release. JitPack builds the
  Maven artifact on first request of that tag (no publish workflow needed for
  Maven). An Actions workflow on tag publishes the npm package.
- **CI on PR:** backend `mvn test` + jacoco check + pitest; frontend
  `npm test` + coverage; formatting checks matching org conventions
  (spotless google-java-format / prettier + eslint).
- **npm:** package `@ucsb-cs156/jobs-components`, published to npmjs.com
  (public scoped packages are free; consumers need no auth). Requires creating
  the `ucsb-cs156` npm org once and adding an `NPM_TOKEN` repo secret.
- **Consumers:** enable Dependabot for Maven + npm so version bumps arrive as PRs.
- **Compatibility policy:** semver; breaking API changes bump major and the
  rollout to five repos is coordinated. Keep the API surface minimal to make
  breaking changes rare.

## 5. Rollout plan

Each phase gates the next.

| Phase | What | Proves |
|---|---|---|
| 0 | This design reviewed by Phill; open questions (§7) resolved | design |
| 1 | Build backend library in this repo, full tests, tag `v0.1.0` | it builds & tests |
| 2 | **Migration pilot: proj-scaffold.** Add dependency, delete core files + their tests, implement `JobUserProvider`, rename executor, schema step, verify admin UI still works against library controller | existing apps can migrate |
| 3 | **Installation pilot: proj-dining.** Add dependency + properties + `JobUserProvider` + one `TestJob` + launch endpoint; backend only until phase 6 | greenfield adoption is 1-dependency easy; exercises fresh schema creation |
| 4 | proj-courses (keeps `JobRateLimit` usage; largest launch-endpoint surface) | |
| 5 | proj-happycows (oldest fork; also adopts pagination) | |
| 6 | proj-frontiers (needs the `Course`→`metadata` migration) — do last | the escape hatch works |
| 7 | Frontend package `@ucsb-cs156/jobs-components`; pilot in dining (no existing UI) then roll out | see §6 |

Per-repo migration checklist (phases 2, 4–6):

- [ ] Add JitPack repo + dependency to `pom.xml`
- [ ] Delete `services/jobs/*`, `entities/**/Job.java`, `repositories/**/JobsRepository.java`,
      generic parts of `JobsController` (keep launch endpoints; they now import
      `edu.ucsb.cs156.jobs.*`)
- [ ] Delete `@EnableAsync`/`@EnableScheduling`/executor beans from application class
- [ ] Implement `JobUserProvider` (bridge over `CurrentUserService`)
- [ ] Update imports in concrete jobs/factories/`ScheduledJobs`
- [ ] Add `app.jobs.*` properties
- [ ] Schema step(s) from §3.7 on dev + prod (dokku)
- [ ] Delete now-redundant core tests; confirm jacoco/pitest still pass on what remains
- [ ] Smoke test: launch `TestJob` from admin UI, watch live log, check paginated table

## 6. Frontend package (phase 7 — design sketch, finalize later)

Extract into `frontend/` of this repo, published as `@ucsb-cs156/jobs-components`:
a **merged-superset** `JobsTable` (pagination + `jobName` column + log
truncation — the forks have fully diverged, so this is a redesign, not a copy),
`SingleButtonJobForm`, `JobsSearchForm`, and a `JobLogViewer`. Pages
(`AdminJobsPage`, `AdminJobLogPage`) stay in the apps (they depend on each
app's `BasicLayout`/routing) but shrink to thin wrappers.

The hard dependency problem: today's `JobsTable` imports each app's `OurTable`,
and pages use each app's `useBackend`. Options:

- (a) package bundles its own internal table built on the same
  `@tanstack/react-table` the apps use — duplicates `OurTable` inside the
  package but makes it self-contained; **recommended starting point**
- (b) components accept the table component / backend hooks as props — awkward API
- (c) extract the whole shared kit (`@ucsb-cs156/components`: `OurTable`,
  `useBackend`, layouts, auth utils) — the honest long-term fix for a much
  bigger duplication problem, but a separate project; jobs components would
  then depend on it

Peer dependencies to align across the five apps before publishing: react,
react-bootstrap, react-router-dom, react-query flavor/version. Audit needed.

## 7. Open questions (need Phill's call before/during phase 1)

1. **Frontiers `Course` coupling:** `metadata` JSON column (recommended) or
   app-level side table? (§3.4)
2. **Repo/package naming:** repo `lib-jobs`; Maven `com.github.ucsb-cs156:lib-jobs`
   (forced by JitPack); npm `@ucsb-cs156/jobs-components`. OK?
3. **License:** MIT to match the app repos?
4. **npm org:** does `ucsb-cs156` exist on npmjs.com / who creates it + token?
5. **Executor rename:** OK to standardize on `jobsExecutor` (scaffold currently
   uses `jobExecutor`)?
6. **`getLongJob` vs `getJobLogs`:** the forks disagree on the method/endpoint
   name for long logs; library standardizes on `getJobLogs` + `/api/jobs/logs/{id}`.
7. Should `JobRateLimit` require its property (`app.jobs.rate-limit-ms`) or
   default silently to 200ms? (Courses currently warns-and-defaults on bad values.)

## Appendix A: Drift survey (as of 2026-07-12, all repos' `main`)

Measured by downloading and diffing the core files across repos.

- **JobService:** frontiers == scaffold except `@Async` vs `@Async("jobExecutor")`
  and package strings. Both have `jobName` + `TransactionTemplate`; courses has
  neither; happycows additionally lacks `JobContextFactory` (constructs
  `JobContext` inline) and calls `e.printStackTrace()` in the catch.
- **JobContextConsumer:** frontiers/scaffold add `default Course getCourse()`
  (frontiers' is the `Course` coupling; scaffold's variant exists structurally).
  courses/happycows: bare functional interface.
- **Job entity:** frontiers adds `jobName` + `course` (EAGER joins);
  courses/happycows: original shape (LAZY `createdBy`, `@JsonIgnore` on it in
  courses). happycows keeps it under `entities/jobs/`, others `entities/`.
- **JobsController:** courses has the `/paginated` endpoint with sort-field
  allowlist; others vary.
- **courses extras:** `JobRateLimit` (property `app.rateLimitDelayMs`).
- **Frontend `JobsTable.jsx`:** 43–71 lines per repo; pairwise diffs ≈ the file
  length — fully diverged (pagination, jobName column, truncation).
- **proj-dining:** no jobs code at all, backend or frontend, and no
  async/scheduling config in its application class.

## Appendix B: Why not Spring Batch

Considered and rejected for this org (analysis 2026-07-11): Spring Batch solves
*batch pipeline* problems (chunked transactions, restart-from-failure,
skip/retry, `BATCH_*` metadata) and provides neither scheduling, nor an admin
UI, nor the human-readable incremental log the admin pages display. Its main
prize (transactional restartability) is diluted where the big job writes to
MongoDB (proj-courses). Cost: heavy conceptual load for a teaching codebase
(JobBuilder/StepBuilder/JobRepository/JobParameters vs ~130 lines students can
read), UI + API rework, and painful jacoco/pitest coverage of framework config —
times five repos. If restartability is ever needed, add a progress checkpoint
to this library's `Job` entity instead.
