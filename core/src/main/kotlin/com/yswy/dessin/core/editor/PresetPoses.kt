package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.RootTransform
import com.yswy.dessin.core.skeleton.Skeleton
import com.yswy.dessin.core.skeleton.isStick
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

private const val D2R = PI / 180

/**
 * プリセットのポーズ(E19-01)。
 * @property tiltDeg 胸の前傾(度。前が +)
 */
enum class PresetPose(val label: String, val tiltDeg: Double) {
    STAND("立つ", 0.0),
    WALK("歩く", 0.0),
    RUN("走る", 10.0),
    SIT("座る", 0.0),
    SQUAT("しゃがむ", 20.0),
    HUG_KNEES("体育座り", 0.0),
    CROSS_LEGGED("あぐら", 5.0),
    RAISE_HAND("手を挙げる", 0.0),
    ARMS_OUT("腕を広げる", 0.0),
}

/** 胸の前傾(ラジアン。胸の回転をオイラー角 YXZ に分けたときの x) */
fun presetTilt(p: PresetPose): Double = p.tiltDeg * D2R

private fun v(x: Double, y: Double, z: Double) = Vec3(x, y, z).normalized()

/** 左の向きを右へ写す(x を反転) */
private fun mirror(d: Vec3) = Vec3(-d.x, d.y, d.z)

/**
 * 各部位の狙いの向き(ワールド。人形は +z を向き、左は +x)。部位の向きは関節から子の側へ向かう向き(休止方向を回したもの)。
 * 胴(首・脇腹・骨盤・付け根)は自然な姿勢(E09)の向きを胸の前傾の分だけ回し、頭はまっすぐ上。書いていない部位は自然な姿勢のまま
 */
fun presetTargets(p: PresetPose): Map<String, Vec3> {
    val tilt = Quat.fromAxisAngle(Vec3.X, presetTilt(p))
    val out = LinkedHashMap<String, Vec3>()
    out["neck"] = tilt.rotate(v(0.0, cos(15 * D2R), sin(15 * D2R)))
    out["head"] = v(0.0, 1.0, 0.1)
    out["flank"] = tilt.rotate(v(0.0, -cos(10 * D2R), -sin(10 * D2R)))
    out["pelvis"] = tilt.rotate(v(0.0, -1.0, 0.0))
    for ((s, sg) in listOf("L" to 1.0, "R" to -1.0)) {
        out["hip$s"] = tilt.rotate(v(sg * cos(20 * D2R), -sin(20 * D2R), 0.0))
    }
    // 左右同じ(右は鏡像)
    fun both(part: String, left: Vec3) {
        out[part + "L"] = left
        out[part + "R"] = mirror(left)
    }
    // 左右で違う
    fun each(part: String, left: Vec3, right: Vec3) {
        out[part + "L"] = left
        out[part + "R"] = right
    }
    // 立ったときの脚と、下ろした腕
    fun standingLegs() {
        both("thigh", v(sin(5 * D2R), -cos(5 * D2R), 0.0))
        both("shin", v(sin(5 * D2R), -cos(5 * D2R), 0.0))
        both("foot", v(0.0, 0.0, 1.0))
    }
    fun relaxedArms() {
        both("upperArm", v(0.15, -1.0, 0.0))
        both("forearm", v(0.08, -1.0, 0.2))
        both("hand", v(0.05, -1.0, 0.15))
    }
    when (p) {
        PresetPose.STAND -> {
            standingLegs()
            relaxedArms()
        }
        PresetPose.WALK -> {
            // 左脚が前(かかとから着く)、右脚は後ろで蹴り出す。腕は脚と逆
            each("thigh", v(0.03, -0.94, 0.34), v(-0.03, -0.96, -0.28))
            each("shin", v(0.0, -0.97, 0.24), v(0.0, -0.82, -0.57))
            each("foot", v(0.0, 0.25, 1.0), v(0.0, -0.35, 1.0))
            each("upperArm", v(0.1, -0.95, -0.3), v(-0.1, -0.95, 0.32))
            each("forearm", v(0.08, -1.0, 0.0), v(-0.05, -0.75, 0.66))
        }
        PresetPose.RUN -> {
            // 左ももを高く上げ、右脚は後ろへ蹴る。肘は曲げて振る
            each("thigh", v(0.05, -0.6, 0.8), v(-0.05, -0.88, -0.45))
            each("shin", v(0.0, -0.95, -0.2), v(0.0, -0.35, -0.94))
            out["footL"] = v(0.0, -0.2, 1.0)
            each("upperArm", v(0.1, -0.75, -0.65), v(-0.1, -0.7, 0.7))
            each("forearm", v(0.05, -0.2, 1.0), v(-0.05, 0.75, 0.65))
        }
        PresetPose.SIT -> {
            // 椅子に座る: ももは水平、すねは真下。手はももの上
            both("thigh", v(0.12, -0.05, 1.0))
            both("shin", v(0.03, -1.0, 0.05))
            both("foot", v(0.0, 0.0, 1.0))
            both("upperArm", v(0.12, -1.0, 0.15))
            both("forearm", v(0.08, -0.35, 1.0))
            both("hand", v(0.05, -0.3, 1.0))
        }
        PresetPose.SQUAT -> {
            // しゃがみ込む: ひざを開いて深く曲げ、腕はひざの上へ
            both("thigh", v(0.25, -0.05, 0.97))
            both("shin", v(0.1, -0.85, -0.5))
            both("foot", v(0.1, -0.15, 1.0))
            both("upperArm", v(0.2, -0.6, 0.75))
            both("forearm", v(0.05, -0.5, 0.85))
        }
        PresetPose.HUG_KNEES -> {
            // 体育座り: ひざを立てて胸に寄せ、腕ですねを抱える
            both("thigh", v(0.1, 0.3, 0.95))
            both("shin", v(0.05, -0.9, 0.4))
            both("foot", v(0.0, -0.1, 1.0))
            both("upperArm", v(0.2, -0.45, 0.87))
            both("forearm", v(-0.5, -0.3, 0.8))
        }
        PresetPose.CROSS_LEGGED -> {
            // あぐら: ももを外へ開いて寝かせ、すねを内へ交差させる。手はひざの上
            both("thigh", v(0.6, -0.35, 0.72))
            both("shin", v(-0.72, -0.45, 0.53))
            both("upperArm", v(0.25, -0.9, 0.35))
            both("forearm", v(0.3, -0.6, 0.75))
        }
        PresetPose.RAISE_HAND -> {
            standingLegs()
            relaxedArms()
            out["upperArmR"] = v(-0.1, 1.0, 0.05)
            out["forearmR"] = v(-0.05, 1.0, 0.05)
            out["handR"] = v(0.0, 1.0, 0.0)
        }
        PresetPose.ARMS_OUT -> {
            standingLegs()
            both("upperArm", v(1.0, 0.0, 0.0))
            both("forearm", v(1.0, 0.0, 0.0))
            both("hand", v(1.0, 0.0, 0.0))
        }
    }
    return out
}

