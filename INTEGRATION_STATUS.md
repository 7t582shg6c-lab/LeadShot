# v0.44 統合検証状況

- ソース: Minecraft 26.3 / Fabric、バージョン 0.44.0-alpha.1
- Java 21数学・設定回帰テスト: 成功（詳細は `/mnt/data/LeadShot_v0.44.0_validation.log`）
- 模擬Minecraft APIによる統合構文検査: 成功（実Fabric互換性の証明ではない）
- 高速射手・高速対象1万ケースの粗/精密弾道比較: 成功
- マルチサーバーでの自動照準、発射、再チャージ: **未検証**
- 実Java 25 + Fabric 26.3 Gradle build: **未実施**
- 速度上限120°/tickおよび0〜4.0tickの先読みは許容操作範囲であり、命中を保証しません。
