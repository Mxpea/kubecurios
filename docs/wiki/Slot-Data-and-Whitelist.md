# 槽位数据包与白名单

[返回首页](Home.md)

用 Curios 数据包声明槽位的格数、验证器和所属实体。用 `.slot(...)` 或 `CuriosWhitelist.allow(...)` 将物品 ID 加入槽位的白名单。这套流程可以绕过 KubeJS 动态创建新 Tag 时遇到的绑定问题。

## 定义和分配新槽位

在数据包的 `data/my_pack/curios/slots/ward_ring.json` 中写入以下配置。文件名去掉 `.json` 就是脚本使用的槽位 ID `ward_ring`。

```json
{
  "size": 2,
  "icon": "curios:slot/empty_ring_slot",
  "validators": ["kubecurios:whitelist"],
  "entities": ["minecraft:player"]
}
```

`size` 是初始格数，上例会给玩家两个功能槽。`entities` 也可以写在单独的分配文件里。例如将上面 JSON 中的 `entities` 字段移到 `data/my_pack/curios/entities/player_slots.json`，并用 `slots` 指定要分配的槽位。

```json
{
  "entities": ["minecraft:player"],
  "slots": ["ward_ring"]
}
```

可复制仓库中适用于 26.1.2 的 [pack.mcmeta](../../examples/attribute-smoke/datapack/pack.mcmeta)。修改过的槽位文件应放进对应的世界数据包，并重启测试实例后检查。

## 保留已有槽位的物品兼容性

Curios 15 会在**任一验证器通过**时放行物品。自定义槽位只列出 `kubecurios:whitelist`，就只接收 `.slot(...)` 和 `CuriosWhitelist.allow(...)` 登记的物品。

给已有的 `ring` 增加白名单时，可以在自己的数据包中用 `data/my_pack/curios/slots/ring.json` 扩展槽位，保留原来的标签验证。示例保留两个验证器，不使用 `replace: true`。已有符合 `curios:tag` 的饰品和新加入白名单的饰品都能继续使用该槽位。玩家还需要由数据包或现有整合包配置分配到 `ring`。

```json
{
  "validators": ["curios:tag", "kubecurios:whitelist"]
}
```

## 允许已有物品装备

在 `kubejs/startup_scripts/` 的脚本中写入槽位 ID 和已有物品的完整 ID。

```js
CuriosWhitelist.allow('ward_ring', 'minecraft:amethyst_shard')
```

该声明只授予装备资格。已有物品的属性和穿脱行为由原注册方管理。想把同一物品加入另一个槽位，给那个槽位再写一条声明。
