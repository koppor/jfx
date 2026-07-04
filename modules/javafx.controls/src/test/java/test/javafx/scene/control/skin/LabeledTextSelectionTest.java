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

package test.javafx.scene.control.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javafx.css.CssMetaData;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.SkinBaseShim;
import javafx.scene.control.skin.LabeledSkinBase;
import javafx.scene.control.skin.LabeledSkinBaseShim;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import test.com.sun.javafx.scene.control.infrastructure.KeyEventFirer;
import test.com.sun.javafx.scene.control.infrastructure.KeyModifier;
import test.com.sun.javafx.scene.control.infrastructure.MouseEventFirer;
import test.com.sun.javafx.scene.control.infrastructure.StageLoader;

/**
 * Tests the text selection support of LabeledSkinBase, which is active when
 * the Labeled's {@code textSelectable} property is enabled (JDK-8091997).
 */
public class LabeledTextSelectionTest {

    private static final String TEXT = "The quick brown fox";

    private Label label;
    private LabeledSkinBase<?> skin;
    private Text text;
    private StageLoader stageLoader;

    @BeforeEach
    public void setup() {
        label = new Label(TEXT);
        stageLoader = new StageLoader(label);
        skin = (LabeledSkinBase<?>) label.getSkin();
        text = LabeledSkinBaseShim.get_text(skin);
    }

    @AfterEach
    public void cleanup() {
        stageLoader.dispose();
    }

    private Path getHighlightPath() {
        for (Node node : SkinBaseShim.getChildren(skin)) {
            if (node instanceof Path path && path.getStyleClass().contains("selection-highlight")) {
                return path;
            }
        }
        return null;
    }

    /**
     * MouseEventFirer aims at the center of the node's layout bounds. The
     * local origin of a Text node is its baseline, so the vertical center of
     * its layout bounds lies below the (first) line of text. These deltas
     * shift the event to the given local x coordinate, at the baseline
     * (local y = 0), which is always within the first line.
     */
    private void firePress(int clickCount, double localX) {
        MouseEventFirer firer = new MouseEventFirer(text);
        firer.fireMousePressed(clickCount,
                localX - text.getLayoutBounds().getWidth() / 2,
                -text.getLayoutBounds().getHeight() / 2);
    }

    /** Fires a mouse drag onto the text node at the given local x coordinate. */
    private void fireDrag(double localX) {
        MouseEventFirer firer = new MouseEventFirer(text);
        firer.fireMouseEvent(MouseEvent.MOUSE_DRAGGED,
                localX - text.getLayoutBounds().getWidth() / 2,
                -text.getLayoutBounds().getHeight() / 2);
    }

    /* ***********************************************************************
     * Property and CSS                                                      *
     ************************************************************************/

    @Test public void textSelectableIsDisabledByDefault() {
        assertFalse(label.isTextSelectable());
        assertNull(getHighlightPath());
        assertNull(text.getCursor());
    }

