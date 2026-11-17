package io.github.romulomga.corekit.sync

import java.time.Instant
import java.util.UUID

/**
 * Uma gravação local que o servidor ainda não confirmou: uma linha da fila de envio do app. O store do app a insere na
 * mesma transação do registro que ela muda (assim um não existe sem o outro se o app fechar no meio); o motor só lê,
 * confirma, recusa ou larga. Gêmeo do `PendingChange` do CoreKit iOS (lá o [payload] é `Data`; aqui, o texto que o app
 * codifica, em geral JSON).
 *
 * [id] nasce no aparelho e vai em toda tentativa: o servidor o usa como chave de idempotência, então uma mudança enviada
 * duas vezes (a resposta perdida na volta) é aplicada uma vez.
 */
public data class PendingChange(
    val id: String = UUID.randomUUID().toString(),
    /** O assunto que a envia ([SyncSubject.name]). */
    val subject: String,
    /** O registro que ela muda: as mudanças seguintes do mesmo registro vão depois dela, em ordem. */
    val entityId: String,
    val kind: Kind,
    val payload: String,
    val createdAt: Instant,
    /** Tentativas que falharam no caminho (sem rede, timeout): a mudança fica e vai de novo. */
    val attempts: Int = 0,
    /**
     * Preenchido quando o servidor recusou de vez. As mudanças seguintes do registro esperam o app resolver (largar a
     * mudança ou limpar a recusa para tentar de novo).
     */
    val rejection: String? = null
) {
    public enum class Kind {
        UPSERT,

        /** O registro fica como lápide (escondido pelas leituras do app) até o servidor confirmar. */
        DELETE
    }
}

/**
 * A fila de envio do app, no schema do app (uma mudança no CoreKit nunca mexe na migração do banco de ninguém). Cada
 * método grava antes de voltar; uma gravação que falha lança, e o motor a relata pelo `mapError`.
 */
public interface Outbox {
    /** As mudanças do assunto, da mais antiga para a mais nova, recusadas incluídas. */
    public suspend fun pending(subject: String): List<PendingChange>

    /** O servidor aplicou: sai da fila. */
    public suspend fun confirm(id: String)

    /** O servidor recusou de vez. */
    public suspend fun reject(id: String, reason: String)

    /** Uma tentativa falhou no caminho. */
    public suspend fun recordAttempt(id: String)

    /** A versão do servidor venceu ([ConflictPolicy.SERVER_WINS]): as mudanças desses registros saem. */
    public suspend fun discard(entityIds: Set<String>, subject: String)

    /** A sessão acabou: nada do assunto fica na fila. */
    public suspend fun removeAll(subject: String)
}

/** Um registro como o servidor mandou numa leitura. */
public interface SyncedRecord {
    public val entityId: String
}

/** O que uma leitura trouxe: o que mudou desde o cursor (ou a lista inteira), o que saiu, e o cursor da próxima vez. */
public data class PullBatch<R : SyncedRecord>(
    val records: List<R>,
    val removed: Set<String> = emptySet(),
    val cursor: String?,
    val shape: Shape,
    /** Há mais mudanças no servidor: o motor pede de novo a partir do [cursor]. */
    val hasMore: Boolean = false
) {
    public enum class Shape {
        /** Só o que mudou desde o cursor; [removed] diz o que saiu. */
        CHANGES,

        /** Tudo o que o servidor tem: o que o aparelho guarda e a leitura não traz saiu. */
        FULL_LIST
    }
}

/** O que o servidor disse de uma mudança. */
public sealed interface PushOutcome {
    public data object Accepted : PushOutcome

    /** Recusada de vez (uma validação, uma permissão): mandar de novo seria recusado de novo. */
    public data class Rejected(val reason: String) : PushOutcome
}

/** Onde o assunto está, guardado pelo app com os dados dele (na mesma transação que aplica uma leitura). */
public data class SyncState(val cursor: String? = null, val syncedAt: Instant? = null)

/** O que uma leitura faz com um registro que ainda tem mudança esperando para ir. */
public enum class ConflictPolicy {
    /**
     * O padrão: a mudança local vai primeiro e o servidor decide; a leitura não toca no registro, e a próxima traz o
     * que o servidor ficou (a última gravação vence, pela ordem do servidor).
     */
    PENDING_WINS,

    /** A versão do servidor substitui a local, e as mudanças esperando saem. */
    SERVER_WINS
}

/**
 * Um assunto guardado no banco do app e sincronizado com um servidor (o jeito "meio a meio"). O app implementa as
 * chamadas de rede e as gravações; as regras (ordem, reenvio, o que a leitura pode tocar, um sync por vez) são do motor.
 */
public interface SyncSubject<R : SyncedRecord> {
    /** A chave do assunto na fila. */
    public val name: String

    public val conflictPolicy: ConflictPolicy get() = ConflictPolicy.PENDING_WINS

    public suspend fun state(): SyncState

    public suspend fun pull(since: String?): PullBatch<R>

    /**
     * Grava a leitura e o estado novo numa transação só, então o cursor nunca anda sem os dados. Uma lista inteira apaga
     * o que não traz, menos os registros em [keeping] (eles ainda têm mudança esperando para ir).
     */
    public suspend fun apply(batch: PullBatch<R>, keeping: Set<String>, state: SyncState)

    /** Manda uma mudança. Uma falha no caminho (sem rede, timeout, servidor fora) lança: a mudança fica e vai de novo. */
    public suspend fun push(change: PendingChange): PushOutcome = PushOutcome.Rejected("$name does not send changes")

    /** A sessão acabou: o cursor e a data do sync saem (os dados o app apaga). */
    public suspend fun resetState()
}
