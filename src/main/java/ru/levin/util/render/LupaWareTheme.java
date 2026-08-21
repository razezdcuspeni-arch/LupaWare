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

    public static final int INK = ColorUtil.rgba(3, 5, 8, 255);
    public static final int SURFACE = ColorUtil.rgba(10, 12, 16, 226);
    public static final int SURFACE_RAISED = ColorUtil.rgba(18, 21, 27, 218);
    public static final int SURFACE_SOFT = ColorUtil.rgba(28, 32, 40, 190);
    public static final int BORDER = ColorUtil.rgba(78, 86, 100, 180);
    public static final int BORDER_SOFT = ColorUtil.rgba(96, 104, 120, 82);
    public static final int GOLD = ColorUtil.rgba(0, 190, 232, 255);
    public static final int MINT = ColorUtil.rgba(0, 210, 239, 255);
    public static final int VIOLET = ColorUtil.rgba(128, 155, 255, 255);
    public static final int WHITE = ColorUtil.rgba(238, 242, 248, 255);
    public static final int MUTED = ColorUtil.rgba(172, 180, 194, 255);
    public static final int DIM = ColorUtil.rgba(104, 112, 128, 255);

    public static int withAlpha(int color, int alpha) {
        return ColorUtil.reAlphaInt(color, Math.max(0, Math.min(255, alpha)));
    }
}

