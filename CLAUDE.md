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
      instead of continuing to run for the rest of the walk. **Next up:
      citelines** (back to the front of the queue now that scaffold, moved
      ahead of it, is done — not yet started).

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
- [ ] Phase 7: frontend package in `frontend/`. On hold until the v0.3.x
      backend rollout finishes (citelines, frontiers, dining, courses).

      **Idea for a starting point (Phill, 2026-08-22):** scaffold has a
      frontend for individual, project-scoped job queues (`JobTabComponent`)
      but no admin-facing view of jobs *globally* across projects — a real
      gap, and a natural first component to build for the shared package.
      Worth checking whether the other apps have (or lack) the same gap
      before designing it, same as the backend drift survey did in phase 0.

      **Open question, not yet decided:** whether to build the Cancel
      button as a shared component *before* finishing the v0.3.x rollout to
      the remaining apps (citelines, frontiers, dining, courses), so each
      app's rollout PR could consume it instead of reimplementing similar
      JSX locally each time — versus continuing the current per-app JSX
      pattern and tracking frontend differences across apps as they're
      encountered, to give Phase 7 the same grounded drift-survey basis the
      backend library had (phase 0), rather than generalizing prematurely
      off of only scaffold's shape. Leaning toward the latter: publishing
      an npm package is a heavier, slower-to-iterate commitment than local
      JSX, and today's backend work needed three same-day point releases
      driven by things only live QA testing surfaced — a similar
      fast-iteration need seems likely on the frontend side too, better
      absorbed locally per-app first.

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
