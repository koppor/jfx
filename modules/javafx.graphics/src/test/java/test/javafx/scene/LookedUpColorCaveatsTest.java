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
import javafx.scene.SubScene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Probes for the JDK-8268657 trigger paths beyond the plain scene root swap: which
 * mid-pulse mutations actually reproduce the dropped looked-up color (CCE String ->
 * Paint)? Each test asserts that no CSS error is recorded. The SubScene root swap
 * reproduces the bug; the stylesheet-swap and mid-tree-insertion probes do not
 * reproduce on their own and pin that behavior down.
 */
public class LookedUpColorCaveatsTest {

    private static String toBase64(String css) {
        return "data:text/plain;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    /** Caveat A: stylesheet clear()+add() mid-pulse, NO scene change (the original report's trigger). */
    @Test
    void caveatA_midPulseStylesheetSwap() {
        var errors = StyleManager.errorsProperty();
        errors.clear();

        String theme1 = toBase64("""
            .root { -jr-fg: #0000ff; -fx-background-color: -jr-fg; }
            .leaf { -fx-background-color: -jr-fg; }
            """);
        String theme2 = toBase64("""
            .root { -jr-fg: #ff0000; -fx-background-color: -jr-fg; }
            .leaf { -fx-background-color: -jr-fg; }
            """);

        StackPane root = new StackPane();
        StackPane leaf = new StackPane();
        leaf.getStyleClass().add("leaf");
        root.getChildren().add(leaf);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(theme1);

        AtomicBoolean swapped = new AtomicBoolean(false);
        root.backgroundProperty().addListener((o, ov, nv) -> {
            if (!swapped.getAndSet(true)) {
                scene.getStylesheets().clear();
                scene.getStylesheets().add(theme2);
            }
        });

        root.applyCss();

        assertEquals(0, errors.size(), errors::toString);
    }

    /** Caveat A2: same, but the swap is on the root node's stylesheets (Parent.getStylesheets()). */
    @Test
    void caveatA2_midPulseParentStylesheetSwap() {
        var errors = StyleManager.errorsProperty();
        errors.clear();

        String theme1 = toBase64("""
            .root { -jr-fg: #0000ff; -fx-background-color: -jr-fg; }
            .leaf { -fx-background-color: -jr-fg; }
            """);
        String theme2 = toBase64("""
            .root { -jr-fg: #ff0000; -fx-background-color: -jr-fg; }
            .leaf { -fx-background-color: -jr-fg; }
            """);

        StackPane root = new StackPane();
        StackPane leaf = new StackPane();
        leaf.getStyleClass().add("leaf");
        root.getChildren().add(leaf);

        Scene scene = new Scene(root);
        root.getStylesheets().add(theme1);

        AtomicBoolean swapped = new AtomicBoolean(false);
        root.backgroundProperty().addListener((o, ov, nv) -> {
            if (!swapped.getAndSet(true)) {
                root.getStylesheets().clear();
                root.getStylesheets().add(theme2);
            }
        });

        root.applyCss();

        assertEquals(0, errors.size(), errors::toString);
    }

    /** Caveat B: a SubScene's root is swapped mid-pulse (the sub-scene analogue of the
     *  Scene root swap; reproduces the bug just like the Scene case). */
    @Test
    void caveatB_subSceneRootSwap() {
        var errors = StyleManager.errorsProperty();
        errors.clear();

        String theme = toBase64("""
            .sub-root { -jr-fg: #0000ff; -fx-background-color: -jr-fg; }
            .leaf { -fx-background-color: -jr-fg; }
            """);

        StackPane outerRoot = new StackPane();
        Scene scene = new Scene(outerRoot);
        scene.getStylesheets().add(theme);

        StackPane subOldRoot = new StackPane();
        subOldRoot.getStyleClass().add("sub-root");
        StackPane leaf = new StackPane();
        leaf.getStyleClass().add("leaf");
        subOldRoot.getChildren().add(leaf);

        SubScene subScene = new SubScene(subOldRoot, 100, 100);
        outerRoot.getChildren().add(subScene);

        AtomicBoolean swapped = new AtomicBoolean(false);
        subOldRoot.backgroundProperty().addListener((o, ov, nv) -> {
            if (!swapped.getAndSet(true)) {
                subScene.setRoot(new StackPane(subOldRoot));
            }
        });

        outerRoot.applyCss();

        assertEquals(0, errors.size(), errors::toString);
    }

    /** The dropped value must recover on the next CSS pass: this bounded the bug's
     *  impact to a single frame (one logged error, one stale frame). */
    @Test
    void selfHealsOnSecondCssPass() {
        var errors = StyleManager.errorsProperty();
        errors.clear();

        String theme = toBase64("""
            .root { -jr-fg: #0000ff; -fx-background-color: -jr-fg; }
            .leaf { -fx-background-color: -jr-fg; }
            """);

        StackPane oldRoot = new StackPane();
        StackPane leaf = new StackPane();
        leaf.getStyleClass().add("leaf");
        oldRoot.getChildren().add(leaf);

        Scene scene = new Scene(oldRoot);
        scene.getStylesheets().add(theme);

        AtomicBoolean swapped = new AtomicBoolean(false);
        oldRoot.backgroundProperty().addListener((o, ov, nv) -> {
            if (!swapped.getAndSet(true)) {
                scene.setRoot(new StackPane(oldRoot));
            }
        });

        oldRoot.applyCss();

        // Regardless of whether the first pass dropped the looked-up color, a second
        // regular pass over the (now installed) new root must resolve it cleanly.
        errors.clear();
        scene.getRoot().applyCss();

        assertEquals(0, errors.size(), errors::toString);
    }

    /** Caveat C: a NON-root mid-tree ancestor that defines the looked-up color is freshly
     *  inserted mid-pulse (it has a parent, so no root-focused handling applies to it). */
    @Test
    void caveatC_midTreeAncestorInsertedMidPulse() {
        var errors = StyleManager.errorsProperty();
        errors.clear();

        String theme = toBase64("""
            .holder { -jr-fg: #0000ff; -fx-background-color: -jr-fg; }
            .leaf { -fx-background-color: -jr-fg; }
            """);

        StackPane root = new StackPane();
        StackPane holder = new StackPane();
        holder.getStyleClass().add("holder");
        StackPane leaf = new StackPane();
        leaf.getStyleClass().add("leaf");
        holder.getChildren().add(leaf);
        root.getChildren().add(holder);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(theme);

        AtomicBoolean swapped = new AtomicBoolean(false);
        holder.backgroundProperty().addListener((o, ov, nv) -> {
            if (!swapped.getAndSet(true)) {
                // Move leaf under a brand-new (unprocessed) .holder that defines -jr-fg.
                StackPane newHolder = new StackPane();
                newHolder.getStyleClass().add("holder");
                holder.getChildren().remove(leaf);
                newHolder.getChildren().add(leaf);
                holder.getChildren().add(newHolder);
            }
        });

        root.applyCss();

        assertEquals(0, errors.size(), errors::toString);
    }
}
