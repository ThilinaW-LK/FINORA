# Thilina 5 — QA: roles, security, AI correctness, SMS/usability/performance (T52–T55)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before starting.

---

### T52 · Three-role authorization tests · `ROLE-CR`
**Lead:** Thilina · **Support:** Sevindu · **Depends on:** T06, T41, T46 · **Touches:** `api-test:security/`
- [ ] T52.1 Matrix test: every endpoint × {none, USER, other USER, STAFF, ADMIN, disabled} → expected status
- [ ] T52.2 ID-guessing tests on every `{id}` endpoint
- [ ] T52.3 Confirm Admin/Staff responses never include amounts or transactions

**Done when**
- [ ] Matrix test green in CI

**Handoff notes:**
- 2026-11-24 reconciliation: PR #10 retains FinancialResponsePrivacyTest, four historical focused passes, 19 read and 23 write mappings per privileged role, and unchanged financial rows. Prior combined verify 261/261. T41/T46 remain acceptance dependencies.
- 2026-11-21 Thilina: Added disabled-account JWT checks for USER, STAFF and ADMIN across discovered protected endpoints. EndpointAuthTest: 6/6 PASS, 0 failures/errors/skips, Java 17 and MySQL 8. Full role matrix, ownership tests, privacy checks and CI confirmation remain pending.
- 2026-11-22 audit: current T53 verify includes EndpointAuthTest and RoleAccessTest; existing discovery covers implemented endpoints, but does not assert exact error-field privacy. Highest-priority independent follow-up: T52.3 response-field privacy assertions on existing finance endpoints, on a separate T52 branch. T41/T46 and CI acceptance remain blocked; no new T52 tests or completion claimed here.
- MD05 D45/D46 added focused T46.1 access and response-shape checks. Full T52 matrix, privacy review and CI remain open.
- D49 MySQL checks cover STAFF revocation, ticket ownership and claim contention, diagnostic roles and ADMIN format mutation. The full matrix and independent review remain open.
- V18 adds ADMIN count schema and role tests, STAFF/USER format mutation denial and approved rollback. Android loopback capture retained only the exact-match verdict for diagnostic metadata.
- D53 adds targeted ADMIN user-management and default-category checks, USER export/deletion isolation and old-token rejection. Complete T52's full per-endpoint matrix and review on the final revision.
- Execution 29 preserves upstream denials and reconciles concrete contribution IDs, MD05 ownership and restricted consent tests. See [route coverage](../../docs/t52-route-evidence.md). Positive endpoint matrix and independent CI remain open.

---

### T53 · Privacy and security checks · `NFR-SEC`
**Lead:** Thilina · **Support:** Sevindu · **Depends on:** T14, T27 · **Touches:** `docs/security-checklist.md`
- [ ] T53.1 Verify HTTPS, BCrypt hashes, token expiry, redacted logs
  - D56 schedules the trusted release HTTPS evidence during T56 deployment. T53 retains its own acceptance and independent review.
- [ ] T53.2 Capture app traffic (proxy) during SMS import: no SMS body; capture LLM request: no forbidden fields
- [ ] T53.3 Run OWASP dependency-check + secret scan (e.g. gitleaks); fix high findings

**Done when**
- [ ] `docs/security-checklist.md` filled with real results

