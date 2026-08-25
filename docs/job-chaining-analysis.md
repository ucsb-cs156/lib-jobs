# Job chaining: transaction-safety analysis and proposed fix

Written 2026-08-25 by a Claude Code session working in `proj-citelines` on
issue [#110](https://github.com/ucsb-cs156/proj-citelines/issues/110), for
whichever session/human next touches `lib-jobs` or does citelines' v0.3.x
bump — flagged by Phill as worth surfacing now, before citelines builds
app-level chaining logic on top of an unreviewed assumption. Not a request
to block anything; a heads-up plus a recommended fix.

## 1. The pattern in question

Issue #110 asks citelines to auto-launch a follow-up job when another job
finishes — e.g. after "Get References" completes, automatically launch an
"Improve BibTeX Entries" pass scoped to the references it just fetched.
Mechanically this is trivial and not lib-jobs-specific: call
`jobService.runAsJob(childJob)` as the last statement of the parent job's
own `accept(JobContext ctx)`. **No app repo does this today** — grepping
citelines confirms `runAsJob` is currently only ever called from a
controller's launch endpoint, never from inside a running job.

## 2. The problem: a caller-transaction dependency in `runAsJob`

Traced against `JobService.java` on `main` (`ae778c6`, same class shape
since v0.3.0 — this is **not** fixed by the v0.3.2 bump citelines is about
to do):

```java
public Job runAsJob(JobContextConsumer jobFunction) {
  Job job = Job.builder()....status("queued")....build();
  jobsRepository.save(job);          // (A)
  self.runJobAsync(job, jobFunction); // (B) — @Async, returns immediately
  return job;
}

@Async("jobsExecutor")
public void runJobAsync(Job job, JobContextConsumer jobFunction) {
  Job current = jobsRepository.findById(job.getId()).orElse(job); // (C)
  ...
  job.setStatus("running");
  jobsRepository.save(job);          // (D) — "outside the wrapping transaction"
  ...
  transactionTemplate.executeWithoutResult(status -> {
    jobFunction.accept(context);     // (E) — the job body runs *inside* this transaction
  });
  ...
  job.setStatus("complete");         // or error/cancelled
  jobsRepository.save(job);          // (F)
}
```

When a **parent job's own `accept()`** calls `runAsJob()` to launch a
child (i.e. (E) contains a call back into (A)):

- Step (A) for the *child* job — `jobsRepository.save(child)` — executes on
  the parent's worker thread, which is currently inside the parent's own
  `transactionTemplate.executeWithoutResult(...)` block (E). Spring's
  default transaction propagation (`REQUIRED`) means this save **joins the
  parent's already-open transaction** rather than committing on its own.
  So the child's initial `queued` row is not durably visible to any other
  connection until the *parent's* transaction commits — which doesn't
  happen until the parent's own `accept()` call returns entirely.
- Step (B) for the child — `self.runJobAsync(child, ...)` — is an `@Async`
  call. Spring submits it to `jobsExecutor` and **returns immediately**,
  without waiting for step (A)'s transaction to commit.

**With the default single-threaded `jobsExecutor`, this is harmless**: no
other thread exists to pick up the child's task until the parent's worker
thread finishes running the parent's `runJobAsync` entirely (including the
transaction commit), so by the time the child task is actually dequeued,
its `queued` row is guaranteed committed. This is the only reason chaining
"just works" today, and it's an accident of the executor's default pool
size, not something `runAsJob`/`runJobAsync` guarantee.

**If `jobsExecutor` ever ran more than one thread** (`app.jobs.max-pool-size
> 1`, already exposed as a config property, not something apps are
currently told is unsafe to raise), the race is real: a second thread could
dequeue and start the child's `runJobAsync` — including step (D)'s
`jobsRepository.save(job)` — *before* the parent's transaction (which holds
the child's own uncommitted `INSERT`) commits. Concretely:

- Step (D) issues (via Hibernate) an `UPDATE jobs SET status='running' ...
  WHERE id = ?` against a row id that, from a second database connection's
  MVCC snapshot, doesn't exist yet. Postgres doesn't block on this (there's
  no existing committed row to lock) — it simply matches 0 rows. No
  exception is thrown; Hibernate doesn't treat "0 rows updated" as an
  error here.
- The child's actual work still runs (the in-memory `job`/`context`
  objects are valid regardless of DB visibility), and its log lines get
  written — though `JobLog` inserts referencing the not-yet-visible
  `job_id` may themselves block on the FK constraint until the parent
  commits, which is a *separate* subtlety worth empirical verification,
  not just reasoning about.
