# E00-02 Androidプロジェクトの作成

親: [E00](../epics/E00-setup.md)

## 概要
空の Compose アプリと、ロジック層用の純Kotlinモジュールを作る。Android Studio のウィザードは使わず、Claude がファイルを直接書いた。

## 完了条件
- `./gradlew lint test assembleDebug` が通る
- 実機またはエミュレータで起動し、「デッサン人形」と表示される

## 設計の決定
- パッケージ名・applicationId は `com.yswy.dessin`(利用者の別プロジェクトに合わせた仮定)
- モジュールは `:app`(Android)と `:core`(純Kotlin・JVM)に分ける。ロジック層で Android API を使えないようにするため
- バージョンは利用者の別プロジェクト(asset-dashboard-app)で実績のある組み合わせに合わせた: AGP 9.4.1、Kotlin 2.4.20、Gradle 9.7.1、Compose BOM 2026.09.00
- compileSdk・targetSdk 37、minSdk 26、Java/JVM ターゲット 17
- ランチャーアイコンは未設定(lint の警告 MissingApplicationIcon は残る)

## 検証結果
- 実行環境: Android Studio 付属の JBR(OpenJDK 25.0.3)、Windows 11
- `./gradlew lint test assembleDebug` → BUILD SUCCESSFUL
  - lint: 0 errors、5 warnings(新しいバージョンがある 4件、アプリのアイコン未設定 1件)
  - test: テストはまだ無い(`:app:testDebugUnitTest NO-SOURCE`)
- エミュレータでの起動(Claude が adb で実施): AVD `AssetDash_API37`(Android 17、x86_64)に `./gradlew installDebug` で入れ、`am start` で起動 → 「デッサン人形」と表示され、アプリのクラッシュはなし(logcat の crash バッファに出ていたのはエミュレータの UWB サービスで、アプリとは無関係)
- 実機での起動: 2026-11-11 に実機(Pixel 10a、Android 17)へ入れ、起動を確認した(E02-12)

## 未検証のまま残したこと
- lint の警告にあるバージョン更新は、別プロジェクトと揃えるため見送った

## ステータス
完了
