package net.minecraft.client.yiz.xian.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 辖界者刷怪蛋（自定义，延迟解析实体类型）。
 *
 * <p><b>为什么不用原版 {@code SpawnEggItem}：</b>原版 {@code SpawnEggItem} 构造需要 {@code EntityType}
 * 实例，而 Forge 的 ITEMS 注册事件早于 ENTITY_TYPE 注册事件——生物蛋 DeferredRegister supplier 里
 * 直接 {@code QUANSHOUZHE.get()} 会抛 {@code Registry Object not present}（跨注册表时序问题）。
 * 本类在 {@code use} 时刻才解析实体类型（此时实体注册表已就绪），右键在脚下生成辖界者。</p>
 */
public class QuanshouzheSpawnEggItem extends Item {

    public QuanshouzheSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            EntityType<?> entityType =
                net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.QUANSHOUZHE.get();
            // 在玩家脚下上方生成（对齐玩家朝向）
            BlockPos pos = player.blockPosition().above();
            var mob = entityType.spawn(sl, stack, player, pos, MobSpawnType.SPAWN_EGG, false, false);
            if (mob == null) {
                player.displayClientMessage(Component.literal("§c生成辖界者失败"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.literal("§c已召唤辖界者"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
