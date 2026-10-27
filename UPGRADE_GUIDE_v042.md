# LeadShot v0.42 — 計算モード仕様刷新

## 変更点

以前の事前計算段階は LOW=0体、DEFAULT=1体、HIGH=8体、ALL=64体という「事前計算する人数」で差を付けていた。v0.42では**すべてのモードで最大64体を追跡し、どこまで計算するかだけを変える**。

| 設定画面の名前 | モード | 64体に適用する処理 | 分散更新 |
|---|---|---|---|
| ECO TRACK | LOW | 位置・速度 | 重い先行処理なし |
| MOTION SCAN | DEFAULT | ＋5/10/20tick未来位置 | 毎tick64体の軽い外挿 |
| BALLISTIC SCAN | HIGH | ＋最大チャージ弾道 | 8体/tickずつ巡回 |
| FULL ANALYSIS | ALL | ＋移動AABB、遮蔽、3モデル | 4体/tickずつ巡回 |

どのモードでも選択した対象は、最大チャージ後に最新データを使い直して照準・READYを判定する。先行計算の結果は発射の保証ではない。

## 設定と表示

ゲーム中にOキー（変更済みなら割り当て先）を押して8ページ目のPROCESSORを開く。PROCESSをクリックしてモードを切替可能。RADAR / CENTERとは独立しており、両モードに適用される。TACTICAL / CLASSICでも同じ計算段階が有効。

詳細HUDでは `STAGE 何体/追跡体数` と表示される。動くモブでは事前計算が無効になりうるため、ALLを選択していても常に64/64になるとは限らない。

## 推奨

- まず **DEFAULT** で常時追跡と動体予測を試す。
- 複数体の切替が多い場合は **HIGH**。
- 多数のブロック・動体が密集した環境では **ALL** のFPSを確認してから使用する。

## 配布・導入

- `LeadShot_v0.42.0_GitHub_Root_Upload.zip` を展開し、中のファイルをGitHubリポジトリの**最上位**へ上書きアップロード（ZIP本体ではない）。
- 既存 `.github/workflows/fabric-build.yml` はそのまま維持。Gradle propertiesのMOD版数は`0.42.0-alpha.1`。
- GitHub Actionsで **Java 25 + Fabric 26.3** のビルドを実施する。成功したらJARをPrism Launcherへ導入。旧LeadShot JARと同時に入れない。
- **v0.42はローカルJava 21単体テスト・模擬API構文チェック済み、実Minecraftコンパイルは未検証。**
