# CoreKit-Android

Base não visual dos apps Android do Rômulo: as peças que evitam os bugs que se repetem de app para app (gravação em
dobro no toque duplo, formulário perdido na morte do processo, evento contado duas vezes, tela empilhada duas vezes,
Pro que nasce `false`). O par visual é o [DSKit-Android](https://github.com/Romulomga/DSKit-Android).

## Módulos

- `core`: Kotlin puro. `SingleFlight`, `Entitlement`, `AppDispatchers`, `stateInScreen`, `GeneratedFiles`,
  `SystemZoneClock`.
- `compose`: Android + Compose. `Navigator`/`NavigationState` (Navigation 3), `SavedDraft`, `TrackOnce`,
  `ObserveEvents`.
- `testing`: `MainDispatcherRule`, `FakeEntitlement`.
- `billing`: Play Billing. `ProStore` (o direito ao Pro com o último valor confirmado guardado e o estado "ainda não
  sei"), `PlayBillingGateway`, `FakeBillingGateway`.
- `firebase`: `FirebaseMonitoring.start` e `FirebaseAnalyticsSink` (Analytics + Crashlytics atrás do `AnalyticsSink` do
  `core`, com a coleta desligada fora do build de loja).
- `review`: `StoreReview.request` (In-App Review); a trava de frequência `ReviewGate` fica no `core`.

O `core` também traz `AnalyticsSink`, `CollectionPolicy` e `ReviewGate`.

## Uso num app

Como submódulo preso a uma tag, igual ao DSKit:

```sh
git submodule add https://github.com/Romulomga/CoreKit-Android.git
```

```kotlin
// settings.gradle.kts
includeBuild("CoreKit-Android")
```

```toml
# gradle/libs.versions.toml
corekit-core = { module = "com.github.Romulomga.CoreKit-Android:core", version.ref = "corekit" }
corekit-compose = { module = "com.github.Romulomga.CoreKit-Android:compose", version.ref = "corekit" }
corekit-testing = { module = "com.github.Romulomga.CoreKit-Android:testing", version.ref = "corekit" }
corekit-billing = { module = "com.github.Romulomga.CoreKit-Android:billing", version.ref = "corekit" }
corekit-firebase = { module = "com.github.Romulomga.CoreKit-Android:firebase", version.ref = "corekit" }
corekit-review = { module = "com.github.Romulomga.CoreKit-Android:review", version.ref = "corekit" }
```

O estilo (ktlint) vem daqui, em vez de uma cópia do script em cada app:

```kotlin
// build.gradle.kts de cada módulo do app
apply(from = "$rootDir/CoreKit-Android/ktlint.gradle")
```

AGP e Kotlin do app precisam ser os mesmos do CoreKit (`gradle/libs.versions.toml`).

## Exemplos

Uma gravação por toque:

```kotlin
private val saving = SingleFlight()
val isSaving = saving.isRunning

fun save() {
    saving.launch(viewModelScope) { repository.save(form.value) }
}
```

Formulário que sobrevive à morte do processo:

```kotlin
class DoseViewModel(handle: SavedStateHandle) : ViewModel() {
    private val form = handle.draft("dose", DoseForm.serializer(), DoseForm())
    val state = form.flow

    fun setAmount(value: String) = form.update { it.copy(amount = value) }
    fun saved() = form.clear()
}
```

Evento de tela uma vez por visita, e eventos de uso único só com a tela à vista:

```kotlin
TrackOnce("weight") { analytics.screen("weight") }

ObserveEvents(viewModel.events) { event -> snackbar.showSnackbar(event.message) }
```

O direito ao Pro, com as chaves e o analytics do app por fora:

```kotlin
val store = ProStore(
    gateway = PlayBillingGateway(context, appScope),
    plans = listOf(ProPlan("app.pro.yearly", ProPlanType.SUBSCRIPTION), ProPlan("app.pro.lifetime", ProPlanType.LIFETIME)),
    memory = dataStoreEntitlementMemory,
    observer = appBillingObserver
)

// Na abertura: a primeira tela espera o direito, em vez de desenhar o teaser para quem paga.
store.awaitResolved()
```

Analytics que não coleta em debug:

```kotlin
val sink = FirebaseMonitoring.start(context, collect = CollectionPolicy.shouldCollect(BuildConfig.DEBUG))
```

## Verificação

```sh
./gradlew assemble ktlint lintDebug :core:test testDebugUnitTest
```
