# Verification Checklist for Future Implementations

Before submitting any code changes, closing a task, or claiming work is complete, every agent must execute and pass the following checklist:

### 1. Compile Verification
```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat compileReleaseKotlin
```
* **Success Criteria**: Zero Kotlin compilation errors. No unresolved references or syntax issues.

### 2. Full Test Suite Execution
```powershell
.\gradlew.bat testDebugUnitTest
```
* **Success Criteria**: ALL unit tests, Robolectric tests, Room DAO in-memory tests, and Compose UI tests pass with 0 failures.

### 3. Verification of New Functionality
* If you added a new database query in a DAO:
  * You MUST add an in-memory SQLite test in `PhotoDaoTest`, `AlbumDaoTest`, or `WallpaperCustomizationDaoTest`.
* If you added a new rotation trigger or debounce rule:
  * You MUST add a test case in `WallpaperCycleCoordinatorTest`.
* If you modified ViewModel state flows or actions:
  * You MUST update or add test cases in `MainViewModelTest`.
* If you added or modified interactive UI elements in Compose:
  * You MUST add or update Compose test cases using `createComposeRule()`.

### 4. Build Packaging Check
```powershell
.\gradlew.bat assembleDebug
```
* **Success Criteria**: Build succeeds without packaging conflicts, AAPT errors, or manifest merger issues.
