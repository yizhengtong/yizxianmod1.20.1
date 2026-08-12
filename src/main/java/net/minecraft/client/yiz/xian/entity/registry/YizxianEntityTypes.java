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
}
