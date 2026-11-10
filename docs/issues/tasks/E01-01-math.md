# E01-01 最小限の数学型

親: [E01](../epics/E01-logic-port.md)

## 概要
Vec3・Quat・Ray を純Kotlinで用意する。three.js の該当機能(正規化、積、逆元、setFromUnitVectors、setFromAxisAngle、ベクトルの回転)だけを持つ。

## 完了条件
- 上記の各操作のJVMテストが通る(許容誤差 1e-6)

## 依存
E00-02

## 設計の決定
- Vec3・Quat・Ray は不変の data class(Double)。`a * b` は b を先に適用する(three.js の multiply と同じ順)
- 計算式は three.js r128 と同じにした(setFromUnitVectors の正反対の扱い、setFromRotationMatrix の4分岐を含む)
- `inverse()` は共役。単位四元数が前提(three.js の invert と同じ)
- 基準フレーム用に makeBasis → setFromRotationMatrix を `Quat.fromBasis` 1つにまとめた

## 検証結果
- テスト先行: テストを書いて `./gradlew :core:test` がコンパイルエラーで失敗することを確認してから実装した
- `./gradlew :core:test` → 14件すべて成功(Vec3Test 4、QuatTest 9、RayTest 1)
- `./gradlew lint test assembleDebug` → BUILD SUCCESSFUL(lint は 0 errors、既存の警告5件のみ)

## 未検証のまま残したこと
- three.js の実際の出力との数値の突き合わせはしていない(式を写したうえで、回転の性質をテストした)。E01-10 で照合する

## ステータス
完了
