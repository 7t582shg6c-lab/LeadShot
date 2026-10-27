from pathlib import Path
import json
base=Path(__file__).resolve().parent.parent
p=(base/'gradle.properties').read_text()
assert 'minecraft_version=26.3' in p and 'mod_version=0.42.0-alpha.1' in p
mod=json.loads((base/'src/main/resources/fabric.mod.json').read_text())
assert mod['environment']=='client' and mod['depends']['java']=='>=25'
java={x.name:x.read_text() for x in (base/'src/main/java/dev/leadshot').glob('*.java')}
entry=java['LeadShot.java']
for phrase in ['KeyMappingHelper.registerKeyMapping','InputConstants.Type.KEYBOARD','HudElementRegistry.attachElementBefore','getTicksUsingItem()','SHADOW.observe','ContactAudit.evaluate','FlightGuidePoints.sample','TargetStability.switchToNew','SHADOW.clear','options.write(CONFIG)','ProjectedBox.edges','BallisticOcclusion.evaluate','ClientBlockOcclusion.probe']:
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

# Rendering must use frame-time WORLD-space interpolation, not fixed per-tick positions.
assert 'SMOOTHER.observe' in entry and 'SMOOTHER.sample(System.nanoTime()' in entry
assert 'GuideFrameSmoother.java' in java
assert 'case "smoothing"' in entry

assert 'AimReadySignal.java' in java
assert 'case "textsize"' in entry and 'case "cue"' in entry and 'case "searchmode"' in entry
assert 'readyState=READY.update' in entry
assert 'SimpleSoundInstance.forUI' in entry
assert 'gfx.pose().pushMatrix()' in entry
print('PASS v0.35 UI signaling, text scaling, and radar acquisition checks')

# v0.37: full-draw-only expensive math, passive radar, held-button release.
for phrase in ['RELEASE_GATE.allow','AimAssistController.step','mc.gameMode.releaseUsingItem(shooter)',
               'guideOptions.lineOfSightOnly()', 'guideOptions.assistedFire()', 'guideOptions.autoAim()',
               'mc.options.keyAttack.isDown()', 'mc.options.keyUse.isDown()',
               'chosen.entity() instanceof Enemy', 'shooter.getTicksUsingItem()<20',
               'acquireRadarWithoutBow', 'drawRadarMarker','AimReadinessCriteria.bounds',
               'TargetVelocityFilter.smooth', 'TargetVelocityFilter.lead']:
    assert phrase in entry, phrase
for file in ['AimAssistController.java','ContinuousReleaseGate.java','AimReadinessCriteria.java','TargetVelocityFilter.java']:
    assert file in java,file
assert 'case "autoaim"' in entry and 'case "autofire"' in entry
assert 'FIRE_ASSIST.consumeClick()' not in entry
print('PASS v0.37 held-input, passive radar and full-draw computation checks')

assert 'guideOptions=guideOptions.withAssist(g.autoAim(),g.assistedFire(),g.assistSpeed())' not in entry
assert 'guideOptions=guideOptions.withNumeric(key,v)' in entry
assert 'guideOptions=guideOptions.withAssist' not in entry
assert 'guideToggle(g,' in entry and 'setFineTune(FineTuneParameter key,int raw)' in entry
assert 'guideOptions.searchMode()==1||!options.linked()||options.hud()' in entry
assert 'preview=Preview.charging(radarMark==null?' in entry
print('PASS v0.37 persistent assist config and always-on radar label checks')

assert 'RadarLockHandoff.aliveNearby' in entry
assert 'RadarLockHandoff.java' in java
print('PASS v0.37 dead-lock handoff policy wired')

# v0.38 settings and warming fast-path without relaxing READY correctness.
assert 'FullDrawWarmCache.java' in java and 'PrecomputePolicy.java' in java
for phrase in ['advancePrecompute','PrecomputePolicy.plan','WARM.peek','WARM.store','WARM.retain','case "compute"','STAGES.observe','STAGES.audited','PRECOMPUTE_ORDER.next']:
    assert phrase in entry,phrase
assert 'CoreBallistics.Solution preciseShot=CoreBallistics.solve' in entry
assert 'ContactAudit.evaluate(eye,preciseShot' in entry
assert 'WARM.clear();' in entry
assert all(name in java for name in ['StagedPrecomputeCache.java','RoundRobinPrecompute.java'])
assert 'PrecomputePolicy.ACTIVE_CANDIDATES' in entry
assert 'fullDrawCandidateCap' not in entry
assert 'warmBudget' not in entry
print('PASS v0.42 consistent 64-entity depth stages, current-tick revalidation and fixed candidate policy')
