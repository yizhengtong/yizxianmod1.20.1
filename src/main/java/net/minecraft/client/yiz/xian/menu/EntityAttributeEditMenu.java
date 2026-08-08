package net.minecraft.client.yiz.xian.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 实体属性编辑容器 Menu（1.20.1 移植版）。
 *
 * <p>服务端打开时把目标实体 id 经 IForgeMenuType 额外数据传给客户端，客户端 Menu 只存实体 id；
 * 实际写入走 C2S 网络包。</p>
 */
public class EntityAttributeEditMenu extends AbstractContainerMenu {

    private final int targetEntityId;

    public EntityAttributeEditMenu(int containerId, Inventory playerInv, int targetEntityId) {
        super(YizxianMenus.ENTITY_ATTRIBUTE_EDIT_MENU.get(), containerId);
        this.targetEntityId = targetEntityId;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    public int getTargetEntityId() {
        return targetEntityId;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
