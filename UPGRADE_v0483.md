# LeadShot v0.48.3-alpha.1 — 観測スナップショット共有基盤

更新日: 2026-10-28
対応: Minecraft Java 26.3 / Fabric。**GitHub上のv0.48.2-alpha.1から更新**。

## 今回の変更

- `ObservationBusV483`を新設。最大64体の対象位置、速度、当たり判定、対象UUID、ワールド世代、観測tickを、**変更不可のフレーム**として保持します。対象は一意に管理し、重複ID・異なるワールド・古いtickを拒否します。
- `LeadShotFlightObserverV48`の診断用追跡を変更。既存のMinecraftクライアントtickで一度収集した診断対象をフレームとして発行し、`TrackRepositoryV481`へ渡します。追加のターゲット取得は行いません。
- `TrackRepositoryV481.observeFrame()`を追加。64件をまとめて取り込み、非アクティブ対象の破棄、重複フレーム防止、ワールド変更時のリセットまでまとめて行います。
- `LeadShotFlightObserverV48.latestObservedFrame()`で**タイムスタンプ付きの読み取り専用最新フレーム**を後続コンポーネントから取得できるようにしました。消費側では`validFor(epoch,tick,maxAge)`を必ず確認します。

### 重要な範囲の制限

今回共有したのは**診断用トラッカー内の入力とフレーム配信**です。従来の`LeadShot.java`本体は別の64体追跡を行っており、**両者を完全に統合したわけではありません**。したがって既存の索敵・自動照準・READY・自動発射の判断も変更していません。

このため、「Minecraft全体で観測処理が一度になった」「FPSが改善した」「命中率が上がった」という主張はできません。既存の自動照準側が同じフレームを安全に読み取る改修は次の段階です。

## GitHubウェブ画面での更新手順

1. `LeadShot_v0.48.3_GitHub_5_Files.zip`を展開。
2. `https://github.com/7t582shg6c-lab/LeadShot`を開き、**Add file → Upload files**を選択。
3. ZIP本体ではなく、展開した**5ファイル**をリポジトリ最上位へ上書きアップロード。
4. **Commit changes**を押し、`https://github.com/7t582shg6c-lab/LeadShot/actions`の最新runの成功を確認。
5. 成功したときのみ生成されたJARへ交換。旧JARは削除する。

変更対象: `ObservationBusV483.java`（追加）、`TrackRepositoryV481.java`、`LeadShotFlightObserverV48.java`、`gradle.properties`、本更新文書。

`.github/workflows/fabric-build.yml`と`fabric.mod.json`は変更不要。診断を有効にする場合の設定ファイルも従来と同じ`config/leadshot-flight-shadow.properties`で、`enabled=true`。診断OFFなら追加の追跡計算はほぼ実行しません。

## テスト

`bash verify-v0483-local.sh`

- `FlightShadowV48Test` 45,011件（1万5,000のランダム矢軌道）
- `ArrowTelemetryV48Test` 5件
- `TrackRepositoryV481Test` 9,480件
- `AdaptiveMotionModelV482Test` 46件
- `ObservationBusV483Test` 2,019件：tick、epoch、64体、重複、再送、バスの不変性、学習済み予測の保持、ワールド変更
- Observerの**模擬Minecraft/Fabric API**コンパイル
- 64体×1200tickのローカル診断ベンチマーク（参考、実FPSではない）

**合計56,561アサーション。** 本物のFabric 26.3 + Java 25でのv0.48.3ビルド、シングル・マルチプレイ、エリトラ命中率、FPSは未検証。

## 次の開発

v0.48.4では`LeadShot.java`の本体側索敵が共有フレームを安全に利用できるよう接続を追加します。ただし診断がOFFでも既存の照準機能が使えるよう、**既存経路へのフォールバック**とワールド/時刻/対象一致チェックを必須にします。その後、v0.49でCV/CA/CTの予測を実際の照準に段階導入します。
