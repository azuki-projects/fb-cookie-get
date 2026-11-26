# NodeJS Runner

A native Android Node.js runner project for CodeAssist ADE.

- Java Android app, dark black/green UI
- Embedded Node.js Mobile runtime (Node.js 18.20.4)
- Project manager, file editor, run/stop controls and terminal logs
- Foreground runner service and optional partial wake lock
- Android 7.0+ (API 24+), intended for Android 10+

The Gradle build downloads the official Node.js Mobile Android archive and extracts the native libraries and headers automatically during build. The runtime is packaged into the APK; first launch initializes the bundled runtime and creates the workspace. The app does not download or execute a generic desktop Node binary.

## Build

Open this repository in CodeAssist ADE as an Android Gradle project and build `:app:assembleDebug`. The first build needs internet access to download the official Node.js Mobile archive (~57 MB).

## Runtime limitations

Node.js Mobile supports most Node APIs, but `child_process` and `cluster` are unsupported. This initial runner supports JavaScript execution, logs and stopping the active worker. It does not claim to provide a full desktop shell or a complete runtime `npm install` command. Packages must be compatible with Node.js Mobile and added to the project in a supported way.

## Upstream runtime

- Node.js Mobile Android release: https://github.com/nodejs-mobile/nodejs-mobile/releases/tag/v18.20.4
- Android integration guide: https://nodejs-mobile.github.io/docs/guide/guide-android/getting-started/
