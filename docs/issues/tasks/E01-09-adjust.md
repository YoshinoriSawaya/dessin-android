# E01-09 調整パラメータ

親: [E01](../epics/E01-logic-port.md)

## 概要
肩幅(上腕の取り付け位置)、骨盤の幅と付け根の内寄り(付け根の長さ・ももの位置、`updateHips`)、頭の大きさを実装する。

## 完了条件
- `updateHips` の式(下限 0.03)の再現テストが通る

## 依存
E01-04

## 設計の決定
- Web版では部位ごとに持っていた値(頭の size、肩と骨盤の wscale、骨盤の hipInset)を、1つの `BodyParams` にまとめた
- 骨格の位置に効く部分だけを扱う: `attachOf`(上腕・ももの取り付け位置)、`hipLength`(付け根の長さ)、`jointScale`(頭の拡大率)。肩・骨盤・付け根のメッシュの拡大は E02 の描画層で行う
- スライダーの範囲は定数として持ち、範囲内に収めるのは UI 側で行う
- E01-08 への申し送り: Web版は部位を外すと size を 1 に戻す(頭を外すと頭の大きさが戻る)。肩幅・骨盤の値は外しても戻さない

## 検証結果
- テスト先行: `BodyParamsTest` を書き、コンパイルエラーで失敗することを確認してから実装した
- `./gradlew :core:test` → `BodyParamsTest` 8件を含め、全85件が成功
- `./gradlew lint test assembleDebug` → BUILD SUCCESSFUL(lint は 0 errors、既存の警告5件のみ)

## 未検証のまま残したこと
- 見た目(メッシュの幅)との整合は E02 で確かめる

## ステータス
完了
