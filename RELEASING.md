# Releasing / 发布流程

How to cut a release of **Create: Production Line** and publish it to **Modrinth**,
**CurseForge** and **GitHub**.

> **Current state (verified against this repository + the live APIs):**
>
> - **GitHub — live.** `origin` is <https://github.com/Es-254/Create-ProductionLine>, `main` is pushed
>   (21 commits), the tag `v1.0.2` exists and there is one GitHub Release for `v1.0.2`.
> - **Modrinth — submitted, in review; not yet publicly visible.** Anonymous calls return **404** for
>   both the slug `createproductionline` and the base62 id `7dcs0ruf`, but the **author view** (token)
>   returns `200` with `status = "processing"` — the project was submitted on 2026-09-13 and is going
>   through Modrinth's review/scan pipeline. Version **`1.0.2` already exists and is `listed`**
>   (1.0.0 / 1.0.1 were deleted), so publishing itself works; what is missing is only the public
>   approval that makes the project page and its versions visible. Everything else about the Modrinth
>   path (token, ids, scripts) is ready.
> - **CurseForge — not set up** (`curseforge_project_id` is empty). Treat **§0.3** and the CurseForge
>   halves of §4 / §5 as *later*.
>
> Because the repository is public and pushed, §0.4 (bootstrap) is **history rather than a to-do**, and
> §3 (tag + GitHub Release) is a normal step of every release. §M remains the day-to-day path.

---

## M. Modrinth + GitHub release

Everything below works today, except that publishing to Modrinth is blocked until the project is
approved and listed (§M.1.2).

### M.1 One-time

1. Author identity — **already filled in; no placeholder remains.** `LICENSE` line 3 reads
   `Copyright (c) 2026 Es254`, and `gradle.properties` has `mod_authors=Es254`. Run this only to
   confirm nothing regressed:

   ```powershell
   Select-String -Path .\LICENSE,.\gradle.properties -Pattern 'YOUR NAME OR HANDLE'
   ```

   - `LICENSE` line 3 → the MIT copyright holder
   - `gradle.properties` → `mod_authors` (this is what shows on the Modrinth project page and in-game)

2. Modrinth project state — the anonymous API returns `404` until the project passes review and becomes
   public, so an anonymous `404` does **not** mean "not submitted". Check the **author view** instead
   (the token comes from §M.1 step 4; never echo it):

   ```powershell
   $token = (Select-String -Path "$env:USERPROFILE\.gradle\gradle.properties" -Pattern '^modrinth_token').Line -replace '^\s*modrinth_token\s*=\s*',''
   $cfg = Join-Path $env:TEMP 'mr.cfg'
   "url = `"https://api.modrinth.com/v2/project/7dcs0ruf`"`nheader = `"Authorization: Bearer $token`"`nheader = `"User-Agent: cpl-release/1.0`"" | Set-Content $cfg -Encoding ASCII
   try { curl.exe -s -K $cfg } finally { Remove-Item $cfg -Force }
   ```

   - `status: "approved"` → public, the page and versions are visible: good to go.
   - `status: "processing"` → submitted, still in Modrinth's review/scan pipeline (this is the current
     state, verified 2026-09-16): versions can be published and are `listed`, but nothing is visible to
     players yet. Nothing to fix on our side — wait for approval.
   - `status: "draft"` → not submitted yet: submit it for review on the project page first.
   - `404` even with the token → the id is wrong (re-check `modrinth_project_id` in `gradle.properties`).

   At the time of writing the id is `7dcs0ruf`, the project `status` is `processing` and version `1.0.2`
   is `listed` — the only thing gating the Modrinth half of a release is Modrinth's own approval.

3. `gradle.properties` already has `modrinth_project_id=7dcs0ruf` — the **base62 project id**, *not*
   the slug (`createproductionline`). The id is used on purpose: it is stable even if the slug is
   renamed. You never type it by hand; the scripts and Minotaur read it from `gradle.properties`.

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
.\gradlew.bat -PpublishMods modrinth            # uploads the jar as mod_version from gradle.properties
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
$env:MODRINTH_PROXY = "http://127.0.0.1:<port>"   # your local proxy / accelerator port
```

For the Gradle/Minotaur path, set the matching `systemProp.*.proxyHost/Port` in your
**user-level** `~/.gradle/gradle.properties` (the commented block in this repo's
`gradle.properties` is a template — keep machine-specific values out of the repository).

### M.3 Option B — upload by hand (no token, no Gradle)

Nothing leaves your machine except the jar, and no credentials are involved.

1. Build: `.\gradlew.bat build`
2. Grab the file and its hash:

   ```powershell
   Get-ChildItem .\build\libs\*.jar | Select-Object Name, Length
   Get-FileHash .\build\libs\create_productionline-*.jar -Algorithm SHA256
   ```
