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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/**
 * Compiles and links one native library the way the legacy build does on
 * Linux: every source file (C and C++ alike) is compiled with gcc, the
 * shared library is linked with g++ (which, together with -static-libgcc
 * -static-libstdc++ in the link flags, settles the runtime libraries).
 */
public abstract class NativeLibTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    /** Added as -I; tracked by content so header changes trigger a rebuild. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getIncludeDirs();

    @Input
    public abstract ListProperty<String> getCcFlags();

    @Input
    public abstract ListProperty<String> getLinkFlags();

    @Input
    public abstract Property<String> getCompiler();

    @Input
    public abstract Property<String> getLinker();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    public NativeLibTask() {
        getCompiler().convention("gcc");
        getLinker().convention("g++");
    }

    @TaskAction
    public void compileAndLink() {
        File objDir = getTemporaryDir();
        getFileSystemOperations().delete(spec -> spec.delete(objDir));
        objDir.mkdirs();

        var includes = new ArrayList<String>();
        for (File dir : getIncludeDirs()) {
            if (dir.isDirectory()) {
                includes.add("-I" + dir.getPath());
            }
        }

        Set<File> objects = new TreeSet<>();
        for (File source : getSources().getFiles().stream().sorted().toList()) {
            File object = new File(objDir, stripExtension(source.getName()) + ".obj");
            if (!objects.add(object)) {
                throw new IllegalStateException("duplicate object file name for " + source);
            }
            var args = new ArrayList<String>(getCcFlags().get());
            args.addAll(includes);
            args.add("-o");
            args.add(object.getPath());
            args.add(source.getPath());
            getExecOperations().exec(spec -> {
                spec.setExecutable(getCompiler().get());
                spec.setArgs(args);
            });
        }

        File library = getOutputFile().get().getAsFile();
        library.getParentFile().mkdirs();
        var linkArgs = new ArrayList<String>();
        objects.forEach(object -> linkArgs.add(object.getPath()));
        linkArgs.add("-o");
        linkArgs.add(library.getPath());
        linkArgs.addAll(getLinkFlags().get());
        getExecOperations().exec(spec -> {
            spec.setExecutable(getLinker().get());
            spec.setArgs(linkArgs);
        });
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
