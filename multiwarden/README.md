# Multiwarden for Android: maintainer notes

Multiwarden for Android is a fork of [bitwarden/android](https://github.com/bitwarden/android)
that makes the Password Manager app work with **several unlocked Bitwarden accounts at the same
time**. It is the Android counterpart of the Multiwarden browser extension
([multiwarden/clients](https://github.com/multiwarden/clients)) and follows the same rules. This
file records exactly where Multiwarden differs from upstream, so that upstream syncs stay easy.

## How multiple accounts work on Android

Bitwarden's app already keeps several vaults unlocked: switching accounts does not lock the
account you leave until its own vault timeout passes (and never, with the timeout "Never"). It
only ever _reads_ the active account's vault, though. Multiwarden reads the other unlocked vaults
too: their encrypted data comes from the local database and is decrypted with that account's own
SDK client, which Bitwarden keeps initialised while the account is unlocked. Nothing is sent to a
server and no keys are copied between accounts.

Other accounts' vaults are as fresh as their last sync (Bitwarden syncs the active account).

## Design rule: hooks, not rewrites

All new logic lives in Multiwarden-only files. Upstream files get small, commented hooks that
call into them. Every hook is marked with a `Multiwarden:` comment, so
`git grep -n "Multiwarden:"` lists them all.

CI checks that no hook goes missing (for example in a sync that merged badly):
`multiwarden/hooks.json` records how many `Multiwarden:` markers and references to Multiwarden
code each upstream file has, and `multiwarden/scripts/check-hooks.mjs` fails when a file has
fewer. After adding, moving or removing a hook on purpose, run
`node multiwarden/scripts/check-hooks.mjs --update` and commit `hooks.json`.

### Multiwarden-only files

| File                                                                         | What it does                                                                                                                                 |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `app/src/main/kotlin/com/x8bit/bitwarden/multiwarden/MultiAccountVault*`     | Lists the unlocked accounts other than the active one, decrypts their ciphers, finds which account owns an item and generates its TOTP code. |
| `app/src/main/kotlin/com/x8bit/bitwarden/multiwarden/MultiwardenAutofill.kt` | Loads autofill items from every unlocked account (active account first) and adds the account's email to each item's subtitle.                |
| `app/src/main/kotlin/com/x8bit/bitwarden/multiwarden/di/`                    | Hilt module that provides `MultiAccountVault`.                                                                                               |
| `app/src/test/kotlin/com/x8bit/bitwarden/multiwarden/`                       | Tests for the above, and for the autofill provider with a second unlocked account.                                                           |
| `multiwarden/multiwarden.gradle`                                             | Build settings: application ID `com.multiwarden.android`, version, app name and icon, F-Droid flavor only, release signing.                  |
| `multiwarden/android/`                                                       | Resources that replace Bitwarden's: app name per build type, launcher icon and colour.                                                       |
| `multiwarden/`                                                               | Version, sync bookkeeping, hook list and scripts.                                                                                            |
| `.github/workflows/multiwarden-*.yml`                                        | The Multiwarden build/test/release and the nightly upstream sync.                                                                            |
| `.github/README.md`                                                          | The repository front page (GitHub shows it instead of upstream's README).                                                                    |

### Upstream files with hooks

| File                                                                                                                                                   | Hook                                                                                                                                        |
| ------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `app/build.gradle.kts`                                                                                                                                 | Applies `multiwarden/multiwarden.gradle` at the end.                                                                                        |
| `app/src/main/kotlin/com/x8bit/bitwarden/data/autofill/provider/AutofillCipherProviderImpl.kt`                                                         | Cards, logins and identities from every unlocked account, labelled; "locked" only when no account is unlocked; each account's own policies. |
| `app/src/main/kotlin/com/x8bit/bitwarden/AutofillCallbackViewModel.kt`                                                                                 | Picking a suggestion decrypts the item from the account that owns it.                                                                       |
| `app/src/main/kotlin/com/x8bit/bitwarden/data/autofill/manager/AutofillTotpManagerImpl.kt`                                                             | TOTP auto-copy uses the owning account's premium status and code.                                                                           |
| `app/src/main/kotlin/com/x8bit/bitwarden/data/autofill/manager/AutofillCompletionManagerImpl.kt`                                                       | Organization events are only recorded for the active account's items.                                                                       |
| `app/src/main/kotlin/com/x8bit/bitwarden/data/autofill/di/AutofillModule.kt`                                                                           | Passes `MultiAccountVault` to the classes above.                                                                                            |
| `app/src/test/.../AutofillCipherProviderTest.kt`, `AutofillCallbackViewModelTest.kt`, `AutofillTotpManagerTest.kt`, `AutofillCompletionManagerTest.kt` | A `MultiAccountVault` with no other unlocked accounts, so upstream's tests keep testing upstream's behaviour.                               |

Branding is **not** done in upstream files: `multiwarden/multiwarden.gradle` adds
`multiwarden/android/res*` as each variant's highest-priority resources.

### Upstream files Multiwarden leaves out

`multiwarden/upstream-removed-paths` lists Bitwarden files that Multiwarden does not carry:
Bitwarden's own developer tooling and repository automation (code owners, labels, review and
response bots). The upstream sync excludes them, so they stay removed. The matching entries in
`.gitignore` are removed too.

## Builds and versions

- **Application ID** `com.multiwarden.android` (`.dev` for debug builds), so Multiwarden installs next
  to the official app. Sign in again in Multiwarden; it does not share data with Bitwarden.
- **F-Droid flavor only.** The standard flavor's Firebase setup is tied to Bitwarden's application
  ID, and Multiwarden does not use Google services.
- **Version** = Bitwarden version + Multiwarden release number, from `multiwarden/version.json`
  (`2026.11.1` release `1` is `2026.11.1.1`, version code `2026110101`).
- Build locally: `./gradlew :app:assembleFdroidDebug` (needs a GitHub token with
  `read:packages` in `user.properties` as `gitHubToken=...`, for Bitwarden's SDK).

### Releases

Run **Multiwarden CI** on `main` from the Actions tab with "release" ticked. It builds a signed
APK and publishes it as release `v<version>`. Bump `release` in `multiwarden/version.json` first
when Bitwarden's version has not changed.

Signing uses these repository secrets (**Settings → Secrets and variables → Actions**). Keep the
keystore safe: Android only installs updates signed with the same key.

| Secret                          | Value                             |
| ------------------------------- | --------------------------------- |
| `MULTIWARDEN_KEYSTORE_BASE64`   | The keystore file, base64-encoded |
| `MULTIWARDEN_KEYSTORE_PASSWORD` | The keystore password             |
| `MULTIWARDEN_KEY_ALIAS`         | The key's alias                   |
| `MULTIWARDEN_KEY_PASSWORD`      | The key's password                |

Create one with:

```bash
keytool -genkeypair -v -keystore multiwarden.keystore -alias multiwarden \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 multiwarden.keystore   # value for MULTIWARDEN_KEYSTORE_BASE64
```

## Staying in sync with upstream

Multiwarden follows Bitwarden's **released** Password Manager app (tags `v<version>-bwpm`), not
their `main` branch.

- `multiwarden/upstream-commit` holds the last upstream commit Multiwarden includes, and
  `multiwarden/version.json` its version.
- `.github/workflows/multiwarden-upstream-sync.yml` runs nightly (or on demand from the Actions
  tab, optionally with a specific version). When Bitwarden has released a newer version, it
  applies upstream's changes from that commit to the release tag with a 3-way merge, resets
  `release` to 1, and opens a PR on `sync/bitwarden-<version>`.
    - Clean: CI builds and tests the branch. When everything passes, the **Merge sync** job merges
      the PR and starts a release. Nothing to do.
    - `[conflicts]` PR: conflict markers are committed on the branch. Fix them with the hook table
      above as a guide, push, and merge. Then start a release by hand.
    - CI fails on a clean PR (a test, the build or the hook check): fix it on the branch, then run
      Multiwarden CI on that branch from the Actions tab to merge it.
- Manual sync, if you prefer:

    ```bash
    git remote add upstream https://github.com/bitwarden/android.git   # once
    git fetch upstream tag v2026.12.0-bwpm
    git diff --binary "$(cat multiwarden/upstream-commit)" v2026.12.0-bwpm -- . \
    $(grep -v '^#' multiwarden/upstream-removed-paths | sed 's/^/:(exclude)/') \
    | git apply --3way --index
    git rev-parse "v2026.12.0-bwpm^{commit}" > multiwarden/upstream-commit
    ```

### Repository settings the automation needs

- **Bitwarden's own workflows off**: they need Bitwarden's secrets and infrastructure. Run once:
  `bash multiwarden/scripts/disable-upstream-workflows.sh` (needs the GitHub CLI).
- **Settings → Actions → General → Workflow permissions**: tick "Allow GitHub Actions to create
  and approve pull requests" (the sync opens PRs, Merge sync merges them).
- **Settings → Branches**: a rule for `main` that requires the Multiwarden CI checks
  (`Lint and test`, `Build APK`) before merging.
- **Settings → Secrets and variables → Actions**: the signing secrets above.

## What is next

| Area                                                     | Status                                                                                              |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Autofill (autofill service, inline)                      | Done: every unlocked account, labelled.                                                             |
| Passkeys and passwords (Credential Manager, Android 14+) | Next: `BitwardenCredentialManagerImpl` and `Fido2CredentialStoreImpl` read the active account only. |
| Save prompt                                              | Next: choose which account a new login is saved to.                                                 |
| Vault screen                                             | Later: a combined view of all unlocked vaults, like the extension's.                                |
| Multiwarden settings screen                              | Later.                                                                                              |
