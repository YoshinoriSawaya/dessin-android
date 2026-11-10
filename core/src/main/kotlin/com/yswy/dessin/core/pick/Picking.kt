package com.yswy.dessin.core.pick

import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.Mesh
import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.mesh.Primitives
import com.yswy.dessin.core.mesh.dotWorldRadius
import com.yswy.dessin.core.mesh.guideMesh
import com.yswy.dessin.core.mesh.meshWorldMatrix
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.render.MeshStyle
import com.yswy.dessin.core.render.drawPlan
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton

/**
 * 画面を押したときに当たったもの(Web版の pointerdown)。
 * @property part 押した部位。選択中の部位の緑の範囲に当たっていて、当たった部位がその親なら null(親は選ばない)
 * @property romPoint 選択中の部位の緑の範囲に当たった点(ワールド)
 */
data class PressPick(val part: String?, val romPoint: Vec3?)

/**
 * レイと三角形の交点(three.js r128 の Ray.intersectTriangle)。
 * backfaceCulling なら裏(a→b→c が時計回りに見える側)からは当たらない。当たらなければ null。
 */
fun intersectTriangle(ray: Ray, a: Vec3, b: Vec3, c: Vec3, backfaceCulling: Boolean): Vec3? {
    val edge1 = b - a
    val edge2 = c - a
    val normal = edge1.cross(edge2)
    var dDotN = ray.direction.dot(normal)
    val sign = when {
        dDotN > 0 -> if (backfaceCulling) return null else 1.0
        dDotN < 0 -> { dDotN = -dDotN; -1.0 }
        else -> return null
    }
    val diff = ray.origin - a
    val b1 = sign * ray.direction.dot(diff.cross(edge2))
    if (b1 < 0) return null
    val b2 = sign * ray.direction.dot(edge1.cross(diff))
    if (b2 < 0) return null
    if (b1 + b2 > dDotN) return null
    val qDotN = -sign * diff.dot(normal)
    if (qDotN < 0) return null
    return ray.at(qDotN / dDotN)
}

/** 押した部位(Web版の pickPart)。選択中の部位の玉に当たれば、手前に何があってもその部位 */
fun pickPart(skeleton: Skeleton, selected: String, options: DisplayOptions, ray: Ray): String? {
    if (pickDot(skeleton, selected, options, ray) != null) return selected
    var best: String? = null
    var bestDistance = Double.MAX_VALUE
    drawPlan(skeleton, selected, options).meshes.forEach { d ->
        val mesh = PartVisuals.meshes(d.partId)[d.meshIndex].mesh
        // 顔の面だけ両面。選択中の色(片面)やゴーストのときは片面
        val hit = nearestHit(mesh, meshWorldMatrix(skeleton, d.partId, d.meshIndex), ray, doubleSided = d.style == MeshStyle.MASK)
        if (hit != null && hit.distance < bestDistance) {
            bestDistance = hit.distance
            best = d.partId
        }
    }
    return best
}

/** 選択中の部位の玉に当たった点。玉を出していなければ null */
fun pickDot(skeleton: Skeleton, selected: String, options: DisplayOptions, ray: Ray): Vec3? {
    if (drawPlan(skeleton, selected, options).guidePart != selected) return null
    val dot = skeleton.dotWorld(selected)
    val parent = skeleton.worldTransforms().getValue(PartSpecs[selected].parent!!)
    // 玉は可動域の表示の中にあるので、親の向きに回っている。大きさは視点(レイの始点)からの距離で決まる
    val model = Mat4.compose(dot, parent.rotation, dotWorldRadius(ray.origin.distanceTo(dot)))
    return nearestHit(DOT_SPHERE, model, ray, doubleSided = false)?.point
}

/** 選択中の部位の緑の範囲に当たった、最も手前の点(Web版の romPoint)。範囲を出していなければ null */
fun romPoint(skeleton: Skeleton, selected: String, options: DisplayOptions, ray: Ray): Vec3? {
    if (drawPlan(skeleton, selected, options).guidePart != selected) return null
    val spec = PartSpecs[selected]
    val parent = skeleton.worldTransforms().getValue(spec.parent!!)
    val area = guideAreas.getOrPut(selected) { guideMesh(spec).area }
    val model = Mat4.compose(skeleton.guideCenter(selected), parent.rotation, parent.scale)
    return nearestHit(area, model, ray, doubleSided = true)?.point
}

/** 押したときの判定(Web版の pointerdown の pickPart・romPoint と、親を選ばない規則) */
fun pickAtPress(skeleton: Skeleton, selected: String, options: DisplayOptions, ray: Ray): PressPick {
    var part = pickPart(skeleton, selected, options, ray)
    val rom = romPoint(skeleton, selected, options, ray)
    if (rom != null && part != null && part == PartSpecs[selected].parent) part = null
    return PressPick(part, rom)
}

private class Hit(val point: Vec3, val distance: Double)

/** メッシュをワールドに置いたときの、レイとの最も手前の交点 */
private fun nearestHit(mesh: Mesh, model: Mat4, ray: Ray, doubleSided: Boolean): Hit? {
    val world = Array(mesh.vertexCount) { model.transformPoint(mesh.vertex(it)) }
    var best: Hit? = null
    val idx = mesh.indices
    for (t in idx.indices step 3) {
        val p = intersectTriangle(ray, world[idx[t]], world[idx[t + 1]], world[idx[t + 2]], backfaceCulling = !doubleSided) ?: continue
        val distance = ray.origin.distanceTo(p)
        if (best == null || distance < best.distance) best = Hit(p, distance)
    }
    return best
}

/** 玉の形(Web版の dotGeo: 半径1の球、16×12 分割) */
private val DOT_SPHERE = Primitives.sphere(1.0, 16, 12)

private val guideAreas = java.util.concurrent.ConcurrentHashMap<String, Mesh>()
