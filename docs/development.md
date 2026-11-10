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
