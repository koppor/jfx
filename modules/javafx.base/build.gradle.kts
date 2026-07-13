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

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    id("jfx.java-module")
}

// The timestamp only leaves the fixed default when SOURCE_DATE_EPOCH is set
// (as the release pipeline does), so that local builds stay deterministic and
// cache-friendly.
val buildTimestamp = providers.environmentVariable("SOURCE_DATE_EPOCH")
    .map {
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochSecond(it.toLong()))
    }
    .getOrElse("1970-01-01-000000")

val versionLong = "$version+0-$buildTimestamp"

val generateVersionInfo = tasks.register<Sync>("generateVersionInfo") {
    description = "Fills in the version placeholders of VersionInfo.java."
    val tokens = mapOf(
        "RELEASE_VERSION_SHORT" to version.toString(),
        "RELEASE_VERSION_LONG" to versionLong,
        "RELEASE_SUFFIX" to "-internal",
        "BUILD_TIMESTAMP" to buildTimestamp,
        "HUDSON_JOB_NAME" to "not_hudson",
        "HUDSON_BUILD_NUMBER" to "0000",
    )
    inputs.property("tokens", tokens)
    into(layout.buildDirectory.dir("generated/sources/version-info"))
    from(layout.projectDirectory.dir("src/main/version-info")) {
        into("com/sun/javafx/runtime")
        filter<ReplaceTokens>("tokens" to tokens)
    }
}

sourceSets.main {
    java.srcDir(generateVersionInfo.map { it.destinationDir })
}

// VersionInfoTest cross-checks VersionInfo against this file.
val generateJavafxProperties = tasks.register<WriteProperties>("generateJavafxProperties") {
    description = "Writes the javafx.properties file as found in an SDK."
    destinationFile = layout.buildDirectory.file("module-lib/javafx.properties")
    property("javafx.version", version.toString())
    property("javafx.runtime.version", versionLong)
}

sourceSets.test {
    java.setSrcDirs(listOf("src/test/java", "src/shims/java"))
}

javaModuleTesting.whitebox(testing.suites["test"]) {
    requires.add("org.junit.jupiter.api")
    requires.add("org.junit.jupiter.params")
    // for test.util.memory.JMemoryBuddy
    requires.add("java.management")
    requires.add("jdk.management")
    opensTo.add("org.junit.platform.commons")
}

tasks.test {
    dependsOn(generateJavafxProperties)
}
