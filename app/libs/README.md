# Drop-in native libraries

`app/build.gradle.kts` picks up any `.aar` or `.jar` placed here.

## sherpa-onnx (speech stack)

Android is distributed as a prebuilt AAR from GitHub releases, not as a Maven artifact —
the JitPack coordinate publishes only the JVM/desktop build and would add roughly 100 MB
of Linux, macOS and Windows native libraries to the APK.

Download `sherpa-onnx-<version>-android.aar` from
<https://github.com/k2-fsa/sherpa-onnx/releases> and drop it in this directory.

Apache-2.0. See the README's "Speech stack" section for which model files to pair with it.
