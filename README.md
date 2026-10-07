# APKリバイブ (APK Revive)

古い APK を、最近の Android にインストールできる形へ「必要最小限の修正」で作り直す Android アプリです。

## できること
- **診断**: targetSdk・ABI・署名方式・exported 未指定などを調べ、この端末で動かない原因を表示
- **targetSdk の引き上げ**: 既定は「インストールできる最低ライン（24）」。上げすぎによる動作変化を避ける
- **android:exported の補完**: targetSdk 31 以上を選んだときだけ、従来の動作を保つ値で補う
- **整列**: resources.arsc の非圧縮化、zipalign 相当の整列（.so は 4KB 境界）
- **再署名**: ユーザー専用の鍵（Android Keystore 内で生成、端末外に出ない）で v1 + v2 + v3 署名

## できないこと（意図的に入れていない）
- 32bit 専用ネイティブコードの 64bit 化（技術的に不可能）
- 広告削除・課金/ライセンス回避などの改造、dex の書き換え
- APK のダウンロード・配布、アプリのインストール

## Google Play ポリシーへの配慮
| ポリシー | 対応 |
|---|---|
| REQUEST_INSTALL_PACKAGES（用途制限あり） | 使わない。保存だけ行い、インストールはユーザーが「ファイル」アプリから行う |
| QUERY_ALL_PACKAGES / ストレージ権限 | 使わない。入出力は SAF（ユーザーが選んだファイル）のみ |
| 外部コードの実行禁止（デバイスとネットワークの濫用） | APK 内のコードを読み込み・実行しない。INTERNET 権限なし |
| 知的財産・不正行為 | 改造機能なし。初回起動時に「権利のある APK に限る」同意を取得 |

## 使用ライブラリ（いずれも Apache-2.0）
- [ARSCLib](https://github.com/REAndroid/ARSCLib) — コミット `089ec68` をソースからビルドした jar を `app/libs/` に同梱
- [apksig](https://android.googlesource.com/platform/tools/apksig) — `com.android.tools.build:apksig`

GPL のコード（APK Explorer & Editor、APK Editor Studio 等）は設計の参考にしただけで、コードは流用していません。

## ビルド
GitHub Actions（[yyoossk/android-build-env](https://github.com/yyoossk/android-build-env) の共通ワークフロー）で `assembleDebug` を実行します。APK は Actions の Artifact `apk-revive-debug` から取得できます。
