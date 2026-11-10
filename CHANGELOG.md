# Changelog

## 0.1.2 - 2026-11-10

- `database` (módulo novo): `KeepCorruptDatabaseFactory`. O Android apaga sozinho o arquivo de um banco que o SQLite
  acusa de corrompido e abre outro vazio; com a fábrica no `openHelperFactory` do Room o arquivo fica e a abertura
  lança, para o app mostrar a tela de recuperação.

## 0.1.1 - 2026-11-09

- `billing`: a busca de produtos devolve `ProductsAnswer`: uma falha parcial mantém os planos que carregaram, e
  "não deu para falar com a Play" (`unavailable`) é distinto de "a busca falhou" (`error`). Quebra só para quem
  implementa `BillingGateway`; o `FakeBillingGateway` continua com `products`.
- `billing`: a conexão com a Play tem teto de 10 s; sem ele, um serviço que nunca responde prendia "Restaurar compras"
  e o catálogo para sempre.

## 0.1.0 - 2026-11-09

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
- `ktlint.gradle` para os apps aplicarem direto do submódulo, com entradas e saídas declaradas (não roda sem mudança).
- `compose`: `TrackAppearance` (a conta do `.onAppear`: conta na volta, nunca na recriação) e `processScoped` (estado
  salvo que não atravessa a morte do processo). Vieram da adoção no Homer.
- `core`: `SingleFlight.launchJob`, para quem precisa do `Job`; `GeneratedFiles.purge(olderThan)`.
- `compose`: `SavedStateHandle.optionalDraft` e `draft(restoreIf)`, para não retomar formulário intocado.
- `core`: `Entitlement.isProFlow`.
- `billing`: em `ALREADY_OWNED` o observador fecha a tentativa antes da nova consulta à Play.
