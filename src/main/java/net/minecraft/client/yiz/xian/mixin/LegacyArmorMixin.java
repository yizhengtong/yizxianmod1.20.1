package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.item.legacy.LegacyArmorHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.8.9 怀旧盔甲的「固定减伤」接管。
 *
 * <p>1.8 公式：{@code 伤害 × (1 − min(25, 护甲) / 25)}，每点 4%、上限 25 点/100%，与单次伤害大小无关，
 * 也不存在护甲韧性。1.20.1 的 {@link CombatRules#getDamageAfterAbsorb} 会按
 * {@code clamp(护甲 − 伤害/(2+韧性/4), 护甲×0.2, 20)} 计算，大伤害时减免明显变小。</p>
 *
 * <p>本 mixin 只接管「怀旧四件套贡献的护甲点」：这部分走 1.8 固定减伤，完全忽略韧性；
 * 玩家身上其余来源的护甲点（原版/其他模组）仍按 1.20.1 公式计算。混穿时两边各自生效。</p>
 *
 * <p>在 HEAD 取消原方法，因此必须自己调用 {@code hurtArmor}（通过 {@link LegacyArmorInvoker}），
 * 保证盔甲耐久仍按原始伤害扣。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LegacyArmorMixin {

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
    private void yizxianmod$legacyArmorAbsorb(DamageSource source, float damage,
                                             CallbackInfoReturnable<Float> cir) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        // 任意 LivingEntity 都走同一套 1.8 固定减伤（玩家、僵尸、骷髅、自定义实体等）。
        if (!((Object) this instanceof LivingEntity living)) {
            return;
        }

        int legacy = LegacyArmorHelper.legacyPoints(living);
        if (legacy <= 0) {
            return;
        }

        // 先取快照：hurtArmor 可能打碎某件甲，之后的 getArmorValue() 会变。
        int otherArmor = Math.max(0, living.getArmorValue() - legacy);
        float toughness = (float) living.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        // 与原版一致：先给盔甲扣耐久（用原始伤害）。
        ((LegacyArmorInvoker) (Object) this).yizxianmod$hurtArmor(source, damage);

        // 怀旧部分：固定 4% / 点，上限 25 点/100%（极尽四件 24 点 = 96%）。
        float afterLegacy = damage * (1.0F - Math.min(25, legacy) / 25.0F);

        // 其余护甲点：保持 1.20.1 公式（含韧性）。
        cir.setReturnValue(CombatRules.getDamageAfterAbsorb(afterLegacy, (float) otherArmor, toughness));
    }
}
