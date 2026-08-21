package ru.levin.modules.render;

import com.google.common.collect.Lists;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.StatusEffectSpriteManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.joml.Vector4i;
import ru.levin.LupaWare;
import ru.levin.manager.themeManager.StyleManager;
import ru.levin.mixin.iface.ItemCooldownEntryAccessor;
import ru.levin.mixin.iface.ItemCooldownManagerAccessor;
import ru.levin.modules.setting.*;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.dragManager.Dragging;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.animations.Animation;
import ru.levin.util.animations.impl.EaseBackIn;
import ru.levin.util.color.ColorUtil;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.math.MathUtil;
import ru.levin.util.player.ServerUtil;
import ru.levin.util.render.ColorRGBA;
import ru.levin.util.render.RenderAddon;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.LupaWareTheme;
import ru.levin.util.render.Scissor;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static ru.levin.util.color.ColorUtil.hud_color;
import static ru.levin.util.render.RenderUtil.*;

@SuppressWarnings("All")
@FunctionAnnotation(name = "HUD", desc = "Интерфейс клиента", type = Type.Render)
public class HUD extends Function {
    public final MultiSetting setting = new MultiSetting(
            "Элементы",
            Arrays.asList("WaterMark", "TargetHUD", "KeyBinds", "StaffList", "PotionHUD", "ItemCoolDownHUD", "Coordinates / TPS", "Speed", "ArmorHUD", "Notifications"),
            new String[]{"WaterMark", "TargetHUD", "KeyBinds", "StaffList", "PotionHUD", "ItemCoolDownHUD", "Coordinates / TPS", "Speed", "ArmorHUD", "Notifications"});


    private final ModeSetting hudColor = new ModeSetting("Цвет худа","Обычный","Обычный","Зависит от темы");
    private final ModeSetting gradientType = new ModeSetting(() -> hudColor.is("Зависит от темы"),"Тип градиента", "Слева направо", "Слева направо", "Справа налево");

    private final SliderSetting customAlpha = new SliderSetting("Прозрачность", 180, 170, 255, 5);
    private final BooleanSetting visibleCrosshair = new BooleanSetting("Показывать TargetHUD при навидении", false, "показывает таргетхуд при навидении на игрока", () -> setting.get("TargetHUD"));
    private final BooleanSetting blur = new BooleanSetting("Размытие", false, "Рендерит размытие на все элементы худа");
    private final SliderSetting roundingSilaSanya = new SliderSetting("Закругление головы", 2f, 0f, 12f, 1f);
    private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{3,16}$");
    private static final Pattern PREFIX_MATCHES = Pattern.compile(".*(mod|мод|adm|адм|help|хелп|curat|курат|own|овн|dev|supp|сапп|yt|ют|сотруд).*", Pattern.CASE_INSENSITIVE);

    private static final Item[] TRACKED_ITEMS = {
            Items.ENDER_PEARL, Items.CHORUS_FRUIT, Items.FIREWORK_ROCKET, Items.SHIELD,
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.TOTEM_OF_UNDYING,
            Items.SNOWBALL, Items.DRIED_KELP, Items.ENDER_EYE, Items.NETHERITE_SCRAP,
            Items.EXPERIENCE_BOTTLE, Items.PHANTOM_MEMBRANE
    };

    private static final Map<Item, String> ITEM_NAMES;
    static {
        Map<Item, String> tmp = new HashMap<>(16);
        tmp.put(Items.ENDER_PEARL, "Эндер-жемчюг");
        tmp.put(Items.CHORUS_FRUIT, "Хорус");
        tmp.put(Items.FIREWORK_ROCKET, "Фейрверк");
        tmp.put(Items.SHIELD, "Щит");
        tmp.put(Items.GOLDEN_APPLE, "Золотое яблоко");
        tmp.put(Items.ENCHANTED_GOLDEN_APPLE, "Чарка");
        tmp.put(Items.TOTEM_OF_UNDYING, "Тотем");
        tmp.put(Items.SNOWBALL, "Снежок");
        tmp.put(Items.DRIED_KELP, "Пласт");
        tmp.put(Items.ENDER_EYE, "Дезориентация");
        tmp.put(Items.NETHERITE_SCRAP, "Трапка");
        tmp.put(Items.EXPERIENCE_BOTTLE, "Пузырёк опыта");
        tmp.put(Items.PHANTOM_MEMBRANE, "Аура");
        ITEM_NAMES = Collections.unmodifiableMap(tmp);
    }

