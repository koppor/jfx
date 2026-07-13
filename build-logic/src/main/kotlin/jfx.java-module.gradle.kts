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

import java.util.Properties

plugins {
    id("java-library")
    id("org.gradlex.java-module-dependencies")
    id("org.gradlex.java-module-testing")
    id("org.gradlex.reproducible-builds")
}

// build.properties remains the single source of truth for version numbers; it
// is also read by CI and the release tooling.
val buildProperties = Properties().apply {
    rootDir.resolve("build.properties").reader().use { load(it) }
}

fun buildProperty(name: String): String =
    buildProperties.getProperty(name)?.trim() ?: error("missing '$name' in build.properties")

// "27.0.0.0" collapses to "27". Builds outside the release pipeline are always
// "-internal" builds, matching the behavior of the legacy build.
val releaseVersion = listOf("major", "minor", "security", "patch")
    .map { buildProperty("jfx.release.$it.version") }
    .dropLastWhile { it == "0" }
    .joinToString(".")

version = "$releaseVersion-internal"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(buildProperty("jfx.build.jdk.version.min").toInt())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = buildProperty("jfx.jdk.target.version").toInt()
    options.encoding = "UTF-8"
}

tasks.compileJava {
    options.javaModuleVersion = provider { version.toString() }
}

testing.suites.named<JvmTestSuite>("test") {
    useJUnitJupiter(buildProperty("junit.jupiter.version"))
}

// Most modules have no tests wired up (yet), which Gradle 9.6 would flag as a
// misconfiguration.
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}

// The unit-test harness of the legacy build (shims, add-exports files) is
// ported per module: a module opts in by setting its test source directories
// explicitly (see modules/javafx.base). Everything else has no tests wired up.
sourceSets.test {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
}
