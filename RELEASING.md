# Releasing / 发布流程

How to cut a release of **Create: Production Line** and publish it to **Modrinth**,
**CurseForge** and **GitHub**.

> **Current state (verified against this repository + the live APIs, 2026-09-20):**
>
> - **`1.0.3-snapshot.0.0.1` is published as an alpha pre-release** (2026-09-20) — a **performance
>   optimization** and **only part of the `1.0.3` line** (the recipe-refresh rework; the texture / GUI /
>   art-style work of `1.0.3` is not in it): GitHub Release `v1.0.3-snapshot.0.0.1`
>   (`prerelease: true`, asset 226,361 B, `sha256:aaf8e9e2…`), Modrinth version `pLbPKG48` (channel
>   *Alpha*, same bytes), CurseForge file **`8947278`** (release type `alpha`, pending review).
>   `1.0.2` stays the current **stable** release; a snapshot is never a finished line, and this one covers
>   one piece of the plan rather than the whole `1.0.3` scope.
> - **`1.0.2` is a RELEASE** (promoted from beta on 2026-09-17) **and all three platforms carry the same
>   bytes**: GitHub Release `v1.0.2` is not a pre-release and holds
>   `create_productionline-1.0.2.jar` (215,411 B, `sha256:27e0a3c6…`), Modrinth holds that same jar as
>   version `1.0.2` channel *Release* (id `NhXpGHHA`), and CurseForge file **8924265** is that same jar
>   with release type `release` (uploaded 2026-09-20).
> - **The CurseForge file was re-uploaded to get there.** Promotion first flipped the existing CF file
>   **8923748** to release type `release`, which left the *earlier beta bytes* (208,860 B) in place — the
>   upload API cannot replace a file's content. The author archived that file (2026-09-20) and the jar
>   was uploaded again as **8924265**; the caveat is therefore closed. Content can only ever be changed
>   by uploading a new file, and archiving the old one is what keeps the list clean.
> - **The `v1.0.2` Release body was re-synced on 2026-09-20** (7,685 chars, release id `392181858`). It had
>   been generated at tag time and still called the jar a beta, referenced the old "16 checks" and the
>   `*Unreleased*` note; the corrected text is the same section Modrinth and the CurseForge upload script
>   take from `CHANGELOG.md`. The asset (215,411 B, `sha256:27e0a3c6…`), the tag and the
>   `prerelease=false` flag were untouched by the update.
> - **`main` is past the published `1.0.2`**: it now carries the recipe-refresh work and builds
>   `1.0.3-snapshot.0.0.1` (20 checks), which is published as the alpha snapshot above. The `1.0.2` release
>   asset stays the released artifact of record until the `1.0.3` line is finished. The `1.0.3` scope is
>   wider than this snapshot — see the snapshot section of `CHANGELOG.md`.
> - **History, superseded by the release above — `1.0.2` as a beta** (2026-09-17): GitHub Release `v1.0.2`
>   marked **pre-release** with `create_productionline-1.0.2.jar` (208,860 B, `sha256:542cc877…`),
>   CurseForge file id **8923748** (release type *beta*), Modrinth version **1.0.2** channel *beta*
>   (id `IJy568s8`). All three carry the same bytes, and `1.0.1` was the current stable release at that
>   point (it is the one before `1.0.2` now).
>   Modrinth now lists two versions numbered `1.0.2` — this beta and the old-numbering entry that
>   cannot be deleted while the project is in review; remove the old one after approval.
> - **GitHub — live and public.** `origin` is <https://github.com/Es-254/Create-ProductionLine>, the
>   repository is **public** (`private: false`, indexed by GitHub search), and `main` is pushed. The
>   tags are `v1.0.1`, `v1.0.2` and `v1.0.3-snapshot.0.0.1` — the number `1.0.2` was reused for the
>   anvil-flow cut after the old-numbering tag of the same name was deleted (see *Version policy*), and
>   that cut is now the current release.
> - **Modrinth — submitted, still in review; not publicly visible.** Anonymous calls return **404** for
>   both the slug `createproductionline` and the base62 id `7dcs0ruf` (re-checked 2026-09-20) and the
>   project does not show up in search, while the **author view** returns `200` with
>   `status = "processing"`. Its **versions** are already `status: listed`, which is why all four are
>   visible through the author API:
>   `NhXpGHHA` = `1.0.2` channel *Release* (215,411 B, `sha256:27e0a3c6…`, the released jar),
>   `IJy568s8` = the same `1.0.2` as a *Beta* (208,860 B, superseded),
>   `iuR73mao` = the old-numbering `1.0.2` (176,195 B; channel `release` at the time, recorded as a dev
>   snapshot under the current policy) and
>   `u4SwiPGj` = `1.0.1` channel *Release* (190,504 B, `sha256:2c644ed6…`).
>   So the releases themselves are done; what is missing is the public approval that makes the page
>   visible. Two cleanups wait for it, because Modrinth **refuses to delete a version while the project
>   is under review** (`400 project must have no required validation nags before or while under
>   review`): delete the superseded beta `IJy568s8` and the old-numbering `iuR73mao`, both of which
>   duplicate the version number `1.0.2`. The project **body is now in sync**: re-pushed on 2026-09-22
>   with `scripts/sync-modrinth-body.ps1` (`PATCH /v2/project/{id}` → `204`), after which the live text is
>   byte-identical to `docs/platform-listing.md` (2,922 chars; the later wording fixes — the redstone
>   signal, overridable mappings and the `cannot_map` quote — needed one more push) — it had still
>   described the old computer
>   slots (paper / clipboard in the middle and right slots, where slot 2 now takes a blank Line Scheme and
>   slot 3 optional paper) and the pre-rework "reloads automatically" loader step. A version's *changelog*
>   can normally be edited while under review too (`NhXpGHHA`'s was re-synced on 2026-09-20,
>   `PATCH /v2/version/{id}` → `204`), **but not for the snapshot**: `GET` and `PATCH` on
>   `/v2/version/pLbPKG48` both answer **404** since 2026-09-22 even though the project's version list
>   still shows it as `alpha / listed` (the author dashboard is the place to look for a review note).
>   Everything else about the Modrinth path (token, ids, scripts) is ready.
> - **Snapshot `1.0.3-snapshot.0.0.1`** is on Modrinth as version `pLbPKG48` (channel *Alpha*, 226,361 B,
>   `sha512:a6180290…`) and on CurseForge as file **`8947278`** (release type `alpha`, pending review);
>   the GitHub side is the pre-release above. All three were fed the same local jar.
> - **CurseForge — live.** Project **`1699977`**, public page
>   <https://www.curseforge.com/minecraft/mc-mods/create-production-line> (slug
>   `create-production-line`), MIT licence, environment **Client & Server**. The current file is
>   **`8924265`** = `create_productionline-1.0.2.jar` (215,411 B, `sha256:27e0a3c6…`, release type
>   `release`, uploaded 2026-09-20), i.e. the same bytes as the GitHub Release and the Modrinth version;
>   the first file was `8905098` (`1.0.1`, 190,504 B) and `8923748` (the `1.0.2` beta jar) is archived.
>   The snapshot is file **`8947278`** (release type `alpha`, pending review).
>   **Two things can only be fixed in the dashboard**, because this API has no write path for either:
>   the project **description** still predates the recipe-refresh wording in `docs/platform-listing.md`
>   (re-checked 2026-09-22: the Eternal API answers `403` for an upload token, i.e. it needs a separate
>   Eternal key, and the legacy API is behind Cloudflare and only carries file endpoints), and each
>   **file-page changelog** is the text captured at upload time (`update-file` answers `500` for
>   `changelog`). Paste `docs/platform-listing.md` into the description editor, and the matching
>   `CHANGELOG.md` section into a file page, whenever those need to match. §0.3 carries the API details.
>
> Because the repository is public and pushed, §0.4 (bootstrap) is **history rather than a to-do**, and
> §3 (tag + GitHub Release) is a normal step of every release. §M remains the day-to-day path.

