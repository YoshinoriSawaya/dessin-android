package com.yswy.dessin.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

private const val TAG = "DessinGl"

/** シェーダを組んだプログラム。失敗したら理由をログに出して例外にする(描画を続けられないため) */
class GlProgram(vertexSource: String, fragmentSource: String) {
    val id: Int
    private val uniforms = HashMap<String, Int>()

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vs)
        GLES30.glAttachShader(id, fs)
        GLES30.glBindAttribLocation(id, ATTR_POSITION, "aPosition")
        GLES30.glBindAttribLocation(id, ATTR_NORMAL, "aNormal")
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(id)
            Log.e(TAG, "プログラムのリンクに失敗: $log")
            throw IllegalStateException("シェーダのリンクに失敗: $log")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
    }

    fun use() = GLES30.glUseProgram(id)

    fun uniform(name: String): Int = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "シェーダのコンパイルに失敗: $log")
            throw IllegalStateException("シェーダのコンパイルに失敗: $log")
        }
        return shader
    }

    companion object {
        const val ATTR_POSITION = 0
        const val ATTR_NORMAL = 1
    }
}

/** 三角形のメッシュを GPU に載せたもの。法線が無いメッシュ(可動域の面)も扱う */
class GpuMesh(positions: FloatArray, normals: FloatArray, indices: IntArray) {
    private val vao: Int
    private val count = indices.size

    init {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glBindVertexArray(vao)
        attribute(GlProgram.ATTR_POSITION, positions)
        if (normals.isNotEmpty()) attribute(GlProgram.ATTR_NORMAL, normals)
        val ibo = IntArray(1)
        GLES30.glGenBuffers(1, ibo, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, intBuffer(indices), GLES30.GL_STATIC_DRAW)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }
}

/** 線分の並び(GL_LINES)または閉じた折れ線(GL_LINE_LOOP)を GPU に載せたもの */
class GpuLines(positions: FloatArray, private val mode: Int = GLES30.GL_LINES) {
    private val vao: Int
    private val count = positions.size / 3

    init {
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glBindVertexArray(vao)
        attribute(GlProgram.ATTR_POSITION, positions)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        if (count == 0) return
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(mode, 0, count)
        GLES30.glBindVertexArray(0)
    }
}

private fun attribute(location: Int, data: FloatArray) {
    val vbo = IntArray(1)
    GLES30.glGenBuffers(1, vbo, 0)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floatBuffer(data), GLES30.GL_STATIC_DRAW)
    GLES30.glEnableVertexAttribArray(location)
    GLES30.glVertexAttribPointer(location, 3, GLES30.GL_FLOAT, false, 0, 0)
}

private fun floatBuffer(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }

private fun intBuffer(data: IntArray): IntBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(data); position(0) }
