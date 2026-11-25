// Only install in the isolated dev server: this probe stops the server after its assertions.
const $CuriosApi = Java.loadClass('top.theillusivec4.curios.api.CuriosApi')
const $CuriosDataComponents = Java.loadClass('top.theillusivec4.curios.api.CuriosDataComponents')
const $CuriosSlotTypes = Java.loadClass('top.theillusivec4.curios.api.CuriosSlotTypes')
const $CuriosCapability = Java.loadClass('top.theillusivec4.curios.api.CuriosCapability')
const $EntityType = Java.loadClass('net.minecraft.world.entity.EntityType')
const $EntitySpawnReason = Java.loadClass('net.minecraft.world.entity.EntitySpawnReason')
const $MobEffects = Java.loadClass('net.minecraft.world.effect.MobEffects')
const $Attributes = Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes')
const $TooltipContext = Java.loadClass('net.minecraft.world.item.Item$TooltipContext')
const $TooltipFlag = Java.loadClass('net.minecraft.world.item.TooltipFlag')
const $TranslatableContents = Java.loadClass('net.minecraft.network.chat.contents.TranslatableContents')
const $ArrayList = Java.loadClass('java.util.ArrayList')
const $Component = Java.loadClass('net.minecraft.network.chat.Component')
const smokeSlot = 'kubecurios_smoke_ring'
const smokeItem = 'kubecurios_smoke:ward_ring'
let smoke = null

function smokeCheck(condition, message) {
  if (!condition) throw new Error(message)
}

function stopSmoke(server, error) {
  if (smoke && smoke.entity) smoke.entity.discard()
  if (smoke && smoke.target) smoke.target.discard()
  if (smoke) smoke.level.setChunkForced(0, 0, false)
  smoke = null
  if (error) console.error(`[KubeCurios smoke] FAIL: ${error}`)
  else console.info('[KubeCurios smoke] PASS: whitelist, existing item, helper, max health 20 -> 24 -> 28 -> 24 -> 20, command cooldown, signed damage, armor and tooltip visibility')
  server.halt(false)
}

ServerEvents.loaded(event => {
  try {
    var probeWorld = event.server.overworld()
    var entity = $EntityType.ZOMBIE.create(probeWorld, $EntitySpawnReason.COMMAND)
    smoke = { level: probeWorld, entity: entity, time: 0, stage: 0 }
    smokeCheck($CuriosSlotTypes.getDefaultPlayerSlotTypes(false).containsKey(smokeSlot), 'Player has no smoke slot')
    var ring = Item.of(smokeItem)
    var stone = Item.of('minecraft:stone')
    var shard = Item.of('minecraft:amethyst_shard')
    smokeCheck(CuriosUtils.itemId(ring) === smokeItem, 'Incorrect Java registry item ID')
    smokeCheck($CuriosSlotTypes.getItemSlotTypes(ring, false).containsKey(smokeSlot), 'Whitelist rejected ring')
    smokeCheck(!$CuriosSlotTypes.getItemSlotTypes(stone, false).containsKey(smokeSlot), 'Whitelist accepted stone')
    smokeCheck($CuriosSlotTypes.getItemSlotTypes(shard, false).containsKey(smokeSlot), 'Whitelist rejected an existing item')
    var inventory = entity.getCapability($CuriosCapability.INVENTORY)
    var stacks = inventory.getCurios().get(smokeSlot).getStacks()
    smokeCheck(stacks.getSlots() === 2, 'Expected two functional slots')
    smokeCheck(!CuriosUtils.isEquipped(entity, smokeSlot), 'Empty slot was reported as equipped')
    smokeCheck(entity.getMaxHealth() === 20, 'Expected base max health 20')
    smokeCheck(!stacks.insertItem(0, stone, true).isEmpty(), 'Inventory accepted stone')
    entity.setNoAi(true)
    entity.setNoGravity(true)
    entity.setInvulnerable(true)
    entity.setPersistenceRequired()
    entity.setPos(0.5, 100.0, 0.5)
    probeWorld.setChunkForced(0, 0, true)
    smokeCheck(probeWorld.addFreshEntity(entity), 'Could not spawn probe entity')
    smokeCheck(stacks.insertItem(0, ring.copy(), false).isEmpty(), 'Inventory rejected ring')
    smoke.ring = ring
    smoke.stacks = stacks
  } catch (err) {
    stopSmoke(event.server, err)
  }
})

