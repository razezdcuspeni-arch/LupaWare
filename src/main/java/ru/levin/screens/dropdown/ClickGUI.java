package ru.levin.screens.dropdown;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.glfw.GLFW;
import ru.levin.manager.ClientManager;
import ru.levin.manager.dragManager.Dragging;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.manager.themeManager.Style;
import ru.levin.modules.Function;
import ru.levin.modules.Type;
import ru.levin.modules.setting.*;
import ru.levin.protect.AES;
import ru.levin.screens.dropdown.impl.*;
import ru.levin.screens.dropdown.search.SearchState;
import ru.levin.util.animations.impl.EaseInOutQuad;
import ru.levin.util.color.ColorUtil;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.math.MathUtil;
import ru.levin.util.render.RenderAddon;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.Scissor;
import ru.levin.util.render.providers.ResourceProvider;
import java.awt.*;
import java.util.*;

import static ru.levin.util.render.RenderUtil.drawBlur;

public class ClickGUI extends Screen implements IMinecraft {
    private boolean isClose;
    private boolean hudLayoutMode = false;

    private final int PANEL_WIDTH = 480;
    private final int PANEL_HEIGHT = 500;
    private final int PANEL_MARGIN = 0;
    private final int RAIL_WIDTH = 164;
    private Type selectedCategory = Type.Combat;

    private final Color GUI_COLOR = new Color(12, 12, 12, 245);

    private final int TITLE_MARGIN_TOP = 9;
    private final int TITLE_HEIGHT = 42;

    private final int FUNCTION_HEIGHT = 34;

    private final int SCROLL_AREA_Y_OFFSET = 96;
    private final int SCROLL_AREA_HEIGHT = PANEL_HEIGHT - SCROLL_AREA_Y_OFFSET - 10;

    private final Set<Type> renderCategories = EnumSet.of(Type.Combat, Type.Move, Type.Render, Type.Player, Type.Misc);
    private static final Map<Type, Float> scrollOffsets = new HashMap<>();
    private static final Map<Type, Float> scrollTargets = new HashMap<>();
    private final Map<Function, Float> arrowRotationProgress = new HashMap<>();
    private final EaseInOutQuad animationOpen = new EaseInOutQuad(250, 1);
    private double animation;

    private final Map<Function, Float> expandProgress = new HashMap<>();

    private final SearchState searchState;
    private boolean functionBinding = false;
    private Function functions = null;

    private SliderSetting draggingSlider = null;
    private int draggingSliderX = 0;
    private int draggingSliderWidth = 0;

    private final BooleanSettingRenderer booleanSettingRenderer = new BooleanSettingRenderer();
    private final BindBooleanSettingRenderer bindbooleanSettingRenderer = new BindBooleanSettingRenderer();
    private final BindSettingRenderer bindSettingRenderer = new BindSettingRenderer();
    private final ModeSettingRenderer modeSettingRenderer = new ModeSettingRenderer();
    private final MultiSettingRenderer multiSettingRenderer = new MultiSettingRenderer();
    private final SliderSettingRenderer sliderSettingRenderer = new SliderSettingRenderer();
    private final TextSettingRenderer textSettingRenderer = new TextSettingRenderer();

    private final int SEARCH_HEIGHT = 28;
    private final int SEARCH_MARGIN_BOTTOM = 9;
    private final int SEARCH_MAX_WIDTH = 310;

    private final int THEME_HEIGHT = 30;
    private final int THEME_MARGIN_BOTTOM = 8;
    private final int THEME_MAX_WIDTH = 280;
    private static float themeScrollOffset = 0;
    private static float themeScrollTarget = 0;
    private final int VISIBLE_THEMES = 8;

    private float themeMenuAnim = 0f;
    private float themeMenuTarget = 0f;
    private float themeAlphaAnim = 0f;
    private static final float THEME_ANIM_SPEED = 0.2f;
    private static boolean themeMenu;
    private float themeNameAnim = 0f;

    private final float SCROLL_SPEED = 12f;
    private final float SCROLL_LERP_FACTOR = 20f;
    private final float SCROLL_SMOOTH_FACTOR = 12f;

    private final float THEME_SCROLL_SPEED = 15f;
    private final float THEME_SCROLL_LERP_FACTOR = 15f;

    private static boolean colorPickerOpen = false;
    private static int selectedColor1 = Color.WHITE.getRGB();
    private static int selectedColor2 = Color.WHITE.getRGB();

    private float picker1CursorX = 0.5f;
    private float picker1CursorY = 0.5f;
    private float picker2CursorX = 0.5f;
    private float picker2CursorY = 0.5f;
    private boolean draggingPicker1 = false;
    private boolean draggingPicker2 = false;
    private float colorPickerAnim = 0f;

    public ClickGUI() {
        super(Text.literal("ClickGUI"));
        this.searchState = new SearchState();
        if (scrollOffsets.isEmpty() && scrollTargets.isEmpty()) {
            renderCategories.forEach(cat -> {
                scrollOffsets.put(cat, 0f);
                scrollTargets.put(cat, 0f);
            });
        }
    }

    @Override
    public void init() {
        super.init();

        isClose = false;
        animationOpen.setDirection(Direction.AxisDirection.POSITIVE);
        animationOpen.reset();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        animation = animationOpen.getOutput();
        if (isClose && animationOpen.finished(Direction.AxisDirection.NEGATIVE)) {
            super.close();
            return;
        }
        if (animation <= 0.01) return;
        super.render(ctx, mouseX, mouseY, delta);
        ctx.getMatrices().push();
        RenderAddon.sizeAnimation(ctx.getMatrices(), width / 2, height / 2, animation);

        int panelX = (width - (RAIL_WIDTH + PANEL_WIDTH + 18)) / 2 + RAIL_WIDTH + 18;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int railX = panelX - RAIL_WIDTH - 18;
        renderNavigationRail(ctx, railX, panelY, mouseX, mouseY);

        for (Type category : renderCategories) {
            float target = scrollTargets.get(category);
            float current = scrollOffsets.get(category);
            scrollOffsets.put(category, MathUtil.lerp(current, target, SCROLL_LERP_FACTOR));
        }
        renderPanel(ctx, panelX, panelY, selectedCategory, mouseX, mouseY);
        Function hoveredFunction = getHoveredFunction(mouseX, mouseY, panelX, panelY);
        if (hoveredFunction != null && hoveredFunction.desc != null && !hoveredFunction.desc.isEmpty()) drawDescription(ctx, hoveredFunction.desc, panelY);
        DescriptionRenderQueue.renderAll(ctx);
        renderSearchField(ctx);
        renderButtomTheme(ctx, mouseX, mouseY);
        renderTheme(ctx, mouseX, mouseY);
        renderExpiryText(ctx);
        renderHudLayoutOverlay(ctx, mouseX, mouseY);
        ctx.getMatrices().pop();
    }

