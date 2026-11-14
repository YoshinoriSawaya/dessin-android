package com.yswy.dessin.storage

import com.yswy.dessin.core.pose.PoseStore
import java.io.File
import java.io.IOException

/**
 * 保存したポーズ・体型を、アプリ専用のフォルダに1件1ファイル(<id><ext>、UTF-8)で置く。ext はポーズが .pose、体型が .body。
 * 書き込みは一時ファイルに書いてから名前を変え、途中で失敗しても元のファイルを壊さない。
 */
class FilePoseStore(private val dir: File, private val ext: String = ".pose") : PoseStore {
    override fun list(): List<String> {
        ensureDir()
        val files = dir.listFiles() ?: throw IOException("一覧を読めない: $dir")
        return files.filter { it.isFile && it.name.endsWith(ext) }.map { it.name.removeSuffix(ext) }
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
        return File(dir, id + ext)
    }

    private fun ensureDir() {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("フォルダを作れない: $dir")
    }

    private companion object {
        val ID = Regex("[A-Za-z0-9-]+")
    }
}
