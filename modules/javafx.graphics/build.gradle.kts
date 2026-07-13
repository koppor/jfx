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

import com.sun.javafx.gradle.JslCompileTask

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

val jslc by sourceSets.creating

dependencies {
    "jslcImplementation"("org.antlr:antlr4:${jfxBuild.property("antlr4.version")}:complete")
}

val generateGrammarSource by tasks.registering(JavaExec::class) {
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

val generateDecoraShaders by tasks.registering(JslCompileTask::class) {
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

val generatePrismShaders by tasks.registering(JslCompileTask::class) {
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
val shaders by sourceSets.creating {
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
    filter {
        // need the native libraries (javafx_font, prism_common) or the print
        // pipeline on top of them, which this build does not produce
        excludeTestsMatching("test.com.sun.javafx.font.PrismFontFactoryTest")
        excludeTestsMatching("test.com.sun.javafx.iio.ImageLoaderScalingTest")
        excludeTestsMatching("test.javafx.print.JobSettingsTest")
        excludeTestsMatching("test.javafx.print.PrinterJobTest")
        excludeTestsMatching("test.javafx.scene.canvas.CanvasTest.testPropertyPropagation_visible")
    }
}