    public HUD() {
        addSettings(setting,hudColor,gradientType, customAlpha, visibleCrosshair, blur,roundingSilaSanya);
    }

    public final Dragging watermarkDrag = LupaWare.getInstance().createDrag(this, "WaterMark", 3, 3);
    public final Dragging targethudDrag = LupaWare.getInstance().createDrag(this, "TargetHUD", 300, 185);
    public final Dragging keybindsDrag = LupaWare.getInstance().createDrag(this, "KeyBindsHUD", 3, 150);
    public final Dragging stafflistDrag = LupaWare.getInstance().createDrag(this, "StaffListHUD", 500, 105);
    public final Dragging itemcooldownDrag = LupaWare.getInstance().createDrag(this, "CoolDownHUD", 3, 205);
    public final Dragging potionhudDrag = LupaWare.getInstance().createDrag(this, "PotionHUD", 700, 105);
    public final Dragging coordinateshudDrag = LupaWare.getInstance().createDrag(this, "CoordinatesHUD", 3, 25);
    public final Dragging bpsDrag = LupaWare.getInstance().createDrag(this, "SpeedHUD", 3, 45);
    public final Dragging armorDrag = LupaWare.getInstance().createDrag(this, "ArmorHUD", 478, 468);

    Animation tHudAnimation = new EaseBackIn(300, 1, 1.5f);
    private final Vector4f corner = new Vector4f(3, 0, 0, 3);
    LivingEntity target = null;
    float health = 0f;
    float health2 = 0f;
    int activeModules = 0;
    private float heightDynamic = 0f;
    private double scale = 0.0D;

    private final List<StaffPlayer> staffPlayers = new ArrayList<>(32);
    private final Set<String> addedPlayers = new HashSet<>(64);

