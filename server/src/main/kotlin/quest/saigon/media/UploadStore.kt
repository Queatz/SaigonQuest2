package quest.saigon.media

import java.io.File
import java.util.UUID

class UploadStore(val root: File) {
    fun ensure() {
        File(root, "posts").mkdirs()
        File(root, "avatars").mkdirs()
        File(root, "private").mkdirs()
    }

    fun writePost(jpeg: ByteArray): String = write("posts", "jpg", jpeg)

    fun writeAvatar(png: ByteArray): String = write("avatars", "png", png)

    fun writeProfile(jpeg: ByteArray): String = write("private", "jpg", jpeg)

    fun read(relative: String): ByteArray {
        val file = resolve(relative) ?: throw IllegalArgumentException("not_found")
        return file.readBytes()
    }

    fun delete(relative: String) {
        resolve(relative)?.delete()
    }

    fun publicFile(kind: String, name: String): File? {
        if (kind != "posts" && kind != "avatars") return null
        if (!safeName(name)) return null
        return resolve("$kind/$name")
    }

    fun resolve(relative: String): File? {
        val file = root.toPath().resolve(relative).normalize().toFile()
        val rootPath = root.toPath().normalize()
        if (!file.toPath().startsWith(rootPath)) return null
        return file.takeIf { it.isFile }
    }

    private fun write(folder: String, extension: String, bytes: ByteArray): String {
        ensure()
        val name = "${UUID.randomUUID().toString().replace("-", "")}.$extension"
        val relative = "$folder/$name"
        val file = root.toPath().resolve(relative).normalize().toFile()
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
        return relative
    }

    private fun safeName(name: String): Boolean =
        name.length in 1..80 && name.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' } && !name.contains("..")
}
