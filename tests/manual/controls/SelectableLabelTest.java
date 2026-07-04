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

import java.util.List;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/**
 * Manual test for the selectable text support of Labeled (JDK-8091997).
 *
 * Things to verify:
 *  - press-drag over a label's text selects a range (highlight + inverted text fill)
 *  - double-click selects a word, triple-click selects all displayed text
 *  - Shortcut+C (or the COPY key) copies the selection, Shortcut+A selects all
 *  - the selection is cleared when the label loses focus or its text changes
 *  - selection operates on the displayed text (after truncation / mnemonic parsing)
 *  - the cursor is a text cursor over the text, and controls without the
 *    feature enabled behave as before
 */
public class SelectableLabelTest extends Application {

    public static void main(String[] args) {
        Application.launch(SelectableLabelTest.class, args);
    }

    @Override
    public void start(Stage stage) {
        Label instructions = new Label("""
                Drag over the text of the controls below to select it. \
                Double-click selects a word, triple-click selects everything. \
                Ctrl/Cmd+C copies the selection, Ctrl/Cmd+A selects all. \
                Clicking elsewhere (focus loss) clears the selection.""");
        instructions.setWrapText(true);

        Label plain = new Label("Plain selectable label — select me and copy me");
        plain.setTextSelectable(true);

        Label truncated = new Label("Truncated selectable label — selection works on the displayed "
                + "(ellipsized) text: the quick brown fox jumps over the lazy dog, "
                + "0123456789 0123456789 0123456789 0123456789 0123456789");
        truncated.setTextSelectable(true);

        Label wrapped = new Label("Wrapped selectable label — "
                + "the quick brown fox jumps over the lazy dog. ".repeat(6));
        wrapped.setWrapText(true);
        wrapped.setTextSelectable(true);

        Label mnemonic = new Label("_Mnemonic label — selection applies to the parsed text");
        mnemonic.setMnemonicParsing(true);
        mnemonic.setTextSelectable(true);

        Label withGraphic = new Label("Label with a graphic — only the text is selectable",
                new Rectangle(16, 16, Color.STEELBLUE));
        withGraphic.setTextSelectable(true);

        Label styled = new Label("This label is enabled via CSS: -fx-text-selectable: true");
        styled.setStyle("-fx-text-selectable: true;");

        Label notSelectable = new Label("Regular label for comparison — not selectable");

        Button button = new Button("Selectable Button text (still fires its action)");
        button.setTextSelectable(true);
        button.setOnAction(e -> System.out.println("Button fired"));

        TextField reference = new TextField("TextField for comparing look & feel of the selection");
        reference.setPrefColumnCount(30);

        TableView<List<String>> table = createTable();

        List<Labeled> toggleTargets = List.of(plain, truncated, wrapped, mnemonic, withGraphic, button);
        CheckBox toggle = new CheckBox("textSelectable enabled on the controls above");
        toggle.setSelected(true);
        toggle.setOnAction(e -> toggleTargets.forEach(l -> l.setTextSelectable(toggle.isSelected())));

        VBox content = new VBox(12,
                instructions,
                new Separator(),
                plain,
                truncated,
                wrapped,
                mnemonic,
                withGraphic,
                styled,
                notSelectable,
                button,
                toggle,
                reference,
                new Label("TableView with '.table-cell { -fx-text-selectable: true; }':"),
                table);
        content.setStyle("-fx-padding: 12;");

        Scene scene = new Scene(new ScrollPane(content), 720, 640);
        // Enables selection in all table cells via the new CSS property.
        scene.getStylesheets().add("data:text/css,.table-cell { -fx-text-selectable: true; }");
        stage.setScene(scene);
        stage.setTitle("SelectableLabelTest (JDK-8091997)");
        stage.show();
    }

    private TableView<List<String>> createTable() {
        TableView<List<String>> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(
                List.of("Amsterdam", "861,000", "219.3 km²"),
                List.of("Berlin", "3,878,000", "891.7 km²"),
                List.of("Copenhagen", "660,000", "86.4 km²")));
        String[] names = {"City", "Population", "Area"};
        for (int i = 0; i < names.length; i++) {
            final int col = i;
            TableColumn<List<String>, String> column = new TableColumn<>(names[i]);
            column.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().get(col)));
            column.setPrefWidth(150);
            table.getColumns().add(column);
        }
        table.setPrefHeight(160);
        return table;
    }
}