    private String serverAddressCache = "";
    private boolean isLocalServerCache = false;
    @Override
    public void onEvent(Event event) {
        if (mc == null || mc.player == null || mc.world == null) return;

        if (event instanceof EventUpdate) {
            if (setting.get("StaffList")) {
                updateStaffPlayers(mc);
            }
        }
        if (event instanceof EventRender2D eventRender2D) {
            boolean sWaterMark = setting.get("WaterMark");
            boolean sTargetHUD = setting.get("TargetHUD");
            boolean sStaffList = setting.get("StaffList");
            boolean sKeyBinds = setting.get("KeyBinds");
            boolean sItemCooldown = setting.get("ItemCoolDownHUD");
            boolean sPotion = setting.get("PotionHUD");
            boolean sCoordinates = setting.get("Coordinates / TPS");
            boolean sSpeed = setting.get("Speed");
            boolean sArmorHUD = setting.get("ArmorHUD");
            boolean sMediaPlayer = setting.get("MediaPlayer");

            if (sWaterMark) waterMark(eventRender2D);
            if (sTargetHUD) targethud(eventRender2D);
            if (sStaffList) staffList(eventRender2D);
            if (sKeyBinds) keybindHud(eventRender2D);
            if (sItemCooldown) cooldown(eventRender2D);
            if (sPotion) potion(eventRender2D);
            if (sCoordinates) сoordinates(eventRender2D);
            if (sSpeed) speed(eventRender2D);
            if (sArmorHUD) armor(eventRender2D);
        }
    }
    private void armor(EventRender2D eventRender2D) {
        float x = armorDrag.getX();
        float y = armorDrag.getY();
        MatrixStack matrices = eventRender2D.getDrawContext().getMatrices();
        float width = 112;
        float height = 31;
        drawRoundedRect(matrices, x, y, width, height, 3, new Color(0, 0, 0, 195).getRGB());
        FontUtils.sf_bold[8].centeredDraw(matrices, "Armor", x + width / 2f, y + 4, Color.WHITE.getRGB());
        for (int i = 0; i < 4; i++) {
            ItemStack itemStack = mc.player.getInventory().armor.get(3 - i);
            matrices.push();
            matrices.translate(x + 6 + i * 19, y + 13, 0);
            matrices.scale(0.72f, 0.72f, 1f);
            eventRender2D.getDrawContext().drawItem(itemStack, 0, 0, 0);
            eventRender2D.getDrawContext().drawStackOverlay(mc.textRenderer, itemStack, 0, 0);
            matrices.pop();
        }
        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        matrices.push();
        matrices.translate(x + 82, y + 13, 0);
        matrices.scale(0.72f, 0.72f, 1f);
        eventRender2D.getDrawContext().drawItem(totem, 0, 0, 0);
        matrices.pop();
        FontUtils.sf_bold[7].drawLeftAligned(matrices, "Totems: " + mc.player.getInventory().count(Items.TOTEM_OF_UNDYING), x + 96, y + 17, Color.WHITE.getRGB());
        armorDrag.setWidth(width);
        armorDrag.setHeight(height);
    }


