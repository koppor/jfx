/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package javafx.scene;

import java.lang.ref.WeakReference;

/**
 * Test access to {@link CssStyleHelper} internals.
 *
 * Used by the JDK-8268657 regression test to deterministically reproduce the
 * stale {@code firstStyleableAncestor} weak reference that, on the affected code,
 * caused a looked-up color reference to be dropped (so the raw token string
 * reached the Paint converter and threw a ClassCastException).
 */
public class CssStyleHelperShim {

    /**
     * Returns the node's cached first styleable ancestor (the dereferenced weak
     * reference), or {@code null} if the node has no style helper or the
     * reference is empty.
     */
    public static Node getCachedFirstStyleableAncestor(Node node) {
        if (node.styleHelper == null) {
            return null;
        }
        return node.styleHelper.firstStyleableAncestor.get();
    }

    /**
     * Forces the node's cached {@code firstStyleableAncestor} weak reference to be
     * empty, simulating the transient state observed during a stylesheet swap
     * (clear()+add()) on Linux/Wayland where a descendant's style helper was built
     * while its ancestor temporarily had no style helper. This is a no-op if the
     * node has no style helper.
     */
    public static void clearCachedFirstStyleableAncestor(Node node) {
        if (node.styleHelper != null) {
            node.styleHelper.firstStyleableAncestor = new WeakReference<>(null);
        }
    }
}
