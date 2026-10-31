# Implementation Plan — WoL Gateway

**Status:** Checkpoint-gated plan; API Settings port/loopback hardening implemented
**Tanggal:** 2026-10-26
**Rule:** satu checkpoint aktif; berhenti setelah report; commit hanya setelah pengguna menyatakan `passed`.

## 1. Repository baseline

- Existing single Android module `:app`.
- Package/application ID `com.yihs.wolgateway`.
- Existing Compose template, now organized into feature-based navigation/screen/component files. The active screen tree is `devices`, `add_devices`, `device/detail`, `device/form`, and `settings`; obsolete screen folders are removed.
- AGP `9.4.1`, Gradle `9.6.0`, Kotlin `2.4.21`, Compose BOM `2026.09.00`.
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
| CP4 | Add Devices discovery | real-device discovery/manual fallback | unit + device |
| CP5 | WoL/status | real target behavior | packet + socket + device |
| CP6 | REST API | auth/API contract | Ktor integration |
| CP7 | Tunnel technical spike | feasibility proven | ARM64/end-to-end |
| CP8 | Tunnel management | state/retry/secret handling | state + process tests |
| CP9 | Background service | lifecycle on target devices | Android device matrix — PASS |
| CP10 | Root Mode | optional isolated fallback | rooted/non-root tests |
| CP11 | Integration/regression | final criteria or blockers approved | E2E/security/regression |
| CP12 | Diagnostics | copy-safe mode-aware troubleshooting page | redaction/unit/device checks |

## 4. Checkpoint details

### CP0 — Audit & Planning

Scope: repository/Git audit, PRD, architecture, design, plan, risk register, report directory ignore rules.
Explicit exclusion: no application feature implementation.
Acceptance: four docs available and internally consistent.
Test: `:app:tasks --all`, `:app:assembleDebug`, `:app:testDebugUnitTest`, `git diff --check`, ignore verification.

### CP1 — Foundation — executed

Add only compatible foundation dependencies and configuration. Establish `@HiltAndroidApp` Application, `@AndroidEntryPoint` Activity, `@HiltViewModel`/`hiltViewModel()` baseline, `AppModule` bindings, serialization, coroutine/testing baseline, Ktor lifecycle skeleton, Room/DataStore interfaces without product behavior.
Acceptance: debug build, unit tests, lint, and Hilt code generation pass. Detailed evidence: `reports/checkpoints/CP1-foundation.md`.
Decision: Hilt is required for DI. KSP is used for Hilt and Room code generation. Manual service locator is not used.
Compatibility note: Hilt `2.60.1` is pinned because earlier Hilt Gradle plugin versions rely on removed AGP 9 `BaseExtension`. Source structure follows one top-level class/component per file; `MainActivity` is lifecycle host only. `android.disallowKotlinSourceSets` is not required after the dependency/toolchain update and is absent from project properties.
Risk: KSP/AGP/Kotlin compatibility remains a build maintenance risk.

### CP2 — Domain & persistence

Implement Device model, statuses, WoL result, validation, Room schema/DAO/repository, and tests.
Acceptance: CRUD, invalid input, duplicate behavior, and Flow consistency pass.
No UI/network scan/API yet.

### CP3 — Device UI — executed

Implement Devices, device detail, device add/edit, delete confirmation, search, states, compact stacked device rows, and reusable components per `docs/DESIGN.md`. The device row opens Details on tap; no overflow menu is shown. Wake is hidden when the saved status is `ONLINE`; status check remains available from Device Details, pull-to-refresh, and automatic polling. Use feature-based Kotlin structure: `ui/screen/devices`, `ui/screen/device/detail`, `ui/screen/device/form`, `ui/screen/add_devices`, and `ui/screen/settings`. Folder/package names use `snake_case`; files, classes, objects, and interfaces use `PascalCase`; functions, properties, variables, and parameters use `camelCase`; constants use `SCREAMING_SNAKE_CASE`. Follow the BLScanner navigation pattern: `AppNavHost` owns the single `Scaffold`, derives `currentRoute` from `currentBackStackEntryAsState()`, and renders the top app bar; `NavGraph` only declares `NavHost` routes and route arguments; `Screen` owns route identity while localized titles are resolved from `strings.xml`. No bottom `NavigationBar`: Settings is reached from the Devices app-bar action; Add Devices is reached from the Devices FAB and empty-state add action; manual registration is reached from Add Devices. Screen composables render content only. Page titles belong to the app bar; content has no duplicate headings. Device Details, Settings, and forms must scroll when content exceeds the viewport; lists use `LazyColumn`. All user-facing labels, actions, content descriptions, validation copy, and snackbar messages belong in `app/src/main/res/values/strings.xml`. Devices auto-checks every 60 seconds while visible; manual check remains available; post-Wake checks run at 5/15/30 seconds.
Acceptance: UI CRUD works and accessibility/state behavior is tested.

Implemented screen ownership:

