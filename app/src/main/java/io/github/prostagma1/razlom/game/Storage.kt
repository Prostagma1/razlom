package io.github.prostagma1.razlom.game

/**
 * Куда игра складывает сохранения. Отделено от Android, чтобы логику
 * можно было гонять в обычных unit-тестах.
 */
interface Storage {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun delete(name: String)

    companion object {
        const val RUN = "run.save"
        const val PROFILE = "profile.save"
    }
}

/** Хранилище в памяти: для тестов и для запуска без диска. */
class MemoryStorage : Storage {
    private val files = mutableMapOf<String, String>()

    override fun read(name: String): String? = files[name]
    override fun write(name: String, content: String) {
        files[name] = content
    }

    override fun delete(name: String) {
        files.remove(name)
    }
}