3. On the project page: **Versions → Create version**, then

   `<version>` below always means the value of `mod_version` in `gradle.properties` — never a literal.

   | Field | Value |
   | --- | --- |
   | Name | `v<version>` (or leave default) |
   | Version number | `<version>` — must match `mod_version` |
   | Release channel | `Release` |
   | Game versions | `1.21.1` |
   | Loaders | `NeoForge` |
   | Dependencies | **Create** → *Required* |
   | File | `build/libs/create_productionline-<version>.jar` |
   | Changelog | paste the matching `## [<version>]` section of `CHANGELOG.md` |

4. If the description/icon are not set yet, upload `icon_512x512.png` as the project icon and paste
   `docs/platform-listing.md` as the project description — the **whole file**. It contains **no HTML
   comment**, so there is nothing to strip or trim.

### M.4 Post-release checks

- [ ] The version page lists **NeoForge** + **1.21.1** and marks **Create** as required
- [ ] The jar downloads and its size matches the build output
- [ ] The description renders (no `<version>`-style placeholder left in it, no unescaped markup)
- [ ] `LICENSE` / `mod_authors` show your real name, not a placeholder

---

## 0. Full setup (GitHub done · CurseForge later)

### 0.1 Placeholders — current status

**No `YOUR NAME OR HANDLE` / `YOUR-GITHUB` placeholder is left anywhere**; confirm with:

```powershell
Select-String -Path .\LICENSE,.\gradle.properties,.\docs\platform-listing.md -Pattern 'YOUR NAME OR HANDLE|YOUR-GITHUB'
```

| File | Placeholder | State | Needed for |
| --- | --- | --- | --- |
| `LICENSE` | `<YOUR NAME OR HANDLE>` | **done** — `Copyright (c) 2026 Es254` | — |
| `gradle.properties` | `mod_authors` | **done** — `Es254` | — |
| `gradle.properties` | `curseforge_project_id` | **still empty** — CurseForge is not set up (§0.3) | CurseForge |
| `neoforge.mods.toml` | `issueTrackerURL` | **done** — literal `…/Create-ProductionLine/issues` | GitHub |
| `gradle.properties` | `mod_issue_tracker_url` | still empty; **intentionally unused** — the TOML carries a literal URL, because `${mod_issue_tracker_url}` would expand to a dead link | GitHub |
| `mod_display_url` | — | left as the Modrinth page on purpose (that is the public home players see); the GitHub URL lives in `issueTrackerURL` | GitHub |

### 0.2 Modrinth

Setup done — see **§M**. Publishing works today; the only outstanding item is Modrinth's own review:
the project `status` is `processing` (author view), so the anonymous API returns 404 and no player can
see the page yet (§M.1.2).

### 0.3 CurseForge

Create an account / project when you can reach <https://authors.curseforge.com/>.

