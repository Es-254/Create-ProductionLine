# Third-party notices

This project is licensed under the MIT License — see [LICENSE](LICENSE).
It also contains or depends on the following third-party work.

## NeoForge MDK (template)

Parts of this repository originate from the NeoForge mod development kit:
`gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.gitattributes`, the skeleton of
`build.gradle` and `settings.gradle`.

> MIT License
>
> Copyright (c) 2023 NeoForged project
>
> This license applies to the template files as supplied by
> <https://github.com/NeoForged/MDK>

The full text is kept verbatim in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).

## Create

This is an addon for [Create](https://github.com/Creators-of-Create/Create) and
depends on it at runtime, but does **not** redistribute any part of it:

- no Create code is copied — only its public API is called;
- no Create assets are bundled. The GUI and texture references are resolved from
  Create itself at runtime, so the mod's jar ships only the author's own artwork.

### Create assets are All Rights Reserved — do not copy them

Create is **not** distributed under an open-source licence: its licence is a custom,
all-rights-reserved mod licence (`LicenseRef-Create-Mod-License`), and its **art assets
(`assets/`) — textures, models, GUI sprites and sounds — are explicitly All Rights Reserved**.

Consequences that are binding for every contribution to this repository:

- **Never** copy a Create texture, model, GUI sprite, sound or `.bbmodel` into
  `src/main/resources` (or anywhere else in this repository).
- Create's GUI textures may only be **referenced by resource location at runtime** (the
  `client/CreateGui` helper does exactly this), so no bytes of Create artwork ever enter the
  built jar.
- Screenshots published with a release may show Create's UI (that is normal gameplay footage),
  but redistributable asset files may not be extracted from it.
- If a texture is needed and Create has no licence-compatible source for it, draw an original one.

The same rule is repeated in `RELEASING.md` (§Notes) and `CHANGELOG.md` (Known limits) so it is
visible on both the contribution and the release path.

## Flywheel

[Flywheel](https://modrinth.com/mod/flywheel) (`dev.engine-room.flywheel:flywheel-neoforge-*`)
is a **build- and runtime-only** dependency pulled in through Create's rendering stack. It is
licensed under the **MIT License** (`Copyright (c) Creators of Create`). This mod does not
redistribute Flywheel: it is declared as `compileOnly` (API) plus `runtimeOnly` (implementation)
in `build.gradle` and is expected to be supplied by the user's instance.

## Ponder

[Ponder](https://modrinth.com/mod/ponder) (`net.createmod.ponder:ponder-neoforge:*`) is an
`implementation` dependency of the Create toolchain used to build and dev-run this mod. It is
licensed under the **MIT License** (`Copyright (c) Creators of Create`). Nothing from Ponder is
copied into this repository or bundled in the released jar, and Ponder's own scene/asset files are
subject to the same Create-ecosystem asset restriction described above.

## JEI (Just Enough Items)

[JEI](https://modrinth.com/mod/jei) (`mezz.jei:jei-*-api` / `mezz.jei:jei-1.21.1-neoforge`) is an
**optional** dependency, declared as `type="optional"` in
`src/main/resources/META-INF/neoforge.mods.toml`. It is licensed under the **MIT License**
(`Copyright (c) mezz`).

- This mod does **not** call JEI's API: JEI is advertised as compatible purely as a recipe viewer,
  so players can inspect the generated Create recipes.
- JEI is never bundled. It appears only as `compileOnly` (API, for local verification) and
  `localRuntime` (development runs) in `build.gradle`, so it is absent from the released jar.

## Build-time only

The following are used to compile or dev-run this project and are **not** redistributed in the
released jar — each remains under its own upstream licence, which is the authoritative source for its
terms: NeoForge (`net.neoforged:neoforge`), Registrate (`com.tterrag.registrate:Registrate`) and
SpongePowered Mixin (`org.spongepowered:mixin`). Only `compileOnly` / `implementation` configurations
reference them, so no part of them ships with this mod.

## Artwork

Every texture, model and icon in this repository was drawn by the author.
