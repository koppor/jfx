/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import com.sun.javafx.gradle.JmodTask
import com.sun.javafx.gradle.JslCompileTask
import com.sun.javafx.gradle.NativeLibTask

plugins {
    id("jfx.java-module")
}

// ---------------------------------------------------------------------------
// JSL shader generation (Decora effects and Prism paints).
//
// The JSLC compiler infrastructure (src/jslc) is compiled against the ANTLR-
// generated JSL grammar. The shader compilers themselves live as sources in
// src/main/jsl-decora and src/main/jsl-prism; they run against the compiled
// main classes and emit Java shader peers plus GLSL (.frag) files, which are
// compiled/packaged into the module jar. Only the ES2 backend is generated;
// HLSL/MSL belong to the native pipelines, which this build does not cover.
// ---------------------------------------------------------------------------

val jslc = sourceSets.create("jslc")

dependencies {
    "jslcImplementation"("org.antlr:antlr4:${jfxBuild.property("antlr4.version")}:complete")
}

val generateGrammarSource = tasks.register<JavaExec>("generateGrammarSource") {
    description = "Generates the JSL parser from the ANTLR grammar."
    val grammarDir = layout.projectDirectory.dir("src/jslc/antlr")
    val outputDir = layout.buildDirectory.dir("generated/sources/antlr")
    inputs.dir(grammarDir)
    outputs.dir(outputDir)
    classpath = configurations["jslcCompileClasspath"]
    mainClass = "org.antlr.v4.Tool"
    workingDir = grammarDir.asFile
    argumentProviders.add {
        listOf(
            "-o", outputDir.get().asFile.path,
            "-package", "com.sun.scenario.effect.compiler",
            "-visitor", "com/sun/scenario/effect/compiler/JSL.g4",
        )
    }
}

jslc.java.srcDir(generateGrammarSource.map { it.outputs.files.singleFile })

val toolchainLauncher = javaToolchains.launcherFor(java.toolchain)
val toolchainCompiler = javaToolchains.compilerFor(java.toolchain)

// The shader compilers of each group are compiled separately (they clash on
// class names, e.g. both groups have a CompileJSL).
fun compilerCompileTask(group: String) = tasks.register<JavaCompile>(
    "compile${group.replaceFirstChar(Char::uppercase)}Compilers"
) {
    description = "Compiles the $group shader compilers."
    source(fileTree("src/main/jsl-$group") { include("*.java") })
    classpath = files(jslc.output, configurations["jslcCompileClasspath"],
        sourceSets.main.get().output, configurations.compileClasspath)
    destinationDirectory = layout.buildDirectory.dir("classes/jsl-compilers/$group")
    // ad-hoc JavaCompile tasks do not inherit the toolchain
    javaCompiler = toolchainCompiler
}

val compileDecoraCompilers = compilerCompileTask("decora")
val compilePrismCompilers = compilerCompileTask("prism")

val generateDecoraShaders = tasks.register<JslCompileTask>("generateDecoraShaders") {
    description = "Generates the Decora effect shaders (Java peers + GLSL)."
    compilerClasspath.from(compileDecoraCompilers, jslc.output, configurations["jslcRuntimeClasspath"],
        layout.projectDirectory.dir("src/jslc/resources"),
        sourceSets.main.get().output, configurations.compileClasspath)
    sourceDir = layout.projectDirectory.dir("src/main/jsl-decora")
    outputDir = layout.buildDirectory.dir("generated/sources/jsl-decora")
    compilerMainClass = "GenAllDecoraShaders"
    packagePath = "com/sun/scenario/effect"
    modeArgs = listOf("-all", "GenAllDecoraShaders")
    launcher = toolchainLauncher
}

val generatePrismShaders = tasks.register<JslCompileTask>("generatePrismShaders") {
    description = "Generates the Prism paint shaders (Java peers + GLSL)."
    compilerClasspath.from(compilePrismCompilers, jslc.output, configurations["jslcRuntimeClasspath"],
        // PrismLoaderGlue.stg is looked up from the classpath
        layout.projectDirectory.dir("src/main/jsl-prism"),
        sourceSets.main.get().output, configurations.compileClasspath)
    sourceDir = layout.projectDirectory.dir("src/main/jsl-prism")
    outputDir = layout.buildDirectory.dir("generated/sources/jsl-prism")
    compilerMainClass = "CompileJSL"
    packagePath = "com/sun/prism"
    perSourceFile = true
    launcher = toolchainLauncher
}

// The generated peers are ordinary module classes; they only need a classpath
// compile because the sources arrive after the module itself is compiled.
val shaders = sourceSets.create("shaders") {
    java.srcDir(generateDecoraShaders.map { it.outputDir })
    java.srcDir(generatePrismShaders.map { it.outputDir })
}

tasks.named<JavaCompile>("compileShadersJava") {
    classpath = files(sourceSets.main.get().output, configurations.compileClasspath)
}

