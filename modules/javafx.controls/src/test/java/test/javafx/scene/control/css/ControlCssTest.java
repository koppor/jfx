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

package test.javafx.scene.control.css;

import com.sun.javafx.tk.Toolkit;
import javafx.css.CssParser;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for JDK-8268657 using only stock controls and Modena, no custom CSS.
 * Creating the TabPane skin during the CSS pass attaches the tab content to the scene;
 * a sceneProperty listener on that content then swaps the scene root mid-pulse (setRoot
 * first, then re-parent the old root under the new one). The demoted old root keeps its
 * style helper whose cached firstStyleableAncestor is frozen at null (it was the root
 * when the helper was built), so Modena looked-up colors defined on .root (e.g.
 * -fx-control-inner-background) fail to resolve for the control subtree: the raw String
 * token reaches the Paint converter and the value is dropped with a ClassCastException.
 */
public class ControlCssTest {

    @Test
    void testLookupResolvesAfterSceneListenerRootSwap() {
        var errors = CssParser.errorsProperty();
        errors.clear();

        StackPane testEditor = new StackPane();
        TabPane tabPane = new TabPane();

        TextField textField = new TextField();
        tabPane.getTabs().add(new Tab("Test", new VBox(textField, new Label("Test"))));
        testEditor.getChildren().setAll(tabPane);

        AtomicBoolean swapped = new AtomicBoolean(false);
        textField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && !swapped.getAndSet(true)) {
                Parent oldRoot = newScene.getRoot();
                StackPane newRoot = new StackPane();
                newScene.setRoot(newRoot);
                newRoot.getChildren().setAll(oldRoot);
            }
        });

        Button btn = new Button("Add Editor");
        VBox root = new VBox(btn);
        btn.setOnAction(e -> root.getChildren().add(testEditor));

        Scene scene = new Scene(root, 800, 600);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();

        btn.fire();

        Toolkit.getToolkit().firePulse();

        stage.hide();

        assertEquals(0, errors.size(), errors::toString);
    }

}
