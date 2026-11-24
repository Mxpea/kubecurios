# KubeCurios

[项目 Wiki](docs/wiki/Home.md) · 安装、槽位配置、脚本接口和排错。

为 Minecraft 26.1.2 的 KubeJS 启动脚本提供 Curios 饰品物品类型，不依赖 `ServerEvents.tags`。

## 在脚本中注册饰品

将以下脚本放入 `kubejs/startup_scripts`，然后重启游戏或服务器。专用服务器的客户端也需要安装该模组和同一份物品注册脚本。

```js
StartupEvents.registry('item', event => {
  event.create('ward_ring', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring')
    .attribute('minecraft:max_health', 4, 'add_value')
    .onEquip(ctx => console.info(`Equipped ${ctx.itemId()} in ${ctx.slot()}[${ctx.index()}]`))
    .onUnequip(ctx => console.info(`Unequipped ${ctx.itemId()} from ${ctx.slot()}[${ctx.index()}]`))
    .displayName('Ward Ring')
    .texture('minecraft:item/amethyst_shard')
})
```

- `.slot(id)` 可重复调用。它声明物品适用的槽位，不创建槽位或分配给实体。槽位 ID 是不带命名空间的字符串。
- 在 Curios 数据包中定义并分配槽位，例如使用[示例槽位 JSON](examples/attribute-smoke/datapack/data/kubecurios_smoke/curios/slots/kubecurios_smoke_ring.json)。槽位必须引用 `kubecurios:whitelist`。只引用它就是严格白名单；若同时保留 `curios:tag` 等验证器，任一通过即可。模组不会改写现有 `ring`。
- `.attribute(id, amount, operation)` 通过 Curios 的数据组件添加属性，只作用于声明的槽位。支持 `add_value`（别名 `addition`）、`add_multiplied_base` 和 `add_multiplied_total`。
- `CuriosUtils.isEquipped(entity, 'kubecurios_smoke_ring')` 查询该槽是否有正在生效的功能装备，不包括外观栏。`CuriosUtils.itemId(stack)` 从 Java 注册表读取 ID，空栈返回 `null`。
- `onEquip` 和 `onUnequip` 在服务端功能槽生效或失效时调用。回调可通过 `ctx.entity()`、`ctx.slot()`、`ctx.index()` 和 `ctx.itemId()` 获取佩戴者、槽位、索引及物品 ID。`onUnequip` 的 ID 始终是被取下的物品。重连或槽位激活状态变动也可能触发生命周期回调。修改注册脚本或回调后需要重启。

## 为饰品添加战斗效果

在 `kubecurios:curio` Builder 上配置命令和带正负号的数值。命令默认以佩戴者身份在原地执行，第三参数可选 `'attacker'`。

```js
StartupEvents.registry('item', event => {
  event.create('guardian_ring', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring')
    .onHurtCommand('effect give @s minecraft:resistance 3 0 true', 200)
    .incomingDamagePercent(-10)
    .outgoingDamagePercent(15)
    .incomingDamageTypePercent('#minecraft:is_fire', -25)
    .armor(2)
    .armorToughness(1.5)
})
```

同款饰品共享触发冷却，伤害倍率按物品 ID 去重。`.hideAttribute()` 隐藏自动生成的属性提示，保留手写提示。详见[内置战斗效果](docs/wiki/Scripting-API.md#内置战斗效果)。

## 允许已有物品进入槽位

在 `kubejs/startup_scripts` 中写入一条声明。第一参数是槽位 ID，第二参数是已注册物品的完整 ID。

```js
CuriosWhitelist.allow('kubecurios_smoke_ring', 'minecraft:amethyst_shard')
```

这条声明只扩充白名单。同一物品可分别加入多个槽位，它原有的类型、属性和装备行为由原注册方管理。每次启动服务器，KubeCurios 会针对白名单检查物品 ID、槽位定义、`kubecurios:whitelist` 验证器和实体分配。只分配给非玩家实体的槽位会记为信息，完全未分配的槽位会记录警告。

## 编译和自动验证

环境：Java 25、NeoForge `26.1.2.130`、KubeJS `26.1.2-8.0.8`、Rhino `2101.2.8-build.91`、Curios `15.0.0+26.1.2`。KubeJS `26.1.2-8.0.8` 在共用启动阶段会引用 `BATIcons`；虽然 KubeJS 的元数据把 Better Advanced Tooltips 标为客户端可选，本次实测专用服缺少它会启动失败。因此保留 `2601.1.0-build.11` 作为运行时前置。

默认使用 Gradle 中的 Maven 坐标。也可以指定现有的 `mods` 目录，直接使用其中四个版本匹配的 JAR（识别诸如 `[犀牛] ` 的文件名前缀，不会拷贝或打包前置模组）：

```powershell
.\gradlew.bat '-Pkubecurios_mods_dir=D:/path/to/mods' build runAttributeSmoke
```

`runAttributeSmoke` 只使用 `build/attribute-smoke/run` 下的独立开发服。它检查玩家槽位配置和配置错误诊断，并让测试实体实际装备两枚戒指，验证最大生命值按 **20 → 24 → 28 → 24 → 20** 变化。它还检查装备与卸下回调、查询工具、已有的紫水晶碎片能否装备，以及白名单外的石头是否被拒绝。战斗测试覆盖命令身份与位置、冷却、伤害比例和类型、护甲和韧性，以及自动提示颜色和手写描述保留。成功或断言失败后自动停服；详情见其 `logs/latest.log`。测试脚本和数据包不会打进发行 JAR。

### 客户端手动复核

只复制 `examples/attribute-smoke/kubejs/startup_scripts` 中的脚本和 `examples/attribute-smoke/datapack` 到测试实例（数据包目录放在测试世界的 `datapacks` 下）。不要复制 `automated`，里面的测试脚本会主动停服。重启后用 `/give @s kubecurios_smoke:ward_ring 2` 获取戒指，在 Curios 界面穿脱，用 `/attribute @s minecraft:max_health get` 查看玩家的生命上限。真实玩家重登、UI 和客户端同步不在自动测试覆盖范围内。
