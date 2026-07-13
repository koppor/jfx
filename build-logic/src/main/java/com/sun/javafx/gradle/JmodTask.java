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

package com.sun.javafx.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

/** Packages a module jar (and its native libraries, if any) as a jmod. */
public abstract class JmodTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getJarFile();

    /** Directory with the module's native libraries; omitted when there are none. */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract DirectoryProperty getNativeLibsDir();

    @Input
    public abstract Property<String> getModuleVersion();

    /** Used to locate bin/jmod of the toolchain. */
    @Nested
    public abstract Property<JavaLauncher> getLauncher();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void createJmod() {
        var output = getOutputFile().get().getAsFile();
        try {
            Files.deleteIfExists(output.toPath()); // jmod refuses to overwrite
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete " + output, e);
        }

        var jmod = getLauncher().get().getMetadata().getInstallationPath().file("bin/jmod").getAsFile();
        var args = new ArrayList<String>();
        args.add("create");
        args.add("--class-path");
        args.add(getJarFile().get().getAsFile().getPath());
        args.add("--module-version");
        args.add(getModuleVersion().get());
        if (getNativeLibsDir().isPresent()) {
            args.add("--libs");
            args.add(getNativeLibsDir().get().getAsFile().getPath());
        }
        args.add(output.getPath());

        getExecOperations().exec(spec -> {
            spec.setExecutable(jmod);
            spec.setArgs(args);
        });
    }
}
