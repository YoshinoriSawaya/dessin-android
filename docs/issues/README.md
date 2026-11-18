# Issues

## エピック一覧
| ID | 内容 | 状態 |
|----|------|------|
| [E00](epics/E00-setup.md) | 運用の導入 | 完了 |
| [E01](epics/E01-logic-port.md) | ロジック層のKotlin移植 | 完了 |
| [E02](epics/E02-render-ui.md) | 3D描画とUI | 完了 |
| [E03](epics/E03-pose-save.md) | ポーズの保存 | 完了 |
| [E04](epics/E04-device-feedback.md) | 実機確認の修正 | 完了 |
| [E05](epics/E05-chest-flank.md) | 胸と脇腹 | 完了 |
| [E06](epics/E06-limb-tuning.md) | 実機確認の調整(手足) | 完了 |
| [E07](epics/E07-body-type.md) | 体型の調整と保存 | 完了 |
| [E08](epics/E08-body-shapes.md) | 体の形 | 完了 |
| [E09](epics/E09-default-posture.md) | 初期姿勢 | 完了 |
| [E10](epics/E10-controls.md) | 操作の追加 | 完了 |
| [E11](epics/E11-self-shadow.md) | 体に落ちる影 | 完了 |
| [E12](epics/E12-camera-mode.md) | カメラだけを操作するモード | 完了 |
| [E13](epics/E13-multi-doll.md) | 複数の人形 | 完了 |
| [E14](epics/E14-usage-feedback.md) | 使ってみて出た要望(体の形と骨盤の根) | 完了 |
| [E15](epics/E15-undo-mirror.md) | Undo と左右対称のコピー | 完了 |
| [E16](epics/E16-pelvis-root-chain.md) | 骨盤が根のときの胴の関節 | 完了 |
| [E17](epics/E17-knee-structure.md) | 膝の構造(転がり・回旋・膝蓋骨) | 完了(扇形の表示は取り下げ) |
| [E18](epics/E18-stick.md) | 手に持たせる棒 | 完了 |
| [E19](epics/E19-preset-poses.md) | プリセットのポーズ | 進行中 |

## いまの状態(2026-11-19 時点)
- 最新は v1.9.0(変更は CHANGELOG、振り返りは docs/retrospectives/R10-v1.9.md)。実機(Pixel 10a)に入れてある
- これまでの機能は、実機で利用者が使って確認済み(v1.1.0・v1.2.0 の機能は、2026-11-19 に v1.9 まで使って問題なしと確認)。実機で見てほしいところ・決まっていないことは無い
- 使いながら気づいたことや要望を下書き(staging)へ入れて進める
- テスト: `./gradlew lint test assembleDebug` が通る(:core のテスト491件、止めているテストは無い。lint は既存の警告5件のみ)
- Web版は今後使わない。仕様の正典は docs/architecture.md と Android 版のコード。テストは Issue ごとに書く(CLAUDE.md)
- ブランチは main だけで、GitHub(origin)と同じ。staging は空

## 再開するときは
1. 利用者が使っていて気づいたことを聞き、staging に下書きを置く
2. 下書きをエピック・タスクに振り分けて進める(staging/README.md の手順)
3. ビルド・テスト・エミュレータと実機での確認の手順は docs/development.md(JAVA_HOME を Android Studio の JBR に向ける。エミュレータは AssetDash_API37。デバッグビルドは `--es demo all|natural|multi --es select <部位id>` で起動時の状態を指定できる。実機へのインストールは裏で動かす)

## 次のフェーズの候補
- Undo の履歴を画面の作り直し(回転など)でも残す
- ポーズ全体の左右の写し・人形の左右反転(E15-02 は選んだ部位とその先だけ)

## 版ごとのエピック
変更の中身は CHANGELOG.md。

| 版 | 日付 | エピック |
|----|------|----------|
| v1.0.0 | 2026-11-14 | E00〜E06(Web版の機能の移植・ポーズの保存・実機確認の修正・胸と脇腹・手足の調整) |
| v1.1.0 | 2026-11-14 | E07〜E11(E07-04 を除く) |
| v1.2.0 | 2026-11-14 | E12・E13(E13-05 を除く) |
| v1.3.0 | 2026-11-14 | E07-04、ひざの転がりのずれで右側の部位がずれる不具合の修正 |
| v1.4.0 | 2026-11-15 | E14 |
| v1.5.0 | 2026-11-15 | E13-05・E04-04 |
| v1.6.0 | 2026-11-15 | E15 |
| v1.7.0 | 2026-11-15 | E16 |
| v1.8.0 | 2026-11-19 | E17(E17-04 は取り下げ) |
| v1.9.0 | 2026-11-19 | E18 |

## 未検証のまま残っていること
- 実機での確認は利用者の操作感の評価に頼っている(エミュレータでは2本指の操作を試せない、実機の重さは測っていない)。各タスクの「未検証のまま残したこと」を参照
