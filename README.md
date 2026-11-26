# NodeJS Runner

A native Android Node.js runner project for CodeAssist ADE. The repository name is intentionally unchanged.

## Features

- Dark black/green native Android interface
- Create, open and delete projects
- Import projects from ZIP and export projects to ZIP
- Local project storage in the app's private directory
- File manager with create/open/edit support
- JavaScript editor with Save, Run and Stop
- Terminal log viewer with Refresh and Clear
- Per-project entry-file and start-argument settings
- Foreground runner service
- Keep-awake switch backed by Android `PARTIAL_WAKE_LOCK`
- Embedded Node.js Mobile runtime; no Termux required

## Runtime setup

The Gradle build downloads the official Node.js Mobile Android archive (about 57 MB) and extracts its native libraries and headers automatically. Those native libraries are packaged into the APK. On first app launch, the app initializes the embedded runtime and prepares its private workspace. After installation, the runtime does not need to be downloaded again.

The runtime is Node.js Mobile v18.20.4. Android ABIs configured: `arm64-v8a` and `armeabi-v7a`.

## Open in CodeAssist ADE

1. Open/import this repository as an Android Gradle project.
2. Allow Gradle to sync and install the required Android SDK, NDK and CMake components if prompted.
3. Build the `:app:assembleDebug` task.
4. Keep internet access enabled during the first build so Gradle can fetch the native runtime archive.
5. Install the generated debug APK.

## Runtime limitations

Node.js Mobile supports most Node.js APIs, but it is not a desktop Linux environment. In particular, `child_process` and `cluster` are unsupported. The runner uses a worker thread to isolate the selected script so Stop can terminate that script without restarting the single embedded Node runtime.

This version does not implement a full `npm install` shell command. Dependencies need to be compatible with Node.js Mobile and included in the project by a supported workflow. Native Node add-ons may need Android-specific builds.

## Wakelock note

Keep awake is enabled by default. It uses a partial CPU wake lock while the foreground runner service is active. Disable it from the top switch when you do not need background execution to reduce battery use.

## Upstream runtime

- [Node.js Mobile v18.20.4 release](https://github.com/nodejs-mobile/nodejs-mobile/releases/tag/v18.20.4)
- [Android integration guide](https://nodejs-mobile.github.io/docs/guide/guide-android/getting-started/)
- [Node.js Mobile API differences](https://nodejs-mobile.github.io/docs/api/differences/)
