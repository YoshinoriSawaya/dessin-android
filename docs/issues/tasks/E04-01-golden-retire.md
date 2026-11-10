# E04-01 照合テストの扱いと方針の更新

親: [E04](../epics/E04-device-feedback.md)

## 概要
- Web版は今後使わない(2026-11-11、利用者の判断)。Web版との照合は完了とし、これからは Issue ごとにテストを書く
- 仕様が Web版と変わるロジック・形・ピッキングの照合テストは、理由を書いて止める(@Ignore)。カメラの照合は人形の仕様に関係しないので残す
- CLAUDE.md と docs/architecture.md の「Web版が参照実装」の記述を、仕様の正典は architecture.md と Android 版のコード、に改める

## 完了条件
- CLAUDE.md・architecture.md・development.md に新しい方針が書かれ、`./gradlew test` が通る

## 依存
なし

## 検証結果
未着手

## 未検証のまま残したこと
未着手

## ステータス
未着手
