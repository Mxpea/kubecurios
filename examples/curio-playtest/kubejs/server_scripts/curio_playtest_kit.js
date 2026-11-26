PlayerEvents.loggedIn(event => {
  const player = event.player
  const data = player.getPersistentData()
  if (data.getBooleanOr('kubecurios_demo_received_kit', false)) return
  event.server.runCommandSilent(`execute as ${player.getGameProfile().name()} run function kubecurios_demo:kit`)
  data.putBoolean('kubecurios_demo_received_kit', true)
})
