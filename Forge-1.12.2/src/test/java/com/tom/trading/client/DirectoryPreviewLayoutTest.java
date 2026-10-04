package com.tom.trading.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class DirectoryPreviewLayoutTest {
    @Test public void allEightIconsAndFourDigitCountCellsFitWithoutOverlapAtBothWidths() {
        for (int width : new int[]{272, 439, 440, 676}) {
            DirectoryPreviewLayout layout = new DirectoryPreviewLayout(width);
            assertTrue(layout.previewWidth < width);
            for (boolean sale : new boolean[]{false, true}) for (int i = 0; i < 4; i++) {
                int x = layout.iconX(0, sale, i), y = layout.iconY(0, i);
                assertTrue(x - 5 >= 0); assertTrue(x + 22 <= layout.previewWidth);
                assertTrue(y >= 15); assertTrue(y + 18 <= layout.rowHeight);
                assertTrue(layout.contains(x, y, x, y)); assertFalse(layout.contains(x, y, x + 22, y));
                for (int j = i + 1; j < 4; j++)
                    assertFalse(layout.contains(x, y, layout.iconX(0, sale, j), layout.iconY(0, j)));
            }
            assertTrue(layout.iconX(0, true, 0) - 5 > layout.arrowX(0) + 12);
        }
    }
}
