package com.yswy.dessin.core.camera

import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** 視点のプリセット(Web版の「正面」「横」「背面」「上」) */
enum class ViewPreset(val yaw: Double, val pitch: Double) {
    FRONT(0.0, 0.12),
    SIDE(PI / 2, 0.12),
    BACK(PI, 0.12),
    TOP(0.0, 1.35),
}

/**
 * 注視点のまわりを回る視点(Web版の target・yaw・pitch・dist と updateCamera)。不変。
 * 画面の座標は左上が原点、右が +x、下が +y(ピクセル)。
 */
data class OrbitCamera(
    val target: Vec3 = Vec3(0.0, 1.10, 0.0),
    val yaw: Double = 0.4,
    val pitch: Double = 0.14,
    val distance: Double = 2.2,
) {
    val position: Vec3
        get() {
            val cp = cos(pitch)
            return Vec3(
                target.x + distance * sin(yaw) * cp,
                target.y + distance * sin(pitch),
                target.z + distance * cos(yaw) * cp,
            )
        }

    /** 視点の向き。−z が注視点、上は +y にできるだけ寄せる(three.js の Camera.lookAt) */
    val rotation: Quat
        get() {
            val z = (position - target).normalized()
            val x = Vec3.Y.cross(z).normalized()
            val y = z.cross(x)
            return Quat.fromBasis(x, y, z)
        }

    /** 1本指のドラッグで回る。縦は真上・真下の手前で止める */
    fun orbit(dx: Double, dy: Double): OrbitCamera =
        copy(yaw = yaw - dx * YAW_PER_PX, pitch = (pitch + dy * PITCH_PER_PX).coerceIn(PITCH_MIN, PITCH_MAX))

    /** 距離を factor 倍にする */
    fun zoom(factor: Double): OrbitCamera = copy(distance = (distance * factor).coerceIn(DISTANCE_MIN, DISTANCE_MAX))

    /** 2本指の間隔が previous から current になった(開くと近づく) */
    fun pinch(previous: Double, current: Double): OrbitCamera = zoom(previous / current)

    fun preset(p: ViewPreset): OrbitCamera = copy(yaw = p.yaw, pitch = p.pitch)

    /**
     * 箱 [min, max] 全体が入るように、注視点と距離を決める(Web版の fitView)。向きは変えない。
     * aspect は画面の幅 / 高さ。
     */
    fun fit(min: Vec3, max: Vec3, aspect: Double): OrbitCamera {
        val size = max - min
        val t = tan(FOV / 2)
        val need = max(size.y / (2 * t), max(size.x / (2 * t * aspect), size.z / (2 * t * aspect)))
        return copy(target = (min + max) * 0.5, distance = (need * 1.5 + 0.2).coerceIn(FIT_DISTANCE_MIN, DISTANCE_MAX))
    }

    /** 画面の点 (sx, sy) を通るレイ(Web版の setRay) */
    fun ray(sx: Double, sy: Double, width: Double, height: Double): Ray {
        val ndcX = (sx / width) * 2 - 1
        val ndcY = -(sy / height) * 2 + 1
        val t = tan(FOV / 2)
        val dirInCamera = Vec3(ndcX * t * (width / height), ndcY * t, -1.0)
        return Ray(position, rotation.rotate(dirInCamera).normalized())
    }

    /** 点 p の奥行きで、画面上の移動 (dx, dy) ピクセルに当たるワールドの移動(Web版の screenDelta) */
    fun screenDelta(p: Vec3, dx: Double, dy: Double, height: Double): Vec3 {
        val unit = 2 * position.distanceTo(p) * tan(FOV / 2) / max(1.0, height)
        val q = rotation
        return q.rotate(Vec3.X) * (dx * unit) + q.rotate(Vec3.Y) * (-dy * unit)
    }

    /** 平行移動(E12)。指を (dx, dy) 動かすと、注視点の奥行きの物が指についてくるように注視点を逆へ動かす */
    fun pan(dx: Double, dy: Double, height: Double): OrbitCamera = copy(target = target - screenDelta(target, dx, dy, height))

    fun viewMatrix(): Mat4 = Mat4.compose(position, rotation, 1.0).inverseRigid()

    fun projectionMatrix(aspect: Double): Mat4 = Mat4.perspective(FOV, aspect, NEAR, FAR)

    companion object {
        /** 縦の視野角(35度) */
        const val FOV = 35 * PI / 180
        const val NEAR = 0.1
        const val FAR = 60.0
        const val YAW_PER_PX = 0.008
        const val PITCH_PER_PX = 0.006
        const val PITCH_MIN = -1.2
        const val PITCH_MAX = 1.4
        const val DISTANCE_MIN = 0.8
        const val DISTANCE_MAX = 9.0
        /** 全体を表示のときは近づきすぎない */
        const val FIT_DISTANCE_MIN = 1.6
    }
}
