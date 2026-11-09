package io.github.romulomga.corekit

import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Recolhe os arquivos que o app gera no cache para compartilhar (PDF, backup, planilha). O Android só limpa o cache
 * quando falta espaço, e um compartilhamento cancelado deixava no aparelho dados do usuário fora do banco: quem criou
 * é quem recolhe.
 *
 * Sem idade mínima, chame só na abertura do processo: limpar ao gerar ou ao voltar ao primeiro plano poderia apagar um
 * arquivo que o destino do compartilhamento ainda não terminou de ler. Com `olderThan`, pode rodar em qualquer
 * abertura, inclusive quando o processo nasce para um widget com um compartilhamento em andamento.
 */
public object GeneratedFiles {
    /**
     * Apaga, em cada pasta de [prefixesByDirectory] dentro de [cacheDir], os arquivos cujo nome começa com o prefixo
     * dela (prefixo vazio: todos). Com [olderThan], só os modificados há mais tempo que isso. Devolve quantos saíram.
     */
    public fun purge(
        cacheDir: File,
        prefixesByDirectory: Map<String, String>,
        olderThan: Duration? = null,
        now: Instant = Instant.now()
    ): Int {
        val cutoff = olderThan?.let { now.minus(it).toEpochMilli() }
        var removed = 0

        for ((directory, prefix) in prefixesByDirectory) {
            val files = File(cacheDir, directory).listFiles() ?: continue

            for (file in files) {
                val isOldEnough = cutoff == null || file.lastModified() <= cutoff

                if (file.isFile && file.name.startsWith(prefix) && isOldEnough && file.delete()) removed += 1
            }
        }

        return removed
    }
}
