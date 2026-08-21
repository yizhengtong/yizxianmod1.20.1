package net.minecraft.client.yiz.xian.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * /yiz sx mzdk &lt;value&gt; — 给玩家主手物品添加灭在多空（DREAM_PERCENT / dream_percent）属性修饰符。
 *
 * <p>1 点 = 目标最大生命 1% 的真实伤害。固定修饰符 Name=sx_mzdk + 固定 UUID（派生自名，
 * 稳定唯一，AttributeMap 按 UUID 去重），重复执行覆盖更新数值，不无限叠加。
 * Slot=mainhand，主手持有时生效，攻击触发由 {@link net.minecraft.client.yiz.xian.mixin.PlayerAttackMixin}
 * 与 {@link net.minecraft.client.yiz.xian.network.C2SDreamAttackPayload} 负责。</p>
 */
public final class YizSxMzdkCommand {
    private YizSxMzdkCommand() {}

    /** 灭在多空修饰符固定 UUID。 */
    private static final UUID MZDK_MODIFIER_UUID =
        UUID.nameUUIDFromBytes("yiz:sx_mzdk".getBytes(StandardCharsets.UTF_8));
    private static final String MZDK_MODIFIER_NAME = "sx_mzdk";

    public static void register() {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("yiz")
            .then(Commands.literal("sx")
                .then(Commands.literal("mzdk")
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, Double.MAX_VALUE))
                        .executes(YizSxMzdkCommand::execute))));
        SimpleCommandRegistry.register(cmd);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "value");
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("§c主手没有物品"));
            return 0;
        }

        // 覆盖式更新：先移除主手物品旧的灭在多空修饰符，再添加新数值（重复执行不无限叠加）
        CompoundTag tag = held.getOrCreateTag();
        ListTag modifiers = tag.getList("AttributeModifiers", Tag.TAG_COMPOUND);
        for (int i = modifiers.size() - 1; i >= 0; i--) {
            CompoundTag m = modifiers.getCompound(i);
            if (MZDK_MODIFIER_NAME.equals(m.getString("Name"))) modifiers.remove(i);
        }

        CompoundTag mod = new CompoundTag();
        mod.putString("AttributeName", "yizmodqzk:dream_percent");
        mod.putString("Name", MZDK_MODIFIER_NAME);
        mod.putDouble("Amount", value);
        mod.putInt("Operation", 0); // ADDITION
        mod.putString("Slot", "mainhand");
        mod.put("UUID", NbtUtils.createUUID(MZDK_MODIFIER_UUID));
        modifiers.add(mod);
        tag.put("AttributeModifiers", modifiers);

        // 同步客户端刷新物品属性工具提示（主手背包槽）
        player.getInventory().setChanged();
        player.connection.send(new ClientboundContainerSetSlotPacket(
            0, player.containerMenu.getStateId(), player.getInventory().selected, held));

        source.sendSuccess(() -> Component.literal(
            "§a已为主手物品「" + held.getHoverName().getString() + "」添加灭在多空 §6" + value
                + "§a（1点=目标最大生命1%）"), true);
        return Command.SINGLE_SUCCESS;
    }
}