- `ui/screen/devices`: device list and `DevicesViewModel`.
- `ui/screen/add_devices`: local-network scan and manual-registration entry.
- `ui/screen/device/detail`: device detail page.
- `ui/screen/device/form`: add/edit device page.
- `ui/screen/settings`: gateway settings page.
- `ui/component`: reusable UI components shared by feature pages.
- `ui/navigation`: app shell, route model, and route declarations.

### CP4 — Add Devices discovery

Implement Add Devices automatic non-root discovery on first screen entry with cancellation, timeout, dedupe, hostname best-effort, reachable-host results, retained ViewModel state across form navigation, explicit pull-to-refresh/retry scan, tap-to-register results with explicit MAC-unavailable guidance, and manual fallback.
Registration behavior: filter discovery results by saved IP; manual registration may reuse an IP when MAC differs. New-device save returns to Devices with snackbar feedback; form input constraints and validation remain enforced at the UI and repository boundary.
Snackbar events use the app-shell `DevicesViewModel` shared with the form route; save feedback is not emitted from an unobserved route-local ViewModel.
Acceptance: real Wi-Fi device environment recorded; manual UI checklist remains user-run and must be marked PASS only after execution. If no device/network is available, mark BLOCKED with evidence.

### CP5 — WoL & status

Implement magic packet, UDP send, configurable broadcast/port, combined ICMP/TCP checker abstraction, `UNKNOWN` semantics, polling limits, method/time/latency/reason metadata. Default Windows TCP check is port `3389`; per-device override remains supported.
Acceptance: byte-level and target tests; packet result separated from target status.

### CP6 — Ktor API

**Status: PASS (user-approved); manual endpoint verification remains pending.** Implemented versioned routes, DTOs, auth middleware, Keystore-backed token management, validation/error envelope, repository/use-case integration, declared request-size limit, API docs, and dedicated Settings → API controls for fixed loopback URL/port/start/stop/token actions. API state is isolated in `ApiSettingsViewModel`/`ApiSettingsUiState`. Bind address is permanently `127.0.0.1`; only ports `1024..65535` are accepted. Ktor startup probes the requested loopback port and resolves connectors before reporting success, so permission/bind failures remain recoverable and do not crash the Android process. Token supports automatic generation, automatic refresh, or custom input; masked copy is available. Unsaved custom-mode drafts are discarded without replacing the saved automatic token. Token preview updates immediately after automatic refresh. Reveal and revoke are omitted. Fixed Android Keystore AES-GCM encryption by using the Cipher-generated IV rather than passing a caller-provided IV. Disabled Ktor `watchPaths` for the Android embedded server to prevent `ClosedWatchServiceException` during API server stop/start. API server toggle success is silent; snackbar feedback remains for configuration, token, validation, and server errors.
Acceptance: automated API integration/auth tests pass; user approved CP6 after source verification. Manual endpoint execution remains pending. No tunnel yet. Evidence: `reports/checkpoints/CP6-rest-api.md` and `docs/API.md`.

### CP7 — Cloudflare Tunnel Technical Spike

**Status: PASS (implementation and device validation user-approved).** `roufsweb/cloudflared-android` remains technical reference only. Official Cloudflare `cloudflared` source `2026.10.0` was checked out at commit `18cdfe0a6fc7b72a0702d255a1f984e776ce0498` and built with Go `1.26.0` using `GOOS=android GOARCH=arm64 CGO_ENABLED=0`. The resulting ARM64 ELF is packaged as `jniLibs/arm64-v8a/libcloudflared.so` and executed from Android `ApplicationInfo.nativeLibraryDir`; app cache/code cache is not used because it may be mounted `noexec`. Tunnel token is written to a temporary app-private token file and passed through `--token-file`; token output is redacted before drain. An app-private ingress config forwards to the configured local Ktor origin. CP7 covers the Android runtime implementation plus end-to-end device validation.

Automated gates pass: `:app:assembleDebug`, `:app:testDebugUnitTest`, source build, ARM64 ELF inspection, and token-redaction unit test. User-confirmed device validation on ARM64 Android 10 passes after the Android-compatible static IPv4 edge fallback; native loading, tunnel registration, forwarding, and lifecycle validation are accepted. Runtime readiness requires `Registered tunnel connection`, with a 20-second startup timeout and serialized lifecycle operations. Runtime diagnostics use Logcat tag `WolGatewayTunnel`; token values are never logged. Evidence: `reports/checkpoints/CP7-tunnel.md`.

Acceptance: `PASS`; CP7 implementation and Android ARM64 end-to-end validation accepted by the user. Do not treat the static edge list as a general-purpose production discovery mechanism outside this validated runtime scope.

### CP8 — Tunnel management

Implement adapter/controller, state machine, secure token storage, health monitoring, retry/backoff/jitter, token replacement, duplicate prevention, cleanup, and the tunnel management UI based on CP7 result. The tunnel screen uses separated Connection, conditional Auto reconnect, and Tunnel token sections with dividers. Fresh install shows the token form directly; no editable origin, manual reconnect action, or token copy exists. Tunnel origin follows the API Settings port at `127.0.0.1:<port>`. Connect remains disabled until a token is saved; token editing is locked during an active connection attempt or connection; connection errors occupy the Connection message slot. Devices dashboard consumes the shared controller Flow through `DevicesViewModel` and renders the live tunnel state in `GatewayStatusStrip`.
Acceptance: connect/disconnect/reconnect verified, including process crash and network loss.