    private void speed(EventRender2D eventRender2D) {
        float x = bpsDrag.getX();
        float y = bpsDrag.getY();
        if (mc.player == null) return;
        double dx = mc.player.getX() - mc.player.prevX;
        double dz = mc.player.getZ() - mc.player.prevZ;
        double blocksPerSecond = Math.hypot(dx, dz) * 20.0;
        String text = String.format(Locale.ENGLISH, "Speed: %.2f", blocksPerSecond);
        float width = FontUtils.sf_bold[9].getWidth(text) + 10;
        drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), x, y, width, 15, 3,
                new Color(0, 0, 0, 195).getRGB());
        FontUtils.sf_bold[9].drawLeftAligned(eventRender2D.getDrawContext().getMatrices(), text, x + 4.5f, y + 4, Color.WHITE.getRGB());
        bpsDrag.setWidth(width);
        bpsDrag.setHeight(15);
    }

    private void updateStaffPlayers(MinecraftClient mc) {
        staffPlayers.clear();
        addedPlayers.clear();

        Map<String, PlayerListEntry> nameToEntry = new HashMap<>(mc.player.networkHandler.getPlayerList().size() + 4);
        for (PlayerListEntry e : mc.player.networkHandler.getPlayerList()) {
            if (e.getProfile() != null && e.getProfile().getName() != null) {
                nameToEntry.put(e.getProfile().getName().toLowerCase(Locale.ROOT), e);
            }
        }

        String ourName = mc.player.getName().getString();
        Scoreboard scoreboard = mc.world.getScoreboard();

        for (Team team : scoreboard.getTeams()) {
            Text prefixComponent = team.getPrefix();
            String prefix = prefixComponent.getString();
            String cleanPrefixLower = repairString(prefix).toLowerCase(Locale.ROOT);

            for (String member : team.getPlayerList()) {
                if (member == null || member.equals(ourName) || addedPlayers.contains(member)) continue;
                if (!NAME_PATTERN.matcher(member).matches()) continue;

                PlayerListEntry entry = nameToEntry.get(member.toLowerCase(Locale.ROOT));
                boolean isVanished = (entry == null);

                if (!isVanished) {
                    if (PREFIX_MATCHES.matcher(cleanPrefixLower).matches() || Manager.STAFF_MANAGER.isStaff(member)) {
                        java.util.UUID uuid = entry.getProfile().getId();
                        staffPlayers.add(new StaffPlayer(member, prefixComponent, uuid));
                        addedPlayers.add(member);
                    }
                } else {
                    if (!prefix.isEmpty()) {
                        staffPlayers.add(new StaffPlayer(member, prefixComponent, null));
                        addedPlayers.add(member);
                    }
                }
            }
        }

        if (!staffPlayers.isEmpty()) {
            staffPlayers.sort(Comparator.comparing(StaffPlayer::getName));
        }
    }




    private float potionListHeightDynamic = 0;

    private void potion(EventRender2D eventRender2D) {
        float x = potionhudDrag.getX(), y = potionhudDrag.getY();
        MatrixStack matrices = eventRender2D.getDrawContext().getMatrices();
        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        var font = FontUtils.sf_bold[8];
        float width = 78;
        for (StatusEffectInstance effect : effects) {
            String label = I18n.translate(effect.getEffectType().value().getTranslationKey()) + " " + (effect.getAmplifier() + 1);
            width = Math.max(width, font.getWidth(label) + font.getWidth(formatDuration(effect)) + 31);
        }
        StatusEffectSpriteManager sprites = mc.getStatusEffectSpriteManager();
        float rowY = y;
        for (StatusEffectInstance effect : effects) {
            String label = I18n.translate(effect.getEffectType().value().getTranslationKey()) + " " + (effect.getAmplifier() + 1);
            String duration = formatDuration(effect);
            drawRoundedRect(matrices, x, rowY, width, 21.3f, 3, new Color(0, 0, 0, 195).getRGB());
            Sprite sprite = sprites.getSprite(effect.getEffectType());
            eventRender2D.getDrawContext().drawSpriteStretched(RenderLayer::getGuiTextured, sprite, (int) x + 2, (int) rowY + 4, 12, 12, -1);
            font.drawLeftAligned(matrices, label, x + 22, rowY + 4, Color.WHITE.getRGB());
            font.drawRightAligned(matrices, duration, x + width - 4, rowY + 4, new Color(185, 190, 202, 235).getRGB());
            rowY += 24;
        }
        potionhudDrag.setWidth(width);
        potionhudDrag.setHeight(Math.max(21.3f, effects.size() * 24f - 2.7f));
    }
    private String formatDuration(StatusEffectInstance eff) {
        if (eff.isInfinite() || eff.getDuration() > 18000) {
            return "**:**";
        }
        String raw = StatusEffectUtil.getDurationText(eff, 1.0F, 20.0f).getString();
        return raw.replace("{", "").replace("}", "");
    }

    private float cooldownProgress = 0f;

    private void cooldown(EventRender2D eventRender2D) {
        float x = itemcooldownDrag.getX();
        float y = itemcooldownDrag.getY();
        ItemCooldownManager manager = mc.player.getItemCooldownManager();
        ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) manager;
        int active = 0;
        float longestRemaining = 0f;
        for (Item item : TRACKED_ITEMS) {
            ItemStack stack = new ItemStack(item);
            if (!manager.isCoolingDown(stack)) continue;
            active++;
            Identifier id = manager.getGroup(stack);
            Object rawEntry = accessor.getEntries().get(id);
            if (rawEntry instanceof ItemCooldownEntryAccessor entry) {
                float remaining = Math.max(0f, (entry.getEndTick() - (accessor.getTick() + mc.getRenderTickCounter().getTickDelta(true))) / 20.0f);
                longestRemaining = Math.max(longestRemaining, remaining);
            }
        }
        float target = active == 0 ? 1f : MathHelper.clamp(1f - longestRemaining / 10f, 0f, 1f);
        cooldownProgress = MathUtil.fast(cooldownProgress, target, 10);
        float width = 92f;
        float height = 25f;
        MatrixStack matrices = eventRender2D.getDrawContext().getMatrices();
        drawRoundedRect(matrices, x, y, width, height, 3, new Color(0, 0, 0, 195).getRGB());
        drawRoundedRect(matrices, x + 5, y + 14, 82, 7, 0, new Color(23, 23, 23, 196).getRGB());
        drawRoundedRect(matrices, x + 5, y + 14, 82 * cooldownProgress, 7, 3, LupaWareTheme.GOLD);
        FontUtils.sf_bold[8].drawLeftAligned(matrices, "Cooldowns", x + 5, y + 4, Color.WHITE.getRGB());
        RenderUtil.drawTexture(matrices, "images/hud/cooldown.png", x + width - 17, y + 5, 10, 10, 0, Color.WHITE.getRGB());
        itemcooldownDrag.setWidth(width);
        itemcooldownDrag.setHeight(height);
    }



    private int activeStaff = 0;
    private void staffList(EventRender2D render2D) {
        float x = stafflistDrag.getX(), y = stafflistDrag.getY();
        MatrixStack matrices = render2D.getDrawContext().getMatrices();
        final float width = 100f;
        final float offset = 9f;
        for (StaffPlayer staff : staffPlayers) staff.updateStatus();
        activeStaff = staffPlayers.size();
        float height = 20.5f + Math.max(0, activeStaff * offset - 4f);
        if (activeStaff == 0) height -= 1.5f;
        drawRoundedRect(matrices, x, y, width, height, 3, new Color(0, 0, 0, 195).getRGB());
        FontUtils.sf_bold[8].centeredDraw(matrices, "Staff Active", x + width / 2f, y + 4, Color.WHITE.getRGB());
        int index = 0;
        for (StaffPlayer staff : staffPlayers) {
            String name = staff.getName().substring(0, Math.min(staff.getName().length(), 10));
            float rowY = y + 16.7f + index * offset;
            FontUtils.sf_bold[7].drawLeftAligned(matrices, name, x + 4, rowY, Color.WHITE.getRGB());
            switch (staff.getStatus()) {
                case SPEC -> FontUtils.sf_bold[6].drawLeftAligned(matrices, "Spectator", x + 63.5f, rowY, new Color(231, 52, 52, 255).getRGB());
                case NONE -> FontUtils.sf_bold[6].drawLeftAligned(matrices, "Online", x + 74, rowY, new Color(116, 236, 114, 255).getRGB());
                case NEAR -> RenderUtil.drawRoundedRect(matrices, x + 85.3f, rowY + 1, 5, 5, 2.5f, new Color(116, 236, 114, 255).getRGB());
                case VANISHED -> RenderUtil.drawRoundedRect(matrices, x + 85.3f, rowY + 1, 5, 5, 2.5f, new Color(231, 52, 52, 255).getRGB());
            }
            index++;
        }
        stafflistDrag.setWidth(width);
        stafflistDrag.setHeight(height);
    }
    private float lastHealth = 0.0f;
    private float lastAbsorption = 0.0f;

    private void targethud(EventRender2D render2D) {
        float x = targethudDrag.getX(), y = targethudDrag.getY();
        target = getTarget(target);
        scale = tHudAnimation.getOutput();
        if (scale == 0.0 || target == null) return;
        float healthValue = MathHelper.clamp(ServerUtil.getHealth(target), 0.0F, 1.0F);
        lastHealth = MathUtil.fast(lastHealth, healthValue, 8);
        String name = Manager.FUNCTION_MANAGER.nameProtect.getProtectedName(target.getName().getString());
        if (name.length() > 15) name = name.substring(0, 15) + "…";
        MatrixStack matrices = render2D.getDrawContext().getMatrices();
        matrices.push();
        RenderAddon.sizeAnimation(matrices, x + 104, y + 29, scale);
        int alpha = MathHelper.clamp(customAlpha.get().intValue(), 175, 255);
        drawRoundedRect(matrices, x, y, 208, 58, 13, LupaWareTheme.withAlpha(LupaWareTheme.SURFACE, alpha));
        drawRoundedBorder(matrices, x, y, 208, 58, 13, 0.9f, LupaWareTheme.withAlpha(LupaWareTheme.BORDER, alpha));
        drawRoundedRect(matrices, x, y, 208, 3, 1, LupaWareTheme.withAlpha(LupaWareTheme.MINT, alpha));
        RenderAddon.drawHead(matrices, target, x + 12, y + 15, 30, roundingSilaSanya.get().floatValue());
        FontUtils.sf_bold[14].drawLeftAligned(matrices, name, x + 53, y + 11, Color.WHITE.getRGB());
        FontUtils.sf_medium[9].drawLeftAligned(matrices, "TARGET / HEALTH", x + 53, y + 27, LupaWareTheme.withAlpha(LupaWareTheme.MUTED, alpha));
        drawRoundedRect(matrices, x + 53, y + 43, 129, 5, 3, LupaWareTheme.withAlpha(LupaWareTheme.SURFACE_SOFT, alpha));
        drawRoundedRect(matrices, x + 53, y + 43, 129 * lastHealth, 5, 3, LupaWareTheme.withAlpha(LupaWareTheme.MINT, alpha));
        FontUtils.sf_bold[10].drawRightAligned(matrices, String.format(Locale.ENGLISH, "%.0f HP", lastHealth * 20.0F), x + 192, y + 25, Color.WHITE.getRGB());
        matrices.pop();
        targethudDrag.setWidth(208); targethudDrag.setHeight(58);
    }
    private void waterMark(EventRender2D render2D) {
        float x = watermarkDrag.getX(), y = watermarkDrag.getY();
        MatrixStack matrices = render2D.getDrawContext().getMatrices();
        String title = "LupaWare";
        String fpsText = ClientManager.getFps() + " fps";
        float titleWidth = FontUtils.sf_bold[9].getWidth(title);
        float separatorWidth = FontUtils.sf_bold[9].getWidth("|");
        float fpsWidth = FontUtils.sf_bold[9].getWidth(fpsText);
        float topWidth = titleWidth + separatorWidth + fpsWidth + 17;
        drawRoundedRect(matrices, x, y, topWidth, 17, 3, new Color(0, 0, 0, 195).getRGB());
        FontUtils.sf_bold[9].drawLeftAligned(matrices, title, x + 4.5f, y + 4.5f, Color.WHITE.getRGB());
        FontUtils.sf_bold[9].drawLeftAligned(matrices, "|", x + 4.5f + titleWidth + 3, y + 4.5f, new Color(128, 128, 128, 255).getRGB());
        FontUtils.sf_bold[9].drawLeftAligned(matrices, fpsText, x + 4.5f + titleWidth + separatorWidth + 6, y + 4.5f, Color.WHITE.getRGB());
        String ticksText = "TPS: " + Math.max(0, Math.round(ClientManager.getTPS()));
        float ticksWidth = FontUtils.sf_bold[8].getWidth(ticksText) + 8;
        drawRoundedRect(matrices, x, y + 20, ticksWidth, 15, 3, new Color(0, 0, 0, 195).getRGB());
        FontUtils.sf_bold[8].drawLeftAligned(matrices, ticksText, x + 4, y + 24.2f, Color.WHITE.getRGB());
        watermarkDrag.setWidth(Math.max(topWidth, ticksWidth));
        watermarkDrag.setHeight(35);
    }
    private int applyHudAlpha(int color, int alpha) {
        Color c = new Color(color, true);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(alpha, 0, 255)).getRGB();
    }

    private void сoordinates(EventRender2D render2D) {
        MatrixStack matrices = render2D.getDrawContext().getMatrices();
        float x = coordinateshudDrag.getX(), y = coordinateshudDrag.getY();
        String coords = String.format(Locale.ENGLISH, "Coords: %d %d %d", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
        float width = FontUtils.sf_bold[9].getWidth(coords) + 10;
        drawRoundedRect(matrices, x, y, width, 15, 3, new Color(0, 0, 0, 195).getRGB());
        FontUtils.sf_bold[9].drawLeftAligned(matrices, coords, x + 4.5f, y + 4, Color.WHITE.getRGB());
        coordinateshudDrag.setWidth(width);
        coordinateshudDrag.setHeight(15);
    }
    private float keybindsHeightDynamic = 0;

    private void keybindHud(EventRender2D render2D) {
        float x = keybindsDrag.getX(), y = keybindsDrag.getY();
        MatrixStack matrices = render2D.getDrawContext().getMatrices();
        var font = FontUtils.sf_bold[8];
        int count = 0;
        float width = 75;
        for (Function f : Manager.FUNCTION_MANAGER.getFunctions()) {
            if (f.bind != 0 && f.state) {
                count++;
                width = Math.max(width, font.getWidth(f.name) + font.getWidth(getShortKey(ClientManager.getKey(f.bind))) + 14);
            }
            for (Setting setting : f.getSettings()) {
                if (setting instanceof BindBooleanSetting b && b.isVisible() && b.getBindKey() != 0 && b.get()) {
                    count++;
                    width = Math.max(width, font.getWidth(b.getName()) + font.getWidth(getShortKey(ClientManager.getKey(b.getBindKey()))) + 14);
                }
            }
        }
        activeModules = count;
        keybindsHeightDynamic = MathUtil.fast(keybindsHeightDynamic, count * 10, 15);
        float height = 18 + keybindsHeightDynamic;
        drawRoundedRect(matrices, x, y, width, height, 3, new Color(0, 0, 0, 195).getRGB());
        FontUtils.sf_bold[8].centeredDraw(matrices, "Key Binds", x + width / 2f, y + 5, Color.WHITE.getRGB());
        float rowY = y + 16;
        for (Function f : Manager.FUNCTION_MANAGER.getFunctions()) {
            if (f.bind != 0 && f.state) rowY = drawBindRow(matrices, font, f.name, getShortKey(ClientManager.getKey(f.bind)), x, rowY, width, Color.WHITE.getRGB(), 4);
            for (Setting setting : f.getSettings()) if (setting instanceof BindBooleanSetting b && b.isVisible() && b.getBindKey() != 0 && b.get()) rowY = drawBindRow(matrices, font, b.getName(), getShortKey(ClientManager.getKey(b.getBindKey())), x, rowY, width, Color.WHITE.getRGB(), 4);
        }
        keybindsDrag.setWidth(width); keybindsDrag.setHeight(height);
    }
    private float drawBindRow(MatrixStack matrices, ru.levin.manager.fontManager.RenderFonts font, String name, String key, float x, float y, float width, int accent, int padding) {
        String trimmed = name.length() > 12 ? name.substring(0, 12) + ".." : name;
        font.drawLeftAligned(matrices, trimmed, x + padding, y + 2, LupaWareTheme.WHITE);
        font.drawRightAligned(matrices, key, x + width - padding, y + 2, LupaWareTheme.withAlpha(LupaWareTheme.MUTED, 230));
        return y + 10;
    }

    private String getShortKey(String key) {
        if (key == null) return "";
        String bindText = key.toUpperCase();
        return bindText.length() > 6 ? bindText.substring(0, 6) + "…" : bindText;
    }


    public LivingEntity getTarget(LivingEntity nullTarget) {
        LivingEntity target = nullTarget;

        if (Manager.FUNCTION_MANAGER.attackAura.target instanceof LivingEntity) {
            target = (LivingEntity) Manager.FUNCTION_MANAGER.attackAura.target;
            tHudAnimation.setDirection(Direction.AxisDirection.POSITIVE);
        }
        else if (visibleCrosshair.get() && mc.crosshairTarget instanceof EntityHitResult) {
            Entity aimed = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (aimed instanceof LivingEntity) {
                target = (LivingEntity) aimed;
                tHudAnimation.setDirection(Direction.AxisDirection.POSITIVE);
            } else {
                tHudAnimation.setDirection(Direction.AxisDirection.NEGATIVE);
            }
        }
        else if (mc.currentScreen instanceof ChatScreen) {
            target = mc.player;
            tHudAnimation.setDirection(Direction.AxisDirection.POSITIVE);
        }
        else {
            tHudAnimation.setDirection(Direction.AxisDirection.NEGATIVE);
        }

        return target;
    }

    private String repairString(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= 65281 && c <= 65374) {
                sb.append((char) (c - 65248));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public void onDisable() {
        staffPlayers.clear();
        addedPlayers.clear();
    }
    public class StaffPlayer {
        @Getter
        private final String name;
        @Getter
        private final Text prefix;
        @Getter
        private Status status;
        @Getter
        private final long joinTime;
        @Getter
        private GameMode gameMode;
        @Getter
        private boolean isOnPlayerList;
        @Getter
        private final java.util.UUID uuid;

        public StaffPlayer(String name, Text prefix, @Nullable java.util.UUID uuid) {
            this.name = name;
            this.prefix = prefix;
            this.uuid = uuid;
            this.joinTime = System.currentTimeMillis();
            updateStatus();
        }

        public void updateStatus() {
            if (mc == null || mc.world == null || mc.getNetworkHandler() == null) {
                this.status = Status.VANISHED;
                this.isOnPlayerList = false;
                this.gameMode = null;
                return;
            }

            PlayerListEntry entry = null;
            if (this.uuid != null) {
                for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                    if (this.uuid.equals(e.getProfile().getId())) {
                        entry = e;
                        break;
                    }
                }
            } else {
                for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                    if (e.getProfile() != null && e.getProfile().getName() != null && e.getProfile().getName().equalsIgnoreCase(this.name)) {
                        entry = e;
                        break;
                    }
                }
            }

            this.isOnPlayerList = (entry != null);
            this.gameMode = (entry != null) ? entry.getGameMode() : null;

            boolean entityLoaded = false;
            if (entry != null) {
                var loaded = mc.world.getPlayerByUuid(entry.getProfile().getId());
                entityLoaded = (loaded != null);
            }

            if (!this.isOnPlayerList) {
                this.status = Status.VANISHED;
            } else if (this.gameMode == GameMode.SPECTATOR) {
                this.status = Status.SPEC;
            } else if (entityLoaded) {
                this.status = Status.NEAR;
            } else {
                this.status = Status.NONE;
            }
        }

        public enum Status {
            NONE("§2[ON]"),
            NEAR("§6[N]"),
            SPEC("§e[GM3]"),
            VANISHED("§c[V]");

            @Getter
            final String string;

            Status(String string) {
                this.string = string;
            }
        }
    }

    private String processName(String original) {
        if (original.length() > 12 || original.matches(".*\\d.*")) {
            return original.substring(0, Math.min(9, original.length())) + "...";
        }
        return original;
    }

    private int getStatusColor(StaffPlayer.Status status) {
        switch(status) {
            case NEAR: return new Color(220, 220, 220).getRGB();
            case SPEC: return new Color(180, 180, 180).getRGB();
            case VANISHED: return new Color(110, 110, 110).getRGB();
            default: return Color.WHITE.getRGB();
        }
    }
    private String formatCooldownTime(float seconds) {
        int totalSeconds = (int) Math.floor(seconds);
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;

        if (minutes > 0) {
            if (secs > 0) {
                return String.format("%dм %02dс", minutes, secs);
            } else {
                return String.format("%dм", minutes);
            }
        } else {
            return String.format("%dс", secs);
        }
    }
}