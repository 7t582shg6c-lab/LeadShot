# LeadShot v0.38 — 更新・操作・検証手順

## 導入した設定

**設定画面を8ページへ拡張。** Oキー（キー競合がある場合は割当変更）→ 最下部の「ページ 1/8」を7回押す → 8ページ目の「事前計算: DEFAULT」をクリックすると LOW → DEFAULT → HIGH → ALL と切り替わります。初期値は DEFAULT。

- **LOW**：近隣の位置・速度は毎tick観測するが、チャージ前の弾道事前計算なし。処理負荷優先。
- **DEFAULT**：優先1体を4tickごとにウォームアップ。通常のサバイバル向け。
- **HIGH**：優先8体を2tickごとにウォームアップ。動きが多い戦闘向け。
- **ALL**：最大64体を毎tickウォームアップ。多数の敵を瞬時に切替えたい場合向け。CPU負荷注意。

CENTER/RADARどちらでも4段階は独立して動作します。弓を持っていなくても常時トラッキングします。重い命中判定・遮蔽判定と実際のREADYは**最大チャージした選択対象の最新弾道**からのみ計算します。

## RADAR・自動照準・連続発射

- RADARをONにすると弓を引いていない間もターゲットマーカーが表示されます。
- I: ロック切替 / P: 次の候補 / K: ロック解除 / J: 自動照準ON/OFF。
- 右＋左クリック保持: 設定7ページ目で連続発射補助をONにし、弓が最大チャージかつREADYで1回発射を試みる。再チャージはゲームの通常入力に依存するため実機検証が必要。
- O: 設定 / N: 対象カテゴリ切替 / G: ガイド / V: 予測 / U: HUD / B: HUDレベル。

## GitHubビルド

1. 別ZIP `LeadShot_v0.38.0_GitHub_Root_Upload.zip` をPCで展開。
2. https://github.com/7t582shg6c-lab/LeadShot → Add file → Upload files で**展開したファイルの中身をリポジトリの最上位へ**アップロード。旧版の同名ファイルを置換。`.github/workflows/fabric-build.yml`は変更しないでください。
3. Commit changes → https://github.com/7t582shg6c-lab/LeadShot/actions から新runを開く。
4. 成功後の Artifacts から LeadShot-Fabric-26.3 を取得し、ZIP中の`-sources.jar`でないMOD JARだけをPrism LauncherのMOD一覧に追加。旧JARは削除。
5. Fabric API・Java 25を保ち、単独のテストワールドで動作を確認する。

ここに示すv0.38の機能はローカルの独立数値テスト・模擬APIコンパイルまで検査しています。**実Fabric 26.3でのコンパイルとゲーム内性能はまだ確認していません。**