function hit(victim, source) {
  victim.setHealth(victim.getMaxHealth())
  victim.invulnerableTime = 0
  smokeCheck(victim.hurtServer(smoke.level, source, 4), 'Test damage did not land')
  return victim.getMaxHealth() - victim.getHealth()
}

function near(value, expected, description) {
  smokeCheck(Math.abs(value - expected) < 0.001, `${description}: expected ${expected}, got ${value}`)
}

function equipCombatRing(index) {
  smokeCheck(smoke.stacks.insertItem(index, Item.of('kubecurios_smoke:combat_ring'), false).isEmpty(), 'Combat ring was rejected')
}

function tooltipColor(lines, key) {
  for (var i = 0; i < lines.size(); i++) {
    var line = lines.get(i)
    var contents = line.getContents()
    if (contents instanceof $TranslatableContents && contents.getKey() === key) {
      return line.getStyle().getColor().getValue()
    }
  }
  return null
}

function checkTooltips() {
  var visibleStack = Item.of('kubecurios_smoke:combat_ring')
  var visible = visibleStack.getTooltipLines($TooltipContext.EMPTY, null, $TooltipFlag.NORMAL)
  smokeCheck(tooltipColor(visible, 'kubecurios.tooltip.damage.incoming.decrease') === 0x5555FF, 'Incoming reduction tooltip should be blue')
  smokeCheck(tooltipColor(visible, 'kubecurios.tooltip.damage.typed.increase') === 0xFF5555, 'Damage vulnerability tooltip should be red')
  smokeCheck(tooltipColor(visible, 'kubecurios.tooltip.damage.outgoing.increase') === 0x5555FF, 'Outgoing bonus tooltip should be blue')
  var vulnerability = Item.of('kubecurios_smoke:vulnerability_ring').getTooltipLines($TooltipContext.EMPTY, null, $TooltipFlag.NORMAL)
  smokeCheck(tooltipColor(vulnerability, 'kubecurios.tooltip.damage.incoming.increase') === 0xFF5555, 'Incoming increase tooltip should be red')

  var hiddenStack = Item.of('kubecurios_smoke:hidden_ring')
  var custom = new $ArrayList()
  custom.add($Component.literal('Manually added tooltip'))
  var hidden = hiddenStack.getTooltipLines($TooltipContext.EMPTY, null, $TooltipFlag.NORMAL)
  smokeCheck(tooltipColor(hidden, 'kubecurios.tooltip.damage.incoming.increase') === null, 'Hidden incoming modifier still has a tooltip')
  smokeCheck(tooltipColor(hidden, 'kubecurios.tooltip.damage.outgoing.decrease') === null, 'Hidden outgoing modifier still has a tooltip')
  smokeCheck(hidden.stream().anyMatch(line => line.getString() === 'Manually added tooltip'), 'hideAttribute removed custom lore')
  var curio = $CuriosApi.getCurio(hiddenStack).orElseThrow()
  smokeCheck(curio.getAttributesTooltip(custom, $TooltipContext.EMPTY).isEmpty(), 'hideAttribute did not suppress the Curios attribute lines')
  smokeCheck(!$CuriosDataComponents.getCurioAttributeModifiersOrEmpty(hiddenStack).showInTooltip(), 'Curios attribute visibility is still enabled')
}

