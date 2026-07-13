# Modern Gradle build (experiment)

This branch replaces the legacy ~6,200-line `build.gradle` (plus `buildSrc/`)
with a small, modern Gradle 9.6 / Kotlin DSL build. It is a personal
experiment, not an upstream contribution.

## What it does

- Compiles the Java sources of all eleven JPMS modules under `modules/` with a
  JDK 25 toolchain (`--release 25`) and packages them as modular jars with a
  proper module version (`javafx.base@27-internal`).
- Assembles an SDK-style module path under `build/sdk/lib` (`gradlew sdk`,
  also part of `assemble`).
- Runs the `javafx.base` unit tests (~5,500 tests) as real whitebox JPMS
  tests: test classes and shims are patched into the module, replacing the
  legacy `addExports`-file harness.

```
gradlew build        # compile everything, package, run wired-up tests
gradlew sdk          # just the module jars in build/sdk/lib
gradlew :javafx.base:test
```

## How it is put together

| Piece | Role |
| --- | --- |
| `settings.gradle.kts` | Discovers each `modules/*` directory with a `module-info.java` as a subproject ([gradlex `java-module-dependencies`](https://github.com/gradlex-org/java-module-dependencies) settings DSL). |
| `build-logic/src/main/kotlin/jfx.java-module.gradle.kts` | The one convention plugin: toolchain, `--release`, version derivation from `build.properties`, test defaults. |
| `build.gradle.kts` | Root project; only aggregates the module jars into `build/sdk/lib`. |
| `modules/javafx.base/build.gradle.kts` | `VersionInfo.java` + `javafx.properties` generation, whitebox test wiring. |
| `modules/javafx.swing/build.gradle.kts` | Opts out of `--release` (needs `jdk.unsupported.desktop`). |
| `modules/javafx.web/build.gradle.kts` | Puts the in-repo `jdk.jsobject` on the upgrade module path (the JDK still ships its own copy). |

There are **no dependency declarations** for the JavaFX modules anywhere:
`requires` / `requires transitive` in the `module-info.java` files are the
single source of truth, mapped to `implementation` / `api` project
dependencies by the gradlex plugin. `build.properties` remains the single
source of truth for all version numbers.

Modern Gradle features in use: configuration cache (warm rebuilds ~300 ms),
build cache, parallel execution, dependency verification
(`gradle/verification-metadata.xml`, kept from the legacy build), toolchain
auto-provisioning (foojay resolver), and reproducible archives (gradlex
`reproducible-builds`; `SOURCE_DATE_EPOCH` is honored for the version-info
timestamp).

## What it deliberately does not do

- **Native compilation** (glass, prism, font, media, WebKit, …) — the whole
  reason the legacy build is big. Gone together with `buildSrc/`.
- **JSL shader generation** for `javafx.graphics` (Decora/Prism). The main
  sources compile without the generated shader peers; they are only loaded
  reflectively at runtime.
- **`javafx.swt`** (not a JPMS module, needs an external SWT jar).
- Unit tests of the other modules: the wiring pattern is demonstrated on
  `javafx.base` and is meant to be extended module by module (each needs its
  shims and cross-module test utilities ported).
- Javadoc, jmods, publishing, the `apps/` samples, and `tests/system`.

The resulting jars are therefore **not runnable** JavaFX artifacts — this
branch is about the build, not the product: how small a properly engineered
Gradle setup for the Java side of OpenJFX can be.
