# Changelog

## 0.1.4 - 2026-11-13

- `billing`: o padrão de `ProProduct.chargedAtPurchaseMicros` segue o `freeTrial`. Um produto montado à mão com teste
  grátis (o fixture de um teste, um fake) informava o preço cheio como cobrado; o da Play já vinha certo.

## 0.1.3 - 2026-11-13

Rodada de paridade com o CoreKit iOS: o que o iOS resolveu e também acontecia no Android.

- `core`: `CollectionPolicy.shouldCollect(debugOptIn = …)`. Um build de debug coleta quando quem desenvolve pede, para
  conferir os eventos (o `--analytics-debug` do iOS); a rodada automatizada nunca coleta, nem com ele.
- `firebase`: `FirebaseMonitoring.isDebugViewEnabled(context)`, o interruptor desse pedido: a mesma propriedade do
  DebugView do Firebase (`adb shell setprop debug.firebase.analytics.app <pacote>`), então os eventos vão só para o
  DebugView e ficam fora dos relatórios.
- `firebase`: o `screen_view` leva `screen_class` com o nome da tela. Sem ele o GA4 punha a Activity, e num app de uma
  Activity só toda tela caía numa linha do relatório de telas.
- `core`/`firebase`: `PurchaseRevenue.itemName` (padrão: o `itemId`), e o item do `purchase` leva preço, moeda e
  quantidade, como no iOS; sem eles os relatórios por produto ficavam sem receita.
- `billing`: `ProProduct.chargedAtPurchaseMicros`/`chargedAtPurchaseCents`, o que a compra cobra na hora (zero quando
  começa com teste grátis). É o valor da receita da venda; o preço de lista inflava a receita dos trials.
- `billing`: `ProStore.force(kind)`, o `force(_:)` do iOS: qualquer direito até o fim do processo, sem perguntar à Play
  e sem guardar nada (o grátis numa conta com vitalício, por exemplo). `forcePro()` continua, como atalho.
- `billing`: a compra que a Play acabou de confirmar dá o Pro mesmo que a consulta seguinte falhe ou ainda não a traga;
  sem resposta, ela só sobe o direito, nunca o rebaixa. Antes, quem acabava de pagar ficava no grátis até a próxima
  volta ao app.
- `billing`: o direito guardado só vale enquanto o estado é `UNKNOWN`. Uma resposta da Play que chegava durante a
  leitura da memória era sobrescrita por ela, e uma assinatura vencida voltava a ser Pro.

Avaliado e não portado: a validade da assinatura guardada (a Play não dá a data de vencimento ao app), o reembolso que
chega pelas atualizações (no Android ele só some da consulta, que já é a verdade) e as consultas em fila (a trava das
consultas já as serializa, na ordem de chegada). Na navegação e na avaliação as regras comuns já eram iguais; o resto
do iOS é problema só de lá.

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
