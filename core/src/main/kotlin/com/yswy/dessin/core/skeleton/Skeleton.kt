package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3

/** 胸(根)の置き方。回転は四元数で持ち、スライダー用のオイラー角は Quat.toEulerYXZ で得る */
data class RootTransform(
    val position: Vec3 = INITIAL_POSITION,
    val rotation: Quat = Quat.IDENTITY,
    val scale: Double = 1.0,
) {
    companion object {
        val INITIAL_POSITION = Vec3(0.0, 1.10, 0.0)
        val SCALE_RANGE = 0.3..1.6
    }
}

/** 関節のワールドでの位置・回転・拡大率(拡大は等方) */
data class JointTransform(val position: Vec3, val rotation: Quat, val scale: Double) {
    /** この関節の空間の点をワールドに直す */
    fun toWorld(local: Vec3): Vec3 = position + rotation.rotate(local * scale)
}

/**
 * 人形全体の状態。不変で、操作は新しい Skeleton を返す。
 * 部位の id は PartSpecs と同じ。置いていない部位も状態と変換を持つ(半透明の表示に使う)。
 */
data class Skeleton(
    val root: RootTransform,
    val states: Map<String, PartState>,
    val placed: Set<String>,
    val body: BodyParams,
) {
    /** 全関節のワールド変換。親から順に合成する */
    private val world: Map<String, JointTransform> by lazy {
        val out = LinkedHashMap<String, JointTransform>()
        PartSpecs.all.forEach { spec ->
            out[spec.id] = if (spec.isRoot) {
                JointTransform(root.position, root.rotation, root.scale)
            } else {
                val parent = out.getValue(spec.parent!!)
                val local = localRotation(spec, state(spec.id))
                // 回転の中心が関節からずれている部位(ひざ)は、中心のまわりに回した分だけ関節が動く
                val joint = attachOf(spec, body) + spec.pivot + local.rotate(-spec.pivot)
                JointTransform(
                    parent.toWorld(joint),
                    parent.rotation * local,
                    parent.scale * jointScale(spec, body),
                )
            }
        }
        out
    }

    fun state(id: String): PartState = states.getValue(id)

    fun isPlaced(id: String): Boolean = id in placed

    /** 置ける部位: 根か、親が置かれている部位(Web版の available) */
    fun isAvailable(id: String): Boolean = PartSpecs[id].parent?.let { it in placed } ?: true

    /** 部位を置く。置けなければ何もしない。初めて置くときは休止状態から始める(Web版の select) */
    fun place(id: String): Skeleton {
        if (isPlaced(id) || !isAvailable(id)) return this
        return copy(placed = placed + id, states = states + (id to PartState.rest(PartSpecs[id])))
    }

    /** 部位を子孫ごと外し、休止状態に戻す。頭を外すと頭の大きさも戻す(Web版の removePart) */
    fun remove(id: String): Skeleton {
        val spec = PartSpecs[id]
        if (spec.isRoot) return this
        val removed = subtree(id)
        val resetStates = removed.associateWith { PartState.rest(PartSpecs[it]) }
        val headRemoved = removed.any { PartSpecs[it].adjust == Adjust.HEAD_SIZE }
        return copy(
            placed = placed - removed.toSet(),
            states = states + resetStates,
            body = if (headRemoved) body.copy(headSize = BodyParams().headSize) else body,
        )
    }

    /** 向きを戻す。胸は位置・回転・大きさを初期値に戻す(Web版の resetPart) */
    fun reset(id: String): Skeleton {
        val spec = PartSpecs[id]
        return if (spec.isRoot) copy(root = RootTransform()) else withState(id, PartState.rest(spec))
    }

    fun withState(id: String, state: PartState): Skeleton = copy(states = states + (id to state))

    fun withRoot(root: RootTransform): Skeleton = copy(root = root)

    fun withBody(body: BodyParams): Skeleton = copy(body = body)

    fun worldTransforms(): Map<String, JointTransform> = world

    /** 可動域の表示の中心(関節のワールド位置) */
    fun guideCenter(id: String): Vec3 = world.getValue(id).position

    /** 可動域の表示の半径。親の拡大率が効く(部位自身の拡大率は効かない) */
    fun guideRadius(id: String): Double = PartSpecs[id].guideRadius * parentOf(id).scale

    /** 玉のワールド位置 */
    fun dotWorld(id: String): Vec3 {
        val spec = PartSpecs[id]
        val parent = parentOf(id)
        return guideCenter(id) + parent.rotation.rotate(dotPosition(spec, state(id)) * parent.scale)
    }

    /** ワールドの方向へ向ける。可動域に収める(Web版の aimPart) */
    fun aim(id: String, worldDir: Vec3): Skeleton {
        val spec = PartSpecs[id]
        if (spec.isRoot) return this
        val dirInParent = parentOf(id).rotation.inverse().rotate(worldDir)
        return withState(id, aimPart(spec, state(id), dirInParent))
    }

    /** ワールドの点へ向ける(Web版の aimAtPoint。緑の範囲のダブルタップ) */
    fun aimAtPoint(id: String, point: Vec3): Skeleton {
        val d = point - guideCenter(id)
        if (d.lengthSq() < 1e-10) return this
        return aim(id, d)
    }

    /** 指のレイで向ける(Web版の dragPart) */
    fun drag(id: String, ray: Ray): Skeleton {
        if (PartSpecs[id].isRoot) return this
        val dir = dragDirection(ray, guideCenter(id), guideRadius(id), dotWorld(id)) ?: return this
        return aim(id, dir)
    }

    /** 回転オーブで、ワールドの回転を加える(Web版の sphereRotate) */
    fun orb(id: String, rotInWorld: Quat): Skeleton {
        val spec = PartSpecs[id]
        if (spec.isRoot) return withRoot(root.copy(rotation = orbRotateRoot(root.rotation, rotInWorld)))
        val pq = parentOf(id).rotation
        val rotInParent = pq.inverse() * rotInWorld * pq
        return withState(id, orbRotate(spec, state(id), rotInParent))
    }

    private fun parentOf(id: String): JointTransform =
        world.getValue(requireNotNull(PartSpecs[id].parent) { "$id は親を持たない" })

    private fun subtree(id: String): List<String> = listOf(id) + PartSpecs.children(id).flatMap { subtree(it.id) }

    companion object {
        /** 胸だけが置かれ、ほかの部位は休止状態 */
        fun initial(): Skeleton = Skeleton(
            root = RootTransform(),
            states = PartSpecs.all.filterNot { it.isRoot }.associate { it.id to PartState.rest(it) },
            placed = setOf(PartSpecs.all.first { it.isRoot }.id),
            body = BodyParams(),
        )
    }
}
