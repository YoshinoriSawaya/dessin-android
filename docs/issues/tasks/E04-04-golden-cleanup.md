# E04-04 止めていた照合テストと生成の道具を片付ける

親: [E04](../epics/E04-device-feedback.md)

## 概要
- E04-01 で止めた(@Ignore)Web版との照合テスト3件と、そのデータ・生成スクリプトが残っていた。v1.4.0 のあと、片付ける(2026-11-15、利用者の依頼。消すかどうかは Claude が決めた)

## 決めたこと(Claude)
- 消す: `WebGoldenTest`・`MeshGoldenTest`・`PickGoldenTest`、それだけが使う `GoldenOps`、データ `web-golden.txt`・`mesh-golden.txt`・`pick-golden.txt`、生成の道具 `tools/golden`(generate.mjs・package.json・package-lock.json と手元の node_modules 約 42MB)。仕様が Web版から離れていて、動かす見込みが無いため
- 残す: `CameraGoldenTest` と `camera-golden.txt`。今も動いていて、カメラの仕様は変わっていない。データは固定のものとして扱い、作り直さない
- 残す: `docs/web-reference.html`。多くのテストが期待値の出どころとして名前を挙げている
- 消したものは Git の履歴(タグ v1.4.0)から取り出せる

## 検証結果
- `./gradlew lint test assembleDebug` → BUILD SUCCESSFUL(:core のテスト422件、止めているテスト0件、失敗0件。lint は既存の警告5件のみ)
- 消したテストのファイル・クラスの名前が残っていたコメント(`Primitives.kt`・`PrimitivesTest.kt`・`CameraGoldenTest.kt`)と、development.md・architecture.md・.gitignore を直した
- テストを消したのは止めていた3件だけで、動いていたテストは変えていない

## 未検証のまま残したこと
なし

## ステータス
完了
