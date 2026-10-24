# Architecture Rules & Code Quality Standards

## 1. Pure Domain State Machine Pattern
* **Rule**: Never put complex lifecycle, debounce, trigger-filtering, or decision-making logic inside Android framework classes (`Activity`, `Service`, `WallpaperService.Engine`, `BroadcastReceiver`).
* **Implementation**: Decouple logic into pure Kotlin classes with zero Android imports (such as `WallpaperCycleCoordinator`).
* **Advantage**: Pure Kotlin state machines can be tested on the JVM in milliseconds with 100% branch coverage using virtual time (`kotlinx.coroutines.test`).

## 2. Humble Object Pattern for Android Framework Components
* **Rule**: `WallpaperService`, `BroadcastReceiver`, `Worker`, and `Activity` must be thin adapters ("Humble Objects").
* **Responsibilities of Humble Objects**:
  1. Capture OS events (e.g. `ACTION_USER_PRESENT`, `onVisibilityChanged`, `onTouchEvent`).
  2. Forward them to the pure coordinator / use case / ViewModel.
  3. Execute platform side effects (e.g. canvas rendering, scheduling alarms, displaying toasts).
* **Anti-Pattern**: Do NOT track 10+ mutable state flags (`isCycling`, `lastUnlockTime`, `targetId`) across disparate Android callbacks in the Service.

## 3. Unidirectional Data Flow (UDF / MVI) in ViewModels
* **Rule**: ViewModels must expose clear, immutable `StateFlow`s and handle explicit user intents/actions.
* **Cold Flow Testing**: When exposing `StateFlow` created via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(...))`, remember that tests must actively collect the flow (or use `subscribeAll` with `backgroundScope.launch`) for upstream flows to produce values.
* **Coroutines Dispatcher Injection**: Never hardcode `Dispatchers.IO` directly into classes without allowing a test dispatcher to be injected or overridden.

## 4. Single Source of Truth for Framing & Customization
* **Rule**: Image framing (`cropX`, `cropY`, `zoom`) is keyed by image URI (`imageUri`) in `wallpaper_customizations` table and synced to `photos` table.
* When editing preview framing or changing active photo, mutations must persist to Room DB and broadcast cleanly via repository flows without racing against periodic cycling.
