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

    /** 实体属性编辑工具（右键实体打开容器界面编辑其 yizmodqzk 自定义属性）。 */
    public static final RegistryObject<Item> ENTITY_ATTRIBUTE_EDITOR =
        ITEMS.register("entity_attribute_editor", () ->
            new net.minecraft.client.yiz.xian.item.EntityAttributeEditorItem(new Item.Properties().stacksTo(1)));

    /** 辖界者生物蛋（创造标签页用，右键生成 Boss；自定义延迟解析实体类型，避免跨注册表时序）。 */
    public static final RegistryObject<Item> QUANSHOUZHE_SPAWN_EGG =
        ITEMS.register("quanshouzhe_spawn_egg", () ->
            new net.minecraft.client.yiz.xian.item.QuanshouzheSpawnEggItem(new Item.Properties()));

    /** 邪狱龙生物蛋（踏虚体邪狱龙，GeckoLib 动画 Boss）。 */
    public static final RegistryObject<Item> XIEYULONG_SPAWN_EGG =
        ITEMS.register("xieyulong_spawn_egg", () ->
            new net.minecraft.client.yiz.xian.item.XieyulongSpawnEggItem(new Item.Properties()));

    /** 踏虚体生物蛋（GeckoLib 动画 Boss）。 */
    public static final RegistryObject<Item> TAXUTI_SPAWN_EGG =
        ITEMS.register("taxuti_spawn_egg", () ->
            new net.minecraft.client.yiz.xian.item.TaxutiSpawnEggItem(new Item.Properties()));

    // ── 自走棋星级生物蛋（辖界者=5费档 ×1/×2/×6；踏虚体/邪狱龙=7费档 ×1/×2.5/×9）──
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

    public static final RegistryObject<Item> XIEYULONG_STAR1 =
        ITEMS.register("xieyulong_spawn_egg_star1", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.XIEYULONG, 7, 1));
    public static final RegistryObject<Item> XIEYULONG_STAR2 =
        ITEMS.register("xieyulong_spawn_egg_star2", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.XIEYULONG, 7, 2));
    public static final RegistryObject<Item> XIEYULONG_STAR3 =
        ITEMS.register("xieyulong_spawn_egg_star3", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.XIEYULONG, 7, 3));

    public static final RegistryObject<Item> TAXUTI_STAR1 =
        ITEMS.register("taxuti_spawn_egg_star1", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.TAXUTI, 7, 1));
    public static final RegistryObject<Item> TAXUTI_STAR2 =
        ITEMS.register("taxuti_spawn_egg_star2", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.TAXUTI, 7, 2));
    public static final RegistryObject<Item> TAXUTI_STAR3 =
        ITEMS.register("taxuti_spawn_egg_star3", () ->
            new net.minecraft.client.yiz.xian.item.ChessSpawnEggItem(new Item.Properties(), YizxianEntityTypes.TAXUTI, 7, 3));

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
                    output.accept(XIEYULONG_SPAWN_EGG.get());
                    output.accept(TAXUTI_SPAWN_EGG.get());
                    output.accept(ENTITY_ATTRIBUTE_EDITOR.get());
                    // 自走棋星级蛋（带描边 NBT 白/蓝/金）
                    output.accept(starEggStack(QUANSHOUZHE_STAR1.get()));
                    output.accept(starEggStack(QUANSHOUZHE_STAR2.get()));
                    output.accept(starEggStack(QUANSHOUZHE_STAR3.get()));
                    output.accept(starEggStack(XIEYULONG_STAR1.get()));
                    output.accept(starEggStack(XIEYULONG_STAR2.get()));
                    output.accept(starEggStack(XIEYULONG_STAR3.get()));
                    output.accept(starEggStack(TAXUTI_STAR1.get()));
                    output.accept(starEggStack(TAXUTI_STAR2.get()));
                    output.accept(starEggStack(TAXUTI_STAR3.get()));
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
                })
                .build());

    public YizxianMod() {
        var modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();

        // 生物实体注册（辖界者）
        YizxianEntityTypes.ENTITY_TYPES.register(modEventBus);

        // 实体属性编辑工具 + 菜单 + 网络
        ITEMS.register(modEventBus);
        // 创造标签页（生物蛋 + 编辑器）
        CREATIVE_TABS.register(modEventBus);
        net.minecraft.client.yiz.xian.menu.YizxianMenus.register(modEventBus);

        // 实体属性创建（辖界者 AttributeSupplier）
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(YizxianEntityTypes.QUANSHOUZHE.get(),
                net.minecraft.client.yiz.xian.entity.QuanshouzheEntity.createAttributes().build());
        });

        // 实体属性创建（邪狱龙 AttributeSupplier）
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(YizxianEntityTypes.XIEYULONG.get(),
                net.minecraft.client.yiz.xian.entity.XieyulongEntity.createAttributes().build());
        });

        // 实体属性创建（踏虚体 AttributeSupplier）
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(YizxianEntityTypes.TAXUTI.get(),
                net.minecraft.client.yiz.xian.entity.TaxutiEntity.createAttributes().build());
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
        // 加入事件反否决：本模组实体的加入不接受外部取消
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
            net.minecraft.client.yiz.xian.core.EntityPresenceEventGuard.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Yiz Xian Mod 1.20.1 初始化完成");
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

    //  范围溅射系统（1.21.1 移植：SPLASH_RADIUS/DAMAGE/FALLOFF 属性驱动）

    /** 溅射递归保护：溅射伤害的 hurt 不再触发溅射。 */
    private static final ThreadLocal<Boolean> IN_SPLASH = ThreadLocal.withInitial(() -> false);

    /** 攻击命中 LivingDamageEvent：读 SPLASH_* 属性触发范围溅射。 */
    @SubscribeEvent
    public void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player player)) return;
        if (player.level().isClientSide) return;
        if (IN_SPLASH.get()) return;

        // 1.20.1 无武器 Profile（MeleeWeaponItem/WeaponLevelData 未移植，见 port-gap-list），
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

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        // 退出存档/服务器停止时清空不死注册表，避免旧实体对象残留导致重进后同 UUID 实体重复
        // （多份血条/实例）。放在 ServerStopping 而不是 Stopped：此时实体还未被全部卸载，
        // 清空注册表即可让守护线程停止对残留旧对象的拉回。
        net.minecraft.client.yiz.xian.entity.base.YizxianMob.clearImmortalRegistry();
    }
}
