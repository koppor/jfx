# Modern Gradle build (experiment)

This branch replaces the legacy ~6,200-line `build.gradle` (plus `buildSrc/`)
with a small, modern Gradle 9.6 build. It is a personal experiment, not an
upstream contribution. Build scripts are Kotlin DSL; the build *logic* is
plain Java (`build-logic/src/main/java`).

## What it does

- Compiles all eleven JPMS modules under `modules/` plus the non-modular
  `javafx.swt` with a JDK 25 toolchain (`--release 25`) and packages them as
  modular jars with a proper module version (`javafx.base@27-internal`).
- Runs the JSL shader generation for `javafx.graphics` (ANTLR grammar → JSLC
  → Decora/Prism compilers → generated Java peers + GLSL `.frag` files, all
  packaged into the jar). Only the ES2 backend is generated; HLSL/MSL feed
  the native pipelines, which are out of scope.
- Assembles an SDK-style module path under `build/sdk/lib` (`gradlew sdk`,
  also part of `assemble`).
- Runs ~39,300 unit tests: `javafx.base` as whitebox JPMS tests (patched into
  the module), and `javafx.graphics`, `javafx.controls`, `javafx.fxml`, and
  `jfx.incubator.richtext` on the classpath — the same unnamed-module setup
  the legacy build uses (its `addExports` files all target `ALL-UNNAMED`),
  which is why none of those arguments are needed here.

```
gradlew build        # compile everything, package, run all wired-up tests
gradlew sdk          # just the module jars in build/sdk/lib
gradlew :javafx.controls:test
```

## How it is put together

| Piece | Role |
| --- | --- |
| `settings.gradle.kts` | Discovers each `modules/*` directory with a `module-info.java` as a subproject ([gradlex `java-module-dependencies`](https://github.com/gradlex-org/java-module-dependencies) settings DSL). |
| `build-logic/.../gradle/JfxJavaModulePlugin.java` | The one convention plugin (plain Java): toolchain, `--release`, version derivation from `build.properties`, test defaults, the `testSupport` variant, the `jfxBuild` extension. |
| `build-logic/.../gradle/JslCompileTask.java` | Task type running the in-tree JSL shader compilers. |
| `build.gradle.kts` | Root project; only aggregates the jars into `build/sdk/lib`. |
| `modules/javafx.base/build.gradle.kts` | `VersionInfo.java` + `javafx.properties` generation, whitebox test wiring. |
| `modules/javafx.graphics/build.gradle.kts` | The shader-generation pipeline and the graphics test wiring. |
| `modules/javafx.swing/build.gradle.kts` | Opts out of `--release` (needs `jdk.unsupported.desktop`). |
| `modules/javafx.swt/build.gradle.kts` | Plain (non-JPMS) library against SWT from Maven Central (`compileOnly`), shipped as `javafx-swt.jar`. |
| `modules/javafx.web/build.gradle.kts` | Puts the in-repo `jdk.jsobject` on the upgrade module path (the JDK still ships its own copy). |
| other `modules/*/build.gradle.kts` | Test wiring only (a few lines each). |

There are **no dependency declarations** for the JavaFX modules anywhere:
`requires` / `requires transitive` in the `module-info.java` files are the
single source of truth, mapped to `implementation` / `api` project
dependencies by the gradlex plugin. `build.properties` remains the single
source of truth for all version numbers (exposed to build scripts as the
`jfxBuild` extension). Cross-module test utilities (`test.util` from base,
`StubToolkit` from graphics) flow through each module's `testSupport`
variant, replacing the legacy `sourceSets.test.output` project reach-ins.

Modern Gradle features in use: configuration cache (warm rebuilds ~300 ms),
build cache, parallel execution, dependency verification
(`gradle/verification-metadata.xml`, kept from the legacy build), toolchain
auto-provisioning (foojay resolver), and reproducible archives (gradlex
`reproducible-builds`; `SOURCE_DATE_EPOCH` is honored for the version-info
timestamp).

## What it deliberately does not do

- **Native compilation** (glass, prism, font, iio, media, WebKit, …) — the
  whole reason the legacy build is big. Gone together with `buildSrc/`.
  Because of this, five graphics tests that require the native libraries
  (`javafx_font`, `prism_common`, the print pipeline) are excluded — see
  `modules/javafx.graphics/build.gradle.kts`.
- **`javafx.web` unit tests** (upstream CI skips them as well; they need the
  WebKit build) and **`javafx.swt` tests** (need a display and a real SWT).
  `javafx.media`, `javafx.swing`, `jdk.jsobject`, and `jfx.incubator.input`
  have no unit tests to begin with.
- Javadoc, jmods, Maven publishing, the `apps/` samples, and `tests/system`.

The resulting jars are therefore **not runnable** JavaFX artifacts — this
branch is about the build, not the product: how small a properly engineered
Gradle setup for the Java side of OpenJFX can be.
