package net.minecraft.client.yiz.xian.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;
import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class YizDhCommand {

    private YizDhCommand() {}

    public static void register() {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("yiz")
            .then(Commands.literal("dh")
                .then(Commands.literal("xjz")
                    .then(Commands.literal("skill")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                            .executes(YizDhCommand::execute)))));
        SimpleCommandRegistry.register(cmd);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(ctx, "index");
        AABB aabb = player.getBoundingBox().inflate(5.0);
        List<QuanshouzheEntity> list = player.level().getEntitiesOfClass(
            QuanshouzheEntity.class, aabb, e -> e.isAlive());
        for (QuanshouzheEntity e : list) {
            playSkill(e, index);
        }
        source.sendSuccess(() -> Component.literal(
            "§a已让周围 5 格内 §6" + list.size() + "§a 只辖界者播放技能 §6" + index + "§a 动画"), true);
        return list.isEmpty() ? 0 : Command.SINGLE_SUCCESS;
    }

    private static void playSkill(QuanshouzheEntity e, int index) {
        switch (index) {
            case 2 -> e.level().broadcastEntityEvent(e, (byte) 59);
            case 3 -> e.level().broadcastEntityEvent(e, (byte) 58);
            default -> e.level().broadcastEntityEvent(e, (byte) 60);
        }
    }
}
