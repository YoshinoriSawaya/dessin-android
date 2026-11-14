package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.camera.ViewPreset
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.Skeleton

/** 表示の切替(Web版の「ガイド」「正中線」「関節の玉」「格子」のチップ) */
enum class Display { GUIDES, LINES, MARKERS, GRID, SHADOWS }

fun EditorState.isOn(d: Display): Boolean = when (d) {
    Display.GUIDES -> options.guides
    Display.LINES -> options.lines
    Display.MARKERS -> options.markers
    Display.GRID -> options.grid
    Display.SHADOWS -> options.shadows
}

fun EditorState.toggle(d: Display): EditorState = copy(
    options = when (d) {
        Display.GUIDES -> options.copy(guides = !options.guides)
        Display.LINES -> options.copy(lines = !options.lines)
        Display.MARKERS -> options.copy(markers = !options.markers)
        Display.GRID -> options.copy(grid = !options.grid)
        Display.SHADOWS -> options.copy(shadows = !options.shadows)
    },
)

/**
 * マウスホイールで拡大縮小する(E12-02)。steps は上へ回した量(1 で一刻み、負で下へ)。
 * 一刻みで距離を 0.9 倍(上へ回すと近づく)。視点の向き・注視点・ポーズは変えない
 */
fun EditorState.wheelZoom(steps: Double): EditorState = withCamera((camera ?: OrbitCamera()).zoom(Math.pow(WHEEL_STEP, steps)))

private const val WHEEL_STEP = 0.9

/** 視点のプリセット(正面・横・背面・上)。注視点と距離は変えない */
fun EditorState.preset(p: ViewPreset): EditorState = withCamera((camera ?: OrbitCamera()).preset(p))

/** 置いた部位全体が入るように注視点と距離を決める(Web版の「全体を表示」)。aspect は 3D の画面の幅 / 高さ */
fun EditorState.fitView(aspect: Double): EditorState {
    val cam = camera ?: OrbitCamera()
    val (lo, hi) = fitBounds(skeleton) ?: return withCamera(cam)
    return withCamera(cam.fit(lo, hi, aspect))
}

/**
 * 保存したポーズを読み込む。選択中の部位が置かれていなければ胸を選び、全体を表示する(視点の向きは保つ)。
 */
fun EditorState.loadPose(pose: Skeleton, aspect: Double): EditorState =
    copy(
        skeleton = NaturalPose.unplacedNatural(pose.withBody(skeleton.body).withAnchor(skeleton.anchor)),
        selected = if (pose.isPlaced(selected)) selected else "rib",
    ).fitView(aspect)

/** 保存した体型を読み込む。ポーズ・選択・視点はそのまま(E07-03) */
fun EditorState.loadBody(body: BodyParams): EditorState = copy(skeleton = skeleton.withBody(body))
