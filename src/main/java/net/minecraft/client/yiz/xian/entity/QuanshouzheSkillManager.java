package net.minecraft.client.yiz.xian.entity;

import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * 辖界者技能执行器（1.20.1 移植版）— 当前纯近战，无远程技能。
 */
public final class QuanshouzheSkillManager {

    private QuanshouzheSkillManager() {}

    public static void execute(QuanshouzheEntity boss, int phase, LivingEntity target) {
        if (boss.level().isClientSide() || !boss.isAlive()) return;
        // 当前辖界者仅近战（MeleeGoal 直接 doHurtTarget），无远程技能
    }

    public static void clear(UUID uuid) {}
}
