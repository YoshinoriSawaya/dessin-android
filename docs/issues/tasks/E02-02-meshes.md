# E02-02 形状の生成

親: [E02](../epics/E02-render-ui.md)

## 概要
- 部位ごとの見た目の寸法と形(Web版の build: 円柱・箱・球・錐台・頭の面)を、頂点・法線・インデックスの配列として生成する
- 正中線と輪郭線の管(Web版の cl・densify)
- 可動域の表示: コーンは phiMax に沿った面と輪郭、ヒンジは扇(Web版の buildGuide)
- 肩幅・骨盤の横幅・付け根の長さに応じたメッシュの拡大(E01-09 で描画層に回した部分)

## 進め方
`:core`(テスト先行)

## 完了条件
- 形状の頂点数・範囲・法線の向き、可動域の輪郭が phiMax の上にあることをJVMテストで確かめる

## 依存
E01-03

## 設計の決定
- `:core` の `mesh` パッケージに置いた。`Mesh`(GL にそのまま渡せる float の頂点・法線と int のインデックス)
- 箱・円柱・球は three.js r128 の BoxGeometry・CylinderGeometry・SphereGeometry と同じ頂点の並びで作る(uv は使わないので作らない)。拡大した形の法線は逆の比で傾けて正規化する(BufferGeometry.scale と同じ)
- 部位ごとのメッシュ(`PartVisuals`)は Web版の build と同じ寸法・置き方・種類(本体・関節の玉・顔の面)。体型による置き方の変化(肩の長さ、骨盤の横幅、付け根の長さ)は `PartVisuals.placement` で求める
- 正中線と輪郭の輪の管は簡略化した: Web版はスプライン曲線に沿う TubeGeometry だが、densify した折れ線の各点に断面を置いてつなぐ(太さ・断面の数は Web版と同じ 0.0028・4)
- 輪郭線は three.js r128 の EdgesGeometry と同じ規則(30度、位置を 1e-4 単位でまとめる)で、GL_LINES 用の端点の並びにした
- 可動域の表示(`guideMesh`)は Web版の buildGuide と同じ面と縁。玉の大きさは `dotWorldRadius`
- 全体を表示の箱(`fitBounds`)は、置いた部位のメッシュと線の箱の8隅をワールドに移して囲む。線の作り方が違うので Web版とは数ミリ違いうる

## 検証結果
- テスト先行: `MeshGoldenTest`・`PrimitivesTest`・`LinesTest`・`PartVisualsTest` を書き、コンパイルエラーで失敗することを確認してから実装した
- Web版との照合: `tools/golden/generate.mjs` に形の書き出しを足した。全20部位のメッシュ35個の頂点・法線・インデックス・置き方(初期と体型を変えたとき)、19部位の可動域の面・インデックス・縁が 1e-6 以内で一致(頂点の数値は float32 なので有効数字9桁で書き出す)
- `./gradlew :core:test` → 全170件が成功
- `./gradlew lint test assembleDebug` → BUILD SUCCESSFUL(lint は 0 errors、既存の警告5件のみ)

## 未検証のまま残したこと
- 管(正中線)と輪郭線は Web版と照合していない(作り方が違うため。性質だけ単体テストで確かめた)。見た目は E02-04 で見比べる
- 全体を表示の箱は Web版と数ミリ違いうる

## ステータス
完了
