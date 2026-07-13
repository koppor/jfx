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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.testing.base.TestingExtension;

/**
 * Convention plugin for the JavaFX modules (and the non-modular javafx.swt).
 *
 * <p>Dependencies between the modules are not declared in build scripts: they
 * are derived from the 'requires' directives in each module-info.java by the
 * gradlex java-module-dependencies plugin. build.properties remains the single
 * source of truth for version numbers; it is also read by CI and the release
 * tooling.
 */
public abstract class JfxJavaModulePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var plugins = project.getPluginManager();
        plugins.apply("java-library");
        plugins.apply("org.gradlex.reproducible-builds");

        // Only JPMS modules get the module-system plugins; javafx.swt has no
        // module-info.java and is built as a plain library.
        boolean isJpmsModule = project.file("src/main/java/module-info.java").exists();
        if (isJpmsModule) {
            plugins.apply("org.gradlex.java-module-dependencies");
            plugins.apply("org.gradlex.java-module-testing");
        }

        Properties buildProperties = JfxBuildExtension.load(project.getRootDir().toPath());
        project.getExtensions().create("jfxBuild", JfxBuildExtension.class, buildProperties);
        project.setVersion(releaseVersion(buildProperties) + "-internal");

        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(
                JavaLanguageVersion.of(intProperty(buildProperties, "jfx.build.jdk.version.min")));

        int targetRelease = intProperty(buildProperties, "jfx.jdk.target.version");
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().getRelease().set(targetRelease);
            task.getOptions().setEncoding("UTF-8");
        });

        if (isJpmsModule) {
            project.getTasks().named("compileJava", JavaCompile.class, task ->
                    task.getOptions().getJavaModuleVersion().set(
                            project.provider(() -> project.getVersion().toString())));
        }

        var testing = project.getExtensions().getByType(TestingExtension.class);
        testing.getSuites().named("test", JvmTestSuite.class, suite ->
                suite.useJUnitJupiter(stringProperty(buildProperties, "junit.jupiter.version")));

        // Most modules have no tests wired up (yet), which Gradle 9.6 would
        // flag as a misconfiguration.
        project.getTasks().withType(Test.class).configureEach(task -> {
            task.getFailOnNoDiscoveredTests().set(false);
            task.systemProperty("junit.jupiter.execution.timeout.test.method.default", "120s");
            task.systemProperty("junit.jupiter.execution.timeout.lifecycle.method.default", "20s");
            task.systemProperty("javafx.enablePreview", "true");
            task.systemProperty("javafx.suppressPreviewWarning", "true");
            task.systemProperty("unstable.test", "false");
            task.systemProperty("test.screenshot", "false");
            // tests run in the unnamed module (classpath); silence JEP 472
            task.jvmArgs("--enable-native-access=ALL-UNNAMED");
        });

        // Test classes and shims of this module, for the tests of downstream
        // modules (StubToolkit lives in javafx.graphics' tests, test.util in
        // javafx.base's). Mirrors the sourceSets.test.output cross-project
        // references of the legacy build.
        var testSupport = project.getConfigurations().consumable("testSupport");
        java.getSourceSets().named("test", sourceSet ->
                sourceSet.getOutput().getClassesDirs().forEach(dir ->
                        project.getArtifacts().add(testSupport.getName(), dir,
                                artifact -> artifact.builtBy(sourceSet.getOutput()))));

        // The unit-test harness of the legacy build (shims, add-exports files)
        // is ported per module: a module opts in by setting its test source
        // directories explicitly (see modules/javafx.base). Everything else
        // has no tests wired up.
        java.getSourceSets().named("test", sourceSet -> {
            sourceSet.getJava().setSrcDirs(List.of());
            sourceSet.getResources().setSrcDirs(List.of());
        });

        // API docs are built aggregated at the root ('javadoc' there); the
        // per-module task lacks the module-source-path setup (the legacy
        // build disables it the same way).
        project.getTasks().named("javadoc", task -> task.setEnabled(false));

        // shipped artifacts only; helper projects like systemTests do not publish
        String artifactId = mavenArtifactId(project.getName());
        if (artifactId != null) {
            plugins.apply("maven-publish");
            java.withSourcesJar();
            var publishing = project.getExtensions().getByType(PublishingExtension.class);
            publishing.getPublications().register("maven", MavenPublication.class, publication -> {
                publication.setArtifactId(artifactId);
                publication.from(project.getComponents().getByName("java"));
            });
        }

        if (isJpmsModule) {
            var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
            project.getTasks().register("jmod", JmodTask.class, task -> {
                task.setGroup("build");
                task.setDescription("Packages the module as a jmod.");
                task.getJarFile().set(
                        project.getTasks().named("jar", Jar.class).flatMap(Jar::getArchiveFile));
                task.getModuleVersion().set(project.provider(() -> project.getVersion().toString()));
                task.getLauncher().set(toolchains.launcherFor(java.getToolchain()));
                task.getOutputFile().set(
                        project.getLayout().getBuildDirectory().file("jmods/" + project.getName() + ".jmod"));
            });
        }
    }

    /**
     * "27.0.0.0" collapses to "27". Builds outside the release pipeline are
     * always "-internal" builds, matching the behavior of the legacy build.
     */
    private static String releaseVersion(Properties buildProperties) {
        var parts = new ArrayList<String>();
        for (String part : new String[] {"major", "minor", "security", "patch"}) {
            parts.add(stringProperty(buildProperties, "jfx.release." + part + ".version"));
        }
        while (parts.size() > 1 && parts.get(parts.size() - 1).equals("0")) {
            parts.remove(parts.size() - 1);
        }
        return String.join(".", parts);
    }

    /** javafx.base -&gt; javafx-base, jfx.incubator.input -&gt; javafx-incubator-input, jdk.jsobject -&gt; javafx-jsobject. */
    private static String mavenArtifactId(String projectName) {
        if (projectName.startsWith("javafx.")) {
            return "javafx-" + projectName.substring("javafx.".length());
        }
        if (projectName.startsWith("jfx.incubator.")) {
            return "javafx-incubator-" + projectName.substring("jfx.incubator.".length());
        }
        if (projectName.equals("jdk.jsobject")) {
            return "javafx-jsobject";
        }
        return null;
    }

    private static String stringProperty(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null) {
            throw new IllegalStateException("missing '" + name + "' in build.properties");
        }
        return value.trim();
    }

    private static int intProperty(Properties properties, String name) {
        return Integer.parseInt(stringProperty(properties, name));
    }
}