---

## Version policy / 版本规范

| Line / 版本线 | Version | Build command | Published as |
| --- | --- | --- | --- |
| **release** | `1.0.x` — the value of `mod_version` in `gradle.properties`, with `mod_version_type=release` | `.\gradlew.bat build` | `release` (Modrinth/CurseForge channel *Release*, GitHub Release, **not** a pre-release) |
| **beta** | the same `mod_version`, with `mod_version_type=beta` | `.\gradlew.bat build` | `beta` (channel *Beta*, GitHub **pre-release**) — how `1.0.2` shipped before it was promoted; use this line for the next beta cut |
| **snapshot (alpha)** | `x.y.z-snapshot.0.0.N` — the value of `mod_version`, with `mod_version_type=alpha` | `.\gradlew.bat build` | `alpha` (channel *Alpha*, GitHub **pre-release**) — work in progress towards `x.y.z`, e.g. `1.0.3-snapshot.0.0.1` |
| **dev (beta)** | `0.0.0-dev.N` — N comes from `dev-build.txt` | `.\gradlew.bat build -PdevBuild` | `beta` (channel *Beta*, GitHub **pre-release**) |

> **OP trust model.** An anvil-authored scheme is as powerful as a datapack: the operator picks the
> materials *and* the per-pass output (`count = outputCount`), so a hand-built line can declare a yield
> no real recipe has. That is the point of the tool, and it is why the flow is server-authoritative and
> gated on permission level 2 — keep that in mind when handing out OP.

