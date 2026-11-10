package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** オーブの大きさ(dp)。Web版の ORB = 88 */
const val ORB_SIZE = 88.0

/** オーブの球の半径(dp)。縁の 7 を除く */
const val ORB_RADIUS = ORB_SIZE / 2 - 7

/**
 * 回転オーブのドラッグ(Web版の orb の pointerdown・pointermove)。座標はオーブの中心を原点とする dp(下が +y)。
 * 中央(半径の 78% まで)で押すと、ドラッグの縦横に応じて視点の横軸・縦軸まわりに傾ける。
 * 外周で押すと、中心のまわりの角度の変化だけ視線まわりにひねる。返すのはワールドでの回転。
 */
class OrbGesture {
    private var active = false
    private var rim = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var angle = 0.0

    fun down(x: Double, y: Double) {
        active = true
        lastX = x
        lastY = y
        rim = hypot(x, y) > ORB_RADIUS * 0.78
        angle = atan2(-y, x)
    }

    /** cameraRotation は視点の向き(OrbitCamera.rotation)。押していなければ null */
    fun move(x: Double, y: Double, cameraRotation: Quat): Quat? {
        if (!active) return null
        if (rim) {
            val a = atan2(-y, x)
            var da = a - angle
            if (da > PI) da -= 2 * PI
            if (da < -PI) da += 2 * PI
            angle = a
            return Quat.fromAxisAngle(cameraRotation.rotate(Vec3.Z), da)
        }
        val k = PI / (2 * ORB_RADIUS)
        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y
        return Quat.fromAxisAngle(cameraRotation.rotate(Vec3.Y), dx * k) * Quat.fromAxisAngle(cameraRotation.rotate(Vec3.X), dy * k)
    }

    fun up() {
        active = false
    }
}

/** 選択中の部位に、オーブで作ったワールドの回転を加える(Web版の sphereRotate) */
fun EditorState.applyOrb(rotInWorld: Quat): EditorState = copy(skeleton = skeleton.orb(selected, rotInWorld))

/** 関節の軸 u を視点から見た向き(x 右、y 上、z 手前。長さ1) */
fun orbProject(cameraRotation: Quat, jointRotation: Quat, u: Vec3): Vec3 = (cameraRotation.inverse() * jointRotation).rotate(u)

enum class OrbPlane { XZ, YZ, XY }

/** 輪1本。points は 0 から 2π まで 64 等分した 65 点(視点から見た向き) */
class OrbRing(val plane: OrbPlane, val points: List<Vec3>)

/** オーブに描くもの(Web版の drawOrb)。front は関節の +z(前)、up は +y(上)の印の位置 */
class OrbPicture(val rings: List<OrbRing>, val front: Vec3, val up: Vec3)

fun orbPicture(cameraRotation: Quat, jointRotation: Quat): OrbPicture {
    fun ring(plane: OrbPlane) = OrbRing(plane, (0..64).map { i ->
        val t = i / 64.0 * 2 * PI
        val c = cos(t)
        val s = sin(t)
        val u = when (plane) {
            OrbPlane.XZ -> Vec3(c, 0.0, s)
            OrbPlane.YZ -> Vec3(0.0, c, s)
            OrbPlane.XY -> Vec3(c, s, 0.0)
        }
        orbProject(cameraRotation, jointRotation, u)
    })
    return OrbPicture(
        OrbPlane.entries.map(::ring),
        orbProject(cameraRotation, jointRotation, Vec3.Z),
        orbProject(cameraRotation, jointRotation, Vec3.Y),
    )
}
