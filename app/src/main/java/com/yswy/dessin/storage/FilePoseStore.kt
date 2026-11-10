package com.yswy.dessin.storage

import com.yswy.dessin.core.pose.PoseStore
import java.io.File
import java.io.IOException

/**
 * 保存したポーズを、アプリ専用のフォルダに1件1ファイル(<id>.pose、UTF-8)で置く。
 * 書き込みは一時ファイルに書いてから名前を変え、途中で失敗しても元のファイルを壊さない。
 */
class FilePoseStore(private val dir: File) : PoseStore {
    override fun list(): List<String> {
        ensureDir()
        val files = dir.listFiles() ?: throw IOException("一覧を読めない: $dir")
        return files.filter { it.isFile && it.name.endsWith(EXT) }.map { it.name.removeSuffix(EXT) }
    }

    override fun read(id: String): String = fileOf(id).readText(Charsets.UTF_8)

    override fun write(id: String, text: String) {
        ensureDir()
        val target = fileOf(id)
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("書き込めない: $target")
        }
    }

    override fun delete(id: String) {
        val f = fileOf(id)
        if (!f.delete()) throw IOException("削除できない: $f")
    }

    /** id はファイル名にそのまま使うので、英数字とハイフンだけを許す(フォルダの外を指さないように) */
    private fun fileOf(id: String): File {
        if (!ID.matches(id)) throw IOException("id が不正: $id")
        return File(dir, id + EXT)
    }

    private fun ensureDir() {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("フォルダを作れない: $dir")
    }

    private companion object {
        const val EXT = ".pose"
        val ID = Regex("[A-Za-z0-9-]+")
    }
}
