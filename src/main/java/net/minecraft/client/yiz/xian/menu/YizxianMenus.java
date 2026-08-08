package net.minecraft.client.yiz.xian.menu;

import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 容器 MenuType 注册中心（1.20.1 移植版）。
 */
public final class YizxianMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, YizxianMod.MODID);

    /** 实体属性编辑容器：目标实体 id 经 S2C 包同步（1.20.1 无带数据 openMenu，extraData 可能为 null）。 */
    public static final RegistryObject<MenuType<EntityAttributeEditMenu>> ENTITY_ATTRIBUTE_EDIT_MENU =
        MENUS.register("entity_attribute_edit", () -> IForgeMenuType.create(
            (containerId, playerInventory, data) ->
                new EntityAttributeEditMenu(containerId, playerInventory,
                    data != null ? data.readInt() : -1)));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }

    private YizxianMenus() {}
}
