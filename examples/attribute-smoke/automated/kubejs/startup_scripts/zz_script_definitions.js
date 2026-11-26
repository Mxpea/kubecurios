const halfHealthFactor = 1.2
let previousContext

function expectDefinitionError(message, define) {
  try {
    define()
  } catch (error) {
    if (String(error).includes(message)) return
    throw error
  }
  throw new Error(`Definition should be rejected: ${message}`)
}

CurioScripts.define('kubecurios_smoke:blood', {
  modifyOutgoingDamage(ctx) {
    const wearer = ctx.entity()
    if (wearer.entityTags().contains('kc_throw')) throw new Error('expected outgoing failure')
    if (wearer.getHealth() >= wearer.getMaxHealth() / 2) return CurioEffects.multiplyDamage(halfHealthFactor)
  },
  modifyIncomingDamage(ctx) {
    if (ctx.attacker() === null && ctx.entity().entityTags().contains('kc_environment')) return CurioEffects.multiplyDamage(0.5)
  },
  equipmentModifiers(ctx) {
    const wearer = ctx.entity()
    if (wearer.entityTags().contains('kc_throw')) throw new Error('expected equipment failure')
    if (wearer.entityTags().contains('kc_invalid')) return [CurioEffects.armor(4), CurioEffects.armor(1)]
    if (wearer.getHealth() < wearer.getMaxHealth() / 2 && ctx.commandSucceeds('execute unless entity @s[tag=exhausted]')) {
      return [CurioEffects.armor(4), CurioEffects.attribute('minecraft:armor_toughness', 0.5, 'addition')]
    }
  },
  onTick(ctx) {
    const wearer = ctx.entity()
    wearer.addTag(`kc_tick_${wearer.tickCount}`)
    const data = wearer.getPersistentData()
    data.putInt('kc_tick_count', data.getIntOr('kc_tick_count', 0) + 1)
  },
  onHurt(ctx) {
    const wearer = ctx.entity()
    if (ctx.attacker() === null || ctx.target().getId() !== wearer.getId() || ctx.damage() <= 0 || !ctx.damageSource()) {
      throw new Error('Incorrect onHurt context')
    }
    wearer.addTag('kc_hurt')
    if (wearer.entityTags().contains('kc_queue')) {
      if (ctx.commandSucceeds('scoreboard players add @s kc_probe 1')) wearer.addTag('kc_queue_accepted')
      if (ctx.commandResult('scoreboard players add @s kc_numeric 1') === null) wearer.addTag('kc_queue_has_no_integer')
      if (ctx.commandSucceeds('execute if entity @s[tag=kc_never]')) wearer.addTag('kc_queued_false_accepted')
      const score = wearer.getServer().getScoreboard().getPlayerScoreInfo(wearer, wearer.getServer().getScoreboard().getObjective('kc_probe'))
      if (score.value() === 0) wearer.addTag('kc_queue_before_zero')
    }
    if (wearer.entityTags().contains('kc_sync')) {
      if (previousContext && !previousContext.commandSucceeds('tag @s add kc_expired')) wearer.addTag('kc_expired_context')
      previousContext = ctx
      if (ctx.commandSucceeds(' /execute if dimension minecraft:overworld if entity @s[tag=kc_sync,distance=..0.01] ')) wearer.addTag('kc_condition_true')
      if (!ctx.commandSucceeds('execute if entity @s[tag=kc_never]')) wearer.addTag('kc_condition_false')
      if (ctx.commandSucceeds('tag @s add kc_side_effect')) wearer.addTag('kc_side_effect_ok')
      if (ctx.commandSucceeds('return 0')) wearer.addTag('kc_return_zero')
      if (ctx.commandResult('return 0') === 0) wearer.addTag('kc_numeric_zero')
      if (ctx.commandResult('scoreboard players get @s kc_numeric') === -7) wearer.addTag('kc_numeric_negative')
      if (ctx.commandResult('execute as @e[tag=kc_numeric_target] run scoreboard players get @s kc_numeric') === null) wearer.addTag('kc_numeric_conflict')
      if (ctx.commandResult('execute as @e[tag=kc_numeric_target] run return 3') === 3) wearer.addTag('kc_numeric_consistent')
      if (ctx.commandResult('kc_unknown_command') === null) wearer.addTag('kc_numeric_invalid')
      if (!ctx.commandSucceeds('kc_unknown_command')) wearer.addTag('kc_invalid_command')
      if (!ctx.commandSucceeds('tag @s add kc_canceled')) wearer.addTag('kc_cancel_seen')
      if (!ctx.commandSucceeds(' / ') && !ctx.commandSucceeds('tag @s add a\ntag @s add b')) wearer.addTag('kc_empty_commands')
    }
  },
  onHit(ctx) {
    const wearer = ctx.entity()
    if (ctx.attacker() && ctx.attacker().getId() === wearer.getId() && ctx.target().entityTags().contains('kc_script_target')) {
      wearer.addTag(ctx.target().isAlive() ? 'kc_hit' : 'kc_kill')
    }
  }
})

