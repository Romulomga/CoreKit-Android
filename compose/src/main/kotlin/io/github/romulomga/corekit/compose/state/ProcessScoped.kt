package io.github.romulomga.corekit.compose.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import java.util.UUID

/** A identidade deste processo. Muda a cada processo novo, inclusive o que o sistema recria depois de matar o app. */
public val processInstanceId: String = UUID.randomUUID().toString()

/**
 * Estado salvo (`rememberSaveable`, `rememberNavBackStack`) que vale na recriação da Activity e NÃO na morte do
 * processo. Serve à posição dentro de um fluxo cujos dados moram em memória: a rotação mantém os dois, a morte do
 * processo apaga os dados, e a posição restaurada sozinha deixaria a pessoa numa etapa adiantada de um formulário vazio.
 *
 * Funciona porque a chave entra no caminho que identifica o estado salvo: o processo novo procura outro caminho e
 * começa do valor inicial. Quando os dados cabem num `SavedDraft`, prefira salvar os dois.
 */
@Composable
public inline fun <T> processScoped(instanceId: String = processInstanceId, content: @Composable () -> T): T = key(instanceId) { content() }
