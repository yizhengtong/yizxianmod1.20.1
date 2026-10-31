package net.minecraft.client.yiz.xian;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes;

/**
 * Yiz Xian Mod — 1.20.1 Forge 内容模组（依赖前置库 yizmodqzk）。
 */
@Mod(YizxianMod.MODID)
public class YizxianMod {

    public static final String MODID = "yizxianmod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final DeferredRegister<net.minecraft.world.item.enchantment.Enchantment> ENCHANTMENTS =
        DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MODID);

    public static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS =
        DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);

    public static final DeferredRegister<net.minecraft.world.item.alchemy.Potion> POTIONS =
        DeferredRegister.create(ForgeRegistries.POTIONS, MODID);

    /** 实体属性编辑工具（右键实体打开容器界面编辑其 yizmodqzk 自定义属性）。 */
    public static final RegistryObject<Item> ENTITY_ATTRIBUTE_EDITOR =
        ITEMS.register("entity_attribute_editor", () ->
            new net.minecraft.client.yiz.xian.item.EntityAttributeEditorItem(new Item.Properties().stacksTo(1)));

    /** 辖界者生物蛋（创造标签页用，右键生成 Boss；自定义延迟解析实体类型，避免跨注册表时序）。 */
    public static final RegistryObject<Item> QUANSHOUZHE_SPAWN_EGG =
        ITEMS.register("quanshouzhe_spawn_egg", () ->
            new net.minecraft.client.yiz.xian.item.QuanshouzheSpawnEggItem(new Item.Properties()));

    // ── 自走棋星级生物蛋（辖界者=5费档 ×1/×2/×6）──
    // 每实体 3 星级蛋，物品描边 OutlineMarker：1星=level0白 / 2星=level4蓝 / 3星=level6金

    public static final RegistryObject<Item> QUANSHOUZHE_STAR1 =
        ITEMS.register("quanshouzhe_spawn_egg_star1", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.QUANSHOUZHE, 5, 1));
    public static final RegistryObject<Item> QUANSHOUZHE_STAR2 =
        ITEMS.register("quanshouzhe_spawn_egg_star2", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.QUANSHOUZHE, 5, 2));
    public static final RegistryObject<Item> QUANSHOUZHE_STAR3 =
        ITEMS.register("quanshouzhe_spawn_egg_star3", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.QUANSHOUZHE, 5, 3));

    public static final RegistryObject<Item> TIEDOUSHI_STAR1 =
        ITEMS.register("tiedoushi_spawn_egg_star1", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.TIEDOUSHI, 3, 1));
    public static final RegistryObject<Item> TIEDOUSHI_STAR2 =
        ITEMS.register("tiedoushi_spawn_egg_star2", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.TIEDOUSHI, 3, 2));
    public static final RegistryObject<Item> TIEDOUSHI_STAR3 =
        ITEMS.register("tiedoushi_spawn_egg_star3", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.TIEDOUSHI, 3, 3));

    // ── 怒翼（幻翼类飞行棋子，一费档）──
    public static final RegistryObject<Item> NUYI_STAR1 =
        ITEMS.register("nuyi_spawn_egg_star1", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.NUYI, 1, 1));
    public static final RegistryObject<Item> NUYI_STAR2 =
        ITEMS.register("nuyi_spawn_egg_star2", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.NUYI, 1, 2));
    public static final RegistryObject<Item> NUYI_STAR3 =
        ITEMS.register("nuyi_spawn_egg_star3", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.NUYI, 1, 3));

    // ── 辅助物品 ──────────────────────────────────────────────

    /** 光明末影之眼：手持时给周围末地传送门框画穿墙发光轮廓。 */
    public static final RegistryObject<Item> BRIGHT_ENDER_EYE =
        ITEMS.register("bright_ender_eye", () -> new Item(new Item.Properties()));

    /** 光明指南针：Shift+右键开配置 GUI，右键扫描高亮工作槽物品对应的方块/实体。 */
    public static final RegistryObject<Item> BRIGHT_COMPASS =
        ITEMS.register("bright_compass", () ->
            new net.minecraft.client.yiz.xian.item.BrightCompassItem(new Item.Properties().stacksTo(1)));

    /** 堆叠核心：铁砧左槽放目标物品、右槽放本物品，取出后该物品 ID 最大堆叠数 ×2（最多 2 次，封顶 99）。 */
    public static final RegistryObject<Item> STACK_CORE =
        ITEMS.register("stack_core", () -> new Item(new Item.Properties().stacksTo(64)));

    /** 实体探查镜：手持右键 LivingEntity 打开探查 GUI（主面板纹理 entity_probe/main.png）。 */
    public static final RegistryObject<Item> ENTITY_PROBE =
        ITEMS.register("entity_probe", () ->
            new net.minecraft.client.yiz.xian.item.EntityProbeItem(new Item.Properties().stacksTo(1)));

    // ── 饰品（Curios API，前置由 Jar-in-Jar 内嵌兜底）────────────────
    // Curios 5.x 的槽位是「两段式」的，两处都要有，缺一段就没有任何槽位（且不报错）：
    //   ① 声明类型：data/yizxianmod/curios/slots/*.json（本模组只自定义了 relic）
    //   ② 分配给实体：data/yizxianmod/curios/entities/player.json
    //      —— Curios 自带的 10 个槽位同样不会自动给玩家，用到哪些就要在这里列哪些
    //   ③ 物品绑定（这一层才是"物品进哪个槽"）：
    //   heartsteel          → data/curios/tags/items/body.json      （胸饰）
    //   rumbling_heart      → data/curios/tags/items/charm.json     （护符）
    //   boundary_eye        → data/curios/tags/items/necklace.json  （项链）
    //   boundary_authority  → data/curios/tags/items/relic.json     （自定义槽位「遗物」）

    /** 心之钢（胸饰）：+6 最大生命、+2 护甲。 */
    public static final RegistryObject<Item> HEARTSTEEL =
        ITEMS.register("heartsteel", () ->
            new net.minecraft.client.yiz.xian.item.curio.HeartsteelItem(new Item.Properties().stacksTo(1)));

    /** 轰然之心（护符）：+3 最大生命、+10% 击退抗性。 */
    public static final RegistryObject<Item> RUMBLING_HEART =
        ITEMS.register("rumbling_heart", () ->
            new net.minecraft.client.yiz.xian.item.curio.RumblingHeartItem(new Item.Properties().stacksTo(1)));

    /** 辖界之眼（项链）：功能型，无属性。 */
    public static final RegistryObject<Item> BOUNDARY_EYE =
        ITEMS.register("boundary_eye", () ->
            new net.minecraft.client.yiz.xian.item.curio.BoundaryEyeItem(new Item.Properties().stacksTo(1)));

    /** 辖界的权柄（遗物·自定义槽位）：+2 攻击伤害。 */
    public static final RegistryObject<Item> BOUNDARY_AUTHORITY =
        ITEMS.register("boundary_authority", () ->
            new net.minecraft.client.yiz.xian.item.curio.BoundaryAuthorityItem(new Item.Properties().stacksTo(1)));

    // ── 1.8.9 怀旧装备（数值严格对齐 1.8.9；不死图腾不在本批）──────────────
    // 剑/镐：伤害、耐久、无冷却、无横扫、剑格挡；甲：3/8/6/3 固定减伤（韧性不参与）；
    // 末影之眼：继承原版投掷/填框；附魔金苹果：再生 V 等 1.8 效果。

    /** 怀旧钻石剑：8 伤害、1562 耐久、无横扫、右键 50% 格挡、无攻击冷却。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_SWORD =
        ITEMS.register("legacy_diamond_sword", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyDiamondSwordItem(new Item.Properties()));

    /** 怀旧钻石镐：6 伤害、1562 耐久、挖掘速度 8/等级 3、无攻击冷却。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_PICKAXE =
        ITEMS.register("legacy_diamond_pickaxe", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyDiamondPickaxeItem(new Item.Properties()));

    /** 怀旧钻石头盔：3 护甲 / 363 耐久 / 韧性 0（固定减伤走 LegacyArmorMixin）。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_HELMET =
        ITEMS.register("legacy_diamond_helmet", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyArmorItem(
                net.minecraft.client.yiz.xian.item.legacy.LegacyArmorMaterial.INSTANCE,
                net.minecraft.world.item.ArmorItem.Type.HELMET, new Item.Properties()));

    /** 怀旧钻石胸甲：8 护甲 / 528 耐久 / 韧性 0。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_CHESTPLATE =
        ITEMS.register("legacy_diamond_chestplate", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyArmorItem(
                net.minecraft.client.yiz.xian.item.legacy.LegacyArmorMaterial.INSTANCE,
                net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    /** 怀旧钻石护腿：6 护甲 / 495 耐久 / 韧性 0。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_LEGGINGS =
        ITEMS.register("legacy_diamond_leggings", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyArmorItem(
                net.minecraft.client.yiz.xian.item.legacy.LegacyArmorMaterial.INSTANCE,
                net.minecraft.world.item.ArmorItem.Type.LEGGINGS, new Item.Properties()));

    /** 怀旧钻石靴子：3 护甲 / 429 耐久 / 韧性 0。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_BOOTS =
        ITEMS.register("legacy_diamond_boots", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyArmorItem(
                net.minecraft.client.yiz.xian.item.legacy.LegacyArmorMaterial.INSTANCE,
                net.minecraft.world.item.ArmorItem.Type.BOOTS, new Item.Properties()));

    /** 怀旧末影珍珠：堆叠 16、投掷传送并造成 5 点伤害、无 1 秒冷却（1.9 才加入）。 */
    public static final RegistryObject<Item> LEGACY_ENDER_PEARL =
        ITEMS.register("legacy_ender_pearl", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyEnderPearlItem(new Item.Properties().stacksTo(16)));

    /** 怀旧附魔金苹果：再生 V 30s、吸收 I 2:00、抗性/抗火 5:00，可满饥饿吃，可合成。 */
    public static final RegistryObject<Item> LEGACY_ENCHANTED_GOLDEN_APPLE =
        ITEMS.register("legacy_enchanted_golden_apple", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyGoldenAppleItem(new Item.Properties()
                .food(new net.minecraft.world.food.FoodProperties.Builder()
                    .nutrition(4)
                    .saturationMod(1.2F)
                    .alwaysEat()
                    .build())
                .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** 1.8.9 钓鱼竿：65 耐久；鱼饵命中实体击退，收回拉回。 */
    public static final RegistryObject<Item> LEGACY_FISHING_ROD =
        ITEMS.register("legacy_fishing_rod", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyFishingRodItem(
                new Item.Properties().durability(65)));

    // ── 极尽态（新 ID；耐久 ×10 + Unbreakable）────────────────────────

    /** 怀旧钻石剑——极尽：1562×10 耐久，Unbreakable。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_SWORD_EXTREME =
        ITEMS.register("legacy_diamond_sword_extreme", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeDiamondSwordItem(new Item.Properties()));

    /** 怀旧钻石镐——极尽：1562×10 耐久，Unbreakable，+200% 挖掘效率。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_PICKAXE_EXTREME =
        ITEMS.register("legacy_diamond_pickaxe_extreme", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeDiamondPickaxeItem(new Item.Properties()));

    /** 怀旧钻石头盔——极尽：363×10 耐久，Unbreakable。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_HELMET_EXTREME =
        ITEMS.register("legacy_diamond_helmet_extreme", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeArmorItem(
                net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeArmorMaterial.INSTANCE,
                net.minecraft.world.item.ArmorItem.Type.HELMET, new Item.Properties()));

    /** 怀旧钻石胸甲——极尽：528×10 耐久，Unbreakable。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_CHESTPLATE_EXTREME =
        ITEMS.register("legacy_diamond_chestplate_extreme", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeArmorItem(
                net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeArmorMaterial.INSTANCE,
                net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    /** 怀旧钻石护腿——极尽：495×10 耐久，Unbreakable。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_LEGGINGS_EXTREME =
        ITEMS.register("legacy_diamond_leggings_extreme", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeArmorItem(
                net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeArmorMaterial.INSTANCE,
                net.minecraft.world.item.ArmorItem.Type.LEGGINGS, new Item.Properties()));

    /** 怀旧钻石靴子——极尽：429×10 耐久，Unbreakable。 */
    public static final RegistryObject<Item> LEGACY_DIAMOND_BOOTS_EXTREME =
        ITEMS.register("legacy_diamond_boots_extreme", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeArmorItem(
                net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeArmorMaterial.INSTANCE,
                net.minecraft.world.item.ArmorItem.Type.BOOTS, new Item.Properties()));

    /** 怀旧附魔金苹果——极尽：2 tick 吃完，吸收 IV/抗性 II/再生 V 60s。 */
    public static final RegistryObject<Item> LEGACY_ENCHANTED_GOLDEN_APPLE_EXTREME =
        ITEMS.register("legacy_enchanted_golden_apple_extreme", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeGoldenAppleItem(new Item.Properties()
                .food(new net.minecraft.world.food.FoodProperties.Builder()
                    .nutrition(4)
                    .saturationMod(1.2F)
                    .alwaysEat()
                    .build())
                .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** 怀旧末影珍珠——极尽：初速 3.0，飞行中额外加速并减空气衰减。 */
    public static final RegistryObject<Item> LEGACY_ENDER_PEARL_EXTREME =
        ITEMS.register("legacy_ender_pearl_extreme", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeEnderPearlItem(
                new Item.Properties().stacksTo(64)));

    /** 1.8.9 锋利附魔（1–5 级，每级 +1.25 伤害）。 */
    public static final RegistryObject<net.minecraft.world.item.enchantment.Enchantment> LEGACY_SHARPNESS =
        ENCHANTMENTS.register("legacy_sharpness",
            net.minecraft.client.yiz.xian.item.legacy.LegacySharpnessEnchantment::new);

    /** 1.8.9 保护附魔（I/II/III/IV = 1/2/3/5 EPF）。 */
    public static final RegistryObject<net.minecraft.world.item.enchantment.Enchantment> LEGACY_PROTECTION =
        ENCHANTMENTS.register("legacy_protection",
            net.minecraft.client.yiz.xian.item.legacy.LegacyProtectionEnchantment::new);

    /** 怀旧力量 Buff（每级 +3 攻击伤害），图标来自 strength.png。 */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> LEGACY_STRENGTH_EFFECT =
        EFFECTS.register("legacy_strength",
            net.minecraft.client.yiz.xian.item.legacy.LegacyStrengthEffect::new);

    /** 怀旧力量药水 I：1.8 的 3:00。 */
    public static final RegistryObject<net.minecraft.world.item.alchemy.Potion> LEGACY_STRENGTH =
        POTIONS.register("legacy_strength", () ->
            new net.minecraft.world.item.alchemy.Potion(
                new net.minecraft.world.effect.MobEffectInstance(LEGACY_STRENGTH_EFFECT.get(), 3600, 0)));

    /** 怀旧力量药水 II：1.8 的 1:30。 */
    public static final RegistryObject<net.minecraft.world.item.alchemy.Potion> LEGACY_STRENGTH_2 =
        POTIONS.register("legacy_strength_2", () ->
            new net.minecraft.world.item.alchemy.Potion(
                new net.minecraft.world.effect.MobEffectInstance(LEGACY_STRENGTH_EFFECT.get(), 1800, 1)));

    /** 极尽力量药水 III：3:00、+9 攻击伤害。 */
    public static final RegistryObject<net.minecraft.world.item.alchemy.Potion> LEGACY_STRENGTH_3 =
        POTIONS.register("legacy_strength_3", () ->
            new net.minecraft.world.item.alchemy.Potion(
                new net.minecraft.world.effect.MobEffectInstance(LEGACY_STRENGTH_EFFECT.get(), 3600, 2)));

    /** 怀旧力量药水 I 物品。 */
    public static final RegistryObject<Item> LEGACY_STRENGTH_POTION =
        ITEMS.register("legacy_strength_potion", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyPotionItem(
                LEGACY_STRENGTH::get, new Item.Properties().stacksTo(1)));

    /** 怀旧力量药水 II 物品。 */
    public static final RegistryObject<Item> LEGACY_STRENGTH_POTION_2 =
        ITEMS.register("legacy_strength_potion_2", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyPotionItem(
                LEGACY_STRENGTH_2::get, new Item.Properties().stacksTo(1)));

    /** 极尽力量药水 III 物品。 */
    public static final RegistryObject<Item> LEGACY_STRENGTH_POTION_3 =
        ITEMS.register("legacy_strength_potion_3", () ->
            new net.minecraft.client.yiz.xian.item.legacy.LegacyPotionItem(
                LEGACY_STRENGTH_3::get, new Item.Properties().stacksTo(1)));

    /** 创造模式标签页：生物蛋 + 编辑器。 */
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<net.minecraft.world.item.CreativeModeTab> TAB =
        CREATIVE_TABS.register("main", () ->
            net.minecraft.world.item.CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.yizxianmod"))
                .icon(() -> new net.minecraft.world.item.ItemStack(QUANSHOUZHE_SPAWN_EGG.get()))
                .displayItems((params, output) -> {
                    output.accept(QUANSHOUZHE_SPAWN_EGG.get());
                    output.accept(ENTITY_ATTRIBUTE_EDITOR.get());
                    // 自走棋星级蛋（带描边 NBT 白/蓝/金）
                    output.accept(starEggStack(QUANSHOUZHE_STAR1.get()));
                    output.accept(starEggStack(QUANSHOUZHE_STAR2.get()));
                    output.accept(starEggStack(QUANSHOUZHE_STAR3.get()));
                    output.accept(starEggStack(TIEDOUSHI_STAR1.get()));
                    output.accept(starEggStack(TIEDOUSHI_STAR2.get()));
                    output.accept(starEggStack(TIEDOUSHI_STAR3.get()));
                    output.accept(starEggStack(NUYI_STAR1.get()));
                    output.accept(starEggStack(NUYI_STAR2.get()));
                    output.accept(starEggStack(NUYI_STAR3.get()));
                })
                .build());

    /** 辅助物品标签页。 */
    public static final RegistryObject<net.minecraft.world.item.CreativeModeTab> AUXILIARY_TAB =
        CREATIVE_TABS.register("auxiliary", () ->
            net.minecraft.world.item.CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.yizxianmod.auxiliary"))
                .icon(() -> new net.minecraft.world.item.ItemStack(BRIGHT_ENDER_EYE.get()))
                .displayItems((params, output) -> {
                    output.accept(BRIGHT_ENDER_EYE.get());
                    output.accept(BRIGHT_COMPASS.get());
                    output.accept(STACK_CORE.get());
                    output.accept(ENTITY_PROBE.get());
                })
                .build());

    /** 饰品标签页（Curios）。 */
    public static final RegistryObject<net.minecraft.world.item.CreativeModeTab> CURIO_TAB =
        CREATIVE_TABS.register("curio", () ->
            net.minecraft.world.item.CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.yizxianmod.curio"))
                .icon(() -> new net.minecraft.world.item.ItemStack(HEARTSTEEL.get()))
                .displayItems((params, output) -> {
                    output.accept(HEARTSTEEL.get());
                    output.accept(RUMBLING_HEART.get());
                    output.accept(BOUNDARY_EYE.get());
                    output.accept(BOUNDARY_AUTHORITY.get());
                })
                .build());

    /** 1.8.9 怀旧装备标签页。 */
    public static final RegistryObject<net.minecraft.world.item.CreativeModeTab> LEGACY_TAB =
        CREATIVE_TABS.register("legacy", () ->
            net.minecraft.world.item.CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.yizxianmod.legacy"))
                .icon(() -> new net.minecraft.world.item.ItemStack(LEGACY_DIAMOND_SWORD.get()))
                .displayItems((params, output) -> {
                    output.accept(LEGACY_DIAMOND_SWORD.get());
                    output.accept(LEGACY_DIAMOND_PICKAXE.get());
                    output.accept(LEGACY_DIAMOND_HELMET.get());
                    output.accept(LEGACY_DIAMOND_CHESTPLATE.get());
                    output.accept(LEGACY_DIAMOND_LEGGINGS.get());
                    output.accept(LEGACY_DIAMOND_BOOTS.get());
                    output.accept(LEGACY_ENDER_PEARL.get());
                    output.accept(LEGACY_ENCHANTED_GOLDEN_APPLE.get());
                    output.accept(LEGACY_FISHING_ROD.get());
                    output.accept(LEGACY_DIAMOND_SWORD_EXTREME.get());
                    output.accept(LEGACY_DIAMOND_PICKAXE_EXTREME.get());
                    output.accept(LEGACY_DIAMOND_HELMET_EXTREME.get());
                    output.accept(LEGACY_DIAMOND_CHESTPLATE_EXTREME.get());
                    output.accept(LEGACY_DIAMOND_LEGGINGS_EXTREME.get());
                    output.accept(LEGACY_DIAMOND_BOOTS_EXTREME.get());
                    output.accept(LEGACY_ENDER_PEARL_EXTREME.get());
                    output.accept(LEGACY_ENCHANTED_GOLDEN_APPLE_EXTREME.get());
                    output.accept(legacyPotionStack(LEGACY_STRENGTH_POTION.get(), LEGACY_STRENGTH.get()));
                    output.accept(legacyPotionStack(LEGACY_STRENGTH_POTION_2.get(), LEGACY_STRENGTH_2.get()));
                    output.accept(legacyPotionStack(LEGACY_STRENGTH_POTION_3.get(), LEGACY_STRENGTH_3.get()));
                })
                .build());

    public YizxianMod() {
        var modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();

        // 生物实体注册（辖界者）
        YizxianEntityTypes.ENTITY_TYPES.register(modEventBus);

        // 实体属性编辑工具 + 菜单 + 网络
        EFFECTS.register(modEventBus);
        POTIONS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENCHANTMENTS.register(modEventBus);
        // 创造标签页（生物蛋 + 编辑器）
        CREATIVE_TABS.register(modEventBus);
        net.minecraft.client.yiz.xian.menu.YizxianMenus.register(modEventBus);

        // 极尽甲的无碰撞属性不能只注册给玩家：给所有非玩家 LivingEntity 补上 NO_COLLISION，
        // 这样僵尸/自定义生物穿戴极尽甲时也能真正穿过实体。
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeModificationEvent e) -> {
            var noCollision = net.minecraft.client.yiz.attribute.YizAttributes.NO_COLLISION.get();
            var knockbackImmunity = net.minecraft.client.yiz.attribute.YizAttributes.KNOCKBACK_IMMUNITY.get();
            for (var type : e.getTypes()) {
                if (type == net.minecraft.world.entity.EntityType.PLAYER) {
                    continue;
                }
                if (!e.has(type, noCollision)) {
                    e.add(type, noCollision);
                }
                if (!e.has(type, knockbackImmunity)) {
                    e.add(type, knockbackImmunity);
                }
            }
        });

        // 属性之外的兜底：极尽四件套齐全时无条件免疫击退。
        // 即使某个环境没把物品 AttributeModifier 正确挂到实体上，也保证全套生效。
        net.minecraft.client.yiz.api.KnockbackImmunityRegistry.register(
            net.minecraft.client.yiz.xian.item.legacy.LegacyArmorHelper::isFullExtremeSet);

        // 实体属性创建（辖界者 AttributeSupplier）
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(YizxianEntityTypes.QUANSHOUZHE.get(),
                net.minecraft.client.yiz.xian.entity.QuanshouzheEntity.createAttributes().build());
        });

        // 实体属性创建（铁斗士 AttributeSupplier）
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(YizxianEntityTypes.TIEDOUSHI.get(),
                net.minecraft.client.yiz.xian.entity.TiedoushiEntity.createAttributes().build());
        });

        // 实体属性创建（怒翼 AttributeSupplier）
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(YizxianEntityTypes.NUYI.get(),
                net.minecraft.client.yiz.xian.entity.NuyiEntity.createAttributes().build());
        });

        // 给玩家默认属性加涨跌多空（FIRST_DREAM/long_short，默认 0）：否则物品修饰符作用在玩家上时
        // getAttribute(FIRST_DREAM) 返回 null、修饰符不生效 → /yiz sx zddk 加的物品属性无效
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeModificationEvent e) -> {
            e.add(net.minecraft.world.entity.EntityType.PLAYER,
                net.minecraft.client.yiz.attribute.YizAttributes.FIRST_DREAM.get());
        });

        modEventBus.addListener(this::commonSetup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
        // 堆叠核心：铁砧强化最大堆叠数（AnvilUpdateEvent / AnvilRepairEvent）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            net.minecraft.client.yiz.xian.item.StackCoreAnvilHandler.class);
        // 1.8.9 怀旧装备：剑格挡 50%（LivingHurtEvent）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            net.minecraft.client.yiz.xian.item.legacy.LegacyCombatEvents.class);
        // 极尽保护：每级 4.995%，总等级封顶 20 → 99.9%
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            new net.minecraft.client.yiz.xian.item.legacy.LegacyProtectionEvents());
        // 极尽装备：任意实体装备槽的 Unbreakable 标签兜底
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeItems.class);
        // 极尽镐：内置 200% 挖掘速度（BreakSpeed）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            net.minecraft.client.yiz.xian.item.legacy.LegacyMiningSpeedEvents.class);
        // 加入事件反否决：本模组实体的加入不接受外部取消
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            net.minecraft.client.yiz.xian.core.EntityPresenceEventGuard.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Yiz Xian Mod 1.20.1 初始化完成");
        // 注册生物技能（供战斗组件 skill_id 派发）
        net.minecraft.client.yiz.creature.CreatureSkills.register(new net.minecraft.client.yiz.xian.entity.TiedoushiEntity.Skill1());
        // 铁斗士生物原型（代码轨默认值；数据包 data/yizxianmod/yiz_creature/tiedoushi.json 同 id 覆盖）
        registerTiedoushiProfile();
        // 注册 SimpleChannel 网络
        net.minecraft.client.yiz.xian.network.NetworkHandler.register();
        // /yiz sx zddk <数值>：给主手物品添加涨跌多空属性修饰符（SimpleCommandRegistry 已由前置库订阅）
        net.minecraft.client.yiz.xian.command.YizSxCommand.register();
        // /yiz sx mzdk <数值>：给主手物品添加灭在多空属性修饰符（1点=目标最大生命1%）
        net.minecraft.client.yiz.xian.command.YizSxMzdkCommand.register();
        // /yiz dh xjz skill <index>：让周围 5 格内辖界者播放技能动画（气丹）
        net.minecraft.client.yiz.xian.command.YizDhCommand.register();
        // /yiz gj xjz skill <index> <count>：让周围 5 格内辖界者接下来 count 次攻击都用 index 技能
        net.minecraft.client.yiz.xian.command.YizGjCommand.register();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.debug("Yiz Xian Mod 服务端启动");
    }

    /**
     * 铁斗士生物原型（代码轨）—— 目前只声明「免疫外部动量」。
     *
     * <p>{@code knockback_immunity} 由 {@code YizxianMob.motionGate()} 执行：它把
     * {@code setDeltaMovement / addDeltaMovement / knockback} 三个速度入口按调用栈鉴权，
     * <b>只拦外部模组的帧</b>（原版/forge/AI/本模组的写入照常放行，爆炸等外部强制力会被拒绝）。
     * 效果开关经 {@code InstanceEffectState.isEffectEnabled} 判定，档位顺序：
     * 实例补丁（指令/星级）→ 原型（本处 + 数据包覆盖）→ 类级基础效果。</p>
     *
     * <p>注：代码轨不会被数据包清除，数据包同 id 会整体覆盖这份原型（含 effects 列表），
     * 所以 {@code tiedoushi.json} 里也同步写了同一条效果，两边保持一致。</p>
     */
    private static void registerTiedoushiProfile() {
        net.minecraft.resources.ResourceLocation id =
            new net.minecraft.resources.ResourceLocation("yizxianmod", "tiedoushi");
        net.minecraft.client.yiz.creature.CreatureProfile profile =
            net.minecraft.client.yiz.creature.CreatureProfile.builder(id)
                .component(net.minecraft.client.yiz.creature.CreatureComponents.KNOCKBACK_IMMUNITY, Boolean.TRUE)
                .build();
        net.minecraft.client.yiz.creature.CreatureProfileRegistry.register(profile);
        net.minecraft.client.yiz.creature.CreatureProfileRegistry.bindClass(
            net.minecraft.client.yiz.xian.entity.TiedoushiEntity.class, id);
    }

    //  范围溅射系统（SPLASH_RADIUS/DAMAGE/FALLOFF 属性驱动）

    /** 溅射递归保护：溅射伤害的 hurt 不再触发溅射。 */
    private static final ThreadLocal<Boolean> IN_SPLASH = ThreadLocal.withInitial(() -> false);

    /** 攻击命中 LivingDamageEvent：读 SPLASH_* 属性触发范围溅射。 */
    @SubscribeEvent
    public void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player player)) return;
        if (player.level().isClientSide) return;
        if (IN_SPLASH.get()) return;

        // 1.20.1 无武器 Profile（MeleeWeaponItem/WeaponLevelData 尚未实现），
        // 溅射只由 SPLASH_* 属性驱动。
        float splashRadius = safeAttr(player, net.minecraft.client.yiz.attribute.YizAttributes.SPLASH_RADIUS);
        if (splashRadius > 0 && event.getAmount() > 0
                && event.getEntity() instanceof net.minecraft.world.entity.LivingEntity) {
            net.minecraft.world.entity.LivingEntity primaryTarget =
                (net.minecraft.world.entity.LivingEntity) event.getEntity();
            float splashDmgPct = safeAttr(player, net.minecraft.client.yiz.attribute.YizAttributes.SPLASH_DAMAGE);
            float splashFalloff = safeAttr(player, net.minecraft.client.yiz.attribute.YizAttributes.SPLASH_FALLOFF);
            if (splashDmgPct > 0) {
                executeSplash(player, primaryTarget, event.getAmount(),
                    splashRadius, splashDmgPct, splashFalloff, event.getSource());
            }
        }
    }

    /**
     * 范围溅射伤害：以被命中目标为中心，对范围内有效实体造成伤害。
     * 目标判定：敌对生物(Monster)始终命中 + 与主目标同类型命中；其他不受。
     * 衰减公式（平滑二次曲线）：t=dist/radius, edgeMul=1-falloff/100,
     * multiplier = edgeMul + (1-edgeMul)*(1-t²), dmg = base*(pct/100)*multiplier。
     */
    private static void executeSplash(net.minecraft.world.entity.player.Player player,
                                      net.minecraft.world.entity.LivingEntity primaryTarget, float baseDamage,
                                      float radius, float splashPct, float falloff,
                                      net.minecraft.world.damagesource.DamageSource source) {
        net.minecraft.world.phys.AABB box = primaryTarget.getBoundingBox().inflate(radius);
        java.util.List<net.minecraft.world.entity.LivingEntity> nearby = primaryTarget.level().getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class, box,
            e -> e != player && e != primaryTarget && e.isAlive()
                 && isValidSplashTarget(e, primaryTarget));

        IN_SPLASH.set(true);
        try {
            for (net.minecraft.world.entity.LivingEntity target : nearby) {
                double dist = primaryTarget.position().distanceTo(target.position());
                float t = (float) Math.min(dist / radius, 1.0);
                float edgeMul = 1.0f - falloff / 100.0f;
                float smoothMul = edgeMul + (1.0f - edgeMul) * (1.0f - t * t);
                float dmg = baseDamage * (splashPct / 100.0f) * smoothMul;
                if (dmg > 0) {
                    // 用无直接实体的 DamageSource 防止 modifyHurtAmount 二次放大
                    target.hurt(new net.minecraft.world.damagesource.DamageSource(
                        source.typeHolder(), null, player), dmg);
                }
            }
        } finally {
            IN_SPLASH.set(false);
        }
    }

    /** 判定候选实体是否应受到溅射伤害。 */
    private static boolean isValidSplashTarget(net.minecraft.world.entity.LivingEntity candidate,
                                               net.minecraft.world.entity.LivingEntity primaryTarget) {
        // 敌对生物(Monster 类别)始终命中
        if (candidate.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) return true;
        return candidate.getClass() == primaryTarget.getClass();
    }

    /** 安全读取前置库属性值，不存在返回 0（1.20.1：RegistryObject → .get()）。 */
    private static float safeAttr(net.minecraft.world.entity.LivingEntity entity,
                                  net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        if (attr == null || !attr.isPresent()) return 0f;
        var inst = entity.getAttribute(attr.get());
        return inst != null ? (float) inst.getValue() : 0f;
    }

    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        // 延迟到下一 tick 恢复（等 chunk 实体加载），从独立 SavedData 恢复被外部模组删掉的辖界者
        event.getServer().execute(() -> {
            for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) {
                try {
                    net.minecraft.client.yiz.xian.persistence.YizxianMobPersistence.restoreMobs(level);
                } catch (Throwable ignored) {}
            }
        });
    }

    /** 包装星级蛋 ItemStack 并写描边 NBT（创造标签页输出用；合成产出经 onCraftedBy 自写）。 */
    private static net.minecraft.world.item.ItemStack starEggStack(net.minecraft.world.item.Item item) {
        net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(item);
        if (item instanceof net.minecraft.client.yiz.xian.item.ChessSpawnEggItem egg) {
            net.minecraft.client.yiz.api.OutlineMarker.setLevel(s, egg.outlineLevel());
        }
        return s;
    }

    /** 创造页药水必须显式写 Potion NBT，否则 accept(Item) 生成的是无药水 NBT 的空瓶。 */
    private static net.minecraft.world.item.ItemStack legacyPotionStack(
            net.minecraft.world.item.Item item,
            net.minecraft.world.item.alchemy.Potion potion) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
        net.minecraft.world.item.alchemy.PotionUtils.setPotion(stack, potion);
        return stack;
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        // 退出存档/服务器停止时清空不死注册表，避免旧实体对象残留导致重进后同 UUID 实体重复
        // （多份血条/实例）。放在 ServerStopping 而不是 Stopped：此时实体还未被全部卸载，
        // 清空注册表即可让守护线程停止对残留旧对象的拉回。
        net.minecraft.client.yiz.xian.entity.base.YizxianMob.clearImmortalRegistry();
        // 释放 native 真值堆外内存（防泄漏；退出存档后旧实体已逐个 removeAuthority 释放，此处兜底全清）
        net.minecraft.client.yiz.tool.health.NativeHealthVault.freeAll();
    }
}