    @Test public void textSelectableCssMetaDataExists() {
        boolean found = false;
        for (CssMetaData<?, ?> metaData : Labeled.getClassCssMetaData()) {
            if ("-fx-text-selectable".equals(metaData.getProperty())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "-fx-text-selectable should be part of Labeled's CssMetaData");
    }

    @Test public void textSelectableCanBeEnabledFromCss() {
        label.setStyle("-fx-text-selectable: true;");
        label.applyCss();
        assertTrue(label.isTextSelectable());
        assertNotNull(getHighlightPath());
    }

    /* ***********************************************************************
     * Skin state                                                            *
     ************************************************************************/

    @Test public void enablingAddsHighlightPathBeneathText() {
        label.setTextSelectable(true);
        Path path = getHighlightPath();
        assertNotNull(path);
        assertFalse(path.isManaged());
        assertTrue(path.isMouseTransparent());
        int pathIndex = SkinBaseShim.getChildren(skin).indexOf(path);
        int textIndex = SkinBaseShim.getChildren(skin).indexOf(text);
        assertTrue(pathIndex < textIndex, "highlight path must be beneath the text node");
        assertEquals(Cursor.TEXT, text.getCursor());
    }

    @Test public void disablingRemovesHighlightPathAndRestoresText() {
        label.setTextSelectable(true);
        firePress(3, 1); // select all
        assertTrue(text.getSelectionStart() >= 0);

        label.setTextSelectable(false);
        assertNull(getHighlightPath());
        assertNull(text.getCursor());
        assertEquals(-1, text.getSelectionStart());
        assertEquals(-1, text.getSelectionEnd());
        assertEquals("", label.getSelectedText());
    }

    @Test public void highlightPathFollowsTextIntoAndOutOfChildrenList() {
        label.setTextSelectable(true);
        assertNotNull(getHighlightPath());

        // With GRAPHIC_ONLY (and no graphic), the text node is not a child
        // of the skin, and neither must be the highlight path.
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        assertNull(getHighlightPath());

        label.setContentDisplay(ContentDisplay.LEFT);
        assertNotNull(getHighlightPath());
    }

    @Test public void disposingSkinWithActiveSelectionDoesNotThrow() {
        label.setTextSelectable(true);
        firePress(3, 1);
        label.getSkin().dispose();
    }

    /* ***********************************************************************
     * Mouse selection                                                       *
     ************************************************************************/

    @Test public void mousePressRequestsFocus() {
        label.setTextSelectable(true);
        assertFalse(label.isFocused());
        firePress(1, 1);
        assertTrue(label.isFocused());
    }

    @Test public void mousePressDoesNotRequestFocusWhenNotSelectable() {
        firePress(1, 1);
        assertFalse(label.isFocused());
    }

    @Test public void dragSelectsRangeAndPopulatesHighlight() {
        label.setTextSelectable(true);
        firePress(1, 1);
        fireDrag(text.getLayoutBounds().getWidth() + 10);
        assertEquals(0, text.getSelectionStart());
        assertEquals(TEXT.length(), text.getSelectionEnd());
        assertFalse(getHighlightPath().getElements().isEmpty());
    }

    @Test public void doubleClickSelectsWord() {
        label.setTextSelectable(true);
        firePress(2, 1);
        assertEquals(0, text.getSelectionStart());
        assertEquals("The".length(), text.getSelectionEnd());
    }

    @Test public void tripleClickSelectsAll() {
        label.setTextSelectable(true);
        firePress(3, 1);
        assertEquals(0, text.getSelectionStart());
        assertEquals(TEXT.length(), text.getSelectionEnd());
    }

    @Test public void singleClickClearsPreviousSelection() {
        label.setTextSelectable(true);
        firePress(3, 1);
        assertEquals(0, text.getSelectionStart());
        firePress(1, 1);
        assertEquals(-1, text.getSelectionStart());
        assertEquals(-1, text.getSelectionEnd());
        assertTrue(getHighlightPath().getElements().isEmpty());
    }

    /* ***********************************************************************
     * Keyboard                                                              *
     ************************************************************************/

    @Test public void shortcutASelectsAll() {
        label.setTextSelectable(true);
        label.requestFocus();
        new KeyEventFirer(label).doKeyPress(KeyCode.A, KeyModifier.getShortcutKey());
        assertEquals(0, text.getSelectionStart());
        assertEquals(TEXT.length(), text.getSelectionEnd());
    }

    @Test public void shortcutCCopiesSelectionToClipboard() {
        label.setTextSelectable(true);
        firePress(2, 1); // selects "The"
        KeyEventFirer keyFirer = new KeyEventFirer(label);
        keyFirer.doKeyPress(KeyCode.C, KeyModifier.getShortcutKey());
        assertEquals("The", Clipboard.getSystemClipboard().getString());
    }

    @Test public void shortcutCWithoutSelectionLeavesClipboardAlone() {
        Clipboard.getSystemClipboard().clear();
        label.setTextSelectable(true);
        label.requestFocus();
        new KeyEventFirer(label).doKeyPress(KeyCode.C, KeyModifier.getShortcutKey());
        assertNull(Clipboard.getSystemClipboard().getString());
    }

    @Test public void keysAreIgnoredWhenNotSelectable() {
        label.requestFocus();
        new KeyEventFirer(label).doKeyPress(KeyCode.A, KeyModifier.getShortcutKey());
        assertEquals(-1, text.getSelectionStart());
    }

    /* ***********************************************************************
     * Selection invalidation                                                *
     ************************************************************************/

    @Test public void changingTextClearsSelection() {
        label.setTextSelectable(true);
        firePress(3, 1);
        assertEquals(0, text.getSelectionStart());

        label.setText("Something else");
        LabeledSkinBaseShim.updateDisplayedText(skin);
        assertEquals(-1, text.getSelectionStart());
        assertEquals(-1, text.getSelectionEnd());
        assertEquals("", label.getSelectedText());
        assertTrue(getHighlightPath().getElements().isEmpty());
    }

    @Test public void losingFocusClearsSelection() {
        Button other = new Button("other");
        ((Group) label.getParent()).getChildren().add(other);

        label.setTextSelectable(true);
        firePress(3, 1);
        assertTrue(label.isFocused());
        assertEquals(0, text.getSelectionStart());

        other.requestFocus();
        assertFalse(label.isFocused());
        assertEquals(-1, text.getSelectionStart());
        assertEquals(-1, text.getSelectionEnd());
        assertEquals("", label.getSelectedText());
    }

    /* ***********************************************************************
     * selectedText property                                                 *
     ************************************************************************/

    @Test public void selectedTextReflectsSelection() {
        assertEquals("", label.getSelectedText());
        label.setTextSelectable(true);

        firePress(2, 1); // selects the word "The"
        assertEquals("The", label.getSelectedText());

        fireDrag(text.getLayoutBounds().getWidth() + 10); // extends to the end
        assertEquals(TEXT, label.getSelectedText());

        firePress(1, 1); // collapses the selection
        assertEquals("", label.getSelectedText());
    }

    @Test public void selectedTextPropertyIsObservable() {
        label.setTextSelectable(true);
        String[] observed = new String[1];
        label.selectedTextProperty().addListener((o, old, value) -> observed[0] = value);
        firePress(3, 1);
        assertEquals(TEXT, observed[0]);
    }

    /* ***********************************************************************
     * Highlight colors                                                      *
     ************************************************************************/

    @Test public void defaultHighlightColorsApply() {
        label.setTextSelectable(true);
        firePress(3, 1);
        assertEquals(Color.web("#0096C9"), getHighlightPath().getFill());
        assertEquals(Color.WHITE, text.getSelectionFill());
    }

    @Test public void highlightColorsAreStyleableWithCss() {
        label.setTextSelectable(true);
        label.setStyle("-fx-highlight-fill: red; -fx-highlight-text-fill: lime;");
        label.applyCss();
        firePress(3, 1);
        assertEquals(Color.RED, getHighlightPath().getFill());
        assertEquals(Color.LIME, text.getSelectionFill());
    }

    /* ***********************************************************************
     * Accessibility                                                         *
     ************************************************************************/

    @Test public void accessibleSelectionAttributesReflectSelection() {
        label.setTextSelectable(true);
        assertNull(label.queryAccessibleAttribute(AccessibleAttribute.SELECTION_START));
        assertNull(label.queryAccessibleAttribute(AccessibleAttribute.SELECTION_END));

        firePress(2, 1); // selects the word "The"
        assertEquals(0, ((Integer) label.queryAccessibleAttribute(AccessibleAttribute.SELECTION_START)).intValue());
        assertEquals(3, ((Integer) label.queryAccessibleAttribute(AccessibleAttribute.SELECTION_END)).intValue());

        firePress(1, 1); // collapses the selection
        assertNull(label.queryAccessibleAttribute(AccessibleAttribute.SELECTION_START));
        assertNull(label.queryAccessibleAttribute(AccessibleAttribute.SELECTION_END));
    }
}