    private void renderNavigationRail(DrawContext ctx, int x, int y, int mouseX, int mouseY) {
        int railHeight = PANEL_HEIGHT;
        int rail = ColorUtil.rgba(13, 22, 37, 248);
        int border = ColorUtil.rgba(62, 91, 116, 210);
        int accent = ColorUtil.rgba(95, 229, 211, 255);
        RenderUtil.drawRoundedRect(ctx.getMatrices(), x, y, RAIL_WIDTH, railHeight, 18, rail);
        RenderUtil.drawRoundedBorder(ctx.getMatrices(), x, y, RAIL_WIDTH, railHeight, 18, 1f, border);
        RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 18, y + 18, 42, 42, 13, accent);
        FontUtils.sf_bold[17].centeredDraw(ctx.getMatrices(), "LW", x + 39, y + 29, ColorUtil.rgba(8, 22, 29, 255));
        FontUtils.sf_bold[17].drawLeftAligned(ctx.getMatrices(), "LUPAWARE", x + 18, y + 76, Color.WHITE.getRGB());
        FontUtils.sf_medium[9].drawLeftAligned(ctx.getMatrices(), "CONTROL DECK", x + 18, y + 96, ColorUtil.rgba(147, 176, 194, 255));
        RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 18, y + 119, RAIL_WIDTH - 36, 1, 0, ColorUtil.rgba(63, 91, 114, 180));
        FontUtils.sf_medium[9].drawLeftAligned(ctx.getMatrices(), "CATEGORIES", x + 18, y + 137, ColorUtil.rgba(132, 163, 182, 255));
        int rowY = y + 160;
        for (Type category : renderCategories) {
            boolean active = category == selectedCategory;
            boolean hovered = mouseX >= x + 10 && mouseX <= x + RAIL_WIDTH - 10 && mouseY >= rowY && mouseY <= rowY + 38;
            int rowColor = active ? ColorUtil.rgba(38, 77, 82, 235) : (hovered ? ColorUtil.rgba(26, 48, 67, 230) : ColorUtil.rgba(0, 0, 0, 0));
            if (rowColor != 0) RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 10, rowY, RAIL_WIDTH - 20, 38, 10, rowColor);
            if (active) RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 10, rowY + 8, 3, 22, 2, accent);
            FontUtils.icomoon[15].drawLeftAligned(ctx.getMatrices(), category.icon, x + 22, rowY + 11, active ? accent : ColorUtil.rgba(157, 182, 197, 255));
            FontUtils.sf_medium[12].drawLeftAligned(ctx.getMatrices(), category.name().toUpperCase(), x + 48, rowY + 11, active ? Color.WHITE.getRGB() : ColorUtil.rgba(189, 205, 215, 255));
            rowY += 43;
        }
        FontUtils.sf_medium[9].drawLeftAligned(ctx.getMatrices(), "H  HUD LAYOUT", x + 18, y + railHeight - 36, ColorUtil.rgba(147, 176, 194, 255));
    }
    private void renderHudLayoutOverlay(DrawContext ctx, int mouseX, int mouseY) {
        if (!hudLayoutMode) return;
        int accent = new Color(210, 210, 210).getRGB();
        int text = ColorUtil.applyAlpha(Color.WHITE.getRGB(), 220);
        FontUtils.sf_medium[14].drawLeftAligned(ctx.getMatrices(), "HUD LAYOUT  •  drag modules with mouse", 16, 16, text);
        Manager.DRAG_MANAGER.draggables.values().forEach(dragging -> {
            if (dragging.getModule() != null && dragging.getModule().state) {
                dragging.onDraw(mouseX, mouseY, mc.getWindow());
            }
        });
        RenderUtil.drawRoundedBorder(ctx.getMatrices(), 10, 10, 235, 22, 6, 1f, ColorUtil.applyAlpha(accent, 180));
    }

    private Function getHoveredFunction(int mouseX, int mouseY, int panelX, int panelY) {
        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < panelY || mouseY > panelY + PANEL_HEIGHT) return null;
        float offset = scrollOffsets.get(selectedCategory);
        float currentY = panelY + SCROLL_AREA_Y_OFFSET - offset;
        for (Function function : Manager.FUNCTION_MANAGER.getFunctions(selectedCategory)) {
            if (!isFunctionVisible(function)) continue;
            int functionHeight = FUNCTION_HEIGHT;
            int fullSettingsHeight = computeSettingsHeight(function);
            float progress = expandProgress.getOrDefault(function, function.expanded ? 1f : 0f);
            int animatedSettingsHeight = (int) Math.round(fullSettingsHeight * progress);
            int totalHeight = functionHeight + animatedSettingsHeight;
            if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= currentY && mouseY <= currentY + functionHeight && mouseY >= panelY + SCROLL_AREA_Y_OFFSET && mouseY <= panelY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) return function;
            if (animatedSettingsHeight > 0) {
                float settingY = currentY + functionHeight;
                int remaining = animatedSettingsHeight;
                for (Setting setting : function.getSettings()) {
                    if (!setting.isVisible()) continue;
                    int settingHeight = getSettingRendererHeight(setting, PANEL_WIDTH - 28);
                    if (settingHeight <= 0) continue;
                    int visible = Math.max(0, Math.min(settingHeight, remaining));
                    if (visible <= 0) break;
                    if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= settingY && mouseY <= settingY + visible && mouseY >= panelY + SCROLL_AREA_Y_OFFSET && mouseY <= panelY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) return function;
                    settingY += settingHeight + 1;
                    remaining -= settingHeight + 1;
                    if (remaining <= 0) break;
                }
            }
            currentY += totalHeight;
        }
        return null;
    }

    private void drawDescription(DrawContext ctx, String desc, int startY) {
        int descWidth = (int) FontUtils.durman[19].getWidth(desc);
        int descHeight = 20;

        int descX = (width - descWidth) / 2;
        int descY = startY - descHeight - 10;


        if (Manager.FUNCTION_MANAGER.clickGUI.blur.get() && Manager.FUNCTION_MANAGER.clickGUI.blurSetting.get("Описание")) {
            drawBlur(ctx.getMatrices(), descX - 6, descY - 3.5f, descWidth + 12, descHeight, 12, 8, -1);
        }

        RenderUtil.drawRoundedRect(ctx.getMatrices(), descX - 6, descY - 3.5f, descWidth + 12, descHeight, 6, GUI_COLOR.getRGB());
        FontUtils.durman[19].drawLeftAligned(ctx.getMatrices(), desc, descX, descY, Color.WHITE.getRGB());
    }

    private float updateExpandAnimation(Function f) {
        float target = f.expanded ? 1f : 0f;
        float prog = expandProgress.getOrDefault(f, target);

        prog = MathUtil.lerp(prog, target, 15f);

        if (Math.abs(target - prog) < 0.001f) prog = target;
        expandProgress.put(f, prog);

        return prog;
    }

    private void renderPanel(DrawContext ctx, int x, int y, Type category, int mouseX, int mouseY) {
        ru.levin.modules.render.ClickGUI clickGUI = Manager.FUNCTION_MANAGER.clickGUI;
        int panelColor = new Color(14, 25, 42, 246).getRGB();
        int headerColor = new Color(22, 42, 62, 250).getRGB();
        int borderColor = new Color(74, 112, 135, 220).getRGB();
        int accentColor = ColorUtil.rgba(95, 229, 211, 255);

        if (clickGUI.blur.get() && clickGUI.blurSetting.get("Панели")) {
            drawBlur(ctx.getMatrices(), x, y, PANEL_WIDTH, PANEL_HEIGHT, 18, 12, -1);
        }
        RenderUtil.drawRoundedRect(ctx.getMatrices(), x, y, PANEL_WIDTH, PANEL_HEIGHT, 18, panelColor);
        RenderUtil.drawRoundedBorder(ctx.getMatrices(), x, y, PANEL_WIDTH, PANEL_HEIGHT, 18, 1f, borderColor);
        RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 1, y + 1, PANEL_WIDTH - 2, 82, 17, headerColor);
        RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 20, y + 20, 5, 42, 2, accentColor);
        RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 20, y + 82, PANEL_WIDTH - 40, 1, 0, ColorUtil.rgba(74, 112, 135, 180));

        String title = category.name().toUpperCase();
        String icon = category.icon;
        FontUtils.icomoon[18].drawLeftAligned(ctx.getMatrices(), icon, x + 40, y + 25, accentColor);
        FontUtils.sf_bold[22].drawLeftAligned(ctx.getMatrices(), title, x + 70, y + 20, Color.WHITE.getRGB());
        FontUtils.sf_medium[11].drawLeftAligned(ctx.getMatrices(), "MODULE LIBRARY  /  RIGHT CLICK FOR SETTINGS", x + 70, y + 48, ColorUtil.rgba(157, 189, 202, 255));
        FontUtils.sf_medium[11].drawRightAligned(ctx.getMatrices(), Manager.FUNCTION_MANAGER.getFunctions(category).size() + " MODULES", x + PANEL_WIDTH - 24, y + 28, ColorUtil.rgba(170, 201, 211, 255));

        {
            int maxBefore = calculateMaxScroll(category);
            float clampedTarget = MathHelper.clamp(scrollTargets.get(category), 0f, (float) maxBefore);
            float clampedOffset = MathHelper.clamp(scrollOffsets.get(category), 0f, (float) maxBefore);
            scrollTargets.put(category, clampedTarget);
            scrollOffsets.put(category, clampedOffset);
        }

        float offset = scrollOffsets.compute(category, (k, v) -> {
            float target = scrollTargets.get(k);
            float current = v;
            float lerped = MathUtil.lerp(current, target, SCROLL_LERP_FACTOR);
            float smoothed = MathUtil.lerp(current, lerped, SCROLL_SMOOTH_FACTOR);

            return smoothed;
        });

        renderScrollbar(ctx, x, y, category, offset);
        ctx.getMatrices().push();
        Scissor.push();
        Scissor.setFromComponentCoordinates(x + 1, y + 90, PANEL_WIDTH - 2, PANEL_HEIGHT - 100);

        float currentY = y + 96 - offset;

        for (Function f : Manager.FUNCTION_MANAGER.getFunctions(category)) {
            if (!isFunctionVisible(f)) continue;

            int functionHeight = FUNCTION_HEIGHT;
            float prog = updateExpandAnimation(f);

            int fullSettingsHeight = computeSettingsHeight(f);
            int animatedSettingsHeight = (int) (fullSettingsHeight * prog);
            int totalHeight = functionHeight + animatedSettingsHeight;

            if (currentY + totalHeight < y + SCROLL_AREA_Y_OFFSET || currentY > y + PANEL_HEIGHT) {
                currentY += totalHeight;
                continue;
            }

            int col1 = f.state ? Color.WHITE.getRGB() : ColorUtil.rgba(183, 203, 214, 255);
            int col2 = col1;

            int colorModule = f.state ? new Color(31, 88, 87, clickGUI.alphaModules.get().intValue()).getRGB() : new Color(20, 38, 57, 225).getRGB();
            int colorModule2 = colorModule;

            if (clickGUI.filling.get() || f.state) {
                RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 18, currentY + 3, PANEL_WIDTH - 36, Math.max(29, totalHeight - 7), 11, colorModule2);
                RenderUtil.drawRoundedRect(ctx.getMatrices(), x + 31, currentY + 14, 5, 9, 2, f.state ? accentColor : ColorUtil.rgba(87, 121, 140, 255));
            }

            String textToRender;
            if (functionBinding && functions == f) {
                int bindCode = f.getBindCode();
                String keyName = bindCode != 0 ? ClientManager.getKey(bindCode) : "";
                textToRender = bindCode == 0 ? "Binding..." : "Binding... [" + keyName + "]";
            } else {
                textToRender = f.name;
            }

            FontUtils.sf_medium[14].drawClipped(ctx.getMatrices(), textToRender, PANEL_WIDTH - 74, x + 50, currentY + 10, f.state ? Color.WHITE.getRGB() : col1);

            if (animatedSettingsHeight > 0) {
                float settingY = currentY + functionHeight;
                ctx.getMatrices().push();
                Scissor.push();
                Scissor.setFromComponentCoordinates(x + 1, (int) settingY, PANEL_WIDTH - 2, animatedSettingsHeight);

                for (Setting setting : f.getSettings()) {
                    if (!setting.isVisible()) continue;
                    int settingHeight = 0;

                    if (setting instanceof BooleanSetting booleanSetting) {
                        settingHeight = booleanSettingRenderer.getHeight();
                        booleanSettingRenderer.render(ctx, booleanSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof BindBooleanSetting bindBooleanSetting) {
                        settingHeight = bindbooleanSettingRenderer.getHeight();
                        bindbooleanSettingRenderer.render(ctx, bindBooleanSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof BindSetting bindSetting) {
                        settingHeight = bindSettingRenderer.getHeight();
                        bindSettingRenderer.render(ctx, bindSetting, x + 10, (int) settingY - 2, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof ModeSetting modeSetting) {
                        settingHeight = modeSettingRenderer.getHeight(modeSetting, PANEL_WIDTH - 20);
                        modeSettingRenderer.render(ctx, modeSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof MultiSetting multiSetting) {
                        settingHeight = multiSettingRenderer.getHeight(multiSetting, PANEL_WIDTH - 20);
                        multiSettingRenderer.render(ctx, multiSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof SliderSetting sliderSetting) {
                        settingHeight = sliderSettingRenderer.getHeight();
                        sliderSettingRenderer.render(ctx, sliderSetting, x + 10, (int) settingY - 2, PANEL_WIDTH - 20, settingHeight);
                    } else if (setting instanceof TextSetting textSetting) {
                        settingHeight = textSettingRenderer.getHeight();
                        textSettingRenderer.render(ctx, textSetting, x + 10, (int) settingY, PANEL_WIDTH - 20, settingHeight);
                    }

                    settingY += settingHeight + 1;
                }

                Scissor.pop();
                ctx.getMatrices().pop();
            }

            boolean hasSettings = !f.getSettings().isEmpty();
            if (hasSettings) {
                float currentProgress = arrowRotationProgress.getOrDefault(f, f.expanded ? 1f : 0f);
                currentProgress = MathUtil.lerp(currentProgress, f.expanded ? 1f : 0f, 15f);
                if (Math.abs((f.expanded ? 1f : 0f) - currentProgress) < 0.001f) currentProgress = f.expanded ? 1f : 0f;
                arrowRotationProgress.put(f, currentProgress);

                int arrowX = x + PANEL_WIDTH - 35;
                int arrowY = (int) (currentY + FUNCTION_HEIGHT / 2);

                ctx.getMatrices().push();
                ctx.getMatrices().translate(arrowX, arrowY, 0);
                float angleRad = (float) Math.toRadians(90.0f * currentProgress);
                Quaternionf rotation = new Quaternionf().fromAxisAngleRad(new Vector3f(0, 0, 1), angleRad);
                ctx.getMatrices().multiply(rotation);
                FontUtils.sf_medium[18].drawLeftAligned(ctx.getMatrices(), "+", -4, -FontUtils.sf_medium[18].getHeight() / 2, f.state ? accentColor : ColorUtil.rgba(148, 180, 194, 255));
                ctx.getMatrices().pop();
            }

            currentY += totalHeight;
        }

        clampScrollForCategory(category);
        Scissor.pop();
        ctx.getMatrices().pop();
    }

    private void renderScrollbar(DrawContext ctx, int x, int y, Type category, float offset) {
        int maxScroll = calculateMaxScroll(category);
        if (maxScroll <= 0) return;

        int scrollbarWidth = 3;
        int scrollbarX = x + PANEL_WIDTH - scrollbarWidth - 1;

        int scrollbarHeight = SCROLL_AREA_HEIGHT - 30;
        int scrollbarY = y + SCROLL_AREA_Y_OFFSET + 15;

        int scrollbarBgColor = new Color(0, 0, 0, 50).getRGB();
        RenderUtil.drawRoundedRect(ctx.getMatrices(), scrollbarX, scrollbarY, scrollbarWidth, scrollbarHeight, 1, scrollbarBgColor);

        float scrollProgress = offset / maxScroll;
        int thumbHeight = Math.max(6, (int) (scrollbarHeight * (SCROLL_AREA_HEIGHT / (float) (SCROLL_AREA_HEIGHT + maxScroll))));
        int thumbY = scrollbarY + (int) (scrollProgress * (scrollbarHeight - thumbHeight));

        int thumbColor = new Color(255, 255, 255, 150).getRGB();
        RenderUtil.drawRoundedRect(ctx.getMatrices(), scrollbarX, thumbY, scrollbarWidth, thumbHeight, 1, thumbColor);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int themeWidth = getThemeWidth();
        int themeX = getThemeX(themeWidth);
        int themeY = getThemeY();

        if (mouseX >= themeX && mouseX <= themeX + themeWidth && mouseY >= themeY && mouseY <= themeY + THEME_HEIGHT) {
            themeScrollTarget += (float) (-scrollY * THEME_SCROLL_SPEED);
            return true;
        }

        int panelX = (width - (RAIL_WIDTH + PANEL_WIDTH + 18)) / 2 + RAIL_WIDTH + 18;
        int panelY = (height - PANEL_HEIGHT) / 2;
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY + SCROLL_AREA_Y_OFFSET && mouseY <= panelY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {
            int maxScroll = calculateMaxScroll(selectedCategory);
            if (maxScroll > 0) {
                scrollTargets.compute(selectedCategory, (k, v) -> Math.max(0, Math.min(v - (float) scrollY * SCROLL_SPEED, maxScroll)));
                return true;
            }
            return false;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int calculateMaxScroll(Type category) {
        int totalHeight = 0;
        for (Function f : Manager.FUNCTION_MANAGER.getFunctions(category)) {
            if (!isFunctionVisible(f)) continue;
            int functionHeight = FUNCTION_HEIGHT;

            int fullSettingsHeight = computeSettingsHeight(f);
            float prog = expandProgress.getOrDefault(f, f.expanded ? 1f : 0f);
            int animated = (int) Math.round(fullSettingsHeight * prog);

            totalHeight += functionHeight + animated;
        }
        int maxScroll = totalHeight - SCROLL_AREA_HEIGHT;
        return Math.max(0, maxScroll);
    }

    @Override
    public boolean charTyped(char c, int keyCode) {
        if (searchState.focused) {
            String prevText = searchState.text;
            if (searchState.text.length() < 30) {
                String before = searchState.text.substring(0, searchState.cursorPosition);
                String after = searchState.text.substring(searchState.cursorPosition);
                searchState.text = before + c + after;
                searchState.cursorPosition++;
            }
            if (!prevText.equals(searchState.text)) {
                resetScrollForAllCategories();
            }
            return true;
        }

        for (Type category : renderCategories) {
            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!function.expanded) continue;
                for (Setting setting : function.getSettings()) {
                    if (setting instanceof TextSetting textSetting && textSetting.isFocused()) {
                        if (textSettingRenderer.charTyped(textSetting, c, keyCode)) {
                            return true;
                        }
                    }
                }
            }
        }

        return super.charTyped(c, keyCode);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_H && !functionBinding && functions == null) {
            hudLayoutMode = !hudLayoutMode;
            return true;
        }
        if (isClose) {
            return true;
        }
        for (Type category : renderCategories) {
            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!isFunctionVisible(function)) continue;
                if (function.expanded) {
                    for (Setting setting : function.getSettings()) {
                        if (!setting.isVisible()) continue;
                        if (setting instanceof BindBooleanSetting bindBooleanSetting) {
                            if (bindBooleanSetting.isListeningForBind()) {
                                if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                                    bindBooleanSetting.setKey(0);
                                } else {
                                    bindBooleanSetting.setKey(keyCode);
                                }
                                bindBooleanSetting.setListeningForBind(false);
                                return true;
                            }
                        }
                        if (setting instanceof BindSetting bindSetting) {
                            if (bindSetting.isBinding()) {
                                if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                                    bindSetting.setKey(-1);
                                } else {
                                    bindSetting.setKey(keyCode);
                                }
                                bindSetting.setBinding(false);
                                return true;
                            }
                        }
                        if (setting instanceof TextSetting textSetting) {
                            if (textSetting.isFocused()) {
                                if (textSettingRenderer.keyPressed(textSetting, keyCode, scanCode, modifiers)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (functionBinding && functions != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                functions.setBindCode(0);
            } else {
                functions.setBindCode(keyCode);
            }
            functionBinding = false;
            functions = null;
            return true;
        }

        if (searchState.focused) {
            String prevText = searchState.text;
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchState.cursorPosition > 0 && !searchState.text.isEmpty()) {
                        searchState.text = searchState.text.substring(0, searchState.cursorPosition - 1) + searchState.text.substring(searchState.cursorPosition);
                        searchState.cursorPosition--;
                    }
                    if (!prevText.equals(searchState.text)) {
                        resetScrollForAllCategories();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (searchState.cursorPosition < searchState.text.length()) {
                        searchState.text = searchState.text.substring(0, searchState.cursorPosition) + searchState.text.substring(searchState.cursorPosition + 1);
                    }
                    if (!prevText.equals(searchState.text)) {
                        resetScrollForAllCategories();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    if (searchState.cursorPosition > 0) searchState.cursorPosition--;
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (searchState.cursorPosition < searchState.text.length()) searchState.cursorPosition++;
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                    searchState.focused = false;
                    return true;
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void resetScrollForAllCategories() {
        for (Type category : renderCategories) {
            scrollTargets.put(category, 0f);
            scrollOffsets.put(category, 0f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        long currentTime = System.currentTimeMillis();
        if (currentTime - searchState.lastCursorBlink >= 500) {
            searchState.cursorVisible = !searchState.cursorVisible;
            searchState.lastCursorBlink = currentTime;
        }
    }

    @Override
    public void close() {
        if (isClose) {
            return;
        }
        draggingPicker1 = false;
        draggingPicker2 = false;
        isClose = true;
        animationOpen.setDirection(Direction.AxisDirection.NEGATIVE);
        animationOpen.reset();
    }

    private int getSearchWidth() {
        return SEARCH_MAX_WIDTH;
    }

    private int getSearchX(int searchWidth) {
        return (width - searchWidth) / 2;
    }

    private int getSearchY() {
        return (height + PANEL_HEIGHT) / 2 + SEARCH_MARGIN_BOTTOM;
    }

    private void renderSearchField(DrawContext ctx) {
        int searchWidth = getSearchWidth();
        int searchX = getSearchX(searchWidth);
        int searchY = getSearchY();
        ru.levin.modules.render.ClickGUI clickGUI = Manager.FUNCTION_MANAGER.clickGUI;
        if (clickGUI.blur.get() && clickGUI.blurSetting.get("Поиск")) {
            drawBlur(ctx.getMatrices(), searchX, searchY, searchWidth, SEARCH_HEIGHT, 6, 12, -1);
        }
        RenderUtil.drawRoundedRect(ctx.getMatrices(), searchX, searchY, searchWidth, SEARCH_HEIGHT, 6, GUI_COLOR.getRGB());

        String displayText;
        int textColor;
        int textX;

        if (searchState.text.isEmpty() && !searchState.focused) {
            displayText = "Поиск...";
            textColor = new Color(255, 255, 255, 120).getRGB();
            int textWidth = (int) FontUtils.sf_medium[18].getWidth(displayText);
            textX = searchX + (searchWidth - textWidth) / 2;
        } else {
            String text = searchState.text;
            if (searchState.focused && searchState.cursorVisible) {
                int pos = Math.min(searchState.cursorPosition, text.length());
                text = text.substring(0, pos) + "|" + text.substring(pos);
            }
            displayText = text;
            textColor = Color.WHITE.getRGB();
            textX = searchX + 6;
        }
        int textY = (int) (searchY + (SEARCH_HEIGHT - FontUtils.sf_medium[18].getHeight()) / 2);
        FontUtils.sf_medium[18].drawLeftAligned(ctx.getMatrices(), displayText, textX, textY, textColor);
    }

    private int getThemeWidth() {
        return THEME_MAX_WIDTH;
    }

    private int getThemeX(int searchWidth) {
        return (width - searchWidth) / 2;
    }

    private int getThemeY() {
        // Theme strip is deliberately above the search field; it must never overlap it.
        return getSearchY() - THEME_HEIGHT - THEME_MARGIN_BOTTOM;
    }
    private void renderButtomTheme(DrawContext ctx, double mouseX, double mouseY) {
        int searchWidth = getSearchWidth();
        int searchX = getSearchX(searchWidth);
        int searchY = getSearchY();

        int buttonX = searchX + searchWidth - 22;
        int buttonY = searchY + 2;
        int buttonWidth = 16;
        int buttonHeight = 16;
        boolean hovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        int color = hovered ? GUI_COLOR.brighter().getRGB() : GUI_COLOR.getRGB();

        RenderUtil.drawRoundedRect(ctx.getMatrices(), buttonX, buttonY, buttonWidth, buttonHeight, 2, color);
        RenderUtil.drawTexture(ctx.getMatrices(), "images/gui/colors2.png", buttonX + 3, buttonY + 2.5f, 10, 10, 0, Color.white.getRGB());
    }

    private void renderTheme(DrawContext ctx, int mouseX, int mouseY) {
        float targetAlpha = themeMenuTarget > 0.01f ? 1f : 0f;
        themeAlphaAnim += (targetAlpha - themeAlphaAnim) * 0.15f;
        if (themeAlphaAnim < 0.01f) return;

        themeMenuAnim += (themeMenuTarget - themeMenuAnim) * THEME_ANIM_SPEED;
        if (themeMenuAnim < 0.01f) return;

        int themeWidth = getThemeWidth();
        int themeX = getThemeX(themeWidth);
        int themeY = getThemeY();
        float offsetY = (1f - themeMenuAnim) * 10f;
        themeScrollOffset = MathUtil.lerp(themeScrollOffset, themeScrollTarget, THEME_SCROLL_LERP_FACTOR);

        int panelColor = ColorUtil.applyAlpha(GUI_COLOR.getRGB(), themeAlphaAnim);

        if (Manager.FUNCTION_MANAGER.clickGUI.blur.get() && Manager.FUNCTION_MANAGER.clickGUI.blurSetting.get("Темы")) {
            drawBlur(ctx.getMatrices(), themeX, themeY + offsetY, themeWidth, THEME_HEIGHT, 3, 12, -1);
        }
        RenderUtil.drawRoundedRect(ctx.getMatrices(), themeX, themeY + offsetY, themeWidth, THEME_HEIGHT, 3, panelColor);

        int circleSize = THEME_HEIGHT - 5;
        int padding = 5;
        int totalThemes = Manager.STYLE_MANAGER.getStyles().size() + 1;

        float maxScroll = Math.max(0, (totalThemes - VISIBLE_THEMES) * (circleSize + padding));
        themeScrollTarget = MathHelper.clamp(themeScrollTarget, 0, maxScroll);
        themeScrollOffset = MathHelper.clamp(themeScrollOffset, 0, maxScroll);

        if (totalThemes > VISIBLE_THEMES) {
            int arrowColor = ColorUtil.applyAlpha(Color.WHITE.getRGB(), themeAlphaAnim * 0.6f);

            if (themeScrollTarget > 0) {
                int arrowY = themeY + THEME_HEIGHT / 2;
                FontUtils.sf_medium[16].drawLeftAligned(ctx.getMatrices(), "←", themeX - 10, arrowY - 5, arrowColor);
            }
            if (themeScrollTarget < maxScroll) {
                int arrowY = themeY + THEME_HEIGHT / 2;
                int arrowX = themeX + themeWidth + 4;
                FontUtils.sf_medium[16].drawLeftAligned(ctx.getMatrices(), "→", arrowX, arrowY - 5, arrowColor);
            }
        }

        ctx.getMatrices().push();
        Scissor.push();
        Scissor.setFromComponentCoordinates(themeX + 1, themeY + offsetY, themeWidth - 2, THEME_HEIGHT);

        float startX = themeX + padding - themeScrollOffset;
        int centerY = (int) (themeY + (THEME_HEIGHT - circleSize) / 2 + 0.9f + offsetY);
        String hoveredTheme = null;
        int x = (int) startX;
        int y = centerY;

        int pickerSize = circleSize;

        if (selectedColor1 != -1 && selectedColor2 != -1) {
            final Vector4i vec = new Vector4i(
                    ColorUtil.gradient(5, 0, selectedColor1, selectedColor2),
                    ColorUtil.gradient(5, 180, selectedColor1, selectedColor2),
                    ColorUtil.gradient(5, 90, selectedColor1, selectedColor2),
                    ColorUtil.gradient(5, 360, selectedColor1, selectedColor2)
            );
            RenderUtil.rectRGB(ctx.getMatrices(), x, y + 0.5f, circleSize, circleSize, 5,
                    ColorUtil.applyAlpha(vec.w, themeAlphaAnim),
                    ColorUtil.applyAlpha(vec.x, themeAlphaAnim),
                    ColorUtil.applyAlpha(vec.y, themeAlphaAnim),
                    ColorUtil.applyAlpha(vec.z, themeAlphaAnim)
            );
        } else {
            RenderUtil.drawTexture(ctx.getMatrices(), "images/gui/pips.png", x, y + 0.5f, pickerSize, pickerSize, 5,
                    ColorUtil.applyAlpha(Color.WHITE.getRGB(), themeAlphaAnim));
        }

        if (RenderUtil.isHovered(mouseX, mouseY, x, y, circleSize, circleSize)) {
            RenderUtil.drawRoundedBorder(ctx.getMatrices(), x - 1, y - 0.5f, circleSize + 2, circleSize + 2, 5, 0.1f,
                    ColorUtil.applyAlpha(Color.WHITE.getRGB(), themeAlphaAnim));
            hoveredTheme = "ЛКМ - Создать свою тему";
        }
        startX += circleSize + padding;

        for (Style style : Manager.STYLE_MANAGER.getStyles()) {
            int[] colors = style.colors;
            int c1 = colors[0];
            int c2 = colors.length > 1 ? colors[1] : colors[0];
            final Vector4i vec = new Vector4i(
                    ColorUtil.gradient(5, 0, c1, c2),
                    ColorUtil.gradient(5, 180, c1, c2),
                    ColorUtil.gradient(5, 90, c1, c2),
                    ColorUtil.gradient(5, 360, c1, c2)
            );
            x = (int) startX;
            y = centerY;

            RenderUtil.rectRGB(ctx.getMatrices(), x, y + 0.5f, circleSize, circleSize, 5,
                    ColorUtil.applyAlpha(vec.w, themeAlphaAnim),
                    ColorUtil.applyAlpha(vec.x, themeAlphaAnim),
                    ColorUtil.applyAlpha(vec.y, themeAlphaAnim),
                    ColorUtil.applyAlpha(vec.z, themeAlphaAnim)
            );

            if (RenderUtil.isHovered(mouseX, mouseY, x, y, circleSize, circleSize)) {
                hoveredTheme = style.name;
            }
            startX += circleSize + padding;
        }

        Scissor.pop();
        ctx.getMatrices().pop();

        float animSpeed = 0.2f;
        float targetAnim = colorPickerOpen ? 1f : 0f;
        colorPickerAnim += (targetAnim - colorPickerAnim) * animSpeed;

        if (colorPickerAnim > 0.01f) {
            int fixedX = themeX + padding;
            int fixedY = centerY;
            renderColorPickers(ctx, fixedX, fixedY, mouseX, mouseY);
        }

        if (hoveredTheme != null) {
            themeNameAnim += (1f - themeNameAnim) * 0.2f;
        } else {
            themeNameAnim += (0f - themeNameAnim) * 0.2f;
        }

        if (themeNameAnim > 0.01f && hoveredTheme != null) {
            int screenWidth = ctx.getScaledWindowWidth();
            int textWidth = (int) FontUtils.sf_medium[18].getWidth(hoveredTheme);
            int textX = (screenWidth - textWidth) / 2;

            int textColor = ColorUtil.applyAlpha(Color.WHITE.getRGB(), themeNameAnim * themeAlphaAnim);
            FontUtils.sf_medium[18].drawLeftAligned(ctx.getMatrices(), hoveredTheme, textX, centerY + 18, textColor);

            if (hoveredTheme.toLowerCase().contains("custom")) {
                int tipColor = ColorUtil.applyAlpha(Color.WHITE.getRGB(), themeNameAnim * themeAlphaAnim * 0.7f);
                String tipText = "ПКМ — удалить";
                int tipWidth = (int) FontUtils.sf_medium[14].getWidth(tipText);
                int tipX = (screenWidth - tipWidth) / 2;
                FontUtils.sf_medium[14].drawLeftAligned(ctx.getMatrices(), tipText, tipX, centerY + 32, tipColor);
            }
        }
    }

    private void renderColorPickers(DrawContext ctx, int x, int y, int mouseX, int mouseY) {
        int panelWidth = 260;
        int panelHeight = 190;
        float animOffsetX = (1f - colorPickerAnim) * 30f;
        float animScale = 0.95f + 0.05f * colorPickerAnim;
        float alphaMult = colorPickerAnim;

        int panelX = Math.min(width - panelWidth - 12, (int) (x + 40 + animOffsetX));
        int panelY = Math.max(12, y - panelHeight - 24);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(panelX + panelWidth / 2f, panelY + panelHeight / 2f, 0);
        ctx.getMatrices().scale(animScale, animScale, 1f);
        ctx.getMatrices().translate(-panelWidth / 2f, -panelHeight / 2f, 0);

        int baseColor = GUI_COLOR.getRGB();
        if (Manager.FUNCTION_MANAGER.clickGUI.blur.get() && Manager.FUNCTION_MANAGER.clickGUI.blurSetting.get("Создание темы")) {
            RenderUtil.drawBlur(ctx.getMatrices(), 0, 0, panelWidth, panelHeight, 4,12,-1);
        }
        RenderUtil.drawRoundedRect(ctx.getMatrices(), 0, 0, panelWidth, panelHeight, 4, ColorUtil.applyAlpha(baseColor, alphaMult));

        int picker1Size = 104;
        int picker1X = 14;
        int picker1Y = 28;
        RenderUtil.drawTexture(ctx.getMatrices(), "images/gui/pick.png", picker1X, picker1Y, picker1Size, picker1Size, 14, ColorUtil.applyAlpha(Color.WHITE.getRGB(), alphaMult));
        RenderUtil.drawRoundedBorder(ctx.getMatrices(), picker1X, picker1Y, picker1Size, picker1Size, 14, 0.1f, ColorUtil.applyAlpha(Color.WHITE.getRGB(), alphaMult));

        int dotX1 = (int) (picker1X + picker1CursorX * picker1Size);
        int dotY1 = (int) (picker1Y + picker1CursorY * picker1Size);
        RenderUtil.drawCircle(ctx.getMatrices(), dotX1, dotY1, 4f, ColorUtil.applyAlpha(Color.BLACK.getRGB(), alphaMult));

        int picker2Size = 104;
        int picker2X = 142;
        int picker2Y = 28;
        RenderUtil.drawTexture(ctx.getMatrices(), "images/gui/pick.png", picker2X, picker2Y, picker2Size, picker2Size, 14, ColorUtil.applyAlpha(Color.WHITE.getRGB(), alphaMult));
        RenderUtil.drawRoundedBorder(ctx.getMatrices(), picker2X, picker2Y, picker2Size, picker2Size, 14, 0.1f, ColorUtil.applyAlpha(Color.WHITE.getRGB(), alphaMult));

        int dotX2 = (int) (picker2X + picker2CursorX * picker2Size);
        int dotY2 = (int) (picker2Y + picker2CursorY * picker2Size);
        RenderUtil.drawCircle(ctx.getMatrices(), dotX2, dotY2, 4f, ColorUtil.applyAlpha(Color.BLACK.getRGB(), alphaMult));

        int closeButtonSize = 18;
        int closeButtonX = panelWidth - closeButtonSize;
        int closeButtonY = 0;
        RenderUtil.drawRoundedRect(ctx.getMatrices(), closeButtonX, closeButtonY, closeButtonSize, closeButtonSize, new Vector4f(0, 4, 0, 4), ColorUtil.applyAlpha(Color.WHITE.getRGB(), alphaMult));
        FontUtils.sf_medium[20].drawLeftAligned(ctx.getMatrices(), "×", closeButtonX + 2, closeButtonY - 1.5f, ColorUtil.applyAlpha(Color.RED.getRGB(), alphaMult));

        RenderUtil.drawRoundedRect(ctx.getMatrices(), 75, 164, 110, 18, new Vector4f(5, 5, 5, 5), ColorUtil.applyAlpha(Color.WHITE.getRGB(), alphaMult));
        FontUtils.durman[13].centeredDraw(ctx.getMatrices(), "Добавить тему", 130, 170, ColorUtil.applyAlpha(Color.BLACK.getRGB(), alphaMult));

        ctx.getMatrices().pop();
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && colorPickerOpen) {
            int panelWidth = 260;
            int panelHeight = 190;
            int panelX = Math.min(width - panelWidth - 12, getThemeX(getThemeWidth()) + 5 + 40);
            int panelY = Math.max(12, getThemeY() - panelHeight - 24);

            int picker1Size = 104;
            int picker1X = panelX + 14;
            int picker1Y = panelY + 28;

            int picker2Size = 104;
            int picker2X = panelX + 142;
            int picker2Y = panelY + 28;
            if (draggingPicker1) {
                float nx = (float) (mouseX - picker1X) / picker1Size;
                float ny = (float) (mouseY - picker1Y) / picker1Size;
                picker1CursorX = MathHelper.clamp(nx, 0f, 1f);
                picker1CursorY = MathHelper.clamp(ny, 0f, 1f);
                selectedColor1 = ColorUtil.getPixelColor(ResourceProvider.color_image, picker1CursorX, picker1CursorY);
                return true;
            }

            if (draggingPicker2) {
                float nx = (float) (mouseX - picker2X) / picker2Size;
                float ny = (float) (mouseY - picker2Y) / picker2Size;
                picker2CursorX = MathHelper.clamp(nx, 0f, 1f);
                picker2CursorY = MathHelper.clamp(ny, 0f, 1f);
                selectedColor2 = ColorUtil.getPixelColor(ResourceProvider.color_image, picker2CursorX, picker2CursorY);
                return true;
            }
        }

        if (draggingSlider != null && button == 0) {
            sliderSettingRenderer.mouseDragged(draggingSlider, mouseX, draggingSliderX, draggingSliderWidth);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (hudLayoutMode) {
            Manager.DRAG_MANAGER.draggables.values().forEach(dragging -> dragging.onRelease(button));
            Manager.DRAG_MANAGER.save();
        }
        draggingPicker1 = false;
        draggingPicker2 = false;
        if (draggingSlider != null) {
            sliderSettingRenderer.mouseReleased(draggingSlider);
            draggingSlider = null;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }



    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hudLayoutMode && button == 0) {
            for (Dragging dragging : Manager.DRAG_MANAGER.draggables.values()) {
                if (dragging.getModule() != null && dragging.getModule().state && dragging.onClick(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        int panelX = (width - (RAIL_WIDTH + PANEL_WIDTH + 18)) / 2 + RAIL_WIDTH + 18;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int railX = panelX - RAIL_WIDTH - 18;
        int categoryY = panelY + 160;
        for (Type category : renderCategories) {
            if (mouseX >= railX + 10 && mouseX <= railX + RAIL_WIDTH - 10 && mouseY >= categoryY && mouseY <= categoryY + 38) {
                selectedCategory = category;
                resetScrollForAllCategories();
                return true;
            }
            categoryY += 43;
        }
        int searchWidth = getSearchWidth();
        int searchX = getSearchX(searchWidth);
        int searchY = getSearchY();
        int buttonX = searchX + searchWidth - 22;
        int buttonY = searchY + 2;
        int buttonWidth = 16;
        int buttonHeight = 16;

        if (colorPickerOpen) {
            int themeWidth = getThemeWidth();
            int themeX = getThemeX(themeWidth);
            int themeY = getThemeY();
            int circleSize = THEME_HEIGHT - 5;
            int padding = 5;

            float offsetY = (1f - themeMenuAnim) * 10f;
            int centerY = (int) (themeY + (THEME_HEIGHT - circleSize) / 2 + 0.9f + offsetY);

            int fixedX = themeX + padding;
            int fixedY = centerY;

            int panelWidth = 260;
            int panelHeight = 190;
            int pickerPanelX = Math.min(width - panelWidth - 12, fixedX + 40);
            int pickerPanelY = Math.max(12, fixedY - panelHeight - 24);

            int picker1Size = 104;
            int picker1X = pickerPanelX + 14;
            int picker1Y = pickerPanelY + 28;

            int picker2Size = 104;
            int picker2X = pickerPanelX + 142;
            int picker2Y = pickerPanelY + 28;

            int closeButtonSize = 18;
            int closeButtonX = pickerPanelX + panelWidth - closeButtonSize;
            int closeButtonY = pickerPanelY;
            if (mouseX >= closeButtonX && mouseX <= closeButtonX + closeButtonSize && mouseY >= closeButtonY && mouseY <= closeButtonY + closeButtonSize) {
                colorPickerOpen = false;
                return true;
            }

            if (mouseX >= picker1X && mouseX <= picker1X + picker1Size && mouseY >= picker1Y && mouseY <= picker1Y + picker1Size) {
                float nx = (float) (mouseX - picker1X) / picker1Size;
                float ny = (float) (mouseY - picker1Y) / picker1Size;
                draggingPicker1 = true;
                picker1CursorX = Math.max(0, Math.min(1, nx));
                picker1CursorY = Math.max(0, Math.min(1, ny));
                selectedColor1 = ColorUtil.getPixelColor(ResourceProvider.color_image, picker1CursorX, picker1CursorY);
                return true;
            }
            if (mouseX >= picker2X && mouseX <= picker2X + picker2Size && mouseY >= picker2Y && mouseY <= picker2Y + picker2Size) {
                float nx = (float) (mouseX - picker2X) / picker2Size;
                float ny = (float) (mouseY - picker2Y) / picker2Size;
                draggingPicker2 = true;
                picker2CursorX = Math.max(0, Math.min(1, nx));
                picker2CursorY = Math.max(0, Math.min(1, ny));
                selectedColor2 = ColorUtil.getPixelColor(ResourceProvider.color_image, picker2CursorX, picker2CursorY);
                return true;
            }

            int addButtonX = pickerPanelX + 75;
            int addButtonY = pickerPanelY + 164;
            int addButtonWidth = 110;
            int addButtonHeight = 18;

            if (mouseX >= addButtonX && mouseX <= addButtonX + addButtonWidth && mouseY >= addButtonY && mouseY <= addButtonY + addButtonHeight) {
                String baseName = "Custom";
                String candidate = baseName;
                int index = 2;

                boolean exists;
                do {
                    exists = false;
                    for (Style s : Manager.STYLE_MANAGER.getStyles()) {
                        if (s.name.equals(candidate)) {
                            exists = true;
                            candidate = baseName + "-" + index++;
                            break;
                        }
                    }
                } while (exists);

                int[] colors = {selectedColor1, selectedColor2};
                Style newStyle = new Style(candidate, colors);
                Manager.STYLE_MANAGER.addCustomTheme(candidate, selectedColor1, selectedColor2);
                Manager.STYLE_MANAGER.setTheme(newStyle);
                colorPickerOpen = false;
                return true;
            }
        }

        if (mouseX >= searchX && mouseX <= searchX + searchWidth && mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT
                && !(mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight)) {
            searchState.focused = true;
            searchState.cursorPosition = searchState.text.length();
            return true;
        } else {
            searchState.focused = false;
        }
        if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth && mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
            themeMenu = !themeMenu;
            themeMenuTarget = themeMenu ? 1f : 0f;
        }

        if (themeMenu) {
            int themeWidth = getThemeWidth();
            int themeX = getThemeX(themeWidth);
            int themeY = getThemeY();
            int circleSize = THEME_HEIGHT - 5;
            int padding = 5;
            int totalThemes = Manager.STYLE_MANAGER.getStyles().size() + 1;
            float maxScroll = Math.max(0, (totalThemes - VISIBLE_THEMES) * (circleSize + padding));
            if (themeScrollTarget > 0 && mouseX >= themeX - 15 && mouseX <= themeX - 5 && mouseY >= themeY && mouseY <= themeY + THEME_HEIGHT) {
                themeScrollTarget -= (circleSize + padding) * 3;
                return true;
            }
            if (themeScrollTarget < maxScroll && mouseX >= themeX + themeWidth + 5 && mouseX <= themeX + themeWidth + 15 && mouseY >= themeY && mouseY <= themeY + THEME_HEIGHT) {
                themeScrollTarget += (circleSize + padding) * 3;
                return true;
            }

            float currentX = themeX + padding - themeScrollTarget;
            int centerY = themeY + (THEME_HEIGHT - circleSize) / 2;
            int visibleAreaEndX = themeX + themeWidth;


            if (currentX + circleSize >= themeX && currentX <= visibleAreaEndX) {
                if (mouseX >= currentX && mouseX <= currentX + circleSize && mouseY >= centerY && mouseY <= centerY + circleSize) {
                    colorPickerOpen = !colorPickerOpen;
                    return true;
                }
            }
            currentX += circleSize + padding;

            if (button == 0) {
                for (Style style : Manager.STYLE_MANAGER.getStyles()) {
                    if (currentX + circleSize < themeX || currentX > visibleAreaEndX) {
                        currentX += circleSize + padding;
                        continue;
                    }
                    if (mouseX >= currentX && mouseX <= currentX + circleSize && mouseY >= centerY && mouseY <= centerY + circleSize) {
                        Manager.STYLE_MANAGER.setTheme(style);
                        return true;
                    }
                    currentX += circleSize + padding;
                }
            }

            if (button == 1) {
                currentX = themeX + padding + circleSize + padding - themeScrollTarget;
                for (Style style : new ArrayList<>(Manager.STYLE_MANAGER.getStyles())) {
                    if (currentX + circleSize < themeX || currentX > visibleAreaEndX) {
                        currentX += circleSize + padding;
                        continue;
                    }

                    if (mouseX >= currentX && mouseX <= currentX + circleSize && mouseY >= centerY && mouseY <= centerY + circleSize) {
                        if (style.name.startsWith("Custom")) {
                            Manager.STYLE_MANAGER.removeStyle(style);
                            return true;
                        } else {
                            return true;
                        }
                    }
                    currentX += circleSize + padding;
                }
            }
        }

        if (functionBinding && functions != null) {
            int code = -(button + 2);
            functions.setBindCode(code);
            functionBinding = false;
            functions = null;
            return true;
        }

        for (Type category : renderCategories) {
            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!function.expanded) continue;
                for (Setting setting : function.getSettings()) {
                    if (!setting.isVisible()) continue;
                    if (setting instanceof BindBooleanSetting bindBooleanSetting) {
                        if (bindBooleanSetting.isListeningForBind()) {
                            int code = -(button + 2);
                            bindBooleanSetting.setKey(code);
                            bindBooleanSetting.setListeningForBind(false);
                            return true;
                        }
                    }
                    if (setting instanceof BindSetting bindSetting) {
                        if (bindSetting.isBinding()) {
                            int code = -(button + 2);
                            bindSetting.setKey(code);
                            bindSetting.setBinding(false);
                            return true;
                        }
                    }
                }
            }
        }

        {
            Type category = selectedCategory;
            float offset = scrollOffsets.get(category);

            float currentY = panelY + SCROLL_AREA_Y_OFFSET - offset;

            for (Function function : Manager.FUNCTION_MANAGER.getFunctions(category)) {
                if (!isFunctionVisible(function)) continue;

                int functionHeight = FUNCTION_HEIGHT;

                int fullSettingsHeight = computeSettingsHeight(function);
                float prog = expandProgress.getOrDefault(function, function.expanded ? 1f : 0f);
                int animatedSettingsHeight = (int) Math.round(fullSettingsHeight * prog);

                int totalHeight = functionHeight + animatedSettingsHeight;

                if (currentY + totalHeight < panelY + SCROLL_AREA_Y_OFFSET) {
                    currentY += totalHeight;
                    continue;
                }
                if (currentY > panelY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {
                    break;
                }

                if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= currentY && mouseY <= currentY + functionHeight && mouseY >= panelY + SCROLL_AREA_Y_OFFSET && mouseY <= panelY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {
                    if (button == 0) {
                        function.toggle();
                        return true;
                    } else if (button == 1) {
                        function.expanded = !function.expanded;
                        clampScrollForCategory(category);
                        return true;
                    } else if (button == 2) {
                        functionBinding = true;
                        functions = function;
                        return true;
                    }
                }

                if (animatedSettingsHeight > 0) {
                    float settingY = currentY + functionHeight;
                    int remaining = animatedSettingsHeight;

                    for (Setting setting : function.getSettings()) {
                        if (!setting.isVisible()) continue;

                        int settingHeight = getSettingRendererHeight(setting, PANEL_WIDTH - 20);
                        if (settingHeight <= 0) continue;

                        int visible = Math.max(0, Math.min(settingHeight, remaining));
                        if (visible <= 0) break;

                        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= settingY && mouseY <= settingY + visible && mouseY >= panelY + SCROLL_AREA_Y_OFFSET && mouseY <= panelY + SCROLL_AREA_Y_OFFSET + SCROLL_AREA_HEIGHT) {

                            int settingX = panelX + 10;
                            int settingWidth = PANEL_WIDTH - 20;

                            if (setting instanceof BooleanSetting booleanSetting) {
                                if (booleanSettingRenderer.mouseClicked(booleanSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof BindBooleanSetting bindBooleanSetting) {
                                if (bindbooleanSettingRenderer.mouseClicked(bindBooleanSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof BindSetting bindSetting) {
                                if (bindSettingRenderer.mouseClicked(bindSetting, mouseX, mouseY, button, settingX, (int) settingY  - 2, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof ModeSetting modeSetting) {
                                if (modeSettingRenderer.mouseClicked(modeSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof MultiSetting multiSetting) {
                                if (multiSettingRenderer.mouseClicked(multiSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            } else if (setting instanceof SliderSetting sliderSetting) {
                                if (sliderSettingRenderer.mouseClicked(sliderSetting, mouseX, mouseY, button, settingX, (int) settingY - 2, settingWidth, visible)) {
                                    draggingSlider = sliderSetting;
                                    draggingSliderX = settingX;
                                    draggingSliderWidth = settingWidth;
                                    return true;
                                }
                            } else if (setting instanceof TextSetting textSetting) {
                                if (textSettingRenderer.mouseClicked(textSetting, mouseX, mouseY, button, settingX, (int) settingY, settingWidth, visible)) {
                                    return true;
                                }
                            }
                        }

                        settingY += settingHeight + 1;
                        remaining -= (settingHeight + 1);
                        if (remaining <= 0) break;
                    }
                }

                currentY += totalHeight;
            }
        }

        for (Function function : Manager.FUNCTION_MANAGER.getFunctions(selectedCategory)) {
            if (!function.expanded) continue;
            for (Setting setting : function.getSettings()) {
                if (setting instanceof TextSetting textSetting) textSetting.setFocused(false);
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
    private void renderExpiryText(DrawContext context) {
        String text = Manager.USER_PROFILE.getExpiry();
        int margin = 5;
        int textHeight = (int) FontUtils.durman[18].getHeight();
        int screenHeight = mc.getWindow().getScaledHeight();

        FontUtils.durman[18].drawLeftAligned(context.getMatrices(), "Окончание - " + text, margin + 4, screenHeight - textHeight - margin, Color.WHITE.getRGB());
    }

    private boolean isFunctionVisible(Function function) {
        String searchTextLower = searchState.text.toLowerCase();
        return searchTextLower.isEmpty() || function.name.toLowerCase().contains(searchTextLower) || function.keywords.toLowerCase().contains(searchTextLower);
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {

    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int computeSettingsHeight(Function f) {
        int settingsHeight = 0;
        for (Setting setting : f.getSettings()) {
            if (!setting.isVisible()) continue;
            if (setting instanceof BooleanSetting) {
                settingsHeight += booleanSettingRenderer.getHeight() + 1;
            } else if (setting instanceof BindBooleanSetting) {
                settingsHeight += bindbooleanSettingRenderer.getHeight() + 1;
            } else if (setting instanceof BindSetting) {
                settingsHeight += bindSettingRenderer.getHeight() + 1;
            } else if (setting instanceof ModeSetting) {
                settingsHeight += modeSettingRenderer.getHeight((ModeSetting) setting, PANEL_WIDTH - 20) + 2;
            } else if (setting instanceof MultiSetting) {
                settingsHeight += multiSettingRenderer.getHeight((MultiSetting) setting, PANEL_WIDTH - 20) + 2;
            } else if (setting instanceof SliderSetting) {
                settingsHeight += sliderSettingRenderer.getHeight() + 1;
            } else if (setting instanceof TextSetting) {
                settingsHeight += textSettingRenderer.getHeight() + 1;
            }
        }
        return Math.max(0, settingsHeight);
    }

    private int getSettingRendererHeight(Setting setting, int width) {
        if (!setting.isVisible()) return 0;
        if (setting instanceof BooleanSetting) {
            return booleanSettingRenderer.getHeight();
        } else if (setting instanceof BindBooleanSetting) {
            return bindbooleanSettingRenderer.getHeight();
        } else if (setting instanceof BindSetting) {
            return bindSettingRenderer.getHeight();
        } else if (setting instanceof ModeSetting modeSetting) {
            return modeSettingRenderer.getHeight(modeSetting, width);
        } else if (setting instanceof MultiSetting multiSetting) {
            return multiSettingRenderer.getHeight(multiSetting, width);
        } else if (setting instanceof SliderSetting) {
            return sliderSettingRenderer.getHeight();
        } else if (setting instanceof TextSetting) {
            return textSettingRenderer.getHeight();
        }
        return 0;
    }

    private void clampScrollForCategory(Type category) {
        int maxScroll = calculateMaxScroll(category);
        float clampedTarget = MathHelper.clamp(scrollTargets.get(category), 0f, (float) maxScroll);
        float clampedOffset = MathHelper.clamp(scrollOffsets.get(category), 0f, (float) maxScroll);
        scrollTargets.put(category, clampedTarget);
        scrollOffsets.put(category, clampedOffset);
    }
}