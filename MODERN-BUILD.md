# Modern Gradle build (experiment)

This branch replaces the legacy ~6,200-line `build.gradle` (plus `buildSrc/`)
with a small, modern Gradle 9.6 build. It is a personal experiment, not an
upstream contribution. Build scripts are Kotlin DSL; the build *logic* is
plain Java (`build-logic/src/main/java/com/sun/javafx/gradle`).

## What it covers

- **All Java modules**: the eleven JPMS modules plus the non-modular
  `javafx.swt`, compiled with a JDK 25 toolchain (`--release 25`) and
  packaged as modular jars with proper module versions.
- **JSL shader generation** for `javafx.graphics` (ANTLR → JSLC →
  Decora/Prism compilers → Java peers + GLSL `.frag`, packaged in the jar;
  ES2 backend, as on Linux).
- **Native libraries (Linux desktop)**: glass (GTK3 + launcher), prism
  common/SW/ES2 (GLX), font (+freetype/+pango), iio (with bundled libjpeg),
  and the Decora SSE library — all ten `.so` files, with flags taken from the
  legacy `buildSrc/linux.gradle`, JNI headers generated via `javac -h`.
- **A runnable SDK** under `build/sdk/lib` (jars + natives): verified by
  booting a JavaFX stage headless against it.
- **jmods** (`gradlew jmods` → `build/jmods`, graphics jmod includes the
  native libs), **aggregated javadoc** (`gradlew javadoc`), and **Maven
  publishing** (`publishToMavenLocal`, `javafx-*` artifact ids, sources jars).
- **~39,300 unit tests**: `javafx.base` whitebox (patched into the module),
  graphics/controls/fxml/richtext on the classpath — mirroring the legacy
  ALL-UNNAMED setup. With the natives present, the font/image/print tests run
  too. Cross-module test utilities flow through a `testSupport` variant.
- **System tests** (`tests/system`): always compiled; execution is opt-in
  (`gradlew :systemTests:test -PrunSystemTests`) on the headless glass
  platform with the software pipeline.
- **Sample apps** (`gradlew :apps:appsJar`): the Ant builds run unchanged
  (Ant from Maven Central on the toolchain JDK) against the assembled SDK,
  including Ensemble8 with its Lucene dependencies.

```
gradlew build            # compile, package, natives, run wired-up tests
gradlew sdk              # SDK image: build/sdk/lib (jars + .so)
gradlew jmods javadoc publishToMavenLocal
gradlew :apps:appsJar    # sample apps (Ant) against the SDK
```

## How it is put together

| Piece | Role |
| --- | --- |
| `settings.gradle.kts` | Discovers each `modules/*` dir with a `module-info.java` as a subproject ([gradlex `java-module-dependencies`](https://github.com/gradlex-org/java-module-dependencies) settings DSL); plain includes for `javafx.swt`, `systemTests`, `apps`. |
| `build-logic/.../JfxJavaModulePlugin.java` | The one convention plugin: toolchain, `--release`, version from `build.properties`, test defaults, `testSupport` variant, publishing, jmod task, `jfxBuild` extension. |
| `build-logic/.../JslCompileTask.java` | Runs the in-tree JSL shader compilers. |
| `build-logic/.../NativeLibTask.java` | gcc compile + g++ link of one native library. |
| `build-logic/.../JmodTask.java` | `jmod create` via the toolchain. |
| `build.gradle.kts` | SDK/jmods aggregation, aggregated javadoc. |
| `modules/javafx.graphics/build.gradle.kts` | Shader pipeline, the ten native libraries, test wiring. |
| other `modules/*/build.gradle.kts` | A few lines each: test wiring and per-module quirks (see file comments). |

There are **no dependency declarations** for the JavaFX modules anywhere:
`requires` directives in `module-info.java` are the single source of truth.
`build.properties` stays authoritative for all versions (exposed as the
`jfxBuild` extension). Configuration cache, build cache, parallel execution,
dependency verification, toolchain auto-provisioning, and reproducible
archives are all active.

## What remains out

- **Media and WebKit natives** — off by default in the legacy build as well
  (`COMPILE_MEDIA` / `COMPILE_WEBKIT`); consequently the `javafx.web` unit
  tests (skipped in upstream CI too) and the WebKit-generated DOM shims stay
  out.
- **Other platforms**: the native recipe is ported for Linux desktop only
  (no Windows/macOS/Android/iOS, no cross builds).
- **Robot/visual, AWT-embedding, and module-launcher system tests** (need a
  display or the `testapp*` trees).
- `javafx.swt` tests (need a display and a real SWT).
- The closed-build machinery, JCov, and the legacy publishing pipeline
  (platform-classifier Maven layout, signed bundles).