**Handoff notes:**
- 2026-11-22: Initial static security audit recorded in docs/security-checklist.md. BCrypt strength 12, password encoding/matching and JWT expiry/signature verification located. HTTPS deployment, log redaction, live SMS/AI privacy, comprehensive secret scanning and dependency scanning remain unverified. T53 in-progress.
- 2026-11-22 recovery: fixed pre-existing expiry fixture using database UTC and EntityManager.clear; clearing managed state alone still failed. Preserved 401 INVALID_REFRESH_TOKEN; AuthService unchanged. Added disabled-refresh/wrong-signature regressions and disabled auth MockMvc payload dumps.
- Unexpected-error logger now omits raw URLs and exception details; dedicated privacy regression added. Focused 17/17 passed; final offline Java 17 Maven verify: 151/151, zero failures/errors/skips, exit 0, isolated finora_test only (`api/target/t53-verify.log`).
- JaCoCo service gate PASS: 602/610 lines (98.69%); report api/target/site/jacoco/index.html. Limited tracked-content secret patterns found no matches; gitleaks/dependency-check not on PATH, so comprehensive scans remain unchecked.
- Recovered pipeline commits 72ec247/ed33c41 match fork branch; static inspection confirms mock-only tests, strict provisional verifier, bounded audit metadata and rule fallback. PRs #5/#6/#7 confirmed merged; no new PR/push, no live calls or policy changes.
- Final PR compatibility: preserved 8d38725, merged origin/main a0b5dfb; offline verify 206/206 passed, zero failures/errors/skips, exit 0; service lines 668/677 (98.67%), gate passed (api/target/final-t53-verify.log). Pipeline PR #8 open; next: reviewer acceptance, T14 capture, deployed TLS/proxy/log review and comprehensive scans. T53 remains in-progress.
- Execution 29 preserves the [classified local scan](../../docs/t53-local-security-evidence.md). Dependency reports remain blocked by feed TLS; D56 deployed HTTPS and runtime captures remain open.

- Execution 40: dedicated emulator NSB paste/inbox connected test passed. Six actual POSTs passed approved field and raw-marker checks, including one diagnostic and one byte-identical explicit retry. Three rows persisted solely under a disposable owner. See [wire evidence](../../docs/t53-android-sms-wire-evidence.md). Dependency remediation, OWASP, deployed HTTPS and independent review remain open.

---

### T54 · AI and financial correctness tests · `FR06–FR09`
**Lead:** Thilina · **Depends on:** T29 · **Touches:** `api-test:advice/`
- [ ] T54.1 Fixture set of user-months with hand-checked metrics and scores
- [ ] T54.2 Run real LLM calls on fixtures; record pass rate of number check
- [ ] T54.3 Confirm every failure fell back to rule advice

**Done when**
- [ ] Pass-rate report from actual runs saved

**Handoff notes:**
- 2026-11-24 reconciliation: PR #11 retains FinancialJourneyTest and six financial fixture oracles. Historical focused 8/8 and verify 265/265. Mocked evidence only. Live acceptance remains open.
- 2026-11-22 audit: recovered T27-T29 branch contains mocked client/verifier/fallback tests; historical 253-test result is branch-specific and was not rerun here. Real LLM pass-rate testing remains unauthorized; T29 acceptance blocks T54 completion. Do not report mocked results as real pass rates.
- D44 now authorizes a bounded synthetic live check for T30 once approved models, isolated credentials and a numeric call budget are available. The T54 pass-rate evaluation and its T29 prerequisite remain open.
- Execution 29 carries the [guarded evaluation](../../docs/t54-provider-evaluation.md): 6/6 oracles, one observed Gemini HTTP 503, canonical fallback, global stop and nine unexecuted candidates. Billing and live acceptance remain open.

---

### T55 · SMS, usability and performance checks · `NFR-SMS / NFR-USE / NFR-PERF`
**Lead:** Thilina · **Support:** Sahan · **Depends on:** T16, T38 · **Touches:** `docs/evaluation.md`, `docs/t55-dataset.md`, `api-test:support/EvaluationDataset*`
Independent dataset preparation explicitly authorized 2026-11-22: pure 12-month blueprint, guarded test-only seed,
four deterministic validation tests and contribution evidence. No production seed/startup hook or device-results claim.
- [x] T55.1 SMS field accuracy from T16 ≥ 90%
- [ ] T55.2 Seed one year of data; measure main screens < 2 s on a mid-range phone
- [ ] T55.3 SUS questionnaire with trial users; target ≥ 70
- [ ] T55.4 Beta advice ratings: compare RULE vs AI with a statistical test

**Done when**
- [ ] `docs/evaluation.md` contains real measured numbers

