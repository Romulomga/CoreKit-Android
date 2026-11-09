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

## Verificação

```sh
./gradlew assemble ktlint :compose:lintDebug :core:test :compose:testDebugUnitTest
```
