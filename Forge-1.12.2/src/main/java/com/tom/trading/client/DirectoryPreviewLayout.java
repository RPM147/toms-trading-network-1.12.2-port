package com.tom.trading.client;

/** Pure layout shared by drawing and hit testing; four-digit quantities fit each cell. */
public final class DirectoryPreviewLayout {
    public final int columns, previewWidth, rowHeight;
    public DirectoryPreviewLayout(int rowWidth) {
        columns = rowWidth >= 440 ? 4 : 2;
        previewWidth = columns * 56 + 18;
        rowHeight = 60;
    }
    public int iconX(int start, boolean sale, int index) {
        return start + (sale ? columns * 28 + 18 : 0) + index % columns * 28 + 6;
    }
    public int iconY(int rowY, int index) { return rowY + 15 + index / columns * 22; }
    public int arrowX(int start) { return start + columns * 28 + 2; }
    public boolean contains(int iconX, int iconY, int mouseX, int mouseY) {
        return mouseX >= iconX - 5 && mouseX < iconX + 22 && mouseY >= iconY && mouseY < iconY + 18;
    }
}
