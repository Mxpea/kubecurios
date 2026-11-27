const $EquipmentEnchantments = Java.loadClass('net.minecraft.world.item.enchantment.Enchantments')
const $EquipmentRegistries = Java.loadClass('net.minecraft.core.registries.Registries')
const $EquipmentValueOutput = Java.loadClass('net.minecraft.world.level.storage.TagValueOutput')
const $EquipmentValueInput = Java.loadClass('net.minecraft.world.level.storage.TagValueInput')
const $EquipmentProblemReporter = Java.loadClass('net.minecraft.util.ProblemReporter')
const $EquipmentFakePlayerFactory = Java.loadClass('net.neoforged.neoforge.common.util.FakePlayerFactory')
const $EquipmentGameType = Java.loadClass('net.minecraft.world.level.GameType')
const $EquipmentTargeting = Java.loadClass('net.minecraft.world.entity.ai.targeting.TargetingConditions')
const $EquipmentActiveSkill = Java.loadClass('com.aurelith.kubecurios.CurioActiveSkill')
const $AggroMemory = Java.loadClass('net.minecraft.world.entity.ai.memory.MemoryModuleType')
const $AggroHurtByGoal = Java.loadClass('net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal')

function checkAggro() {
  const wearer = smoke.entity
  const level = smoke.level
  const data = wearer.getPersistentData()
  smoke.stacks.setStackInSlot(0, Item.of('kubecurios_smoke:aggro_probe'))

  for (const type of [$EntityType.ZOMBIE, $EntityType.ENDERMAN, $EntityType.PIGLIN]) {
    var mob = type.create(level, $EntitySpawnReason.COMMAND)
    var source = mob.damageSources().mobAttack(wearer)
    data.putBoolean('kc_aggro_changed', false)
    scriptedHit(mob, source, 1)
    smokeCheck(data.getBooleanOr('kc_aggro_changed', false), `Script could not set ${type} aggro`)
    smokeCheck(mob.getTarget().getId() === wearer.getId(), `Wrong ${type} target`)

    var retaliation = null
    if (type === $EntityType.ZOMBIE) {
      retaliation = mob.targetSelector.getAvailableGoals().stream()
        .filter(goal => goal.getGoal() instanceof $AggroHurtByGoal).findFirst().orElseThrow()
      retaliation.start()
    } else if (type === $EntityType.ENDERMAN) {
      smokeCheck(mob.getPersistentAngerTarget().matches(wearer) && mob.isAngry(), 'Enderman native anger was not set')
    } else {
      smokeCheck(mob.getBrain().getMemory($AggroMemory.ANGRY_AT).isPresent(), 'Piglin anger memory was not set')
    }

    wearer.addTag('kc_clear_aggro')
    data.putBoolean('kc_aggro_changed', false)
    scriptedHit(mob, source, 1)
    wearer.removeTag('kc_clear_aggro')
    smokeCheck(data.getBooleanOr('kc_aggro_changed', false) && mob.getTarget() === null, `Script could not clear ${type} aggro`)
    if (retaliation !== null) {
      smokeCheck(!retaliation.isRunning() && !retaliation.getGoal().canContinueToUse(), 'Zombie retained its running retaliation target')
    } else if (type === $EntityType.ENDERMAN) {
      smokeCheck(mob.getPersistentAngerTarget() === null && !mob.isAngry(), 'Enderman native anger was not cleared')
    } else {
      smokeCheck(mob.getBrain().getMemory($AggroMemory.ANGRY_AT).isEmpty(), 'Piglin anger memory was not cleared')
    }

    if (type === $EntityType.ZOMBIE) {
      mob.setTarget(smoke.restoredWearer)
      wearer.addTag('kc_clear_aggro')
      scriptedHit(mob, source, 1)
      wearer.removeTag('kc_clear_aggro')
      smokeCheck(mob.getTarget().getId() === smoke.restoredWearer.getId(), 'Clear aggro erased an unrelated target')
      smokeCheck(CuriosUtils.setAggro(mob, wearer) && mob.getTarget().getId() === wearer.getId(), 'Global helper could not set aggro')
      smokeCheck(CuriosUtils.clearAggro(mob, wearer) && mob.getTarget() === null, 'Global helper could not clear aggro')
      smokeCheck(!CuriosUtils.clearAggro(mob, wearer) && !CuriosUtils.setAggro(mob, null), 'Global helper accepted a missing target')
    }
    mob.discard()
  }
  console.info('[KubeCurios aggro] PASS: Goal targeting, native anger, Brain memories and targeted clearing')
}

