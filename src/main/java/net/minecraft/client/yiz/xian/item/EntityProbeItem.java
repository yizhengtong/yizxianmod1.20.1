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

import net.minecraft.client.yiz.xian.menu.EntityProbeMenu;
import net.minecraft.client.yiz.xian.network.S2CEntityProbeTargetPayload;

/**
 * 实体探查镜 — 手持右键任意 LivingEntity，服务端 openMenu 打开探查 GUI。
 *
 * <p>1.20.1 无带数据 openMenu：菜单本身只承载容器 id，被探查目标实体 id 经独立 S2C 包
 * S2CEntityProbeTargetPayload 推给客户端 Screen（与实体属性编辑工具同套路）。</p>
 */
public class EntityProbeItem extends Item {

    public EntityProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!player.level().isClientSide() && player instanceof ServerPlayer sp) {
            sp.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.yizxianmod.entity_probe");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
                    return new EntityProbeMenu(containerId, inv, target);
                }
            });
            // 服务端把被探查目标实体 id 推给客户端 Screen / Menu
            net.minecraft.client.yiz.xian.network.NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                new S2CEntityProbeTargetPayload(target.getId()));
            // 首次蓝量快照（打开瞬间）
            net.minecraft.client.yiz.xian.network.NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                new net.minecraft.client.yiz.xian.network.S2CEntityProbeBluePayload(
                    net.minecraft.client.yiz.tool.health.ManaTracker.get(target),
                    net.minecraft.client.yiz.tool.health.ManaTracker.getMax(target)));
        }
        return InteractionResult.SUCCESS;
    }
}
