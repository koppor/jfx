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
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.inject.Inject;

/**
 * Read-only view on build.properties, the single source of truth for version
 * numbers (also read by CI and the release tooling). Registered as 'jfxBuild'
 * on every project using the 'jfx.java-module' convention plugin.
 */
public abstract class JfxBuildExtension {

    private final Properties properties;

    @Inject
    public JfxBuildExtension(Properties properties) {
        this.properties = properties;
    }

    static Properties load(Path rootDir) {
        var file = rootDir.resolve("build.properties");
        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        return properties;
    }

    public String property(String name) {
        String value = properties.getProperty(name);
        if (value == null) {
            throw new IllegalStateException("missing '" + name + "' in build.properties");
        }
        return value.trim();
    }

    public int intProperty(String name) {
        return Integer.parseInt(property(name));
    }
}