Rules / 规则:

1. `mod_version` holds the version this checkout builds: a finished release (`x.y.z`) or, while a line
   is being prepared, a snapshot of it (`x.y.z-snapshot.0.0.N`). A **dev** number never goes there.
2. A snapshot is a pre-release and can never ship as `release`: `build.gradle` and both publish scripts
   **refuse** `-PreleaseType=release` / `-ReleaseType release` for a `-snapshot.` version. The counter
   is part of the string and only moves forward (`…snapshot.0.0.2` next); finishing the line means
   dropping the suffix (`mod_version=1.0.3`) and setting `mod_version_type=release`.
3. A dev build reads N from `dev-build.txt` and **advances that file after the jar is written**
   (`0.0.0-dev.5` → file becomes `6`); commit the bumped file together with the dev build so the
   numbering stays traceable. `-PdevBuildNumber=<N>` reproduces N without touching the file — that is
   how CI rebuilds a `v0.0.0-dev.N` tag.
4. A dev version is a beta by definition: the publish tasks default to `beta` for it and **refuse**
   `release` (`-PreleaseType=release` on a dev version fails on purpose). 开发版默认 beta；
   只有 `1.0.x` 能以 `release` 类型发布。
5. Tags: `v1.0.x` for a release-line cut (release or beta, decided by `mod_version_type`),
   `vx.y.z-snapshot.0.0.N` for a snapshot, and `v0.0.0-dev.N` for a dev build. A pushed `v*` tag makes
   `build.yml` build that exact version and create/update the GitHub Release, marked as a pre-release
   when the channel is not `release` (a `-dev.`/`-snapshot.` tag, or `mod_version_type=beta`/`alpha` at
   the tagged commit), so tagging is normally all you do on the GitHub side.
6. History: the **old numbering** (`1.0.0` / `1.0.1` / `1.0.2` / `1.0.3`) produced four jars *before this
   policy existed*. They were published with channel `release` at the time — which is why the platforms
   still carry entries of those numbers — and `CHANGELOG.md` records them as the development snapshots
   `0.0.0-dev.1` … `0.0.0-dev.4`; under today's policy none of them is a release. The **first official
   release** is `1.0.1` as cut under the current policy (190,504 B, `sha256:2c644ed6…`): it shares a
   number with one of those snapshots but is a different artifact.
