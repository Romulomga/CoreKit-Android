package io.github.romulomga.corekit

import java.io.File

/**
 * Recolhe os arquivos que o app gera no cache para compartilhar (PDF, backup, planilha). O Android só limpa o cache
 * quando falta espaço, e um compartilhamento cancelado deixava no aparelho dados do usuário fora do banco: quem criou
 * é quem recolhe.
 *
 * Chame só na abertura do processo: limpar ao gerar ou ao voltar ao primeiro plano poderia apagar um arquivo que o
 * destino do compartilhamento ainda não terminou de ler. Na abertura nada está em voo.
 */
public object GeneratedFiles {
    /**
     * Apaga, em cada pasta de [prefixesByDirectory] dentro de [cacheDir], os arquivos cujo nome começa com o prefixo
     * dela (prefixo vazio: todos). Devolve quantos saíram.
     */
    public fun purge(cacheDir: File, prefixesByDirectory: Map<String, String>): Int {
        var removed = 0

        for ((directory, prefix) in prefixesByDirectory) {
            val files = File(cacheDir, directory).listFiles() ?: continue

            for (file in files) {
                if (file.isFile && file.name.startsWith(prefix) && file.delete()) removed += 1
            }
        }

        return removed
    }
}