1. Create the project at <https://authors.curseforge.com/>.
2. Game **Minecraft**, category **Mods**, and note the **numeric project id** (shown in the
   project's "About" panel / URL) → `curseforge_project_id`.
3. Generate an API token at <https://authors.curseforge.com/#/account> → **API Tokens**.
   Export it as `CURSEFORGE_TOKEN`.

> Keep both tokens **out of the repository**. Use environment variables (preferred) or
> `~/.gradle/gradle.properties` (`modrinth_token=…` / `curseforge_token=…`). Never commit them.

### 0.4 GitHub — done (kept for reference only)

The repository is **public and pushed**: `origin` is
<https://github.com/Es-254/Create-ProductionLine>, `main` holds the full history (21 commits) and the
`v1.0.2` tag + GitHub Release exist. The bootstrap below is recorded so the original setup stays
reproducible — **do not re-run it on a clone that already has `origin`.**

The repository root is **this directory** (`create_productionline/`), not the enclosing workspace
folder — the workspace also holds third-party code and local build tooling that must never be
published.

```powershell
# one-time bootstrap — already performed, reference only
git init
git add .
git commit -m "Create: Production Line <version>"
git branch -M main
git remote add origin https://github.com/Es-254/Create-ProductionLine.git
git push -u origin main
```

---

## 1. Prepare the version

1. Bump `mod_version` in `gradle.properties` (semantic versioning).
2. Move the `CHANGELOG.md` "Unreleased" items into a new `## [x.y.z] — YYYY-MM-DD` section, update the
   comparison link at the bottom, **and add the matching `[x.y.z]: …` link definition** — a heading
   like `## [1.0.2]` renders as literal brackets unless its reference is defined at the end of the file.
   (1.0.2 and 1.0.1 were missing theirs; this is now fixed.)
3. If the description changed, edit `docs/platform-listing.md`.

## 2. Verify

```powershell
cd create_productionline

# Compiles
.\gradlew.bat compileJava --offline

# Full build → build/libs/create_productionline-<version>.jar
.\gradlew.bat build

# Headless QA self-test on a real server (9 checks, then the server halts)
.\gradlew.bat runServer -PselfTest
```

Check the self-test log ends with a line matching:

```
\d+ passed, 0 failed
```

**Do not hard-code the check count when judging a build.** `qa/SelfTest.java` prints
`CPL SELF-TEST RESULT: <pass> passed, <fail> failed` (`SelfTest.java:94`), so the acceptance
criterion is *"the last line matches `\d+ passed, 0 failed`"* — a literal number would silently
misjudge the very next release that adds a case. The current snapshot is **9 passed** (the count of
`check("…")` calls in `qa/SelfTest.java`; the 7th, `Plan topology (chain: base -> machine+material ->
product)`, was added in 1.0.2, and `Tag ingredients kept in flat recipes` / `Deriver refuses
unconvertible recipe` landed after that release). If the number grows, only the snapshot mentions in
this file, `README.md` and `CHANGELOG.md` need touching — never the criterion.

```powershell
# one-liner: run the self-test and assert on the pattern instead of a literal count
.\gradlew.bat runServer -PselfTest 2>&1 | Select-String -Pattern '\d+ passed, 0 failed'
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

This is a real gate, not a formality: `build.gradle` excludes the editor leftovers (`**/*.bak`,
`*.orig`, `*.rej`, `*.psd`, `*.xcf`, `*.kra`, `*.bbmodel`, `Thumbs.db`, `desktop.ini`) and the empty
`debug/` package and `src/generated/` no longer exist. Verified for `1.0.2`: **143 entries, no
`.bak` / `*_particle.png` / `debug/` match.**

Record the size and SHA-256 for the release notes.

## 3. Tag and publish on GitHub

GitHub is live, so this is a normal step of every release (it was done for `v1.0.2`).

```powershell
git add -A
git commit -m "Release v<version>"
git tag -a v<version> -m "Create: Production Line <version>"
git push origin main --tags        # origin already exists — do NOT re-add it
```

Then create a GitHub Release for the tag and attach
`build/libs/create_productionline-<version>.jar`. (The `build.yml` workflow also uploads the jar as
a build artifact on every push, so you can grab it from the Actions run instead.)

> Only `v1.0.2` is tagged. `1.0.0` / `1.0.1` have no tag or Release, which is why their `CHANGELOG.md`
> link definitions point at Modrinth instead of a GitHub compare URL — see the comment at the bottom
> of `CHANGELOG.md`. Push the older tags first if you ever want compare links.

## 4. Publishing commands

**GitHub (works today)** — tag + Release, see **§3**. **Modrinth (publishing works, project waiting for
review:** the anonymous API returns 404 while `status = processing` — **§M.1.2)** — see **§M.2**:

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
3. Paste `docs/platform-listing.md` **in full** (it has no HTML comment to strip) as the project
   description the first time, and keep Modrinth/CurseForge bodies in sync with that file.

## 5. Release checklist

- [x] Machine-specific settings and internal docs removed **from the working tree** — note this holds
      for the tree as committed; it is not a property of git *history*, so check `git status` and the
      staged diff on every commit rather than trusting the tick
- [x] No placeholders left in the repo (`LICENSE` / `mod_authors` are `Es254`)
- [ ] `mod_version` bumped; `CHANGELOG.md` section written and dated **and its `[x.y.z]:` link
      definition added at the bottom**
- [ ] `gradlew build` succeeds
- [ ] `runServer -PselfTest` → last log line matches **`\d+ passed, 0 failed`** (current snapshot:
      9 passed; never hard-code the number)
- [ ] Jar contains no `.bak` / `*_particle.png` / `debug/` entries
- [ ] Size + SHA-256 recorded
- [ ] Git tag pushed; GitHub Release created with the jar attached
- [ ] Modrinth version published (MC 1.21.1, NeoForge, Create = required dependency)
- [ ] CurseForge file published (1.21.1 / NeoForge / Java 21, Create = required dependency) — *blocked:
      CurseForge is not set up yet*
- [ ] Project icons uploaded (`icon_512x512.png`)
- [ ] Tokens were **not** committed (`git status` clean of secrets)

---

## Notes

- **Never** point the repository at the enclosing workspace folder: it also holds
  decompiled third-party code, local network tooling and certificates, and archived
  logs — none of which belong in a public repository.
- **Create's assets are All Rights Reserved.** This mod only references them at runtime; never copy
  Create textures or models into `src/main/resources`. The authoritative statement of this constraint
  is in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) — "Create assets are All Rights Reserved —
  do not copy them" — and it also covers Flywheel and Ponder scene/asset files.
- The `qa/SelfTest` class **is** shipped on purpose — server admins can run the same self-test.
