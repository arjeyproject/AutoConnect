# Contributing

Pull requests are welcome. Keep them focused and easy to review.

## Before you open a pull request

```bash
bash scripts/fetch-android-assets.sh
cd android
gradle testDebugUnitTest lintDebug assembleDebug
```

## House rules

- Java for the Android application. No new dependency without a clear reason.
- Every user-facing string goes in `res/values/strings.xml` **and** `res/values-fa/strings.xml`.
  Untranslated strings are treated as broken.
- Use design tokens from `res/values/colors.xml` and the shared styles in `res/values/themes.xml`.
  No hard-coded colors, sizes or radii in layouts.
- Right-to-left safety: use `start`/`end`, never `left`/`right`. Keep addresses, ports, versions and
  code left to right.
- Both appearances matter. Anything added to the light theme must be checked in the dark theme.
- Never commit signing material or the contents of `jniLibs/`.

## Reporting bugs

Open an issue with the app version, device model, Android version, the mode and protocol in use,
and what you expected instead. Screenshots help.
