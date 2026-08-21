package net.minecraft.client.yiz.xian.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * 移除闸门统一鉴权 —— 各闸门共用同一套调用来源判定，避免每个拦截点各写一份判据后互相跑偏。
 *
 * <p>判定方式是「调用栈上第一个决定性帧」：跳过引擎自身在该拦截点必然出现的框架帧后，
 * 看第一个真正的调用者属于谁。<b>必须跳过注入帧自身</b>，否则注入方法所在的包会恒命中
 * 本模组白名单，使整个闸门形同虚设。</p>
 */
public final class RemovalGateAuth {

    /** 本模组类的公共包前缀（前置库与下游共用同一根）。 */
    private static final String OWN_PACKAGE = "net.minecraft.client.yiz";
    /** 注入帧所在包：这些帧属于闸门自身，判定时必须跳过。 */
    private static final String GATE_PACKAGE = "net.minecraft.client.yiz.xian.mixin";

    private RemovalGateAuth() {}

    /**
     * 调用来源是否为本模组。
     *
     * @param frameworkFrames 该拦截点上需要跳过的引擎框架帧类名（目标类自身及其转调链）
     */
    public static boolean isOwnCaller(String... frameworkFrames) {
        String caller = decisiveCaller(frameworkFrames);
        return caller != null && caller.startsWith(OWN_PACKAGE);
    }

    /**
     * 调用来源是否为引擎自身（原版 / Forge / Mojang 库）。
     *
     * <p>用于区分「引擎在正常生命周期里操作实体」与「外部代码直接操作实体」：
     * 前者必须放行（区块卸载、存档写出、维度流转都依赖它），后者才是闸门要拦的对象。</p>
     */
    public static boolean isEngineCaller(String... frameworkFrames) {
        String caller = decisiveCaller(frameworkFrames);
        if (caller == null) return true;
        return caller.startsWith("net.minecraft.")
            || caller.startsWith("net.minecraftforge.")
            || caller.startsWith("com.mojang.");
    }

    /**
     * 外部代码是否正在直接操作该实体的世界结构 —— 闸门的拦截条件。
     *
     * <p>放行：引擎自身、本模组、停机保存期、以及确已死亡（权威血量 ≤0）的实体。</p>
     */
    public static boolean isForeignStructureAccess(Entity entity, String... frameworkFrames) {
        if (entity == null) return false;
        if (entity.level().isClientSide()) return false;
        if (entity.level() instanceof ServerLevel sl && EntityRemoveProtection.isShuttingDownOrSaving(sl)) return false;
        if (isDeadByAuthority(entity)) return false;
        String caller = decisiveCaller(frameworkFrames);
        if (caller == null) return false;
        if (caller.startsWith(OWN_PACKAGE)) return false;
        return !caller.startsWith("net.minecraft.")
            && !caller.startsWith("net.minecraftforge.")
            && !caller.startsWith("com.mojang.");
    }

    /** 权威血量是否已归零（真实死亡的实体不再受结构保护，正常走完死亡与清理流程）。 */
    public static boolean isDeadByAuthority(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return false;
        try {
            if (!net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(living)) return false;
            return net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(living) <= 0.0F;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 取调用栈上第一个决定性帧的类名：跳过本方法自身、闸门注入帧、以及调用方声明的框架帧。
     *
     * @return 第一个决定性调用者的类名；整条栈都是被跳过的帧时返回 null
     */
    private static String decisiveCaller(String... frameworkFrames) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String cn = element.getClassName();
            if (cn.equals("java.lang.Thread")) continue;
            if (cn.equals(RemovalGateAuth.class.getName())) continue;
            if (cn.startsWith(GATE_PACKAGE)) continue;
            boolean framework = false;
            for (String frame : frameworkFrames) {
                if (cn.equals(frame)) {
                    framework = true;
                    break;
                }
            }
            if (framework) continue;
            return cn;
        }
        return null;
    }
}
