# Testing Pyramid & Anti-Patterns to Avoid

## 1. The 5-Tier Testing Pyramid

```
                       / \
                      /   \
                     /  5  \    <-- Tier 5: Verification Guardrails (assembleDebug, testDebugUnitTest)
                    /-------\
                   /    4    \   <-- Tier 4: Compose UI Tests on JVM (Compose Test Rule + Robolectric)
                  /-----------\
                 /      3      \  <-- Tier 3: Android Component Tests (Robolectric: Receivers, Workers)
                /---------------\
               /        2        \ <-- Tier 2: Real SQLite Integration Tests (Room in-memory DAOs)
              /-------------------\
             /          1          \ <-- Tier 1: Pure JVM Unit Tests (Coordinator, ViewModel, Crop Math)
            /-----------------------\
```

### Tier 1: Pure JVM Unit Tests
* **Target**: State machines (`WallpaperCycleCoordinator`), ViewModels (`MainViewModel`), mathematical engines (`CropCalculation`), parsers (`GooglePhotosShareParser`).
* **Tooling**: JUnit 4, Truth, Turbine, `kotlinx.coroutines.test.runTest`.
* **Requirement**: Test edge cases, debounce timing, virtual time advancement, and error branches.

### Tier 2: In-Memory SQLite Room DAO Tests
* **Target**: `PhotoDao`, `AlbumDao`, `WallpaperCustomizationDao`.
* **Tooling**: `Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()`.
* **Requirement**: Never mock Room DAOs when verifying data queries! Test actual SQLite queries (e.g. joins, ordering by `RANDOM()` or `dateAdded`, subqueries like `deleteOrphanedCustomizations`).

### Tier 3: Android Component Tests (Robolectric)
* **Target**: `UserPresentReceiver`, `BootCompletedReceiver`, WorkManager workers.
* **Tooling**: `@RunWith(RobolectricTestRunner::class)`.
* **Requirement**: Verify Android broadcast processing and background scheduling without physical emulators.

### Tier 4: Compose UI Tests on JVM
* **Target**: `PreviewScreen`, `SettingsScreen`, `AlbumSelectionScreen`.
* **Tooling**: `createComposeRule()`, Robolectric.
* **Requirement**: Verify UI state rendering (badges, counters, button visibility) and user interactions (clicks, toggles) run in seconds on JVM.

### Tier 5: Build & Verification Guardrails
* **Execution**: `./gradlew.bat testDebugUnitTest` and `./gradlew.bat assembleDebug`.
* **Requirement**: 0 test failures and 0 compilation warnings/errors before declaring any task complete.

---

## 2. Anti-Patterns to Avoid

| Anti-Pattern | Why It Fails | What to Do Instead |
| :--- | :--- | :--- |
| **"Mock-Everything"** | Mocking all DAOs, Context, and Preferences tests only that functions were called, not that the software works. | Test pure logic directly; test DAOs with real in-memory SQLite. |
| **Silent Default Values** | `unitTests.isReturnDefaultValues = true` returns `null`/`0` for unmocked framework methods, hiding crashes. | Run Android-dependent tests with Robolectric so the real framework is simulated. |
| **Cold StateFlow Inactivity** | `WhileSubscribed` flows do not emit values in tests if no observer is collecting them. | Use `subscribeAll` with `backgroundScope.launch` in test setups. |
| **Static State Leaks** | Companion object properties (like `lastUnlockTime`) retain state across tests in the same JVM runner. | Always reset static fields in `@Before` or encapsulate state in instances. |
