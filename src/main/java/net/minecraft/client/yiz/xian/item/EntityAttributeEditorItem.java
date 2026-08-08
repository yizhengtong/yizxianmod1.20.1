package net.minecraft.client.yiz.xian.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.xian.menu.EntityAttributeEditMenu;

/**
 * 实体属性编辑工具（1.20.1 移植版）— 手持右键任意 LivingEntity 打开容器界面编辑其 yizmodqzk 属性。
 */
public class EntityAttributeEditorItem extends Item {

    public EntityAttributeEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!player.level().isClientSide() && player instanceof ServerPlayer sp) {
            // 1.20.1 无带数据 openMenu：openMenu 单参 + 独立 S2C 包推 targetId 给客户端 Screen
            sp.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("实体属性编辑");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
                    return new EntityAttributeEditMenu(containerId, inv, target.getId());
                }
            });
            // 服务端把目标实体 id 推给客户端 Screen（实体属性编辑目标同步）
            net.minecraft.client.yiz.xian.network.NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                new net.minecraft.client.yiz.xian.network.S2CEntityTargetPayload(target.getId()));
        }
        return InteractionResult.SUCCESS;
    }
}
