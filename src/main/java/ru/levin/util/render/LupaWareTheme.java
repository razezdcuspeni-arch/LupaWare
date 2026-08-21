package ru.levin.util.render;

import ru.levin.util.color.ColorUtil;

/**
 * Shared visual language for the LupaWare client UI.
 *
 * <p>The palette intentionally combines the olive/graphite glass panels from
 * the HUD reference with the clear hierarchy of the existing LupaWare screens.
 * Keeping the values here prevents ClickGUI, HUD and the main menu from drifting
 * apart when the interface is refined later.</p>
 */
public final class LupaWareTheme {
    private LupaWareTheme() {
    }

    public static final int INK = ColorUtil.rgba(8, 15, 18, 255);
    public static final int SURFACE = ColorUtil.rgba(14, 27, 29, 242);
    public static final int SURFACE_RAISED = ColorUtil.rgba(23, 42, 43, 238);
    public static final int SURFACE_SOFT = ColorUtil.rgba(31, 53, 50, 214);
    public static final int BORDER = ColorUtil.rgba(103, 132, 122, 205);
    public static final int BORDER_SOFT = ColorUtil.rgba(72, 99, 94, 90);
    public static final int GOLD = ColorUtil.rgba(232, 199, 100, 255);
    public static final int MINT = ColorUtil.rgba(106, 226, 184, 255);
    public static final int VIOLET = ColorUtil.rgba(169, 143, 255, 255);
    public static final int WHITE = ColorUtil.rgba(246, 249, 245, 255);
    public static final int MUTED = ColorUtil.rgba(205, 216, 209, 255);
    public static final int DIM = ColorUtil.rgba(145, 166, 157, 255);

    public static int withAlpha(int color, int alpha) {
        return ColorUtil.reAlphaInt(color, Math.max(0, Math.min(255, alpha)));
    }
}

