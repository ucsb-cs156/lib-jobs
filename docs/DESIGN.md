# lib-jobs: Shared Async Jobs System for ucsb-cs156 Projects

**Status:** §7 open questions resolved with Phill 2026-07-12; phase 1 underway
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
  (transaction wrapping, `jobName`, pagination, rate limiting, job scoping).

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
| `Job` (entity, table `jobs`) | id, `createdById` (Long), `createdByEmail` (String), `jobName`, `status`, `log` (TEXT), `createdAt`, `updatedAt`, `scopeType` (nullable String), `scopeId` (nullable Long) | union; see §3.3, §3.4 |
| `JobsRepository` | `JpaRepository<Job, Long>` + `findAllByOrderByIdDesc()` + paging + `findByScopeTypeAndScopeIdOrderByIdDesc(String, Long)` + `deleteByScopeTypeAndScopeId(String, Long)` | courses + §3.4 |
| `JobContextConsumer` | `void accept(JobContext c) throws Exception`; `default String getJobName()` → simple class name; `default String getScopeType()` / `default Long getScopeId()` → null | frontiers (generalized) |
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

### 3.4 Job scoping (generalizing frontiers' `Course` link)

**Decision (Phill, 2026-07-12):** promote frontiers' per-course jobs feature
into the library as generic **scope columns**, decoupled from any entity. This
supersedes both options drafted earlier (`metadata` JSON column; app-level side
table) — rationale at the end of this section.

