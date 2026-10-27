from pathlib import Path
import json
base=Path(__file__).resolve().parent.parent
p=(base/'gradle.properties').read_text()
assert 'minecraft_version=26.3' in p and 'mod_version=0.32.0-alpha.1' in p
mod=json.loads((base/'src/main/resources/fabric.mod.json').read_text())
assert mod['environment']=='client' and mod['depends']['java']=='>=25'
java={x.name:x.read_text() for x in (base/'src/main/java/dev/leadshot').glob('*.java')}
entry=java['LeadShot.java']
for phrase in ['KeyMappingHelper.registerKeyMapping','InputConstants.Type.KEYSYM','HudElementRegistry.attachElementBefore','getTicksUsingItem()','SHADOW.observe','ContactAudit.evaluate','FlightGuidePoints.sample','TargetStability.switchToNew','SHADOW.clear','options.write(CONFIG)','ProjectedBox.edges','BallisticOcclusion.evaluate','ClientBlockOcclusion.probe']:
    assert phrase in entry,phrase
assert 'ProjectileContactWindow.earliest' in java['ContactAudit.java']
for name in ['TrajectoryConsensus.java','ShotConfidence.java','ProjectedAimRings.java','ShotReleaseAudit.java','WorldGuideGeometry.java','ObservedArrowAudit.java']:
    assert name in java, name
for word in ['TrajectoryConsensus.evaluate','ShotConfidence.classify','ProjectedAimRings.aimRing','ProjectedAimRings.targetRing','3モデル支持','RELEASE_AUDIT.recordUseEnd','RELEASE_AUDIT.observe','RELEASE_AUDIT.expire','RELEASE_AUDIT.clear','WorldGuideGeometry.build','lastWorldGuideFrame','ARROW_AUDIT.noteUseEnd','ARROW_AUDIT.observe','ARROW_AUDIT.expire','ARROW_AUDIT.clear','AbstractArrow.class']:
    assert word in entry, word
for phrase in ['@Mod','net.neoforged','org.lwjgl.glfw','RenderLevelStageEvent','HudRenderCallback']:
    assert phrase not in '\n'.join(java.values()),phrase
assert (base/'.github/workflows/fabric-build.yml').is_file()
print('PASS Fabric26.3 static integration checks')
