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
            Arrays.asList("WaterMark", "TargetHUD", "KeyBinds", "StaffList", "PotionHUD", "ItemCoolDownHUD", "Coordinates / TPS","ArmorHUD", "Notifications"),
            new String[]{"WaterMark", "TargetHUD", "KeyBinds", "StaffList", "PotionHUD", "ItemCoolDownHUD", "Coordinates / TPS","ArmorHUD", "Notifications"});


    private final ModeSetting hudColor = new ModeSetting("Цвет худа","Обычный","Обычный","Зависит от темы");
    private final ModeSetting gradientType = new ModeSetting(() -> hudColor.is("Зависит от темы"),"Тип градиента", "Слева направо", "Слева направо", "Справа налево");

    private final SliderSetting customAlpha = new SliderSetting("Прозрачность", 120, 120, 255, 5);
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

    public final Dragging watermarkDrag = LupaWare.getInstance().createDrag(this, "WaterMark", 10, 10);
    public final Dragging targethudDrag = LupaWare.getInstance().createDrag(this, "TargetHUD", 10, 45);
    public final Dragging keybindsDrag = LupaWare.getInstance().createDrag(this, "KeyBindsHUD", 10, 95);
    public final Dragging stafflistDrag = LupaWare.getInstance().createDrag(this, "StaffListHUD", 10, 128);
    public final Dragging itemcooldownDrag = LupaWare.getInstance().createDrag(this, "CoolDownHUD", 10, 165);
    public final Dragging potionhudDrag = LupaWare.getInstance().createDrag(this, "PotionHUD", 10, 198);
    public final Dragging coordinateshudDrag = LupaWare.getInstance().createDrag(this, "CoordinatesHUD", 10, 198);
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
            boolean sArmorHUD = setting.get("ArmorHUD");
            boolean sMediaPlayer = setting.get("MediaPlayer");

            if (sWaterMark) waterMark(eventRender2D);
            if (sTargetHUD) targethud(eventRender2D);
            if (sStaffList) staffList(eventRender2D);
            if (sKeyBinds) keybindHud(eventRender2D);
            if (sItemCooldown) cooldown(eventRender2D);
            if (sPotion) potion(eventRender2D);
            if (sCoordinates) сoordinates(eventRender2D);
            if (sArmorHUD) armor(eventRender2D);
        }
    }
    private void armor(EventRender2D eventRender2D) {
        float x = armorDrag.getX();
        float y = armorDrag.getY();
        int armorCount = 0;
        for (int i = 0; i < 4; i++) {
            if (!mc.player.getInventory().armor.get(i).isEmpty()) armorCount++;
        }

        int width = armorCount > 0 ? 20 * armorCount : 35;
        armorDrag.setWidth(width);
        armorDrag.setHeight(18);

        float startX = x + width - 20;
        for (int i = 0; i < 4; i++) {
            ItemStack itemStack = mc.player.getInventory().armor.get(i);
            if (!itemStack.isEmpty()) {
                eventRender2D.getDrawContext().getMatrices().push();
                eventRender2D.getDrawContext().getMatrices().translate(startX, y + 0.2f, 0);
                eventRender2D.getDrawContext().getMatrices().scale(1, 1, 1);
                eventRender2D.getDrawContext().drawItem(itemStack, 0, 0, 0);
                eventRender2D.getDrawContext().drawStackOverlay(mc.textRenderer, itemStack, 0, 0);
                eventRender2D.getDrawContext().getMatrices().pop();
                startX -= 20;
            }
        }
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
        float posX = potionhudDrag.getX();
        float posY = potionhudDrag.getY();
        float time = (System.currentTimeMillis() % 2000L) / 2000f;
        float pulse = (float)(Math.sin(time * Math.PI * 2) * 0.1f + 0.9f);

        int headerHeight = 18;
        int padding = 5;
        int lineHeight = 10;

        List<StatusEffectInstance> activeEffects = new ArrayList<>(mc.player.getStatusEffects());
        float maxWidth = 100;
        List<Runnable> list = Lists.newArrayListWithCapacity(activeEffects.size());

        float maxDurationWidth = 0;
        for (StatusEffectInstance eff : activeEffects) {
            String name = I18n.translate(eff.getEffectType().value().getTranslationKey());
            int level = eff.getAmplifier() + 1;
            String levelStr = (level > 1) ? " " + level : "";
            String displayName = name + levelStr;
            float nameWidth = FontUtils.durman[13].getWidth(displayName);

            String duration = formatDuration(eff);
            float durationWidth = FontUtils.durman[13].getWidth(duration);
            maxDurationWidth = Math.max(maxDurationWidth, durationWidth);

            float totalWidth = padding * 2 + 25 + nameWidth + padding + durationWidth;
            if (totalWidth > maxWidth) maxWidth = totalWidth;
        }

        float listHeightTarget = activeEffects.size() * lineHeight;
        potionListHeightDynamic = MathUtil.fast(potionListHeightDynamic, listHeightTarget, 15);
        float totalHeight = headerHeight + potionListHeightDynamic;
        int alpha = customAlpha.get().intValue();

        if (alpha <= 240) {
            if (blur.get()) {
                drawBlur(eventRender2D.getDrawContext().getMatrices(), posX, posY + headerHeight - 1, maxWidth, potionListHeightDynamic + 6, new Vector4f(0, 3, 3, 0), 12, Color.white.getRGB());
            }
        }

        StyleManager theme = Manager.STYLE_MANAGER;
        Color upColor = new Color(theme.getFirstColor());
        Color downColor = new Color(theme.getSecondColor());

        if (hudColor.is("Обычный")) {
            drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), posX, posY, maxWidth, headerHeight + 1, new Vector4f(3, 0, 0, 3), hud_color);
        } else {
            int left   = ColorUtil.gradient(10,   90, upColor.getRGB(), downColor.getRGB());
            int right  = ColorUtil.gradient(10,    0, upColor.getRGB(), downColor.getRGB());
            int top    = ColorUtil.gradient(10,  180, upColor.getRGB(), downColor.getRGB());
            int bottom = ColorUtil.gradient(10,  270, upColor.getRGB(), downColor.getRGB());
            boolean leftToRight = gradientType.is("Слева направо");
            int c1 = leftToRight ? hud_color : left;
            int c2 = leftToRight ? hud_color : right;
            int c3 = leftToRight ? right     : hud_color;
            int c4 = leftToRight ? left      : hud_color;
            rectRGB(eventRender2D.getDrawContext().getMatrices(), posX, posY, maxWidth, headerHeight + 1, corner, c1, c2, c3, c4);
        }

        RenderUtil.drawTexture(eventRender2D.getDrawContext().getMatrices(), "images/hud/potion.png", posX + maxWidth - 16, posY + 4.5f, 11, 11, 0, Color.white.getRGB());

        FontUtils.durman[15].drawLeftAligned(eventRender2D.getDrawContext().getMatrices(), "Effects", posX + 10, posY + 5f, -1);

        drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), posX, posY + headerHeight - 1, maxWidth, potionListHeightDynamic + 6, new Vector4f(0, 3, 3, 0), new Color(18, 18, 18, alpha).getRGB());

        Scissor.push();
        Scissor.setFromComponentCoordinates(posX, posY, maxWidth, (headerHeight + potionListHeightDynamic + padding / 2.0F + 5));

        float yOffset = posY + headerHeight + padding - 1;
        StatusEffectSpriteManager spriteManager = mc.getStatusEffectSpriteManager();

        for (StatusEffectInstance eff : activeEffects) {
            StatusEffect effect = eff.getEffectType().value();
            RegistryEntry<StatusEffect> holder = eff.getEffectType();
            String name = I18n.translate(effect.getTranslationKey());
            int level = eff.getAmplifier() + 1;
            String levelStr = (level > 1) ? " " + level : "";
            String displayName = name + levelStr;
            String duration = formatDuration(eff);

            int ticksLeft = eff.getDuration();
            int colorAlpha;
            if (ticksLeft <= 60 && ticksLeft > 0) {
                colorAlpha = (int) ((Math.sin(System.currentTimeMillis() / 200.0) * 80 + 128));
            } else {
                colorAlpha = 255;
            }
            int color = ColorUtil.rgba(255, 255, 255, colorAlpha);
            Sprite texture = spriteManager.getSprite(holder);

            float finalYOffset = yOffset;
            list.add(() -> {eventRender2D.getDrawContext().drawSpriteStretched(RenderLayer::getGuiTextured, texture, (int) (posX + padding - 1.5f), (int) finalYOffset - 1, 9, 9, -1);});

            RenderUtil.drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), posX + padding + 9, finalYOffset - 1f, 1.2f, 9, 0, new Color(220, 220, 220, 190).getRGB());

            FontUtils.durman[13].drawLeftAligned(eventRender2D.getDrawContext().getMatrices(), displayName, posX + padding + 14f, yOffset - 1f, color);

            RenderUtil.drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), posX + maxWidth - maxDurationWidth - padding - 7, yOffset - 1, 8 + maxDurationWidth, 10, 1, hud_color);

            FontUtils.durman[13].drawLeftAligned(eventRender2D.getDrawContext().getMatrices(), duration, posX + maxWidth - maxDurationWidth - padding - 3, yOffset - 0.3f, color);

            yOffset += lineHeight;
        }

        Scissor.unset();
        Scissor.pop();
        list.forEach(Runnable::run);

        potionhudDrag.setWidth(maxWidth);
        potionhudDrag.setHeight(totalHeight + 5);
    }



    private String formatDuration(StatusEffectInstance eff) {
        if (eff.isInfinite() || eff.getDuration() > 18000) {
            return "**:**";
        }
        String raw = StatusEffectUtil.getDurationText(eff, 1.0F, 20.0f).getString();
        return raw.replace("{", "").replace("}", "");
    }

    private float cooldownListHeightDynamic = 0;

    private void cooldown(EventRender2D eventRender2D) {
        float posX = itemcooldownDrag.getX();
        float posY = itemcooldownDrag.getY();
        int headerHeight = 18;
        int padding = 5;
        int lineHeight = 10;

        List<Item> activeItems = new ArrayList<>();
        float maxWidth = 100;

        ItemCooldownManager manager = mc.player.getItemCooldownManager();
        ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) manager;

        for (Item item : TRACKED_ITEMS) {
            ItemStack stack = new ItemStack(item);
            if (manager.isCoolingDown(stack)) {
                activeItems.add(item);

                String itemName = ITEM_NAMES.getOrDefault(item, stack.getName().getString());

                Identifier id = manager.getGroup(stack);
                Object rawEntry = accessor.getEntries().get(id);

                float remainingSeconds = 0f;
                if (rawEntry instanceof ItemCooldownEntryAccessor entry) {
                    int end = entry.getEndTick();
                    int current = accessor.getTick();
                    float remainingTicks = end - (current + mc.getRenderTickCounter().getTickDelta(true));
                    remainingSeconds = Math.max(0f, remainingTicks / 20.0f);
                }

                String timeLeft = formatCooldownTime(remainingSeconds);

                float nameWidth = FontUtils.durman[13].getWidth(itemName);
                float timeWidth = FontUtils.durman[13].getWidth(timeLeft);
                float totalWidth = padding * 2 + 25 + nameWidth + padding + timeWidth;

                if (totalWidth > maxWidth) maxWidth = totalWidth;
            }
        }

        float listHeightTarget = activeItems.size() * lineHeight;
        cooldownListHeightDynamic = MathUtil.fast(cooldownListHeightDynamic, listHeightTarget, 15);
        float totalHeight = headerHeight + cooldownListHeightDynamic;

        int alpha = customAlpha.get().intValue();
        if (alpha <= 240) {
            if (blur.get()) {
                drawBlur(eventRender2D.getDrawContext().getMatrices(), posX, posY + headerHeight - 1, maxWidth, cooldownListHeightDynamic + 6, new Vector4f(0, 3, 3, 0), 12, Color.white.getRGB());
            }
        }

        StyleManager theme = Manager.STYLE_MANAGER;
        Color upColor = new Color(theme.getFirstColor());
        Color downColor = new Color(theme.getSecondColor());

        if (hudColor.is("Обычный")) {
            drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), posX, posY, maxWidth, headerHeight + 1, new Vector4f(3, 0, 0, 3), hud_color);
        } else {
            int left   = ColorUtil.gradient(10,   90, upColor.getRGB(), downColor.getRGB());
            int right  = ColorUtil.gradient(10,    0, upColor.getRGB(), downColor.getRGB());
            int top    = ColorUtil.gradient(10,  180, upColor.getRGB(), downColor.getRGB());
            int bottom = ColorUtil.gradient(10,  270, upColor.getRGB(), downColor.getRGB());
            boolean leftToRight = gradientType.is("Слева направо");
            int c1 = leftToRight ? hud_color : left;
            int c2 = leftToRight ? hud_color : right;
            int c3 = leftToRight ? right     : hud_color;
            int c4 = leftToRight ? left      : hud_color;
            rectRGB(eventRender2D.getDrawContext().getMatrices(), posX, posY, maxWidth, headerHeight + 1, corner, c1, c2, c3, c4);
        }

        RenderUtil.drawTexture(eventRender2D.getDrawContext().getMatrices(), "images/hud/cooldown.png", posX + maxWidth - 17, posY + 4.5f, 11, 11, 0, Color.white.getRGB());

        FontUtils.durman[15].drawLeftAligned(eventRender2D.getDrawContext().getMatrices(), "Cooldowns", posX + 10, posY + 5f, -1);

        drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), posX, posY + headerHeight - 1, maxWidth, cooldownListHeightDynamic + 6, new Vector4f(0, 3, 3, 0), new Color(18, 18, 18, alpha).getRGB());

        Scissor.push();
        Scissor.setFromComponentCoordinates(posX, posY, maxWidth, (headerHeight + cooldownListHeightDynamic + padding / 2.0F + 5));

        float yOffset = posY + headerHeight + padding - 1;
        for (Item item : activeItems) {
            ItemStack stack = item.getDefaultStack();
            String itemName = ITEM_NAMES.getOrDefault(item, stack.getName().getString());

            Identifier id = manager.getGroup(stack);
            Object rawEntry = accessor.getEntries().get(id);

            float remainingSeconds = 0f;
            if (rawEntry instanceof ItemCooldownEntryAccessor entry) {
                int end = entry.getEndTick();
                int current = accessor.getTick();
                float remainingTicks = end - (current + mc.getRenderTickCounter().getTickDelta(true));
                remainingSeconds = Math.max(0f, remainingTicks / 20.0f);
            }

            String timeLeft = formatCooldownTime(remainingSeconds);

            RenderAddon.renderItem(eventRender2D.getDrawContext(), stack, posX + padding - 1.5f, yOffset - 1f, 0.6f,false);

            RenderUtil.drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), posX + padding + 10, yOffset - 0.5f, 1.2f, 9, 0, new Color(220, 220, 220, 190).getRGB());

            FontUtils.durman[13].drawLeftAligned(eventRender2D.getDrawContext().getMatrices(), itemName, posX + padding + 14f, yOffset - 0.3f, -1);

            float timeWidth = FontUtils.durman[13].getWidth(timeLeft);
            RenderUtil.drawRoundedRect(eventRender2D.getDrawContext().getMatrices(), posX + maxWidth - timeWidth - padding - 5, yOffset - 1, 6 + timeWidth, 10, 1, hud_color);

            FontUtils.durman[13].drawLeftAligned(eventRender2D.getDrawContext().getMatrices(), timeLeft, posX + maxWidth - timeWidth - padding - 2, yOffset - 0.3f, -1);

            yOffset += lineHeight;
        }

        Scissor.unset();
        Scissor.pop();

        itemcooldownDrag.setWidth(maxWidth);
        itemcooldownDrag.setHeight(totalHeight + 5);
    }



    private int activeStaff = 0;
    private float hDynam = 0;
    private float widthDynamic = 0;
    private float nameWidth = 0;

    private void staffList(EventRender2D render2D) {
        float posX = stafflistDrag.getX();
        float posY = stafflistDrag.getY();

        var fontBig = FontUtils.durman[15];
        var fontSmall = FontUtils.durman[13];

        int headerHeight = 18;
        int padding = 4;
        int offset = 10;
        float width = Math.max(nameWidth + 60, 100);
        int index = 0;
        nameWidth = 0;

        hDynam = MathUtil.fast(this.hDynam, activeStaff * offset, 15);
        widthDynamic = MathUtil.fast(this.widthDynamic, width, 8);

        int alpha = customAlpha.get().intValue();
        if (alpha <= 240) {
            if (blur.get()) {
                drawBlur(render2D.getDrawContext().getMatrices(), posX, posY + headerHeight - 1, widthDynamic, hDynam + 6, new Vector4f(0, 3, 3, 0), 12, Color.white.getRGB());
            }
        }
        StyleManager theme = Manager.STYLE_MANAGER;
        Color upColor = new Color(theme.getFirstColor());
        Color downColor = new Color(theme.getSecondColor());

        if (hudColor.is("Обычный")) {
            drawRoundedRect(render2D.getDrawContext().getMatrices(), posX, posY, widthDynamic, headerHeight + 1, corner, hud_color);
        } else {
            int left   = ColorUtil.gradient(10,   90, upColor.getRGB(), downColor.getRGB());
            int right  = ColorUtil.gradient(10,    0, upColor.getRGB(), downColor.getRGB());
            int top    = ColorUtil.gradient(10,  180, upColor.getRGB(), downColor.getRGB());
            int bottom = ColorUtil.gradient(10,  270, upColor.getRGB(), downColor.getRGB());
            boolean leftToRight = gradientType.is("Слева направо");
            int c1 = leftToRight ? hud_color : left;
            int c2 = leftToRight ? hud_color : right;
            int c3 = leftToRight ? right     : hud_color;
            int c4 = leftToRight ? left      : hud_color;

            rectRGB(render2D.getDrawContext().getMatrices(), posX, posY, widthDynamic, headerHeight + 1, corner, c1, c2, c3, c4);
        }


        RenderUtil.drawTexture(render2D.getDrawContext().getMatrices(), "images/hud/staff.png", posX + widthDynamic - headerHeight, posY + 4, 12, 12, 0, Color.white.getRGB());

        fontBig.drawLeftAligned(render2D.getDrawContext().getMatrices(), "StaffList",posX + 10, posY + 5f,-1);

        drawRoundedRect(render2D.getDrawContext().getMatrices(), posX, posY + headerHeight - 1, widthDynamic, hDynam + 6, new Vector4f(0, 3, 3, 0), new Color(18, 18, 18, alpha).getRGB());

        if (!staffPlayers.isEmpty()) {
            Scissor.push();
            Scissor.setFromComponentCoordinates(posX, posY, widthDynamic, headerHeight + hDynam + padding / 2f + 5);

            Map<String, PlayerListEntry> playerInfoMap = new HashMap<>();
            for (PlayerListEntry info : mc.getNetworkHandler().getPlayerList()) {
                playerInfoMap.put(info.getProfile().getName(), info);
            }

            for (StaffPlayer staff : staffPlayers) {
                String staffname = staff.getName();
                String status = staff.getStatus().getString();
                float statusWidth = fontSmall.getWidth(status);
                float currentWidth = fontSmall.getWidth(staffname);
                if (currentWidth > nameWidth) nameWidth = currentWidth;

                float baseY = posY + headerHeight + padding + (index * offset);

                PlayerListEntry playerInfo = playerInfoMap.get(staffname);
                if (playerInfo != null) {
                    if (!(staff.getStatus() == StaffPlayer.Status.VANISHED || staff.getStatus() == StaffPlayer.Status.SPEC)) {
                        RenderAddon.drawStaffHead(render2D.getDrawContext().getMatrices(), playerInfo.getSkinTextures().texture(), posX + padding, baseY - 1f, 9, 3);
                    }
                } else {
                    RenderUtil.drawTexture(render2D.getDrawContext().getMatrices(), "images/hud/staffvanish.png", posX + padding, baseY - 1f, 9, 9, 3, Color.white.getRGB());
                }

                RenderUtil.drawRoundedRect(render2D.getDrawContext().getMatrices(), posX + padding + 11, baseY - 0.8f, 1.2f, 9, 0, new Color(220, 220, 220, 190).getRGB());

                fontSmall.drawLeftAligned(render2D.getDrawContext().getMatrices(), staffname, posX + padding + 16f, baseY - 0.5f, Color.WHITE.getRGB());

                fontSmall.drawLeftAligned(render2D.getDrawContext().getMatrices(), status, posX + widthDynamic - statusWidth - 4, baseY - 1, getStatusColor(staff.getStatus()));

                index++;
            }

            Scissor.unset();
            Scissor.pop();
        }

        activeStaff = index;
        stafflistDrag.setWidth(widthDynamic);
        stafflistDrag.setHeight(hDynam + headerHeight + padding + 1);
    }

    private float lastHealth = 0.0f;
    private float lastAbsorption = 0.0f;

    private void targethud(EventRender2D render2D) {
        float x = targethudDrag.getX();
        float y = targethudDrag.getY();

        target = getTarget(target);
        scale = tHudAnimation.getOutput();
        if (scale == 0.0 || target == null) return;

        float rawHealth = MathHelper.clamp(ServerUtil.getHealth(target), 0.0F, 1.0F);
        float rawAbsorption = MathHelper.clamp(target.getAbsorptionAmount(), 0.0F, 7.1F);

        lastHealth = MathUtil.fast(lastHealth, rawHealth, 8);
        lastAbsorption = MathUtil.fast(lastAbsorption, rawAbsorption, 8);

        String healthDisplay = String.format(Locale.ENGLISH, "%.0f", lastHealth * 20.0F);

        render2D.getMatrixStack().push();
        RenderAddon.sizeAnimation(render2D.getMatrixStack(), x + 60.0F, y + 17.5f, scale);
        drawRoundedRect(render2D.getDrawContext().getMatrices(), x, y, 120, 35, 3, hud_color);

        String displayName = Manager.FUNCTION_MANAGER.nameProtect.getProtectedName(target.getName().getString());
        if (displayName.length() > 12) displayName = displayName.substring(0, 12) + "...";
        FontUtils.durman[15].drawLeftAligned(render2D.getDrawContext().getMatrices(), displayName, x + 35, y + 5f, -1);

        RenderAddon.drawHead(render2D.getDrawContext().getMatrices(), target, x + 4, y + 3.5f, 28, roundingSilaSanya.get().floatValue());

        drawRoundedRect(render2D.getDrawContext().getMatrices(), x + 34.2F, y + 26F, 82, 5.0F, 0, new Color(11, 11, 11, 255).getRGB());

        StyleManager theme = Manager.STYLE_MANAGER;
        java.awt.Color upColor = new java.awt.Color(theme.getFirstColor());
        java.awt.Color downColor = new java.awt.Color(theme.getSecondColor());
        final Vector4i vec = new Vector4i(ColorUtil.gradient(5, 90, upColor.getRGB(), downColor.getRGB()), ColorUtil.gradient(5, 0, upColor.getRGB(), downColor.getRGB()), ColorUtil.gradient(5, 180, upColor.getRGB(), downColor.getRGB()), ColorUtil.gradient(5, 270, upColor.getRGB(), downColor.getRGB()));
        rectRGB(render2D.getDrawContext().getMatrices(), x + 34.2F, y + 26F, 82 * lastHealth, 5.0F, 0, vec.w, vec.x, vec.y, vec.z);

        drawRoundedRect(render2D.getDrawContext().getMatrices(), x + 34.2F, y + 26F, 4 * lastAbsorption, 5.0F, 0, Color.YELLOW.getRGB());

        FontUtils.sf_bold[13].centeredDraw(render2D.getDrawContext().getMatrices(), healthDisplay + " HP", x + 105, y + 16, -1);

        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(target.getMainHandStack());
        stacks.add(target.getOffHandStack());
        stacks.removeIf(i -> i.getItem() instanceof AirBlockItem || i.isEmpty());

        float renderOffset = 0;
        for (ItemStack stack : stacks) {
            render2D.getDrawContext().getMatrices().push();
            render2D.getDrawContext().getMatrices().translate(x + renderOffset + 35, y + 16, 0);
            render2D.getDrawContext().getMatrices().scale(0.5f, 0.5f, 1.0f);
            render2D.getDrawContext().drawItem(stack, 0, 0, 7, 0);
            render2D.getDrawContext().drawStackOverlay(mc.textRenderer, stack, 0, 0);
            render2D.getDrawContext().getMatrices().pop();
            renderOffset += 15;
        }
        render2D.getMatrixStack().pop();

        targethudDrag.setWidth(120);
        targethudDrag.setHeight(35);
    }



    private void waterMark(EventRender2D render2D) {
        float x = watermarkDrag.getX();
        float y = watermarkDrag.getY();
        MatrixStack matrices = render2D.getDrawContext().getMatrices();
        int alpha = MathHelper.clamp(customAlpha.get().intValue(), 140, 255);
        int ink = new Color(8, 8, 8, alpha).getRGB();
        int paper = new Color(238, 238, 238, alpha).getRGB();
        int muted = new Color(158, 158, 158, alpha).getRGB();
        int line = new Color(92, 92, 92, alpha).getRGB();
        float width = 218;

        if (blur.get()) drawBlur(matrices, x, y, width, 42, new Vector4f(0, 0, 0, 0), 12, Color.WHITE.getRGB());
        drawRoundedRect(matrices, x, y, 4, 42, 0, paper);
        drawRoundedRect(matrices, x + 10, y + 3, width - 10, 36, 0, ink);
        FontUtils.sf_bold[18].drawLeftAligned(matrices, "LUPAWARE", x + 20, y + 6, paper);
        FontUtils.sf_medium[10].drawLeftAligned(matrices, "CLIENT / 1.21.4", x + 20, y + 25, muted);
        String detail = Manager.USER_PROFILE.getName() + "  |  " + ClientManager.getFps() + " FPS  |  " + ClientManager.getPing() + " MS";
        FontUtils.sf_medium[10].drawClipped(matrices, detail, width - 112, x + 108, y + 25, muted);
        drawRoundedRect(matrices, x + 108, y + 11, 1, 12, 0, line);
        watermarkDrag.setWidth(width);
        watermarkDrag.setHeight(42);
    }

    private int applyHudAlpha(int color, int alpha) {
        Color c = new Color(color, true);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp(alpha, 0, 255)).getRGB();
    }

    private void сoordinates(EventRender2D render2D) {
        MatrixStack matrices = render2D.getDrawContext().getMatrices();
        float x = coordinateshudDrag.getX();
        float y = coordinateshudDrag.getY();
        String coords = String.format("%d  %d  %d", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
        String tps = "TPS " + ClientManager.getTPS();
        int alpha = MathHelper.clamp(customAlpha.get().intValue(), 140, 255);
        int ink = new Color(8, 8, 8, alpha).getRGB();
        int paper = new Color(238, 238, 238, alpha).getRGB();
        int muted = new Color(150, 150, 150, alpha).getRGB();
        float width = 186;
        drawRoundedRect(matrices, x, y, width, 38, 0, ink);
        drawRoundedRect(matrices, x, y, 4, 38, 0, paper);
        FontUtils.sf_medium[10].drawLeftAligned(matrices, "POSITION", x + 15, y + 6, muted);
        FontUtils.sf_bold[14].drawLeftAligned(matrices, coords, x + 15, y + 18, paper);
        FontUtils.sf_medium[10].drawRightAligned(matrices, tps, x + width - 10, y + 20, muted);
        coordinateshudDrag.setWidth(width);
        coordinateshudDrag.setHeight(38);
    }

    private float keybindsHeightDynamic = 0;

    private void keybindHud(EventRender2D render2D) {
        float posX = keybindsDrag.getX();
        float posY = keybindsDrag.getY();
        MatrixStack matrices = render2D.getDrawContext().getMatrices();
        var font = FontUtils.sf_medium[13];
        int alpha = MathHelper.clamp(customAlpha.get().intValue(), 140, 255);
        int ink = new Color(8, 8, 8, alpha).getRGB();
        int paper = new Color(238, 238, 238, alpha).getRGB();
        int muted = new Color(150, 150, 150, alpha).getRGB();
        int index = 0;
        float maxWidth = 168;
        for (Function f : Manager.FUNCTION_MANAGER.getFunctions()) {
            if (f.bind != 0 && f.state) maxWidth = Math.max(maxWidth, font.getWidth(f.name) + font.getWidth(getShortKey(ClientManager.getKey(f.bind))) + 42);
            for (Setting setting : f.getSettings()) {
                if (setting instanceof BindBooleanSetting bindSetting && bindSetting.isVisible() && bindSetting.getBindKey() != 0 && bindSetting.get()) {
                    maxWidth = Math.max(maxWidth, font.getWidth(bindSetting.getName()) + font.getWidth(getShortKey(ClientManager.getKey(bindSetting.getBindKey()))) + 42);
                }
            }
        }
        float targetHeight = 29 + activeModules * 18;
        keybindsHeightDynamic = MathUtil.fast(keybindsHeightDynamic, targetHeight - 29, 15);
        float height = Math.max(29, 29 + keybindsHeightDynamic);
        drawRoundedRect(matrices, posX, posY, 3, height, 0, paper);
        drawRoundedRect(matrices, posX + 9, posY, maxWidth - 9, height, 0, ink);
        FontUtils.sf_bold[14].drawLeftAligned(matrices, "KEYS", posX + 19, posY + 7, paper);
        FontUtils.sf_medium[10].drawRightAligned(matrices, String.valueOf(activeModules), posX + maxWidth - 10, posY + 9, muted);
        float yOffset = posY + 29;
        for (Function f : Manager.FUNCTION_MANAGER.getFunctions()) {
            if (f.bind != 0 && f.state) {
                yOffset = drawBindRow(matrices, font, f.name, getShortKey(ClientManager.getKey(f.bind)), posX, yOffset, maxWidth, paper, 8);
                index++;
            }
            for (Setting setting : f.getSettings()) {
                if (setting instanceof BindBooleanSetting bindSetting && bindSetting.isVisible() && bindSetting.getBindKey() != 0 && bindSetting.get()) {
                    yOffset = drawBindRow(matrices, font, bindSetting.getName(), getShortKey(ClientManager.getKey(bindSetting.getBindKey())), posX, yOffset, maxWidth, paper, 8);
                    index++;
                }
            }
        }
        activeModules = index;
        keybindsDrag.setWidth(maxWidth);
        keybindsDrag.setHeight(height);
    }

    private float drawBindRow(MatrixStack matrices, ru.levin.manager.fontManager.RenderFonts font, String name, String key, float x, float y, float width, int accent, int padding) {
        float keyWidth = font.getWidth(key);
        font.drawLeftAligned(matrices, name, x + 18, y + 3, new Color(218, 218, 218).getRGB());
        font.drawRightAligned(matrices, key, x + width - padding, y + 3, new Color(150, 150, 150).getRGB());
        drawRoundedRect(matrices, x + 12, y + 1, width - 24, 1, 0, new Color(60, 60, 60, 180).getRGB());
        return y + 18;
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