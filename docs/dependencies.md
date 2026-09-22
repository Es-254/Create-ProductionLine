# 依赖 / Dependencies

> [← 总体 / Overview](../README.md) · 相关： [构建与安装](build.md) · [兼容性与边界](compatibility.md) · [使用](usage.md)

## 运行前置 / Runtime prerequisites

| Item / 项 | Value / 值 |
| --- | --- |
| Platform / 平台 | NeoForge (FML 1.x) / MC `[1.21.1]` / JDK 21 |
| Prerequisites / 前置 | Create `6.0.10+` (**required** 缺失拒载); JEI `19.x` (**optional** 仅配方查看，不调用其 API) |

**EN** — Create is the hard requirement: the mod refuses to load without it. JEI is optional, used for recipe viewing only — its API is not called.

**中文** — Create 是硬前置：缺失即拒载。JEI 为可选，仅用于配方查看，本模组不调用其 API。

## 依赖声明方式 / How the dependencies are declared

**EN** — Declared in `META-INF/neoforge.mods.toml` inside the built jar (the values are filled in from `gradle.properties` at build time):

| Dependency | Type | Version range | Ordering / side |
| --- | --- | --- | --- |
| `neoforge` | required | `[21.1.249,)` | NONE / BOTH |
| `minecraft` | required | `[1.21.1]` | NONE / BOTH |
| `create` | required | `[6.0.10,7.0.0)` | NONE / BOTH |
| `jei` | optional | `[19.0.0,20.0.0)` | NONE / BOTH |

plus `loaderVersion="[1,)"`, `license="MIT"` and `displayTest="IGNORE_SERVER_VERSION"`. Bumping the NeoForge patch version only means editing `neo_version` in `gradle.properties` — see [compatibility.md](compatibility.md) for what the declared ranges mean for other loaders and MC versions.

**中文** — 声明在产物 jar 的 `META-INF/neoforge.mods.toml` 里（数值在构建时由 `gradle.properties` 填入）：

| 依赖 | 类型 | 版本范围 | ordering / side |
| --- | --- | --- | --- |
| `neoforge` | required | `[21.1.249,)` | NONE / BOTH |
| `minecraft` | required | `[1.21.1]` | NONE / BOTH |
| `create` | required | `[6.0.10,7.0.0)` | NONE / BOTH |
| `jei` | optional | `[19.0.0,20.0.0)` | NONE / BOTH |

另有 `loaderVersion="[1,)"`、`license="MIT"`、`displayTest="IGNORE_SERVER_VERSION"`。换 NeoForge 小版本时只要改 `gradle.properties` 里的 `neo_version`；声明范围对其他加载器与 MC 版本的含义见 [compatibility.md](compatibility.md)。

## 开发期依赖版本 / Development-time dependency versions

Source: the `gradle.properties` keys (the file's section heading is "Third-party dependency versions (verified against official sources, 2026-04)").

源码取自 `gradle.properties`（该文件的分节标题为 "Third-party dependency versions (verified against official sources, 2026-04)"）：

| Key | Version / 版本 | Note / 说明 |
| --- | --- | --- |
| `minecraft_version` | `1.21.1` | Must agree with the Neo version to get a valid artifact ／ 必须与 Neo 版本一致才能得到有效产物 |
| `neo_version` | `21.1.249` | Runtime requires `>=` this value, see the ranges above ／ 运行时要求 `>=` 此值，见上方依赖范围 |
| `create_version` | `6.0.10-280` | Create 6.0.10 release → the matching maven build `6.0.10-280` ／ Create 6.0.10 正式版对应的 maven 构建号 |
| `ponder_version` | `1.0.82` | Create Ponder ／ Create Ponder 版本 |
| `flywheel_version` | `1.0.6` | Flywheel ／ Flywheel 版本 |
| `jei_version` | `19.52.0.423` | JEI 19.x for 1.21.1 ／ 1.21.1 用的 JEI 19.x |

## 构建网络与代理 / Build network & proxy

**EN** — If your network cannot reach a repository, put the proxy in your **user-level** `~/.gradle/gradle.properties`, not in this repo (there is a commented example in that file).

**中文** — 如果网络访问不了仓库，代理请写在**用户级** `~/.gradle/gradle.properties` 里，别写进本仓库（本仓库 `gradle.properties` 有注释示例）。

离线构建（跳过资产下载）与只验证编译的命令见 [build.md](build.md)。

> 返回 [总体 / Overview](../README.md)