**Handoff notes:**
- 2026-11-24 reconciliation: PR #12 retains the 826-transaction fixture, guarded rollback-only seeding and historical 4/4 focused and 261/261 full verification. Dataset and contribution evidence remain in docs. Real measurements remain open.
- 2026-11-22 audit: T16/T38 remain todo; no device timing, SMS accuracy, SUS participants or ratings measurements available. T55 remains todo. Beta comparison wording also needs reconciliation with D03 V1-only scope before evaluation; no measurements invented.
- Later 2026-11-22: built two-user synthetic Dec 2025–Nov 2026 dataset: 826 transactions, 24 budgets, 2 bills/18 paid markers, 2 goals/18 contributions and 21 declarations; existing V6 categories only. Loopback finora_test transaction guard, identity-collision refusal, no existing-row updates/deletes; actual rollback checked.
- Focused 4/4 passed after correcting two hand-oracle arithmetic errors (initial 3 assertion failures). Database projection/totals, budget/goal consistency, ownership/control rows and refusal-before-write verified. Fresh full-suite/coverage evidence follows; no real bank/SMS/performance results collected.
- Contribution audit: representative GitHub commits map to ThilinaW-LK; one consistent configured author email (not disclosed), 25 authored commits across start-of-audit refs / 20 on upstream main. Pending fork branches and graph timing differ from authorship; no history or attribution rewritten. Evidence: docs/thilina-contribution-evidence.md.
- PRs #10/#11 open with API/Android CI success and no submitted reviews when checked. T30/T31/T32/T52–T55 owners, approvals and integration blockers are listed in docs/t55-dataset.md. T55 remains in-progress; full subtask/acceptance boxes stay unchecked.
- T55.2 Execution 37: guarded persistent load verified in dedicated loopback MySQL. Fresh read-back matched all 826 transactions and the canonical digest. Both synthetic users logged in and cross-owner goals returned 404. Failure rolled back and duplicate load refused. See [dataset operating evidence](../../docs/t55-dataset.md#persistent-evaluation-variant). Emulator and handset timing remain open.
- T55.1 reuses the [T16 result](../sahan/3-sms-quality.md#t16--sms-parser-regression-tests--nfr-sms): 12/12 fields (100.0%) on the three approved D35 NSB fixtures. T20 measured the HMD dashboard at 505/504/486 ms with synthetic data over 12 months. T55.2 still needs the full main-screen timing scope. SUS and the D03-compatible evaluation decision remain open, as do formal T16/T38 prerequisite closure and Thilina’s evaluation review.
- Execution 29 preserves [Execution 27 evidence and draft protocols](../../docs/evaluation.md): 19/19 V1 JVM tests, 12/12 D35 synthetic fields and 6/6 negative routes. Independent T55.1 review and T16/T38 remain open. Timing, usability and comparison remain unexecuted.
- Execution 38: restored and verified the loopback evaluation API. Both November dashboard oracles and cross-owner goal refusals passed, and the canonical dataset digest was preserved. The Android test-source frame probe is staged for connected validation. Emulator assignment, screen-level SMS capture and all timing cohorts remain open.
- Execution 39: dedicated API 37 AVD verified both synthetic profiles across the November read-only journey, plus a separate paste-diagnostic confirmation. Canonical data and digest were preserved. Approved NSB paste/inbox wire capture, corroborated emulator timing and handset acceptance remain open. See [evaluation evidence](../../docs/evaluation.md#execution-39-dedicated-emulator-journeys).
- Execution 41: 40 warm and five fresh-process Home submission diagnostics collected. One separate Home trace-check matched an actual displayed frame, while Records, Plan and Insights remained unqualified for presentation. See [raw evidence and limitations](../../docs/evaluation.md#execution-41-emulator-timing-pilot). Handset timing and formal acceptance remain open.
- Execution 42: eight qualified displayed frames across all four routes, two unqualified Plan attempts, five correlated HTTP probes and a separate external cold-launch trace. See [evaluation evidence](../../docs/evaluation.md#execution-42-displayed-frame-qualification-and-startup-investigation). T55.2 handset and formal acceptance remain open.
- Execution 44: offline handset manifest and sanitized timing-join checks passed. Actual screen-request capture and handset execution remain gated. See [qualification evidence](../../docs/evaluation.md#execution-44-performance-qualification).
