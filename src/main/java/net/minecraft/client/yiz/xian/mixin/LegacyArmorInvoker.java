package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link LivingEntity#hurtArmor}（protected）给 {@link LegacyArmorMixin}。
 * 在 HEAD 取消 {@code getDamageAfterArmorAbsorb} 后需要手动补上盔甲耐久结算。
 */
@Mixin(LivingEntity.class)
public interface LegacyArmorInvoker {

    @Invoker("hurtArmor")
    void yizxianmod$hurtArmor(DamageSource source, float damage);
}
