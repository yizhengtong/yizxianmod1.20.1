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
import net.minecraftforge.registries.RegistryObject;

/**
 * 自走棋星级生物蛋（通用，延迟解析实体类型）。
 *
 * <p>同一实体 3 个星级蛋（1/2/3 星），物品描边 OutlineMarker：1星=level0(白)、2星=level4(蓝)、3星=level6(金)。
 * 右键生成对应费用档 + 星级的棋子实体：写 {@code ChessUnitTable} 外部表 + DataParameter 镜像，
 * 实体首个服务端 tick 经 {@code YizxianMob.applyChessStarIfNeeded} 放大随倍率属性。</p>
 */
public class ChessSpawnEggItem extends Item {

    private final RegistryObject<? extends EntityType<?>> entityType;
    private final int cost;
    private final int star;

    public ChessSpawnEggItem(Properties properties,
                             RegistryObject<? extends EntityType<?>> entityType,
                             int cost, int star) {
        super(properties);
        this.entityType = entityType;
        this.cost = cost;
        this.star = Math.max(1, Math.min(3, star));
    }

    /** 星级蛋描边 level：1星=白(0)、2星=蓝(4)、3星=金(6)。 */
    public int outlineLevel() {
        return switch (this.star) {
            case 2 -> 4;
            case 3 -> 6;
            default -> 0;
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            EntityType<?> type = this.entityType.get();
            BlockPos pos = player.blockPosition().above();
            var mob = type.spawn(sl, stack, player, pos, MobSpawnType.SPAWN_EGG, false, false);
            if (mob == null) {
                player.displayClientMessage(Component.literal("§c生成棋子失败"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (mob instanceof net.minecraft.client.yiz.xian.entity.base.YizxianMob ym) {
                net.minecraft.client.yiz.tool.chess.ChessUnitTable.init(ym, this.cost, this.star);
                ym.syncChessToClient();
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.literal("§e已召唤 " + this.cost + "费" + this.star + "星棋子"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** 合成产出（3×低星蛋→1×高星蛋）时给结果蛋写入描边 NBT。 */
    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        net.minecraft.client.yiz.api.OutlineMarker.setLevel(stack, outlineLevel());
    }
}
