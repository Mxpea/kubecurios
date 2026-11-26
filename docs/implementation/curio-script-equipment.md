# 饰品脚本的数值、耐久与附魔

命令数值、装备查询和耐久声明已实现。附魔允许列表仍待细化。目标版本为 Minecraft 26.1.2、NeoForge 26.1.2.130、KubeJS 26.1.2-8.0.8 和 Curios 15.0.0。回调的触发条件和执行顺序沿用[饰品脚本效果实现规格](curio-script-effects.md)。

## 指令返回数值

`ctx.commandResult(command)` 真实执行一条服务端命令，沿用 `ctx.commandSucceeds(command)` 的执行者、位置、朝向、维度、权限和静默反馈。`@s` 是佩戴者。普通 JS `if`、KubeJS 实体查询、指令成败和指令数值都可以用于同一段判断。

| 返回前观察到的状态 | `commandSucceeds` | `commandResult` |
| --- | --- | --- |
| 立即报告成功并给出整数 | `true` | 原样返回整数，包括 `0` 和负数 |
| 明确拒绝，或只报告失败 | `false` | `null` |
| 已提交，尚未报告成败 | `true` | `null` |

每次调用执行一次命令。脚本需要数字时直接调用 `commandResult`，用 `value !== null` 区分可用数值和无结果。指令产生的状态修改照常生效。命令排队后照常执行，脚本在本次调用内拿到的返回值随即固定。

**实现默认值**取数优先使用 `@s` 这类单一目标。若命令在本次调用内报告多个成功结果，所有整数一致时返回该整数，有分歧时返回 `null`。同一结果从原版 source 和 frame 各回调一次时仍可正常取值。成败判断仍按至少一次立即成功即 `true` 处理。

在 `CurioScriptCommands` 复用一次提交和结果探针，内部保留接受状态、成功/失败标记和即时成功的整数。两个 `CurioScriptContext` 方法分别投影布尔值和可空整数。于返回前取快照，命令队列稍后报告的结果只属于那次命令执行。

```js
CurioScripts.define('mypack:rage', {
  modifyOutgoingDamage(ctx) {
    const rage = ctx.commandResult('scoreboard players get @s rage')
    if (rage !== null && rage > 0) {
      return CurioEffects.multiplyDamage(1 + rage / 100)
    }
    return null
  }
})
```

## 玩家统计沿用 KubeJS

`ctx.entity()` 提供佩戴者。服务端玩家通过 KubeJS 的 `stats` 读取原版统计。KubeCurios 直接复用这个接口。KubeJS 8.0.8 覆盖九类原版统计。

| 原版统计类型 | KubeJS 调用 |
| --- | --- |
| `CUSTOM` | `player.stats.getJumps()`；其他自定义统计可用原生 `Stat` 对象。实测 KubeJS 8.0.8 的 `get('minecraft:jump')` 字符串调用会报错 |
| `BLOCK_MINED` | `player.stats.getBlocksMined('minecraft:stone')` |
| `ITEM_CRAFTED` | `player.stats.getItemsCrafted('minecraft:stone')` |
| `ITEM_USED` | `player.stats.getItemsUsed('minecraft:stone')` |
| `ITEM_BROKEN` | `player.stats.getItemsBroken('minecraft:iron_pickaxe')` |
| `ITEM_PICKED_UP` | `player.stats.getItemsPickedUp('minecraft:stone')` |
| `ITEM_DROPPED` | `player.stats.getItemsDropped('minecraft:stone')` |
| `ENTITY_KILLED` | `player.stats.getKilled('minecraft:zombie')` |
| `ENTITY_KILLED_BY` | `player.stats.getKilledBy('minecraft:zombie')` |

KubeJS 也提供 `get(Stat)` 接受原生统计对象。通用脚本需要先判断佩戴者是服务端玩家，再调用玩家统计。查询有效条目但玩家尚无记录时，由原版统计返回 `0`。

## 来源格、标签和累计佩戴时间

### 来源格

现有回调每次按佩戴者和物品 ID 去重。若同款有多件生效，从合格的功能格中选定一格作为本次调用的来源格。实现默认值为按槽位 ID 排序，再按格索引升序选择。装备查询记录槽位 ID、索引和实际物品栈，同一回调的耐久读写、附魔读取和排除本件都使用这组数据。继续按既有的规则筛选可运行脚本的饰品，并保留内置战斗效果的遍历顺序。

### 其他饰品的物品标签

`ctx.countOtherEquipped('#mypack:blood_set')` 返回匹配该物品标签的启用功能格数。按格计数，排除本次回调的来源格。同款另一件、原版物品和其他模组的饰品均可计入。空格、停用格和外观格贡献 `0`。标签读取使用当前生效的物品标签。合法但没有成员的标签返回 `0`。

```js
CurioScripts.define('mypack:blood_set', {
  equipmentModifiers(ctx) {
    if (ctx.countOtherEquipped('#mypack:blood_set') >= 2) {
      return CurioEffects.armor(4)
    }
    return null
  }
})
```

### 累计佩戴时间

`ctx.equippedTicks()` 返回「佩戴者 × 物品 ID」已累计的佩戴 tick 数，首次计数前为 `0`。同一 tick 中，只要至少一件同款在可生效的功能格中佩戴，就只计一次，不受副本数量或来源格切换影响。最后一件卸下、破碎或停用时暂停，再戴同款（包括全新的物品栈）时从原值继续。外观格、背包和离线期间都不计时；重连或正常停服后重启不会清零。

服务端复用 Curios 为启用功能格调用的 `curioTick`，用当前服务器 tick 序号对同款去重，不做时间差补算，也不为计时额外扫描装备。累计值存入佩戴者的 NeoForge 实体附件，随实体或玩家存档保存；去重用的 tick 序号不写入存档。佩戴时长独立于临时属性和物品耐久，不会随来源栈清理；旧版本未保存的佩戴时间不能补算。

