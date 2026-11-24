<p align="center"><img src="../multiwarden/branding/logo512.png" width="112" alt="Multiwarden"></p>

<h1 align="center">Multiwarden for Android</h1>

<p align="center">
  All your vaults, working as one.
</p>

<p align="center">
  <a href="https://github.com/multiwarden/android/releases/latest"><img src="https://img.shields.io/github/v/release/multiwarden/android?style=flat-square&label=release&color=5e5feb" alt="Latest release"></a>
  <a href="https://github.com/multiwarden/android/actions/workflows/multiwarden-ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/multiwarden/android/multiwarden-ci.yml?style=flat-square&label=build&color=5e5feb" alt="Build status"></a>
  <img src="https://img.shields.io/badge/license-GPL--3.0-5e5feb?style=flat-square" alt="GPL-3.0">
  <img src="https://img.shields.io/badge/status-experimental-473ecf?style=flat-square" alt="Experimental">
</p>

<p align="center">
  <a href="https://multiwarden.com">Website</a> ·
  <a href="https://github.com/multiwarden/android/releases">Releases</a> ·
  <a href="https://github.com/multiwarden/clients">Browser extension</a> ·
  <a href="../multiwarden/README.md">Maintainer notes</a>
</p>

---

## Overview

The official Bitwarden app lets you sign in to several accounts, but only the _active_ account
takes part in autofill. To fill a login from another account you first have to switch to it.

Multiwarden for Android is an unofficial fork of the Bitwarden Android app in which every
unlocked account takes part. It is the Android counterpart of the
[Multiwarden browser extension](https://github.com/multiwarden/clients) and stays close to
upstream in the same way: new behaviour lives in separate files, and upstream code only receives
small, marked hooks.

> [!NOTE]
> Multiwarden is experimental. It is not audited and not supported by Bitwarden. Choose
> Multiwarden as your autofill service in Android's settings instead of Bitwarden.

## What changes

| Area               | Upstream Bitwarden  | Multiwarden                                                            |
| ------------------ | ------------------- | ---------------------------------------------------------------------- |
| **Autofill**       | Active account only | Suggestions from all unlocked accounts, each labelled with its account |
| **Locked state**   | Active account      | Autofill works while any account is unlocked                           |
| **TOTP auto-copy** | Active account      | Uses the account that owns the login                                   |
| **App**            | Bitwarden           | Own app ID, name and icon, so it installs next to the official app     |

Coming next: passkeys and passwords through Android's Credential Manager, choosing the account
when saving a new login, and a combined vault view.

## Install

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.multiwarden.android%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fmultiwarden%2Fandroid%22%2C%22author%22%3A%22Multiwarden%22%2C%22name%22%3A%22Multiwarden%22%7D"><img src="../multiwarden/branding/get-it-on-obtainium.png" height="48" alt="Get it on Obtainium"></a>

1. **With [Obtainium](https://github.com/ImranR98/Obtainium) (recommended):** open the badge above on your
   phone. Obtainium installs Multiwarden from the latest release and updates it whenever a new
   release is published. Or add `https://github.com/multiwarden/android` in Obtainium by hand.

   **Without Obtainium:** download `multiwarden-<version>.apk` from the
   [latest release](https://github.com/multiwarden/android/releases/latest) and open it on your
   phone. Allow installing from your browser or file manager when Android asks. You will have to
   install updates the same way.
2. Sign in to each account through the account switcher (up to five) and unlock them.
3. In Android's settings, choose **Multiwarden** as the autofill service.

An account stays unlocked after you switch away from it until its own vault timeout passes, so
set the timeout of every account you want in autofill accordingly.

## How it is built

```
bitwarden/android  ──nightly sync──▶  multiwarden/android  ──CI──▶  tested, signed APK  ──▶  release
                                         │
                                         ├─ multiwarden-only files   (all new logic)
                                         └─ "Multiwarden:" hooks     (small, marked edits upstream)
```

- **Hooks, not rewrites.** `git grep -n "Multiwarden:"` lists every place upstream code is touched.
- **Upstream sync.** A nightly workflow applies each new Bitwarden release with a 3-way merge and
  opens a PR.
- **Branding outside upstream files.** The app ID, name and icon come from
  `multiwarden/multiwarden.gradle` and `multiwarden/android/`, so they never conflict with
  upstream.
- **No Google services.** Multiwarden builds Bitwarden's F-Droid flavor.

Build steps, the file map and the sync process are in the
[maintainer notes](../multiwarden/README.md).

## License and trademarks

Multiwarden is licensed under the GNU GPL v3, like the upstream code it is based on.

Multiwarden is **not affiliated with, endorsed by, or supported by Bitwarden, Inc.** "Bitwarden"
is a registered trademark of Bitwarden Inc., used here only to describe compatibility. Please
report Multiwarden issues [here](https://github.com/multiwarden/android/issues), not to Bitwarden.
