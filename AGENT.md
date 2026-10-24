# Agent Guidelines: Android Background Switcher

Welcome! This codebase enforces strict architectural separation and a 5-tier testing pyramid to prevent runtime regressions, race conditions, and silent test false-positives.

Whenever working on this project, follow the **Incremental Discovery** links below based on the subsystem you are touching.

---

## 🧭 Incremental Discovery Index

| If you are working on... | Read this first | Key Concepts to Know |
| :--- | :--- | :--- |
| **Business logic, state machines, or ViewModels** | [`docs/agent/architecture-rules.md`](docs/agent/architecture-rules.md) | Pure Kotlin state machines, Humble Object pattern, UDF, Dispatcher injection |
| **Writing, fixing, or reviewing tests** | [`docs/agent/testing-pyramid.md`](docs/agent/testing-pyramid.md) | 5-tier pyramid, avoid "mock-everything", in-memory SQLite Room tests, Robolectric, Compose UI tests |
| **Live Wallpaper, screen unlock, or cycling timers** | [`docs/agent/wallpaper-engine.md`](docs/agent/wallpaper-engine.md) | `WallpaperCycleCoordinator` single decision point, surface visibility, canvas black clear |
| **Finishing a task, pre-commit, or making changes** | [`docs/agent/verification-checklist.md`](docs/agent/verification-checklist.md) | Mandatory Gradle verification commands and pass criteria |

---

## ⚡ Core Tenets (Non-Negotiable)

1. **Pure Kotlin State Machines over Android God Classes**:
   * Never put complex debouncing, trigger evaluation, or transition state tracking directly inside `WallpaperService.Engine`, `BroadcastReceiver`, or `Activity`.
   * All state transitions and trigger decisions belong in pure, testable classes like `WallpaperCycleCoordinator`.

2. **No "Mock-Everything" Tests**:
   * Do not write tests where every single dependency is `mockk(relaxed = true)`. Such tests only verify that mock methods were called; they do not prove that the code actually works.
   * Test Room DAOs using real in-memory SQLite (`Room.inMemoryDatabaseBuilder`).
   * Test Android components (`BroadcastReceiver`, `Worker`, Compose UI) using Robolectric and `createComposeRule()`.

3. **Active StateFlow Collection in ViewModel Tests**:
   * `StateFlow`s using `SharingStarted.WhileSubscribed` are cold in unit tests without an active collector.
   * Always collect flows in tests (e.g. via `subscribeAll` with `backgroundScope.launch` or Turbine `test {}`).

4. **Always Run the Verification Checklist**:
   * Never claim a task or feature is complete without running:
     ```powershell
     .\gradlew.bat testDebugUnitTest
     .\gradlew.bat assembleDebug
     ```
   * Both commands must pass with **0 failures and 0 errors**.