tasks.jar {
    from(shaders.output)
    from(generateDecoraShaders.map { it.outputDir }) { include("**/*.frag") }
    from(generatePrismShaders.map { it.outputDir }) { include("**/*.frag") }
}

// ---------------------------------------------------------------------------
// Unit tests. They run on the classpath (unnamed module), like in the legacy
// build, where every entry of src/test/addExports targets ALL-UNNAMED; with
// no module system at test runtime, none of those arguments are needed.
// ---------------------------------------------------------------------------

sourceSets.test {
    java.setSrcDirs(listOf("src/test/java", "src/shims/java"))
    resources.setSrcDirs(listOf("src/test/resources"))
}

javaModuleTesting.classpath(testing.suites["test"])

dependencies {
    testImplementation(project(":javafx.base", "testSupport"))
}

tasks.test {
    systemProperty("javafx.toolkit", "test.com.sun.javafx.pgstub.StubToolkit")
    // CssMetaDataTest scans the compiled javafx.** classes
    systemProperty("CSS_META_DATA_TEST_DIR",
        layout.buildDirectory.dir("classes/java/main/javafx").get().asFile.path)
    // from src/test/addExports; J2DImageLoaderTest reflects into ImageIO
    jvmArgs("--add-opens", "java.desktop/javax.imageio=ALL-UNNAMED")
}

// ---------------------------------------------------------------------------
// Native libraries (Linux desktop). Flags and source lists follow the legacy
// buildSrc/linux.gradle: everything compiles with gcc and links with g++,
// C++ runtimes linked statically. Media and WebKit natives stay out, as in
// the legacy build's defaults.
// ---------------------------------------------------------------------------

val mainJniHeaders = layout.buildDirectory.dir("generated/headers/main")
tasks.compileJava {
    options.compilerArgs.addAll(listOf("-h", mainJniHeaders.get().asFile.path))
    outputs.dir(mainJniHeaders)
}

val shadersJniHeaders = layout.buildDirectory.dir("generated/headers/shaders")
tasks.named<JavaCompile>("compileShadersJava") {
    options.compilerArgs.addAll(listOf("-h", shadersJniHeaders.get().asFile.path))
    outputs.dir(shadersJniHeaders)
}

fun pkgConfig(vararg args: String): Provider<List<String>> = providers.exec {
    commandLine("pkg-config", *args)
}.standardOutput.asText.map { it.trim().split(Regex("\\s+")).filter(String::isNotEmpty) }

val commonNativeFlags = listOf(
    "-fno-strict-aliasing", "-fPIC", "-fno-omit-frame-pointer", "-fstack-protector",
    "-Wextra", "-Wall", "-Wformat-security", "-Wno-unused", "-Wno-parentheses",
    "-Werror=trampolines")
val cppFlags = commonNativeFlags +
    listOf("-c", "-ffunction-sections", "-fdata-sections", "-O2", "-DNDEBUG")
val cFlags = cppFlags + "-Werror=implicit-function-declaration"
val dynamicLinkFlags = listOf("-static-libgcc", "-static-libstdc++", "-shared") +
    commonNativeFlags + listOf("-z", "relro", "-Wl,--gc-sections")

val jdkIncludeDirs = toolchainLauncher.map {
    val include = it.metadata.installationPath.dir("include")
    listOf(include, include.dir("linux"))
}

fun nativeLib(taskName: String, libFile: String, configure: NativeLibTask.() -> Unit) =
    tasks.register<NativeLibTask>(taskName) {
        description = "Compiles and links $libFile."
        includeDirs.from(
            tasks.compileJava.map { mainJniHeaders.get() },
            tasks.named<JavaCompile>("compileShadersJava").map { shadersJniHeaders.get() },
            jdkIncludeDirs)
        linkFlags.addAll(dynamicLinkFlags)
        outputFile = layout.buildDirectory.file("native/$libFile")
        configure()
    }

val nativeGlassLauncher = nativeLib("nativeGlassLauncher", "libglass.so") {
    sources.from(fileTree("src/main/native-glass/gtk") { include("**/launcher.c") })
    ccFlags.addAll(cppFlags + "-Werror")
    linkFlags.addAll(listOf("-lX11", "-ldl"))
}

val nativeGlassGtk3 = nativeLib("nativeGlassGtk3", "libglassgtk3.so") {
    sources.from(fileTree("src/main/native-glass/gtk") {
        include("**/*.c", "**/*.cpp", "**/*.cc")
        exclude("**/launcher.c")
    })
    includeDirs.from(layout.projectDirectory.dir("src/main/native-glass/gtk/libpipewire/include"))
    ccFlags.addAll(cppFlags)
    ccFlags.addAll(listOf(
        "-Wno-deprecated-declarations", "-DGTK_3_MIN_MINOR_VERSION=20", "-DGTK_3_MIN_MICRO_VERSION=0"))
    ccFlags.addAll(pkgConfig("--cflags", "gtk+-3.0", "gthread-2.0", "xtst", "gio-unix-2.0"))
    ccFlags.add("-Werror")
    linkFlags.addAll(pkgConfig("--libs", "gtk+-3.0", "gthread-2.0", "xtst", "gio-unix-2.0"))
}