function startAggroAuraChecks() {
  const wearer = smoke.entity
  const level = smoke.level
  const zombie = $EntityType.ZOMBIE.create(level, $EntitySpawnReason.COMMAND)
  const skeleton = $EntityType.SKELETON.create(level, $EntitySpawnReason.COMMAND)
  const creeper = $EntityType.CREEPER.create(level, $EntitySpawnReason.COMMAND)
  zombie.setPos(wearer.getX() + 3, wearer.getY(), wearer.getZ())
  skeleton.setPos(zombie.getX(), zombie.getY(), zombie.getZ())
  creeper.setPos(zombie.getX(), zombie.getY(), zombie.getZ())
  const discovery = $EquipmentTargeting.forCombat().range(20).ignoreLineOfSight()
  const retaliation = discovery.copy().ignoreInvisibilityTesting()
  smoke.stacks.setStackInSlot(0, Item.of('minecraft:air'))
  smokeCheck(discovery.test(level, zombie, wearer) && discovery.test(level, skeleton, wearer),
    'Targets must be eligible before equipping the aura')
  smoke.stacks.setStackInSlot(0, Item.of('kubecurios_smoke:aggro_aura'))
  smokeCheck(!discovery.test(level, zombie, wearer), 'Ignored zombie could proactively select the wearer')
  smokeCheck(!discovery.test(level, skeleton, wearer), 'Skeleton matched by a tag could proactively select the wearer')
  smokeCheck(discovery.test(level, creeper, wearer), 'Unlisted creeper was suppressed')
  smokeCheck(retaliation.test(level, zombie, wearer), 'Vanilla retaliation was suppressed')
  zombie.setLastHurtByMob(wearer)
  smokeCheck(discovery.test(level, zombie, wearer), 'The wearer should remain a valid revenge target')
  zombie.setLastHurtByMob(null)
  smoke.equipmentInventory.setSlotActive(smokeSlot, 0, false)
  smokeCheck(discovery.test(level, zombie, wearer), 'An inactive aura should not suppress targeting')
  smoke.equipmentInventory.setSlotActive(smokeSlot, 0, true)

  smoke.aggroWolves = [3, 9].map(distance => {
    const wolf = $EntityType.WOLF.create(level, $EntitySpawnReason.COMMAND)
    wolf.setPos(wearer.getX() + distance, wearer.getY(), wearer.getZ())
    wolf.setNoAi(true)
    wolf.setNoGravity(true)
    smokeCheck(level.addFreshEntity(wolf), 'Could not add wolf for the provocation check')
    return wolf
  })
}

function finishAggroAuraChecks() {
  const nearby = smoke.aggroWolves[0]
  const outside = smoke.aggroWolves[1]
  smokeCheck(nearby.isAngry() && nearby.getTarget().getId() === smoke.entity.getId(), 'Neutral wolf did not acquire the wearer in range')
  smokeCheck(!outside.isAngry() && outside.getTarget() === null, 'A wolf outside the radius was provoked')
  console.info('[KubeCurios aggro] PASS: passive targeting exclusions, retaliation and provocation radius')
}

