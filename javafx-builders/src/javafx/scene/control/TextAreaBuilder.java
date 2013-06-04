/* 
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javafx.scene.control;

/**
Builder class for javafx.scene.control.TextArea
@see javafx.scene.control.TextArea
@deprecated This class is deprecated and will be removed in the next version
* @since JavaFX 2.0
*/
@javax.annotation.Generated("Generated by javafx.builder.processor.BuilderProcessor")
@Deprecated
public class TextAreaBuilder<B extends javafx.scene.control.TextAreaBuilder<B>> extends javafx.scene.control.TextInputControlBuilder<B> implements javafx.util.Builder<javafx.scene.control.TextArea> {
    protected TextAreaBuilder() {
    }
    
    /** Creates a new instance of TextAreaBuilder. */
    @SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
    public static javafx.scene.control.TextAreaBuilder<?> create() {
        return new javafx.scene.control.TextAreaBuilder();
    }
    
    private int __set;
    public void applyTo(javafx.scene.control.TextArea x) {
        super.applyTo(x);
        int set = __set;
        if ((set & (1 << 0)) != 0) x.getParagraphs().addAll(this.paragraphs);
        if ((set & (1 << 1)) != 0) x.setPrefColumnCount(this.prefColumnCount);
        if ((set & (1 << 2)) != 0) x.setPrefRowCount(this.prefRowCount);
        if ((set & (1 << 3)) != 0) x.setPromptText(this.promptText);
        if ((set & (1 << 4)) != 0) x.setScrollLeft(this.scrollLeft);
        if ((set & (1 << 5)) != 0) x.setScrollTop(this.scrollTop);
        if ((set & (1 << 6)) != 0) x.setWrapText(this.wrapText);
    }
    
    private java.util.Collection<? extends java.lang.CharSequence> paragraphs;
    /**
    Add the given items to the List of items in the {@link javafx.scene.control.TextArea#getParagraphs() paragraphs} property for the instance constructed by this builder.
    */
    @SuppressWarnings("unchecked")
    public B paragraphs(java.util.Collection<? extends java.lang.CharSequence> x) {
        this.paragraphs = x;
        __set |= 1 << 0;
        return (B) this;
    }
    
    /**
    Add the given items to the List of items in the {@link javafx.scene.control.TextArea#getParagraphs() paragraphs} property for the instance constructed by this builder.
    */
    public B paragraphs(java.lang.CharSequence... x) {
        return paragraphs(java.util.Arrays.asList(x));
    }
    
    private int prefColumnCount;
    /**
    Set the value of the {@link javafx.scene.control.TextArea#getPrefColumnCount() prefColumnCount} property for the instance constructed by this builder.
    */
    @SuppressWarnings("unchecked")
    public B prefColumnCount(int x) {
        this.prefColumnCount = x;
        __set |= 1 << 1;
        return (B) this;
    }
    
    private int prefRowCount;
    /**
    Set the value of the {@link javafx.scene.control.TextArea#getPrefRowCount() prefRowCount} property for the instance constructed by this builder.
    */
    @SuppressWarnings("unchecked")
    public B prefRowCount(int x) {
        this.prefRowCount = x;
        __set |= 1 << 2;
        return (B) this;
    }
    
    private java.lang.String promptText;
    /**
    Set the value of the {@link javafx.scene.control.TextArea#getPromptText() promptText} property for the instance constructed by this builder.
    * @since JavaFX 2.2
    */
    @SuppressWarnings("unchecked")
    public B promptText(java.lang.String x) {
        this.promptText = x;
        __set |= 1 << 3;
        return (B) this;
    }
    
    private double scrollLeft;
    /**
    Set the value of the {@link javafx.scene.control.TextArea#getScrollLeft() scrollLeft} property for the instance constructed by this builder.
    */
    @SuppressWarnings("unchecked")
    public B scrollLeft(double x) {
        this.scrollLeft = x;
        __set |= 1 << 4;
        return (B) this;
    }
    
    private double scrollTop;
    /**
    Set the value of the {@link javafx.scene.control.TextArea#getScrollTop() scrollTop} property for the instance constructed by this builder.
    */
    @SuppressWarnings("unchecked")
    public B scrollTop(double x) {
        this.scrollTop = x;
        __set |= 1 << 5;
        return (B) this;
    }
    
    private boolean wrapText;
    /**
    Set the value of the {@link javafx.scene.control.TextArea#isWrapText() wrapText} property for the instance constructed by this builder.
    */
    @SuppressWarnings("unchecked")
    public B wrapText(boolean x) {
        this.wrapText = x;
        __set |= 1 << 6;
        return (B) this;
    }
    
    /**
    Make an instance of {@link javafx.scene.control.TextArea} based on the properties set on this builder.
    */
    public javafx.scene.control.TextArea build() {
        javafx.scene.control.TextArea x = new javafx.scene.control.TextArea();
        applyTo(x);
        return x;
    }
}
