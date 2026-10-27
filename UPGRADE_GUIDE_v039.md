# v0.39 UI/HUD 改善 — 操作と用語の対応

## 何が変わったか

表示部分だけを変更し、狙う対象、弾道の数値、遮蔽判定、READY判定、自動照準・連続発射、ロック対象はv0.38から引き継ぎます。**照準表示のカッコよさを命中精度と混同しない**よう、色の意味を固定しました。

| 旧表示 / 内部機能 | TACTICAL名称 | 意味 |
|---|---|---|
| AIM | LEAD [AIM] | 予測した発射方向。ここへ合わせる |
| PRED | INTERCEPT [PRED] | モブの推定未来位置 |
| LOCK | STT LOCK | 手動固定中（STTは単一目標追跡をイメージ） |
| AUTO | AUTO TRACK | 自動で対象を選んでいる状態 |
| CENTER | BORESIGHT | 画面中央優先の索敵 |
| RADAR | WIDE SCAN | 周囲から候補を選ぶレーダー索敵 |
| READY | FIRE CUE | 予測上の発射条件が揃った状態。命中保証なし |
| ブロック遮蔽あり | OBSTRUCTED | 経路に遮蔽を検出 |
| LOW / DEFAULT / HIGH / ALL | ECO / STANDARD / BOOST / FULL CACHE | 事前計算段階。内部の4段階処理は変更なし |
| 自動照準 / 補助発射 | AIM / FIRE | ONまたはSAFEで状態表示 |

### HUD構成

- **左上**：LEADSHOT FCS-39、追跡方式、対象名、距離・到達tick・角度・物理モデル一致。情報量モードで表示行を制限。
- **右上**：WEAPON SYSTEM。AIM/FIREのON/SAFE、レーダー候補数。小さい画面では省略。
- **中央より下**：FIRE CUE / TRACKING / ACQUIRING / OBSTRUCTED等の視認性重視の状態帯。
- **照準点周辺**：菱形型LEAD記号、未来位置INTERCEPT、従来の予測枠・飛行点。
- **パネル下端**：弓のチャージバー（弓使用中のみ）。

### 外観を戻したい場合

**Hキー**（Minecraftの操作設定で変更可）、または **O → 設定9ページ目 → HUD SKIN: CLASSIC/TACTICAL** を押してください。設定は `config/leadshot-guides.properties` の `hudStyle=0` (CLASSIC) / `hudStyle=1` (TACTICAL) に保存されます。既存設定に該当キーがない場合はTACTICALが初期値です。

文字倍率は5ページ目、線の太さと透明度は4ページ目。色をユーザー指定すると状態別の色表示が上書きされる部分があるため、危険判定を見やすくしたい場合は「色: 0（状態連動）」を推奨します。

### GitHub Actionsに反映

1. `LeadShot_v0.39.0_GitHub_Root_Upload.zip` を展開。
2. https://github.com/7t582shg6c-lab/LeadShot を開き、**Add file → Upload files**でZIP内の平坦なファイル一式を**リポジトリのルート**へアップロード。既存の同名ファイルは置換（ZIP自体はアップロードしない）。
3. `Commit changes`を押し、https://github.com/7t582shg6c-lab/LeadShot/actions を開く。
4. Gradleビルドが成功したらArtifacts内の`-sources.jar`でないJARを取得。旧版LeadShotを外し、Fabric APIは残す。

動作未確認のため、最初はクリエイティブのテストワールドで**Hで切替→RADARの事前ロック→最大チャージ時のLEAD/PRED→READY表示**の順に試してください。
