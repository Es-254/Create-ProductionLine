# 构建与安装 / Build & install

> [← 总体 / Overview](../README.md) · 相关： [依赖](dependencies.md) · [无头自检](qa.md) · [兼容性与边界](compatibility.md) · [配方转换](conversion.md)

## Build & install / 构建与安装

```
gradlew build -x neoFormJoined1.21.1-20240808.144430DownloadAssets   # skip asset download offline / 离线跳过资产下载
```

**EN** — Requires **JDK 21**. To check that the sources compile, `gradlew compileJava --offline` is enough; a full `gradlew build` leaves `build/libs/create_productionline-<version>.jar`. Install by dropping that jar into your instance's `mods/` folder. Build-network and proxy notes live in [`dependencies.md`](dependencies.md).

**EN — Version policy** — `gradlew build` cuts the **release** (`mod_version`, `1.0.x`); with `mod_version=1.0.3-snapshot.0.0.1` and `mod_version_type=alpha` it cuts a **snapshot** of the next line instead (a pre-release — the build and both publish scripts refuse to publish a `-snapshot.` version as `release`); `gradlew build -PdevBuild` cuts a **dev beta** (`0.0.0-dev.N`, N from `dev-build.txt`, auto-incremented after the jar is written). Only a release version is published as `release`: a dev version defaults to `beta`, and it is rejected only if you force `-PreleaseType=release`. `gradlew build -PdevBuildNumber=5` reproduces a numbered dev artifact without touching the counter, which is what CI does for a `v0.0.0-dev.N` tag.

**中文** — 需要 **JDK 21**。只想确认能不能编译，跑 `gradlew compileJava --offline` 就够了；完整的 `gradlew build` 会产出 `build/libs/create_productionline-<版本>.jar`。安装就是把 jar 放进实例的 `mods/` 目录。构建网络与代理说明见 [`dependencies.md`](dependencies.md)。

**中文 — 版本规范** — `gradlew build` 出**正式版**（取 `mod_version`，形如 `1.0.x`）；若 `mod_version=1.0.3-snapshot.0.0.1` 且 `mod_version_type=alpha`，出的则是下一条版本线的**快照**（属预发布——构建与两个发布脚本都拒绝把 `-snapshot.` 版本以 `release` 类型发布）；`gradlew build -PdevBuild` 出**开发版 beta**（`0.0.0-dev.N`，N 取自 `dev-build.txt`，出包后自动 +1）。只有正式版能以 `release` 类型发布；开发版默认就是 `beta`，只有强行 `-PreleaseType=release` 才会被发布任务拒绝。`gradlew build -PdevBuildNumber=5` 用来复现指定编号的开发包、不动计数器，CI 对 `v0.0.0-dev.N` tag 就是这么构建的。

Release flow (tag, CI, the three-platform upload) is in [`../RELEASING.md`](../RELEASING.md); per-version changes are in [`../CHANGELOG.md`](../CHANGELOG.md).

发布流程（tag、CI、三平台上传）见 [`../RELEASING.md`](../RELEASING.md)；逐版本变更见 [`../CHANGELOG.md`](../CHANGELOG.md)。

## Source layout (highlights) / 源码布局（要点）

```
com/create/productionline/
├── ProductionLineMod              @Mod entry (registration + dual-side event buses) / @Mod 主类（注册 + 双端事件总线挂载）
├── registry/                      ModBlocks/Items/BlockEntities/MenuTypes/CreativeTabs
├── block(+block/entity)           production_computer / scheme_loader / dismantler + ModContainer
├── menu/ + client/screen/         three GUIs (CreateGui runtime-references Create slot skin) / 三个 GUI（CreateGui 运行时引用 Create 槽位皮肤）
├── item/                          LineSchemeItem / GenericIntermediateItem / LineSchemeMirrorItem
├── line/scheme                    LineScheme + LineSchemeSerializer (NBT incl. embedded recipe entries)
├── line/mapper                    RecipeDescriptor / ServerRecipeLookup / Mappers / MappingDictionary
│                                  / RecipeMapper (build-guide text only)
├── line/analyzer                  RecipeAnalyzer (features) + MachineSelector (machine choice / step layout)
├── recipegen                      CreateRecipePack (flat/mechanical/sequenceEntry + multi-cabinet union rebuild)
│                                  RecipeHotSwap (recipe-only refresh: owned-recipe inject / listener re-run)
│                                  RecipeDeriver (server-side single derivation entry) / 服务端唯一推导入口
├── compat/                        ClipboardCompat (carrier checks / whitelist / guide injection)
├── client/                        ClientSetup / CreateGui / ClientRecipeResolver
├── mixin/                         only two Smithing @Accessors (mixin config lists exactly those) / 仅 Smithing 两个 @Accessor
├── util/                          Names (#tag localization) / RecipeJsonReader (order- & tag-preserving)
└── qa/ event/ network/            SelfTest (22 headless checks) / events incl. the /cpl command / payloads
```

> 返回 [总体 / Overview](../README.md)
