# lib-jobs

Shared async jobs system (Maven Spring Boot starter + npm components package)
for the ucsb-cs156 course projects. Extracted from the homegrown jobs system
duplicated across proj-courses, proj-frontiers, proj-scaffold, proj-happycows;
to be installed fresh into proj-dining.

## Read this first

**`docs/DESIGN.md` is the source of truth** for the architecture, the drift
survey of the five app repos, all design decisions (User decoupling via
`JobUserProvider`, frontiers `Course` decoupling via `metadata` column,
auto-configuration contents), the publishing setup (JitPack for Maven,
npmjs for `@ucsb-cs156/jobs-components`), the phased rollout plan, and the
open questions awaiting Phill's decisions (§7). Do not re-derive any of that;
start from the doc and update it when decisions change.

## Current status

- [x] Phase 0 (partially): design doc drafted, awaiting Phill's review of §7 open questions
- [ ] Phase 1: build backend library here (root `pom.xml`, package `edu.ucsb.cs156.jobs`), full jacoco+pitest coverage, tag v0.1.0
- [ ] Phase 2: migration pilot in proj-scaffold
- [ ] Phase 3: installation pilot in proj-dining (backend)
- [ ] Phases 4–6: proj-courses, proj-happycows, proj-frontiers (frontiers last — Course coupling)
- [ ] Phase 7: frontend package in `frontend/`

Update the checklist above as phases complete.

## Conventions

- Mirror the org's app-repo conventions: Spring Boot 3.x, google-java-format
  (spotless), jacoco 100% + pitest gates, prettier/eslint on frontend.
- Reference implementations to compare against live in the sibling checkouts,
  e.g. `~/github/ucsb-cs156/proj-courses` (and the other app repos on GitHub
  under ucsb-cs156). The most evolved backend core to seed from is
  proj-frontiers' `services/jobs/` (see DESIGN.md Appendix A).
- JitPack requires the Maven build at the repo root; keep `frontend/` for npm.
- Releases: tag `vX.Y.Z`; JitPack builds Maven on demand; GitHub Actions
  publishes npm on tag.
