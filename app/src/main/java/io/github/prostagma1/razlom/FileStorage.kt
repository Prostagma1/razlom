package io.github.prostagma1.razlom

import android.content.Context
import io.github.prostagma1.razlom.game.Storage
import java.io.File

/** Сохранения лежат обычными текстовыми файлами в приватной папке приложения. */
class FileStorage(context: Context) : Storage {
    private val dir: File = File(context.filesDir, "saves").apply { mkdirs() }

    override fun read(name: String): String? =
        runCatching { File(dir, name).takeIf { it.exists() }?.readText() }.getOrNull()

    override fun write(name: String, content: String) {
        runCatching {
            // Пишем во временный файл и переименовываем: если процесс умрёт
            // посреди записи, старое сохранение останется целым.
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(content)
            val target = File(dir, name)
            target.delete()
            tmp.renameTo(target)
        }
    }

    override fun delete(name: String) {
        runCatching { File(dir, name).delete() }
    }
}
