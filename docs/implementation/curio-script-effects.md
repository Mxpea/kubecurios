# 饰品脚本效果：实现规格

**状态：已实现。** 目标环境及旧行为见[架构总览](../architecture.md)。脚本使用方式见[用户 Wiki](../wiki/Scripting-API.md#命名脚本效果)。命令整数、来源格查询和耐久返回值见[装备脚本补充规格](curio-script-equipment.md)。

## 本轮交付与非目标

完成 `.script(id)`、启动脚本定义、伤害前与伤害后回调、低频回调、脚本临时属性，以及真实执行并返回成功状态的 `ctx.commandSucceeds(command)`。端到端用「半血及以上增伤、半血以下且未疲惫时加护甲」验收。原有 Builder、穿脱回调、白名单和快捷效果不变。既有快捷命令仍然不根据命令结果退冷却。

不用自定义 JS 解析器、文件加载器、表达式语言或新事件总线。键位扰乱、自动选取 Boss、强制不致死、任意伤害覆盖、脚本热替换均不在本轮。`.onHitCommand` 是单独的快捷入口提案，不挡住本轮；下文的 `onHit` 是本轮的**命名脚本回调**。

## 脚本接口

```js
// kubejs/startup_scripts/kc_blood_oath.js
CurioScripts.define('mypack:blood_oath', {
  modifyOutgoingDamage(ctx) {
    const wearer = ctx.entity()
    if (wearer.getHealth() >= wearer.getMaxHealth() / 2) {
      return CurioEffects.multiplyDamage(1.2)
    }
    return null
  },

  equipmentModifiers(ctx) {
    const wearer = ctx.entity()
    if (wearer.getHealth() < wearer.getMaxHealth() / 2) {
      if (ctx.commandSucceeds('execute unless entity @s[tag=exhausted]')) {
        return CurioEffects.armor(4)
      }
    }
    return null
  }
})
```

```js
// kubejs/startup_scripts/items.js
StartupEvents.registry('item', event => {
  event.create('blood_ring', 'kubecurios:curio')
    .slot('ring') // 已定义并分配的 Curios 槽，引用 kubecurios:whitelist
    .script('mypack:blood_oath')
})
```

- `CurioScripts.define(id, handlers)` 在 `startup_scripts` 的**文件顶层**调用；`.script(id)` 在物品注册 Builder 上调用。两者的 ID 都要求完整 `namespace:path`。无路径查找或文件名约定，同一 ID 可被多个物品引用，不依赖文件排序。
- 每件饰品最多绑定一个效果 ID；重复指定同一 ID 无操作，指定第二个不同 ID 抛配置错误。效果 ID 重复定义则报告配置错误，保留首个有效定义；未知字段、非函数回调和没有任何回调的定义均拒绝。所有已列回调分别可选，不支持额外字段。
- 缺失定义：物品创建时记录含物品 ID 和效果 ID 的错误，该物品仍注册，脚本行为不启用；不影响它的槽位、内置效果和属性。重启后才应用定义变更；`/reload` 不重绑已创建物品。专用服与客户端仍按现有要求分发同一份物品注册脚本；脚本效果只在逻辑服务端运行。
- 普通条件使用 JS `if` 和 KubeJS 已能访问的实体方法（例如 `getHealth()`、`getMaxHealth()`、`getMainHandItem().isEmpty()`）。不复制一套血量/装备读取接口。`CurioEffects` 的辅助方法只创建返回值，不当场改实体。

### 回调、数据与返回值

所有回调共享 `ctx.entity()`（佩戴者，原生 `LivingEntity` 经 KubeJS/Rhino 包装）、`ctx.itemId()`（完整物品 ID）、`ctx.commandSucceeds(command)`。战斗回调另外有 `ctx.attacker()`、`ctx.target()`、`ctx.damageSource()` 和 `ctx.damage()`：`target()` 是这次伤害的受害者，`attacker()` 是 `DamageSource.getEntity()` 所指的归属生物（箭取射手；无生物归属时为 `null`）；不做准星或最近实体推断。受击回调中 `entity()` 是受害者；攻击方回调中 `entity()` 是攻击者。定期回调没有战斗字段。回调参数只面向本次调用，不允许留待以后执行 `ctx.commandSucceeds`。

| 回调 | 触发 | 返回 |
| --- | --- | --- |
| `modifyOutgoingDamage(ctx)` | 服务端 `LivingDamageEvent.Pre`，待结算伤害大于 0，归属攻击者是受害者以外的生物；查询攻击者装备。 | `CurioEffects.multiplyDamage(factor)`，或 `null`/无返回。 |
| `modifyIncomingDamage(ctx)` | 同一 `Pre`，待结算伤害大于 0；查询受害者装备。环境伤害可进入，`attacker()` 可为 `null`。 | 同上。 |
| `onHurt(ctx)` | 服务端 `LivingDamageEvent.Post`，实际生命伤害大于 0，受害者仍活着，来源是另一生物；查询受害者装备。与现有受伤命令的进入条件相同。 | 忽略返回。 |
| `onHit(ctx)` | 同一 `Post`，实际生命伤害大于 0，归属攻击者是受害者以外的生物；查询攻击者装备。致命命中也触发。 | 忽略返回。 |
| `equipmentModifiers(ctx)` | 活着的佩戴者每 5 个实体 tick 刷新一次。 | 单个属性声明、声明数组，或 `null`/无返回/空数组表示清空该效果的临时属性。 |
| `onTick(ctx)` | 活着的佩戴者每 20 个实体 tick 调用一次，不依赖是否发生战斗。 | 忽略返回。 |

- 所有脚本回调只看**启用的、非外观的、物品声明过的功能槽**。同一个佩戴者的同款物品在一次事件/刷新中只运行一次；不同物品即使引用同一脚本也各自运行。原有穿脱和原生属性仍按格处理。遍历快照用物品 ID 排序使脚本执行可重现；不重排内置效果。
- `Pre` 捕获该阶段入参为 `ctx.damage()`，同一事件的脚本都读这个值。在现有内置倍率之后，依次乘攻击方脚本、受害方脚本的倍率，最多写一次 `event.setNewDamage`。`factor` 要求是有限数值且 ≥ 0；`1.2` 是增伤 20%，显式 `0` 才是归零。沿用旧实现的零因子优先和最终数值上限，避免 `0 × Infinity` 写成 `NaN`。不开放原始可变事件给脚本。
- `Post` 的 `ctx.damage()` 是 `getHealthDamage()`，不是待结算伤害。先保留原有受伤命令，再执行脚本 `onHurt`，最后执行 `onHit`；受害者和攻击者在各自阶段查询当时的装备。无归属、全格挡或只扣吸收生命不会触发这两个回调。脚本回调不继承内置命令的冷却和概率；作者用条件或随机分支控制自己的逻辑。
- 回调抛错或返回错误类型：不阻断此次事件和其他饰品。`Pre` 忽略该条倍率；`equipmentModifiers` 清理该条脚本此前贡献的属性；其余忽略结果。按服务器/物品/效果/回调限频记录脚本诊断，包含原因；普通条件返回 `false` 不记错误。用 `try/finally` 防止同一佩戴者、物品和回调在同一个调用栈中重入，例如回调里执行伤害命令。排队后产生的新事件不等于同栈重入，仍受 Minecraft 命令链限制，不承诺任意递归脚本会终止。

### 临时属性如何应用和清理

- `CurioEffects.armor(amount)` 等价于 `CurioEffects.attribute('minecraft:armor', amount, 'add_value')`。`attribute(id, amount, operation)` 复用原生属性 ID 和现有 Builder 的运算名（含 `addition`）；`amount` 必须有限，接受负数。数组内同一个 `(属性 ID, 运算)` 重复声明视作无效结果；实体没有该属性实例则只跳过该项。未知属性 ID 是脚本错误，不要创建新的属性实例。
- 返回的是**这次希望保留的完整列表**，不是增量。按佩戴者实例和物品 ID 比较应有/已应用列表；为脚本属性使用独立且稳定的 modifier ID，例如 `kubecurios:script/<物品命名空间>/<物品路径>/<运算名>`（每个 `AttributeInstance` 有独立 modifier 表）。用 `addOrUpdateTransientModifier`，只更新变化项，只移除自身的 ID，不改属性基础值，不碰旧的 `curio/...` 原生修饰符。不会每次刷新累加，也不自动补满生命。
- 可用 NeoForge `EntityTickEvent.Post` 接入：仅逻辑服务端、活着的 `LivingEntity`，且本次运行存在周期回调或实体已有待清理修饰符时才进入。以 `entity.tickCount % 5 == 0` 为扫描点；在刷新点先更新临时属性，再在 `tickCount % 20 == 0` 时运行 `onTick`。两者共用这次装备快照并去重。没有合格饰品则清理旧条目；不每 tick 扫 Curios 库存，不为 `Pre/Post` 引入装备缓存。
- 穿脱、停用槽或换外观后的效果最晚于下一次 5-tick 刷新移除；`EntityLeaveLevelEvent` 清理旧实体实例（含离线、卸载、跨维度、实体移除），死亡的实体在后续 tick 或离开世界时也清理；服务器停止时清空服务端跟踪表。重登或换维度后由新一轮扫描重建，不序列化临时修饰符或运行表。不要用 UUID 跨实体实例复用已应用记录。动态属性不生成自动工具提示，手写提示照常工作。

## `ctx.commandSucceeds(command)`

### 对外语义

在逻辑服务端**实际执行一条命令**；以佩戴者为 `@s`，使用佩戴者调用时的维度、位置和朝向，命令权限保持现有 `.onHurtCommand` 的 `GAMEMASTER` 级别。只供受信任的启动脚本使用，不接收玩家、网络或物品数据提供的命令内容。静默指的是不向玩家刷命令反馈，不是预览或不产生副作用。允许首个 `/`，去除首尾空白，空串、单独 `/`、多行和已失效的上下文不提交，返回 `false`。

| 方法返回之前观测到的状态 | 返回 |
| --- | --- |
| 原版或 NeoForge 明确拒绝提交（如无法解析、权限不足导致命令不可用、`CommandEvent` 被取消），或提交过程报告异常 | `false` |
| 至少一个立即返回的原版成功回调 | `true`，即使回调的整数结果是 `0` |
| 收到立即失败回调、没有立即成功回调 | `false` |
| 命令已提交，但没有立即返回成败 | **`true`** |

这里的「成功」表示当前是否走 JS 的 `if` 分支。已提交时不等待、不跑第二遍、不回收结果；后来失败不回滚分支或副作用。因此，这个方法也可用于伤害前回调，只是**排队的命令没有执行完时，不能紧接着读取它本该写入的数据**。一个带分叉的 `execute … run …` 若没有执行到最后的命令，可能不会产生结果回调；若需要明确的真假，推荐以 `execute if …` 或 `execute unless …` **结束命令**。

```js
// 两种条件可以并存。不做伤害放大时返回 null。
modifyOutgoingDamage(ctx) {
  const wearer = ctx.entity()
  if (wearer.getMainHandItem().isEmpty()) {
    if (ctx.commandSucceeds('execute if entity @s[tag=fist_master]')) {
      return CurioEffects.multiplyDamage(1.2)
    }
  }
  return null
}
```

### 26.1.2 实现接缝

`runCommand` / `runCommandSilent` 以及 `Commands.performPrefixedCommand` 都返回 `void`。**不要用「没有抛异常」或「没有回调」直接推断已提交**：错误命令、取消事件和成功入队都可能没有回调。Minecraft 区分 `CommandResultCallback.onResult(boolean success, int result)` 的两个字段，只用 `success`。

新建一个包内命令适配器，沿用 `CurioCombatEffects.runCommand` 的 `CommandSourceStack` 构造方式。本方法参考目标版本 `Commands.performCommand` 的管线，在提交点保留可观测的接受状态：

1. 校验命令文本。用 `Commands.getDispatcher().parse` 和佩戴者 source 解析；向 NeoForge 发送**一次** `CommandEvent`。被取消直接返回 `false`，保留可诊断的异常信息。读取事件最终 `getParseResults()`，不要丢弃其他模组对解析结果的有效修改。
2. 按原版用 `Commands.validateParseResults` 与 `ContextChain.tryFlatten` 判断最终命令是否可执行。失败返回 `false`，配置错误限频记录，不冒充「已提交」。用 `Commands.mapSource` 在最终 source 上串接本次 `CommandResultCallback`（可用 `CommandResultCallback::chain`），保留原有回调，并用映射后的 source 构建执行链。不要手动调用 Brigadier 命令节点来绕开队列和权限。
3. 用 `Commands.executeCommandInContext(source, ...)` 和 `ExecutionContext.queueInitialCommandExecution(...)` 提交一次；可把同一个幂等结果探针传给 frame 回调，以覆盖显式返回的命令。不再调用会重新触发 `CommandEvent` 的 `performPrefixedCommand`。若已有命令上下文，原版可以仅入队；接受提交且没有可观测的拒绝时按上表结算。原版入队方法返回 `void`，不追踪队列后续是否耗尽配额。探针只存成功/失败两标志；在方法结束时取快照，迟到的回调不能重新调用 Rhino。
4. 把命令文本/解析/提交的配置错误与普通条件失败区分开来，不要在日志里逐次报告后者。保留已有 `.onHurtCommand` 的路径和冷却行为，避免为这次改动改变旧命令的反馈和错误语义。

核对位置：Gradle 的 `build/moddev/artifacts/minecraft-patched-26.1.2.130-sources.jar` 中 `net.minecraft.commands.Commands`、`net.minecraft.commands.execution.ExecutionContext` 和 `net.minecraft.commands.CommandResultCallback`；NeoForge 的 `net.neoforged.neoforge.event.CommandEvent`。测试不能只用纯 Java 模拟它们，必须覆盖一次真实的嵌套命令。

## 文件落点与实现提示

| 落点 | 要做的事 |
| --- | --- |
| `MyCuriosKubeJSPlugin` | 仅 STARTUP 绑定 `CurioScripts`、`CurioEffects`；在 `beforeScriptsLoaded`/`afterScriptsLoaded`（检查 `manager.scriptType.isStartup()`）建立并冻结该次启动脚本的定义表。全量启动文件中的 `define` 在注册回调之前执行；注册时从冻结表解析引用。 |
| `CurioBuilder` / `ScriptedCurioItem` | Builder 暂存一个命名 ID；`createObject()` 将解析结果冻结到物品定义。仍写入当前白名单和属性组件；只绑定脚本、没有内置战斗效果的物品也要能进入脚本装备查询。不要求这次先改变现有内部类名。 |
| 脚本定义与 Rhino 桥接 | 读取 `define(id, handlers)` 的 Rhino 对象，只保存经过验证的函数和其定义作用域。可由 `BindingRegistry.context()` 捕获启动期 `KubeJSContext`，用 Rhino 的 `Context.wrap` 和 `Context.callSync` 调用（参照目标 Rhino 的 `InterfaceAdapter`），不要另建原生 Rhino 环境或丢失脚本作用域。运行期对 `undefined` 与 Java `null` 统一处理，对 `CurioEffects` 的 Java 包装值先解包后校验；数组逐项校验，不依赖 JS truthiness 或隐式数字转换。 |
| `CurioCombatEffects` / `KubeCurios` | 提取与 `CuriosUtils.isEquipped` 一致的活跃功能格筛选，再分别做脚本/内置去重；接入伤害和低频刷新、清理事件。`CuriosUtils.isEquipped` 对普通物品仍有效。 |
| `examples/attribute-smoke/automated` 与 `build.gradle` | 增加专用服脚本夹具和明确的 PASS/FAIL 标记，沿用隔离世界与自动停服。保留此前已接受的日志、属性与回调断言。 |

## 实施顺序与验收

1. **基线与注册。** 运行现有 `runAttributeSmoke`；加命名绑定和 Rhino 桥接。用两个文件验证顺序无关；同一 ID 可复用；重复定义、非法 ID/回调、缺失绑定有明确诊断；缺失绑定的物品仍可穿戴。只绑脚本、没有内置效果的戒指必须能运行。至少一个回调从它的启动文件闭包读取局部值，验证没丢作用域。
2. **伤害与命令。** 先实现 `Pre/Post`，跑纯 JS 半血判断，然后接命令适配器并断言下表。固定伤害类型、基础伤害与间隔，别用概率或实际 TPS 作为验收前提。
3. **定期属性、钉住完整例子。** 接入 5/20-tick 事件和临时修饰符生命周期。失败属性结果应清掉旧加成，异常不能影响另一枚饰品。最后跑全量回归，再把已实现且验收通过的能力写入用户 Wiki。

| 场景 | 必须观察到 |
| --- | --- |
| 血量 `20/40`、`19/40`，无 `exhausted` | 正好半血时，进入 `Pre` 的 10 点伤害变 12，护甲无变化；低于半血时，这条脚本不增伤，5 tick 内护甲 +4。稳定多轮仍只 +4。 |
| 低血时添加 `exhausted`、回血、卸下、停用格、仅外观、重连 | 标签加上后或回到半血都撤掉 +4；卸下/停用也撤，外观不加。旧原生护甲、别的饰品属性不被误删；重连不重复叠加。戴两件同款脚本只加 +4，原有按格属性仍可叠加。 |
| `Pre/Post` 的角色和阶段 | `onHit` 近战与归属投射物均取正确的攻击者/受害者，致命命中只触发命中一侧；吸收伤害不触发两种 `Post`；无攻击者的环境伤害仍可跑 `modifyIncomingDamage`。回调出错不吞掉其余倍率或原生伤害。 |
| 同步真/假、非法命令、拒绝 | 有/无标签的终止式 `execute if entity @s[tag=…]` 分别为真/假；无效命令和被取消的命令都为假，不能当排队成功。`return 0` 成功但整数结果为 0，仍为真。 |
| 命令确实执行且身份正确 | `tag @s add …` 或等价有副作用的命令确实改到佩戴者，不改攻击者。`execute if entity @s[distance=..0.01]` 为真，另用佩戴者所在维度/坐标的断言校验 source；命令不是空跑。 |
| 定期触发与异常恢复 | `onTick` 在第 20、40 个实体 tick 各触发一次，同款两格不重复；`equipmentModifiers` 按 5 tick 刷新，曾返回有效属性的回调若抛错或返回非法声明，下一次刷新清理自己的属性，其他饰品继续正常运行。 |
| 真实排队 | 以原版 `/damage` 或数据包函数制造一次有生物归属的受击（如 `damage @e[tag=kc_victim,limit=1] 2 minecraft:mob_attack by @e[tag=kc_attacker,limit=1]`），在脚本中提交 `scoreboard players add @s kc_probe 1`（预设目标且佩戴者初值为 0）。调用当下记录「分支为真、分数仍为 0」，外层命令链排空后断言分数为 1，以确认命令只执行一次；再提交一条排队后明确失败的终止式条件，断言分支仍为真且不会被重复/撤回。 |
| 原有行为 | `runAttributeSmoke` 原有白名单、穿脱、生命上限叠加、命令冷却与执行者、0%/100%、倍率及提示断言全部保留。既有 `.onHurtCommand` 即使命令条件失败也不退冷却。 |

```powershell
# Java 25；可使用默认 Maven 依赖
.\gradlew.bat build runAttributeSmoke

# 或用目标版本的本地 mods 目录
.\gradlew.bat '-Pkubecurios_mods_dir=D:/path/to/mods' build runAttributeSmoke
```
