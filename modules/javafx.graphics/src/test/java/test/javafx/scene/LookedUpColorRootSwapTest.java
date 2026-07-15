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

package test.javafx.scene;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.javafx.css.StyleManager;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Black-box reproduction of JDK-8268657 via public API only (no reflection / shims):
 * swapping the scene root mid-pulse (inside transitionToState) leaves the new root,
 * which now owns the .root rule that defines the looked-up color, without a styleMap,
 * so the lookup cannot resolve. Note the order: the old root is attached to the new
 * root BEFORE setRoot, which marks the new root DIRTY_BRANCH and makes reapplyCSS()
 * defer it for the rest of the in-flight pass.
 */
public class LookedUpColorRootSwapTest {

    private static String toBase64(String css) {
        return "data:text/plain;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testLookupResolvesAfterMidPulseTransitionToStateRootSwap() {
        var errors = StyleManager.errorsProperty();
        errors.clear();

        String theme = toBase64("""
            .root {
                -jr-fg: #0000ff;
                -fx-background-color: -jr-fg;
            }
            .leaf {
                -fx-background-color: -jr-fg;
            }
            """);

        StackPane oldRoot = new StackPane();

        StackPane leaf = new StackPane();
        leaf.getStyleClass().add("leaf");
        oldRoot.getChildren().add(leaf);

        Scene scene = new Scene(oldRoot);
        scene.getStylesheets().add(theme);

        // When CSS applies -fx-background-color to oldRoot, we are in the transitionToState
        // phase (mid-pulse). In this phase, we change the root to reproduce the bug.
        AtomicBoolean swapped = new AtomicBoolean(false);
        oldRoot.backgroundProperty().addListener((obs, ov, nv) -> {
            if (!swapped.getAndSet(true)) {
                scene.setRoot(new StackPane(oldRoot));
            }
        });

        oldRoot.applyCss();

        assertEquals(0, errors.size(), errors::toString);
    }
}
