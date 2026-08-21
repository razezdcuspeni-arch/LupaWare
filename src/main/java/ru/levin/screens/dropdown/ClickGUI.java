package ru.levin.screens.dropdown;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.levin.manager.ClientManager;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.Type;
import ru.levin.modules.setting.*;
import ru.levin.screens.dropdown.impl.*;
import ru.levin.screens.dropdown.search.SearchState;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.math.MathUtil;
import ru.levin.util.render.LupaWareTheme;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.Scissor;
import ru.levin.manager.fontManager.FontUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClickGUI extends Screen implements IMinecraft {
    private static final int SHELL_WIDTH = 400;
    private static final int SHELL_HEIGHT = 250;
    private static final int RAIL_WIDTH = 85;
    private static final int CONTENT_PADDING = 9;
    private static final int CONTENT_HEADER_HEIGHT = 30;
    private static final int MODULE_ROW_HEIGHT = 17;
    private static final int MODULE_GAP = 3;
    private static final int COLUMN_GAP = 6;
    private static final int SEARCH_WIDTH = 80;
    private static final int SEARCH_HEIGHT = 15;
    private static final int SEARCH_X_OFFSET = 6;
    private static final int SCROLL_STEP = 16;

    private static final Set<Type> CATEGORIES = EnumSet.of(Type.Combat, Type.Move, Type.Render, Type.Player, Type.Misc);
    private final Map<Type, Float> scroll = new EnumMap<>(Type.class);
    private final Map<Type, Float> scrollTarget = new EnumMap<>(Type.class);
    private final List<ModuleLayout> visibleLayout = new ArrayList<>();
    private final SearchState searchState = new SearchState();

    private Type selectedCategory = Type.Combat;
    private Function bindingFunction;
    private float bindPopupX;
    private float bindPopupY;
    private boolean closeRequested;
    private float openProgress;

    private final BooleanSettingRenderer booleanRenderer = new BooleanSettingRenderer();
    private final BindBooleanSettingRenderer bindBooleanRenderer = new BindBooleanSettingRenderer();
    private final BindSettingRenderer bindRenderer = new BindSettingRenderer();
    private final ModeSettingRenderer modeRenderer = new ModeSettingRenderer();
    private final MultiSettingRenderer multiRenderer = new MultiSettingRenderer();
    private final SliderSettingRenderer sliderRenderer = new SliderSettingRenderer();
    private final TextSettingRenderer textRenderer = new TextSettingRenderer();

    public ClickGUI() {
        super(Text.literal("ClickGUI"));
        for (Type category : CATEGORIES) {
            scroll.put(category, 0f);
            scrollTarget.put(category, 0f);
        }
    }

    @Override
    protected void init() {
        super.init();
        closeRequested = false;
        openProgress = 0f;
        searchState.focused = false;
        bindingFunction = null;
    }

    @Override
    public void tick() {
        super.tick();
        if (searchState.focused && System.currentTimeMillis() - searchState.lastCursorBlink > 500) {
            searchState.cursorVisible = !searchState.cursorVisible;
            searchState.lastCursorBlink = System.currentTimeMillis();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        openProgress += (1f - openProgress) * 0.22f;
        if (closeRequested) {
            openProgress += (0f - openProgress) * 0.25f;
            if (openProgress < 0.02f) {
                super.close();
                return;
            }
        }

        int shellX = (width - SHELL_WIDTH) / 2;
        int shellY = (height - SHELL_HEIGHT) / 2;
        float scale = 0.94f + openProgress * 0.06f;

        RenderUtil.drawRoundedRect(context.getMatrices(), 0, 0, width, height, 0,
                LupaWareTheme.withAlpha(LupaWareTheme.INK, 142));

        context.getMatrices().push();
        context.getMatrices().translate(width / 2f, height / 2f, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-width / 2f, -height / 2f, 0);

        renderShell(context, shellX, shellY);
        renderRail(context, shellX, shellY, mouseX, mouseY);
        renderContent(context, shellX + RAIL_WIDTH, shellY, mouseX, mouseY);
        renderSearch(context, shellX + RAIL_WIDTH, shellY);
        renderBindPopup(context);
        context.getMatrices().pop();
    }

    private void renderShell(DrawContext context, int x, int y) {
        RenderUtil.drawRoundedRect(context.getMatrices(), x, y, SHELL_WIDTH, SHELL_HEIGHT, 6,
                LupaWareTheme.withAlpha(LupaWareTheme.SURFACE, 244));
        RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, SHELL_WIDTH, SHELL_HEIGHT, 6, 1.4f,
                LupaWareTheme.withAlpha(LupaWareTheme.BORDER, 230));
        RenderUtil.drawRoundedRect(context.getMatrices(), x + RAIL_WIDTH, y, SHELL_WIDTH - RAIL_WIDTH, SHELL_HEIGHT, 5,
                LupaWareTheme.withAlpha(LupaWareTheme.SURFACE_RAISED, 230));
        RenderUtil.drawRoundedRect(context.getMatrices(), x + RAIL_WIDTH, y + 28, 0.7f, SHELL_HEIGHT - 28,
                0, LupaWareTheme.withAlpha(LupaWareTheme.BORDER_SOFT, 180));
    }

    private void renderRail(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int accent = LupaWareTheme.GOLD;
        String username = mc.getSession().getUsername();
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 5, y - 30, 75, 25, 4,
                LupaWareTheme.withAlpha(LupaWareTheme.SURFACE_SOFT, 220));
        RenderUtil.drawRoundedBorder(context.getMatrices(), x + 5, y - 30, 75, 25, 4, 0.8f,
                LupaWareTheme.withAlpha(LupaWareTheme.BORDER_SOFT, 180));
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 22, y - 15, 5, 5, 2.5f, LupaWareTheme.MINT);
        FontUtils.sf_medium[8].drawLeftAligned(context.getMatrices(), username.length() > 12 ? username.substring(0, 12) : username,
                x + 31, y - 21, LupaWareTheme.WHITE);
        FontUtils.sf_medium[6].drawLeftAligned(context.getMatrices(), "CLIENT ONLINE", x + 31, y - 12, LupaWareTheme.DIM);
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 6, y + 8, 29, 29, 8, accent);
        FontUtils.sf_bold[13].centeredDraw(context.getMatrices(), "LW", x + 20.5f, y + 16, LupaWareTheme.INK);
        FontUtils.sf_bold[11].drawLeftAligned(context.getMatrices(), "LUPAWARE", x + 8, y + 47, LupaWareTheme.WHITE);
        FontUtils.sf_medium[7].drawLeftAligned(context.getMatrices(), "CONTROL DECK", x + 8, y + 61, LupaWareTheme.DIM);
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 8, y + 76, RAIL_WIDTH - 16, 0.6f, 0,
                LupaWareTheme.withAlpha(LupaWareTheme.BORDER_SOFT, 160));
        FontUtils.sf_medium[7].drawLeftAligned(context.getMatrices(), "CATEGORIES", x + 8, y + 87, LupaWareTheme.DIM);

        int rowY = y + 101;
        for (Type category : CATEGORIES) {
            boolean active = category == selectedCategory;
            boolean hovered = isHovered(mouseX, mouseY, x + 5, rowY, RAIL_WIDTH - 10, 20);
            if (active || hovered) {
                RenderUtil.drawRoundedRect(context.getMatrices(), x + 5, rowY, RAIL_WIDTH - 10, 20, 5,
                        active ? LupaWareTheme.withAlpha(LupaWareTheme.GOLD, 48) : LupaWareTheme.withAlpha(LupaWareTheme.SURFACE_SOFT, 130));
            }
            if (active) {
                RenderUtil.drawRoundedRect(context.getMatrices(), x + 5, rowY + 4, 2, 12, 1,
                        accent);
            }
            FontUtils.icomoon[11].drawLeftAligned(context.getMatrices(), category.icon, x + 12, rowY + 5,
                    active ? accent : LupaWareTheme.MUTED);
            FontUtils.sf_medium[8].drawLeftAligned(context.getMatrices(), category.name().toUpperCase(), x + 28, rowY + 6,
                    active ? LupaWareTheme.WHITE : LupaWareTheme.MUTED);
            rowY += 23;
        }
        FontUtils.sf_medium[7].drawLeftAligned(context.getMatrices(), "H  HUD LAYOUT", x + 8, y + SHELL_HEIGHT - 15, LupaWareTheme.DIM);
    }

    private void renderContent(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int contentX = x + CONTENT_PADDING;
        int contentY = y + CONTENT_HEADER_HEIGHT;
        int contentWidth = SHELL_WIDTH - RAIL_WIDTH - CONTENT_PADDING * 2;
        int contentHeight = SHELL_HEIGHT - CONTENT_HEADER_HEIGHT - 8;

        String title = selectedCategory.name().toUpperCase();
        FontUtils.icomoon[12].drawLeftAligned(context.getMatrices(), selectedCategory.icon, contentX, y + 8, LupaWareTheme.GOLD);
        FontUtils.sf_bold[14].drawLeftAligned(context.getMatrices(), title, contentX + 20, y + 7, LupaWareTheme.WHITE);
        FontUtils.sf_medium[7].drawLeftAligned(context.getMatrices(), "MODULES / RIGHT CLICK FOR SETTINGS", contentX + 20, y + 21, LupaWareTheme.DIM);
        FontUtils.sf_medium[7].drawRightAligned(context.getMatrices(), countVisible(selectedCategory) + " MODULES",
                x + SHELL_WIDTH - 16, y + 10, LupaWareTheme.DIM);
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX, y + CONTENT_HEADER_HEIGHT - 1, contentWidth, 0.6f, 0,
                LupaWareTheme.withAlpha(LupaWareTheme.BORDER_SOFT, 140));

        float target = scrollTarget.getOrDefault(selectedCategory, 0f);
        float current = scroll.getOrDefault(selectedCategory, 0f);
        current = MathUtil.lerp(current, target, 0.22f);
        scroll.put(selectedCategory, current);

        visibleLayout.clear();
        List<Function> functions = visibleFunctions(selectedCategory);
        float[] columnY = {contentY + 5, contentY + 5};
        float columnWidth = (contentWidth - COLUMN_GAP) / 2f;
        float totalBottom = contentY;

        context.getMatrices().push();
        Scissor.push();
        Scissor.setFromComponentCoordinates(contentX, contentY, contentWidth, contentHeight);
        for (int index = 0; index < functions.size(); index++) {
            Function function = functions.get(index);
            int column = index % 2;
            float moduleX = contentX + column * (columnWidth + COLUMN_GAP);
            float moduleY = columnY[column] - current;
            float moduleHeight = getModuleHeight(function, (int) columnWidth - 10);
            ModuleLayout layout = new ModuleLayout(function, moduleX, moduleY, columnWidth, moduleHeight);
            visibleLayout.add(layout);
            if (moduleY + moduleHeight >= contentY && moduleY <= contentY + contentHeight) {
                renderModule(context, layout, mouseX, mouseY);
            }
            columnY[column] += moduleHeight + MODULE_GAP;
            totalBottom = Math.max(totalBottom, columnY[column]);
        }
        Scissor.pop();
        context.getMatrices().pop();

        float maxScroll = Math.max(0f, totalBottom - contentY - contentHeight);
        scrollTarget.put(selectedCategory, Math.max(0f, Math.min(scrollTarget.get(selectedCategory), maxScroll)));
        if (maxScroll > 0f) {
            float trackX = contentX + contentWidth - 2;
            float trackY = contentY + 2;
            float trackHeight = contentHeight - 4;
            RenderUtil.drawRoundedRect(context.getMatrices(), trackX, trackY, 2, trackHeight, 1,
                    LupaWareTheme.withAlpha(LupaWareTheme.INK, 100));
            float handleHeight = Math.max(12f, trackHeight * trackHeight / (trackHeight + maxScroll));
            float ratio = scroll.get(selectedCategory) / maxScroll;
            float handleY = trackY + (trackHeight - handleHeight) * ratio;
            RenderUtil.drawRoundedRect(context.getMatrices(), trackX, handleY, 2, handleHeight, 1,
                    LupaWareTheme.withAlpha(LupaWareTheme.GOLD, 210));
        }
    }

    private void renderModule(DrawContext context, ModuleLayout layout, int mouseX, int mouseY) {
        Function function = layout.function;
        boolean hovered = isHovered(mouseX, mouseY, layout.x, layout.y, layout.width, MODULE_ROW_HEIGHT);
        int stateColor = function.state ? LupaWareTheme.withAlpha(LupaWareTheme.GOLD, 44) : LupaWareTheme.withAlpha(LupaWareTheme.SURFACE_SOFT, 170);
        if (hovered) stateColor = LupaWareTheme.withAlpha(function.state ? LupaWareTheme.GOLD : LupaWareTheme.BORDER_SOFT, 82);
        RenderUtil.drawRoundedRect(context.getMatrices(), layout.x, layout.y, layout.width, layout.height, 3,
                stateColor);
        RenderUtil.drawRoundedBorder(context.getMatrices(), layout.x, layout.y, layout.width, layout.height, 3, 0.7f,
                function.state ? LupaWareTheme.withAlpha(LupaWareTheme.GOLD, 150) : LupaWareTheme.withAlpha(LupaWareTheme.BORDER_SOFT, 115));
        RenderUtil.drawRoundedRect(context.getMatrices(), layout.x + 5, layout.y + 6, 3, 5, 1,
                function.state ? LupaWareTheme.GOLD : LupaWareTheme.DIM);

        int textColor = function.state ? LupaWareTheme.WHITE : LupaWareTheme.MUTED;
        String moduleName = function.name;
        FontUtils.sf_medium[8].drawLeftAligned(context.getMatrices(), moduleName, layout.x + 13, layout.y + 5, textColor);
        if (function.getBindCode() != 0) {
            String key = ClientManager.getKey(function.getBindCode());
            if (key != null && !key.isEmpty()) {
                FontUtils.sf_medium[6].drawRightAligned(context.getMatrices(), key.length() > 4 ? key.substring(0, 4) : key,
                        layout.x + layout.width - 19, layout.y + 5, LupaWareTheme.DIM);
            }
        }
        FontUtils.sf_medium[10].drawLeftAligned(context.getMatrices(), "f", layout.x + layout.width - 15, layout.y + 3,
                function.state ? LupaWareTheme.GOLD : LupaWareTheme.MUTED);

        if (!function.expanded || function.getSettings().isEmpty()) return;
        RenderUtil.drawRoundedRect(context.getMatrices(), layout.x + 5, layout.y + MODULE_ROW_HEIGHT + 2, layout.width - 10, 0.5f, 0,
                LupaWareTheme.withAlpha(LupaWareTheme.BORDER_SOFT, 120));
        int settingY = (int) layout.y + MODULE_ROW_HEIGHT + 5;
        for (Setting setting : function.getSettings()) {
            if (!setting.isVisible()) continue;
            int settingHeight = getSettingHeight(setting, (int) layout.width - 12);
            renderSetting(context, setting, (int) layout.x + 6, settingY, (int) layout.width - 12, settingHeight);
            settingY += settingHeight + 1;
        }
    }

    private void renderBindPopup(DrawContext context) {
        if (bindingFunction == null) return;
        float x = Math.max(4, Math.min(bindPopupX, width - 124));
        float y = Math.max(4, Math.min(bindPopupY, height - 61));
        RenderUtil.drawRoundedRect(context.getMatrices(), x, y, 120, 57, 4,
                LupaWareTheme.withAlpha(LupaWareTheme.SURFACE, 248));
        RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, 120, 57, 4, 1f,
                LupaWareTheme.withAlpha(LupaWareTheme.BORDER, 220));
        FontUtils.sf_bold[8].drawLeftAligned(context.getMatrices(), "Binding module", x + 8, y + 8, LupaWareTheme.WHITE);
        String name = bindingFunction.name.length() > 15 ? bindingFunction.name.substring(0, 15) + ".." : bindingFunction.name;
        FontUtils.sf_medium[7].drawLeftAligned(context.getMatrices(), name, x + 8, y + 22, LupaWareTheme.MUTED);
        FontUtils.sf_medium[7].drawLeftAligned(context.getMatrices(), "Press a key / ESC", x + 8, y + 37, LupaWareTheme.DIM);
    }

    private void renderSearch(DrawContext context, int x, int y) {
        int searchX = x + SHELL_WIDTH - RAIL_WIDTH - SEARCH_WIDTH - SEARCH_X_OFFSET;
        int searchY = y + 6;
        boolean hovered = isHovered((int) mouseX(), (int) mouseY(), searchX, searchY, SEARCH_WIDTH, SEARCH_HEIGHT);
        RenderUtil.drawRoundedRect(context.getMatrices(), searchX, searchY, SEARCH_WIDTH, SEARCH_HEIGHT, 3,
                LupaWareTheme.withAlpha(hovered || searchState.focused ? LupaWareTheme.SURFACE_RAISED : LupaWareTheme.SURFACE_SOFT, 180));
        RenderUtil.drawRoundedBorder(context.getMatrices(), searchX, searchY, SEARCH_WIDTH, SEARCH_HEIGHT, 3, 0.6f,
                LupaWareTheme.withAlpha(searchState.focused ? LupaWareTheme.GOLD : LupaWareTheme.BORDER_SOFT, 150));
        String text = searchState.text.isEmpty() && !searchState.focused ? "Search" : searchState.text;
        if (searchState.focused && searchState.cursorVisible) {
            int pos = Math.min(searchState.cursorPosition, text.length());
            text = text.substring(0, pos) + "|" + text.substring(pos);
        }
        FontUtils.sf_medium[8].drawLeftAligned(context.getMatrices(), text, searchX + 5, searchY + 4,
                searchState.focused ? LupaWareTheme.WHITE : LupaWareTheme.DIM);
        FontUtils.sf_medium[8].drawRightAligned(context.getMatrices(), "⌕", searchX + SEARCH_WIDTH - 5, searchY + 4, LupaWareTheme.MUTED);
    }

    private double lastMouseX;
    private double lastMouseY;

    private double mouseX() { return lastMouseX; }
    private double mouseY() { return lastMouseY; }

    private List<Function> visibleFunctions(Type category) {
        String query = searchState.text.trim().toLowerCase();
        List<Function> result = new ArrayList<>();
        for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
            if (query.isEmpty() || function.name.toLowerCase().contains(query) || function.desc.toLowerCase().contains(query)) {
                result.add(function);
            }
        }
        result.sort(Comparator.comparing(f -> f.name.toLowerCase()));
        return result;
    }

    private int countVisible(Type category) {
        return visibleFunctions(category).size();
    }

    private float getModuleHeight(Function function, int width) {
        if (!function.expanded || function.getSettings().isEmpty()) return MODULE_ROW_HEIGHT;
        float height = MODULE_ROW_HEIGHT + 5;
        for (Setting setting : function.getSettings()) {
            if (setting.isVisible()) height += getSettingHeight(setting, width) + 1;
        }
        return height + 3;
    }

    private int getSettingHeight(Setting setting, int width) {
        if (setting instanceof BooleanSetting) return booleanRenderer.getHeight();
        if (setting instanceof BindBooleanSetting) return bindBooleanRenderer.getHeight();
        if (setting instanceof BindSetting) return bindRenderer.getHeight();
        if (setting instanceof ModeSetting mode) return modeRenderer.getHeight(mode, width);
        if (setting instanceof MultiSetting multi) return multiRenderer.getHeight(multi, width);
        if (setting instanceof SliderSetting) return sliderRenderer.getHeight();
        if (setting instanceof TextSetting) return textRenderer.getHeight();
        return 18;
    }

    private void renderSetting(DrawContext context, Setting setting, int x, int y, int width, int height) {
        if (setting instanceof BooleanSetting value) booleanRenderer.render(context, value, x, y, width, height);
        else if (setting instanceof BindBooleanSetting value) bindBooleanRenderer.render(context, value, x, y, width, height);
        else if (setting instanceof BindSetting value) bindRenderer.render(context, value, x, y, width, height);
        else if (setting instanceof ModeSetting value) modeRenderer.render(context, value, x, y, width, height);
        else if (setting instanceof MultiSetting value) multiRenderer.render(context, value, x, y, width, height);
        else if (setting instanceof SliderSetting value) sliderRenderer.render(context, value, x, y, width, height);
        else if (setting instanceof TextSetting value) textRenderer.render(context, value, x, y, width, height);
    }

    private boolean clickSetting(Setting setting, double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        if (setting instanceof BooleanSetting value) return booleanRenderer.mouseClicked(value, mouseX, mouseY, button, x, y, width, height);
        if (setting instanceof BindBooleanSetting value) return bindBooleanRenderer.mouseClicked(value, mouseX, mouseY, button, x, y, width, height);
        if (setting instanceof BindSetting value) return bindRenderer.mouseClicked(value, mouseX, mouseY, button, x, y, width, height);
        if (setting instanceof ModeSetting value) return modeRenderer.mouseClicked(value, mouseX, mouseY, button, x, y, width, height);
        if (setting instanceof MultiSetting value) return multiRenderer.mouseClicked(value, mouseX, mouseY, button, x, y, width, height);
        if (setting instanceof SliderSetting value) return sliderRenderer.mouseClicked(value, mouseX, mouseY, button, x, y, width, height);
        if (setting instanceof TextSetting value) return textRenderer.mouseClicked(value, mouseX, mouseY, button, x, y, width, height);
        return false;
    }

    private boolean settingMouseReleased(Setting setting, double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        if (setting instanceof SliderSetting value) {
            sliderRenderer.mouseReleased(value);
            return true;
        }
        if (setting instanceof TextSetting value) return textRenderer.mouseReleased(value, mouseX, mouseY, button, x, y, width, height);
        return false;
    }

    private boolean settingKeyPressed(Setting setting, int keyCode, int scanCode, int modifiers) {
        if (setting instanceof BindBooleanSetting value && value.isListeningForBind()) {
            value.setKey(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE ? 0 : keyCode);
            value.setListeningForBind(false);
            return true;
        }
        if (setting instanceof BindSetting value && value.isBinding()) {
            value.setKey(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode);
            value.setBinding(false);
            return true;
        }
        if (setting instanceof TextSetting value && value.isFocused()) return textRenderer.keyPressed(value, keyCode, scanCode, modifiers);
        return false;
    }

    private boolean settingCharTyped(Setting setting, char c, int modifiers) {
        if (setting instanceof TextSetting value && value.isFocused()) return textRenderer.charTyped(value, c, modifiers);
        return false;
    }

    private int settingsYFor(Function function, ModuleLayout layout, double mouseX, double mouseY, int button) {
        int settingY = (int) layout.y + MODULE_ROW_HEIGHT + 5;
        int width = (int) layout.width - 12;
        for (Setting setting : function.getSettings()) {
            if (!setting.isVisible()) continue;
            int height = getSettingHeight(setting, width);
            if (isHovered(mouseX, mouseY, layout.x + 6, settingY, width, height)) {
                if (clickSetting(setting, mouseX, mouseY, button, (int) layout.x + 6, settingY, width, height)) return settingY;
            }
            settingY += height + 1;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        int shellX = (width - SHELL_WIDTH) / 2;
        int shellY = (height - SHELL_HEIGHT) / 2;
        int railRowY = shellY + 101;
        int row = 0;
        for (Type category : CATEGORIES) {
            if (isHovered(mouseX, mouseY, shellX + 5, railRowY + row * 23, RAIL_WIDTH - 10, 20)) {
                selectedCategory = category;
                return true;
            }
            row++;
        }
        int searchX = shellX + RAIL_WIDTH + SHELL_WIDTH - RAIL_WIDTH - SEARCH_WIDTH - SEARCH_X_OFFSET;
        int searchY = shellY + 6;
        if (isHovered(mouseX, mouseY, searchX, searchY, SEARCH_WIDTH, SEARCH_HEIGHT) && button == 0) {
            searchState.focused = true;
            searchState.cursorPosition = searchState.text.length();
            searchState.cursorVisible = true;
            return true;
        }

        for (ModuleLayout layout : visibleLayout) {
            if (!isHovered(mouseX, mouseY, layout.x, layout.y, layout.width, layout.height)) continue;
            if (isHovered(mouseX, mouseY, layout.x, layout.y, layout.width, MODULE_ROW_HEIGHT)) {
                if (button == 0 && isHovered(mouseX, mouseY, layout.x + layout.width - 22, layout.y + 2, 18, 13)) {
                    bindingFunction = layout.function;
                    bindPopupX = (float) mouseX + 8;
                    bindPopupY = (float) mouseY + 8;
                    return true;
                }
                if (button == 0) {
                    layout.function.toggle();
                    return true;
                }
                if (button == 1) {
                    if (!layout.function.getSettings().isEmpty()) layout.function.expanded = !layout.function.expanded;
                    return true;
                }
                if (button == 2) {
                    bindingFunction = layout.function;
                    return true;
                }
            } else if (layout.function.expanded && settingsYFor(layout.function, layout, mouseX, mouseY, button) >= 0) {
                return true;
            }
        }
        if (button == 0) searchState.focused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (ModuleLayout layout : visibleLayout) {
            if (!layout.function.expanded) continue;
            int settingY = (int) layout.y + MODULE_ROW_HEIGHT + 5;
            int settingWidth = (int) layout.width - 12;
            for (Setting setting : layout.function.getSettings()) {
                if (!setting.isVisible()) continue;
                int height = getSettingHeight(setting, settingWidth);
                settingMouseReleased(setting, mouseX, mouseY, button, (int) layout.x + 6, settingY, settingWidth, height);
                settingY += height + 1;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (ModuleLayout layout : visibleLayout) {
            if (!layout.function.expanded) continue;
            int settingY = (int) layout.y + MODULE_ROW_HEIGHT + 5;
            int settingWidth = (int) layout.width - 12;
            for (Setting setting : layout.function.getSettings()) {
                if (!setting.isVisible()) continue;
                int height = getSettingHeight(setting, settingWidth);
                if (setting instanceof SliderSetting slider) sliderRenderer.mouseDragged(slider, mouseX, (int) layout.x + 6, settingWidth);
                settingY += height + 1;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int shellX = (width - SHELL_WIDTH) / 2;
        int shellY = (height - SHELL_HEIGHT) / 2;
        int contentX = shellX + RAIL_WIDTH + CONTENT_PADDING;
        int contentY = shellY + CONTENT_HEADER_HEIGHT;
        int contentWidth = SHELL_WIDTH - RAIL_WIDTH - CONTENT_PADDING * 2;
        int contentHeight = SHELL_HEIGHT - CONTENT_HEADER_HEIGHT - 8;
        if (isHovered(mouseX, mouseY, contentX, contentY, contentWidth, contentHeight)) {
            scrollTarget.put(selectedCategory, Math.max(0f, scrollTarget.get(selectedCategory) - (float) verticalAmount * SCROLL_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingFunction != null) {
            bindingFunction.setBindCode(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE ? 0 : keyCode);
            bindingFunction = null;
            return true;
        }
        if (searchState.focused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchState.focused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searchState.cursorPosition > 0) {
                int pos = searchState.cursorPosition;
                searchState.text = searchState.text.substring(0, pos - 1) + searchState.text.substring(pos);
                searchState.cursorPosition--;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                searchState.cursorPosition = Math.max(0, searchState.cursorPosition - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                searchState.cursorPosition = Math.min(searchState.text.length(), searchState.cursorPosition + 1);
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeRequested = true;
            return true;
        }
        for (ModuleLayout layout : visibleLayout) {
            if (!layout.function.expanded) continue;
            for (Setting setting : layout.function.getSettings()) {
                if (setting.isVisible() && settingKeyPressed(setting, keyCode, scanCode, modifiers)) return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchState.focused) {
            if (searchState.text.length() < 24) {
                int pos = Math.max(0, Math.min(searchState.cursorPosition, searchState.text.length()));
                searchState.text = searchState.text.substring(0, pos) + chr + searchState.text.substring(pos);
                searchState.cursorPosition = pos + 1;
            }
            return true;
        }
        for (ModuleLayout layout : visibleLayout) {
            if (!layout.function.expanded) continue;
            for (Setting setting : layout.function.getSettings()) {
                if (setting.isVisible() && settingCharTyped(setting, chr, modifiers)) return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        closeRequested = true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static final class ModuleLayout {
        private final Function function;
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        private ModuleLayout(Function function, float x, float y, float width, float height) {
            this.function = function;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
