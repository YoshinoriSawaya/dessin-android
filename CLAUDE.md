# CLAUDE.md

3Dデッサン人形ツール(Android / Kotlin版)。運用の重さは**中間**。

## 判断の拠り所
- 構図・下絵用。出力はスクリーンショットで足りる。見た目の凝りより操作感を優先する
- 仕様の正典は `docs/architecture.md`。Web版(three.js)の挙動が参照実装
- 同じ内容を複数箇所に書かない。迷ったら README=事実、architecture=設計、issues=経緯

## 技術方針
- Kotlin + Jetpack Compose(UI)+ GLSurfaceView / OpenGL ES(3D描画)。構成は仮決定(`docs/architecture.md`)
- 計算ロジック(可動域・スイング/ツイスト・当たり判定)は Android 非依存の純Kotlinに分離する

## テスト(TDD強度: 中間)
- ロジック層(純Kotlin)はテスト先行(Red→Green→Refactor)。JUnitのJVMテストで回す
- UI・GL描画は手動確認。結果はタスクの検証結果に書く。テスト先行を外した場合は理由を一言残す
- バグ修正はまず再現テストを書く
- 実装に合わせてテストを書き換えて通すのは禁止。変更するなら理由を説明する

## 運用
- ドキュメントは `docs/` に置く(ルートは CLAUDE.md・README.md・CHANGELOG.md のみ)
- タスクは `docs/issues/` で管理。完了時に検証結果と未検証事項を書く
- リリースごとに `docs/retrospectives/` へ短く振り返りを書く
- 独立したタスクは並行で進める(コードを変えるものは別worktree)
- Git運用・完了の定義・破壊的操作の確認は dev-fundamentals に従う

## 環境の制約
- Claude側の環境にはAndroid SDKがなく、ビルド・実機確認は人間が Android Studio で行う
- Claudeはコードとテストを書き、ビルドと動作確認の結果は人間が検証結果に記入する
