# lib-jobs

Shared async jobs system (Maven Spring Boot starter + npm components package)
for the ucsb-cs156 course projects. Extracted from the homegrown jobs system
duplicated across proj-courses, proj-frontiers, proj-scaffold, proj-happycows;
to be installed fresh into proj-dining.

## Read this first

**`docs/DESIGN.md` is the source of truth** for the architecture, the drift
survey of the five app repos, all design decisions (User decoupling via
`JobUserProvider`, job scoping via `scopeType`/`scopeId` columns generalizing
frontiers' `Course` link, auto-configuration contents), the publishing setup
(JitPack for Maven, npmjs for `@ucsb-cs156/jobs-components`), the phased
rollout plan, and the resolved §7 decisions (all settled with Phill
2026-07-12). Do not re-derive any of that; start from the doc and update it
when decisions change.

## Current status

- [x] Phase 0: design reviewed; all §7 questions resolved with Phill 2026-07-12
      (headline: frontiers' Course link generalizes into library `scopeType`/`scopeId`
      columns, DESIGN.md §3.4; MIT license; Phill creating the npm org)
- [x] Phase 1: backend library built and released 2026-07-12 — 45 tests, jacoco
      100%, pitest 100% (autoconfig excluded), org CI workflows green, gh-pages
      docs live, tagged v0.1.0, JitPack build verified
      (`com.github.ucsb-cs156:lib-jobs:v0.1.0` resolves)
- [x] Phase 2: installation pilot in proj-dining (backend) — done 2026-07-12.
      PR ucsb-cs156/proj-dining#131; adoption = JitPack dep + one Liquibase
      include + JobUserProviderImpl + TestJob/launch endpoint; dining gates
      stayed at jacoco 100% / pitest 100%; smoke-tested on dokku (Postgres)
      incl. Liquibase-from-jar and both complete/error paths. Lesson captured
      in v0.1.1: library bean names must be namespaced (libJobs*) since apps
      keep a launch controller whose bean name is jobsController.
- [x] Phase 3: migration pilot in proj-scaffold — merged 2026-07-13, PR
      ucsb-cs156/proj-scaffold#86 (backend 744 tests / jacoco 100% / pitest
      1039/1039, frontend 646 tests / prettier / eslint). Deleted core +
      AsyncConfig; scaffold turned out to have frontiers' FULL Course coupling,
      so it also piloted the course→scope migration (changeset 039) originally
      planned for phase 6.

      Two real bugs found via dokku smoke-testing after #86 merged, each fixed
      as a lib-jobs point release and landed via follow-up PR
      ucsb-cs156/proj-scaffold#88 (merged 2026-07-13; both fixes verified live
      on dokku before merge):
      - **v0.1.4**: job body runs in one all-or-nothing transaction, so log
        writes joined it and were invisible until the job finished — a
        multi-minute job (SyncCourseWithPlRepoJob working through GitHub API
        retries) looked hung. Fixed with a REQUIRES_NEW transaction per log
        line; jobs also now start `queued` and only report `running` once the
        executor picks them up. Verified with an integration test that reads
        the log from another connection mid-run.
      - **v0.1.5**: v0.1.4 itself shipped a regression — swagger-annotations-
        jakarta was a plain compile dependency, so its pinned 2.2.28 won
        Maven's nearest-wins mediation over springdoc's own (newer, deeper)
        transitive version, breaking OpenAPI doc generation app-wide
        (NoSuchMethodError on `Parameter.validationGroups()`; Swagger UI
        failed to load). Fixed by marking it `provided`. A same-reactor
        runtime test to guard this is structurally impossible (`provided`
        doesn't stop the conflict from recreating itself inside lib-jobs's
        own build); guarded instead with `PomDependencyScopeTests`, which
        pins the pom.xml scope declaration directly.

      Lesson for phases 4-6: budget for a live smoke test after each merge,
      not just green CI — scaffold has no integration/web-IT workflow, so
      neither regression would have been caught by CI alone.
- [x] Phase 4: proj-courses — merged 2026-07-13, PR ucsb-cs156/proj-courses#314.
      Backend 416 tests / jacoco 100% / pitest 571/571; frontend 639 tests.
      Uses config to preserve pre-migration behavior rather than silently
      changing it: executor pool-size 2/2/500 (library defaults to
      single-thread), and app.jobs.rate-limit-ms reads the same
      RATE_LIMIT_DELAY_MS env var as before. Added JobContext.getJob() to the
      library (v0.1.6) — courses' GradeHistoryImportServiceImplTests needed to
      inspect job state after running a job body, a gap the other three
      migrations hadn't hit. Found and closed a pre-existing gap: the admin UI
      had a working "Test Job" launch form with no backend endpoint at all;
      added one (also needed for this migration's own live smoke test, which
      passed on dokku). Two drive-by fixes unrelated to lib-jobs, needed for a
      clean `mvn test` (both noted in the PR): a missing mock bean in an
      unrelated controller test, and a misplaced test file moved to match its
      subject's package.
