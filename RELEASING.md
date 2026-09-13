# Releasing / 发布流程

How to cut a release of **Create: Production Line** and publish it to **GitHub**, **Modrinth**
and **CurseForge**.

---

## 0. One-time setup

### 0.1 Fill in the placeholders ⚠️

The repository currently ships with placeholders that **must** be replaced before the first
public release. Grep for them:

```powershell
Select-String -Path .\gradle.properties,.\LICENSE,.\docs\platform-listing.md,.\CHANGELOG.md -Pattern '<YOUR'
```

| File | Placeholder | Replace with |
| --- | --- | --- |
| `LICENSE` | `<YOUR NAME OR HANDLE>` | Your name / handle (the MIT copyright line) |
| `gradle.properties` | `mod_authors` | Your display name (shown in-game and on the platforms) |
| `gradle.properties` | `mod_display_url` | Repository URL |
| `gradle.properties` | `mod_issue_tracker_url` | `…/issues` |
| `gradle.properties` | `modrinth_project_id` | Modrinth project id (see 0.2) |
| `gradle.properties` | `curseforge_project_id` | CurseForge numeric project id (see 0.3) |
| `docs/platform-listing.md`, `CHANGELOG.md` | `<YOUR-GITHUB>` | Your GitHub user/org |

### 0.2 Modrinth

1. Create the project at <https://modrinth.com/modrinth-create> ("Create a project").
2. Project type **Mod**, loader **NeoForge**, MC version **1.21.1**, licence **MIT**.
3. Set the **environment** to *Client and server* (the mod has both sides).
4. Upload `icon_512x512.png` as the project icon.
5. Copy the project **ID** (Settings → the slug is in the URL, the ID is shown in the project
   settings) into `modrinth_project_id`.
6. Create a **personal access token** at <https://modrinth.com/settings/pats> with the
   `Create versions` + `Write projects` scopes. Export it as `MODRINTH_TOKEN`.

### 0.3 CurseForge

1. Create the project at <https://authors.curseforge.com/>.
2. Game **Minecraft**, category **Mods**, and note the **numeric project id** (shown in the
   project's "About" panel / URL) → `curseforge_project_id`.
3. Generate an API token at <https://authors.curseforge.com/#/account> → **API Tokens**.
   Export it as `CURSEFORGE_TOKEN`.

> Keep both tokens **out of the repository**. Use environment variables (preferred) or
> `~/.gradle/gradle.properties` (`modrinth_token=…` / `curseforge_token=…`). Never commit them.

### 0.4 GitHub

The repository root is **this directory** (`create_productionline/`), not the enclosing workspace
folder — the workspace also contains decompiled third-party code and local build tooling that must
never be published.

```powershell
git init
git add .
git commit -m "Create: Production Line 1.0.0"
git branch -M main
git remote add origin https://github.com/<YOUR-GITHUB>/create_production_line.git
git push -u origin main
```

---

## 1. Prepare the version

1. Bump `mod_version` in `gradle.properties` (semantic versioning).
2. Move the `CHANGELOG.md` "Unreleased" items into a new `## [x.y.z] — YYYY-MM-DD` section and
   update the comparison link at the bottom.
3. If the description changed, edit `docs/platform-listing.md`.

## 2. Verify

```powershell
cd create_productionline

# Compiles
.\gradlew.bat compileJava --offline

# Full build → build/libs/create_productionline-<version>.jar
.\gradlew.bat build

# Headless QA self-test on a real server (6 checks, then the server halts)
.\gradlew.bat runServer -Dcreate_productionline.selfTest=true
```

Check the self-test log ends with:

```
CPL SELF-TEST RESULT: 6 passed, 0 failed
```

Then sanity-check the jar itself — it must be free of editor leftovers:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Get-Item .\build\libs\create_productionline-*.jar
$z = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
$z.Entries | Where-Object { $_.FullName -match '\.bak$|_particle|/debug/' }   # must print nothing
"$($jar.Name)  $($jar.Length) B  $($z.Entries.Count) entries"
$z.Dispose()
Get-FileHash $jar.FullName -Algorithm SHA256
```

Record the size and SHA-256 for the release notes.

## 3. Tag and publish on GitHub

```powershell
git add -A
git commit -m "Release v<version>"
git tag -a v<version> -m "Create: Production Line <version>"
git push origin main --tags
```

Then create a GitHub Release for the tag and attach
`build/libs/create_productionline-<version>.jar`. (The `build.yml` workflow also uploads the jar as
a build artifact on every push, so you can grab it from the Actions run instead.)

## 4. Publish to Modrinth + CurseForge

Tokens must be in the environment for the shell that runs Gradle:

```powershell
$env:MODRINTH_TOKEN     = "<token>"
$env:CURSEFORGE_TOKEN   = "<token>"

# Both platforms, using CHANGELOG.md as the changelog
.\gradlew.bat -PpublishMods modrinth publishCurseForge

# Or one at a time
.\gradlew.bat -PpublishMods modrinth
.\gradlew.bat -PpublishMods publishCurseForge

# Sync the project description from docs/platform-listing.md
.\gradlew.bat -PpublishMods modrinthSyncBody
```

Options:

- `-PreleaseType=beta` (or `alpha`) publishes as a pre-release on both platforms
  (default `release`).
- Nothing in the publishing path is evaluated without `-PpublishMods`, so ordinary builds and CI
  never need the tokens or the project ids.

### Manual fallback

If you would rather not hand tokens to Gradle, upload by hand:

1. **Modrinth** — project → *Versions* → *Create version*: upload the jar, pick MC `1.21.1`,
   loader `NeoForge`, paste the new `CHANGELOG.md` section as the changelog, and add **Create** as a
   required dependency.
2. **CurseForge** — project → *Files* → *Upload file*: same jar, game version
   `1.21.1 / NeoForge / Java 21`, release type, mark **Create** as a required dependency.
3. Paste `docs/platform-listing.md` (minus the HTML comment) as the project description the first
   time, and keep Modrinth/CurseForge bodies in sync with that file.

## 5. Release checklist

- [ ] Placeholders replaced (`<YOUR NAME OR HANDLE>`, `<YOUR-GITHUB>`) — nothing left in the repo
- [ ] `mod_version` bumped; `CHANGELOG.md` section written and dated
- [ ] `gradlew build` succeeds
- [ ] `runServer -Dcreate_productionline.selfTest=true` → **6 passed, 0 failed**
- [ ] Jar contains no `.bak` / `*_particle.png` / `debug/` entries
- [ ] Size + SHA-256 recorded
- [ ] Git tag pushed; GitHub Release created with the jar attached
- [ ] Modrinth version published (MC 1.21.1, NeoForge, Create = required dependency)
- [ ] CurseForge file published (1.21.1 / NeoForge / Java 21, Create = required dependency)
- [ ] Project icons uploaded (`icon_512x512.png`)
- [ ] Tokens were **not** committed (`git status` clean of secrets)

---

## Notes

- **Never** point the repository at the enclosing workspace folder: it contains
  `参考文献/create解包/` (decompiled Create), `nettest/` (local proxy tooling and certificates) and
  `_archive_logs/`. See `docs/开发文档.md` for the layout.
- Create's assets are all rights reserved. This mod only references them at runtime; never copy
  Create textures or models into `src/main/resources`.
- The `qa/SelfTest` class **is** shipped on purpose — server admins can run the same self-test.
