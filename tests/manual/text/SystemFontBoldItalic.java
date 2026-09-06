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

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * Shows the logical "System" font family in its four styles, once set
 * through the Font API and once through CSS. Every row must look different:
 * the bold rows heavier, the italic rows slanted, and the text of a bold row
 * wider than that of the regular row above it.
 *
 * Run with -Dprism.debugfonts=true to see which native faces were chosen.
 */
public class SystemFontBoldItalic extends Application {

    private static final String SAMPLE = "The quick brown fox jumps over the lazy dog";

    @Override
    public void start(Stage stage) {
        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 20;");
        grid.add(new Text("Font API"), 1, 0);
        grid.add(new Text("CSS"), 2, 0);

        int row = 1;
        for (FontWeight weight : new FontWeight[] {FontWeight.NORMAL, FontWeight.BOLD}) {
            for (FontPosture posture : new FontPosture[] {FontPosture.REGULAR, FontPosture.ITALIC}) {
                Font font = Font.font("System", weight, posture, 24);
                Text text = new Text(SAMPLE);
                text.setFont(font);
                Label label = new Label(SAMPLE);
                label.setStyle("-fx-font-size: 24;"
                    + " -fx-font-weight: " + weight.name().toLowerCase() + ";"
                    + " -fx-font-style: " + (posture == FontPosture.ITALIC ? "italic" : "normal") + ";");
                grid.add(new Text(font.getName()), 0, row);
                grid.add(text, 1, row);
                grid.add(label, 2, row);
                System.out.println(font + " width=" + text.getLayoutBounds().getWidth());
                row++;
            }
        }

        stage.setScene(new Scene(grid));
        stage.setTitle("System font styles");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
