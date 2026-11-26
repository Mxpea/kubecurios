const $DemoComponent = Java.loadClass('net.minecraft.network.chat.Component')
const $DemoPlayer = Java.loadClass('net.minecraft.server.level.ServerPlayer')
const $DemoComponents = Java.loadClass('net.minecraft.core.component.DataComponents')
const $DemoLore = Java.loadClass('net.minecraft.world.item.component.ItemLore')
const $DemoList = Java.loadClass('java.util.ArrayList')

const demoSlot = 'kubecurios_demo_ring'
const demoSet = '#kubecurios_demo:ward_set'

function demoRing(event, id, name, texture, ...lines) {
  const lore = new $DemoList()
  for (const line of lines) lore.add($DemoComponent.literal(line))
  return event.create(`kubecurios_demo:${id}`, 'kubecurios:curio')
    .slot(demoSlot).displayName(name).texture(`minecraft:item/${texture}`)
    .component($DemoComponents.LORE, new $DemoLore(lore))
}

CurioScripts.define('kubecurios_demo:hourglass', {
  onTick(ctx) {
    const player = ctx.entity()
    if (player instanceof $DemoPlayer) {
      const ticks = ctx.equippedTicks()
      player.sendSystemMessage($DemoComponent.literal(
        `积时戒指：${ticks} tick (${(ticks / 20).toFixed(1)} 秒) | 耐久 ${ctx.remainingDurability()}/${ctx.maxDurability()} | 耐久附魔 ${ctx.enchantmentLevel('minecraft:unbreaking')}`), true)
    }
  },
  onHit(ctx) {
    const player = ctx.entity()
    if (player instanceof $DemoPlayer) {
      player.sendSystemMessage($DemoComponent.literal(
        `积时戒指命中：累计 ${ctx.equippedTicks()} tick，扣损前耐久 ${ctx.remainingDurability()}/${ctx.maxDurability()}`), false)
    }
    return CurioEffects.damageDurability(1)
  }
})

CurioScripts.define('kubecurios_demo:blood_oath', {
  equipmentModifiers(ctx) {
    const wearer = ctx.entity()
    if (wearer.getHealth() < wearer.getMaxHealth() / 2 &&
        ctx.commandSucceeds('execute unless entity @s[tag=demo_exhausted]')) {
      return CurioEffects.armor(ctx.countOtherEquipped(demoSet) > 0 ? 8 : 4)
    }
  },
  modifyOutgoingDamage(ctx) {
    const wearer = ctx.entity()
    if (wearer.getHealth() >= wearer.getMaxHealth() / 2) {
      const rage = ctx.commandResult('scoreboard players get @s demo_rage')
      if (rage !== null && rage > 0) return CurioEffects.multiplyDamage(1 + Math.min(rage, 50) / 100)
    }
  }
})

StartupEvents.registry('item', event => {
  demoRing(event, 'hourglass_ring', '积时戒指', 'clock_00',
    '动作栏显示累计佩戴时间与耐久，同款共用计时。', '有效命中消耗 1 点耐久；破碎后换新戒指可继续计时。')
    .maxDamage(16).script('kubecurios_demo:hourglass')

  demoRing(event, 'guardian_ring', '守卫戒指', 'amethyst_shard',
    '被生物打伤后获得 3 秒抗性，冷却 10 秒。', '守卫套装部件。佩戴时显示金头盔外观。')
    .armorAppearance('head', 'minecraft:gold')
    .attribute('minecraft:max_health', 4, 'add_value').armor(2).armorToughness(1.5)
    .incomingDamagePercent(-10).incomingDamageTypePercent('#minecraft:is_fire', -25)
    .onHurtCommand('effect give @s minecraft:resistance 3 0 true', 200)

  demoRing(event, 'blood_ring', '血誓戒指', 'redstone',
    '生命不低于一半：每点 demo_rage 增伤 1%，最多 50%。',
    '生命低于一半：护甲 +4，搭配守卫套装部件时为 +8。',
    '带有 demo_exhausted 标签时不获得条件护甲。')
    .itemAppearance('body', 0, 5, -3.5, 0.4)
    .script('kubecurios_demo:blood_oath')

  demoRing(event, 'frost_ring', '寒霜反击戒指', 'snowball',
    '被生物打伤时有 25% 概率让攻击者缓慢 5 秒，冷却 3 秒。',
    '自身输出 +15%，摔落伤害 +25%。守卫套装部件。佩戴时显示钻石胸甲外观。')
    .armorAppearance('chest', 'minecraft:diamond')
    .onHurtCommand('effect give @s minecraft:slowness 5 1 true', 60, 'attacker', 25)
    .outgoingDamagePercent(15).incomingDamageTypePercent('minecraft:fall', 25)

  demoRing(event, 'control_ring', '隐匿对照戒指', 'echo_shard',
    '保留这条手写说明，但不显示自动属性提示。', '实际受到的伤害 +20%、输出 -20%，受伤命令概率 0%。')
    .incomingDamagePercent(20).outgoingDamagePercent(-20)
    .onHurtCommand('effect give @s minecraft:glowing 5 0 true', 60, 0)
    .hideAttribute()
})
