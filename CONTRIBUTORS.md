# Contributors & Funding / 贡献与资助名单

This project exists thanks to the people who spend time **and** money on it.
Lists are ordered by support date (newest first). Amounts are optional — you may stay anonymous
("Anonymous / 匿名") or list a range instead of an exact number.

本项目得以持续，靠的是投入时间与**资金**的人。名单按支持时间倒序；金额可写可不写，
也可以写区间，或直接署"匿名 / Anonymous"。

---

## 1. Funding supporters / 资金支持（主要）

| # | Supporter / 支持者 | Amount / 金额 | Date / 日期 | Note / 备注 |
| --- | --- | --- | --- | --- |
| 1 | 那狐不开提那狐 [LUOZY] | ¥50 CNY | 2026-09-16 | 公开 / public |
| 2 | _（待登记 / to be listed）_ | | | |

> **How to be added / 如何登记**：send the maintainer the name (or "Anonymous"), the amount or range,
> the date, and whether the name may be shown publicly. 把「署名（或匿名）/ 金额或区间 / 日期 / 是否公开」发给维护者即可入列。
> **Recurring support / 持续资助**：state the period (e.g. `2026-09 monthly`) and it is listed once per period.
> **Declined / 谢绝**：if you prefer not to be listed, say so and nothing is recorded.
> **Corrections / 更正**：any entry can be changed or removed at any time on request. 任何条目可随时按要求修改或删除。

---

## 2. Technical support / 技术支持

| Contributor / 贡献者 | Contribution / 贡献内容 | Period / 时间 |
| --- | --- | --- |
| **DeepSeek Harness (dsh) · deepseek-v4-flash** — AI coding agent / AI 编码代理 | Create 6.0.10 反编译取证（序列装配执行链、机械手 USE/FACING 前置、过渡物进度组件）；配方转换算法修复（标签保真、方案与配方单一顺序、原生 `create:mechanical_crafting` 额外序列化）；多人服防注入加固（`RecipeDeriver` 服务端重推导、加载柜白名单、破拆机权威退款、镜像纯文本化）；构建/网络工具链（1.0.x 构建与安装、直连/代理与迅雷下载通道）；双语文档与 QA 自检用例 | 2026-09 |
| **deepseek-v4-pro** — AI model / AI 模型 | 架构与实现评审、加固与发布方案评估 / architecture & implementation review, hardening and release assessment | 2026-09 |
| **deepseek-v4-vision-exp** — AI vision model / AI 视觉模型 | 截图判读与 GUI/资源核对（模组界面、实机产线截图取证）/ screenshot reading and GUI/asset verification | 2026-09 |
| _（待登记 / to be listed）_ | | |

> Technical support covers work done on the project's behalf by tooling/agents: code analysis, algorithm
> fixes, hardening, build & docs — not funding (see §1 for money) and not original artwork (see §3).
> 技术支持指由工具/代理代劳的工作：代码分析、算法修复、加固、构建与文档——不含资金（见 §1）与原创美术（见 §3）。

---

## 3. Code, art & testing / 代码、美术与测试贡献

| Contributor / 贡献者 | Contribution / 贡献内容 | Date / 日期 |
| --- | --- | --- |
| YUNLIN (project owner / 项目作者) | Mod design, implementation, in-game verification / 模组设计、实现、实机验证 | 2026-09 |
| _（待填 / TBD）_ | | |

Art credit / 美术来源：item icons (Generic Intermediate, Line Scheme, Mirror) and the app icon are
original drawings by the project owner (sources in `贴图/`). GUI slot skin is **runtime-referenced**
from Create's `AllGuiTextures` (no Create asset is bundled or redistributed).

美术说明：物品图标（通用中间产物 / 产线方案 / 镜像）与应用图标均为项目作者原创（源文件在 `贴图/`）；
GUI 槽位皮肤为**运行时引用** Create 的 `AllGuiTextures`，未打包或再分发任何 Create 资源。

---

## 4. Upstream & third-party / 上游与第三方

| Project / 项目 | Role / 作用 | License / 许可 |
| --- | --- | --- |
| Create (Creators of Create) | Required dependency; native recipe types & sequenced assembly / 必需前置；原生配方类型与序列装配 | Code MIT · `assets/` All Rights Reserved |
| NeoForge | Mod loader / 加载器 | LGPL-2.1 |
| JEI | Optional recipe viewer / 可选配方查看器 | MIT |
| Registrate / Ponder / Flywheel | Build/runtime libraries / 构建与运行时库 | MIT |

> Full inventory / 完整清单见 `THIRD_PARTY_NOTICES.md`；本项目自身许可见 `LICENSE`。
> Assets note / 素材说明：本模组**不打包**任何第三方素材——全部贴图与图标为作者原创（源文件 `贴图/`，
> 工程内为 `create_productionline.ico` / `icon_512x512.png` 与 `src/main/resources/assets/...`）；
> Create 的 GUI 控件仅为运行时引用。

---

_Last updated / 最后更新：2026-09-16_
