LeadShot v0.42.0-alpha.1 | GitHubルート更新用パッケージ

1. このZIPを展開する。
2. ZIP内の全ファイル（java/json/sh/py/md/properties）をLeadShot GitHubリポジトリのルート（最上位）へアップロード。
   .github/workflows/fabric-build.ymlは既存の正常動作品を変更しない。
3. Commit changes を行うとGitHub Actionsのビルドが開始される。
4. Java25 / Minecraft26.3 / Fabricでコンパイルが成功したら、ActionsのArtifactsのJARをダウンロード。
5. Prism LauncherのModsで古いLeadShot JARを削除して新しいJARを配置。

v0.42の変更: 64体固定追跡＋深度の4段階（ECO TRACK/MOTION SCAN/BALLISTIC SCAN/FULL ANALYSIS）。
ALLは64体を毎tick一斉に重い検査するのではなく、最大4体/tickのUUID公平巡回です。
ゲーム内での動作・FPSは未検証です。
