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

pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradlex.java-module-dependencies") version "1.13.1"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // Put the convention plugin from 'build-logic' on the build classpath so
    // that the javaModules block below can apply it to the module projects.
    id("jfx.java-module") apply false
}

rootProject.name = "jfx"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Every directory below 'modules/' that contains a module-info.java becomes a
// subproject named after the directory. Dependencies between the modules are
// derived from the 'requires' directives in the module-info.java files, so the
// build scripts contain no dependency declarations for them.
javaModules {
    directory("modules") {
        group = "org.openjfx"
        plugin("jfx.java-module")
        // javafx.swt is not a JPMS module and needs an external SWT jar.
        exclusions.add("javafx\\.swt")
    }
}
