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

package test.com.sun.javafx.font;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.javafx.font.CompositeFontResource;
import com.sun.javafx.font.FontResource;
import com.sun.javafx.font.FontStrike;
import com.sun.javafx.font.PGFont;
import com.sun.javafx.font.PrismFontFactory;
import com.sun.javafx.geom.transform.BaseTransform;
import org.junit.jupiter.api.Test;

/**
 * The logical "System" family must resolve to distinct regular, bold,
 * italic and bold italic faces of the platform UI font.
 */
public class SystemFontStylesTest {

    private static final float SIZE = 24f;
    private static final String SAMPLE = "The quick brown fox jumps over the lazy dog";

    private static PGFont font(boolean bold, boolean italic) {
        PGFont font = PrismFontFactory.getFontFactory().createFont("System", bold, italic, SIZE);
        FontResource fr = font.getFontResource();
        FontResource face = fr instanceof CompositeFontResource cfr ? cfr.getSlotResource(0) : fr;
        System.err.println("System bold=" + bold + " italic=" + italic
            + " -> " + face.getFullName() + " (" + face.getFileName() + ")"
            + " bold=" + face.isBold() + " italic=" + face.isItalic());
        return font;
    }

    private static FontStrike strike(PGFont font) {
        return font.getStrike(BaseTransform.IDENTITY_TRANSFORM, FontResource.AA_GREYSCALE);
    }

    private static float width(PGFont font) {
        FontStrike strike = strike(font);
        float width = 0;
        for (char ch : SAMPLE.toCharArray()) {
            width += strike.getGlyph(ch).getPixelXAdvance();
        }
        return width;
    }

    /* 'l' is a bare stem, which slanting makes wider. */
    private static float stemWidth(PGFont font) {
        return strike(font).getGlyph('l').getBBox().getWidth();
    }

    @Test
    public void stylesResolveToDistinctFaces() {
        FontResource regular = font(false, false).getFontResource();
        FontResource bold = font(true, false).getFontResource();
        FontResource italic = font(false, true).getFontResource();
        FontResource boldItalic = font(true, true).getFontResource();

        assertFalse(regular.isBold());
        assertFalse(regular.isItalic());
        assertTrue(bold.isBold());
        assertFalse(bold.isItalic());
        assertFalse(italic.isBold());
        assertTrue(italic.isItalic());
        assertTrue(boldItalic.isBold());
        assertTrue(boldItalic.isItalic());
    }

    @Test
    public void boldIsWiderThanRegular() {
        float regular = width(font(false, false));
        float bold = width(font(true, false));
        assertTrue(bold > regular, "bold " + bold + " vs regular " + regular);

        float italic = width(font(false, true));
        float boldItalic = width(font(true, true));
        assertTrue(boldItalic > italic, "bold italic " + boldItalic + " vs italic " + italic);
    }

    @Test
    public void italicIsSlanted() {
        float regular = stemWidth(font(false, false));
        float italic = stemWidth(font(false, true));
        assertTrue(italic > regular, "italic " + italic + " vs regular " + regular);

        float bold = stemWidth(font(true, false));
        float boldItalic = stemWidth(font(true, true));
        assertTrue(boldItalic > bold, "bold italic " + boldItalic + " vs bold " + bold);
    }
}
