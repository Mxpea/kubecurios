StartupEvents.registry('item', event => {
  for (const name of ['blood_a', 'blood_b']) {
    event.create(`kubecurios_smoke:${name}`, 'kubecurios:curio')
      .slot('kubecurios_smoke_ring').armor(1).script('kubecurios_smoke:blood').script('kubecurios_smoke:blood')
  }
  event.create('kubecurios_smoke:script_only', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring').script('kubecurios_smoke:observer')
  event.create('kubecurios_smoke:missing_script', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring').script('kubecurios_smoke:undefined')
  event.create('kubecurios_smoke:charged_ring', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring').slot('kubecurios_probe_extra').maxDamage(128).script('kubecurios_smoke:charged')
  event.create('kubecurios_smoke:undamageable_ring', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring').script('kubecurios_smoke:charged')
  event.create('kubecurios_smoke:shadow_ring', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring').hostileDetectionRangePercent(-50)
    .maxDamage(8).activeSkillCooldown(20).script('kubecurios_smoke:activate')
  event.create('kubecurios_smoke:beacon_ring', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring').hostileDetectionRangePercent(50)
    .onActiveCommand('tag @s add kc_active_command', 20)
  event.create('kubecurios_smoke:aggro_probe', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring').script('kubecurios_smoke:aggro')
  event.create('kubecurios_smoke:aggro_aura', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring').provokeNeutralMobs(6).ignoreHostileMob('minecraft:zombie')
    .ignoreHostileMob('#minecraft:skeletons')
  const ring = event.create('kubecurios_smoke:legacy_failure', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring')
    .onHurtCommand('execute if entity @s[tag=kc_allow_legacy] run tag @s add kc_legacy_ran', 40)
  try {
    ring.script('kubecurios_smoke:observer').script('kubecurios_smoke:blood')
    throw new Error('Second script binding was accepted')
  } catch (error) {
    if (!String(error).includes('already binds script')) throw error
  }
})