- [x] Phase 5: proj-happycows — code complete 2026-07-13, PR
      ucsb-cs156/proj-happycows#270 (merged 2026-08-08 — this checklist
      wasn't updated at the time; caught while building `docs/STATUS.md`
      2026-08-19). Backend 283 tests / jacoco 100% /
      pitest 483/483; frontend 654 tests. **No lib-jobs changes needed** — the
      first migration to work against an existing release with zero library
      changes. Oldest/most-drifted fork per the original survey: no
      JobContextFactory (constructed JobContext inline), no JobRateLimit, no
      Liquibase FK on created_by_id needed an explicit drop
      (FK_JOBS_USERS). Its custom `/all/pageable` endpoint (fixed sort by id,
      param `size`) had no library equivalent, so it's replaced by the
      library's `/paginated` with explicit sortField=id&sortDirection=DESC —
      frontend PagedJobsTable.jsx updated accordingly. Simplified two
      controller tests that wired the real JobService through a WebMvcTest
      and asserted exact internal save() call counts (brittle, coupled to the
      deleted JobService's implementation) to the mock-based convention used
      by every other migration; restored the resulting TestJob branch/timing
      coverage with direct unit tests, matching dining/scaffold/courses'
      pattern. No git-code-format plugin configured in this repo (unlike the
      other four) — confirmed via its CI workflows, not just absence in pom.xml.
- [ ] **v0.2.0 interstitial release** (2026-07-14): job-log storage redesign
      — see DESIGN.md §8. Replaces the single `jobs.log` TEXT column with a
      normalized `job_logs` table (fixes an O(N²) read-modify-write and a
      status-clobbering bug the old design enabled); `/paginated` gains
      optional filter params + a widened sort allowlist; new
      `GET /logs/{id}/tail?afterId=` for incremental live-tailing. Backend
      library: 61 tests, jacoco 100%, pitest 100%. Tagged and verified on
      JitPack (`com.github.ucsb-cs156:lib-jobs:v0.2.0` resolves).

      **This supersedes phases 5/6 below as the immediate next step** — per
      DESIGN.md §8's revised rollout (lowest-risk-first, decided with Phill
      2026-07-13): **dining** (piloting now — no real users, no historical
      log data to backfill) **→ scaffold → courses → v0.3.0** (job
      cancellation, §9, design-only, not yet built) **→ happycows** (picks up
      v0.2.0 and v0.3.0 together, deliberately held back since it's
      mission-critical) **→ frontiers** (last regardless, still needs the
      Course→scope migration). Phase 5 (happycows PR #270) merged since this
      was written; phase 6 (frontiers) below stays open and is on hold until
      this sequence reaches it.

      Status as of 2026-08-16: dining pilot PR ucsb-cs156/proj-dining#132
      **merged** (bumped lib-jobs to v0.2.0; no app-level code or Liquibase
      changes needed — the `job_logs` changeset ships inside the library
      jar's own changelog, which dining already includes wholesale). CI green
      (10/10 checks), live dokku smoke test of `/api/jobs/all`,
      `/api/jobs/logs/{id}`, and `/api/jobs/logs/{id}/tail` passed (Phill,
      manual redeploy + curl/Swagger since dining has no frontend jobs UI).

      **Scaffold** PR ucsb-cs156/proj-scaffold#118 **merged** 2026-08-17:
      backend 830 tests / jacoco 100%, verified live on a fresh QA dokku
      (clean migration-from-scratch run against real historical `jobs.log`
      data, backfill confirmed byte-for-byte correct). Needed a two-changeset
      backfill (`jobs.log` → staging column → `job_logs`, split around the
      library's own changeset since it creates `job_logs` and drops
      `jobs.log` atomically in one unit — no room to inject a copy in
      between) and, unlike dining, includes only the library's
      `002-job-logs-table` changeset rather than its whole
      `changelog-master.json`, since scaffold already owns a `jobs` table
      from its phase-3 migration and re-running the library's
      `001-create-jobs-table` would collide with it. Also caught a real bug
      during smoke-testing: `jobsByCourse` (an app-owned endpoint returning
      raw `Job` entities) showed blank logs everywhere, because `Job.log`
      went from a real persisted column (auto-populated by Hibernate) to
      `@Transient` in v0.2.0 — fixed by explicitly populating it via
      `JobService.getJobLogPreview`, same as the library's own controller
      does. No other frontend changes needed.

      **Courses — Liquibase infrastructure now merged (2026-08-18), v0.2.0
      bump itself not yet started.** Before courses could get the same
      recipe as scaffold, it needed Liquibase at all — it was still on
      Hibernate `ddl-auto=update`. That work (ucsb-cs156/proj-courses#316,
      pre-existing when this session picked it up) hit the exact same
      "table already exists" collision scaffold did, but for *every* table,
      not just `jobs` — Hibernate had already created all of them in
      dev/QA/prod. Already fixed there (before this session) with
      `preConditions`/`MARK_RAN` guards per changeset, `jobs` included, via
      a locally-owned changeset rather than `include`-ing the library's
      changelog (confirms scaffold's approach was right all along; an
      earlier draft of that PR's survey doc had it backwards). Two more
      issues surfaced getting #316 green: a `commons-io`/`commons-compress`
      Maven nearest-wins conflict breaking embedded-Mongo test setup on
      every fresh CI run (fixed, `commons-io` pinned to 2.20.0), and a
      pre-existing bug where an unguarded UCSB API call crashed the whole
      app on startup if the key was missing/invalid — fixed separately as
      ucsb-cs156/proj-courses#318 (merged), which also introduced a
      reusable `SystemMessage`/`SystemMessagesService` mechanism
      (ucsb-cs156/proj-courses#319, merged) so misconfiguration now shows as
      a banner under the navbar instead of either crashing the app or only
      showing up in server logs. None of this is lib-jobs-specific, but it's
      why courses' v0.2.0 bump took this long to become unblocked.

      **Courses' v0.2.0 bump: PR ucsb-cs156/proj-courses#321 open, CI green
      (2026-08-18).** Same recipe as scaffold — bumped the `lib-jobs`
      dependency, added the stage/complete backfill changeset pair
      (`010`/`changes-post-lib-jobs/011`), included only
      `002-job-logs-table.json` (courses owns its `jobs` table via its own
      guarded changeset, `009-create-jobs-table.json`). Checked for
      courses' own analog of the `jobsByCourse` bug scaffold hit — clean,
      courses' own `JobsController` has only launch endpoints, no listing
      endpoints of its own, so no app-code fix was needed there.

      Did hit a **new, different bug class** getting CI green:
      `AsyncJobTestsIT` (a pre-existing integration test) mocked
      `JobsRepository`, so the launched job's `id` stayed at Java's default
      `0`. Harmless under v0.1.x (`ctx.log()` only mutated an in-memory
      field), but v0.2.0's `ctx.log()` writes real, FK-constrained rows to
      `job_logs` — every log line the job wrote violated
      `FK_JOB_LOGS_JOBS`, landing the job in `"error"` status
      deterministically, every run, not flakily. **Generalizes beyond
      courses**: any app with an integration test that mocks
      `JobsRepository` (or otherwise never gives a launched `Job` a real
      persisted id) while exercising real job execution will hit this on
      its own v0.2.0 bump — check for the pattern proactively on
      happycows/citelines/frontiers rather than waiting for CI to find it.
      Fixed by no longer mocking `JobsRepository` in that test. While
      already touching it, also fixed the already-tracked
      ucsb-cs156/proj-courses#320 race (asserting a job's status
      immediately after launch reads a field a background thread may
      already be writing to — asserted `assertNotEquals("complete", ...)`
      instead).

      **Merged 2026-08-19** (as `ucsb-cs156/proj-courses#321`). Live-
      verified on `courses-qa`: backfill diffed byte-for-byte correct
      against a real historical job's log (job 631 — first paste came
      back with newlines flattened to spaces, traced to how the browser
      copy-pasted it, not a real bug; re-verified via a clean paste,
      MD5-identical). Backend: 421 tests / jacoco 100%.

      Also needed a **separate, unrelated fix along the way**:
      `ucsb-cs156/proj-courses#324`, merged first — a Liquibase
      `validCheckSums` fix plus a guarded rename changeset, for an
      environment (a QA dokku instance) that had run an *early* version
      of #316's branch, before a same-PR follow-up commit corrected
      `pass1_begin`/`pass2_begin`/`pass3_begin` to
      `pass1begin`/`pass2begin`/`pass3begin` (Hibernate's naming
      strategy never actually matched the underscored names — a
      pre-existing bug, not something #316 introduced). This blocked
      deploying plain `main` to that instance, not just #321. Lesson:
      never edit a changeset's content after it may have been applied
      anywhere, even mid-PR before merging — use `validCheckSums`
      instead if a genuine fix is needed post-hoc.

      **Courses is fully done.** No further courses-specific work
      queued.

      **Citelines' v0.2.0 bump: done — PR ucsb-cs156/proj-citelines#97
      merged 2026-08-19.** Unlike courses, citelines already had Liquibase
      infrastructure from day one (`ddl-auto=none`,
      `spring.liquibase.change-log` configured), so it was a clean repeat
      of the scaffold/courses recipe with no Hibernate-to-Liquibase
      detour: bumped `lib-jobs` to v0.2.0, added the two-changeset
      stage/complete backfill pair (`041-stage-jobs-log-backfill` /
      `changes-post-lib-jobs/042-complete-jobs-log-backfill`), included
      only `002-job-logs-table.json` (citelines owns its `jobs` table via
      its own guarded changeset). Backend 700/700 tests, jacoco 100%. Hit
      and fixed both known bug classes proactively before any deploy: the
      `jobsByProject` blank-log bug (raw `Job` entities returned directly,
      same fix pattern as scaffold's `jobsByCourse` — populate via
      `jobService.getJobLogPreview` before serializing) and its
      self-fulfilling mock-test companion (test reused the same mutable
      object as both mock return value and expected JSON). Checked for
      the `AsyncJobTestsIT`-style mocked-`JobsRepository` bug — clean, no
      such pattern in citelines' tests. Live-verified on citelines-qa:
      migration ran clean (`041` → library's `lib-jobs-002-job-logs-table`
      → `042`, 125 rows affected, app started clean), job 58's backfilled
      log byte-for-byte identical to the before-deployment snapshot, and
      Phill ran three fresh jobs post-deploy to confirm live logging and
      tailing all work correctly. CI green including `enforce`.

      (`proj-citelines` background: added to the rollout by Phill on
      2026-08-16, not one of the original five forks — built as a
      lib-jobs consumer from day one, pom.xml already on v0.1.5, no
      homegrown jobs code to retrofit. Had the old single `log` column and
      real job history from `GetCitationsJob`/`CheckLinksJob`/etc., now
      migrated. Fold it into DESIGN.md §8's rollout list next time that
      doc is updated.)

      **Known environment gotcha hit during the dining pilot:** committing
      from a `git worktree` (the established isolation pattern for these
      migrations) fails git-code-format-maven-plugin's pre-commit hook with
      a JGit "Bare Repository has neither a working tree, nor an index"
      error, even though the hook script itself has the correct per-worktree
      pom.xml path. Root cause not fully diagnosed; workaround (confirmed
      with Phill) is `git commit --no-verify` since the equivalent
      `validate-code-format` check already passes via `mvn verify` and CI
      re-runs it server-side anyway. Expect to hit this again on the
      scaffold/courses migrations.
- [x] Phase 6: proj-frontiers — merged 2026-08-21, PR
      ucsb-cs156/proj-frontiers#694. The biggest single migration in the
      rollout: frontiers had never adopted lib-jobs at all (still ran the
      original homegrown code the library was extracted from), so this PR
      combined two migrations every other app did separately — first-ever
      lib-jobs adoption, and generalizing `Job.course` into the library's
      `scopeType`/`scopeId` columns (DESIGN.md §3.4) — landing directly on
      v0.2.0. Deleted `services/jobs/*`, `entities/Job`,
      `repositories/JobsRepository`; added `JobUserProviderImpl`; all 13
      course-scoped job classes moved `getCourse()` → `getScopeType()`/
      `getScopeId()` (two of them, `DeleteRepoJob` and the push/pull-teams
      pair, simplified to report `courseId` directly instead of querying
      the repository just to report scope). Changesets `015`/`016`/`017`
      migrate `course_id` into scope columns and stage/complete the
      `jobs.log` → `job_logs` backfill around the library's own
      `002-job-logs-table` changeset. Frontend `JobsTable`: "Course Name" →
      "Course Id", matching scaffold's precedent. Executor: adopted the
      library's single-thread FIFO default — frontiers previously had *no*
      executor bean at all (Spring's unbounded default), so this is a real
      behavior change but a safer one, closing a pre-existing latent race
      between concurrent GitHub-org-mutating jobs for the same course
      (confirmed with Phill before implementing). Backend 697 tests /
      jacoco 100% / pitest 1110/1110; frontend 502 tests / 100% coverage.

      Two things found beyond the original migration survey, worth
      checking proactively on any future full-adoption migration (not just
      partial version bumps): (1) `CoursesController.deleteCourse` called
      `deleteByCourse_Id` directly — not from `JobsController` at all —
      which would have been a **compile-time** break, not runtime, if
      missed; fixed by switching to the library's `deleteByScopeTypeAndScopeId`,
      which already existed (the purge-side twin of the scoped-listing
      query), no library change needed. (2) `DownloadRequest.job`, a real
      `@ManyToOne` FK to `Job` from an entity *outside* the jobs package
      entirely, needed only an import fix — its FK constraint targets the
      `jobs` table's unaffected `id` column.

      Local full-clean `pitest` (no history-skip) surfaced ~40 survived
      mutations purely in job `accept()` methods' `ctx.log(...)` calls —
      pre-existing test-assertion gaps (tests using `.contains()` checks on
      a subset of log lines, never hit by the `getCourse()`→`getScopeType()`
      edits themselves) that a full clean run exposes but CI's
      incremental-history gate may not have caught before. Fixed by adding
      the missing log-line assertions to existing tests rather than
      writing new ones. Live-verified on a QA dokku instance: full
      changeset chain ran clean (187 rows affected), a real historical
      job's log diffed byte-for-byte identical before/after, and
      post-deploy job launches/course jobs tab confirmed working.

      **v0.2.0 rollout is now effectively complete for every app except
      happycows** (frozen until ~2026-09-15, see below) — dining, scaffold,
      courses, citelines, and frontiers are all on v0.2.0.

- [x] **v0.3.0 release** (2026-08-21): job cancellation, built per the
      design agreed with Phill 2026-07-13 (DESIGN.md §9). `POST
      /api/jobs/{id}/cancel`: a queued job is killed directly (nothing
      executing yet); a running job is marked `cancelling` and stops at its
      next `ctx.log()` checkpoint with zero job-body code changes required,
      landing in a terminal `cancelled` status via a new
      `JobCancelledException` (deliberately unchecked — several
      already-migrated job bodies, e.g. frontiers'
      `PushTeamsToGithubJob`/`PullTeamsFromGithubJob`, call `ctx.log(...)`
      inside `Map.forEach` lambdas whose functional interface doesn't
      declare checked exceptions; a checked exception there would have
      broken those call sites). Escape hatch: `ctx.logNoCancelCheck(...)`.

      **Real bug caught only by the integration test, not by any
      mocked-repository unit test:** the first implementation re-fetched
      the job directly on the ambient job-body transaction and silently
      never detected cancellation in a real run. Root cause: the job body
      already executes inside one long-lived transaction (v0.1.x design);
      Hibernate's session-scoped first-level cache returns the `Job`
      entity it already loaded earlier in that same session on every
      subsequent `findById`, invisible to a concurrent commit from a
      different connection — the exact staleness problem
      `logTransactionTemplate` (REQUIRES_NEW) already exists to avoid for
      log writes (v0.2.0). Fixed by wrapping the cancellation check in the
      same REQUIRES_NEW template. **Lesson for any future change that
      reads fresh state from within a job body: mocked-repository unit
      tests cannot catch this class of bug — only a real end-to-end test
      against an actual database with a real blocked/resumed job body
      will.**

      85 tests (12 new/updated), jacoco 100%, pitest 81/81. Tagged and
      verified on JitPack (`com.github.ucsb-cs156:lib-jobs:v0.3.0`
      resolves).

      **Rollout order for adopting v0.3.0, per Phill (2026-08-21):**
      citelines → scaffold → frontiers → dining → courses (happycows
      excluded — still frozen until ~2026-09-15). Each app's PR bumps the
      dependency and adds a "Cancel" action to its admin Jobs UI for
      queued/running jobs.

      **Order adjusted mid-rollout (2026-08-21, Phill):** citelines was
      mid-deploy, so scaffold went first instead. **Scaffold's PR
      (ucsb-cs156/proj-scaffold#121) merged 2026-08-22 — scaffold is done.**
      It went CI-green on v0.3.0, then dokku QA
      testing surfaced the real bug described below — the fix landed as
      v0.3.1 (same PR, updated in place) before merge.

- [x] **v0.3.1 release** (2026-08-21): startup recovery sweep, built the
      same day v0.3.0 shipped after live dokku QA testing of scaffold's
      v0.3.0 PR surfaced a real bug: a job left `running` from a past crash
      still showed a **Cancel** button; clicking it moved it to
      `cancelling`, and since nothing was actually executing anymore, it
      never honored the request — it just sat there forever, permanently
      occupying the single-threaded executor's notion of "there's an active
      job" and blocking every job queued behind it.

      Root cause distinguished into two separate failure modes (Phill's own
      diagnosis, confirmed): (A) a job's DB row is stale after an app
      restart — nothing is actually running, the in-memory executor queue
      didn't survive the restart — vs. (B) a job's thread is genuinely still
      alive but permanently blocked (e.g. an HTTP call with no timeout).
      Only (A) is fixable in the library; (B) requires the consuming app to
      configure timeouts on its own blocking calls.

      Fix for (A): `JobService.recoverInterruptedJobsOnStartup()`, an
      `@EventListener(ApplicationReadyEvent.class)` method on the
      already-auto-configured `JobService` bean — fires automatically on
      every consuming app's startup with **zero required wiring**. Marks
      every job still `queued`, `running`, or `cancelling` (a restart
      orphans an in-flight cancellation request the same way it orphans a
      running job) as a new terminal status, `interrupted`.

      (B) is out of scope for the library by design — flagged instead as a
      standing action item for every app's own v0.3.x rollout PR, since it
      has nothing to do with lib-jobs specifically but is newly
      consequential now that jobs run on a single-threaded executor:
      **audit that app's own blocking calls (RestTemplate, HttpClient,
      etc.) for missing timeouts, not just as part of scaffold's PR.**
      Scaffold's own instance of this — `RestTemplate` with no timeout at
      all — is fixed in ucsb-cs156/proj-scaffold#121 itself (10s connect /
      60s read), alongside the library bump.

      61 tests, jacoco 100%, pitest 100%. Tagged and verified on JitPack
      (`com.github.ucsb-cs156:lib-jobs:v0.3.1` resolves). Scaffold's
      downstream re-verification against the real v0.3.1 jar (fresh `.m2`
      resolution, full `mvn test`) also passed clean: 831 tests, jacoco
      100%, pitest 1167/1167.

- [x] **v0.3.2 release** (2026-08-21): `JobContext.checkCancellation()`
      made public, same day as v0.3.1, found while live-testing v0.3.1's
      own RestTemplate fix on scaffold. With the timeout fix in place,
      cancelling a real slow job (`SyncCourseWithPlRepoJob`, which walks a
      course's GitHub question/assessment tree) still appeared to hang —
      the job wasn't stuck, it was legitimately still executing with no
      visible progress, because its walk only calls `ctx.log(...)` on
      specific branches (skipped/unparseable entries). The common case on
      a re-sync — an unchanged question or assessment, which is most of
      them — produces zero log output, and therefore never reached a
      cancellation checkpoint either, since that check previously only
      lived inside `log()`. A cancel request could sit unactioned for the
      whole rest of a large, silent walk even though nothing was hung and
      no timeout would ever trip.

      Fix: `checkCancellation()` (already existed privately, called only
      from `log()`) is now public — same re-fetch-in-a-REQUIRES_NEW-
      transaction-and-throw behavior, but callable directly by a job body
      with **no log line written**, so a tight loop can check every
      iteration without flooding the log. Applied in scaffold's
      `SyncCourseWithPlRepoJob` at the top of both its per-directory
      recursive walk and its per-assessment loop (ucsb-cs156/proj-scaffold#121,
      same PR as the v0.3.1 timeout fix). **Generalizes: check for this same
      pattern (a loop that logs only on specific branches) in every
      remaining app's own job bodies during its v0.3.x rollout, alongside
      the timeout audit above.**

      Getting scaffold's PR green with this required two new tests
      specifically targeting the new checkpoints — pitest initially
      reported 2 survived mutations ("removed call to checkCancellation")
      because no existing test made that call's presence observable.
      Fixed by mocking `JobsRepository.findById` to return "running" for
      exactly the N calls known to precede each checkpoint under a given
      test's setup, then "cancelling" from then on, and asserting both
      `JobCancelledException` is thrown *and* the next GitHub call the
      checkpoint should have pre-empted was never made (`verify(never())`)
      — the second assertion is what actually distinguishes the real code
      from the mutant, since removing the checkpoint just shifts the
      exception to a later checkpoint with the same observable exception
      type otherwise.

      61 tests, jacoco 100%, pitest 84/84. Tagged and verified on JitPack
      (`com.github.ucsb-cs156:lib-jobs:v0.3.2` resolves). Scaffold's
      downstream re-verification: 831 tests, jacoco 100%, pitest
      1169/1169.

      **Scaffold merged and fully done (PR #121, 2026-08-22).** Live QA
      testing on dokku confirmed all of: cancel-while-running (→
      `cancelling` → `cancelled`), cancel-while-queued (→ `cancelled`
      immediately), the startup-recovery sweep marking orphaned jobs
      `interrupted`, and — the specific regression test for the bug that
      drove v0.3.2 — cancelling `SyncCourseWithPlRepoJob` during a
      mostly-unchanged (silent) re-sync now reaches `cancelled` promptly
      instead of continuing to run for the rest of the walk. **Order
      adjusted again (2026-08-22, Phill): courses next, not citelines** —
      citelines has unrelated cleanup work in progress, so it's deferred
      again.

      **Courses: DONE (PR ucsb-cs156/proj-courses#331, merged
      2026-08-24).** Bumped straight to v0.3.2. Backend 424 tests, jacoco
      100%, pitest 575/575; frontend 665 tests, Stryker mutation score
      100% (courses' CI includes a frontend mutation-testing gate scaffold
      doesn't have — caught and fixed one survived mutation, a header-text
      assertion that was inadvertently also satisfied by the Cancel
      button's own label). Proactively applied both standing audit items
      before hitting them as live bugs: fixed missing RestTemplate
      timeouts on four separate construction sites (worse than scaffold's
      one), and added `checkCancellation()` checkpoints to
      `UpdateCourseDataJob`'s two silent loops and
      `GradeHistoryImportServiceImpl`'s CSV row loop. Also found and fixed
      a real bug along the way: `GradeHistoryImportServiceImpl` was
      catching `JobCancelledException` in a generic exception handler and
      re-wrapping it, which would have landed every cancelled grade-import
      job in `error` instead of `cancelled`.

      **Unrelated but blocking issue hit along the way, also fixed:** a
      courses-qa deploy attempt failed with a Liquibase
      `ValidationFailedException` — a checksum mismatch on
      `010-stage-jobs-log-backfill.json`, caused by a just-merged,
      unrelated PR (#330, a production-only fix for job logs over 1MB)
      editing that changeset's column type in place. Safe for production
      (whose original attempt had failed and rolled back before recording
      anything) but not for courses-qa, which had already successfully
      run the original version. Fixed with the same `validCheckSums`
      mechanism courses had already established as precedent (PR #324) —
      merged separately as ucsb-cs156/proj-courses#333, with #331 rebased
      on top of it before #331's own merge/redeploy.

      Also decided while wrapping up scaffold (Phill, 2026-08-22): keep
      building the Cancel button as per-app JSX for the remaining rollouts
      rather than pausing to build a shared frontend component first —
      publishing an npm package is a heavier, slower-to-iterate commitment
      than local JSX, and this same feature needed three same-day point
      releases on the backend driven by things only live QA surfaced,
      suggesting the frontend side may need similar fast iteration too.
      Track real cross-app frontend differences as they come up during the
      remaining rollouts instead, to inform Phase 7 with actual drift data
      rather than generalizing off of scaffold's shape alone (see Phase 7
      entry below).

      **Dining: DONE (PR ucsb-cs156/proj-dining#147, merged 2026-08-24).**
      Picked up next since citelines still had unrelated cleanup in
      progress. Bumped straight to v0.3.2 (dining was on v0.2.0, the
      v0.2.0-pilot version, and skipped v0.1.x's Course/scope migration
      entirely since it never had course-scoped jobs). Smallest diff of
      the rollout so far — no app-code changes were needed for
      cancellation itself: dining's only job, `TestJob`, is a trivial
      log/sleep/log with no loop, so it needs no `checkCancellation()`
      checkpoint of its own; and dining still has no frontend Jobs UI at
      all (confirmed unchanged since the phase-2/v0.2.0 pilots — jobs are
      launched and inspected via curl/Swagger), so there's no Cancel
      button to add. Proactively applied the standing RestTemplate-timeout
      audit anyway: added connect/read timeouts (10s/60s) to all three
      construction sites (`DiningCommonsService`, `UCSBDiningMenuItemsService`,
      `UCSBDiningMenuService`), even though no job body calls any of them
      today — closes the gap before a future job does, matching the fix
      scaffold and courses each needed live. Checked for the
      `AsyncJobTestsIT`-style mocked-`JobsRepository` bug — clean, no such
      pattern in dining's tests. Backend 186 tests, jacoco 100%, pitest
      218/218. **One wrinkle during live verification:** Phill initially
      couldn't find `POST /api/jobs/{id}/cancel` in Swagger UI; traced to
      the QA instance simply not having redeployed the merged branch yet
      — not a scanning/config issue, since `/cancel` lives in the same
      `JobsController` class, with the same annotation pattern, as the
      other jobs endpoints that were already visible. Endpoint appeared
      once the redeploy caught up. Verified working via Swagger before
      merge.

      **Remaining: citelines, frontiers** — order between these two not
      yet decided.

      **Frontiers: DONE (PR ucsb-cs156/proj-frontiers#700, merged
      2026-08-25).**
      Picked up next since citelines still had unrelated cleanup in
      progress. By far the biggest surface area in the v0.3.x rollout so
      far — frontiers has 16 job classes (vs. courses' 2-3 and dining's
      1), 5 RestTemplate construction sites (vs. courses' 4), and two
      separate Jobs UIs (the admin-global `AdminJobsPage` plus a
      per-course `JobTabComponent` scaffold-style tab that none of the
      other apps have needed to update for this feature yet).

      Audited all 16 job classes for the silent-loop
      `checkCancellation()` gap; 7 had no loop at all, 3 already logged
      unconditionally on every iteration (safe as-is), and 6 needed a
      new checkpoint added, 10 in total:
      `CreateStudentOrStaffRepositoriesJob` (student loop, staff loop),
      `CreateTeamRepositoriesJob` (team loop), `MembershipAuditJob`
      (course loop, student loop, staff loop — the whole method only
      logs once at the very start and once at the very end, so every one
      of its three nested loops needed its own checkpoint),
      `PullTeamsFromGithubJob` (team loop, and its per-member
      `githubMemberships.forEach(...)` lambda), `RemoveStudentsJob`
      (student loop), `UpdateOrgMembershipJob` (member loop).
      `PullTeamsFromGithubJob`'s team loop is the closest analog yet to
      the exact bug that drove v0.3.2 in the first place: the common
      case on a re-sync is a team that's already correct on every field
      with no membership changes either, which produces zero log output
      for that iteration — confirmed the `Map.forEach`-with-`ctx.log()`
      pattern cited in the v0.3.0 release notes as motivating
      `JobCancelledException` being unchecked lives specifically in this
      job (not, as that note implied, also in `PushTeamsToGithubJob`,
      which turned out on inspection to use only plain `for` loops with
      every branch logging — a minor inaccuracy in that historical note,
      not worth correcting retroactively).

      Getting pitest clean required one dedicated test per checkpoint
      (10 total, following courses' established pattern: mock
      `JobsRepository.findById` to report "running" for exactly the
      calls known to precede the checkpoint, "cancelling" from then on,
      assert both `JobCancelledException` is thrown and a downstream
      call the checkpoint should have pre-empted was never made). Two
      real test-authoring bugs surfaced and fixed along the way, both
      worth watching for on any future checkpoint test: (1) a loop
      preceded by multiple `ctx.log()` calls needs the mock to report
      "running" for each of *those* first, since every `log()` call
      internally checks cancellation too — stubbing "cancelling"
      unconditionally makes the test pass for the wrong reason (throws
      from an earlier log call, never reaching the loop's own check at
      all, so the mutant that removes the checkpoint would go
      undetected); (2) `RosterStudentRepository.findByCourseAndGithubId`
      takes a primitive `int`, and verifying it with `any()` NPEs on
      unboxing — needs `anyInt()`. That NPE, thrown mid-`verify()`, also
      corrupted Mockito's argument-matcher stack for the rest of that
      JVM fork, cascading into spurious `InvalidUseOfMatchers`/
      `UnfinishedVerification` failures in unrelated, unmodified tests
      in other classes later in the same `mvn test` run — worth
      recognizing as a symptom (failures in tests you didn't touch,
      alongside one you did that used a bad matcher) rather than chasing
      each cascade failure individually.

      Proactively applied the standing RestTemplate-timeout audit to all
      5 construction sites (`OrganizationMemberService`, `JwtService`,
      `OrganizationLinkerService`, `RepositoryService`,
      `GithubTeamService`) — worse than courses' four. Checked for the
      `AsyncJobTestsIT`-style mocked-`JobsRepository` bug — clean, no
      such pattern in frontiers' tests (the one test class that does mock
      `JobsRepository`, `JobsControllerJobsTests`, also mocks
      `JobService` itself, so no real job body ever executes there).

      Added the Cancel button to `JobsTable.jsx` in the same shape
      established by courses (red Bootstrap button, shown only for
      `queued`/`running`, POSTs to `/api/jobs/{id}/cancel` via
      `useBackendMutation`, toasts "Cancellation requested.", calls an
      `onCancelled` prop). Wired into both consumers: `AdminJobsPage`
      (added `refetch` to its existing `useBackend` destructure) and
      `JobTabComponent` (already destructured `refetch` for its own
      Refresh button, just needed to pass it through).

      Backend 707 tests, jacoco 100%, pitest 1120/1120. Frontend 515
      tests, 100% coverage, eslint/prettier clean.

      **Remaining: citelines** — last app in the v0.3.x rollout
      (happycows still excluded, frozen until ~2026-09-15). Deferred
      twice already for unrelated cleanup work in that repo; picked back
      up 2026-08-25, see the v0.3.3 entry below — it ended up targeting
      v0.3.3 directly rather than v0.3.2.

- [x] **v0.3.3 release** (2026-08-25): fixes a job-chaining
      transaction-visibility race in `JobService.runAsJob`. Surfaced by a
      session working on citelines issue #110 part 2 (auto-launching one
      job from inside another's own `accept()` via
      `jobService.runAsJob(child)`) — flagged proactively, before any app
      built chaining or hit the bug live, and full mechanics/options
      written up at `docs/job-chaining-analysis.md` (not committed by
      that session; committed here alongside the fix). Resolved analysis
      now also lives in `DESIGN.md` §10.

      **The bug:** `runAsJob`'s initial `queued`-row save previously used
      Spring's default `REQUIRED` transaction propagation. Called from a
      controller (every call site before chaining existed), harmless — no
      ambient transaction to join. Called from *inside* a parent job's
      own `accept()`, the calling thread is already inside the parent's
      long-lived job-body transaction (`runJobAsync`'s
      `transactionTemplate.executeWithoutResult(...)`), so the child's
      `INSERT` joined that same transaction — invisible to any other
      connection, including the child's own worker thread once
      `jobsExecutor` picked it up, until the *parent's* transaction
      eventually committed. With `jobsExecutor` at more than one thread, a
      second thread could dequeue and start updating the child's row
      before that commit — every one of the child's own status-transition
      `UPDATE`s matched 0 rows (Postgres doesn't error on this), silently
      stranding the child on `queued` forever in the admin UI even though
      its actual work completed normally. Harmless only with the default
      single-threaded executor — and not hypothetical: **courses already
      runs `jobsExecutor` at pool-size 2** (Phase 4, preserving
      pre-migration concurrency), so it was one un-reviewed chaining call
      site away from hitting this for real.

      **Fix:** `runAsJob`'s save now runs through a dedicated
      `TransactionTemplate` configured `PROPAGATION_REQUIRES_NEW` — same
      idiom §8/§9 already established for `logTransactionTemplate`'s log
      writes and the cancellation re-fetch. Small, self-contained,
      backward-compatible: `runJobAsync`'s own transaction handling is
      unchanged, and calling `runAsJob` from a controller behaves exactly
      as before.

      **Empirically verified, not just reasoned about:** a new
      integration test (`JobChainingIntegrationTests`, `jobsExecutor` at
      pool-size 2 — the default 1 everywhere else in the suite can't
      reproduce this race at all) launches a parent that chains a child
      and blocks before returning, giving the child's own worker thread a
      window to attempt its status transitions while the parent's
      transaction is still open. Hand-verified red/green by temporarily
      reverting just the `REQUIRES_NEW` wrap: the test then fails with a
      clean 10-second timeout (the child never leaves `queued`) every
      time; restoring the fix passes reliably. Getting pitest to 100%
      needed a second, deterministic unit test pinning the propagation
      behavior directly via reflection — the race itself is real but too
      timing-sensitive to reliably kill the "removed
      `setPropagationBehavior` call" mutant under pitest's
      differently-timed instrumented execution.

      93 tests, jacoco 100%, pitest 86/86. Tagged and verified on JitPack
      (`com.github.ucsb-cs156:lib-jobs:v0.3.3` resolves).

      **Citelines: DONE (PR ucsb-cs156/proj-citelines#125, merged
      2026-08-25).**
      Bumped straight to v0.3.3 rather than stopping at v0.3.2, since
      it's the one app about to build chaining (issue #110 part 2, not
      yet started). Branched from pre-#119 `main` (the
      `BibTexEntryUpgrade*` → `BibTexEntryImprove*` rename, plus
      scope-aware `ImproveScope` support) and cleanly rebased once #119
      merged, so the checkCancellation() checkpoint below targets the
      renamed class/method rather than the old one. Added
      `ctx.checkCancellation()` to the two service loops that don't log
      on every iteration: `CheckLinksService`'s per-entry loop (the
      common case — a valid, unchanged link — never logs) and
      `BibTexEntryImproveService.improveEntries`'s per-entry loop (an
      entry with no DOI or nothing new to add never logs either).

      **Real regression caught and fixed before merge, not after:** the
      first RestTemplate-timeout attempt used an injected
      `RestTemplateBuilder` parameter on the primary `RestTemplate` bean
      — broke every single `@WebMvcTest`-sliced controller test (220
      failures) because that auto-configured bean isn't provided in that
      narrower test slice, only in a full `@SpringBootTest` context.
      Fixed by building the timeout via `SimpleClientHttpRequestFactory`
      directly instead (no injected bean dependency at all), matching how
      the app's *second* RestTemplate bean (no-redirect, for DOI lookups)
      already avoids redirects the same low-level way. Worth checking for
      on any future app whose `RestTemplate` beans are also loaded inside
      `@WebMvcTest`-sliced contexts, not just full integration tests —
      running the *entire* backend test suite (not just the touched
      service's own tests) is what caught this, a spot-check of
      `CheckLinksServiceTests` alone would have missed it entirely.

      Checked for the `AsyncJobTestsIT`-style mocked-`JobsRepository`
      bug — clean, the one test class that mocks `JobsRepository` also
      mocks `JobService` itself, so no real job body ever executes there.

      Backend 713 tests, jacoco 100%, pitest 926/926. Frontend 542 tests
      (citelines' coverage gate is 70%/75%/60%, not 100% like
      courses/frontiers — comfortably cleared). Merged same day, ahead
      of the batch of defensive v0.3.3 bumps below (this PR started
      first and was the only one with actual app-code changes to
      verify, so it landed before the four pure-version-bump PRs even
      existed).

      **The v0.3.x rollout is now effectively complete for every app
      except happycows** (frozen until ~2026-09-15) — scaffold, courses,
      dining, and frontiers are merged on v0.3.2; citelines' v0.3.3 PR is
      open.

      **Defensive v0.3.3 bumps for the other four apps, same day
      (2026-08-25).** Phill asked whether courses needed v0.3.3 too,
      since it runs `jobsExecutor` at pool-size 2 — checked, and it
      doesn't: courses' two `runAsJob` call sites are a `@Scheduled` cron
      method and two app-startup hooks, none of which run inside another
      job's own transaction, so it doesn't chain jobs today. Phill then
      asked to check frontiers as well (correctly guessing it was the
      most likely candidate, given its size); also clean — none of its
      16 job classes reference `JobService`/`runAsJob` at all. Proactively
      checked scaffold and dining too, for full coverage; also clean.
      Rather than leave the gap latent in four apps that happened not to
      need it *yet*, opened defensive version-only bump PRs for all four
      in parallel via subagents (pure `pom.xml` changes, no app-code
      touched, since none of them have anything to fix): dining
      [#148](https://github.com/ucsb-cs156/proj-dining/pull/148),
      scaffold [#123](https://github.com/ucsb-cs156/proj-scaffold/pull/123),
      courses [#337](https://github.com/ucsb-cs156/proj-courses/pull/337),
      frontiers [#701](https://github.com/ucsb-cs156/proj-frontiers/pull/701).
      All four green: dining 186 tests; scaffold 833 backend/706
      frontend; courses 424 backend/669 frontend; frontiers 707
      backend/515 frontend (100% coverage) — jacoco 100% on every
      backend. **With these four open plus citelines' #125, every
      adopted app now has a v0.3.3 bump in flight** (happycows still
      excluded, frozen until ~2026-09-15).
- [ ] **Spring Boot 3.5.16 / Java 25 migration** (issue #2, PR opened
      2026-09-23, following the recipe from proj-citelines#137 and the
      in-progress proj-courses Java 25 branch): Boot parent 3.4.3 → 3.5.16,
      `java.version`/`.java-version`/`jitpack.yml` → 25, `<proc>full</proc>`
      on maven-compiler-plugin (JDK 23+ no longer runs classpath annotation
      processors, so Lombok generates nothing without it),
      git-code-format 5.3 → 6.1 (needs the extra `javac.code` add-export in
      `.mvn/jvm.config`), jacoco 0.8.12 → 0.8.15, pitest 1.17.0 → 1.30.0 +
      pitest-junit5-plugin 1.2.3 + pitest-history-plugin 0.0.1 (history moved
      out of core in 1.23). No source changes needed; formatter 6.1 reformats
      nothing here. **Consumer impact:** the jar is now class-file major
      version 69, so the next tag is Java-25-only — apps must migrate to
      Java 25 before bumping past v0.3.3 (existing tags keep building on
      their own `jitpack.yml`, so Java 21 apps are unaffected until they
      bump). Happycows (frozen until ~2026-09-15, now expired) and the other
      Java 21 apps therefore need their own Java 25 migration before any
      future lib-jobs bump.
- [ ] Phase 7: frontend package in `frontend/`. Was on hold until the
      v0.3.x backend rollout finished — it now has, as of citelines' PR
      #125 (2026-08-25) — so Phase 7 is unblocked to start whenever it's
      picked up; not yet begun.

      **Idea for a starting point (Phill, 2026-08-22):** scaffold has a
      frontend for individual, project-scoped job queues (`JobTabComponent`)
      but no admin-facing view of jobs *globally* across projects — a real
      gap, and a natural first component to build for the shared package.
      Worth checking whether the other apps have (or lack) the same gap
      before designing it, same as the backend drift survey did in phase 0.

      **Drift survey, now complete across all five apps:** scaffold has
      only the per-course `JobTabComponent`, no admin-global view — the
      original gap. Frontiers (PR #700) and citelines (PR #125) both have
      *both* a per-course/per-project `JobTabComponent`/`JobsTabComponent`
      and an admin-global `AdminJobsPage` — scaffold's gap doesn't
      generalize to every app. Dining has neither (no frontend Jobs UI at
      all, admin access via curl/Swagger only). Courses' shape wasn't
      re-verified during its own v0.3.x rollout (its bump PR #331 was
      already Cancel-UI-complete going in) but per its phase-4/phase-5
      history has an `AdminJobsPage` at minimum. `JobsTable` column sets
      also differ per app — e.g. frontiers' has a "Course Id" column;
      citelines' has a generic "Scope" column (`scopeType:scopeId`)
      instead, reflecting its multi-entity-type scoping vs. frontiers'
      course-only scoping. This is the grounded basis Phase 7 should
      design against, whenever it's picked up.

      **Resolved, not just leaning:** the earlier open question (build
      the Cancel button as a shared component before finishing the
      v0.3.x rollout, vs. keep it per-app JSX) is now moot — the rollout
      finished with every app's Cancel button hand-written locally,
      confirming the "leaning toward per-app JSX" call from 2026-08-22.
      Justified in hindsight: frontiers needed JSX wiring into *two*
      separate consumers (`AdminJobsPage` and `JobTabComponent`), and
      citelines' `JobsTable` needed a different Scope column entirely —
      variation a shared component built too early might not have
      anticipated. Phase 7, whenever started, now has five real apps'
      worth of actual drift to design from instead of generalizing off
      of one.

Update the checklist above as phases complete.

## Conventions

- Mirror the org's app-repo conventions: Spring Boot 3.x, google-java-format
  via git-code-format-maven-plugin (the shared format workflow requires that
  plugin, not spotless), jacoco 100% + pitest gates, prettier/eslint on
  frontend.
- CI = thin callers into `ucsb-cs156/workflows@main` (same numbering as the
  app repos); gh-pages docs site built from `frontend/docs-index/` (workflow
  04 hardcodes that path — do not move it to the repo root). Tests run against
  the shipped Liquibase changelog (`db/migration/lib-jobs/`) with
  `ddl-auto=validate`, so entity/changelog drift fails `mvn test`.
- Reference implementations to compare against live in the sibling checkouts,
  e.g. `~/github/ucsb-cs156/proj-courses` (and the other app repos on GitHub
  under ucsb-cs156). The most evolved backend core to seed from is
  proj-frontiers' `services/jobs/` (see DESIGN.md Appendix A).
- JitPack requires the Maven build at the repo root; keep `frontend/` for npm.
- Releases: tag `vX.Y.Z`; JitPack builds Maven on demand; GitHub Actions
  publishes npm on tag.