CurioScripts.define('kubecurios_smoke:observer', {
  equipmentModifiers(ctx) {
    if (ctx.entity().entityTags().contains('kc_observe')) return CurioEffects.armor(2)
  },
  modifyOutgoingDamage(ctx) {
    if (ctx.entity().entityTags().contains('kc_observe') && ctx.damage() > 0) return CurioEffects.multiplyDamage(1.1)
  }
})

const $ChargedCapability = Java.loadClass('top.theillusivec4.curios.api.CuriosCapability')
function recordCharged(ctx) {
  const wearer = ctx.entity()
  const data = wearer.getPersistentData()
  data.putLong('kc_equipped_ticks', ctx.equippedTicks())
  data.putInt('kc_other_count', ctx.countOtherEquipped('#kubecurios_test:charged_set'))
  if (ctx.countOtherEquipped('#kubecurios_test:empty') !== 0) throw new Error('Empty tag was not empty')
  data.putInt('kc_remaining', ctx.remainingDurability() ?? -1)
  data.putInt('kc_max', ctx.maxDurability() ?? -1)
  data.putDouble('kc_percent', ctx.remainingDurabilityPercent() ?? -1)
  data.putInt('kc_unbreaking', ctx.enchantmentLevel('minecraft:unbreaking'))
}

CurioScripts.define('kubecurios_smoke:charged', {
  equipmentModifiers(ctx) {
    return CurioEffects.armor(3)
  },
  onTick(ctx) {
    recordCharged(ctx)
    const wearer = ctx.entity()
    const data = wearer.getPersistentData()
    data.putInt('kc_cost_ticks', data.getIntOr('kc_cost_ticks', 0) + 1)
    if (wearer.entityTags().contains('kc_periodic_cost')) return CurioEffects.damageDurability(1)
  },
  onHit(ctx) {
    recordCharged(ctx)
    const wearer = ctx.entity()
    if (wearer.entityTags().contains('kc_replace')) {
      const inventory = wearer.getCapability($ChargedCapability.INVENTORY)
      inventory.getStacksHandler('kubecurios_smoke_ring').orElseThrow().getStacks()
        .setStackInSlot(0, Item.of('kubecurios_smoke:charged_ring'))
    }
    if (wearer.entityTags().contains('kc_cost_hit')) return CurioEffects.damageDurability(1)
  },
  onHurt(ctx) {
    recordCharged(ctx)
    if (ctx.entity().entityTags().contains('kc_cost_hurt')) return CurioEffects.damageDurability(20)
  }
})

expectDefinitionError('Duplicate Curio script definition', () => CurioScripts.define('kubecurios_smoke:blood', { onTick() {} }))
expectDefinitionError('namespace:path', () => CurioScripts.define('unqualified', { onTick() {} }))
expectDefinitionError('Unknown Curio script callback', () => CurioScripts.define('kubecurios_smoke:bad', { nope() {} }))
expectDefinitionError('must be a function', () => CurioScripts.define('kubecurios_smoke:bad', { onHit: true }))
expectDefinitionError('at least one callback', () => CurioScripts.define('kubecurios_smoke:bad', {}))
console.info('[KubeCurios script] PASS: invalid definitions rejected')
