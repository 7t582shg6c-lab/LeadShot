# LeadShot for Minecraft Java 26.3 / Fabric — v0.42.0-alpha.1

Minecraft Java 26.3向けのクライアント側弓弾道予測MODです。v0.41のTACTICAL/CLASSIC HUD、弓最大チャージ時のREADY/発射補助、ロックオン、設定直接入力を引き継いでいます。

## v0.42の主な変更

事前計算モードを「対象数の違い」から「**共通の最大64体にどの深さの事前処理を施すか**」へ変更。

- LOW: 位置・速度の観測
- DEFAULT: 64体の位置・速度＋5/10/20tickの簡易未来位置
- HIGH: さらにフルチャージ弾道を8件/tickの巡回で段階的に計算
- ALL: さらに移動AABB、ブロック経路、3モデル予測を4件/tickで巡回

READYと発射補助の最終判定には**選択1体の最新精密計算**を使用し、事前結果では射撃を許可しません。設定はOキー→8ページ目。CENTER/RADARのどちらでも全64体の追跡枠は共通です。

[詳細設計](SPEC_v042.md) / [更新と利用案内](UPGRADE_GUIDE_v042.md) / [変更履歴](CHANGELOG.md)

## 技術要件

Minecraft 26.3、Fabric Loader、Fabric API、Java 25。付属GitHub Actionsで本環境のコンパイルを確認する必要があります。**統合ソースZIPはJARではありません。** 本版は単体テストと模擬APIコンパイルのみ実施済み。ゲーム内動作や実測FPSは未検証です。
