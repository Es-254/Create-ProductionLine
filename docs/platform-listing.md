# Create: Production Line / 机械动力：产业线

**EN** — A Create addon for **Minecraft 1.21.1 / NeoForge** that converts recipes from any source (vanilla, Create, other mods) into **native Create recipe JSON** at runtime and installs them into your world — so the item can be produced by a real Create line you build.

**中文** — **MC 1.21.1 / NeoForge** 的 Create 附属模组：把任意来源配方在游戏内实时转成 **Create 原生配方 JSON** 写入世界，让目标物品沿你自己搭的真实 Create 产线生产出来。

## Use / 用法

1. **Computer / 计算机** — item to produce in the **left** slot, paper / clipboard / blank Line Scheme in the middle and right. Press **Compute**; an ordered plan, a generated native Create recipe and a build guide are written onto the carrier.
   左格放目标物品，中/右格放载体，点【计算】即生成方案 + 原生配方 + 施工指引。
2. **Loader / 加载柜** — insert the written **Line Scheme** into a **Scheme Loader**: recipes install into the world datapack and reload automatically, and several schemes combine (union).
   放入方案即写入数据包并 reload；多方案并集生效，生效时输出红石信号。
3. **Build / 搭建** — follow the plan. Assembly recipes use **Sequenced Assembly**: base first on a belt, then **one Deployer per extra material** above it.
   按方案搭建；装配类即序列装配：基底先上带，每种追加原料一台机械手。
4. **Dismantler / 破拆机** — reverts an intermediate or finished product into raw materials and leaves a read-only mirror.
   把中间产物/成品还原成原料，并留下只读镜像。

## Details / 说明

- **Real conversion / 真转换** — machine processes → flat `create:<type>`; crafting/assembly → `create:sequenced_assembly`; native `create:mechanical_crafting` also gets a non-conflicting variant.
- **Tag fidelity / 标签保真** — tags are written back as `{"tag": …}`, so any tag member matches (this is what makes tag-based recipes such as 48-round rifle ammo craftable).
- **24 built-in mappings / 24 条内建映射**, extendable in `config/create_productionline-mappings.json`, which also selects the assembly mode.
- **Honest failure / 如实报错** — an unmappable recipe reports "cannot map" instead of inventing a plan.

## Requirements / 前置

Minecraft **1.21.1** · NeoForge **21.1.249+** · Create **6.0.10+** (required 必需) · JEI 19.x (optional 可选，仅作配方查看)

## Multiplayer / 多人服

The server is the only authority: nothing stored on an item and nothing sent by a client is trusted, so forged items or packets cannot inject recipes. 服务端为唯一权威：物品 NBT 与客户端数据都不被信任，伪造物品/数据包无法注入配方。