function startPlayerSkillChecks() {
  const player = $EquipmentFakePlayerFactory.getMinecraft(smoke.level)
  const inventory = player.getCapability($CuriosCapability.INVENTORY)
  const handler = inventory.getStacksHandler(smokeSlot).orElseThrow()
  const stacks = handler.getStacks()
  const zombie = smoke.entity
  const cow = $EntityType.COW.create(smoke.level, $EntitySpawnReason.COMMAND)
  // Exercise the vanilla visibility-distance gate independently of fake-player combat eligibility.
  const targeting = $EquipmentTargeting.forNonCombat().range(20).ignoreLineOfSight()
  const shadow = Item.of('kubecurios_smoke:shadow_ring')
  const beacon = Item.of('kubecurios_smoke:beacon_ring')
  player.setPos(zombie.getX() + 16, zombie.getY(), zombie.getZ())
  smokeCheck(targeting.test(smoke.level, zombie, player), `Zombie should see the unequipped player at 16 blocks (distance=${zombie.distanceToSqr(player)}, visibility=${player.getVisibilityPercent(zombie)}, visible=${player.canBeSeenByAnyone()})`)
  stacks.setStackInSlot(0, shadow)
  stacks.setStackInSlot(1, shadow.copy())
  near(player.getVisibilityPercent(zombie), 0.5, 'Same detection item must count once')
  near(player.getVisibilityPercent(cow), 1, 'Passive mobs must retain normal visibility')
  smokeCheck(!targeting.test(smoke.level, zombie, player), 'Zombie should not detect the concealed player at 16 blocks')
  inventory.setSlotActive(smokeSlot, 0, false)
  inventory.setSlotActive(smokeSlot, 1, false)
  near(player.getVisibilityPercent(zombie), 1, 'Disabled slots must not modify detection')
  inventory.setSlotsActive(smokeSlot, true)
  stacks.setStackInSlot(1, beacon)
  near(player.getVisibilityPercent(zombie), 0.75, 'Different detection items should multiply')
  stacks.setStackInSlot(0, Item.of('minecraft:air'))
  player.setPos(zombie.getX() + 25, zombie.getY(), zombie.getZ())
  smokeCheck(targeting.test(smoke.level, zombie, player), 'Positive modifier should make 25 blocks reachable with a 20-block base')

  const data = player.getPersistentData()
  data.putInt('kc_active_calls', 0)
  player.removeTag('kc_active_command')
  stacks.setStackInSlot(0, shadow)
  stacks.setStackInSlot(1, shadow.copy())
  $EquipmentActiveSkill.activate(player)
  $EquipmentActiveSkill.activate(player)
  smokeCheck(data.getIntOr('kc_active_calls', 0) === 1 && shadow.getDamageValue() === 1,
    'Active callback should run once and spend durability only on its source copy')
  smokeCheck(stacks.getStackInSlot(1).getDamageValue() === 0, 'Second active curio copy spent durability')
  smokeCheck(!player.getCooldowns().isOnCooldown(shadow), 'Skill cooldown must not block vanilla item use')
  stacks.setStackInSlot(1, beacon)
  smoke.skillPlayer = player
  smoke.skillInventory = inventory
  smoke.skillStacks = stacks
}

function checkPlayerSkillsDuringCooldown() {
  const player = smoke.skillPlayer
  smoke.skillStacks.setStackInSlot(0, Item.of('minecraft:air'))
  smoke.skillStacks.setStackInSlot(0, Item.of('kubecurios_smoke:shadow_ring'))
  $EquipmentActiveSkill.activate(player)
  smokeCheck(player.getPersistentData().getIntOr('kc_active_calls', 0) === 1, 'Re-equipping bypassed the active cooldown')
  smokeCheck(player.entityTags().contains('kc_active_command'), 'Independent active command did not execute as the wearer')
}

function finishPlayerSkillChecks() {
  const player = smoke.skillPlayer
  player.removeTag('kc_active_command')
  smoke.skillInventory.setSlotsActive(smokeSlot, false)
  $EquipmentActiveSkill.activate(player)
  smokeCheck(player.getPersistentData().getIntOr('kc_active_calls', 0) === 1, 'Inactive skill slot executed a callback')
  smokeCheck(!player.entityTags().contains('kc_active_command'), 'Inactive skill slot executed a command')
  smoke.skillInventory.setSlotsActive(smokeSlot, true)
}

