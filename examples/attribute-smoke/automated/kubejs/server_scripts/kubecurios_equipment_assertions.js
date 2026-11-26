const $EquipmentEnchantments = Java.loadClass('net.minecraft.world.item.enchantment.Enchantments')
const $EquipmentRegistries = Java.loadClass('net.minecraft.core.registries.Registries')
const $EquipmentValueOutput = Java.loadClass('net.minecraft.world.level.storage.TagValueOutput')
const $EquipmentValueInput = Java.loadClass('net.minecraft.world.level.storage.TagValueInput')
const $EquipmentProblemReporter = Java.loadClass('net.minecraft.util.ProblemReporter')
const $EquipmentFakePlayerFactory = Java.loadClass('net.neoforged.neoforge.common.util.FakePlayerFactory')
const $EquipmentGameType = Java.loadClass('net.minecraft.world.level.GameType')

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
      console.info('[KubeCurios equipment] PASS: source slots, tags, cumulative equipped time and save/load, durability and enchantment')
      return true
  }
  smoke.equipmentAt = wearer.tickCount + 5
  return false
}
