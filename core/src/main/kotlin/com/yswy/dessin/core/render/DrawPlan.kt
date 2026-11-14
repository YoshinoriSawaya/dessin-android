package com.yswy.dessin.core.render

import com.yswy.dessin.core.mesh.MeshKind
import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton

/** 表示の切替(Web版の「ガイド」「正中線」「関節の玉」「格子」) */
data class DisplayOptions(
    val guides: Boolean = true,
    val lines: Boolean = true,
    val markers: Boolean = true,
    val grid: Boolean = true,
)

/** メッシュの描き方(Web版の材質) */
enum class MeshStyle {
    /** 置いた部位の本体 */
    BASE,
    /** 選択中の部位 */
    HOT,
    /** 関節の玉 */
    MARKER,
    /** 顔の面 */
    MASK,
    /** 置ける部位の半透明の影 */
    GHOST,
}

/** メッシュ1つの描き方。edges は輪郭線、lines は正中線などの管を描くか */
data class MeshDraw(val partId: String, val meshIndex: Int, val style: MeshStyle, val edges: Boolean, val lines: Boolean)

/**
 * 1回の描画で描くもの。guidePart は可動域と玉を出す部位(出さなければ null)。
 * 並びは PartSpecs の順で、描く順序(不透明 → 半透明 → 可動域)は描画層が決める。
 */
data class DrawPlan(val meshes: List<MeshDraw>, val guidePart: String?)

/** 何をどう描くかを決める(Web版の refresh) */
fun drawPlan(skeleton: Skeleton, selected: String, options: DisplayOptions): DrawPlan {
    val meshes = ArrayList<MeshDraw>()
    PartSpecs.all.forEach { spec ->
        val id = spec.id
        PartVisuals.meshes(id).forEachIndexed { i, pm ->
            // 手は今の形のメッシュだけを描く(E08-02)
            if (pm.handShape != null && pm.handShape != skeleton.handShape(id)) return@forEachIndexed
            when {
                skeleton.isPlaced(id) -> {
                    if (pm.kind == MeshKind.MARKER && !options.markers) return@forEachIndexed
                    val style = when {
                        id == selected -> MeshStyle.HOT
                        pm.kind == MeshKind.MARKER -> MeshStyle.MARKER
                        pm.kind == MeshKind.MASK -> MeshStyle.MASK
                        else -> MeshStyle.BASE
                    }
                    meshes += MeshDraw(id, i, style, edges = pm.edges != null, lines = options.lines && pm.lines.isNotEmpty())
                }
                skeleton.isAvailable(id) && options.guides && pm.kind != MeshKind.MARKER ->
                    meshes += MeshDraw(id, i, MeshStyle.GHOST, edges = false, lines = false)
            }
        }
    }
    val guide = selected.takeIf { options.guides && skeleton.isPlaced(it) && !PartSpecs[it].isRoot }
    return DrawPlan(meshes, guide)
}
