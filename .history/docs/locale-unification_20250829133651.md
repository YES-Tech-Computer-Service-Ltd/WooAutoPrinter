## Internationalization & Locale Unification Summary

### Background
Users reported that, even after selecting Chinese, parts of the app still appeared in English for a while and then switched to Chinese. This was intermittent and most visible on first launch, Settings, bottom navigation, and some order detail text.

### Root Causes
- Multiple, competing locale application paths existed:
  - Direct `Resources.updateConfiguration` and `Locale.setDefault`.
  - `AppCompatDelegate.setApplicationLocales` via `LocaleManager/LocaleHelper`.
- Language was initialized asynchronously during `MainActivity.onCreate`, so the first frame could render using the system locale (often English) and later switch to Chinese (visual flicker).
- `MainActivity` extended `ComponentActivity`, so AppCompat’s locale APIs did not fully manage resource loading for that Activity/Compose tree.
- A few UI strings were hardcoded in English instead of using string resources.

### What Changed
- Unified the language switch entry point to `LocaleManager` and modern AppCompat APIs:
  - Use `LocaleManager.updateLocale` / `LocaleManager.setAndSaveLocale` → `AppCompatDelegate.setApplicationLocales`.
  - Removed direct `Resources.updateConfiguration` calls. Kept `Locale.setDefault(locale)` where appropriate to align number/date formatting with the UI.
- Moved language initialization earlier and made it synchronous:
  - Call `LocaleManager.initialize(applicationContext)` in `WooAutoApplication.onCreate`.
  - Ensure initialization in `MainActivity.onCreate` happens before UI composition.
- Switched `MainActivity` to extend `AppCompatActivity` so AppCompat’s locale management fully applies.
- Deprecated and then removed `LocaleHelper.setLocale`. `LocaleHelper` now remains a small utility (persist/load locale, localized `Resources` helpers) without any app-wide “set locale” entry.
- Replaced hardcoded strings with resource-based strings in key places (ensuring both English and Chinese are provided):
  - `MainActivity.kt` (action button text)
  - `ProductsScreen.kt` (navigation/back-to-orders text)
  - `BackgroundPollingService.kt` (notification channel name/description, progress text, order notifications)

### Files Touched (key)
- `app/src/main/java/com/example/wooauto/WooAutoApplication.kt`
- `app/src/main/java/com/example/wooauto/MainActivity.kt`
- `app/src/main/java/com/example/wooauto/utils/LocaleManager.kt`
- `app/src/main/java/com/example/wooauto/utils/LocaleHelper.kt` (removed legacy `setLocale`)
- `app/src/main/java/com/example/wooauto/presentation/screens/products/ProductsScreen.kt`
- `app/src/main/java/com/example/wooauto/service/BackgroundPollingService.kt`
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-zh/strings.xml`

### Compatibility Notes
- Project `minSdk=24`: fully aligned with modern AppCompat locale APIs. No legacy `updateConfiguration` path needed.
- Compose works correctly under `AppCompatActivity` with `AppCompatDelegate.setApplicationLocales`.
- `Locale.setDefault(locale)` is retained to keep formatting (date/number/currency) consistent in libraries that read the JVM default locale.

### Migration & Usage Guidance
- Do not call any direct locale setters outside `LocaleManager`.
- Only use:
  - `LocaleManager.setAndSaveLocale(context, locale)` when the user changes language in Settings.
  - `LocaleManager.updateLocale(locale)` when applying an already-saved locale during initialization.
- Avoid direct `Resources.updateConfiguration`, `createConfigurationContext` for app-wide locale changes.
- Keep all user-facing text in resources; do not hardcode strings in code.

### Verification Checklist
1. Cold start with system language = English, app language saved = Chinese → first frame renders in Chinese (no flicker).
2. Bottom navigation labels reflect the selected app language.
3. Settings screen: all titles/subtitles/hints are localized.
4. Orders list and Order Details screens: all texts resolve from string resources and reflect the selected locale.
5. Notifications (channel name/description and live texts) show the correct language.
6. Change language in Settings → UI updates accordingly; restart app → language persists.

### Known Considerations
- Some third-party components may rely on `Locale.getDefault()`. We set the default locale alongside AppCompat locales to keep formatting consistent.
- If the system language changes while the app is running, a process restart may be required for full, consistent localization across all components.

### Rationale
This unification removes race conditions and split responsibilities, ensures first-frame correctness, and aligns with recommended AppCompat locale management on modern Android, while minimizing risk by preserving necessary formatting behavior.


