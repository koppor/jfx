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
package test.com.sun.glass.ui.win;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.glass.ui.win.WinTextRangeProviderShim;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class WinTextRangeProviderTest {

    static Stream<Arguments> getEndIndex() {
        return Stream.of(
                Arguments.of(1, 0, 1, 2),
                Arguments.of(1, 0, 2, 1),
                Arguments.of(1, -1, 2, -1),
                Arguments.of(1, 0, Integer.MAX_VALUE, 1),
                Arguments.of(1, 0, Integer.MAX_VALUE, Integer.MAX_VALUE),
                Arguments.of(55, 50, Integer.MAX_VALUE, 50),
                Arguments.of(50, 50, -1, 55),
                Arguments.of(55, 50, 10, 55),
                Arguments.of(60, 50, 10, Integer.MAX_VALUE),
                Arguments.of(60, 50, 10, Integer.MIN_VALUE),
                Arguments.of(50, 50, Integer.MIN_VALUE, Integer.MIN_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void getEndIndex(Integer expected, Integer startIndex, Integer length, Integer maxEndIndex) {
        assertEquals(expected, WinTextRangeProviderShim.getEndIndex(startIndex, length, maxEndIndex));
    }
}
