# E01-04 状態から姿勢への変換

親: [E01](../epics/E01-logic-port.md)

## 概要
`applyState`(Qb·swing·roll·Qb⁻¹)と、玉の位置を実装する。

## 完了条件
- 休止状態で単位回転になる
- 既知の φ・ψ・roll で期待どおりの方向になる

## 依存
E01-03

## 設計の決定
- 状態は不変の `PartState(phi, psi, roll)`。休止状態は `PartState.rest(spec)`(ヒンジは psi を曲げる向きにする。Web版の resetPart)
- Web版の applyState は three.js のオブジェクトを書き換えるが、ここでは回転を返す純粋関数 `localRotation` と、玉の位置を返す `dotPosition` に分けた
- 可動域へのクランプはしない(Web版の applyState と同じく、入力側の E01-05・E01-07 で行う)

## 検証結果
- テスト先行: `PoseTest` を書き、コンパイルエラーで失敗することを確認してから実装した
- `./gradlew :core:test` → `PoseTest` 10件を含め、全53件が成功
- `./gradlew lint test assembleDebug` → BUILD SUCCESSFUL(lint は 0 errors、既存の警告5件のみ)

## 未検証のまま残したこと
- Web版の出力との数値の照合は E01-10 で行う

## ステータス
完了