7. The number `1.0.2` was **reused** for the anvil-flow cut (the old `v1.0.2` tag and its Release were
   deleted first, so the current `v1.0.2` is a fresh object). That cut shipped as a beta on 2026-09-17
   and was **promoted to a release** the same day, so `1.0.2` is the current stable release. `1.0.3` is
   being cut in snapshots — `1.0.3-snapshot.0.0.1` is the first. Modrinth still carries an old entry
   named `1.0.2`; see the Modrinth note in the current-state block.

---

## M. Modrinth + GitHub release

Everything below works today, Modrinth publishing included — the project *page* stays private until
Modrinth approves and lists it (§M.1.2).

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

   At the time of writing the id is `7dcs0ruf` and the project `status` is `processing`; `1.0.1` is
   published as a *Release* and the old `1.0.2` still sits in the version list because Modrinth blocks
   version deletion while a project is under review. Delete `1.0.2` once the project is approved — the
   only thing gating the Modrinth half of a release is Modrinth's own approval.

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
.\gradlew.bat -PpublishMods modrinth            # uploads the resolved project version (1.0.x, or 0.0.0-dev.N with -PdevBuild)
.\gradlew.bat -PpublishMods modrinthSyncBody    # pushes docs/platform-listing.md as the description
```

`-PreleaseType=beta` (or `alpha`) publishes as a pre-release; the default is `release` for `1.0.x` and
`beta` for a dev version. The changelog sent with the version is
`CHANGELOG.md`; the description is `docs/platform-listing.md`. Both are already written — nothing to
copy by hand.

### M.2b If Minotaur times out (flaky international routes)

Minotaur has **no retry**: on a connection where Modrinth's edge is intermittently unreachable it
dies with `Failed to upload file to Modrinth! java.net.SocketTimeoutException: Connect timed out`,
even though `api.modrinth.com` is reachable a second later. Two hardened fallbacks live in
`scripts/` — same API calls, long timeouts and a retry loop:

```powershell
$env:MODRINTH_TOKEN = "mrp_…"

.\scripts\publish-modrinth.ps1            # uploads mod_version from gradle.properties (release)
.\scripts\publish-modrinth.ps1 -Dev       # uploads the newest 0.0.0-dev.N jar, channel beta
.\scripts\publish-modrinth.ps1 -Version 0.0.0-dev.5   # explicit version (jar must exist)
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

   `<version>` below always means `project.version`: the value of `mod_version` in `gradle.properties`
   for a release (`1.0.x`), or `0.0.0-dev.N` for a `-PdevBuild` artifact — never a literal.

   | Field | Value |
   | --- | --- |
   | Name | `v<version>` (or leave default) |
   | Version number | `<version>` — must match the jar name and the in-game mod list entry |
   | Release channel | `Release` for `1.0.x`; **`Beta` for `0.0.0-dev.N`** |
   | Game versions | `1.21.1` |
   | Loaders | `NeoForge` |
   | Dependencies | **Create** → *Required* |
   | File | `build/libs/create_productionline-<version>.jar` |
   | Changelog | paste the matching `## [<version>]` section of `CHANGELOG.md` (for a dev build: the `0.0.0-dev.N` entry) |

4. If the description/icon are not set yet, upload `icon_512x512.png` as the project icon and paste
   `docs/platform-listing.md` as the project description — the **whole file**. It contains **no HTML
   comment**, so there is nothing to strip or trim.

### M.4 Post-release checks

- [ ] The version page lists **NeoForge** + **1.21.1** and marks **Create** as required
- [ ] The jar downloads and its size matches the build output
- [ ] The description renders (no `<version>`-style placeholder left in it, no unescaped markup)
- [ ] `LICENSE` / `mod_authors` show your real name, not a placeholder

---

## 0. Full setup (GitHub + CurseForge done · Modrinth waiting for review)

