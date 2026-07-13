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

// javafx.swt is not a JPMS module: it is compiled on the classpath (which is
// also what gives FXCanvas access to the internal graphics API that is only
// exported to it) and shipped as the plain javafx-swt.jar.

plugins {
    id("jfx.java-module")
}

base {
    archivesName = "javafx-swt"
}

// SWT for the platform the build runs on, from Maven Central. The legacy
// build downloads the same artifact from the Eclipse update site; the Maven
// version is the update-site version without the ".v..." qualifier.
val swtVersion = jfxBuild.property("swt.version").substringBefore(".v")
val os = System.getProperty("os.name").lowercase()
val swtWindowingSystem = when {
    os.contains("linux") -> "gtk.linux"
    os.contains("mac") -> "cocoa.macosx"
    else -> "win32.win32"
}
val swtArch = if (System.getProperty("os.arch") == "aarch64") "aarch64" else "x86_64"

dependencies {
    implementation(project(":javafx.graphics"))
    // compileOnly: FXCanvas users bring their own SWT; the SDK does not ship
    // it. isTransitive=false dodges the unresolvable ${osgi.platform}
    // placeholder in the SWT POMs.
    compileOnly("org.eclipse.platform:org.eclipse.swt.$swtWindowingSystem.$swtArch:$swtVersion") {
        isTransitive = false
    }
}
