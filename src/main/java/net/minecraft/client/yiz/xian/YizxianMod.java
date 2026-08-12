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

    /** 辖界者生物蛋（创造标签页用，右键生成 Boss；自定义延迟解析实体类型，避免跨注册表时序）。 */
    public static final RegistryObject<Item> QUANSHOUZHE_SPAWN_EGG =
        ITEMS.register("quanshouzhe_spawn_egg", () ->
            new net.minecraft.client.yiz.xian.item.QuanshouzheSpawnEggItem(new Item.Properties()));

    /** 创造模式标签页：生物蛋 + 编辑器。 */
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<net.minecraft.world.item.CreativeModeTab> TAB =
        CREATIVE_TABS.register("main", () ->
            net.minecraft.world.item.CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.yizxianmod"))
                .icon(() -> new net.minecraft.world.item.ItemStack(QUANSHOUZHE_SPAWN_EGG.get()))
                .displayItems((params, output) -> {
                    output.accept(QUANSHOUZHE_SPAWN_EGG.get());
                    output.accept(ENTITY_ATTRIBUTE_EDITOR.get());
                })
                .build());

    public YizxianMod() {
        var modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();

        // 生物实体注册（辖界者）
        YizxianEntityTypes.ENTITY_TYPES.register(modEventBus);

        // 实体属性编辑工具 + 菜单 + 网络
        ITEMS.register(modEventBus);
        // 创造标签页（生物蛋 + 编辑器）
        CREATIVE_TABS.register(modEventBus);
        net.minecraft.client.yiz.xian.menu.YizxianMenus.register(modEventBus);

        // 实体属性创建（辖界者 AttributeSupplier）
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(YizxianEntityTypes.QUANSHOUZHE.get(),
                net.minecraft.client.yiz.xian.entity.QuanshouzheEntity.createAttributes().build());
        });

        // 给玩家默认属性加涨跌多空（FIRST_DREAM/long_short，默认 0）：否则物品修饰符作用在玩家上时
        // getAttribute(FIRST_DREAM) 返回 null、修饰符不生效 → /yiz sx zddk 加的物品属性无效
        modEventBus.addListener((net.minecraftforge.event.entity.EntityAttributeModificationEvent e) -> {
            e.add(net.minecraft.world.entity.EntityType.PLAYER,
                net.minecraft.client.yiz.attribute.YizAttributes.FIRST_DREAM.get());
        });

        modEventBus.addListener(this::commonSetup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Yiz Xian Mod 1.20.1 初始化完成");
        // 注册 SimpleChannel 网络
        net.minecraft.client.yiz.xian.network.NetworkHandler.register();
        // /yiz sx zddk <数值>：给主手物品添加涨跌多空属性修饰符（SimpleCommandRegistry 已由前置库订阅）
        net.minecraft.client.yiz.xian.command.YizSxCommand.register();
        // /yiz dh xjz skill <index>：让周围 5 格内辖界者播放技能动画（气丹）
        net.minecraft.client.yiz.xian.command.YizDhCommand.register();
        // /yiz gj xjz skill <index> <count>：让周围 5 格内辖界者接下来 count 次攻击都用 index 技能
        net.minecraft.client.yiz.xian.command.YizGjCommand.register();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.debug("Yiz Xian Mod 服务端启动");
    }
}
