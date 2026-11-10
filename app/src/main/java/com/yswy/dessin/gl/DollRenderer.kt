package com.yswy.dessin.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.mesh.MeshKind
import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.mesh.gridLines
import com.yswy.dessin.core.mesh.meshWorldMatrix
import com.yswy.dessin.core.skeleton.Skeleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 3D の描画。GL のスレッドで動き、UI のスレッドからは scene を差し替えて requestRender する。
 * いまは床の格子と、置いた部位を基本の色で描く(色分け・可動域・玉は E02-04)。
 */
class DollRenderer : GLSurfaceView.Renderer {
    /** 描く内容。UI のスレッドから丸ごと差し替える */
    class Scene(val skeleton: Skeleton, val camera: OrbitCamera)

    @Volatile
    var scene = Scene(Skeleton.initial(), OrbitCamera())

    private lateinit var lit: GlProgram
    private lateinit var flat: GlProgram
    private lateinit var gridCenter: GpuLines
    private lateinit var gridOthers: GpuLines
    private var aspect = 1.0

    /** 部位ごとの GPU のメッシュ。GL の文脈が作り直されたら捨てる */
    private class PartGpu(val body: GpuMesh, val edges: GpuLines?, val lines: List<GpuMesh>, val kind: MeshKind)
    private val parts = HashMap<String, List<PartGpu>>()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(PAPER[0], PAPER[1], PAPER[2], 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        lit = GlProgram(LitShader.VERTEX, LitShader.FRAGMENT)
        flat = GlProgram(FlatShader.VERTEX, FlatShader.FRAGMENT)
        val grid = gridLines(size = 6.0, divisions = 12, y = 0.001)
        gridCenter = GpuLines(grid.center)
        gridOthers = GpuLines(grid.others)
        parts.clear()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspect = width.toDouble() / height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = scene
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val viewProjection = (s.camera.projectionMatrix(aspect) * s.camera.viewMatrix()).toFloatArray()

        flat.use()
        GLES30.glUniformMatrix4fv(flat.uniform("uViewProjection"), 1, false, viewProjection, 0)
        GLES30.glUniformMatrix4fv(flat.uniform("uModel"), 1, false, IDENTITY, 0)
        GLES30.glUniform4fv(flat.uniform("uColor"), 1, GRID, 0)
        gridOthers.draw()
        GLES30.glUniform4fv(flat.uniform("uColor"), 1, GRID_CENTER, 0)
        gridCenter.draw()

        s.skeleton.placed.forEach { id ->
            gpuOf(id).forEachIndexed { i, p ->
                val model = meshWorldMatrix(s.skeleton, id, i).toFloatArray()
                lit.use()
                GLES30.glUniformMatrix4fv(lit.uniform("uViewProjection"), 1, false, viewProjection, 0)
                GLES30.glUniformMatrix4fv(lit.uniform("uModel"), 1, false, model, 0)
                GLES30.glUniform3fv(lit.uniform("uColor"), 1, if (p.kind == MeshKind.MARKER) MARK else BASE, 0)
                GLES30.glUniform3f(lit.uniform("uEmissive"), 0f, 0f, 0f)
                GLES30.glUniform1f(lit.uniform("uOpacity"), 1f)
                p.body.draw()
                flat.use()
                GLES30.glUniformMatrix4fv(flat.uniform("uViewProjection"), 1, false, viewProjection, 0)
                GLES30.glUniformMatrix4fv(flat.uniform("uModel"), 1, false, model, 0)
                GLES30.glUniform4fv(flat.uniform("uColor"), 1, EDGE, 0)
                p.edges?.draw()
                GLES30.glUniform4fv(flat.uniform("uColor"), 1, LINE, 0)
                p.lines.forEach { it.draw() }
            }
        }
    }

    private fun gpuOf(id: String): List<PartGpu> = parts.getOrPut(id) {
        PartVisuals.meshes(id).map { pm ->
            PartGpu(
                GpuMesh(pm.mesh.positions, pm.mesh.normals, pm.mesh.indices),
                pm.edges?.let { GpuLines(it) },
                pm.lines.map { GpuMesh(it.positions, FloatArray(0), it.indices) },
                pm.kind,
            )
        }
    }

    private companion object {
        val IDENTITY = Mat4.IDENTITY.toFloatArray()

        // 色は Web版の値(0xRRGGBB を 0〜1 に)
        val PAPER = rgb(0xe6e4df)
        val BASE = rgb(0xdcd8cf)
        val MARK = rgb(0xb8b3a6)
        val EDGE = rgba(0x58534a)
        val LINE = rgba(0xb02a2a)
        val GRID = rgba(0xcdcac1)
        val GRID_CENTER = rgba(0xb7b3a8)

        fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xff) / 255f, ((c shr 8) and 0xff) / 255f, (c and 0xff) / 255f)
        fun rgba(c: Int, a: Float = 1f) = rgb(c) + a
    }
}
