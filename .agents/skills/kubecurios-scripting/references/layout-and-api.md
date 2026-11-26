# 工程布局与 API 核对

## 核对来源

1. 以实际目标实例的 Minecraft、加载器与模组版本为准。仓库内读 `gradle.properties`、`build.gradle` 和 `docs/wiki/`；仓库外向用户取得版本表和实际 JAR，不能把本仓库的基线当作自动兼容承诺。
2. 在 [KubeJS Wiki](https://kubejs.com/wiki) 查[目录与生命周期](https://kubejs.com/wiki/folder-structure)、[物品注册](https://kubejs.com/wiki/tutorials/item-registry)或[配方](https://kubejs.com/wiki/tutorials/recipes)；在 [KubeCurios Wiki](https://github.com/Mxpea/kubecurios/blob/master/docs/wiki/Home.md) 查脚本 API、槽位、外观及诊断子页。在线 `master` 可能先于发行包，同分支 [raw 首页](https://raw.githubusercontent.com/Mxpea/kubecurios/master/docs/wiki/Home.md)及对应 raw 子页可作为网页回退。记录实际查阅的子页、适用版本或提交、访问失败及替代证据；不能只记 HTTP 成功。
3. 核对 KubeCurios 的 `CurioBuilder`、`CurioScripts`、`CurioScriptRuntime`、`CurioScriptContext` 和对应实现；对普通 KubeJS Builder、配方或资源生成查目标 JAR（例如 `jar tf`、`javap -classpath <jar> <class>`）或该版本源码。查注册项、原始资源 JSON、模组元数据；方法存在不代表支持某个脚本参数、所有实体或指定生命周期。文档和实际版本冲突时以可核实的目标实现为准，解释差异；还不确定就停在该事项，不做猜测。

## 交付结构

```text
kjs/
  kubejs/
    startup_scripts/
      <project>.js                       # 物品注册，按大类分段
      <project>_<item-or-effect>.js       # 一个文件定义一个命名效果
    server_scripts/
      <project>_recipes.js                # 需要时：ServerEvents.recipes
    client_scripts/                      # 仅确需客户端事件时
    assets/<namespace>/                  # 贴图、模型、装备资源、语言文件
    data/<namespace>/                    # 仅确需并验证能从此加载的服务端数据
  datapacks/                             # 可选，独立于 kubejs/
    <project>_curios/
      pack.mcmeta
      data/<namespace>/curios/slots/<slot>.json
      data/<namespace>/curios/entities/<assignment>.json  # 可选的独立实体分配
  test.md
```

- `<project>` 用确认后的合法命名空间；物品、效果、配方和资源用明确的 `namespace:path`。大类用注册文件的小节组织，不因此强制制造新槽位或创造栏。修改现有项目时保持用户已经使用的物品 ID，迁移 ID 需单独确认；避免全局变量、重复物品／效果 ID 和跨项目污染。
- 将 `kjs/kubejs/` 合并到目标实例的 `kubejs/`，联机同步启动脚本和客户端所需资源；将 `kjs/datapacks/` 下每个包目录装入目标世界的 `datapacks/`，保证 `pack.mcmeta` 直接在包目录里。专用服通常为 `<level-name>/datapacks/`，单人世界为 `saves/<世界>/datapacks/`。有同名文件先确认合并方式，勿覆盖原配置。不要把整个 `kjs/` 当成一个数据包，也不要把测试存档或模组 JAR 打入脚本交付。
- 注册集中在 `StartupEvents.registry('item', ...)`。命名效果定义必须在 `startup_scripts` 文件**顶层**，不是注册事件内部、`server_scripts` 或 `client_scripts`。物理文件名只用于维护，`.script(...)` 按完整 ID 查找，不按文件路径解析，也不需要人为加 `00`/`zz` 排序；同一物品只绑定一个效果，多个物品可以引用同一效果。无自定义命名效果时不造空定义。

### 最小配对示例（26.1.2 基线）

`kubejs/startup_scripts/moonforge.js`：

```js
StartupEvents.registry('item', event => {
  // 戒指
  event.create('moonforge:ward_ring', 'kubecurios:curio')
    .slot('moonforge_ring')
    .displayName('守望戒指')
    .texture('moonforge:item/ward_ring')
    .maxDamage(64)
    .script('moonforge:ward_ring')
})
```

`kubejs/startup_scripts/moonforge_ward_ring.js`：

```js
CurioScripts.define('moonforge:ward_ring', {
  onTick(ctx) {
    return CurioEffects.damageDurability(1)
  }
})
```

示例需要 `kubejs/assets/moonforge/textures/item/ward_ring.png`，以及已定义、分配到目标实体且启用 `kubecurios:whitelist` 的 `moonforge_ring`。它是每 20 个实体 tick 请求扣 1 点耐久的写法，不是按攻击扣损。

## 容易出错的边界

- **槽位**：`.slot(...)` 为新饰品加入白名单，`CuriosWhitelist.allow(slot, itemId)` 只给已有物品装备资格。槽位定义、实体分配和验证器是三个不同条件。26.1.2 不用 `ServerEvents.tags` 创建新标签来绑定槽位；按 `examples/attribute-smoke/datapack/pack.mcmeta` 复核数据包格式（基线 `min_format: 101`、`max_format: [101, 1]`），按 Wiki 选 `curios/slots` 与 `curios/entities` 配置。扩展已有槽时按需保留 `curios:tag`，不随意 `replace: true`。其他用途的标签（如套装判断）可用已有或实际加载的数据包标签，注意基线使用 `data/<namespace>/tags/item/`。
- **脚本时机**：Java 的 `ICurioItem.curioTick(...)` 不是公开的 `CurioScripts.define` 回调；普通 `ServerEvents.tick` 也不是饰品回调。基线中 `onTick` 每 20 个实体 tick、`equipmentModifiers` 每 5 个实体 tick；`onHit` 只在归属于佩戴者的攻击使另一生物实际失去生命后触发，含致命命中和有归属的投射物。周期扣损在 `onTick` 返回 `CurioEffects.damageDurability(...)`，命中扣损才写 `onHit`；读累计时间或 `.maxDamage(...)` 不会自动扣损。功能格、来源格、创造模式、耐久附魔和只扣吸收生命都要考虑。
- **累计时长**：`ctx.equippedTicks()` 按佩戴者和物品 ID 累计，不是该次佩戴的秒表。最后一件卸下、停用或破碎后暂停；重戴同款续算，多件不重计，离线不计。需「本次佩戴时长」或「每枚物品独立计时」时先明确需求，不把累计 API 当成这两种语义。
- **Rhino 与单位**：完整物品 ID 用 `ctx.itemId()` 或 `CuriosUtils.itemId(stack)`，不要用 `stack.id`。基线属性示例为 `minecraft:max_health`、`add_value`；内置伤害百分数 `15`、脚本倍率 `1.15`、属性乘数增量 `0.15` 不可混用。命令的执行者、位置、概率、冷却与返回值依 API 核对；`commandResult` 可返回 `0` 或负数，用 `!== null` 判定有值。不要为改固定数值引入重复的全局 tick 轮询。
- **资源**：资源根是 `kubejs/assets/<namespace>/`，不要再套一层 `assets/`。确认贴图扩展名、大小写及引用（如 `.texture('moonforge:item/ward_ring')` 不带 `.png`）。提供自定义本体模型时，要核对目标版本的客户端 item 定义（如 `items/<id>.json`）、`models/item/`、贴图和 KubeJS 生成结果的引用关系，不能只放一个模型文件就宣称已绑定。盔甲式外观引用装备资源 ID（`equipment/<id>.json` 及对应 `textures/entity/equipment/` 层贴图），不是盔甲物品 ID；挂件式外观用 `.itemAppearance(...)` 复用该饰品自己的物品模型，可调骨骼挂点、位置、比例与模型的 `fixed` 变换，不是任意独立几何模型加载器。只影响人形第三人称外观，不提供护甲或占盔甲格。重启应用 Builder 和命名效果改动；仅改客户端资源时可重载资源。
- **配方**：普通配方放在 `server_scripts` 的 `ServerEvents.recipes` 中，或使用用户确认的已支持数据包格式。实际检查输入、输出、标签、配方序列化器及机器条件；模组制定的 `event.custom(...)` JSON 从该版模组的示例／实际配方核对，不把其他 MC 版本的 `result`、数量、组件字段机械移植。列出定义物品、标签、机器或序列化器的模组及其传递前置，测试使用真实依赖，不拿替身物品或原版工作台代替依赖配方。缺必需依赖或找不到准确 ID 就说明阻塞。
