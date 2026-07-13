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

// The sample apps keep their Ant builds (as in the legacy build, which also
// shelled out to Ant); Ant itself comes from Maven Central and runs on the
// toolchain JDK against the assembled SDK.

plugins {
    base
    id("jvm-toolchains")
}

val buildProperties = java.util.Properties().apply {
    rootDir.resolve("build.properties").reader().use { load(it) }
}

fun buildProperty(name: String): String = buildProperties.getProperty(name).trim()

val lucene = configurations.dependencyScope("lucene")
val lucenePath = configurations.resolvable("lucenePath") { extendsFrom(lucene.get()) }
val ant = configurations.dependencyScope("ant")
val antPath = configurations.resolvable("antPath") { extendsFrom(ant.get()) }

dependencies {
    val luceneVersion = buildProperty("lucene.version")
    listOf("lucene-core", "lucene-grouping", "lucene-queryparser").forEach {
        add(lucene.name, "org.apache.lucene:$it:$luceneVersion")
    }
    add(ant.name, "org.apache.ant:ant:${buildProperty("jfx.build.ant.version")}")
}

// Ensemble8 expects its search-index dependencies in its lib directory
val getLucene = tasks.register<Copy>("getLucene") {
    from(lucenePath) {
        include("lucene-core-*.jar", "lucene-grouping-*.jar", "lucene-queryparser-*.jar")
    }
    into(layout.projectDirectory.dir("samples/Ensemble8/lib"))
}

// HelloFXCanvas expects the SWT jar where the legacy build parked it
val swt = configurations.dependencyScope("swt")
val swtPath = configurations.resolvable("swtPath") { extendsFrom(swt.get()) }

dependencies {
    val swtVersion = buildProperty("swt.version").substringBefore(".v")
    val os = System.getProperty("os.name").lowercase()
    val windowingSystem = when {
        os.contains("linux") -> "gtk.linux"
        os.contains("mac") -> "cocoa.macosx"
        else -> "win32.win32"
    }
    val arch = if (System.getProperty("os.arch") == "aarch64") "aarch64" else "x86_64"
    add(swt.name, "org.eclipse.platform:org.eclipse.swt.$windowingSystem.$arch:$swtVersion") {
        isTransitive = false
    }
}

val getSwt = tasks.register<Copy>("getSwt") {
    from(swtPath)
    into(rootProject.layout.buildDirectory.dir("libs"))
    rename { "swt-debug.jar" }
}

val jfxModuleNames = listOf(
    "javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml",
    "javafx.media", "javafx.swing", "javafx.web",
    "jfx.incubator.input", "jfx.incubator.richtext",
).joinToString(",")

// The Ant builds take the module arguments through @argfiles, like the
// compile.args/run.args of the legacy build.
val writeArgFiles = tasks.register("writeArgFiles") {
    val sdkLib = "$rootDir/build/sdk/lib"
    val compileArgs = layout.buildDirectory.file("compile.args")
    val runArgs = layout.buildDirectory.file("run.args")
    outputs.file(compileArgs)
    outputs.file(runArgs)
    doLast {
        compileArgs.get().asFile.writeText("--module-path\n$sdkLib\n--add-modules=$jfxModuleNames\n")
        runArgs.get().asFile.writeText(
            "-Djava.library.path=$sdkLib\n--module-path\n$sdkLib\n--add-modules=$jfxModuleNames\n")
    }
}

val toolchainHome = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(buildProperty("jfx.build.jdk.version.min").toInt())
}.map { it.metadata.installationPath }

val appsJar = tasks.register<JavaExec>("appsJar") {
    description = "Builds the sample apps (Ant) against the assembled SDK."
    dependsOn(":sdk", getLucene, getSwt, writeArgFiles)
    classpath = antPath.get()
    mainClass = "org.apache.tools.ant.launch.Launcher"
    workingDir = projectDir
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(buildProperty("jfx.build.jdk.version.min").toInt())
    }
    args(
        "-DtargetBld=linux",
        "-Dplatforms.JDK_1.9.home=${toolchainHome.get()}",
        "-Dcompile.patch=@${layout.buildDirectory.file("compile.args").get().asFile}",
        "-Drun.patch=@${layout.buildDirectory.file("run.args").get().asFile}",
        "appsJar",
    )
}
