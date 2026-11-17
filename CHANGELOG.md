# Changelog

## 0.1.11 - 2026-11-17

- `core`: blocos de cache para o assunto "online" (o dado é do servidor e só faz sentido com ele), vindos do Homer e
  generalizados: `KeyedResource` (um valor por chave), `PagedResource` (listas paginadas), `Loadable`/`LoadStatus`,
  `Slice`, `SessionCache`/`SnapshotCache` com `values`, e `SessionCaches` (o logout esvazia todos). A falha agora é do
  app: cada bloco recebe `mapError: (Exception) -> F?`, e `null` diz que foi um cancelamento da camada de rede do app,
  que nunca vira falha. Gêmeos dos blocos do CoreKit iOS 0.1.12.

## 0.1.10 - 2026-11-17

- `core`: `AnalyticsSink.setUserId` (quem usa o app, num app com conta; `null` no logout), `setCrashKey` e
  `addBreadcrumb` (o que vai no próximo relatório de falha), com padrão vazio. O `CompositeAnalyticsSink` repassa, e o
  `UserSwitchSink` guarda o último id e o entrega quando a chave volta a ligar. Gêmeos do CoreKit iOS 0.1.11.
- `firebase`: user ID no Analytics e no Crashlytics, chaves e trilha no Crashlytics.
- `mixpanel` (novo módulo, Mixpanel Android 8.12): `MixpanelMonitoring.start(context, token, collect, userEnabled)`
  devolve o destino atrás da chave do usuário (opt-out do SDK desligada, opt-in religada, nada sai com ela desligada
  na abertura); `identify`/`reset` pelo `setUserId`. Gêmeo do `CoreKitMixpanel` do iOS.

## 0.1.9 - 2026-11-17

Gêmeos do CoreKit iOS 0.1.10, com o comportamento de antes como padrão:

- `core`: `CollectionPolicy.shouldCollect(…, debugBuildsCollect)`, para um app cujos testadores usam build de debug.
- `billing`: `ProStore.purchase(activity, product, obfuscatedAccountId)` liga a compra à conta do app (gêmeo do
  `appAccountToken`); `BillingGateway.purchase` ganhou o parâmetro e o `FakeBillingGateway` registra
  `purchaseAccountIds`. `ProStore(productOrder = ProductOrder.AS_PLANNED)` mantém a ordem dos planos do app.

Ficam só no iOS, por não existirem aqui: vários observadores do `NavigationDiagnostics`, chaves padrão do `ReviewGate`
(aqui ele não guarda nada), CloudKit, e o período de carência (a Play devolve a assinatura em carência como comprada, e o
`EntitlementKind` daqui não carrega validade).

## 0.1.8 - 2026-11-17

- `network` (novo módulo, OkHttp 5): a parte genérica da rede do Homer Android, gêmea do `CoreKitNetwork` do CoreKit iOS
  0.1.9. O que era fixo no Homer virou escolha do app, com o comportamento dele como exemplo: o cabeçalho e o prefixo do
  token e a regra de quais rotas o levam (`AuthPolicy`), a política de retry (`RetryPolicy`, padrão igual ao do iOS),
  os segmentos de URL mascarados (`UrlRedaction`), os cabeçalhos apagados do log e a tradução de erro do `ApiCaller`.
  O cabeçalho de "não repetir" passou a se chamar `X-CoreKit-No-Retry`. Fica no app o que é de um servidor só:
  envelopes de resposta, a renovação do token, uploads.
- Regra nova no `CLAUDE.md`: API pública e pontos de extensão se avaliam pelo que um app qualquer precisaria, não pelo
  que os apps de hoje usam.

## 0.1.7 - 2026-11-17

- `core`: a chave do usuário ("Enviar estatísticas" nos Ajustes). `AnalyticsSink.setCollectionEnabled` (padrão: nada;
  o `CompositeAnalyticsSink` repassa) e `UserSwitchSink`, que envolve um destino: repassa enquanto a chave está ligada,
  nada enquanto está desligada, e avisa o SDK a cada virada. Um erro vai só com a classe e a pilha, sem a mensagem
  (`Throwable.withoutMessage()`), que pode citar o que o usuário digitou. Gêmeo do `Analytics.setCollectionEnabled`
  do CoreKit iOS 0.1.8.
- `firebase`: `FirebaseMonitoring.start(context, collect, userEnabled = true)` devolve o `FirebaseAnalyticsSink`
  dentro de um `UserSwitchSink` ligado à coleta do Firebase Analytics e do Crashlytics. Com a chave desligada na
  abertura, nada sai do aparelho. Acréscimo: a chamada de antes continua igual.

## 0.1.6 - 2026-11-16

- `compose`: `LaunchedOnce` guarda a marca de "já carregou" no `ViewModelStoreOwner` (o da entrada, com o
  `rememberViewModelStoreNavEntryDecorator`, ou o da Activity), e não mais no estado salvo. Depois da morte do processo
  ou com "Não manter atividades", o estado salvo voltava dizendo que a carga terminou, ao lado de uma ViewModel nova e
  vazia: a carga não rodava e a tela ficava presa no esqueleto. Agora a marca some junto com a ViewModel, e o estado
  salvo guarda só um id do ponto de chamada (duas cargas no mesmo dono não dividem a marca). A chamada continua
  `LaunchedOnce(key) { }`; o novo parâmetro opcional `owner` aceita outro dono. O contorno
  `key(viewModel) { LaunchedOnce(viewModel) { … } }` deixa de ser necessário. O gêmeo iOS (`onFirstAppear` assíncrono)
  não muda: o `@State` dele não sobrevive à morte do processo, então a marca já some junto com a tela.
- `testing`: `ArchitectureCheck` e `ArchitectureRule`, o porte do CoreKit iOS com o mesmo contrato: uma regra é um nome,
  um filtro pelo caminho relativo à raiz e uma expressão regular proibida; linhas de comentário são puladas (aqui também
  as do KDoc, que começam com `*`); pastas proibidas entram no relatório; a saída é "caminho:linha: regra: código",
  ordenada e sem repetição. Lê os `.kt` sob a raiz. `entityTypeNames` lê as classes `@Entity` do Room, gêmeo do
  `modelTypeNames` (`@Model`) do iOS, para a regra "tela não segura objeto do banco".

## 0.1.5 - 2026-11-16

- `compose`: `LaunchedOnce`, para a carga inicial da tela. O `LaunchedEffect(viewModel) { viewModel.load() }` carregava
  de novo a cada volta à tela e a cada recriação da Activity; aqui roda uma vez por entrada na pilha. Sair antes de
  terminar cancela a carga, e a próxima aparição roda de novo. Gêmeo do `onFirstAppear` assíncrono do CoreKit iOS.

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