What frontiers has today: `@ManyToOne Course course` on `Job`,
`default Course getCourse()` on `JobContextConsumer`, a
`GET /api/jobs/course?courseId=` endpoint guarded by
`@CourseSecurity.hasManagePermissions` (course *staff*, not just admins, can
watch their course's jobs in `JobTabComponent`), and
`deleteByCourse_Id` cleanup when a course is deleted. Generalized, the feature
is: *a job may belong to one app-domain object, and the app may expose scoped
listing under its own authorization rule.*

Library design:

- `Job.scopeType` (nullable String) + `Job.scopeId` (nullable Long) — plain
  columns, no FK; both null for unscoped jobs (the only kind the other four
  apps have today).
- `JobContextConsumer` gains `default String getScopeType()` and
  `default Long getScopeId()` (both null); `JobService.runAsJob` copies them
  onto the entity at launch. Launch endpoints that know the scope at request
  time set them via the job builder as frontiers does today.
- `JobsRepository` ships `findByScopeTypeAndScopeIdOrderByIdDesc` and
  `deleteByScopeTypeAndScopeId`.
- **Scoped endpoints stay app-level** because the authorization rule is
  app-specific (frontiers: `@CourseSecurity`); the library's own endpoints
  remain `ROLE_ADMIN`. Frontiers' endpoint becomes a one-liner over the
  library repository method with `scopeType = "course"`.

Frontiers migration (phase 6): set `scope_type = 'course'`,
`scope_id = course_id` for existing rows; drop the FK and the `course_id`
column; replace `deleteByCourse_Id` call sites with
`deleteByScopeTypeAndScopeId("course", id)`. Only losses vs. today: the DB-level
FK integrity check (the delete was already an explicit repository call, not a
cascade), and jobs no longer serialize a nested `course` object (frontend reads
`scopeId` instead).

Why not the `metadata` JSON column: querying a TEXT blob portably across H2
(dev) and Postgres (prod) degenerates to unindexed `LIKE` string-matching that
is easy to get subtly wrong (a naive pattern for `"courseId": 17` also matches
`171`), and course-delete cleanup would ride on the same fragile match. Why not
an app-level side table: real FKs, but the most app-side machinery (a wrapper
around `runAsJob`, join queries), and no other app gains the capability. Scope
columns keep frontiers' indexed equality queries and give all five apps the
same feature for free.

### 3.5 Auto-configuration (the "starter" part)

`JobsAutoConfiguration`, registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

- **Entity/repository discovery** (corrected during phase 1): the library uses
  `@AutoConfigurationPackage(basePackages = "edu.ucsb.cs156.jobs")`, which
  *adds* the library package to Spring Boot's default entity + Spring Data
  scanning. The originally drafted `@EntityScan`/`@EnableJpaRepositories` would
  have *replaced* the defaults and broken every consuming app's own entities
  and repositories (none of the five apps declares those annotations; verified
  2026-07-12).
- Explicit `@Bean`s (not `@ComponentScan`) for `JobService`, `JobsController`,
  `JobContextFactory`, `JobRateLimit`, each `@ConditionalOnMissingBean` so an
  app can override any piece.
- `@EnableAsync` + `@EnableScheduling`.
- `jobsExecutor`: `ThreadPoolTaskExecutor` (properties `app.jobs.core-pool-size`,
  `app.jobs.max-pool-size`, `app.jobs.queue-capacity`) wrapped in
  `DelegatingSecurityContextAsyncTaskExecutor` so the launching admin's
  security context propagates. `JobService` uses `@Async("jobsExecutor")`.
  Defaults adopt scaffold's deliberate choice: **one thread, unbounded FIFO
  queue** — jobs run strictly one at a time in submission order, preventing
  concurrent-job races; apps wanting concurrency raise the pool sizes.

Implementation notes (phase 1):

- `Job` timestamps use JPA lifecycle callbacks (`@PrePersist`/`@PreUpdate`)
  instead of Spring Data auditing, avoiding any interaction with the apps'
  `@EnableJpaAuditing` configs.
- The apps' 404/400 exception mapping lives in an app-level abstract
  `ApiController`; the library controller can't extend that, so it carries its
  own `@ExceptionHandler`s (plus its own `errors.EntityNotFoundException`)
  producing the same `{type, message}` response shape.
- pitest excludes `JobsAutoConfiguration` (Spring's test-context cache means
  `@Bean`-method mutations never re-execute — same reason the app repos
  exclude their config classes); everything else is at 100% mutation kill and
  the pom enforces `mutationThreshold=100`.

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

**Survey correction (2026-07-12, while setting up CI):** frontiers, scaffold,
and happycows manage schema with **Liquibase** (JSON changelogs under
`src/main/resources/db/migration/changes/`, checked in CI by workflow
`18-validate-db-schema`, which boots the app with `ddl-auto=validate`);
courses and dining still rely on Hibernate `ddl-auto`. The original draft of
this section assumed `ddl-auto` everywhere.

The library therefore ships its canonical schema as a Liquibase changelog
**inside the jar**: `db/migration/lib-jobs/changelog-master.json` (which
includes `db/migration/lib-jobs/changes/001-create-jobs-table.json`, creating
the `jobs` table with all columns, types mirroring frontiers'
`000-init-database.json`). The library's own tests run against that changelog
with `ddl-auto=validate`, so any drift between the `Job` entity and the
changelog fails the library's build — the migration is continuously sanity
checked at the source.

Per consuming app:

1. **Fresh installs (dining), Liquibase flavor:** add one include to the app's
   master changelog —
   `{"include": {"file": "db/migration/lib-jobs/changelog-master.json"}}` —
   resolved from the library jar via the classpath. Future library schema
   changesets arrive automatically with version bumps.
2. **Liquibase apps with an existing `jobs` table (frontiers, scaffold,
   happycows):** do *not* include the library changelog (the table already
   exists). Write app-level changesets instead: add the missing columns
   (`job_name`, `created_by_email`, `scope_type`, `scope_id` as applicable),
   drop the FK on `created_by_id`; frontiers additionally
   `UPDATE jobs SET scope_type='course', scope_id=course_id`, then drops the
   FK and `course_id` (§3.4).
3. **`ddl-auto` apps (courses):** added columns appear automatically; drop the
   FK constraint on `jobs.created_by_id` manually on prod Postgres (dokku).
4. No backfill of `created_by_email` (nullable; UI tolerates null).

## 4. Versioning, publishing, CI

- **Releases:** git tag `vX.Y.Z` on `main` → GitHub Release. JitPack builds the
  Maven artifact on first request of that tag (no publish workflow needed for
  Maven). An Actions workflow on tag publishes the npm package.
- **CI:** thin caller workflows into the org's shared reusable workflows
  (`ucsb-cs156/workflows@main`), same numbering as the app repos:
  00 (all-checks-pass gate), 01/02 (gh-pages docs site from
  `frontend/docs-index/` — workflows 01/02 accept either location but 04
  hardcodes `frontend/docs-index`, so it must live there),
  10 (JUnit 5 unit tests), 12 (jacoco, 100% gate in pom, report to gh-pages),
  13/14 (pitest incremental on PRs / full on main, `mutationThreshold=100`),
  15 (google-java-format via git-code-format-maven-plugin — the shared
  workflow requires this plugin, so the library uses it rather than spotless),
  18 (db-schema validation: boots the src/test `TestApplication` with
  `ddl-auto=validate` against the shipped Liquibase changelog), 56/58
  (javadoc for main/PRs to gh-pages). Shared-workflow requirements the repo
  satisfies: `.java-version` file, `.mvn/jvm.config` with the
  `--add-exports jdk.compiler/...` flags google-java-format needs, and a
  `spring-boot-maven-plugin` config that can boot the test app
  (`useTestClasspath` + test-classes on the classpath) while skipping
  `repackage` so the artifact stays a plain library jar. Setup after first
  push: enable GitHub Pages from the `gh-pages` branch, run
  `02-gh-pages-rebuild` once, and add `enforce` to required checks.
- **npm:** package `@ucsb-cs156/jobs-components`, published to npmjs.com
  (public scoped packages are free; consumers need no auth). One-time setup:
  1. Create/log in to an npmjs.com account (enable 2FA).
  2. Avatar menu → **Add Organization** → name `ucsb-cs156` → choose the free
     "Unlimited public packages" plan. (This also reserves the `@ucsb-cs156`
     scope.)
  3. Publish credentials — defer to phase 7: since npm's 2025 supply-chain
     hardening, write-capable granular tokens are short-lived (90-day max), so
     a set-and-forget `NPM_TOKEN` secret is no longer practical. Preferred:
     npm **trusted publishing** (OIDC from GitHub Actions, no long-lived
     secret), configured on the package after an initial manual
     `npm publish --access public` from a maintainer's machine. Note `--access
     public` is required on first publish — scoped packages default to
     restricted.
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
| 2 | **Installation pilot: proj-dining.** Add dependency + properties + `JobUserProvider` + one `TestJob` + launch endpoint; backend only until phase 7 | greenfield adoption is 1-dependency easy; exercises fresh schema creation |
| 3 | **Migration pilot: proj-scaffold.** Add dependency, delete core files + their tests, implement `JobUserProvider`, rename executor, schema step, verify admin UI still works against library controller | existing apps can migrate |
| 4 | proj-courses (keeps `JobRateLimit` usage; largest launch-endpoint surface) | |
| 5 | proj-happycows (oldest fork; also adopts pagination) | |
| 6 | proj-frontiers (needs the `Course`→scope-columns migration, §3.4) — do last | the scoping generalization works |
| 7 | Frontend package `@ucsb-cs156/jobs-components`; pilot in dining (no existing UI) then roll out | see §6 |

Ordering rationale (Phill, 2026-07-12): dining — the acid test for
1-dependency adoption — comes immediately after the library builds; then
migrate existing apps easiest-first: scaffold (closest to the library shape),
courses (pagination + rate limit), happycows (oldest drift), frontiers last
(scope migration).

Per-repo migration checklist (phases 3–6):

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

## 7. Open questions — RESOLVED (Phill, 2026-07-12)

1. **Frontiers `Course` coupling:** neither drafted option — generalized
   **scope columns** (`scopeType`/`scopeId`) in the library instead; see §3.4.
2. **Repo/package naming:** confirmed as drafted (`lib-jobs`,
   `com.github.ucsb-cs156:lib-jobs`, `@ucsb-cs156/jobs-components`).
3. **License:** MIT. (Survey correction: none of the five app repos actually
   has a LICENSE file, so there was no org convention to match; the library
   needs an explicit license to be publicly consumable.)
4. **npm org:** confirmed it does not exist (the `@ucsb-cs156` scope has zero
   packages); Phill is creating it — see §4 for the steps. Publish credentials
   deferred to phase 7 (see the token-lifetime note in §4).
5. **Executor rename:** confirmed — standardize on `jobsExecutor`; scaffold
   renames during its migration.
6. **Logs endpoint:** confirmed — `getJobLogs` + `GET /api/jobs/logs/{id}`.
7. **`JobRateLimit`:** property optional; default 200 ms, warn-and-default on
   unparseable values (matches courses' behavior).

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