function prepareCombatChecks() {
  var wearer = smoke.entity
  var target = $EntityType.PIG.create(smoke.level, $EntitySpawnReason.COMMAND)
  smoke.target = target
  target.setNoAi(true)
  target.setNoGravity(true)
  target.setPersistenceRequired()
  target.setPos(4.5, 100.0, 0.5)
  smokeCheck(smoke.level.addFreshEntity(target), 'Could not spawn combat target')
  wearer.setInvulnerable(false)
  wearer.getAttribute($Attributes.ARMOR_TOUGHNESS).setBaseValue(2)
  smoke.baseArmor = wearer.getAttributeValue($Attributes.ARMOR)
  smoke.outgoing = target.damageSources().mobAttack(wearer)
  smoke.incoming = wearer.damageSources().mobAttack(target)
  near(hit(target, smoke.outgoing), 4, 'Without a combat ring')
  hit(wearer, smoke.incoming)
  smokeCheck(!wearer.hasEffect($MobEffects.RESISTANCE), 'Unequipped ring triggered its command')
  checkTooltips()
  equipCombatRing(0)
  equipCombatRing(1)
}

function checkDamageAndTrigger() {
  var wearer = smoke.entity
  var target = smoke.target
  near(wearer.getAttributeValue($Attributes.ARMOR), smoke.baseArmor + 4, 'Two +2 armor modifiers')
  near(wearer.getAttributeValue($Attributes.ARMOR_TOUGHNESS), 5, 'Two +1.5 toughness modifiers')
  near(hit(target, smoke.outgoing), 4.6, 'Outgoing damage bonus with two copies')
  var arrow = $EntityType.ARROW.create(smoke.level, $EntitySpawnReason.COMMAND)
  near(hit(target, target.damageSources().arrow(arrow, wearer)), 4.6, 'Projectile damage attributed to the wearer')

  var fire = wearer.damageSources().inFire()
  var fall = wearer.damageSources().fall()
  var inventory = wearer.getCapability($CuriosCapability.INVENTORY)
  inventory.setSlotsActive(smokeSlot, false)
  near(hit(target, smoke.outgoing), 4, 'Outgoing damage with both slots disabled')
  var unmodifiedFire = hit(wearer, fire)
  var unmodifiedFall = hit(wearer, fall)
  var unmodifiedAttack = hit(wearer, smoke.incoming)
  smokeCheck(!wearer.hasEffect($MobEffects.RESISTANCE), 'Disabled slots ran a command')
  inventory.setSlotsActive(smokeSlot, true)

  near(hit(wearer, fire), unmodifiedFire * 0.9 * 0.75, 'Incoming damage and fire tag reduction')
  near(hit(wearer, fall), unmodifiedFall * 0.9 * 1.1, 'Incoming damage and fall type vulnerability')
  smokeCheck(!wearer.hasEffect($MobEffects.RESISTANCE), 'Environmental damage ran a command')
  near(hit(wearer, smoke.incoming), unmodifiedAttack * 0.9, 'Triggered hit should precede the resistance command')
  var resistance = wearer.getEffect($MobEffects.RESISTANCE)
  smokeCheck(resistance !== null && resistance.getAmplifier() === 0 && resistance.getDuration() === 20, 'Command did not apply one second of resistance to self')
  near(hit(wearer, smoke.incoming), unmodifiedAttack * 0.9 * 0.8, 'Resistance should reduce the next hit')
  smoke.triggeredAt = smoke.time
}

function checkAttackerAndHiddenModifiers() {
  var wearer = smoke.entity
  near(wearer.getAttributeValue($Attributes.ARMOR), smoke.baseArmor - 1, 'Negative armor modifier')
  near(wearer.getAttributeValue($Attributes.ARMOR_TOUGHNESS), 1.5, 'Negative toughness modifier')
  near(hit(smoke.target, smoke.outgoing), 3.2, 'Negative outgoing damage')
  var inventory = wearer.getCapability($CuriosCapability.INVENTORY)
  inventory.setSlotsActive(smokeSlot, false)
  var unmodifiedAttack = hit(wearer, smoke.incoming)
  inventory.setSlotsActive(smokeSlot, true)
  near(hit(wearer, smoke.incoming), unmodifiedAttack * 1.1, 'Positive incoming damage')
  smokeCheck(smoke.target.entityTags().contains('kubecurios_attacker'), 'Command did not run as and at the attacker')
  smokeCheck(!wearer.entityTags().contains('kubecurios_attacker'), 'Attacker command targeted the wearer')
}

