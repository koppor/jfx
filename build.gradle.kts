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
    listOf(
        "javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml",
        "javafx.media", "javafx.swing", "javafx.swt", "javafx.web",
        "jdk.jsobject", "jfx.incubator.input", "jfx.incubator.richtext",
    ).forEach { add(sdkModules.name, project(":$it")) }
}

val sdk by tasks.registering(Sync::class) {
    group = "build"
    description = "Copies all JavaFX module jars into build/sdk/lib."
    from(sdkModulePath)
    into(layout.buildDirectory.dir("sdk/lib"))
    // javafx.base-27-internal.jar -> javafx.base.jar
    rename("(.+)-\\d.*\\.jar", "$1.jar")
}

tasks.assemble {
    dependsOn(sdk)
}
