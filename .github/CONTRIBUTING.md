# Contributing to Multiwarden for Android

Thanks for helping out. Multiwarden is a small, independent fork of the Bitwarden Android app, so
a few rules keep it easy to maintain and easy to update from upstream.

## Before you start

- **Bugs:** check the [open issues](https://github.com/multiwarden/android/issues) first, then
  [open a bug report](https://github.com/multiwarden/android/issues/new/choose).
- **Bugs that also happen in the official Bitwarden app** belong to Bitwarden. Report them in
  [bitwarden/android](https://github.com/bitwarden/android/issues).
- **Security problems:** never in a public issue. See the [security policy](SECURITY.md).
- **Larger changes:** open an issue to discuss them first, so no work is wasted.

## How Multiwarden code is organised

Read the [maintainer notes](../multiwarden/README.md) before changing code. In short:

- New logic goes in Multiwarden-only files (folders named `multiwarden/`).
- Upstream Bitwarden files only get small hooks that call into that code, each marked with a
  `Multiwarden:` comment. After adding, moving or removing a hook, run
  `node multiwarden/scripts/check-hooks.mjs --update` and commit `multiwarden/hooks.json`.
- Follow the upstream architecture and style: [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)
  and [docs/STYLE_AND_BEST_PRACTICES.md](../docs/STYLE_AND_BEST_PRACTICES.md).
- Do not log vault data, keys or personal data, and never send unencrypted vault data anywhere.

## Making a change

1. Fork the repository and create a branch from `main`.
2. Make your change, with tests where it makes sense.
3. Check it:

    ```sh
    ./gradlew detekt
    ./gradlew app:testFdroidDebugUnitTest
    ```

4. Open a pull request against `main` and fill in the template. CI must pass before a pull
   request is merged.

## License

Multiwarden is licensed under the [GNU GPL v3](../LICENSE.txt). By contributing, you agree that
your contribution is licensed under the same license.

## Code of conduct

Everyone taking part is expected to follow the
[code of conduct](https://github.com/multiwarden/.github/blob/main/CODE_OF_CONDUCT.md).
