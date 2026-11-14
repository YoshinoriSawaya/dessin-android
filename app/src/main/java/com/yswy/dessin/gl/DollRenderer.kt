package com.yswy.dessin.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.mesh.Primitives
import com.yswy.dessin.core.mesh.DOT_RING_SCALE
import com.yswy.dessin.core.mesh.dotWorldRadius
import com.yswy.dessin.core.mesh.gridLines
import com.yswy.dessin.core.mesh.guideMesh
import com.yswy.dessin.core.mesh.meshWorldMatrix
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.render.MeshDraw
import com.yswy.dessin.core.render.MeshStyle
import com.yswy.dessin.core.render.drawPlan
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 3D の描画。GL のスレッドで動き、UI のスレッドからは scene を差し替えて requestRender する。
 * 何をどう描くかは :core の drawPlan が決め、ここでは Web版(three.js)と同じ順序で描く:
 * 不透明な部位 → 半透明のゴースト(奥から) → 可動域・縁・玉(奥行きを見ずに手前へ)。
 */
class DollRenderer : GLSurfaceView.Renderer {
    /** 描く内容。UI のスレッドから丸ごと差し替える */
    class Scene(
        val skeleton: Skeleton,
        val camera: OrbitCamera,
        val selected: String,
        val options: DisplayOptions,
    )

    @Volatile
    var scene = Scene(Skeleton.initial(), OrbitCamera(), "rib", DisplayOptions())

    private lateinit var lit: GlProgram
    private lateinit var flat: GlProgram
    private lateinit var gridCenter: GpuLines
    private lateinit var gridOthers: GpuLines
    private lateinit var dotSphere: GpuMesh
    private var aspect = 1.0
    private lateinit var viewProjection: FloatArray

    /** GPU に載せた形。GL の文脈が作り直されたら捨てる */
    private class PartGpu(val body: GpuMesh, val edges: GpuLines?, val lines: List<GpuMesh>)
    private class GuideGpu(val area: GpuMesh, val outline: GpuLines)
    private val parts = HashMap<String, List<PartGpu>>()
    private val guides = HashMap<String, GuideGpu>()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(PAPER[0], PAPER[1], PAPER[2], 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        lit = GlProgram(LitShader.VERTEX, LitShader.FRAGMENT)
        flat = GlProgram(FlatShader.VERTEX, FlatShader.FRAGMENT)
        val grid = gridLines(size = 6.0, divisions = 12, y = 0.001)
        gridCenter = GpuLines(grid.center)
        gridOthers = GpuLines(grid.others)
        val sphere = Primitives.sphere(1.0, 16, 12)
        dotSphere = GpuMesh(sphere.positions, FloatArray(0), sphere.indices)
        parts.clear()
        guides.clear()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspect = width.toDouble() / height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = scene
        // Web版の材質は顔の面と可動域を除いて表側だけを描く(裏側も描くと半透明の重なりが縞になる)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        viewProjection = (s.camera.projectionMatrix(aspect) * s.camera.viewMatrix()).toFloatArray()
        val plan = drawPlan(s.skeleton, s.selected, s.options)

        if (s.options.grid) {
            drawFlat(IDENTITY, GRID) { gridOthers.draw() }
            drawFlat(IDENTITY, GRID_CENTER) { gridCenter.draw() }
        }

        // 不透明な部位
        plan.meshes.filter { it.style != MeshStyle.GHOST }.forEach { d ->
            val model = meshWorldMatrix(s.skeleton, d.partId, d.meshIndex).toFloatArray()
            val p = gpuOf(d.partId)[d.meshIndex]
            drawLit(model, d.style)
            p.body.draw()
            if (d.edges) drawFlat(model, EDGE) { p.edges?.draw() }
            if (d.lines) drawFlat(model, LINE) { p.lines.forEach { it.draw() } }
        }

        // 半透明のゴースト。奥行きは書かず、奥から順に重ねる
        GLES30.glDepthMask(false)
        val eye = s.camera.position
        plan.meshes.filter { it.style == MeshStyle.GHOST }
            .map { d -> d to meshWorldMatrix(s.skeleton, d.partId, d.meshIndex) }
            .sortedByDescending { (_, m) -> m.transformPoint(Vec3.ZERO).distanceTo(eye) }
            .forEach { (d: MeshDraw, m: Mat4) ->
                drawLit(m.toFloatArray(), MeshStyle.GHOST)
                gpuOf(d.partId)[d.meshIndex].body.draw()
            }

        // 可動域と玉。奥行きを見ずに常に手前へ
        plan.guidePart?.let { id -> drawGuide(s, id) }
        GLES30.glDepthMask(true)
    }

