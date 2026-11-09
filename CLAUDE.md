# CLAUDE.md

CoreKit-Android is the non-visual base shared by the owner's Android apps (`~/Canetto-Android`, `~/ReciboJa-Android`,
`~/homer.app.android`). It exists because a method review (2026-11-09) found the same bugs in all three, each fixed by
hand per screen: double-tap saves, form state lost on Activity recreation and process death, "appeared" analytics
counted twice, Pro starting as `false`, the same screen pushed twice, and a navigation/billing/build base copied between
repos that had already diverged. A fix here reaches every app.

The visual twin is DSKit-Android (`~/DSKit-Android`): anything that draws (dialogs, fields, sheets) goes there, never
here. CoreKit does not depend on DSKit.

## Decisions (owner interview, 2026-11-09)

- **Repository**: public, `github.com/Romulomga/CoreKit-Android`, MIT. No secrets, no app-specific code or names.
- **Android only**: no iOS twin. The problems it solves are Android's (Activity recreation, process death, async saves);
  on iOS the reference for patterns stays MagicPick.
- **minSdk 30**, compileSdk 37, JDK 21. AGP/Kotlin must match DSKit-Android and the apps (an `includeBuild` is one build).
- **Modules**: `:core` (pure Kotlin), `:compose` (Android + Compose), `:testing` (fakes and rules). Pieces that pull a
  heavy SDK get their own module so an app takes only what it uses: `:billing` (Play Billing), `:firebase` (Analytics +
  Crashlytics), `:review` (Play In-App Review).
- **v0.1 scope**: the base pieces below, Play Billing (`ProStore`), the analytics sink with the "debug never collects"
  rule, the store review prompt, and the common ktlint script (apps apply `CoreKit-Android/ktlint.gradle` instead of
  keeping a copy). Gradle convention plugins were left out on purpose: the three app build files differ in SDKs and
  flavors more than they repeat.
- **Out of v0.1**: the Homer network layer (`ApiCaller`, retry, connectivity) until a second app has a backend.
- **Visual pieces** repeated in the apps (discard guard, delete confirmation, decimal field, stepper, date pills) go to
  DSKit-Android in the same round, with the iOS twin evaluated case by case.
- **No CI**: verification is local, before every tag.
- **Distribution**: git submodule pinned to a tag + `includeBuild("CoreKit-Android")`, like DSKit. Maven coordinates
  `com.github.Romulomga.CoreKit-Android:<module>` are substituted by the included build.

## What is here

| Piece | Module | Rule it enforces |
|---|---|---|
| `SingleFlight`, `KeyedSingleFlight` | core | An action that writes runs once at a time. Every save/send goes through one. |
| `Entitlement`, `EntitlementState` | core | Pro has an `UNKNOWN` state; no Pro gate or paywall impression before it resolves. |
| `AppDispatchers` | core | Dispatchers arrive by injection, never `Dispatchers.IO` inline. |
| `stateInScreen` | core | One `stateIn` policy for screen state. |
| `GeneratedFiles.purge` | core | Shared files written to the cache are collected at process start. |
| `SystemZoneClock` | core | One injected `Clock`; the zone always comes from it. |
| `Navigator`, `NavigationState` | compose | One stack per tab, saved; the key already on top is not pushed again. |
| `SavedDraft` (`SavedStateHandle.draft`) | compose | What the user typed lives in the ViewModel and survives process death. |
| `TrackOnce` | compose | "Appeared" events fire once per visit, not per Activity recreation. |
| `ObserveEvents` | compose | One-shot events are collected only while the screen is visible. |
| `MainDispatcherRule`, `FakeEntitlement` | testing | Shared test support instead of a copy per app. |
| `AnalyticsSink`, `CollectionPolicy` | core | Screens never see an analytics SDK; only store builds in real use collect. |
| `ReviewGate` | core | The review prompt is asked once per version (and, optionally, N days apart). |
| `ProStore`, `BillingGateway` | billing | Only a complete Play answer changes the right; the last confirmed one is kept; purchases are always acknowledged. |
| `FirebaseMonitoring`, `FirebaseAnalyticsSink` | firebase | The only place that knows the Firebase SDK. |
| `StoreReview` | review | Mark "asked" only after the Play call returned. |

What stays in each app around these: its typed events (names are product decisions), its plan ids, where the confirmed
right is stored (`EntitlementMemory` over the app's own keys), purchase analytics and anything trial-related
(`ProStoreObserver`), and WHEN to ask for a review.

## Conventions

- `explicitApi()` everywhere; KDoc in pt-BR explaining the *why* (the bug the piece prevents), like DSKit-Android.
- Nothing app-specific: no product ids, strings, event names or package names of an app.
- Every piece ships with a test that fails without it (double tap saves once, draft survives a new handle, recreation
  does not count again).
- Blank line between logical blocks. No comments that restate the code.
- A new piece enters only when at least two apps need it; otherwise it stays in the app.

## Verify

```sh
./gradlew assemble ktlint lintDebug :core:test testDebugUnitTest
```

`ktlintFormat` fixes style. Release: bump `VERSION_NAME` in `gradle.properties`, update `CHANGELOG.md`, tag `x.y.z`.
