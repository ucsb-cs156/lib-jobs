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

      **Next up:** with frontiers done, the only lib-jobs-adjacent work not
      blocked on the happycows freeze is v0.3.0 (job cancellation,
      DESIGN.md §9, design-only so far) or phase 7 (frontend package). Not
      yet decided — check with Phill. Once the freeze lifts (~2026-09-15),
      happycows still needs its own v0.2.0 bump (deliberately last since
      it's mission-critical), likely paired with whatever v0.3.0 ships by
      then per the original plan.
- [ ] Phase 7: frontend package in `frontend/`

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
