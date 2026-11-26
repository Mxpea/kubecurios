# 饰品佩戴外观

[返回首页](Home.md)

KubeCurios 提供两种佩戴外观：**盔甲式外观**复用 Minecraft 的盔甲层，**挂件式外观**通过 Curios 15 的 `ICurioRenderer` 把饰品的物品模型挂在人形骨骼上。两种外观可独立使用，也能用于同一件饰品。它们只改变画面，不占用原版盔甲格、不提供护甲，也不改变耐久结算。

## 盔甲式外观

在 `kubejs/startup_scripts/` 注册饰品时调用 `.armorAppearance(part, equipmentAssetId)`。例如，戴上戒指后显示金头盔和锁链靴：

```js
StartupEvents.registry('item', event => {
  event.create('kubejs:armored_ring', 'kubecurios:curio')
    .slot('ward_ring')
    .armorAppearance('head', 'minecraft:gold')
    .armorAppearance('feet', 'minecraft:chainmail')
    .displayName('覆甲戒指')
    .texture('minecraft:item/gold_ingot')
})
```

`ward_ring` 需要先在 [Curios 数据包](Slot-Data-and-Whitelist.md#定义和分配新槽位)中定义。`part` 是渲染部位，不是 Curios 槽位名，有 `head`、`chest`、`legs`、`feet` 四种。第二个参数是**装备资源 ID**，例如 `minecraft:gold`、`minecraft:diamond` 或 `minecraft:netherite`，不是 `minecraft:golden_helmet` 这样的物品 ID，也不是 PNG 路径。不同部位可重复声明，同一部位以最后一次声明为准。

只有带原版 `HumanoidArmorLayer` 的渲染器会显示这种外观，例如玩家和僵尸；实体还必须拥有允许装备这件饰品的 Curios 槽位。若对应部位已有正在渲染的原版盔甲，优先显示原版盔甲。多个饰品争同一部位时，外观栏优先，其次按 Curios 槽位 ID 和格子索引排序；同一部位只显示一件。

### 自定义盔甲资源

`.armorAppearance('chest', 'kubejs:onyx')` 对应客户端资源 `assets/kubejs/equipment/onyx.json`。例如：

```json
{
  "layers": {
    "humanoid": [{ "texture": "kubejs:onyx" }],
    "humanoid_leggings": [{ "texture": "kubejs:onyx" }],
    "humanoid_baby": [{ "texture": "kubejs:onyx" }]
  }
}
```

贴图路径分别是 `assets/kubejs/textures/entity/equipment/humanoid/onyx.png`、`assets/kubejs/textures/entity/equipment/humanoid_leggings/onyx.png` 和 `assets/kubejs/textures/entity/equipment/humanoid_baby/onyx.png`。按原版盔甲 UV 绘制，只需提供目标佩戴者与部位会用到的层。普通成人头、胸、脚使用 `humanoid`，成人腿部使用 `humanoid_leggings`；幼体通常使用 `humanoid_baby`。资源可放普通客户端资源包或 `kubejs/assets/` 对应的命名空间目录。

## 挂件式外观

`.itemAppearance(anchor, x, y, z, scale)` 在 Curios 的饰品渲染层绘制注册物品的模型。下面的红石吊坠使用原版红石图样，随胸部一起移动：

```js
StartupEvents.registry('item', event => {
  event.create('kubejs:ruby_pendant', 'kubecurios:curio')
    .slot('ward_ring')
    .displayName('红石吊坠')
    .texture('minecraft:item/redstone')
    .itemAppearance('body', 0, 5, -3.5, 0.4)
})
```

| 参数 | 含义 |
| --- | --- |
| `anchor` | `head`、`body`、`left_arm`、`right_arm`、`left_leg`、`right_leg`。左右以佩戴者自身为准。 |
| `x, y, z` | 相对所选骨骼原点的偏移，以模型像素计，`16` 等于一格。人形模型坐标的 `+Y` 朝下、`-Z` 朝前。 |
| `scale` | 正数缩放，例如 `0.4`。物品模型自己的 `fixed` 显示变换也会生效。 |

默认复用物品自身的模型和贴图；想做有厚度的挂坠、戒指或护符，可为物品提供自定义模型，通过其 `fixed` 变换调整朝向。每件饰品只保留一次 `itemAppearance` 配置，重复调用以最后一次为准。两件挂件在同一个点可能重叠；需要同时显示时，在脚本里给它们不同偏移。

## 显示规则和排错

- 两种外观都支持 Curios 外观栏。外观栏里的物品优先用于显示，不提供功能属性；关闭功能格的显示或停用功能格后，该格的功能物品不会渲染。
- 这两个接口面向第三人称的人形渲染，不添加第一人称手臂渲染。挂件式外观要求佩戴者拥有 `HumanoidModel`；非人形、自定义骨骼和脚本自绘回调不在这两个接口的范围内。
- 联机时，观察者客户端也需要相同的 KubeCurios、启动脚本和相应资源。Builder 声明改动需要重启；只改装备资源 JSON 或贴图时，重载客户端资源即可。
- 没显示时，先按 `F5` 切第三人称，检查功能格的显示开关、对应外观格和原版盔甲。盔甲式外观再检查装备资源与贴图路径；挂件式外观可先增大缩放并把 `z` 调得更负，排除模型被身体遮住。

实际样例见[饰品试玩](../../examples/curio-playtest/README.md#佩戴外观)。挂件注册方式参考 [Prunoideae/KubeJS-Curios](https://github.com/Prunoideae/KubeJS-Curios)，渲染实现使用 26.1.2 的 `SubmitNodeCollector`，不沿用旧版渲染回调签名。