val nativePrism = nativeLib("nativePrism", "libprism_common.so") {
    sources.from(fileTree("src/main/native-prism") { include("*.c") })
    includeDirs.from(layout.projectDirectory.dir("src/main/native-prism"))
    ccFlags.addAll(cFlags + "-DINLINE=inline")
}

val nativePrismSw = nativeLib("nativePrismSw", "libprism_sw.so") {
    sources.from(fileTree("src/main/native-prism-sw") { include("*.c") })
    includeDirs.from(layout.projectDirectory.dir("src/main/native-prism-sw"))
    ccFlags.addAll(cFlags + "-DINLINE=inline")
}

val nativePrismEs2 = nativeLib("nativePrismEs2", "libprism_es2.so") {
    sources.from(
        fileTree("src/main/native-prism-es2") { include("*.c") },
        fileTree("src/main/native-prism-es2/x11") { include("*.c") })
    includeDirs.from(
        layout.projectDirectory.dir("src/main/native-prism-es2"),
        layout.projectDirectory.dir("src/main/native-prism-es2/GL"),
        layout.projectDirectory.dir("src/main/native-prism-es2/x11"))
    ccFlags.addAll(listOf("-DLINUX") + cFlags)
    linkFlags.addAll(listOf("-lX11", "-lXxf86vm", "-lGL"))
}

val nativeFont = nativeLib("nativeFont", "libjavafx_font.so") {
    sources.from(fileTree("src/main/native-font") { include("*.c", "*.cpp") })
    includeDirs.from(layout.projectDirectory.dir("src/main/native-font"))
    ccFlags.addAll(listOf("-DJFXFONT_PLUS") + cppFlags)
}

val nativeFontFreetype = nativeLib("nativeFontFreetype", "libjavafx_font_freetype.so") {
    sources.from(layout.projectDirectory.file("src/main/native-font/freetype.c"))
    includeDirs.from(layout.projectDirectory.dir("src/main/native-font"))
    ccFlags.addAll(listOf("-DJFXFONT_PLUS") + cFlags + "-D_ENABLE_PANGO")
    ccFlags.addAll(pkgConfig("--cflags", "freetype2"))
    linkFlags.addAll(pkgConfig("--libs", "freetype2"))
}

val nativeFontPango = nativeLib("nativeFontPango", "libjavafx_font_pango.so") {
    sources.from(layout.projectDirectory.file("src/main/native-font/pango.c"))
    includeDirs.from(layout.projectDirectory.dir("src/main/native-font"))
    ccFlags.addAll(listOf("-DJFXFONT_PLUS") + cFlags + "-D_ENABLE_PANGO")
    ccFlags.addAll(pkgConfig("--cflags", "pangoft2"))
    linkFlags.addAll(pkgConfig("--libs", "pangoft2"))
}

val nativeIio = nativeLib("nativeIio", "libjavafx_iio.so") {
    sources.from(
        fileTree("src/main/native-iio") { include("*.c") },
        fileTree("src/main/native-iio/libjpeg") { include("*.c") })
    includeDirs.from(
        layout.projectDirectory.dir("src/main/native-iio"),
        layout.projectDirectory.dir("src/main/native-iio/libjpeg"))
    ccFlags.addAll(cFlags + "-fvisibility=hidden")
}

val nativeDecora = nativeLib("nativeDecora", "libdecora_sse.so") {
    sources.from(
        generateDecoraShaders.flatMap { it.outputDir }
            .map { it.asFileTree.matching { include("**/*.cc") } },
        fileTree("src/main/native-decora") { include("*.cc") })
    includeDirs.from(
        generateDecoraShaders.flatMap { it.outputDir },
        layout.projectDirectory.dir("src/main/native-decora"))
    ccFlags.addAll(cppFlags + "-ffast-math")
}

val collectNativeLibs = tasks.register<Sync>("collectNativeLibs") {
    description = "Collects all native libraries into build/natives."
    listOf(
        nativeGlassLauncher, nativeGlassGtk3, nativePrism, nativePrismSw, nativePrismEs2,
        nativeFont, nativeFontFreetype, nativeFontPango, nativeIio, nativeDecora,
    ).forEach { from(it) }
    into(layout.buildDirectory.dir("natives"))
}

tasks.assemble {
    dependsOn(collectNativeLibs)
}

// downstream consumers: the root SDK image and this module's jmod
configurations.consumable("nativeLibs")
artifacts.add("nativeLibs", collectNativeLibs.map { it.destinationDir })

tasks.named<JmodTask>("jmod") {
    nativeLibsDir = layout.dir(collectNativeLibs.map { it.destinationDir })
}

// fonts, image loading, and printing tests load the native libraries
tasks.test {
    dependsOn(collectNativeLibs)
    systemProperty("java.library.path", layout.buildDirectory.dir("natives").get().asFile.path)
}