### 0.1 Placeholders — current status

**No `YOUR NAME OR HANDLE` / `YOUR-GITHUB` placeholder is left anywhere**; confirm with:

```powershell
Select-String -Path .\LICENSE,.\gradle.properties,.\docs\platform-listing.md -Pattern 'YOUR NAME OR HANDLE|YOUR-GITHUB'
```

| File | Placeholder | State | Needed for |
| --- | --- | --- | --- |
| `LICENSE` | `<YOUR NAME OR HANDLE>` | **done** — `Copyright (c) 2026 Es254` | — |
| `gradle.properties` | `mod_authors` | **done** — `Es254` | — |
| `gradle.properties` | `curseforge_project_id` | **done** — `1699977` (§0.3) | CurseForge |
| `neoforge.mods.toml` | `issueTrackerURL` | **done** — written as `${mod_issue_tracker_url}`, expanded by `ProcessResources` | GitHub |
| `gradle.properties` | `mod_issue_tracker_url` | **done** — `https://github.com/Es-254/Create-ProductionLine/issues` | GitHub |
| `mod_display_url` | — | left as the Modrinth page on purpose (that is the public home players see); the GitHub URL lives in `issueTrackerURL` | GitHub |

### 0.2 Modrinth

Setup done — see **§M**. Publishing works today; the only outstanding item is Modrinth's own review:
the project `status` is `processing` (author view), so the anonymous API returns 404 and no player can
see the page yet (§M.1.2).

### 0.3 CurseForge — done (live)

Project: **`curseforge_project_id=1699977`**, public page
<https://www.curseforge.com/minecraft/mc-mods/create-production-line> (slug `create-production-line`),
licence **MIT**, distribution **Allow distribution to 3rd party**, environment **Client & Server**.
The file line so far: **id 8905098** (`create_productionline-1.0.1.jar`, 190,504 B, `sha256:2c644ed6…`)
→ **id 8923748** (the `1.0.2` beta jar, 208,860 B; later flipped to release type `release` and then
**archived** by the author on 2026-09-20) → **id 8924265** (`create_productionline-1.0.2.jar`,
215,411 B, `sha256:27e0a3c6…`, release type `release`, uploaded 2026-09-20) — the last one is the same
jar as the GitHub Release and the Modrinth version, all uploaded through
`scripts/publish-curseforge.ps1`.

1. The project was created at <https://authors.curseforge.com/> → **Create Project** (game
   **Minecraft** → **Mods**, licence **MIT**, distribution **Allow distribution to 3rd party**).
2. The numeric id is the number in the authors-dashboard URL → `curseforge_project_id`.
3. Upload token: <https://authors-old.curseforge.com/account/api-tokens> → export as
   `CURSEFORGE_TOKEN` (or `curseforge_token` in `~/.gradle/gradle.properties`, which is where the
   local one lives).

> Two cosmetic items are still open on the dashboards: the project title reads
> `Create:Productionline` on CurseForge (and `Create:ProductionLine` on Modrinth) while the mod's own
> display name is `Create: Production Line` — worth aligning — and the CurseForge gallery has no
> images yet (`docs/platform-listing.md` is already pasted as the description).

> Keep both tokens **out of the repository**. Use environment variables (preferred) or
> `~/.gradle/gradle.properties` (`modrinth_token=…` / `curseforge_token=…`). Never commit them.

#### What the CurseForge upload path actually requires (measured 2026-09-17, re-checked 2026-09-20)

The Gradle plugin (`net.darkhax.curseforgegradle`) **cannot** upload any more, and this repo no
longer declares it:

