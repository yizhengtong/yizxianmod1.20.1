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
 * 邪狱龙刷怪蛋（自定义，延迟解析实体类型，避免 ITEMS/ENTITY_TYPE 跨注册表时序问题——同辖界者）。
 */
public class XieyulongSpawnEggItem extends Item {

    public XieyulongSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            EntityType<?> entityType =
                net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.XIEYULONG.get();
            BlockPos pos = player.blockPosition().above();
            var mob = entityType.spawn(sl, stack, player, pos, MobSpawnType.SPAWN_EGG, false, false);
            if (mob == null) {
                player.displayClientMessage(Component.literal("§c生成邪狱龙失败"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.literal("§c已召唤邪狱龙"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