    private fun drawGuide(s: Scene, id: String) {
        val spec = PartSpecs[id]
        val world = s.skeleton.worldTransforms()
        val parent = world.getValue(spec.parent!!)
        val g = guides.getOrPut(id) {
            val gm = guideMesh(spec)
            GuideGpu(GpuMesh(gm.area.positions, FloatArray(0), gm.area.indices), GpuLines(gm.outline, GLES30.GL_LINE_LOOP))
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        val model = Mat4.compose(s.skeleton.guideCenter(id), parent.rotation, s.skeleton.guideScale(id)).toFloatArray()
        drawFlat(model, ROM) { g.area.draw() }
        drawFlat(model, ROM_LINE) { g.outline.draw() }
        val dot = s.skeleton.dotWorld(id)
        val r = dotWorldRadius(s.camera.position.distanceTo(dot))
        drawFlat(Mat4.compose(dot, Quat.IDENTITY, r * DOT_RING_SCALE).toFloatArray(), RING) { dotSphere.draw() }
        drawFlat(Mat4.compose(dot, Quat.IDENTITY, r).toFloatArray(), DOT) { dotSphere.draw() }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private fun drawLit(model: FloatArray, style: MeshStyle) {
        lit.use()
        // 顔の面だけは両面(Web版の DoubleSide)
        if (style == MeshStyle.MASK) GLES30.glDisable(GLES30.GL_CULL_FACE) else GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glUniformMatrix4fv(lit.uniform("uViewProjection"), 1, false, viewProjection, 0)
        GLES30.glUniformMatrix4fv(lit.uniform("uModel"), 1, false, model, 0)
        val (color, emissive, opacity) = when (style) {
            MeshStyle.BASE -> Triple(BASE, BLACK, 1f)
            MeshStyle.HOT -> Triple(HOT, HOT_EMISSIVE, 1f)
            MeshStyle.MARKER -> Triple(MARK, BLACK, 1f)
            MeshStyle.MASK -> Triple(MASK, BLACK, 1f)
            MeshStyle.GHOST -> Triple(GHOST, BLACK, 0.26f)
        }
        GLES30.glUniform3fv(lit.uniform("uColor"), 1, color, 0)
        GLES30.glUniform3fv(lit.uniform("uEmissive"), 1, emissive, 0)
        GLES30.glUniform1f(lit.uniform("uOpacity"), opacity)
    }

    private inline fun drawFlat(model: FloatArray, color: FloatArray, draw: () -> Unit) {
        flat.use()
        GLES30.glUniformMatrix4fv(flat.uniform("uViewProjection"), 1, false, viewProjection, 0)
        GLES30.glUniformMatrix4fv(flat.uniform("uModel"), 1, false, model, 0)
        GLES30.glUniform4fv(flat.uniform("uColor"), 1, color, 0)
        draw()
    }

    private fun gpuOf(id: String): List<PartGpu> = parts.getOrPut(id) {
        PartVisuals.meshes(id).map { pm ->
            PartGpu(
                GpuMesh(pm.mesh.positions, pm.mesh.normals, pm.mesh.indices),
                pm.edges?.let { GpuLines(it) },
                pm.lines.map { GpuMesh(it.positions, FloatArray(0), it.indices) },
            )
        }
    }

    private companion object {
        val IDENTITY = Mat4.IDENTITY.toFloatArray()

        // 色は Web版の材質の値(0xRRGGBB を 0〜1 に)
        val PAPER = rgb(0xe6e4df)
        val BASE = rgb(0xdcd8cf)
        val MARK = rgb(0xb8b3a6)
        val HOT = rgb(0xf0b29b)
        val HOT_EMISSIVE = rgb(0x3d1409)
        val MASK = rgb(0xf3f0e8)
        val GHOST = rgb(0xa39e92)
        val BLACK = rgb(0x000000)
        val EDGE = rgba(0x58534a)
        val LINE = rgba(0xb02a2a)
        val GRID = rgba(0xcdcac1)
        val GRID_CENTER = rgba(0xb7b3a8)
        val ROM = rgba(0x1f9d75, 0.34f)
        val ROM_LINE = rgba(0x0f7a58)
        val DOT = rgba(0xd9481f)
        val RING = rgba(0xffffff)

        fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xff) / 255f, ((c shr 8) and 0xff) / 255f, (c and 0xff) / 255f)
        fun rgba(c: Int, a: Float = 1f) = rgb(c) + a
    }
}
