# KubeCurios Wiki

KubeCurios 为 Minecraft 26.1.2 的 KubeJS 脚本提供 Curios 饰品支持。你可以注册新饰品、让已有物品进入槽位，并配置属性、战斗效果、耐久消耗、累计佩戴时间和随身外观。

- [槽位数据包与白名单](Slot-Data-and-Whitelist.md)
- [脚本接口与属性运算](Scripting-API.md)
- [饰品佩戴外观](Wearable-Appearance.md)
- [排错、测试与重连兼容](Diagnostics-and-Verification.md)

## 安装

| 组件 | 测试版本 |
|---|---|
| Minecraft | `26.1.2` |
| Java | `25` |
| NeoForge | `26.1.2.130` |
| KubeJS | `26.1.2-8.0.8` |
| Rhino | `2101.2.8-build.91` |
| Curios API | `15.0.0+26.1.2` |
| Better Advanced Tooltips | `2601.1.0-build.11` |

将 KubeCurios JAR 和前置模组放入实例的 `mods/`。联机时，客户端和专用服都需要安装这些模组，并使用同一份饰品注册脚本。KubeJS 8.0.8 在专用服启动时也会加载 Better Advanced Tooltips 的 `BATIcons`。

[Max Health Fix 1.2](Diagnostics-and-Verification.md#重连后当前血量变为-20) 是可选的服务端兼容模组。源码构建方式见 [README](../../README.md#编译和自动验证)。

## 注册并试戴第一枚戒指

1. 将[示例启动脚本](../../examples/attribute-smoke/kubejs/startup_scripts/kubecurios_attribute_smoke.js)放入实例的 `kubejs/startup_scripts/`。它会注册 `kubecurios_smoke:ward_ring`，每枚戒指增加 4 点生命上限。
2. 把[示例数据包](../../examples/attribute-smoke/datapack)整个复制到测试世界的 `datapacks/`，可以把复制后的目录命名为 `kubecurios_attribute_smoke`。确保 `pack.mcmeta` 直接位于该目录下。专用服默认路径是 `world/datapacks/`，单人世界一般是 `saves/<世界名>/datapacks/`。示例包会创建两个测试槽位。`examples/attribute-smoke/automated/` 只供自动测试使用，其中的脚本会主动停服。
3. 重启实例。在有指令权限的测试世界输入 `/give @s kubecurios_smoke:ward_ring 2`，打开 Curios 界面并依次戴上戒指。
4. 用 `/attribute @s minecraft:max_health get` 确认生命上限从 `20` 变为 `24`、`28`，再逐枚取下看它回到 `24`、`20`。

自己制作饰品时，可以从[槽位配置](Slot-Data-and-Whitelist.md)复制 JSON，再按[脚本接口](Scripting-API.md)替换物品 ID、名称和属性。
