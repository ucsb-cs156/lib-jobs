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
- [ ] Phase 1: build backend library here (root `pom.xml`, package `edu.ucsb.cs156.jobs`), full jacoco+pitest coverage, tag v0.1.0
      — code complete 2026-07-12 (45 tests, jacoco 100%, pitest 100% w/ autoconfig excluded); awaiting Phill's review, commit, and tag
- [ ] Phase 2: installation pilot in proj-dining (backend)
- [ ] Phase 3: migration pilot in proj-scaffold
- [ ] Phases 4–6: proj-courses, proj-happycows, proj-frontiers (frontiers last — scope migration)
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
