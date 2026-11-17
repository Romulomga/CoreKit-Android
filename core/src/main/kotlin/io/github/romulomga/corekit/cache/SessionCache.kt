package io.github.romulomga.corekit.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Um repositório do que as telas leem do servidor, guardado em memória, um por processo, esvaziado quando a sessão
 * acaba (gêmeo do `SessionCache` do CoreKit iOS). Cada um entra no conjunto do [SessionCaches] (num app com Hilt, um
 * `@Binds @IntoSet` em `di/`).
 */
public interface SessionCache {
    /** A sessão acabou: nada fica, e as respostas ainda a caminho são descartadas. */
    public fun clear()
}

/** Os caches em memória de uma sessão, esvaziados juntos quando ela acaba (logout, token recusado). */
public class SessionCaches(private val caches: Set<SessionCache>) {
    public val count: Int get() = caches.size

    /** Nada de quem saiu fica, e as respostas ainda a caminho são descartadas. */
    public fun clear() {
        caches.forEach(SessionCache::clear)
    }
}

/** O que um repositório entrega às telas: o valor agora ([snapshot]) e o fluxo de valores ([values]). */
public interface SnapshotCache<S> : SessionCache {
    /** O cache agora. A ViewModel lê o valor no `init` para o primeiro quadro (ler memória não é efeito colateral). */
    public val snapshot: StateFlow<S>
}

/** O que [read] tira do cache agora, e de novo a cada mudança que o mexe (nunca duas vezes o mesmo valor seguido). */
public fun <S, V> SnapshotCache<S>.values(read: (S) -> V): Flow<V> = snapshot.map(read).distinctUntilChanged()
