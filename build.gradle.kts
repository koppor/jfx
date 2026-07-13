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

// All real build logic lives in the 'jfx.java-module' convention plugin (see
// 'build-logic/') and is wired to the subprojects in settings.gradle.kts.
// This root project only aggregates the module jars into an SDK-style
// directory usable as a '--module-path': build/sdk/lib.

plugins {
    base
    id("jvm-toolchains")
}

// The JPMS modules; javafx.swt additionally ships as a plain jar.
val jfxModules = listOf(
    "javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml",
    "javafx.media", "javafx.swing", "javafx.web", "jdk.jsobject",
    "jfx.incubator.input", "jfx.incubator.richtext",
)

val buildProperties = java.util.Properties().apply {
    file("build.properties").reader().use { load(it) }
}

val sdkModules = configurations.dependencyScope("sdkModules")

val sdkModulePath = configurations.resolvable("sdkModulePath") {
    extendsFrom(sdkModules.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

dependencies {
    (jfxModules + "javafx.swt").forEach { add(sdkModules.name, project(":$it")) }
}

val graphicsNatives = configurations.dependencyScope("graphicsNatives")

val graphicsNativesPath = configurations.resolvable("graphicsNativesPath") {
    extendsFrom(graphicsNatives.get())
}

dependencies {
    add(graphicsNatives.name,
        project(mapOf("path" to ":javafx.graphics", "configuration" to "nativeLibs")))
}

val sdk = tasks.register<Sync>("sdk") {
    group = "build"
    description = "Copies all JavaFX module jars and native libraries into build/sdk/lib."
    from(sdkModulePath)
    from(graphicsNativesPath)
    into(layout.buildDirectory.dir("sdk/lib"))
    // javafx.base-27-internal.jar -> javafx.base.jar
    rename("(.+)-\\d.*\\.jar", "$1.jar")
}

tasks.assemble {
    dependsOn(sdk)
}

val jmods = tasks.register<Sync>("jmods") {
    group = "build"
    description = "Collects the jmods of all modules into build/jmods."
    into(layout.buildDirectory.dir("jmods"))
    jfxModules.forEach {
        from("modules/$it/build/jmods")
        dependsOn(":$it:jmod")
    }
}

val javadoc = tasks.register<Javadoc>("javadoc") {
    group = "documentation"
    description = "Generates the aggregated JavaFX API documentation."
    // VersionInfo.java is generated and referenced from javafx.base internals
    dependsOn(":javafx.base:generateVersionInfo")
    setDestinationDir(layout.buildDirectory.dir("javadoc").get().asFile)
    // Only the public API packages are documented; internals are picked up
    // for resolution through the module source path.
    jfxModules.forEach { m ->
        source(fileTree("modules/$m/src/main/java") {
            include("javafx/**/*.java", "jfx/incubator/**/*.java", "netscape/**/*.java", "module-info.java")
        })
    }
    classpath = files()
    javadocTool = javaToolchains.javadocToolFor {
        languageVersion = JavaLanguageVersion.of(
            buildProperties.getProperty("jfx.build.jdk.version.min").trim().toInt())
    }
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        windowTitle = "JavaFX API"
        addStringOption(
            "-module-source-path",
            listOf(
                "$projectDir/modules/*/src/main/java",
                "$projectDir/modules/*/build/generated/sources/version-info",
            ).joinToString(File.pathSeparator))
        // the legacy build uses a curated doclint set; this build documents
        // as-is and leaves lint to upstream
        addBooleanOption("Xdoclint:none", true)
        links("https://docs.oracle.com/en/java/javase/"
            + buildProperties.getProperty("jdk.docs.version").trim() + "/docs/api/")
    }
}
