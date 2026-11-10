# CLAUDE.md

3Dデッサン人形ツール(Android / Kotlin版)。運用の重さは**中間**。

## 判断の拠り所
- 構図・下絵用。出力はスクリーンショットで足りる。見た目の凝りより操作感を優先する
- 仕様の正典は `docs/architecture.md`。Web版(three.js)の挙動が参照実装
- 同じ内容を複数箇所に書かない。迷ったら README=事実、architecture=設計、issues=経緯

## 技術方針
- Kotlin + Jetpack Compose(UI)+ GLSurfaceView / OpenGL ES(3D描画)。構成は仮決定(`docs/architecture.md`)
- 計算ロジック(可動域・スイング/ツイスト・ピッキング用のレイ計算)は Android 非依存の純Kotlinに分離する
- 失敗しうる処理(ポーズの読み込みなど)は例外を外に出さず結果型で返す。理由は内部ログ用で、利用者には短い文言だけを出す。例外を握りつぶさない

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
- Claude側でもビルド・lint・JVMテストを実行できる(手順は `docs/development.md`)
- エミュレータでの起動・スクリーンショットは Claude も adb で確認できる。実機での動作確認と操作感の評価は人間が行い、結果を検証結果に記入する
