# Implementation Plan — WoL Gateway

**Status:** Checkpoint-gated plan
**Tanggal:** 2026-10-26
**Rule:** satu checkpoint aktif; berhenti setelah report; commit hanya setelah pengguna menyatakan `passed`.

## 1. Repository baseline

- Existing single Android module `:app`.
- Package/application ID `com.yihs.wolgateway`.
- Existing Compose template only.
- AGP `9.4.1`, Gradle `9.8.0`, Kotlin `2.2.10`, Compose BOM `2026.02.01`.
- compile/target SDK `37`, min SDK `29` (Android 10).
- Existing dependency set lacks Hilt, KSP, Ktor, Room, DataStore, and secure storage.
- Existing staged/unstaged `.idea` changes are user work and out of scope.

## 2. Version policy

Dependency versions must be checked for compatibility before CP1 changes. Existing versions are preserved unless a technical reason is documented. Candidate additions require:

1. Kotlin/AGP/Gradle compatibility check.
2. compile/target/min SDK compatibility check.
3. Android runtime suitability check.
4. Test/build verification.
5. Version pin in `gradle/libs.versions.toml`.

Do not add a new library when JDK/Kotlin/Android platform APIs or an installed dependency solves the requirement.

## 3. Checkpoint matrix

| ID | Deliverable | Acceptance gate | Primary tests |
|---|---|---|---|
| CP0 | Planning docs, ignore rules, report | Docs review | Gradle baseline, Git checks |
| CP1 | Build/DI/API/database foundation | Build + DI works | assemble, unit, lint |
| CP2 | Domain/persistence | CRUD valid | validation, Room, repository |
| CP3 | Device UI | CRUD UX works | Compose UI/navigation |
| CP4 | Discovery | real-device discovery/manual fallback | unit + device |
| CP5 | WoL/status | real target behavior | packet + socket + device |
| CP6 | REST API | auth/API contract | Ktor integration |
| CP7 | Tunnel technical spike | feasibility proven | ARM64/end-to-end |
| CP8 | Tunnel management | state/retry/secret handling | state + process tests |
| CP9 | Background service | lifecycle on target devices | Android device matrix |
| CP10 | Root Mode | optional isolated fallback | rooted/non-root tests |
| CP11 | Integration/regression | final criteria or blockers approved | E2E/security/regression |

## 4. Checkpoint details

### CP0 — Audit & Planning

Scope: repository/Git audit, PRD, architecture, design, plan, risk register, report directory ignore rules.
Explicit exclusion: no application feature implementation.
Acceptance: four docs available and internally consistent.
Test: `:app:tasks --all`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `git diff --check`, ignore verification.

### CP1 — Foundation

Add only compatible foundation dependencies and configuration. Establish Application/DI, serialization, coroutine/testing baseline, Ktor lifecycle skeleton, Room/DataStore interfaces without product behavior.
Acceptance: debug build, unit tests, and DI smoke test pass.
Risk: KSP/AGP/Kotlin mismatch.

### CP2 — Domain & persistence

Implement Device model, statuses, WoL result, validation, Room schema/DAO/repository, and tests.
Acceptance: CRUD, invalid input, duplicate behavior, and Flow consistency pass.
No UI/network scan/API yet.

### CP3 — Device UI

Implement Dashboard, Detail, Add/Edit, delete confirmation, search, states, NavigationBar (`Devices`, `Discovery`, `Settings`), compact stacked device rows, and reusable components per `docs/DESIGN.md`. Dashboard auto-checks every 60 seconds while visible; manual check remains available; post-Wake checks run at 5/15/30 seconds.
Acceptance: UI CRUD works and accessibility/state behavior is tested.

### CP4 — Discovery

Implement standard non-root discovery with cancellation, timeout, dedupe, hostname best-effort, and manual fallback.
Acceptance: real Wi-Fi test or explicitly BLOCKED with evidence.

### CP5 — WoL & status

Implement magic packet, UDP send, configurable broadcast/port, combined ICMP/TCP checker abstraction, `UNKNOWN` semantics, polling limits, method/time/latency/reason metadata. Default Windows TCP check is port `3389`; per-device override remains supported.
Acceptance: byte-level and target tests; packet result separated from target status.

### CP6 — Ktor API

Implement versioned routes, DTOs, auth middleware, token management, validation/error envelope, repository integration, request limits/timeouts, and API docs.
Acceptance: API integration/auth tests pass. No tunnel yet.

### CP7 — Cloudflare Tunnel Technical Spike

Use `roufsweb/cloudflared-android` only as technical reference. Build the runtime only from pinned official Cloudflare/cloudflared source for ARM64 Android. Verify native loading, start/stop, DNS workaround, token redaction, local Ktor forwarding, network recovery, foreground lifecycle, and target SDK interaction. Local fixture must pass before public hostname/domain testing.
Acceptance: PASS/FAIL/BLOCKED report. Do not continue CP8 on unresolved FAIL.

### CP8 — Tunnel management

Implement adapter/controller, state machine, secure token storage, health monitoring, retry/backoff/jitter, token replacement, duplicate prevention, and cleanup based on CP7 result.
Acceptance: connect/disconnect/reconnect verified, including process crash and network loss.

### CP9 — Background service

Implement foreground service, notification, server/tunnel coordinator, network callbacks, boot best-effort, and battery/OEM diagnostics.
Acceptance: target Android versions/devices tested; limitations documented.

### CP10 — Root Mode

Implement last, only after Standard Mode is stable and CP10 scope is justified. Isolate root capability, consent, supervisor/diagnostics, neighbor-table adapter, and fallback.
Acceptance: Standard Mode unaffected. Missing rooted hardware means BLOCKED, not PASS.

### CP11 — Integration/regression

Run UI→domain→DB, API→domain→WoL, local tunnel→Ktor, then external tunnel→API, reconnect, background, security, UI consistency, documentation, and Git checks.
Acceptance: all criteria PASS or explicitly approved blockers.

## 5. Mandatory report format

Each checkpoint writes a local ignored report under `reports/checkpoints/` containing ID/name, purpose, completed work, files, decisions, actual build/test commands and results, acceptance statuses, unrun tests/reasons, known issues, risks, manual test recommendations, Git status, and commit hash if applicable.

Operational test artifacts belong under `reports/test-results/`. Reports must never contain tokens or credentials.

## 6. Git workflow

Before checkpoint: `git status`, identify user changes, define file scope. During checkpoint: no unrelated refactor, no automatic commit. After report: stop and request review. Only after exact user approval `passed`: re-run required checks, inspect unstaged/staged diff, secret scan, stage checkpoint files only, commit with Indonesian convention, verify hash, stop. Never push unless requested.

## 7. Stop conditions

Stop and ask when:

- Cloudflare runtime fails or license/provenance is unclear.
- Required real device/network/domain is unavailable.
- A dependency is incompatible with current SDK/toolchain.
- A security boundary requires a material architecture change.
- User changes scope or approves a blocker workaround.
