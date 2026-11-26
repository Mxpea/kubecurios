const $ProbeEntityType = Java.loadClass('net.minecraft.world.entity.EntityType')
const $ProbeSpawnReason = Java.loadClass('net.minecraft.world.entity.EntitySpawnReason')
const $ProbeAttributes = Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes')
const $ProbeComponent = Java.loadClass('net.minecraft.network.chat.Component')
const $ProbeCurios = Java.loadClass('top.theillusivec4.curios.api.CuriosCapability')

const probeRequest = 'kubecurios_demo_probe_hit'
const probeSlot = 'kubecurios_demo_ring'
const hourglassId = 'kubecurios_demo:hourglass_ring'

function hourglassSource(player) {
  const inventory = player.getCapability($ProbeCurios.INVENTORY)
  if (inventory === null) return null
  const slot = inventory.getStacksHandler(probeSlot).orElse(null)
  if (slot === null) return null
  const stacks = slot.getStacks()
  for (let i = 0; i < stacks.getSlots(); i++) {
    const stack = stacks.getStackInSlot(i)
    if (inventory.isSlotActive(probeSlot, i) && CuriosUtils.itemId(stack) === hourglassId) {
      return { stacks, index: i, remaining: stack.getMaxDamage() - stack.getDamageValue() }
    }
  }
  return null
}

PlayerEvents.tick(event => {
  const player = event.player
  if (!player.removeTag(probeRequest)) return
  // Run outside the command queue so commandResult can read the rage score immediately.
  const probeWorld = player.level
  const target = $ProbeEntityType.ZOMBIE.create(probeWorld, $ProbeSpawnReason.COMMAND)
  const source = hourglassSource(player)
  try {
    target.removeAllEffects()
    target.getAttribute($ProbeAttributes.ARMOR).setBaseValue(0)
    target.setHealth(target.getMaxHealth())
    target.setPos(player.getX(), player.getY(), player.getZ())
    const before = target.getHealth()
    target.hurtServer(probeWorld, target.damageSources().playerAttack(player), 8)
    var loss = (before - target.getHealth()).toFixed(2)
    let durability = '未佩戴积时戒指'
    if (source !== null) {
      var after = source.stacks.getStackInSlot(source.index)
      durability = after.isEmpty()
        ? `积时戒指 ${source.remaining} → 破碎`
        : `积时戒指 ${source.remaining} → ${after.getMaxDamage() - after.getDamageValue()}`
      if (player.hasInfiniteMaterials()) durability += '（创造模式不扣耐久）'
    }
    player.sendSystemMessage($ProbeComponent.literal(`测试命中：基础 8.00，实际扣血 ${loss} | ${durability}`), false)
  } finally {
    target.discard()
  }
})
