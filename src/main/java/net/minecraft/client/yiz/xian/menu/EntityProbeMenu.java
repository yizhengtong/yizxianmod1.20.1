package net.minecraft.client.yiz.xian.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.yiz.xian.EntityProbeGuiSpec;
import net.minecraft.client.yiz.xian.client.layout.GuiLayoutConfig;

import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * 实体探查容器 Menu —— A6 装备槽为「真实槽位」。
 *
 * <p>把被探查实体的 5 个装备槽(头/胸/腿/靴/主手)包装成真正的 {@link Container}（{@link EntityEquipContainer}），
 * 菜单槽全部绑定其上；点取、Shift 快捷、数字键、拖拽全部走原版容器机制，改动直接写穿到实体装备槽。</p>
 *
 * <p>槽位：0..4 A6 装备 · 5..31 玩家背包主格 · 32..40 快捷栏。</p>
 */
public class EntityProbeMenu extends AbstractContainerMenu {

    /** A6 五个槽对应实体装备槽。 */
    private static final EquipmentSlot[] EQ = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND
    };
    private static final int EQUIP_START = 0;
    private static final int EQUIP_END = EQ.length;           // 5
    private static final int INV_START = EQ.length;           // 5
    private static final int INV_END = 5 + 36;                // 41

    private static final int MAINHAND_INDEX = EQ.length - 1;

    /** 客户端经 S2C 同步来的被探查实体 id。 */
    private static volatile int receivedTargetId = -1;

    public static void onTargetReceived(int targetId) {
        receivedTargetId = targetId;
    }

    private final LivingEntity serverTarget;        // 服务端权威实体（换装执行）
    private final int targetEntityId;
    private final Inventory playerInventory;
    private final Supplier<LivingEntity> targetSupplier;
    private final EntityEquipContainer equipContainer;

    /** 服务端构造：持有实体引用。 */
    public EntityProbeMenu(int containerId, Inventory playerInv, LivingEntity target) {
        super(YizxianMenus.ENTITY_PROBE_MENU.get(), containerId);
        this.serverTarget = target;
        this.targetEntityId = target != null ? target.getId() : -1;
        this.playerInventory = playerInv;
        this.targetSupplier = () -> serverTarget;
        this.equipContainer = new EntityEquipContainer(this::equipTargetLiving);
        bindEquipment();
        bindPlayerInventory(playerInv);
    }

    /** 客户端构造（MenuType 工厂）：无实体引用，装备容器按 S2C id 动态解析。 */
    public EntityProbeMenu(int containerId, Inventory playerInv, int targetEntityId) {
        super(YizxianMenus.ENTITY_PROBE_MENU.get(), containerId);
        this.serverTarget = null;
        this.targetEntityId = targetEntityId;
        this.playerInventory = playerInv;
        this.targetSupplier = this::resolveClientTarget;
        this.equipContainer = new EntityEquipContainer(this::equipTargetLiving);
        if (targetEntityId >= 0) receivedTargetId = targetEntityId;
        bindEquipment();
        bindPlayerInventory(playerInv);
    }

    // 布局键与 Screen 一致
    private static final String LAYOUT_KEY = "entity_probe";

    // Slot.x/y 为 final：拖动槽位宿主元素时用反射即时改写（高版本同款做法）
    private static final Field SLOT_X = slotField("x");
    private static final Field SLOT_Y = slotField("y");

    private static Field slotField(String name) {
        try {
            Field f = Slot.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void setSlotXY(Slot slot, int x, int y) {
        try {
            if (SLOT_X != null) SLOT_X.setInt(slot, x);
            if (SLOT_Y != null) SLOT_Y.setInt(slot, y);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** 按元素树当前布局（含编辑器即时改动）重排全部真实槽位坐标。 */
    public void applyLiveSlotLayout() {
        int aX = offX("A", EntityProbeGuiSpec.A_OX), aY = offY("A", EntityProbeGuiSpec.A_OY);
        int a6X = offX("A6", EntityProbeGuiSpec.A6_X), a6Y = offY("A6", EntityProbeGuiSpec.A6_Y);
        for (int i = 0; i < EQ.length && i < this.slots.size(); i++) {
            int x = aX + a6X + offX("c" + i, i * EntityProbeGuiSpec.A6_CELL);
            int y = aY + a6Y + offY("c" + i, 0);
            setSlotXY(this.slots.get(i), x, y);
        }
        float inner = guiScale() * cs("inv");
        int ox = offX("inv", EntityProbeGuiSpec.INV_OFF_X), oy = offY("inv", EntityProbeGuiSpec.INV_OFF_Y);
        int idx = 0;
        for (int i = INV_START; i < INV_END && i < this.slots.size(); i++) {
            if (idx < 27) {
                setSlotXY(this.slots.get(i),
                    ox + Math.round(EntityProbeGuiSpec.invMainX(idx) * inner),
                    oy + Math.round(EntityProbeGuiSpec.invMainY(idx) * inner));
            } else {
                int col = idx - 27;
                setSlotXY(this.slots.get(i),
                    ox + Math.round(EntityProbeGuiSpec.invHotX(col) * inner),
                    oy + Math.round(EntityProbeGuiSpec.invHotY() * inner));
            }
            idx++;
        }
    }

    /** 读取元素树节点相对父的本地偏移（编辑器可改）；无覆盖返回默认。已乘整 GUI 缩放。 */
    private static float guiScale() {
        return 1f; // 缩放已关闭
    }

    /** 节点本地偏移 → 根坐标系贡献：偏移 × 整缩放 × 父级联 scale。 */
    private static int offX(String node, float def) {
        float raw = def;
        GuiLayoutConfig.Layout l = GuiLayoutConfig.rawNode(LAYOUT_KEY, node);
        if (l != null) raw = l.x();
        return Math.round(raw * guiScale() * cs(nodeParent(node)));
    }

    private static int offY(String node, float def) {
        float raw = def;
        GuiLayoutConfig.Layout l = GuiLayoutConfig.rawNode(LAYOUT_KEY, node);
        if (l != null) raw = l.y();
        return Math.round(raw * guiScale() * cs(nodeParent(node)));
    }

    // ── 元素级缩放级联（与 Screen 同步）：cs(id)= 祖先至本节点 scale 之积，cs('')=1 ──
    private static float nodeScale(String id) {
        return 1f; // 元素缩放已关闭
    }

    private static String nodeParent(String id) {
        if (id == null || id.isEmpty() || id.equals("inv") || id.equals("A")) return "";
        if (id.startsWith("c")) return "A6";
        return "A";
    }

    private static float cs(String id) {
        if (id == null || id.isEmpty()) return 1f;
        return cs(nodeParent(id)) * nodeScale(id);
    }

    private void bindEquipment() {
        int aX = offX("A", EntityProbeGuiSpec.A_OX), aY = offY("A", EntityProbeGuiSpec.A_OY);
        int a6X = offX("A6", EntityProbeGuiSpec.A6_X), a6Y = offY("A6", EntityProbeGuiSpec.A6_Y);
        for (int i = 0; i < EQ.length; i++) {
            EquipmentSlot eq = EQ[i];
            int x = aX + a6X + offX("c" + i, i * EntityProbeGuiSpec.A6_CELL);
            int y = aY + a6Y + offY("c" + i, 0);
            addSlot(new Slot(equipContainer, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return canPlaceIn(eq, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return eq == EquipmentSlot.MAINHAND ? 64 : 1;
                }
            });
        }
    }

    private void bindPlayerInventory(Inventory inv) {
        // 0-背包自身偏移(可被拖动) + 内部格子偏移 × 自身级联 scale × 整缩放
        int ox = offX("inv", EntityProbeGuiSpec.INV_OFF_X), oy = offY("inv", EntityProbeGuiSpec.INV_OFF_Y);
        float inner = guiScale() * cs("inv");
        for (int idx = 0; idx < 27; idx++) {
            addSlot(new Slot(inv, idx + 9,
                ox + Math.round(EntityProbeGuiSpec.invMainX(idx) * inner),
                oy + Math.round(EntityProbeGuiSpec.invMainY(idx) * inner)));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col,
                ox + Math.round(EntityProbeGuiSpec.invHotX(col) * inner),
                oy + Math.round(EntityProbeGuiSpec.invHotY() * inner)));
        }
    }

    /** 服务端权威目标实体。 */
    public LivingEntity equipTargetLivingServer() {
        return (serverTarget != null && serverTarget.isAlive()) ? serverTarget : null;
    }

    /** 目标实体解析：服务端=serverTarget；客户端=按 S2C id 从世界取。 */
    private LivingEntity equipTargetLiving() {
        if (serverTarget != null && serverTarget.isAlive()) return serverTarget;
        int id = receivedTargetId >= 0 ? receivedTargetId : targetEntityId;
        if (id < 0 || playerInventory.player == null) return null;
        Entity e = playerInventory.player.level().getEntity(id);
        return e instanceof LivingEntity le ? le : null;
    }

    private LivingEntity resolveClientTarget() {
        return equipTargetLiving();
    }

    /** A6 装备槽是否允许放入该物品：装甲槽按部位、主手任意。 */
    private static boolean canPlaceIn(EquipmentSlot eq, ItemStack stack) {
        if (eq == EquipmentSlot.MAINHAND) return true;
        if (stack.isEmpty()) return true;
        EquipmentSlot kind = armorSlotOf(stack);
        return kind == eq;
    }

    /** 解析物品装甲部位；非盔甲返回 null。兼顾不继承 ArmorItem 的自定义盔甲。 */
    private static EquipmentSlot armorSlotOf(ItemStack stack) {
        EquipmentSlot kind = stack.getEquipmentSlot();
        if (kind != null && kind.getType() == EquipmentSlot.Type.ARMOR) return kind;
        if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor) {
            EquipmentSlot a = armor.getEquipmentSlot();
            if (a != null && a.getType() == EquipmentSlot.Type.ARMOR) return a;
        }
        return null;
    }

    private static int ordinalOfArmor(EquipmentSlot kind) {
        for (int i = 0; i < EQ.length - 1; i++) {
            if (EQ[i] == kind) return i;
        }
        return -1;
    }

    /** 该物品应去的装备槽下标（盔甲→部位，其他→主手）；盔甲且非合法部位返回 -1。 */
    private static int targetOrdinalFor(ItemStack stack) {
        EquipmentSlot armor = armorSlotOf(stack);
        if (armor != null) {
            int o = ordinalOfArmor(armor);
            return o >= 0 ? o : -1;
        }
        return MAINHAND_INDEX;
    }

    // ── Shift 快捷（真实槽位 + 路由）──

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;

        if (index < EQUIP_END) {
            // 装备槽 → 背包
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        } else if (index < INV_END) {
            // 背包 → 对应空装甲槽：仅盔甲可快捷存入；其它类型不动（无副作用，避免挪位/翻倍）
            if (!stack.isEmpty()) {
                EquipmentSlot armor = armorSlotOf(stack);
                int target = armor != null ? ordinalOfArmor(armor) : -1;
                if (target >= 0) {
                    Slot targetSlot = this.slots.get(target);
                    if (targetSlot.getItem().isEmpty()) {
                        int n = stack.getCount();
                        targetSlot.set(stack.copy());
                        slot.remove(n);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** 空白 QUICK_CRAFT 取消（slotId=-999）vanilla 会按槽索引越界，这里兜底忽略。 */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.QUICK_CRAFT && slotId == -999) return;
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * 把目标实体 5 个装备槽伪装成容器：读=实体当前装备，写=setItemSlot 写穿到实体。
     * 客户端解析的是同步过装备的本地实体，服务端是权威实体。
     */
    private static class EntityEquipContainer extends SimpleContainer {
        private final Supplier<LivingEntity> holder;

        EntityEquipContainer(Supplier<LivingEntity> holder) {
            super(EQ.length);
            this.holder = holder;
        }

        @Override
        public ItemStack getItem(int index) {
            LivingEntity l = holder.get();
            return l != null ? l.getItemBySlot(EQ[index]) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            LivingEntity l = holder.get();
            if (l == null || index < 0 || index >= EQ.length) return ItemStack.EMPTY;
            ItemStack cur = l.getItemBySlot(EQ[index]);
            if (cur.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = cur.split(count);
            if (cur.isEmpty()) l.setItemSlot(EQ[index], ItemStack.EMPTY);
            else l.setItemSlot(EQ[index], cur);
            return out;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            LivingEntity l = holder.get();
            if (l == null || index < 0 || index >= EQ.length) return;
            l.setItemSlot(EQ[index], stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            LivingEntity l = holder.get();
            if (l == null || index < 0 || index >= EQ.length) return ItemStack.EMPTY;
            ItemStack cur = l.getItemBySlot(EQ[index]);
            if (cur.isEmpty()) return ItemStack.EMPTY;
            l.setItemSlot(EQ[index], ItemStack.EMPTY);
            return cur;
        }

        @Override
        public boolean isEmpty() {
            LivingEntity l = holder.get();
            if (l == null) return true;
            for (EquipmentSlot eq : EQ) {
                if (!l.getItemBySlot(eq).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