ServerEvents.tick(event => {
  if (smoke === null) return
  try {
    smokeCheck(++smoke.time <= 200, `Timed out at stage ${smoke.stage}; max health is ${smoke.entity.getMaxHealth()}`)
    if (smoke.time % 5 !== 0) return
    var health = smoke.entity.getMaxHealth()
    if (smoke.stage === 0 && health === 24) {
      smokeCheck(CuriosUtils.isEquipped(smoke.entity, smokeSlot), 'Reality and helper disagreed after equip')
      smokeCheck(smoke.stacks.insertItem(1, smoke.ring.copy(), false).isEmpty(), 'Second ring was rejected')
      smoke.stage++
    } else if (smoke.stage === 1 && health === 28) {
      smoke.stacks.extractItem(0, 1, false)
      smoke.stage++
    } else if (smoke.stage === 2 && health === 24) {
      smoke.stacks.extractItem(1, 1, false)
      smoke.stage++
    } else if (smoke.stage === 3 && health === 20) {
      smokeCheck(!CuriosUtils.isEquipped(smoke.entity, smokeSlot), 'Slot still reported as occupied after removal')
      smokeCheck(smoke.stacks.insertItem(0, Item.of('minecraft:amethyst_shard'), false).isEmpty(), 'Inventory rejected an existing item')
      smoke.stage++
    } else if (smoke.stage === 4) {
      smokeCheck(health === 20 && CuriosUtils.isEquipped(smoke.entity, smokeSlot), 'Existing item should equip without adding health')
      smoke.stacks.extractItem(0, 1, false)
      prepareCombatChecks()
      smoke.stage++
    } else if (smoke.stage === 5) {
      checkDamageAndTrigger()
      smoke.stage++
    } else if (smoke.stage === 6 && smoke.time - smoke.triggeredAt >= 25) {
      var wearer = smoke.entity
      smokeCheck(!wearer.hasEffect($MobEffects.RESISTANCE), 'Resistance did not expire on time')
      smoke.stacks.extractItem(0, 1, false)
      smoke.stacks.extractItem(1, 1, false)
      equipCombatRing(0)
      equipCombatRing(1)
      hit(wearer, smoke.incoming)
      smokeCheck(!wearer.hasEffect($MobEffects.RESISTANCE), 'Re-equipping or a second copy bypassed the cooldown')
      smoke.stage++
    } else if (smoke.stage === 7 && smoke.time - smoke.triggeredAt >= 40) {
      hit(smoke.entity, smoke.incoming)
      smokeCheck(smoke.entity.hasEffect($MobEffects.RESISTANCE), 'Command did not run again after cooldown')
      smoke.entity.removeEffect($MobEffects.RESISTANCE)
      smoke.stacks.extractItem(0, 1, false)
      smoke.stacks.extractItem(1, 1, false)
      smokeCheck(smoke.stacks.insertItem(0, Item.of('kubecurios_smoke:hidden_ring'), false).isEmpty(), 'Hidden-tooltip ring was rejected')
      smoke.stage++
    } else if (smoke.stage === 8) {
      checkAttackerAndHiddenModifiers()
      smokeCheck(smoke.stacks.insertItem(1, Item.of('kubecurios_smoke:zero_chance_ring'), false).isEmpty(), 'Zero chance ring was rejected')
      hit(smoke.entity, smoke.incoming)
      smokeCheck(!smoke.entity.entityTags().contains('kubecurios_zero_chance'), 'Command with 0% chance ran')
      stopSmoke(event.server, null)
    }
  } catch (err) {
    stopSmoke(event.server, err)
  }
})
