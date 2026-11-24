// Restart after copying this startup script into the test instance.
StartupEvents.registry('item', event => {
  event.create('kubecurios_smoke:ward_ring', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring')
    .attribute('minecraft:max_health', 4, 'add_value')
    .onEquip(ctx => console.info(`[KubeCurios smoke] equipped ${ctx.itemId()} in ${ctx.slot()}[${ctx.index()}]`))
    .onUnequip(ctx => console.info(`[KubeCurios smoke] unequipped ${ctx.itemId()} in ${ctx.slot()}[${ctx.index()}]`))
    .displayName('Ward Ring (KubeCurios smoke test)')
    .texture('minecraft:item/amethyst_shard')
})