## 耐久和附魔

### 本件饰品的读取接口

| 调用 | 返回 |
| --- | --- |
| `ctx.remainingDurability()` | 来源格物品栈的剩余点数，例如 `32` |
| `ctx.maxDurability()` | 来源格物品栈的耐久上限，例如 `128` |
| `ctx.remainingDurabilityPercent()` | 剩余点数除以上限乘 `100`，保留小数，范围 `0–100`。`32/128` 返回 `25` |
| `ctx.enchantmentLevel('minecraft:unbreaking')` | 来源格实际物品栈上的该附魔等级，已注册附魔未附上时返回 `0` |

没有耐久的物品在三项耐久查询中返回 `null`。数值取自物品栈当前的数据组件，可用原生 `getDamageValue()` 和 `getMaxDamage()` 计算。附魔等级按完整附魔 ID 解析，读取原生附魔数据。

### 通过返回值消耗耐久

物品注册使用 KubeJS 的 `.maxDamage(128)` 定义最大耐久。`CurioEffects.damageDurability(amount)` 创建一次消耗声明，`amount` 为正整数。`onTick`、`onHit` 和 `onHurt` 可以返回该声明，`null` 或无返回时跳过消耗。伤害前回调继续返回倍率，`equipmentModifiers` 继续返回完整属性声明。

| 回调 | 每次返回声明的时机 |
| --- | --- |
| `onTick` | 每 20 个实体 tick 最多一次，沿用当前调用频率 |
| `onHit` | 每次符合 `onHit` 条件的命中最多一次 |
| `onHurt` | 每次符合 `onHurt` 条件的受击最多一次 |

框架在回调返回后确认来源格仍启用，且其中仍是本次调用选中的栈，再执行一次原生 `ItemStack.hurtAndBreak`。物品已被换走或不可损耗时跳过。这保留了耐久附魔等原生损耗规则。破碎后让 Curios 正确识别来源格变化，并清理该物品失去的脚本效果。若另一枚同款仍生效，它可参加下一次回调。

```js
CurioScripts.define('mypack:wearing_cost', {
  onTick(ctx) {
    const remaining = ctx.remainingDurability()
    if (remaining !== null && remaining > 1) {
      return CurioEffects.damageDurability(1)
    }
    return null
  }
})

StartupEvents.registry('item', event => {
  event.create('charged_ring', 'kubecurios:curio')
    .slot('ring')
    .maxDamage(128)
    .script('mypack:wearing_cost')
})
```

### 指定附魔的适用范围

设计支持为饰品指定可接受的附魔，以及通过 `ctx.enchantmentLevel(id)` 让脚本按实际等级结算。优先复用 Minecraft 的附魔注册表、物品标签和原生附魔数据。耐久损耗走原生调用，因此可以直接验证耐久附魔对消耗的作用。

**待细化**的是允许列表采用的 Builder 写法，以及附魔书铁砧、附魔台、创建时预置附魔分别支持哪些路径。配置适用范围时，应分别验证允许和拒绝的附魔在每个已支持入口中的表现。原版修补会否选中 Curios 槽，以及其他附魔能否在 Curios 槽触发其原生效果，按附魔实际行为验证。饰品脚本可以通过等级查询表达需要自己结算的效果。

## 实施顺序与验收

1. 在现有指令适配器补充整数结果，并验证布尔语义、真正副作用和嵌套命令。用 KubeJS 玩家统计验证 `CUSTOM` 与 `ITEM_PICKED_UP` 的读取。
2. 在装备查询中保留来源格，接入标签计数和佩戴计时。用两件同款加一个其他物品验证按物品去重与按格计数。
3. 在 `CurioEffects` 定义消耗声明，在 `CurioScriptRuntime` 的三个回调处理返回值，补齐本件物品的耐久和附魔查询。通过现有 `runAttributeSmoke` 的隔离专用服夹具验证回归。

| 场景 | 预期 |
| --- | --- |
| `return 0`、负分计分板、非法命令和排队 | 立即成功分别读到 `0` 和负数。非法命令与排队均无整数。已提交的排队命令仍可在布尔入口返回 `true`，并在外层命令结束前后验证它只执行了一次 |
| 玩家跳跃并拾取指定物品 | `CUSTOM` 与 `ITEM_PICKED_UP` 读到各自的原版统计增量 |
| 一个来源格、另一枚同标签同款、一个同标签其他物品 | 来源格被排除，计数为 `2`。将任一个匹配物品放入外观栏或停用其格后，计数减 `1` |
| 双戴、卸下、停用、破碎再佩戴 | 同款每 tick 仅累计一次；全部失效时暂停，恢复后接着累计，不计中间的空档 |
| 存档读回、重登或重启 | 保留存档中的累计值；离线和未加载期间不增加 |
| 读数和周期消耗 | `32/128` 返回 `32`、`128` 和 `25`。持续返回声明时每 20 tick 请求一次损耗。无耐久物品返回 `null` 并跳过损耗 |
| 命中与受击返回声明 | 符合条件的 `onHit` 与 `onHurt` 分别消耗攻击方和受击方来源格的耐久。回调返回 `null` 时耐久维持原值 |
| 本件定位、替换和破碎 | 同款两件只消耗来源格，回调内替换来源栈后新栈完好。最后一件破碎后临时效果撤销 |
| 附魔等级和实际消耗 | 已应用的指定附魔等级可读取。耐久损耗按原版规则执行。有附魔和无附魔的物品分别验证，概率相关断言使用可重现的测试条件 |
