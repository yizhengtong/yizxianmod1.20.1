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
     * 铁斗士（三费棋子）。碰撞箱按模型比例（1.0×2.7，模型高 43 单位），纯近战 + 满蓝释放 skill1。
     */
    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.TiedoushiEntity>> TIEDOUSHI =
        ENTITY_TYPES.register("tiedoushi", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.TiedoushiEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.TiedoushiEntity(type, level),
            MobCategory.MISC)
            .sized(1.0f, 2.7f)
            .build("tiedoushi"));

    /**
     * 怒翼（一费棋子）。碰撞箱照原版幻翼（0.9×0.5）：飞行单位的碰撞箱只围躯干，
     * 翼展（±1.3 格）按原版惯例不进碰撞箱；俯冲判定的 {@code inflate(0.2)} 与原版一致。
     */
    public static final RegistryObject<EntityType<net.minecraft.client.yiz.xian.entity.NuyiEntity>> NUYI =
        ENTITY_TYPES.register("nuyi", () -> EntityType.Builder.of(
            (EntityType<net.minecraft.client.yiz.xian.entity.NuyiEntity> type,
             net.minecraft.world.level.Level level) ->
                new net.minecraft.client.yiz.xian.entity.NuyiEntity(type, level),
            MobCategory.MISC)
            .sized(0.9f, 0.5f)
            .clientTrackingRange(8)
            .build("nuyi"));
}
