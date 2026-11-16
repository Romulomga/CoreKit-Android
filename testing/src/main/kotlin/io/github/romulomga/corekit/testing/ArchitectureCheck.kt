package io.github.romulomga.corekit.testing

import java.io.File

/**
 * Uma regra sobre onde uma coisa pode aparecer no código do app, conferida por um teste.
 *
 * A arquitetura escrita deriva: uma tela passa a segurar uma `@Entity`, uma ViewModel volta a ler o banco direto, uma
 * pasta `screens/` reaparece. Um teste que lê o código e falha com o arquivo e a linha faz o desvio quebrar o build, em
 * vez de esperar uma revisão. Gêmeo do `ArchitectureRule` do CoreKit iOS.
 *
 * As linhas dos arquivos que [files] aceita (pelo caminho relativo à raiz, com `/`) não podem conter [forbidding]
 * (uma expressão regular, procurada em qualquer ponto da linha). Linhas de comentário são puladas.
 */
public class ArchitectureRule(public val name: String, internal val files: (String) -> Boolean, forbidding: String) {
    internal val pattern: Regex = Regex(forbidding)
}

public object ArchitectureCheck {
    /**
     * Cada linha que quebra uma regra, como "caminho:linha: regra: código", e cada pasta de [forbiddenFolders]
     * encontrada sob [root], como "caminho: folder X/ is not allowed". Vazia quando o código segue as regras; ordenada e
     * sem repetição, como no iOS.
     *
     * Linha de comentário é a que começa (depois dos espaços) com duas barras, com a abertura de um bloco de comentário
     * ou com um asterisco: no Kotlin o KDoc ocupa várias linhas e costuma citar justamente o nome que a regra proíbe.
     */
    public fun violations(root: File, rules: List<ArchitectureRule>, forbiddenFolders: Set<String> = emptySet()): List<String> {
        val found = mutableSetOf<String>()

        for (path in kotlinFiles(root)) {
            for (folder in path.split("/").dropLast(1)) {
                if (folder in forbiddenFolders) {
                    found += "$path: folder $folder/ is not allowed"
                }
            }

            val applicable = rules.filter { it.files(path) }
            if (applicable.isEmpty()) continue

            root.resolve(path).readLines().forEachIndexed { index, line ->
                val code = line.trim()
                if (code.isComment()) return@forEachIndexed

                for (rule in applicable) {
                    if (rule.pattern.containsMatchIn(code)) {
                        found += "$path:${index + 1}: ${rule.name}: $code"
                    }
                }
            }
        }

        return found.sorted()
    }

    /**
     * Os nomes das classes marcadas com `@Entity` (Room) sob [root]: o que uma regra sobre objetos do banco nas telas deve
     * proibir, lido do código em vez de uma lista que envelhece. Gêmeo do `modelTypeNames` do iOS, que lê o `@Model`.
     */
    public fun entityTypeNames(root: File): List<String> {
        val names = sortedSetOf<String>()

        for (path in kotlinFiles(root)) {
            val lines = root.resolve(path).readLines()

            lines.forEachIndexed { index, line ->
                if (!ENTITY.containsMatchIn(line.trim())) return@forEachIndexed

                val rest = lines.drop(index).take(ENTITY_LOOKAHEAD).joinToString(" ")
                DECLARATION.find(rest)?.let { names += it.groupValues[1] }
            }
        }

        return names.toList()
    }

    /** Os caminhos dos arquivos `.kt` sob [root], relativos a ela, com `/`, ordenados. */
    internal fun kotlinFiles(root: File): List<String> = root
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .sorted()
        .toList()

    private fun String.isComment(): Boolean = startsWith("//") || startsWith("/*") || startsWith("*")

    private val ENTITY = Regex("""^@Entity\b""")

    // `Pai::class` dentro dos argumentos do `@Entity` (chave estrangeira) não é a declaração.
    private val DECLARATION = Regex("""(?<!::)\bclass\s+([A-Za-z_][A-Za-z0-9_]*)""")

    // O `@Entity(...)` com índices e chaves estrangeiras costuma ocupar várias linhas antes do `class`.
    private const val ENTITY_LOOKAHEAD = 12
}
