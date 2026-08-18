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
- [ ] Phase 5: proj-happycows — code complete 2026-07-13, PR
      ucsb-cs156/proj-happycows#270 (open). Backend 283 tests / jacoco 100% /
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
      Course→scope migration). Phase 5 (happycows PR #270) and phase 6
      (frontiers) below stay open but are on hold until this sequence reaches
      them.

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

      **Next up: courses' actual v0.2.0 bump.** Same recipe as scaffold —
      bump the `lib-jobs` dependency, add the stage/complete backfill
      changeset pair, include only `002-job-logs-table.json` (courses now
      owns its `jobs` table via its own guarded changeset, `009-create-
      jobs-table.json`, same shape as scaffold's situation) — plus courses'
      own analog of the `jobsByCourse` bug scaffold hit: grep for any
      app-owned endpoint returning raw `Job` entities without an explicit
      `getJobLogPreview`/`getJobLogs` call, since that bug class will
      reproduce anywhere `Job.log`'s column-to-`@Transient` change isn't
      accounted for. Not yet started.

      **New app added to the rollout (Phill, 2026-08-16):
      `ucsb-cs156/proj-citelines`** — not one of the original five forks;
      built as a lib-jobs consumer from day one (pom.xml already on v0.1.5,
      no homegrown jobs code to retrofit), but still needs the v0.2.0 bump +
      backfill (still has the old single `log` column, real job history from
      `GetCitationsJob`/`CheckLinksJob`/etc.). Slot it into the rollout
      sequence — exact position TBD — and fold it into DESIGN.md §8's
      rollout list next time that doc is updated. Two follow-up passes
      confirmed for citelines specifically (and eventually every app): v0.3.0
      job interruptability, then factoring frontend jobs components into the
      `frontend/` npm package (phase 7).

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
- [ ] Phase 6: proj-frontiers (last — needs the Course→scope migration)
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
