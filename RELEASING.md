# Releasing / 发布流程

How to cut a release of **Create: Production Line** and publish it to **Modrinth**,
**CurseForge** and **GitHub**.

> **Current state:** only Modrinth is reachable. GitHub and CurseForge are configured but not yet
> usable — follow **§M (Modrinth-only release)** below and treat §0.3 / §0.4 / §3 as *later*.

---

## M. Modrinth-only release (no GitHub, no CurseForge needed)

Everything below works today.

### M.1 One-time

1. Replace the one remaining placeholder — your display name — in **two** places:

   ```powershell
   Select-String -Path .\LICENSE,.\gradle.properties -Pattern 'YOUR NAME OR HANDLE'
   ```

   - `LICENSE` line 3 → the MIT copyright holder
   - `gradle.properties` → `mod_authors` (this is what shows on the Modrinth project page and in-game)

2. Confirm the Modrinth project is **submitted and approved** (listed). While it is still a draft the
   Modrinth API returns `404` and no version can be published:

   ```powershell
   (Invoke-RestMethod 'https://api.modrinth.com/v2/project/createproductionline' `
     -Headers @{'User-Agent'='cpl-release/1.0'}).id
   ```

   - Returns an id → listed, good to go.
   - `404` → still a draft. Submit it for review on the project page first (or publish manually —
     see M.3, the web UI also requires approval before a version becomes public).

3. `gradle.properties` already has `modrinth_project_id=createproductionline` (the slug works and is
   stable; if the API ever rejects it, paste the base62 id from the project's Settings instead).

4. Create a personal access token at <https://modrinth.com/settings/pats> with the
   **`Create versions`** and **`Write projects`** scopes.

   **Where to keep it (never in this repository):**

   ```properties
   # ~/.gradle/gradle.properties   (user level, outside any repo)
   modrinth_token=mrp_...
   ```

   Both `scripts/*.ps1` and `MINOTAUR` read that. The scripts also still honour
   `$env:MODRINTH_TOKEN` (CI), and they pass the credential to curl through a
   throw-away `--config` file rather than an `-H` argument, so the secret never
   appears in the process argument list. `.gitignore` additionally blocks
   `gradle-local.properties`, `.secrets/` and `*.token` as a safety net.

### M.2 Option A — publish with Gradle (token)

```powershell
$env:MODRINTH_TOKEN = "mrp_…"

.\gradlew.bat build
.\gradlew.bat -PpublishMods modrinth            # uploads the jar as version 1.0.0
.\gradlew.bat -PpublishMods modrinthSyncBody    # pushes docs/platform-listing.md as the description
```

`-PreleaseType=beta` (or `alpha`) publishes as a pre-release. The changelog sent with the version is
`CHANGELOG.md`; the description is `docs/platform-listing.md`. Both are already written — nothing to
copy by hand.

### M.2b If Minotaur times out (flaky international routes)

Minotaur has **no retry**: on a connection where Modrinth's edge is intermittently unreachable it
dies with `Failed to upload file to Modrinth! java.net.SocketTimeoutException: Connect timed out`,
even though `api.modrinth.com` is reachable a second later. Two hardened fallbacks live in
`scripts/` — same API calls, long timeouts and a retry loop:

```powershell
$env:MODRINTH_TOKEN = "mrp_…"

.\scripts\publish-modrinth.ps1            # uploads mod_version from gradle.properties
.\scripts\publish-modrinth.ps1 -ReleaseType beta
.\scripts\publish-modrinth.ps1 -Attempts 10

.\scripts\sync-modrinth-body.ps1          # pushes docs/platform-listing.md + the client/server flags
```

They report the real API error body instead of a wrapped exception, which is also how you find out
about things Minotaur hides (e.g. `missing field 'featured'`).

Diagnosing which host is down:

```powershell
foreach ($h in 'api.modrinth.com','cdn.modrinth.com','modrinth.com') {
  try { Invoke-WebRequest "https://$h/" -Method Head -TimeoutSec 10 -UseBasicParsing | Out-Null; "$h OK" }
  catch { "$h FAIL" }
}
```

- `api.modrinth.com` reachable but uploads time out → just retry (the scripts do).
- TCP connects but HTTPS always times out → the failure is at the TLS/SNI layer, not routing.
  A DNS/hosts-based accelerator cannot fix that; you need a real tunnel. Retrying will not help
  until the route recovers.
- `cdn.modrinth.com` / `modrinth.com` unreachable → the network's international route is down;
  wait for it (or enable the acceleration/proxy) — nothing local will fix it.
- Note the API also rate-limits: 300 requests per minute, plenty for releases.

Both scripts honour a proxy, which is the fix when only a tunnel works:

```powershell
$env:MODRINTH_PROXY = "http://127.0.0.1:31181"   # your accelerator's local proxy port
```

For the Gradle/Minotaur path, uncomment the `systemProp.*.proxyHost/Port` block in
`gradle.properties` with the same port instead.

### M.3 Option B — upload by hand (no token, no Gradle)

Nothing leaves your machine except the jar, and no credentials are involved.

1. Build: `.\gradlew.bat build`
2. Grab the file and its hash:

   ```powershell
   Get-ChildItem .\build\libs\*.jar | Select-Object Name, Length
   Get-FileHash .\build\libs\create_productionline-*.jar -Algorithm SHA256
   ```
3. On the project page: **Versions → Create version**, then

   | Field | Value |
   | --- | --- |
   | Name | `v1.0.0` (or leave default) |
   | Version number | `1.0.0` — must match `mod_version` |
   | Release channel | `Release` |
   | Game versions | `1.21.1` |
   | Loaders | `NeoForge` |
   | Dependencies | **Create** → *Required* |
   | File | `build/libs/create_productionline-1.0.0.jar` |
   | Changelog | paste the `## [1.0.0]` section of `CHANGELOG.md` |

4. If the description/icon are not set yet, upload `icon_512x512.png` as the project icon and paste
   `docs/platform-listing.md` (everything **below** the HTML comment) as the project description.

### M.4 Post-release checks

- [ ] The version page lists **NeoForge** + **1.21.1** and marks **Create** as required
- [ ] The jar downloads and its size matches the build output
- [ ] The description renders (no leftover `<!-- … -->` note, no unescaped placeholders)
- [ ] `LICENSE` / `mod_authors` show your real name, not a placeholder

---

## 0. Full setup (later — GitHub + CurseForge)

### 0.1 Fill in the remaining placeholders ⚠️

```powershell
Select-String -Path .\gradle.properties,.\docs\platform-listing.md -Pattern 'YOUR NAME OR HANDLE|YOUR-GITHUB'
```

| File | Placeholder | Replace with | Needed for |
| --- | --- | --- | --- |
| `LICENSE` | `<YOUR NAME OR HANDLE>` | Your name / handle (MIT copyright line) | **now** |
| `gradle.properties` | `mod_authors` | Your display name | **now** |
| `gradle.properties` | `curseforge_project_id` | CurseForge numeric project id (§0.3) | CurseForge |
| `gradle.properties` | `mod_issue_tracker_url` + re-add `issueTrackerURL` in `neoforge.mods.toml` | `…/issues` | GitHub |
| `mod_display_url` | already set to the Modrinth page | repository URL once it exists | GitHub |

### 0.2 Modrinth

Done — see **§M**.

### 0.3 CurseForge

Create an account / project when you can reach <https://authors.curseforge.com/>.

1. Create the project at <https://authors.curseforge.com/>.
2. Game **Minecraft**, category **Mods**, and note the **numeric project id** (shown in the
   project's "About" panel / URL) → `curseforge_project_id`.
3. Generate an API token at <https://authors.curseforge.com/#/account> → **API Tokens**.
   Export it as `CURSEFORGE_TOKEN`.

> Keep both tokens **out of the repository**. Use environment variables (preferred) or
> `~/.gradle/gradle.properties` (`modrinth_token=…` / `curseforge_token=…`). Never commit them.

### 0.4 GitHub (later — needs GitHub access)

The repository root is **this directory** (`create_productionline/`), not the enclosing workspace
folder — the workspace also contains decompiled third-party code and local build tooling that must
never be published.

```powershell
git init
git add .
git commit -m "Create: Production Line 1.0.0"
git branch -M main
git remote add origin https://github.com/Es-254/Create-ProductionLine.git
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
.\gradlew.bat runServer -PselfTest
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

## 3. Tag and publish on GitHub (later)

Only relevant once GitHub is reachable; the git repository is already initialised locally.

```powershell
git add -A
git commit -m "Release v<version>"
git tag -a v<version> -m "Create: Production Line <version>"
git remote add origin https://github.com/Es-254/Create-ProductionLine.git
git push origin main --tags
```

Then create a GitHub Release for the tag and attach
`build/libs/create_productionline-<version>.jar`. (The `build.yml` workflow also uploads the jar as
a build artifact on every push, so you can grab it from the Actions run instead.)

## 4. Publishing commands

**Modrinth only (works today)** — see **§M.2**:

```powershell
$env:MODRINTH_TOKEN = "<token>"
.\gradlew.bat -PpublishMods modrinth
.\gradlew.bat -PpublishMods modrinthSyncBody
```

**Both platforms (once CurseForge is set up)** — tokens must be in the environment of the shell
that runs Gradle:

```powershell
$env:MODRINTH_TOKEN     = "<token>"
$env:CURSEFORGE_TOKEN   = "<token>"

# Both platforms, using CHANGELOG.md as the changelog
.\gradlew.bat -PpublishMods modrinth publishCurseForge

# Or one at a time
.\gradlew.bat -PpublishMods modrinth
.\gradlew.bat -PpublishMods publishCurseForge
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

- [x] Machine-specific settings and internal docs removed; no placeholders left in the repo
- [ ] `mod_version` bumped; `CHANGELOG.md` section written and dated
- [ ] `gradlew build` succeeds
- [ ] `runServer -PselfTest` → **6 passed, 0 failed**
- [ ] Jar contains no `.bak` / `*_particle.png` / `debug/` entries
- [ ] Size + SHA-256 recorded
- [ ] Git tag pushed; GitHub Release created with the jar attached
- [ ] Modrinth version published (MC 1.21.1, NeoForge, Create = required dependency)
- [ ] CurseForge file published (1.21.1 / NeoForge / Java 21, Create = required dependency)
- [ ] Project icons uploaded (`icon_512x512.png`)
- [ ] Tokens were **not** committed (`git status` clean of secrets)

---

## Notes

- **Never** point the repository at the enclosing workspace folder: it also holds
  decompiled third-party code, local network tooling and certificates, and archived
  logs — none of which belong in a public repository.
- Create's assets are all rights reserved. This mod only references them at runtime; never copy
  Create textures or models into `src/main/resources`.
- The `qa/SelfTest` class **is** shipped on purpose — server admins can run the same self-test.
