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

// System tests boot the real toolkit. They always compile as part of 'build';
// running them is opt-in (-PrunSystemTests) and uses the headless glass
// platform with the software pipeline. The module-launcher tests (their
// testapp* source trees) and robot/AWT-embedding tests are not wired up.

plugins {
    id("jfx.java-module")
}

sourceSets.test {
    java.setSrcDirs(listOf("src/test/java"))
    resources.setSrcDirs(listOf("src/test/resources"))
}

dependencies {
    listOf(
        "javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml",
        "javafx.media", "javafx.swing", "javafx.web", "jdk.jsobject",
        "jfx.incubator.input", "jfx.incubator.richtext",
    ).forEach { testImplementation(project(":$it")) }
    testImplementation(project(":javafx.base", "testSupport"))
    testImplementation(project(":javafx.graphics", "testSupport"))
    testImplementation(project(":javafx.controls", "testSupport"))
    testImplementation(project(":javafx.swing", "testSupport"))
    testImplementation(project(":javafx.web", "testSupport"))
    testImplementation(project(":javafx.swt"))
}

tasks.compileTestJava {
    // ClipboardTest compiles against sun.awt.datatransfer; exporting a system
    // package is not allowed together with --release, so this project compiles
    // against the toolchain's full module set (the toolchain is pinned anyway).
    options.release.set(null as Int?)
    options.compilerArgs.addAll(listOf("--add-exports", "java.desktop/sun.awt.datatransfer=ALL-UNNAMED"))
}

tasks.test {
    enabled = providers.gradleProperty("runSystemTests").isPresent
    dependsOn(":javafx.graphics:collectNativeLibs")
    systemProperty("glass.platform", "Headless")
    systemProperty("prism.order", "sw")
    systemProperty("java.library.path", "$rootDir/modules/javafx.graphics/build/natives")
    // as in the legacy build
    forkEvery = 1
    maxHeapSize = "1000m"
    // robot-based visual tests, AWT embedding, and the module-launcher tests
    // (which need the testapp* trees) are not runnable here
    exclude("test/robot/**")
    exclude("**/javafx/embed/swing/*.*")
    exclude("**/com/sun/javafx/application/Swing*.*")
    exclude("test/launchertest/**")
}
