# Changelog

## Não lançado

- `core`: `SingleFlight`, `KeyedSingleFlight`, `Entitlement`/`EntitlementState`, `AppDispatchers`, `stateInScreen`,
  `GeneratedFiles.purge`, `SystemZoneClock`.
- `compose`: `Navigator`/`NavigationState` com a trava de chave repetida no topo, `SavedDraft`, `TrackOnce`,
  `ObserveEvents`.
- `testing`: `MainDispatcherRule`, `FakeEntitlement`.
- `core`: `AnalyticsSink` (com o destino nulo e o composto), `CollectionPolicy`, `ReviewGate`.
- `billing`: `ProStore` sobre o `BillingGateway` (direito guardado, estado `UNKNOWN` com teto de espera, consulta que
  falha não muda nada, compra sempre reconhecida), `PlayBillingGateway`, `FakeBillingGateway`.
- `firebase`: `FirebaseMonitoring.start` e `FirebaseAnalyticsSink`.
- `review`: `StoreReview.request`.
- `ktlint.gradle` para os apps aplicarem direto do submódulo.
