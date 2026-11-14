# 開発手順

## 環境
- Android Studio(付属のJDK = JBR を使う)、Android SDK(compileSdk 37)
- 実機(USBデバッグ)またはエミュレータ
- `local.properties` に SDK の場所を書く(Gitには入れない)。例: `sdk.dir=C\:/Users/<user>/AppData/Local/Android/Sdk`

## モジュール
- `:app` Android アプリ(Compose・GL描画)
- `:core` ロジック層。純Kotlin(JVM)で Android に依存しない

## コマンド
`JAVA_HOME` を Android Studio の JBR に向けてから実行する(Git Bash の例: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`)。

| 目的 | コマンド |
|------|---------|
| まとめて確認 | `./gradlew lint test assembleDebug` |
| ロジックのテストだけ | `./gradlew :core:test` |
| ビルド | `./gradlew assembleDebug` |
| 静的解析 | `./gradlew lint` |

## 検証結果の書き方
タスクファイルの「検証結果」に、実行したコマンドと結果、実機での確認内容を書く。

## エミュレータでの確認(adb)
SDK の `emulator` と `platform-tools` を使う(PATH に無ければ SDK のフォルダから呼ぶ。Git Bash の例: `export PATH="$PATH:/c/Users/<user>/AppData/Local/Android/Sdk/platform-tools:/c/Users/<user>/AppData/Local/Android/Sdk/emulator"`)。

```
emulator -list-avds                          # AVD の一覧
emulator -avd AssetDash_API37 -no-snapshot-save &   # 起動
adb wait-for-device && adb shell getprop sys.boot_completed   # 1 なら起動完了
./gradlew installDebug
adb shell am start -W -n com.yswy.dessin/.MainActivity
adb exec-out screencap -p > screen.png       # スクリーンショット
adb logcat -d -b crash                       # クラッシュの確認
adb shell input mouse scroll 540 700 --axis VSCROLL,1   # マウスホイール(E12-02)
```

- エミュレータと実機を同時につないでいるときは `adb -s <serial>` で選ぶ(`adb devices -l`)
- adb の `input` では2本の指を同時に動かせないので、ピンチ・2本指の平行移動はテストで確かめ、実機で利用者に見てもらう
- インストールの直後に起動すると、起動時の状態の指定(`--es ...`)が効かないことがある。起動し直す

## 実機での確認
- 実機は Pixel 10a(Android 17)。USB デバッグの許可が要る(つなぎ直すと聞き直されることがある)
- 入れ方: `./gradlew assembleDebug` のあと `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`(`-r` で上書きし、保存したデータを残す)
- インストールは、実機の画面で確認(USB 経由のインストールの許可、Google Play プロテクト、画面ロック)を待って止まることがある。Claude はインストールを裏で動かし、利用者に実機の画面を見てもらう
- 入ったか: `adb -s <serial> shell dumpsys package com.yswy.dessin | grep version`
- 操作感の評価は利用者が行い、結果をタスクの検証結果に書く(CLAUDE.md)

## Web版との照合データ(E01-10。v1 で役目を終えた)
Web版を使わない方針(2026-11-11)に合わせ、`WebGoldenTest`・`MeshGoldenTest`・`PickGoldenTest` は止めた(@Ignore。仕様が Web版と変わるため)。`CameraGoldenTest` は人形の仕様に関係しないので動かし続ける。以下は記録として残す。

`core` の `WebGoldenTest`・`CameraGoldenTest`・`MeshGoldenTest`・`PickGoldenTest` は、Web版(`docs/web-reference.html`)を Node で動かして作ったデータ(`core/src/test/resources/golden/` の `web-golden.txt`・`camera-golden.txt`・`mesh-golden.txt`・`pick-golden.txt`)と照合する。Web版やデータの作り方を変えたら作り直す。

```
cd tools/golden
npm install
npm run generate
```

Node.js が必要。three.js r128 と jsdom は `tools/golden` の開発用の依存で、アプリには入らない。

## 起動時の状態の指定(デバッグビルドだけ)
エミュレータで見た目を確かめるため、起動時の状態を adb から指定できる(`app/.../DebugStart.kt`)。

```
adb shell am start -n com.yswy.dessin/.MainActivity --es demo all --es select upperArmL
```

- `--es demo all`: 全部位を置き、腕と脚を少し動かした姿勢
- `--es demo natural`: 画面で順に選んだときと同じく、全部位を自然な姿勢(E09)で置く
- `--es demo multi`: 全部位を自然な姿勢で置いた人形を3体並べる(E13)
- `--es pose "<保存形式の文字列。改行の代わりに ;>"`: 保存形式(PoseCodec)で指定
- `--es select <部位id>`: 選択する部位(置けるなら、画面で選んだときと同じく自然な姿勢で置く)

## Claude で作業するときの注意
- Git Bash のヒアドキュメントでは、バックスラッシュ(`\n` など)や終わりの印が崩れることがある。複数のファイルを書き換えるスクリプトは、Write でファイルに書いてから `python <ファイル>` で動かす(R02 の振り返り)
- テストの件数・失敗は `core/build/test-results/test/*.xml` から集計できる