function checkPlayerDurability() {
  const player = $EquipmentFakePlayerFactory.getMinecraft(smoke.level)
  const inventory = player.getCapability($CuriosCapability.INVENTORY)
  inventory.loadDatapacks()
  const stackHandler = inventory.getStacksHandler(smokeSlot).orElseThrow().getStacks()
  player.setGameMode($EquipmentGameType.SURVIVAL)
  smokeCheck(!player.hasInfiniteMaterials(), 'Player probe must be in survival')
  player.addTag('kc_cost_hit')
  try {
    const fresh = Item.of('kubecurios_smoke:charged_ring')
    stackHandler.setStackInSlot(0, fresh)
    const target = smoke.target
    const source = target.damageSources().playerAttack(player)
    target.setHealth(target.getMaxHealth())
    scriptedHit(target, source, 1)
    smokeCheck(stackHandler.getStackInSlot(0).getDamageValue() === 1, 'Survival player nonlethal hit did not spend durability')
    fresh.setDamageValue(127)
    const lethalTarget = $EntityType.SLIME.create(smoke.level, $EntitySpawnReason.COMMAND)
    scriptedHit(lethalTarget, lethalTarget.damageSources().playerAttack(player), 20)
    smokeCheck(stackHandler.getStackInSlot(0).isEmpty(), 'Survival player lethal hit did not break the curio')
  } finally {
    player.removeTag('kc_cost_hit')
    stackHandler.setStackInSlot(0, Item.of('minecraft:air'))
  }
}

function startEquipmentChecks() {
  smoke.equipmentStep = 0
  smoke.equipmentAt = smoke.entity.tickCount
  smoke.equipmentInventory = smoke.entity.getCapability($CuriosCapability.INVENTORY)
  smoke.extra = smoke.equipmentInventory.getStacksHandler('kubecurios_probe_extra').orElseThrow()
  smoke.wearerData = smoke.entity.getPersistentData()
  smoke.enchanted = Item.of('kubecurios_smoke:charged_ring')
  smoke.enchanted.setDamageValue(96)
  const lookup = smoke.level.registryAccess().lookupOrThrow($EquipmentRegistries.ENCHANTMENT)
  smoke.enchanted.enchant(lookup.getOrThrow($EquipmentEnchantments.UNBREAKING), 3)
  smoke.wearRing = Item.of('kubecurios_smoke:charged_ring')
  smoke.stacks.setStackInSlot(0, smoke.wearRing)
  smoke.firstEquipAt = smoke.entity.tickCount
  smoke.stacks.setStackInSlot(1, Item.of('minecraft:air'))
  smoke.extra.getStacks().setStackInSlot(0, smoke.enchanted)
  smoke.stacks.setStackInSlot(1, Item.of('minecraft:amethyst_shard'))
  smoke.target.setHealth(40)
  scriptedHit(smoke.target, smoke.outgoing, 1)
  smokeCheck(smoke.wearerData.getLongOr('kc_equipped_ticks', -1) === 0, 'First observed equip time should be zero')
  smokeCheck(smoke.wearerData.getIntOr('kc_other_count', -1) === 2, 'Count other cells, including a vanilla item')
  smokeCheck(smoke.wearerData.getIntOr('kc_remaining', -1) === 32, 'The first slot was not selected')
  smokeCheck(smoke.wearerData.getIntOr('kc_max', -1) === 128, 'Wrong max durability')
  near(smoke.wearerData.getDoubleOr('kc_percent', -1), 25, 'Wrong remaining durability percentage')
  smokeCheck(smoke.wearerData.getIntOr('kc_unbreaking', -1) === 3, 'Applied enchantment level was not read')
  smokeCheck(smoke.enchanted.getDamageValue() === 96 && smoke.wearRing.getDamageValue() === 0, 'No return value should not spend durability')
}