| Reality | Consequence |
| --- | --- |
| The endpoint is `/api/projects/{id}/upload-file`; the plugin still posts to `/upload` | the old path answers `302 → /error`, which the plugin reports as `403 Forbidden` |
| `metadata` must be a **plain form field** | `curl -F "metadata=<file"` (field value read from disk); sending it as a *file part* is rejected with `1001 Missing field 'metadata'` |
| At least one **environment** version is mandatory | `1002/1021 You must select at least one version from the environment group` — `Client` / `Server` must be in `gameVersionNames` |
| `minecraft.curseforge.com` sits behind Cloudflare's managed challenge | the default `curl/x.y` User-Agent gets a `403` HTML challenge page; a browser UA normally gets through (the API still authenticates with `X-Api-Token`) |
| The challenge can also hit a POST **intermittently** | observed 2026-09-20 on a real upload (`403` + a 63 KB `__cf_chl_rt_tk` page); an immediate retry of the identical request returned `200`. The script therefore treats a challenge `403` as transient and retries it with backoff, while other `4xx` stay fatal |
| Read methods are not available to an upload token | `GET /api/projects/{id}/files` answers `403 {"errorCode":5100,"errorMessage":"You do not have permission to access this method."}`, so nothing can be read back: the `200` of the POST plus the file id it returns is the evidence, and the file list is checked in the dashboard |
| `update-file` answers `500 An unhandled exception` for `changelog`, `relations` and `gameVersionNames` | only `displayName` / `releaseType` can be edited through it; edit the rest in the dashboard |
| File **content** can never be replaced through the API | a wrong jar means uploading a new file (same display name is accepted once the old one is archived), then archiving the old one — not editing it |

### 0.4 GitHub — done (kept for reference only)

The repository is **public and pushed**: `origin` is
<https://github.com/Es-254/Create-ProductionLine> and `main` holds the full history. The tags are
`v1.0.1`, `v1.0.2` and `v1.0.3-snapshot.0.0.1` — the old-numbering `v1.0.2` was deleted and the number
re-cut for the anvil-flow release (see *Version policy*), so a fresh clone sees exactly those three.
The bootstrap below is recorded so the original setup stays
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

1. Pick the line (see *Version policy*): a release bumps `mod_version` in `gradle.properties` to the
   next `1.0.x`; a snapshot sets it to `x.y.z-snapshot.0.0.N` with `mod_version_type=alpha`; a dev build
   just uses `dev-build.txt` (`-PdevBuild`).
2. Move the `CHANGELOG.md` "Unreleased" items into a new section — `## [x.y.z] — YYYY-MM-DD` for a
   release, `## x.y.z-snapshot.0.0.N — YYYY-MM-DD (alpha)` for a snapshot (that is the form
   `1.0.3-snapshot.0.0.1` uses), `## 0.0.0-dev.N — YYYY-MM-DD (beta)` for a dev build — and **for a
   release add the matching `[x.y.z]: …` link definition at the bottom**, otherwise a heading like
   `## [1.0.1]` renders as literal brackets. Snapshot and dev headings carry no brackets on purpose
   (only `1.0.x` releases use the `[x.y.z]` link form).
3. If the description changed, edit `docs/platform-listing.md`.

## 2. Verify

```powershell
cd create_productionline

# Compiles
.\gradlew.bat compileJava --offline

# Full build → build/libs/create_productionline-<version>.jar
.\gradlew.bat build

# Headless QA self-test on a real server (20 checks, then the server halts)
.\gradlew.bat runServer -PselfTest
```

Check the self-test log ends with a line matching:

```
\d+ passed, 0 failed
```

**Do not hard-code the check count when judging a build.** `qa/SelfTest.java` prints
`CPL SELF-TEST RESULT: <pass> passed, <fail> failed` (`SelfTest.java:139`), so the acceptance
criterion is *"the last line matches `\d+ passed, 0 failed`"* — a literal number would silently
misjudge the very next release that adds a case. The current snapshot is **20 passed** (the count of
`check("…")` calls in `qa/SelfTest.java`; `Plan topology (chain: base -> machine+material -> product)`
arrived with dev snapshot `0.0.0-dev.3`, and `Tag ingredients kept in flat recipes` / `Deriver refuses
native/unmappable recipes` / `Single-material recipes map to a semantic machine` / `Duration only on
duration-capable types` / `Loader accepts written schemes only` / `Self-referential recipes are skipped`
landed by release 1.0.1, with the anvil state table, the material budget and the two recipe-refresh cases
afterwards). If the number grows, only the snapshot mentions in
this file, `docs/qa.md` and `CHANGELOG.md` need touching — never the criterion.

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
`debug/` package and `src/generated/` no longer exist. Re-verify on every cut:
**no `.bak` / `*_particle.png` / `debug/` match** (the CI build fails the run if one appears).

