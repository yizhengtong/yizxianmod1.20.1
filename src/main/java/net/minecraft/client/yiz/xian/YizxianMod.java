package net.minecraft.client.yiz.xian;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes;

/**
 * Yiz Xian Mod — 1.20.1 Forge 内容模组（依赖前置库 yizmodqzk）。
 */
@Mod(YizxianMod.MODID)
public class YizxianMod {

    public static final String MODID = "yizxianmod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    /** 实体属性编辑工具（右键实体打开容器界面编辑其 yizmodqzk 自定义属性）。 */
    public static final RegistryObject<Item> ENTITY_ATTRIBUTE_EDITOR =
        ITEMS.register("entity_attribute_editor", () ->
            new net.minecraft.client.yiz.xian.item.EntityAttributeEditorItem(new Item.Properties().stacksTo(1)));

    public YizxianMod() {
        var modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();

        // 生物实体注册（辖界者）
        YizxianEntityTypes.ENTITY_TYPES.register(modEventBus);

        // 实体属性编辑工具 + 菜单 + 网络
        ITEMS.register(modEventBus);
        net.minecraft.client.yiz.xian.menu.YizxianMenus.register(modEventBus);

        // 实体属性创建（辖界者 AttributeSupplier）
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(YizxianEntityTypes.QUANSHOUZHE.get(),
                net.minecraft.client.yiz.xian.entity.QuanshouzheEntity.createAttributes().build());
        });

        modEventBus.addListener(this::commonSetup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Yiz Xian Mod 1.20.1 初始化完成");
        // 注册 SimpleChannel 网络
        net.minecraft.client.yiz.xian.network.NetworkHandler.register();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.debug("Yiz Xian Mod 服务端启动");
    }
}