- Every subsequent status write for the child (`running` → `complete`/
  `error`/`cancelled`) is the same kind of no-op `UPDATE`, **until** the
  parent's transaction eventually commits and the child's `queued` row
  becomes visible — at which point nothing ever updates it again, because
  all the updates that were supposed to move it forward already ran (and
  silently no-op'd) in the past.
- **Net effect: the child job's actual work completes normally, but its
  `Job` row is stuck at `queued` forever** — a confusing "phantom" entry in
  the Jobs UI that never shows as running or complete, indistinguishable
  from a job that's been sitting in the queue this whole time.

This exact mechanism (concurrent `UPDATE` racing an uncommitted `INSERT`
from a different transaction) hasn't been empirically reproduced against a
live Postgres instance as part of this analysis — the reasoning above is
sound MVCC behavior, but worth a real integration test rather than taking
on faith before relying on it.

## 3. Options

**Option A — do nothing at the lib-jobs level; document the dependency in
each consuming app.** What citelines' issue #110 plan currently does: a
code comment at each chaining call site noting the single-thread-FIFO
dependency. Cheapest, but every app that adopts chaining has to
independently rediscover and re-document this, and it's an easy thing for
a future PR to invalidate silently by bumping `max-pool-size` for
unrelated throughput reasons.

**Option B — app-level fix: fire the child job from a
`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
instead of a direct call.** Genuinely thread-count-independent — the
listener only fires once the parent's transaction has actually committed.
Doesn't require any lib-jobs change. Downsides: every chaining call site in
every app needs its own event class + listener + `ApplicationEventPublisher`
wiring, which is more boilerplate than the one-line call the pattern
otherwise is, and it's easy to get subtly wrong (e.g. publishing the event
from the wrong place, or a listener registered on a bean that isn't
actually inside the transactional scope).

**Option C (recommended) — lib-jobs-level fix: make the initial `queued`
row's save transactionally independent of the caller.** Wrap just step (A)
in `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` (a second,
differently-configured `TransactionTemplate` bean, or
`transactionTemplate.execute(...)` with propagation set per-call), so it
always commits — regardless of what transaction, if any, is open on the
calling thread — before `self.runJobAsync()` is invoked in step (B). This
is a small, self-contained, backward-compatible change confined to
`runAsJob`; nothing about `runJobAsync`'s own transaction handling needs to
change. It makes `runAsJob` safe to call from anywhere, including from
inside another job's `accept()`, **regardless of `jobsExecutor`'s thread
count** — turning chaining from an undocumented accident of the default
config into an actually-supported capability, with no app-level workaround
needed at all.

**Option D (optional, larger scope) — first-class chaining support.** E.g.
a `Job.parentJobId` column for UI traceability ("this job was launched by
job #42"), or a dedicated `JobContext.chainJob(JobContextConsumer)` method
that wraps Option C's fix plus records the parent link. Not required to
unblock citelines; worth considering only if chaining turns out to be a
recurring pattern across apps once citelines ships it.

**Recommendation:** Option C, as a small, low-risk addition to whatever
version comes after v0.3.2 (or folded into the citelines v0.3.x bump PR
directly if that's more convenient than a separate lib-jobs release).
Citelines' own app-level code then needs zero special-casing — no event
listeners, no pool-size caveats in comments — just a plain call to
`jobService.runAsJob(childJob)` at the end of `accept()`, same as the
pattern already used elsewhere for direct (non-chained) job launches.

Also worth adding regardless of which option is chosen: **lib-jobs
currently has zero test coverage for calling `runAsJob` from within another
job's own `accept()`.** Every existing test treats `runAsJob` as called
from outside any job (i.e. from a controller). An integration test that
exercises real chaining — ideally against real Postgres/Testcontainers
rather than mocks, so the MVCC behavior in §2 is actually verified rather
than assumed — would be valuable independent of which fix option is taken.

## 4. Current citelines-side state (coordination note)

As of this writing:

- PR [#119](https://github.com/ucsb-cs156/proj-citelines/pull/119) (open,
  rebased onto latest `main`, all checks green) is **part 1 of 2** for
  issue #110: renames `BibTexEntryUpgradeJob`/`BibTexEntryUpgradeService` →
  `BibTexEntryImproveJob`/`BibTexEntryImproveService`, restructures
  `upgradeEntries`/`upgradeEntry` into a scope-aware
  `improveEntries`/`improveEntry` (project/entry/references/citations),
  renames the `/api/jobs/launch/upgradeBibTexEntries` endpoint, and touches
  `JobsController.java` and `JobsTabComponent.jsx`. It does **not** yet add
  any chaining — that's part 2, not yet started, intentionally paused
  pending this analysis.
- PR [#120](https://github.com/ucsb-cs156/proj-citelines/pull/120) (merged
  into `main`) is an unrelated bug fix (BibTeX quote-escaping on
  export/re-import) with no overlap with the above.
- A separate session (`lib-jobs-2c`) is concurrently doing citelines' v0.2.0
  → v0.3.2 bump, including `ctx.checkCancellation()` checkpoints in
  `CheckLinksService` and (by its own description) "`BibTexEntryUpgradeService`'s
  loops" — **that class is renamed to `BibTexEntryImproveService` in PR
  #119**, with its loop now living in `improveEntries`/`improveEntry`
  rather than `upgradeEntries`/`upgradeEntry`. If that v0.3.x bump PR is
  branched from `main` before #119 merges, it will conflict; if branched
  after, the checkpoint work should target the new class/method names.
  Flagged directly to that session as well.

Part 2 of issue #110 (the actual chaining work this document is about) is
on hold in the citelines session pending a decision here: proceed with
Option A/B as an app-level-only workaround, or wait for/pair with an
Option C fix in lib-jobs first.