/**
 * ひねりを探すときの重み。ひねりは子の向きが十分に良くなるときだけ使う
 * (重みが小さいと、立ち姿勢でもすねを数度寄せるためにももを大きくひねってしまう)
 */
private const val ROLL_WEIGHT = 0.1

private fun angle(a: Vec3, b: Vec3) = acos(a.normalized().dot(b.normalized()).coerceIn(-1.0, 1.0))

/** 部位のワールドの向き */
private fun Skeleton.dirOf(id: String) = worldTransforms().getValue(id).rotation.rotate(PartSpecs[id].restDir)

/**
 * プリセットのポーズの骨格(胸は原点の高さのまま、向きは前傾だけ)。棒以外の全部位を親から順に置き、自然な姿勢から狙いへ向ける。
 * 子がヒンジ(前腕・すね)で狙いを持つ部位は、子の向きが狙いに最も近くなるひねりを探す(同じなら小さいひねり)
 */
fun presetSkeleton(p: PresetPose): Skeleton {
    val targets = presetTargets(p)
    var s = Skeleton.initial().withRoot(RootTransform(rotation = Quat.fromAxisAngle(Vec3.X, presetTilt(p))))
    for (spec in PartSpecs.all) {
        if (spec.isRoot || isStick(spec.id)) continue
        s = s.place(spec.id).withState(spec.id, NaturalPose.state(spec))
        val target = targets[spec.id] ?: continue
        s = s.aim(spec.id, target)
        val child = PartSpecs.children(spec.id).firstOrNull { it.hinge != null && it.id in targets } ?: continue
        val childTarget = targets.getValue(child.id)
        val range = spec.roll
        val steps = 36
        var best = s
        var bestErr = Double.MAX_VALUE
        for (i in 0..steps) {
            val r = range.min + (range.max - range.min) * i / steps
            val trial = s.withState(spec.id, s.state(spec.id).copy(roll = r))
            val err = angle(trial.place(child.id).aim(child.id, childTarget).dirOf(child.id), childTarget) + ROLL_WEIGHT * abs(r)
            if (err < bestErr) {
                bestErr = err
                best = trial
            }
        }
        s = best
    }
    return s
}

/**
 * 操作中の人形にプリセットのポーズを当てる(E19-01)。棒以外の全部位を置いてその姿勢にし、
 * 体型・横の位置・床の上での向き(胸の向きの鉛直軸まわり)・大きさ・棒(置いたか・状態・形)・手の形・根は保つ。
 * 胸の前傾はポーズのものにし、いちばん低い点が床(y = 0)に付くよう高さを合わせる。選んでいる部位は保つ
 */
fun EditorState.applyPreset(p: PresetPose): EditorState {
    val cur = skeleton
    val pose = presetSkeleton(p)
    val yaw = cur.root.rotation.toEulerYXZ().y
    val rotation = (Quat.fromAxisAngle(Vec3.Y, yaw) * Quat.fromAxisAngle(Vec3.X, presetTilt(p))).normalized()
    val posed = cur.copy(
        states = cur.states + pose.states.filterKeys { !isStick(it) },
        placed = pose.placed + cur.placed.filter { isStick(it) },
        root = cur.root.copy(rotation = rotation),
    )
    val lowest = fitBounds(posed)?.first?.y ?: 0.0
    val grounded = posed.movedBy(Vec3(0.0, -lowest, 0.0))
    return copy(skeleton = grounded, selected = if (grounded.isPlaced(selected)) selected else "rib")
}