function continueEquipmentChecks() {
  const wearer = smoke.entity
  if (wearer.tickCount < smoke.equipmentAt) return false
  const data = smoke.wearerData
  const extra = smoke.extra.getStacks()
  const air = Item.of('minecraft:air')
  const target = smoke.target
  switch (smoke.equipmentStep++) {
    case 0:
      // A seeded native control measures the expected Unbreaking cost for the same stack.
      const control = smoke.enchanted.copy()
      smoke.level.getRandom().setSeed(8)
      control.hurtAndBreak(20, smoke.level, wearer, item => {})
      smokeCheck(control.getDamageValue() < 116, 'Unbreaking control did not reduce damage')
      smoke.level.getRandom().setSeed(8)
      wearer.addTag('kc_cost_hurt')
      scriptedHit(wearer, smoke.incoming, 1)
      wearer.removeTag('kc_cost_hurt')
      smokeCheck(smoke.enchanted.getDamageValue() === control.getDamageValue(), 'Script cost did not follow native Unbreaking')
      smokeCheck(smoke.wearRing.getDamageValue() === 0, 'Second copy must not take durability cost')
      smoke.equipTime = data.getLongOr('kc_equipped_ticks', -1)
      smokeCheck(smoke.equipTime === wearer.tickCount - smoke.firstEquipAt, 'Two copies must add exactly one tick per wearer tick')
      smoke.equipmentInventory.setSlotActive(smokeSlot, 1, false)
      scriptedHit(target, smoke.outgoing, 1)
      smokeCheck(data.getIntOr('kc_other_count', -1) === 1, 'Disabled cell must be excluded')
      smoke.equipmentInventory.setSlotActive(smokeSlot, 1, true)
      smoke.stacks.setStackInSlot(1, air)
      smoke.scriptSlot.getCosmeticStacks().setStackInSlot(1, Item.of('minecraft:amethyst_shard'))
      scriptedHit(target, smoke.outgoing, 1)
      smokeCheck(data.getIntOr('kc_other_count', -1) === 1, 'Cosmetic cell must be excluded')
      extra.setStackInSlot(0, air)
      break
    case 1:
      wearer.addTag('kc_cost_hit')
      scriptedHit(target, smoke.outgoing, 1)
      smokeCheck(data.getLongOr('kc_equipped_ticks', -1) === wearer.tickCount - smoke.firstEquipAt, 'Removing one copy interrupted the clock')
      smokeCheck(data.getIntOr('kc_unbreaking', -1) === 0 && smoke.wearRing.getDamageValue() === 1, 'Source did not move to the remaining unenchanted copy')
      wearer.addTag('kc_replace')
      scriptedHit(target, smoke.outgoing, 1)
      wearer.removeTag('kc_replace')
      wearer.removeTag('kc_cost_hit')
      smokeCheck(smoke.stacks.getStackInSlot(0).getDamageValue() === 0 && smoke.wearRing.getDamageValue() === 1, 'Replacement stack was charged')
      smoke.equipTime = data.getLongOr('kc_equipped_ticks', -1)
      smoke.stacks.setStackInSlot(0, air)
      smoke.equipmentAt = wearer.tickCount + 2
      return false
    case 2:
      smoke.wearRing = Item.of('kubecurios_smoke:charged_ring')
      smoke.stacks.setStackInSlot(0, smoke.wearRing)
      scriptedHit(target, smoke.outgoing, 1)
      smokeCheck(data.getLongOr('kc_equipped_ticks', -1) === smoke.equipTime, 'Unequipped ticks were counted or the total was reset')
      smoke.equipmentInventory.setSlotActive(smokeSlot, 0, false)
      smoke.equipmentAt = wearer.tickCount + 10
      return false
    case 3:
      smoke.equipmentInventory.setSlotActive(smokeSlot, 0, true)
      scriptedHit(target, smoke.outgoing, 1)
      smokeCheck(data.getLongOr('kc_equipped_ticks', -1) === smoke.equipTime, 'Disabled ticks were counted or the total was reset')
      smoke.reactivatedAt = wearer.tickCount
      smoke.costCalls = data.getIntOr('kc_cost_ticks', 0)
      wearer.addTag('kc_periodic_cost')
      smoke.equipmentAt = wearer.tickCount + 40
      return false
    case 4:
      wearer.removeTag('kc_periodic_cost')
      const calls = data.getIntOr('kc_cost_ticks', 0) - smoke.costCalls
      smokeCheck(calls === 2 && smoke.wearRing.getDamageValue() === calls, 'Periodic hook did not charge once per 20 entity ticks')
      near(wearer.getAttributeValue($Attributes.ARMOR), smoke.baseArmor + 3, 'Charged ring effect was not applied')
      smoke.wearRing.setDamageValue(127)
      wearer.addTag('kc_cost_hit')
      scriptedHit(target, smoke.outgoing, 1)
      const brokenAt = data.getLongOr('kc_equipped_ticks', -1)
      smokeCheck(brokenAt === smoke.equipTime + wearer.tickCount - smoke.reactivatedAt, 'Reactivated ticks did not resume the counter')
      smoke.equipTime = brokenAt
      wearer.removeTag('kc_cost_hit')
      smokeCheck(smoke.stacks.getStackInSlot(0).isEmpty(), 'Broken curio remained in the source cell')
      near(wearer.getAttributeValue($Attributes.ARMOR), smoke.baseArmor, 'Broken curio left its temporary armor behind')
      smoke.stacks.setStackInSlot(0, Item.of('kubecurios_smoke:undamageable_ring'))
      scriptedHit(target, smoke.outgoing, 1)
      smokeCheck(!smoke.stacks.getStackInSlot(0).isEmpty(), 'Durability cost consumed an undamageable curio')
      for (const key of ['kc_remaining', 'kc_max', 'kc_percent']) {
        smokeCheck(data.getDoubleOr(key, 0) === -1, `Non-durable reading ${key} must be null`)
      }
      smokeCheck(data.getLongOr('kc_equipped_ticks', -1) === 0, 'Different item IDs must not share totals')
      smoke.equipmentAt = wearer.tickCount + 10
      return false
    case 5:
      smoke.stacks.setStackInSlot(0, Item.of('kubecurios_smoke:charged_ring'))
      scriptedHit(target, smoke.outgoing, 1)
      smokeCheck(data.getLongOr('kc_equipped_ticks', -1) === smoke.equipTime, 'Breaking and replacing the last copy lost the total or counted the gap')
      // Save while equipped, then load the saved entity after game time has advanced.
      const output = $EquipmentValueOutput.createWithContext($EquipmentProblemReporter.DISCARDING, smoke.level.registryAccess())
      wearer.saveWithoutId(output)
      output.discard('UUID')
      smoke.savedWearer = output.buildResult()
      smoke.equipmentAt = wearer.tickCount + 10
      return false
    case 6:
      const input = $EquipmentValueInput['create(net.minecraft.util.ProblemReporter,net.minecraft.core.HolderLookup$Provider,net.minecraft.nbt.CompoundTag)'](
        $EquipmentProblemReporter.DISCARDING, smoke.level.registryAccess(), smoke.savedWearer)
      const restored = $EntityType.ZOMBIE.create(smoke.level, $EntitySpawnReason.COMMAND)
      restored.load(input)
      smokeCheck(CuriosUtils.isEquipped(restored, smokeSlot), 'Restored wearer lost the equipped ring')
      const restoredData = restored.getPersistentData()
      restoredData.putLong('kc_equipped_ticks', -1)
      scriptedHit(target, target.damageSources().mobAttack(restored), 1)
      smokeCheck(restoredData.getLongOr('kc_equipped_ticks', -1) === smoke.equipTime, 'Save/load must preserve only ticks earned before the save')
      smokeCheck(smoke.level.addFreshEntity(restored), 'Could not add the restored wearer')
      smoke.restoredWearer = restored
      smoke.restoredAt = restored.tickCount
      smoke.equipmentAt = wearer.tickCount + 10
      return false
    case 7:
      const loadedWearer = smoke.restoredWearer
      scriptedHit(target, target.damageSources().mobAttack(loadedWearer), 1)
      smokeCheck(loadedWearer.getPersistentData().getLongOr('kc_equipped_ticks', -1) === smoke.equipTime + loadedWearer.tickCount - smoke.restoredAt,
        'Restored wearer did not resume counting exactly once per equipped tick')
      checkPlayerDurability()
      startPlayerSkillChecks()
      break
    case 8:
      checkPlayerSkillsDuringCooldown()
      smoke.equipmentAt = wearer.tickCount + 20
      return false
    case 9:
      finishPlayerSkillChecks()
      break
    case 10:
      $EquipmentActiveSkill.activate(smoke.skillPlayer)
      smokeCheck(smoke.skillPlayer.getPersistentData().getIntOr('kc_active_calls', 0) === 2
        && smoke.skillPlayer.entityTags().contains('kc_active_command'), 'Active skills did not recover after cooldown')
      checkAggro()
      startAggroAuraChecks()
      smoke.equipmentAt = wearer.tickCount + 15
      return false
    case 11:
      finishAggroAuraChecks()
      console.info('[KubeCurios skill] PASS: hostile detection, active skill sources and cooldowns')
      console.info('[KubeCurios equipment] PASS: source slots, tags, cumulative equipped time and save/load, durability and enchantment')
      return true
  }
  smoke.equipmentAt = wearer.tickCount + 5
  return false
}
