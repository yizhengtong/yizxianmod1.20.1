# 第三方组件声明（Third-Party Notices）

本模组（yizxianmod）以 **Jar-in-Jar** 形式内嵌了下列第三方模组。内嵌的是**未经修改的原版 jar**，
其自身的许可证与版权声明随该 jar 一起分发（见 jar 内 `licenses/LICENSE`、`licenses/GPL`、`licenses/GPL.LESSER`、`README.md`）。

## Curios API

| 项 | 内容 |
| --- | --- |
| 文件 | `libs/curios-forge-5.14.1+1.20.1.jar`（modId `curios`） |
| 作者 | C4（TheIllusiveC4） |
| 版权 | Copyright (C) 2018-2020 C4 |
| 许可证 | GNU Lesser General Public License v3.0 or later（LGPL-3.0-or-later） |
| 源码 | https://github.com/TheIllusiveC4/Curios |
| 发布页 | https://www.curseforge.com/minecraft/mc-mods/curios |
| Maven | `top.theillusivec4.curios:curios-forge:5.14.1+1.20.1`（https://maven.theillusivec4.top/） |
| 内嵌位置 | `META-INF/jarjar/curios-forge-5.14.1+1.20.1.jar` |

### 为什么可以内嵌

LGPL-3.0 明确允许再分发（redistribute）本库，包括把它作为「组合作品（Combined Work）」的一部分分发，
前提是保留版权与许可证声明、不改变其许可证、并让使用者能够替换/修改该库。本项目按以下方式满足：

1. **未修改**：内嵌的是官方发布的原始 jar，未做任何字节级修改
   （`META-INF/jarjar/curios-forge-5.14.1+1.20.1.jar` 的 SHA256 = `1e817919…8e51f`，与 libs/ 内的原文件逐字节一致，
   构建里的 reobf 步骤不会碰嵌套 jar）。
2. **声明与许可证全文**：本 jar 自带 `META-INF/licenses/LICENSE-LGPL-3.0.txt` 与 `META-INF/licenses/LICENSE-GPL-3.0.txt`
   （LGPL-3.0 正文 + 其引入的 GPL-3.0 正文），另有 `META-INF/THIRD-PARTY-NOTICES.md` 与 `mods.toml` 的 `credits` 字段
   给出作者、许可证与源码地址。
   注：Curios 原版 jar 内的 `licenses/` 目录里两个文件名看起来是反的 —— `licenses/GPL` 里其实是 **LGPL** 正文、
   `licenses/GPL.LESSER` 里其实是 **GPL** 正文（本仓库 `META-INF/licenses/` 下的副本已按正文内容正确命名）。
3. **可替换**：玩家把任意版本的 Curios 直接放进 `mods/`，Forge 的 JarJar 选择器会**丢弃内嵌副本**并改用玩家的版本
   （见 `JarSelector` 的 `claimed` 机制），因此使用者始终可以自行升级/替换该库。
   这也是 LGPL §4(d)(1) 所说的「合适的共享库机制」：运行时优先使用用户系统里已有的库副本。

> 说明：以上为工程实现层面的合规措施，不构成法律意见；如需公开分发到 CurseForge / Modrinth 等平台，
> 还需遵守平台自身的「再分发第三方模组」规则，并建议先与原作者确认。

## 未内嵌但需要的前置

| 模组 | 关系 | 说明 |
| --- | --- | --- |
| `yizmodqzk` | 必需前置（未内嵌） | 由玩家/整合包单独安装，`mods.toml` 中声明 `mandatory = true` |