Record the size and SHA-256 for the release notes.

## 3. Tag and publish on GitHub

GitHub is live, so this is a normal step of every release.

```powershell
git add -A
git commit -m "Release v<version>"
git tag -a v<version> -m "Create: Production Line <version>"
git push origin main --tags        # origin already exists — do NOT re-add it
```

Pushing the tag is normally the whole GitHub step: `build.yml` builds **that** version
(`-PdevBuildNumber=N` for a `v0.0.0-dev.N` tag; a snapshot tag builds its own `mod_version`), verifies the
jar name matches the tag, and
creates/updates the Release with the jar attached — a pre-release whenever the channel is not `release`
(a `-dev.` or `-snapshot.` tag, or `mod_version_type=beta`/`alpha`).
The Release body is the matching `CHANGELOG.md` section (release, snapshot or dev heading), with GitHub's
generated notes after it. To do it by hand instead, create a Release for the tag and attach
`build/libs/create_productionline-<version>.jar`.

> **The Release body is a snapshot taken when the tag is pushed.** Correcting the `CHANGELOG.md` section
> afterwards leaves the published notes behind, and nothing re-runs by itself: the promotion of `1.0.2`
> and the later wording fixes both happened after the tag, so the `v1.0.2` body still told readers the jar
> was a beta (and still said "16 checks") until it was re-synced by hand on 2026-09-20. When a section
> changes after its tag, re-sync the body with the same extraction the workflow uses —
> `gh release edit v<version> --notes-file release-body.md`, or
> `PATCH /repos/{owner}/{repo}/releases/{id}` with a `{"body": …}` payload — and keep the trailing
> `**Full Changelog**: …` line the workflow appends. The asset and the pre-release flag are not touched
> by that call.

> Tagging rules: `v1.0.x` = a release-line cut — a release, or a pre-release when `mod_version_type` is
> `beta`/`alpha` at the tagged commit (that is how the `1.0.2` beta carried `v1.0.2` before it was
> promoted); `vx.y.z-snapshot.0.0.N` = snapshot/alpha (pre-release); `v0.0.0-dev.N` = dev/beta
> (pre-release).
> Dev snapshots `0.0.0-dev.1` … `0.0.0-dev.4` are **not** tagged retroactively — they are documented
> in `CHANGELOG.md` only. The retired `v1.0.2` tag/Release belonged to the old numbering.
>
> **Never move or delete a tag that has a Release.** GitHub reacts to a tag being re-cut by turning
> its Release into a draft (invisible to everyone but the owner), and that can land *after* the CI run
> that just published it. If you must re-cut a tag, re-check the Release afterwards and publish it
> again — the workflow passes `--draft=false`, but a late draft flip still wins. Publishing a release
> with a stale asset in it is worse than re-cutting: bump the version instead.
>
> **The released `1.0.1` is the jar attached to the `v1.0.1` tag** (190,504 B, `sha256:2c644ed6…`),
> and building that tag still reproduces it byte for byte. A build from `main` is *not* that jar: the
> in-game description in `META-INF/neoforge.mods.toml` was corrected after the tag, so `main` builds
> 190,536 B / `sha256:6ba3ccfc…` while still reporting version `1.0.1`. Treat the Release asset as the
> artifact of record; the next cut carries the description fix.

## 4. Publishing commands

