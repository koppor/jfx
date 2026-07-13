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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

/**
 * Runs one of the in-tree JSL shader compilers (src/main/jsl-decora,
 * src/main/jsl-prism of javafx.graphics) to generate Java shader peers and
 * GLSL (.frag) shaders. Only the ES2 backend is generated: HLSL/MSL output
 * feeds the native pipelines, which are outside the scope of this build.
 *
 * <p>The compilers run on a plain classpath (unnamed module), so none of the
 * --add-exports arguments of the legacy build are needed.
 */
public abstract class JslCompileTask extends DefaultTask {

    /** Everything needed to run the generator: compiler classes, JSLC, main classes, templates. */
    @Classpath
    public abstract ConfigurableFileCollection getCompilerClasspath();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDir();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Input
    public abstract Property<String> getCompilerMainClass();

    /** Package path argument, e.g. "com/sun/scenario/effect". */
    @Input
    public abstract Property<String> getPackagePath();

    /** Trailing arguments for a single run, e.g. ["-all", "GenAllDecoraShaders"]. */
    @Input
    public abstract ListProperty<String> getModeArgs();

    /** When set, the compiler is invoked once per *.jsl file with "-name &lt;file&gt;". */
    @Input
    public abstract Property<Boolean> getPerSourceFile();

    /** The generated classes target the toolchain, not the JVM running Gradle. */
    @Nested
    public abstract Property<JavaLauncher> getLauncher();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    public JslCompileTask() {
        getPerSourceFile().convention(false);
    }

    @TaskAction
    public void generate() {
        File sourceDir = getSourceDir().get().getAsFile();
        File outputDir = getOutputDir().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(outputDir));

        List<String> baseArgs = List.of(
                "-i", sourceDir.getPath(),
                "-o", outputDir.getPath(),
                "-t",
                "-pkg", getPackagePath().get(),
                "-es2");

        if (getPerSourceFile().get()) {
            for (File jsl : jslFiles(sourceDir)) {
                var args = new ArrayList<>(baseArgs);
                args.add("-name");
                args.add(jsl.getPath());
                run(args);
            }
        } else {
            var args = new ArrayList<>(baseArgs);
            args.addAll(getModeArgs().get());
            run(args);
        }
    }

    private List<File> jslFiles(File sourceDir) {
        try (var stream = Files.walk(sourceDir.toPath())) {
            return stream
                    .filter(path -> path.toString().endsWith(".jsl"))
                    .map(java.nio.file.Path::toFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list *.jsl in " + sourceDir, e);
        }
    }

    private void run(List<String> args) {
        getExecOperations().javaexec(spec -> {
            spec.setExecutable(getLauncher().get().getExecutablePath().getAsFile());
            spec.getMainClass().set(getCompilerMainClass().get());
            spec.setClasspath(getCompilerClasspath());
            spec.setArgs(args);
        });
    }
}
