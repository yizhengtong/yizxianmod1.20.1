package net.minecraft.client.yiz.xian.entity.registry;

import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 生物实体注册中心（1.20.1 移植版）。
 */
public final class YizxianEntityTypes {

    private YizxianEntityTypes() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, YizxianMod.MODID);

    /**
     * 辖界者 Boss。碰撞箱按铁傀儡比例（1.4×2.8）。
     */
    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.QuanshouzheEntity>> QUANSHOUZHE =
        ENTITY_TYPES.register("quanshouzhe", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.QuanshouzheEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.QuanshouzheEntity(type, level),
            MobCategory.MONSTER)
            .sized(1.3f, 2.8f)
            .fireImmune()
            .build("quanshouzhe"));

    /**
     * 邪狱龙（踏虚体邪狱龙）Boss。碰撞箱按源模组比例（3.0×6.0），GeckoLib 动画。
     */
    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.XieyulongEntity>> XIEYULONG =
        ENTITY_TYPES.register("xieyulong", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.XieyulongEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.XieyulongEntity(type, level),
            MobCategory.MONSTER)
            .sized(3.0f, 6.0f)
            .fireImmune()
            .build("xieyulong"));

    /**
     * 邪狱龙三技能弹道实体（火球/陨石/毒云，无 AI 无碰撞箱，无渲染器靠 vanilla 粒子）。
     */
    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.skill.XieyulongFireEntity>> XIEYULONG_FIRE =
        ENTITY_TYPES.register("xieyulong_fire", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.skill.XieyulongFireEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.skill.XieyulongFireEntity(type, level),
            MobCategory.MISC)
            .sized(0.2f, 0.2f)
            .build("xieyulong_fire"));

    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.skill.XieyulongMeteorEntity>> XIEYULONG_METEOR =
        ENTITY_TYPES.register("xieyulong_meteor", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.skill.XieyulongMeteorEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.skill.XieyulongMeteorEntity(type, level),
            MobCategory.MISC)
            .sized(0.2f, 0.2f)
            .build("xieyulong_meteor"));

    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.skill.XieyulongPoisonCloudEntity>> XIEYULONG_POISON_CLOUD =
        ENTITY_TYPES.register("xieyulong_poison_cloud", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.skill.XieyulongPoisonCloudEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.skill.XieyulongPoisonCloudEntity(type, level),
            MobCategory.MISC)
            .sized(1.0f, 1.0f)
            .build("xieyulong_poison_cloud"));

    /**
     * 踏虚体（虚末麟/kirin）Boss。碰撞箱按源模组比例（4.0×8.0），地面近战 + 传送 + orb。
     */
    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.TaxutiEntity>> TAXUTI =
        ENTITY_TYPES.register("taxuti", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.TaxutiEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.TaxutiEntity(type, level),
            MobCategory.MONSTER)
            .sized(4.0f, 8.0f)
            .fireImmune()
            .build("taxuti"));

    /** 踏虚体 orb 球技能弹道（无渲染器，vanilla 粒子显示）。 */
    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.skill.TaxutiOrbEntity>> TAXUTI_ORB =
        ENTITY_TYPES.register("taxuti_orb", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.skill.TaxutiOrbEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.skill.TaxutiOrbEntity(type, level),
            MobCategory.MISC)
            .sized(1.0f, 1.0f)
            .build("taxuti_orb"));

    /**
     * 山林首者（Warden 同人系，铁傀儡属性）。碰撞箱按铁傀儡比例（1.4×2.7）。
     */
    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.ShanlinshouzheEntity>> SHANLINSHOUZHE =
        ENTITY_TYPES.register("shanlinshouzhe", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.ShanlinshouzheEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.ShanlinshouzheEntity(type, level),
            MobCategory.MONSTER)
            .sized(1.4f, 2.7f)
            .fireImmune()
            .build("shanlinshouzhe"));
}