**GitHub — works today** — tag + Release, see **§3**.
**Modrinth — publishing works, project waiting for review** (the anonymous API returns 404 while
`status = processing`, **§M.1.2**) — see **§M.2**.
**CurseForge — live** (project `1699977`, **§0.3**) — see below.

```powershell
$env:MODRINTH_TOKEN   = "<token>"
$env:CURSEFORGE_TOKEN = "<token>"     # optional: ~/.gradle/gradle.properties works too

# Modrinth (Minotaur)
.\gradlew.bat -PpublishMods modrinth
.\gradlew.bat -PpublishMods modrinthSyncBody

# CurseForge: runs scripts/publish-curseforge.ps1 (the old CurseForgeGradle plugin is broken, §0.3)
.\gradlew.bat -PpublishMods publishCurseForge
.\gradlew.bat -PpublishMods publishCurseForge -PcurseforgeFileId=8924265   # edit that file instead of adding one
.\gradlew.bat -PpublishMods publishCurseForge -PdevBuild                   # publish the newest dev jar as beta
```

`scripts/publish-curseforge.ps1` can also be run on its own (same flags: `-Version`, `-Dev`, `-FileId`,
`-Attempts`), which is useful when a Gradle daemon is in the way. It resolves the version, the jar and
the matching `CHANGELOG.md` section exactly like the Modrinth script does.

Options:

- `-PreleaseType=beta` (or `alpha`) publishes as a pre-release on both platforms. The default follows
  the version line: `release` for `1.0.x`, `beta` for `0.0.0-dev.N` (a dev version published as
  `release` is rejected).
- Nothing in the publishing path is evaluated without `-PpublishMods`, so ordinary builds and CI
  never need the tokens or the project ids.

### Manual fallback

If you would rather not hand tokens to Gradle, upload by hand:

1. **Modrinth** — project → *Versions* → *Create version*: upload the jar, pick MC `1.21.1`,
   loader `NeoForge`, paste the new `CHANGELOG.md` section as the changelog, and add **Create** as a
   required dependency.
2. **CurseForge** — project → *Files* → *Upload file*: same jar, game versions
   `1.21.1 / NeoForge / Java 21 / Client / Server` (**environment versions are mandatory**), release
   type, and **Create** as a required dependency. Note that CurseForge refuses a second file with the
   same display name, so a re-cut of the same version has to *replace* the existing file.
3. Paste `docs/platform-listing.md` **in full** (it has no HTML comment to strip) as the project
   description the first time, and keep Modrinth/CurseForge bodies in sync with that file.

## 5. Release checklist

- [x] Machine-specific settings and internal docs removed **from the working tree** — note this holds
      for the tree as committed; it is not a property of git *history*, so check `git status` and the
      staged diff on every commit rather than trusting the tick
- [x] No placeholders left in the repo (`LICENSE` / `mod_authors` are `Es254`)
- [ ] Version decided by line: release → `mod_version` bumped in `gradle.properties`; dev →
      `dev-build.txt` holds the next N and is committed after the build
- [ ] `CHANGELOG.md` section written and dated **and, for a release, its `[x.y.z]:` link
      definition added at the bottom**
- [ ] `gradlew build` succeeds (dev: `gradlew build -PdevBuild`)
- [ ] `runServer -PselfTest` → last log line matches **`\d+ passed, 0 failed`** (current snapshot:
      20 passed; never hard-code the number)
- [ ] Jar contains no `.bak` / `*_particle.png` / `debug/` entries
- [ ] Size + SHA-256 recorded
- [ ] Git tag pushed (`v1.0.x`, `vx.y.z-snapshot.0.0.N` or `v0.0.0-dev.N`); GitHub Release created with the jar attached
      (CI does this from the tag — a pre-release unless the channel is `release`)
- [ ] Modrinth version published (MC 1.21.1, NeoForge, Create = required dependency)
- [ ] CurseForge file published — `gradlew -PpublishMods publishCurseForge`
      (1.21.1 / NeoForge / Java 21 / Client / Server, Create = required dependency)
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