### CP9 — Background service — PASS (user-approved)

Implemented Standard Mode foreground service, persistent notification, shared API/tunnel coordinator, default-network recovery callback, and best-effort `BOOT_COMPLETED` startup. Settings persist API-service and tunnel autostart switches. Tunnel autostart requires a saved token and starts the API service because the tunnel origin is loopback. Root mode is deliberately excluded; root startup remains CP10.
Acceptance: user-approved. Boot recovery, API-only/API+tunnel autostart, network recovery, process-death behavior, battery optimization guidance, Android 13 notification behavior, and OEM/device validation are PASS. Build/test commands were unavailable because Java Runtime was unavailable; user-approved as PASS. Evidence: `reports/checkpoints/CP9-background-service.md` and `reports/checkpoints/CP9-battery-guidance.md`.

### CP10 — Root Mode

Implement last, only after Standard Mode is stable and CP10 scope is justified. Isolate root capability, consent, supervisor/diagnostics, neighbor-table adapter, and fallback.
Acceptance: Standard Mode unaffected. Missing rooted hardware means BLOCKED, not PASS.

### CP11 — Integration/regression

Run UI→domain→DB, API→domain→WoL, local tunnel→Ktor, then external tunnel→API, reconnect, background, security, UI consistency, documentation, and Git checks.
Acceptance: all criteria PASS or explicitly approved blockers.

### CP12 — Diagnostics

Implement the final Settings → Diagnostics page as one scrollable, mode-aware page. Always show general diagnostics: app version, Android version/ABI, device classification (automatic test/emulator/real device), active network interface, API status, tunnel status, GatewayService status, and last safe errors. Show only `configured/not configured` or redacted values for secrets.

Show Standard Mode diagnostics when Standard Mode is active: service running state, API-only/API+tunnel mode, foreground-service and notification status, API/tunnel autostart preferences, and relevant battery/OEM or boot-recovery limitations. Show Root Mode diagnostics when Root Mode is active: root capability, user consent, privilege check, supervisor status, root-dependent feature availability, fallback status, and root-specific safe errors. Root capability may remain informational in Standard Mode; no arbitrary shell execution or raw privilege output.

Diagnostics is opened from a Settings row and uses one page with `General`, `Standard Mode`, or `Root Mode` sections rather than separate pages. Include an explicit copy action producing copy-safe, redacted output. Preserve distinctions between `pass`, `warn`, `fail`, and `not configured`; do not infer target-device status from gateway diagnostics.

Acceptance: no API/tunnel/root secret appears in UI, copied output, logs, notifications, reports, or screenshots; diagnostics distinguish automatic test, emulator, and real device; Standard Mode remains unaffected by Root Mode diagnostics; copy output is stable and redacted; scroll/font scaling/accessibility requirements pass. Validate with unit redaction tests, `:app:assembleDebug`, `:app:testDebugUnitTest`, and Standard/Root device checks where hardware exists. Missing rooted hardware is `BLOCKED`, not `PASS`.

## 5. Mandatory report format

Each checkpoint writes a local ignored report under `reports/checkpoints/` containing ID/name, purpose, completed work, files, decisions, actual build/test commands and results, acceptance statuses, unrun tests/reasons, known issues, risks, manual test recommendations, Git status, and commit hash if applicable.

Operational test artifacts belong under `reports/test-results/`. Reports must never contain tokens or credentials.

## 6. Git & Commit Workflow

### Before each checkpoint

- Run `git status`.
- Identify existing changes.
- Do not change files outside scope without a reason.
- Do not delete user changes.

### During a checkpoint

- Implement only the active checkpoint scope.
- Run relevant tests.
- Write the checkpoint report.
- Do not create an automatic commit.

### After the user states `passed`

1. Confirm acceptance criteria are met.
2. Check build and test results.
3. Check `git diff`.
4. Check `git diff --cached`.
5. Ensure no secrets or report files are staged.
6. Stage only files related to the checkpoint.
7. Create the commit.
8. Verify the commit succeeded.
9. Report the commit hash.
10. Stop and wait for further instructions.

If a required test fails, a secret is detected, or unrelated changes are unsafe, do not create an invalid commit. Explain the blocker and request a decision.

Do not push to the remote unless explicitly requested.

### Commit message convention

Required format:

```text
<type>: <short description in English>
```

Use the type that matches the actual change: `chore`, `feat`, `fix`, `test`, or `docs`. Do not reuse the same commit message for different work.

## 7. Stop conditions

Stop and ask when:

- Cloudflare runtime fails or license/provenance is unclear.
- Required real device/network/domain is unavailable.
- A dependency is incompatible with current SDK/toolchain.
- A security boundary requires a material architecture change.
- User changes scope or approves a blocker workaround.
