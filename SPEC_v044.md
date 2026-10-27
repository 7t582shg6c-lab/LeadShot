# 技術仕様 v0.44 — 高速目標への照準と日本語化

- `GuideControls`の設定ファイルは後方互換を維持。照準角速度の設定値 `aimRateTenths` の最大だけ1200に変更。
- 新設定 `AdvancedAimOptions` は `config/leadshot-aim-advanced.properties` に保存。`latencyTenths`：0..40、`playerAutomation`：初期false。設定保存は一時ファイル＋原子的置換。
- 追跡64体の軽量観測は維持。エリトラ高速目標向けに `TargetVelocityFilter` の有効観測上限を3.5→12.0block/tickに変更。速度変化は `FlightTargetPredictor` によって最大0.55block/tick分の速度補正をする。速度予測は意図的に一定速度として後段の衝突監査・3モデルへ渡す。急旋回の未来を確定するものではない。
- `CoreBallistics.solve()` は性能と既存テストのため整数tick探索のまま。`solveRefined()` が選択された対象のみ初期最適tickの前後±1tickを1/16tick間隔で計算し直す。連続時間係数は離散サンプル間を線形補間し、`Solution.ticks` は監査互換性のため最も近い整数を保持。
- `MultiplayerAimSync`は対象UUIDが2tick以上安定することを確認。従来の「最後の追従操作から2tick待つ」ガードによる連続追従時のデッドロックを回避。発射の直前に標準の視点更新を送り、その後標準の弓使用終了を送る。サーバーからのACKではない。
- 既存の発射制約（右＋左クリック保持、最大チャージ、READY、敵対モブまたは明示許可したプレイヤー、遮蔽なし、モデル支持、間隔制限）は維持。未確認ルートで自動発射しない。
- マルチプレイの命中精度、エリトラ相手の追従性、サーバーの同期と再チャージは実機未検証。優先して実測する。
