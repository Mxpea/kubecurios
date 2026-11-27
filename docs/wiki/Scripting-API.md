# 脚本接口与属性运算

[返回首页](Home.md)

## 注册饰品

将脚本放入 `kubejs/startup_scripts/`。这里使用[自定义槽位示例](Slot-Data-and-Whitelist.md#定义和分配新槽位)中的 `ward_ring`。

```js
StartupEvents.registry('item', event => {
  event.create('kubejs:ward_ring', 'kubecurios:curio')
    .slot('ward_ring')
    .attribute('minecraft:max_health', 4, 'add_value')
    .onEquip(ctx => console.info(`装备 ${ctx.itemId()}，槽位 ${ctx.slot()}[${ctx.index()}]`))
    .onUnequip(ctx => console.info(`卸下 ${ctx.itemId()}，槽位 ${ctx.slot()}[${ctx.index()}]`))
    .displayName('守护戒指')
    .texture('minecraft:item/amethyst_shard')
})
```

`event.create` 的第一个参数是新物品 ID，第二个参数选择 `kubecurios:curio` 物品 Builder。饰品默认堆叠数为 1，可以通过普通物品 Builder 的 `.maxStackSize(...)` 修改。改动注册脚本、白名单或回调后需要重启。

| 方法 | 行为 |
|---|---|
| `.slot(slotId)` | 将当前饰品加入指定槽位的白名单。至少声明一个槽位，可以链式声明多个。槽位 ID 使用 `ring` 这样的字符串。 |
| `.attribute(attributeId, amount, operation)` | 为已声明的槽位添加一项装备属性，可以多次调用。 |
| `.onHurtCommand(command, cooldownTicks[, executor][, chancePercent])` | 受攻击并损失生命后按概率执行一条命令。默认执行者为佩戴者，概率为 100%。 |
| `.hostileDetectionRangePercent(percent)` | 调整敌对怪物察觉佩戴者的距离。`-40` 表示把原版察觉距离乘 `0.6`。 |
| `.provokeNeutralMobs(radius)` | 持续激怒半径内可攻击佩戴者的中立生物。`0` 关闭效果。 |
| `.ignoreHostileMob(typeOrTag)` | 让指定类型的敌对生物跳过对佩戴者的主动寻敌。支持实体类型 ID 和 `#` 实体类型标签，可以多次调用。 |
| `.onActiveCommand(command, cooldownTicks)` | 按主动技能键时，以佩戴者为执行者运行一条命令。 |
| `.activeSkillCooldown(cooldownTicks)` | 设置主动技能的冷却 tick 数。只写 `onActivate` 时默认为 `20`。 |
| `.incomingDamagePercent(percent)` | 调整受到的伤害。`-10` 表示减免 10%。 |
| `.outgoingDamagePercent(percent)` | 调整归属于佩戴者的伤害。`15` 表示提高 15%。 |
| `.incomingDamageTypePercent(typeOrTag, percent)` | 只调整匹配指定伤害类型或标签的受伤数值。 |
| `.armor(amount)` / `.armorToughness(amount)` | 按点数增减护甲或护甲韧性。 |
| `.hideAttribute()` | 隐藏自动生成的战斗和 Curios 属性提示。手写提示仍显示。 |
| `.script(id)` | 为饰品绑定一个在启动脚本顶层注册的命名效果。 |
| `.onEquip(callback)` | 注册服务端功能槽的装备回调。 |
| `.onUnequip(callback)` | 注册服务端功能槽的卸下回调。 |
| `.armorAppearance(part, equipmentAssetId)` | 用原版盔甲层显示饰品外观。部位可选 `head`、`chest`、`legs`、`feet`。 |
| `.itemAppearance(anchor, x, y, z, scale)` | 让 Curios 渲染层将物品模型挂在佩戴者的人形骨骼上。 |

外观和装备属性互相独立。原版材质、自定义贴图、挂点和显示规则见[饰品佩戴外观](Wearable-Appearance.md)。

## 属性 ID 和运算方式

属性须存在于 Minecraft 的内置属性注册表。可填写 `minecraft:max_health`、`minecraft:armor`、`minecraft:attack_damage`、`minecraft:attack_speed` 或 `minecraft:movement_speed` 等 ID，也可填写模组注册的属性 ID。**实体需要拥有该属性实例**才会生效。`amount` 必须是有限数值，`operation` 使用下表中的小写字符串。

| 运算方式 | 运算规则 |
|---|---|
| `add_value` 或 `addition` | 固定加减。例如 `4` 给生命上限加 4。 |
| `add_multiplied_base` | 在固定加减后，以同一个中间值计算各项比例增量，再把这些增量相加。`0.10` 表示该中间值的 10%。 |
| `add_multiplied_total` | 在前两步之后，按每项的 `1 + amount` 依次相乘。`0.10` 表示乘 `1.10`。 |

例如某属性基础值为 20，先固定加 4，再加 10% 的中间值，最后乘 `1.25`，计算过程为 `24 → 26.4 → 33`。最终结果还受该属性自身的取值范围限制。

Curios 负责在功能槽生效时添加属性，失效时移除。两件同款饰品戴在不同格子时，各格的修饰符独立。详见[实测范围](Diagnostics-and-Verification.md#已验证的行为)。

## 穿脱回调

| `ctx` 方法 | 返回值 |
|---|---|
| `ctx.entity()` | 佩戴饰品的实体。 |
| `ctx.slot()` | 槽位 ID。 |
| `ctx.index()` | 槽位内的格子索引，从 `0` 开始。 |
| `ctx.itemId()` | 物品的完整注册 ID。卸下回调返回被卸下的物品。 |

回调跟随 Curios 的功能槽生命周期。玩家重连或槽位重新激活时也可能触发 `onEquip`，槽位停用时也可能触发 `onUnequip`。用 `.attribute(...)` 配置装备属性，让 Curios 处理叠加和移除。

## 内置战斗效果

以下示例使用[自定义槽位示例](Slot-Data-and-Whitelist.md#定义和分配新槽位)中的 `ward_ring` 槽。脚本只需填写命令和数值。

```js
StartupEvents.registry('item', event => {
  event.create('kubejs:guardian_ring', 'kubecurios:curio')
    .slot('ward_ring')
    .onHurtCommand('effect give @s minecraft:resistance 3 0 true', 200)
    .incomingDamagePercent(-10)
    .outgoingDamagePercent(15)
    .incomingDamageTypePercent('#minecraft:is_fire', -25)
    .armor(2)
    .armorToughness(1.5)
    .displayName('守卫戒指')
    .texture('minecraft:item/amethyst_shard')
})
```

### 受伤后执行命令

- `onHurtCommand` 在佩戴者被其他生物攻击、实际掉血且仍存活时触发。无攻击者的环境伤害、完全挡下的攻击和只消耗吸收生命的攻击不会触发。用它给予抗性时，抗性从这次伤害结算后开始生效。
- 每件饰品配置一条命令，可以省略开头的 `/`。第三参数填 `self` 或 `attacker`，默认 `self`。命令在所选实体的维度、位置和朝向执行，`@s` 是该实体。有明确射手的箭矢使用射手作为攻击者。
- 概率使用 `0` 到 `100` 的百分数，支持小数。可直接写 `.onHurtCommand(command, 200, 25)`，以佩戴者身份按 25% 概率触发。指定执行者时，概率放在第四个参数，例如 `.onHurtCommand(command, 200, 'attacker', 25)`。省略概率时默认 100%，`0` 会禁用触发。冷却就绪的每次有效受伤都会判定，判定成功才开始冷却。
- 命令按命令方块权限执行。正整数冷却以服务端 tick 计时，`200` 在 20 TPS 下是 10 秒。同一佩戴者的同款饰品共享冷却，取下重戴不会刷新。

例如，让攻击者获得 2 秒缓慢：

```js
StartupEvents.registry('item', event => {
  event.create('kubejs:retaliation_ring', 'kubecurios:curio')
    .slot('ward_ring')
    .onHurtCommand('effect give @s minecraft:slowness 2 0 true', 200, 'attacker')
})
```

### 带正负号的伤害与护甲

伤害接口使用百分数。`10` 乘 `1.10`，`-10` 乘 `0.90`，`-100` 将该阶段的伤害减至 `0`。`0` 清除对应规则，低于 `-100` 的数值无效。

- **受到的伤害**包括环境伤害。类型规则可以填写单个类型 ID，如 `minecraft:in_fire`，或 `#` 开头的类型标签，如 `#minecraft:is_fire`。全局受伤倍率、匹配的类型倍率与攻击者的增伤倍率相乘。例如全局减免 10% 和火焰减免 25% 叠加，火焰伤害乘 `0.90 × 0.75`，即总减免 32.5%。
- **造成的伤害**按造成伤害的生物归属计算，覆盖近战和有明确射手的箭矢。伤害倍率在护甲与抗性减免后、吸收生命扣减前应用。同一物品 ID 的伤害倍率只结算一次，不同物品的倍率相乘。
- **护甲和护甲韧性**使用点数，交给 Curios 的原生属性系统处理。可与其他属性一样按装备格叠加。要调整攻击伤害属性，可用 `.attribute('minecraft:attack_damage', 0.15, 'add_multiplied_total')`。

这些效果在声明过且启用的功能槽中生效。早期脚本的 `resistanceOnHurt` 和 `damageBonusPercent` 仍可运行，新脚本可用上面的通用接口。

### 自动工具提示

受伤减免和输出提升显示为蓝色，受伤提升和输出降低显示为红色。例如 `.incomingDamagePercent(10)` 显示红色的「受到的伤害提升10%」，`-10` 显示蓝色的「受到的伤害减免10%」。护甲和韧性通过 Curios 的常规属性提示显示。调用 `.hideAttribute()` 会隐藏这些自动属性行，手写的 `.tooltip(...)`、物品描述和实际效果都保留。命令效果的说明可以通过手写提示提供。

## 敌对怪物察觉距离

`.hostileDetectionRangePercent(-40)` 会把敌对怪物用于发现佩戴玩家的原版可见度乘 `0.6`。正数增加，`0` 清除该物品的设置。下蹲、隐身和怪物头颅等原版修正继续生效。同一玩家佩戴多件同款只算一次，不同物品的倍率相乘。外观格和停用格不参与结算。

生效范围是使用原版可见度来判断距离的敌对怪物。一般的原版目标检查保留两格的距离下限，所以 `-100` 仍可能在近处被发现。视线、AI 候选搜索范围、已锁定目标和自定义寻敌逻辑仍由怪物决定。

## 生物仇恨

在注册饰品时可以叠加下面的佩戴效果。示例需要已定义并分配的 `ward_ring` 槽位。

```js
StartupEvents.registry('item', event => {
  event.create('kubejs:provocation_ring', 'kubecurios:curio')
    .slot('ward_ring')
    .provokeNeutralMobs(12)

  event.create('kubejs:truce_ring', 'kubecurios:curio')
    .slot('ward_ring')
    .ignoreHostileMob('minecraft:zombie')
    .ignoreHostileMob('#minecraft:skeletons')
})
```

- `provokeNeutralMobs` 接受 `0–128` 格的半径。每 10 个佩戴者 tick 扫描一次，以实体间的实际距离判断。多个启用功能格提供该效果时取最大半径。已驯服的生物和正在攻击其他目标的生物会被跳过。走出范围或卸下饰品会停止施加激怒，已有怒气和攻击目标由原版 AI 继续处理。
- `ignoreHostileMob` 合并所有启用功能格的类型与标签规则。排除作用于沿用原版可见度战斗判定的主动寻敌。已有攻击目标、怒气、受击反击和脚本显式设置的仇恨仍可生效。使用独立寻敌逻辑的 AI 可能继续主动选中佩戴者。
- 两种效果均生成工具提示，可用 `.hideAttribute()` 隐藏。

需要在事件中一次性修改指定生物时，可在服务端脚本调用全局 `CuriosUtils.setAggro(mob, target)` 和 `CuriosUtils.clearAggro(mob, target)`。在命名效果回调中也能调用 `ctx.setAggro(mob, target)` 和 `ctx.clearAggro(mob, target)`。例如，在 `onHurt(ctx)` 里调用 `ctx.clearAggro(ctx.attacker(), ctx.entity())`，尝试清掉攻击者对佩戴者的仇恨。

`mob` 是要修改的生物，`target` 是目标实体。调用须在服务端主线程，且两者处于同一维度。`setAggro` 在目标设置成功时返回 `true`。`clearAggro` 在清除至少一项匹配的目标或怒气状态时返回 `true`，对其他实体的仇恨会保留。清除后，生物仍可通过正常寻敌重新选中该目标。向回调方法传入空目标、在回调结束后调用或跨维度调用都会返回 `false`。

## 主动技能

进入游戏后按 **V** 激活饰品技能。可在「选项 → 控制 → 按键绑定 → KubeCurios」修改按键。每次按下会检查所有启用的功能格，同款物品只激活一次，不同物品各自结算冷却。

```js
StartupEvents.registry('item', event => {
  event.create('kubejs:veil_ring', 'kubecurios:curio')
    .slot('ward_ring')
    .hostileDetectionRangePercent(-40)
    .onActiveCommand('effect give @s minecraft:invisibility 5 0 true', 200)
})
```

`onActiveCommand` 在服务端以佩戴者为执行者、在其当前位置运行。使用命名效果时，可在 `CurioScripts.define` 中写 `onActivate(ctx)`，并用 `.activeSkillCooldown(200)` 设置冷却。同一件饰品同时声明命令与回调时，两者共用一次冷却，先运行命令。冷却从开始激活时计时，摘下重戴不会刷新。活着且处于非旁观模式的玩家可以激活技能。

察觉距离和主动技能的提示会随物品显示。`.hideAttribute()` 可隐藏这些自动提示，手写说明会保留。

## 命名脚本效果

在 `kubejs/startup_scripts/` 的文件顶层注册效果，物品 Builder 用 `.script(id)` 引用它。ID 使用完整的 `namespace:path`。定义与物品注册可以分开写，启动文件排序不影响绑定。每种物品可绑定一个效果，不同物品可以共用同一份定义。重启后应用脚本改动。

```js
const halfHealthBonus = 1.2

CurioScripts.define('mypack:blood_oath', {
  modifyOutgoingDamage(ctx) {
    const wearer = ctx.entity()
    if (wearer.getHealth() >= wearer.getMaxHealth() / 2) {
      return CurioEffects.multiplyDamage(halfHealthBonus)
    }
  },
  equipmentModifiers(ctx) {
    const wearer = ctx.entity()
    if (wearer.getHealth() < wearer.getMaxHealth() / 2 &&
        ctx.commandSucceeds('execute unless entity @s[tag=exhausted]')) {
      return CurioEffects.armor(4)
    }
  }
})

StartupEvents.registry('item', event => {
  event.create('blood_ring', 'kubecurios:curio')
    .slot('kubecurios_smoke_ring')
    .script('mypack:blood_oath')
})
```

各回调均可选，在服务端对启用的功能格执行。一次事件或刷新中，同款物品只调用一次。

| 回调 | 时机与返回值 |
|---|---|
| `modifyOutgoingDamage` | 伤害结算前查询归属攻击者的饰品。返回 `CurioEffects.multiplyDamage(factor)` 或空值。 |
| `modifyIncomingDamage` | 伤害结算前查询受害者的饰品。支持环境伤害，返回值与输出回调相同。 |
| `onHurt` | 佩戴者被另一生物攻击、损失生命且存活后调用。 |
| `onHit` | 归属于佩戴者的攻击使其他生物损失生命后调用，包括有明确射手的投射物及致命命中。 |
| `equipmentModifiers` | 每 5 个实体 tick 刷新。返回 `CurioEffects.armor(amount)`、`CurioEffects.attribute(id, amount, operation)`、声明数组或空值。空值清除该脚本的临时属性。 |
| `onTick` | 每 20 个实体 tick 调用，可用于定期效果。 |
| `onActivate` | 玩家按主动技能键且饰品冷却就绪时执行。可返回 `CurioEffects.damageDurability(...)` 请求扣耐久。 |

`ctx.entity()` 和 `ctx.itemId()` 分别返回佩戴者和完整物品 ID。战斗回调还可以调用 `ctx.attacker()`、`ctx.target()`、`ctx.damageSource()` 和 `ctx.damage()`。无生物归属时 `ctx.attacker()` 为 `null`。结算前回调读取同一次事件进入该阶段时的伤害，结算后回调读取实际生命伤害。脚本倍率在内置倍率之后相乘。

`ctx.commandSucceeds(command)` 以佩戴者的身份和位置静默提交一条命令，只能在当前回调内调用。命令即时执行时按成功状态返回，显式 `return 0` 也可以成功。解析失败、被拒绝或即时失败会返回 `false`。如果命令已入队，方法立即返回 `true`，后续结果按原版队列处理。读取刚排队命令的副作用需要等外层命令执行完毕。

临时属性使用稳定的修饰符 ID。切换条件、卸下饰品或停用槽位后，最迟在下一次 5-tick 刷新移除对应贡献。回调异常会按物品和回调限频记录，失效的 `equipmentModifiers` 会清理它此前添加的属性。动态属性的说明可以写进手工工具提示。

### 命令整数与装备状态

需要命令数值时用 `ctx.commandResult(command)`。它和 `commandSucceeds` 共用执行流程，每次调用只提交一次。成功的即时整数原样保留，包括 `0` 和负数；执行失败、仍在队列中或多个成功结果不同都返回 `null`。用 `value !== null` 检查是否取得数值。

| 接口 | 行为 |
|---|---|
| `ctx.countOtherEquipped('#mypack:blood_set')` | 数出匹配标签的启用功能格，排除本次回调的来源格。同款另一件和其他模组饰品也可计入。 |
| `ctx.equippedTicks()` | 当前佩戴者对该物品 ID 的累计佩戴 tick。同款多件每 tick 只计一次；最后一件卸下、停用或破碎后暂停，再戴同款续算。累计值随存档保留，离线时间不计入。 |
| `ctx.remainingDurability()` / `ctx.maxDurability()` | 读取来源物品栈的剩余耐久和上限。无耐久时返回 `null`。 |
| `ctx.remainingDurabilityPercent()` | 返回 `0–100` 的数值，可含小数；无耐久时返回 `null`。 |
| `ctx.enchantmentLevel('minecraft:unbreaking')` | 读取来源栈中实际写入的已注册附魔等级，未附上时为 `0`。 |

同款饰品选用槽位 ID 和格索引最靠前的启用功能格作为来源。玩家统计直接使用 KubeJS，例如 `ctx.entity().stats.getJumps()` 或 `ctx.entity().stats.getItemsPickedUp('minecraft:amethyst_shard')`，调用前确认佩戴者是服务端玩家。

`.maxDamage(128)` 可让饰品拥有耐久。`onTick`、`onHit`、`onHurt` 和 `onActivate` 可以返回 `CurioEffects.damageDurability(1)`，在回调结束后请求原版耐久结算。消耗只作用于仍在来源格、且仍启用的原物品栈，附魔减耗和破碎按原版规则处理。附魔允许列表、附魔台和铁砧的适配仍待实现。

**有耐久不等于自动扣耐久。** 要每 20 个实体 tick 请求扣 1 点，在 `onTick` 返回 `CurioEffects.damageDurability(1)`；想在造成实际生命伤害时扣损，就在 `onHit` 返回。只读 `ctx.equippedTicks()` 或 `ctx.remainingDurability()` 不会扣损。创造模式按原版规则免扣耐久。

## 白名单与查询

| 接口 | 行为 |
|---|---|
| `CuriosWhitelist.allow(slotId, itemId)` | 在启动脚本中把已有物品加入槽位白名单。第二参数使用完整物品 ID。 |
| `CuriosUtils.isEquipped(entity, slotId)` | 指定槽位只要有一个启用的功能格装备着物品，就返回 `true`。空槽、缺少该槽位或仅外观格有物品时返回 `false`。 |
| `CuriosUtils.itemId(stack)` | 从 Java 物品注册表读取完整 ID。空栈或未注册物品返回 `null`。 |
| `CuriosUtils.setAggro(mob, target)` / `CuriosUtils.clearAggro(mob, target)` | 对指定生物一次性设置或清除目标仇恨。见[生物仇恨](#生物仇恨)。 |

`slotId` 可以直接传入字符串，`CurioSlot` 类型转换已由插件处理。脚本需要读取物品 ID 时，优先用 `ctx.itemId()` 或 `CuriosUtils.itemId(stack)`，避免 Rhino 中的 `stack.id` 取值差异。
