package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Mat4
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
 * hands は手の形(E08-02)。既定(パー)と違う手だけを持つ(等しさの比較を保存の前後で揃えるため)
 * sticks は棒の形(長さと握る位置。E18-01)。手の形と同じく既定と違うものだけを持つ
 * anchor は操作の根(E10-01)。データはいつも胸を根に持ち、骨盤が根のときは脇腹・骨盤の状態を変えても骨盤が止まるよう
 * 胸の置き方を補正する。そのときの胸・脇腹は骨盤から逆にたどって動かす(E16-01。guideJoint)。ポーズには保存しない
 */
data class Skeleton(
    val root: RootTransform,
    val states: Map<String, PartState>,
    val placed: Set<String>,
    val body: BodyParams,
    val hands: Map<String, HandShape> = emptyMap(),
    val sticks: Map<String, StickShape> = emptyMap(),
    val anchor: Anchor = Anchor.CHEST,
) {
    /** 全関節のワールド変換。親から順に合成する */
    private val world: Map<String, JointTransform> by lazy {
        val out = LinkedHashMap<String, JointTransform>()
        PartSpecs.all.forEach { spec ->
            out[spec.id] = if (spec.isRoot) {
                // 身長は人形全体の倍率で、胸の大きさと掛け合わさる(E07-02)
                JointTransform(root.position, root.rotation, root.scale * body.height)
            } else {
                val parent = out.getValue(spec.parent!!)
                val st = state(spec.id)
                val local = localRotation(spec, st)
                // 回転の中心が関節からずれている部位(ひざ)は、中心のまわりに回した分だけ関節が動く(滑り。E06-02)。
                // さらに転がりの分だけ、すねの前向き(ひねりを除いた曲げの向き)へずれる(E17-02)
                val pivot = pivotOf(spec, body)
                val rolling = rollingShift(spec, body, st.phi)
                val roll = if (rolling == 0.0) Vec3.ZERO else localRotation(spec, st.copy(roll = 0.0)).rotate(Vec3(0.0, 0.0, rolling))
                val joint = attachOf(spec, body) + pivot + local.rotate(-pivot) + roll
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

    /** 部位を子孫ごと外し、休止状態に戻す(Web版の removePart)。体型は変えない(E07-03。Web版は頭の大きさを戻していた) */
    fun remove(id: String): Skeleton {
        val spec = PartSpecs[id]
        if (spec.isRoot) return this
        val removed = subtree(id)
        val resetStates = removed.associateWith { PartState.rest(PartSpecs[it]) }
        // 手を外したら手の形も既定(パー)に戻す(E08-02)
        return copy(placed = placed - removed.toSet(), states = states + resetStates, hands = hands - removed.toSet(), sticks = sticks - removed.toSet())
    }

    /** 向きを戻す。胸は位置・回転・大きさを初期値に戻す(Web版の resetPart)。骨盤から逆にたどる部位は、動かす関節を戻す */
    fun reset(id: String): Skeleton {
        val j = guideJoint(id)
        return if (PartSpecs[j].isRoot) copy(root = RootTransform()) else withState(j, PartState.rest(PartSpecs[j]))
    }

    /**
     * 部位の状態を変える。骨盤が根で、胸と骨盤の間の関節(脇腹・骨盤の状態)を変えたときは、
     * 骨盤が止まるよう胸の置き方を補正する
     */
    fun withState(id: String, state: PartState): Skeleton {
        val changed = copy(states = states + (id to state))
        return if (pelvisAnchored && id in JOINTS_TO_PELVIS) changed.keepingPelvisOf(this) else changed
    }

    fun withAnchor(anchor: Anchor): Skeleton = copy(anchor = anchor)

    /** 骨盤を根にしていて骨盤を置いている */
    private val pelvisAnchored: Boolean get() = anchor == Anchor.PELVIS && isPlaced(PELVIS)

    /**
     * 骨盤が根のときに、骨盤から逆にたどって動かす部位(胸・脇腹。E16-01)。
     * 自分と骨盤の側の隣との間の関節(guideJoint)で動き、骨盤の側は止まったまま、自分と胸の側が動く
     */
    fun isReversed(id: String): Boolean = pelvisAnchored && id in REVERSED_JOINT

    /**
     * 選んだ部位を動かすときに状態を変える関節(可動域の表示もこの関節のもの)。ふつうは部位自身。
     * 骨盤が根のときは、胸はみぞおち(脇腹の関節)、脇腹は骨盤との間(骨盤の関節)で動かす(E16-01)
     */
    fun guideJoint(id: String): String = if (isReversed(id)) REVERSED_JOINT.getValue(id) else id

    /**
     * 骨盤が根で、id が骨盤(E14-04)。胸が根のときの胸と同じく、操作で人形全体を動かす・回す
     * (ドラッグで位置、回転オーブ・傾きのスライダーで骨盤の関節を中心に回す)
     */
    fun isAnchorRoot(id: String): Boolean = pelvisAnchored && id == PELVIS

    /** 人形全体をワールドの点 center を中心に rot だけ回す。部位の状態(体の形)は変えない */
    fun rotatedAbout(center: Vec3, rot: Quat): Skeleton = copy(
        root = root.copy(position = center + rot.rotate(root.position - center), rotation = (rot * root.rotation).normalized()),
    )

    /** 人形全体を delta だけ動かす */
    fun movedBy(delta: Vec3): Skeleton = copy(root = root.copy(position = root.position + delta))

    /** 骨盤のワールドの位置と向きが before と同じになるよう、胸の置き方を回して動かす */
    private fun keepingPelvisOf(before: Skeleton): Skeleton {
        val p0 = before.worldTransforms().getValue(PELVIS)
        val p1 = worldTransforms().getValue(PELVIS)
        val dq = (p0.rotation * p1.rotation.inverse()).normalized()
        val position = p0.position - dq.rotate(p1.position - root.position)
        return copy(root = root.copy(position = position, rotation = (dq * root.rotation).normalized()))
    }

    fun withRoot(root: RootTransform): Skeleton = copy(root = root)

    fun withBody(body: BodyParams): Skeleton = copy(body = body)

    /** 手の形(E08-02)。手でない部位は既定 */
    fun handShape(id: String): HandShape = hands[id] ?: HandShape.DEFAULT

    fun withHandShape(id: String, shape: HandShape): Skeleton {
        require(isHand(id)) { "$id は手ではない" }
        return copy(hands = if (shape == HandShape.DEFAULT) hands - id else hands + (id to shape))
    }

    /** 棒の形(E18-01)。棒でない部位は既定 */
    fun stickShape(id: String): StickShape = sticks[id] ?: StickShape.DEFAULT

    /** 棒の形を変える。範囲に収める */
    fun withStickShape(id: String, shape: StickShape): Skeleton {
        require(isStick(id)) { "$id は棒ではない" }
        val s = shape.clamped()
        return copy(sticks = if (s == StickShape.DEFAULT) sticks - id else sticks + (id to s))
    }

    fun worldTransforms(): Map<String, JointTransform> = world

    /** 可動域の表示の中心(動かす関節のワールド位置) */
    fun guideCenter(id: String): Vec3 = world.getValue(guideJoint(id)).position

    /**
     * 可動域の表示(緑の範囲)の置き方。ふつうは親の向きに置く。
     * 骨盤から逆にたどる部位(isReversed)は、動かす関節の部位の向き(骨盤の側で止まっている)に置き、上下を反転する。
     * こうすると範囲は自分の上向きが取れる範囲になり、玉(自分の上向き)はその上に乗る(E10-01・E16-01)
     */
    fun guideMatrix(id: String): Mat4 {
        val s = guideScale(id)
        return if (isReversed(id)) {
            Mat4.compose(guideCenter(id), world.getValue(guideJoint(id)).rotation, Vec3(s, -s, s))
        } else {
            Mat4.compose(guideCenter(id), parentOf(id).rotation, s)
        }
    }

    /**
     * 可動域の表示(緑の範囲・玉)の拡大率。親の拡大率と、部位自身の長さの倍率(E07-02)が効く。
     * 部位自身の拡大率(頭の大きさ)は効かない
     */
    fun guideScale(id: String): Double = guideJoint(id).let { j -> parentOf(j).scale * lengthScale(PartSpecs[j], body) }

    /** 可動域の表示の半径 */
    fun guideRadius(id: String): Double = PartSpecs[guideJoint(id)].guideRadius * guideScale(id)

    /** 玉のワールド位置。骨盤から逆にたどる部位は、自分の上向き(E10-01・E16-01) */
    fun dotWorld(id: String): Vec3 {
        if (isReversed(id)) return guideCenter(id) + world.getValue(id).rotation.rotate(Vec3.Y) * guideRadius(id)
        return guideCenter(id) + parentOf(id).rotation.rotate(dotPosition(PartSpecs[id], state(id)) * guideScale(id))
    }

    /** ワールドの方向へ向ける。可動域に収める(Web版の aimPart) */
    fun aim(id: String, worldDir: Vec3): Skeleton {
        val j = guideJoint(id)
        val spec = PartSpecs[j]
        if (spec.isRoot) return this
        val dirInParent = if (isReversed(id)) {
            // 骨盤から逆にたどる部位は、自分の上向きとして受け取る。関節の部位 j(骨盤の側)は止まっているので、
            // j から見た自分の上向き v になるような j の回転を求め、その回転で j の向きを決める
            val v = world.getValue(j).rotation.inverse().rotate(worldDir.normalized())
            Quat.fromUnitVectors(v, Vec3.Y).rotate(spec.restDir)
        } else {
            parentOf(j).rotation.inverse().rotate(worldDir)
        }
        return withState(j, aimPart(spec, state(j), dirInParent))
    }

    /** ワールドの点へ向ける(Web版の aimAtPoint。緑の範囲のダブルタップ) */
    fun aimAtPoint(id: String, point: Vec3): Skeleton {
        val d = point - guideCenter(id)
        if (d.lengthSq() < 1e-10) return this
        return aim(id, d)
    }

    /** 指のレイで向ける(Web版の dragPart) */
    fun drag(id: String, ray: Ray): Skeleton {
        if (PartSpecs[guideJoint(id)].isRoot) return this
        val dir = dragDirection(ray, guideCenter(id), guideRadius(id), dotWorld(id)) ?: return this
        return aim(id, dir)
    }

    /** 回転オーブで、ワールドの回転を加える(Web版の sphereRotate) */
    fun orb(id: String, rotInWorld: Quat): Skeleton {
        val j = guideJoint(id)
        val spec = PartSpecs[j]
        if (spec.isRoot) return withRoot(root.copy(rotation = orbRotateRoot(root.rotation, rotInWorld)))
        // 骨盤が根のときの骨盤は、骨盤の関節を中心に全体を回す(E14-04)
        if (isAnchorRoot(id)) return rotatedAbout(world.getValue(id).position, rotInWorld)
        // 骨盤から逆にたどる部位は、自分と胸の側をその向きに回す(関節に逆の回転を加えると、補正で胸の側が回る)
        val rot = if (isReversed(id)) rotInWorld.inverse() else rotInWorld
        val pq = parentOf(j).rotation
        val rotInParent = pq.inverse() * rot * pq
        return withState(j, orbRotate(spec, state(j), rotInParent))
    }

    private fun parentOf(id: String): JointTransform =
        world.getValue(requireNotNull(PartSpecs[id].parent) { "$id は親を持たない" })

    private fun subtree(id: String): List<String> = listOf(id) + PartSpecs.children(id).flatMap { subtree(it.id) }

    companion object {
        private const val PELVIS = "pelvis"
        /** 骨盤が根のとき、骨盤から逆にたどって動かす部位と、そのとき動かす関節(E16-01) */
        private val REVERSED_JOINT = mapOf("rib" to "flank", "flank" to PELVIS)
        /** 胸から骨盤までの関節。骨盤が根のときに変えたら骨盤が止まるよう補正する */
        private val JOINTS_TO_PELVIS = REVERSED_JOINT.values.toSet()

        /** 胸だけが置かれ、ほかの部位は休止状態 */
        fun initial(): Skeleton = Skeleton(
            root = RootTransform(),
            states = PartSpecs.all.filterNot { it.isRoot }.associate { it.id to PartState.rest(it) },
            placed = setOf(PartSpecs.all.first { it.isRoot }.id),
            body = BodyParams(),
        )
    }
}

/** 操作の根(E10-01) */
enum class Anchor(val label: String) {
    CHEST("胸"),
    PELVIS("骨盤"),
}
