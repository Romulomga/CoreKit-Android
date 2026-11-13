# CoreKit-Android

Base não visual dos apps Android do Rômulo: as peças que evitam os bugs que se repetem de app para app (gravação em
dobro no toque duplo, formulário perdido na morte do processo, evento contado duas vezes, tela empilhada duas vezes,
Pro que nasce `false`). O par visual é o [DSKit-Android](https://github.com/Romulomga/DSKit-Android); o gêmeo por
conceito é o [CoreKit iOS](https://github.com/Romulomga/CoreKit): quando o problema existe nas duas plataformas, a peça
tem o mesmo nome e a mesma regra nas duas.

## Módulos

- `core`: Kotlin puro. `SingleFlight`, `Entitlement`, `AppDispatchers`, `stateInScreen`, `GeneratedFiles`,
  `SystemZoneClock`.
- `compose`: Android + Compose. `Navigator`/`NavigationState` (Navigation 3), `SavedDraft`, `processScoped`, `TrackOnce`,
  `TrackAppearance`, `ObserveEvents`.
- `testing`: `MainDispatcherRule`, `FakeEntitlement`.
- `billing`: Play Billing. `ProStore` (o direito ao Pro com o último valor confirmado guardado e o estado "ainda não
  sei"; `force(kind)` para os builds de teste), `ProProduct.chargedAtPurchaseCents` (o que a compra cobra na hora),
  `PlayBillingGateway`, `FakeBillingGateway`.
- `firebase`: `FirebaseMonitoring.start` e `FirebaseAnalyticsSink` (Analytics + Crashlytics atrás do `AnalyticsSink` do
  `core`, com a coleta desligada fora do build de loja), `FirebaseMonitoring.isDebugViewEnabled`.
- `review`: `StoreReview.request` (In-App Review); a trava de frequência `ReviewGate` fica no `core`.
- `database`: `KeepCorruptDatabaseFactory`, para o Room não deixar o Android apagar um banco corrompido.

O `core` também traz `AnalyticsSink`, `PurchaseRevenue`, `CollectionPolicy` e `ReviewGate`.

Só no Android: `SingleFlight`, `AppDispatchers`, `stateInScreen`, `GeneratedFiles`, `SystemZoneClock`, `SavedDraft`,
`processScoped`, `TrackOnce`, `ObserveEvents` (recriação da Activity e morte do processo não existem no iOS).

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
corekit-database = { module = "com.github.Romulomga.CoreKit-Android:database", version.ref = "corekit" }
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

// Build de teste: o grátis numa conta que tem o vitalício.
store.force(EntitlementKind.NONE)
```

A receita da venda com o preço cobrado, não o de lista (zero quando a compra começa com teste grátis):

```kotlin
val plan = product.plan

sink.logPurchase(PurchaseRevenue(plan.productId, product.chargedAtPurchaseCents, product.currencyCode, itemName = plan.analyticsName))
```

Analytics que não coleta em debug, salvo com o DebugView ligado para o app
(`adb shell setprop debug.firebase.analytics.app <pacote>`; `.none.` desliga):

```kotlin
val collect = CollectionPolicy.shouldCollect(
    isDebugBuild = BuildConfig.DEBUG,
    debugOptIn = BuildConfig.DEBUG && FirebaseMonitoring.isDebugViewEnabled(context)
)
val sink = FirebaseMonitoring.start(context, collect)
```

## Verificação

```sh
./gradlew assemble ktlint lintDebug :core:test testDebugUnitTest
```
