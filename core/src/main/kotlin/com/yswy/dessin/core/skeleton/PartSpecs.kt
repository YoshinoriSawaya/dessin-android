package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Vec3
import kotlin.math.PI

/**
 * 全20部位の定義。値は docs/web-reference.html の spec(...) を正とし、左側の値で書く。
 * 右側は取り付け位置・休止方向の x を反転し、コーンの ±x を入れ替え、ロールを鏡像にする。
 */
object PartSpecs {
    private const val D2R = PI / 180

    val all: List<PartSpec> = buildList {
        add(PartSpec("rib", "胸", null, null, Vec3.ZERO, Vec3.ZERO, Vec3.Z, 0.0, 0.0, null, null, RollRange(0.0, 0.0), null))
        // 首の付け根は背骨の線(骨盤と同じ前後 −0.035。E04-03)
        add(part("neck", "首", "rib", null, v(0, 0.15, -0.035), v(0, 1, 0), 0.07, 0.2, cone(45, 50, 35, 35), roll(-30, 30)))
        add(part("head", "頭", "neck", null, v(0, 0.07, 0), v(0, 1, 0), 0.115, 0.22, cone(35, 45, 30, 30), roll(-60, 60), adjust = Adjust.HEAD_SIZE))
        // 肋骨を胸(根)と脇腹に分ける。高さ 0.30 を上から 6.5:3.5、関節は分かれ目の背骨の線(E05-01)
        add(part("flank", "脇腹", "rib", null, v(0, 0.15 - 0.195, -0.035), v(0, -1, 0), 0.105, 0.15, cone(25, 20, 20, 20), roll(-30, 30)))
        // 骨盤は脇腹の子。休止状態のワールドの位置は分ける前(肋骨から (0, −0.15, −0.035))と同じ
        add(part("pelvis", "骨盤", "flank", null, v(0, -0.105, 0), v(0, -1, 0), 0.07, 0.2, cone(70, 35, 35, 35), roll(-45, 45), adjust = Adjust.PELVIS_WIDTH))
        for (side in Side.entries) {
            val s = if (side == Side.LEFT) "L" else "R"
            val n = if (side == Side.LEFT) "左" else "右"
            fun sided(id: String, label: String, parent: String, attach: Vec3, restDir: Vec3, length: Double, guideRadius: Double,
                      cone: Cone?, roll: RollRange, hinge: Hinge? = null, ref: Vec3 = Vec3.Z, adjust: Adjust? = null, pivot: Vec3 = Vec3.ZERO) =
                part(id + s, n + label, parent, side, attach, restDir, length, guideRadius, cone, roll, hinge, ref, adjust, pivot)
            add(sided("clav", "肩", "rib", v(0.025, 0.12, 0), v(1, 0, 0), 0.12, 0.2, cone(20, 20, 35, 15), roll(0, 0), adjust = Adjust.SHOULDER_WIDTH))
            add(sided("upperArm", "上腕", "clav$s", v(0.12, 0, 0), v(0, -1, 0), 0.25, 0.25, cone(180, 60, 180, 30), roll(-90, 90)))
            // 前腕のひねり(回内・回外)は ±90°(E06-01)
            add(sided("forearm", "前腕", "upperArm$s", v(0, -0.25, 0), v(0, -1, 0), 0.23, 0.23, null, roll(-90, 90), hinge = Hinge(PI, -15 * D2R, 150 * D2R)))
            // 手は平たい面が体側を向く。手首は手の平・甲の側(±x)に 60°、親指・小指の側に 25°。ひねりは前腕で行う(E06-01)
            add(sided("hand", "手", "forearm$s", v(0, -0.23, 0), v(0, -1, 0), 0.10, 0.2, cone(25, 25, 60, 60), roll(0, 0)))
            add(sided("hip", "付け根", "pelvis", v(0.025, -0.11, 0.035), v(1, 0, 0), 0.05, 0.18, cone(25, 25, 25, 20), roll(0, 0)))
            // もものひねりは ±90°(E06-01)
            add(sided("thigh", "もも", "hip$s", v(0.05, 0, 0), v(0, -1, 0), 0.40, 0.4, cone(120, 30, 70, 30), roll(-90, 90)))
            // ひざは大腿骨の丸み(顆)の中心で回り、曲げるとすねの付け根が後ろ上へ転がるように移る(E06-02)
            add(sided("shin", "すね", "thigh$s", v(0, -0.40, 0), v(0, -1, 0), 0.38, 0.38, null, roll(0, 0), hinge = Hinge(0.0, 0.0, 150 * D2R), pivot = v(0, 0.03, 0)))
            add(sided("foot", "足", "shin$s", v(0, -0.38, 0), v(0, 0, 1), 0.16, 0.2, cone(20, 45, 20, 20), roll(0, 0), ref = Vec3.Y))
        }
    }

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): PartSpec = byId[id] ?: throw NoSuchElementException("部位がない: $id")

    fun children(id: String): List<PartSpec> = all.filter { it.parent == id }

    /** 左側の値で受け取り、右側なら鏡像にする */
    private fun part(
        id: String, label: String, parent: String, side: Side?, attach: Vec3, restDir: Vec3,
        length: Double, guideRadius: Double, cone: Cone?, roll: RollRange,
        hinge: Hinge? = null, ref: Vec3 = Vec3.Z, adjust: Adjust? = null, pivot: Vec3 = Vec3.ZERO,
    ): PartSpec {
        val right = side == Side.RIGHT
        fun mx(p: Vec3) = if (right) Vec3(-p.x, p.y, p.z) else p
        return PartSpec(
            id, label, parent, side, mx(attach), mx(restDir), ref, length, guideRadius,
            if (right) cone?.mirrored() else cone, hinge, if (right) roll.mirrored() else roll, adjust, mx(pivot),
        )
    }

    private fun v(x: Number, y: Number, z: Number) = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
    private fun cone(front: Int, back: Int, plusX: Int, minusX: Int) = Cone(front * D2R, back * D2R, plusX * D2R, minusX * D2R)
    private fun roll(min: Int, max: Int) = RollRange(min * D2R, max * D2R)
}
