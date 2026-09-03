package net.minecraft.client.yiz.xian.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.RegistryObject;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.health.SecureHealthClosure;
import net.minecraft.client.yiz.xian.EntityProbeGuiSpec;
import net.minecraft.client.yiz.xian.client.layout.GuiLayoutConfig;
import net.minecraft.client.yiz.xian.menu.EntityProbeMenu;
import net.minecraft.client.yiz.xian.network.C2SEntityProbeBlueRequestPayload;
import net.minecraft.client.yiz.xian.network.NetworkHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体探查 GUI —— 完整子元素版 + 元素树编辑器（guiskill 定则：子元素坐标相对父元素原点）。
 *
 * <p>元素树（坐标均相对父）：根=(leftPos,topPos) 即 0-背包左上；A 面板叠于其上；A1..A6 相对 A；
 * A6 的 5 个格子相对 A6。真槽位宿主(0-背包/A6/格子)为锁定位，可下钻查看但不单独拖动；
 * 纯视觉元素(A1..A5)可拖动定位。</p>
 *
 * <p>编辑器：Shift+DEL 进入/退出；A=拖动当前层元素；Shift+点击元素=下钻其子扩展；ESC 逐级回退；
 * 编辑界面提供「重置」按钮：清除玩家覆盖恢复默认布局。</p>
 */
public class EntityProbeScreen extends AbstractContainerScreen<EntityProbeMenu> {

    private static final String LAYOUT_KEY = "entity_probe";

    // ── 元素树节点 ──
    private static final class Node {
        final String id;
        final String parent;
        final int x, y, w, h;   // 相对父的默认偏移 / 尺寸
        final boolean draggable;
        Node(String id, String parent, int x, int y, int w, int h, boolean draggable) {
            this.id = id; this.parent = parent; this.x = x; this.y = y; this.w = w; this.h = h;
            this.draggable = draggable;
        }
    }

    private static final Map<String, Node> NODES = new LinkedHashMap<>();

    private static void node(String id, String parent, int x, int y, int w, int h, boolean d) {
        NODES.put(id, new Node(id, parent, x, y, w, h, d));
    }

    static {
        // 所有元素均可拖动定位；带真槽位的宿主(A/0-背包/A6/格子)槽位坐标在 Menu 打开时读取节点布局重建
        // 除 A6 外的所有元素纹理 ×2（A 底、A1~A5 ×2）；A6 保持原尺寸
        node("inv", "", EntityProbeGuiSpec.INV_OFF_X, EntityProbeGuiSpec.INV_OFF_Y,
            EntityProbeGuiSpec.INV_W, EntityProbeGuiSpec.INV_H, true);
        node("A", "", EntityProbeGuiSpec.A_OX, EntityProbeGuiSpec.A_OY,
            EntityProbeGuiSpec.BG_W * 2, EntityProbeGuiSpec.BG_H * 2, true);
        node("A1", "A", 4 * 2, 8 * 2, 24 * 2, 24 * 2, true);
        node("A2", "A", 63 * 2, 12 * 2, 27 * 2, 16 * 2, true);
        node("A3", "A", 12 * 2, 39 * 2, 76 * 2, 5 * 2, true);
        node("A4", "A", 12 * 2, 47 * 2, 76 * 2, 5 * 2, true);
        node("A5", "A", 3 * 2, 56 * 2, 24 * 2, 24 * 2, true);
        node("A6", "A", EntityProbeGuiSpec.A6_X, EntityProbeGuiSpec.A6_Y, 90, 18, true);
        for (int i = 0; i < 5; i++) {
            node("c" + i, "A6", i * EntityProbeGuiSpec.A6_CELL, 0,
                EntityProbeGuiSpec.A6_CELL, EntityProbeGuiSpec.A6_CELL, true);
        }
        node("A7", "A", 3 * 2, 82 * 2, 90 * 2, 49 * 2, true); // 属性面板：本体 2×（180×98），左右两列属性
    }

    // 纹理
    private static final ResourceLocation MAIN_BG = rl("main.png");
    private static final ResourceLocation T_A1 = rl("element_a1.png");
    private static final ResourceLocation T_A2 = rl("element_a2.png");
    private static final ResourceLocation T_A3 = rl("element_a3.png");
    private static final ResourceLocation T_A3_EMPTY = rl("element_a3_empty.png");
    private static final ResourceLocation T_A4 = rl("element_a4.png");
    private static final ResourceLocation T_A4_EMPTY = rl("element_a4_empty.png");
    private static final ResourceLocation T_A5 = rl("element_a5.png");
    private static final ResourceLocation T_A6 = rl("element_a6.png");
    private static final ResourceLocation T_A7 = rl("element_a7.png");
    private static final ResourceLocation INV_BG = rl("inv_bg.png");
    // 属性图标（gui/属性 32×32 转置）
    private static final ResourceLocation I_MAXHEALTH = rl("attrs/maxhealth.png");
    private static final ResourceLocation I_ATTACK = rl("attrs/attack.png");
    private static final ResourceLocation I_ARMOR = rl("attrs/armor.png");
    private static final ResourceLocation I_SPELLDEF = rl("attrs/spell_def.png");
    private static final ResourceLocation I_CRITRATE = rl("attrs/crit_rate.png");
    private static final ResourceLocation I_CRITDMG = rl("attrs/crit_dmg.png");
    private static final ResourceLocation I_SPELLPOWER = rl("attrs/spell_power.png");
    private static final ResourceLocation I_MAXMANA = rl("attrs/maxmana.png");

    private static ResourceLocation rl(String file) {
        return new ResourceLocation("yizxianmod", "textures/gui/entity_probe/" + file);
    }

    /** 服务端经 S2C 推来的被探查实体 id。 */
    private static volatile int receivedTargetId = -1;
    private static volatile float blueCurrent = 0f;
    private static volatile float blueMax = -1f;

    public static void onTargetReceived(int targetId) {
        receivedTargetId = targetId;
    }

    public static void setBlue(float current, float max) {
        blueCurrent = current;
        blueMax = max;
    }

    private int blueTick = 0;
    private long a2FlashUntil = 0;
    private int a7Scroll = 0;                 // A7 属性行滚动偏移（行号）

    // ── 编辑器状态 ──
    private boolean editMode = false;
    private final List<String> path = new ArrayList<>();          // 当前下钻路径，末位为当前容器
    private final Map<String, int[]> live = new LinkedHashMap<>(); // 本会话节点本地偏移覆盖
    private String editNode = null;                               // 正在拖动的节点
    private int grabX, grabY;                                     // 按下时 光标相对节点原点
    private float guiScale = 1f;                                  // 整 GUI 缩放（相对根锚点）
    private static final int RESET_X0 = 0, RESET_Y0 = 0;          // 重置按钮（draw 时定位）

    private boolean dragging = false;                             // 非编辑态：整 GUI 拖动
    private double wholeDX, wholeDY;

    public EntityProbeScreen(EntityProbeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = EntityProbeGuiSpec.IMAGE_W;
        this.imageHeight = EntityProbeGuiSpec.IMAGE_H;
        this.titleLabelX = 9999;
        this.inventoryLabelX = 9999;
    }

    @Override
    public void init() {
        super.init();
        GuiLayoutConfig.reload(); // 丢弃上次未保存改动，回到磁盘/默认
        this.guiScale = 1f;       // 缩放已关闭（此值恒为 1）
        live.clear();
        for (Map.Entry<String, Node> e : NODES.entrySet()) {
            GuiLayoutConfig.Layout saved = GuiLayoutConfig.rawNode(LAYOUT_KEY, e.getKey());
            if (saved != null) live.put(e.getKey(), new int[]{Math.round(saved.x()), Math.round(saved.y())});
        }
    }

    private void applyLayout() {
        GuiLayoutConfig.Layout saved = GuiLayoutConfig.raw(LAYOUT_KEY);
        int gw = GuiLayoutConfig.savedGuiW();
        int gh = GuiLayoutConfig.savedGuiH();
        if (saved != null && gw > 0 && gh > 0) {
            this.leftPos = (int) Math.round(saved.x() * this.width / (double) gw);
            this.topPos = (int) Math.round(saved.y() * this.height / (double) gh);
        } else {
            this.leftPos = (this.width - EntityProbeGuiSpec.INV_W) / 2;
            this.topPos = this.height - EntityProbeGuiSpec.INV_H - 6;
        }
    }

    // ── 节点坐标（元素树：子坐标相对父，逐层累加）──

    private int[] liveOff(String id) {
        int[] o = live.get(id);
        return o != null ? o : new int[]{NODES.get(id).x, NODES.get(id).y};
    }

    // ── 元素级缩放级联（cs=祖先至本节点 scale 之积；cs('')=1）──

    private String nodeParentOf(String id) {
        Node p = NODES.get(id);
        return p == null ? "" : p.parent;
    }

    private float nodeScaleOf(String id) {
        return 1f; // 元素缩放已关闭：忽略存档里的 scale，恒为 1
    }

    private float cs(String id) {
        if (id == null || id.isEmpty()) return 1f;
        return cs(nodeParentOf(id)) * nodeScaleOf(id);
    }

    /** 本节点视觉因子 = 整缩放 × 级联(含自身)。 */
    private float nodeFactor(String id) {
        return guiScale * cs(id);
    }

    /** 本节点偏移因子 = 整缩放 × 父级联（决定其相对父位置）。 */
    private float parentFactor(String id) {
        return guiScale * cs(nodeParentOf(id));
    }

    private int scNode(String id, int v) {
        return Math.round(v * nodeFactor(id));
    }

    /** 节点绝对坐标（屏幕像素），偏移按父级联 scale × 整缩放 换算。 */
    private int[] absOf(String id) {
        int x = leftPos, y = topPos;
        String cur = id;
        while (!cur.isEmpty()) {
            int[] o = liveOff(cur);
            x += Math.round(o[0] * parentFactor(cur));
            y += Math.round(o[1] * parentFactor(cur));
            cur = NODES.get(cur).parent;
        }
        return new int[]{x, y};
    }

    private List<Node> childrenOf(String container) {
        List<Node> list = new ArrayList<>();
        for (Node n : NODES.values()) {
            if (n.parent.equals(container)) list.add(n);
        }
        return list;
    }

    private boolean hasChildren(String id) {
        for (Node n : NODES.values()) if (n.parent.equals(id)) return true;
        return false;
    }

    private String currentContainer() {
        return path.isEmpty() ? "" : path.get(path.size() - 1);
    }

    // ── 数据 ──

    public LivingEntity targetEntity() {
        if (this.minecraft == null || this.minecraft.level == null || receivedTargetId < 0) return null;
        if (this.minecraft.level.getEntity(receivedTargetId) instanceof LivingEntity le) return le;
        return null;
    }

    /** 本模组实体取权威真实生命，普通实体回退 vanilla（外界可用 SecureHealthClosure 读到真实值）。 */
    private float realHp(LivingEntity t) {
        return SecureHealthClosure.getHealth(t);
    }

    private float realMaxHp(LivingEntity t) {
        return SecureHealthClosure.getMaxHealth(t);
    }

    private float healthPct() {
        LivingEntity t = targetEntity();
        if (t == null) return 0f;
        float max = realMaxHp(t);
        return max <= 0 ? 0f : Math.max(0f, Math.min(1f, realHp(t) / max));
    }

    private float bluePct() {
        float max = blueMax > 0 ? blueMax : 200f;
        return Math.max(0f, Math.min(1f, blueCurrent / max));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (++blueTick >= 20) {
            blueTick = 0;
            NetworkHandler.CHANNEL.sendToServer(new C2SEntityProbeBlueRequestPayload());
        }
    }

    // ── 按键：Shift+DEL 编辑；ESC 回退/退出编辑 ──

    /** 整 GUI 缩放：在根层可直接操作（[ 减小 / ] 增大），范围 0.25~4。 */
    private void scaleWhole(float delta) {
        guiScale = Math.max(0.25f, Math.min(4f, guiScale + delta));
        GuiLayoutConfig.put(LAYOUT_KEY, leftPos, topPos, this.width, this.height, guiScale);
        GuiLayoutConfig.save();
        this.menu.applyLiveSlotLayout();
    }

    private int scW(int v) {
        return Math.round(v * guiScale);
    }

    private int scH(int v) {
        return Math.round(v * guiScale);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_DELETE && hasShiftDown()) {
            editMode = !editMode;
            if (editMode) {
                path.clear();
                editNode = null;
            } else {
                saveAll();
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (editMode) {
                if (!path.isEmpty()) {
                    path.remove(path.size() - 1);
                } else {
                    editMode = false; // 不自动保存：未点“保存”则丢弃
                }
                editNode = null;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveAll() {
        GuiLayoutConfig.save();
    }

    private void saveNodeOverride(String id) {
        int[] o = live.get(id);
        if (o != null) {
            GuiLayoutConfig.putNode(LAYOUT_KEY, id, o[0], o[1]);
        }
    }

    private void resetLayout() {
        GuiLayoutConfig.remove(LAYOUT_KEY);
        GuiLayoutConfig.save();
        live.clear();
        guiScale = 1f;
        applyLayout();
        path.clear();
        editNode = null;
        this.menu.applyLiveSlotLayout();
    }

    // ── 交互 ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (editMode) {
            // 编辑态：屏蔽一切下层点击交互（含右键），仅响应编辑器自身操作
            if (button == 0) return editMouseClicked(mx, my);
            return true;
        }
        if (button == 0) {
            // 原版槽位先处理
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            int a2x = absOf("A2")[0], a2y = absOf("A2")[1];
            if (mx >= a2x && mx < a2x + scNode("A2", 27) && my >= a2y && my < a2y + scNode("A2", 16)) {
                this.a2FlashUntil = System.currentTimeMillis() + 300;
                return true;
            }
            int[] a = absOf("A");
            if (mx >= a[0] && mx < a[0] + scNode("A", EntityProbeGuiSpec.BG_W)
                    && my >= a[1] && my < a[1] + scNode("A", EntityProbeGuiSpec.BG_H)
                    && menu.getCarried().isEmpty()) {
                dragging = true;
                wholeDX = mouseX - leftPos;
                wholeDY = mouseY - topPos;
                return true;
            }
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean editMouseClicked(int mx, int my) {
        // 保存按钮（右上，仅此会落盘）
        int sx = this.width - 132;
        if (mx >= sx && mx < sx + 60 && my >= 4 && my < 20) {
            GuiLayoutConfig.save();
            return true;
        }
        // 重置按钮（右上）
        int rx = this.width - 64;
        if (mx >= rx && mx < rx + 60 && my >= 4 && my < 20) {
            resetLayout();
            return true;
        }
        List<Node> layer = childrenOf(currentContainer());
        // 自后向前取最上层命中的节点（Shift+点击=下钻）
        for (int i = layer.size() - 1; i >= 0; i--) {
            Node n = layer.get(i);
            int[] p = absOf(n.id);
            int wS = scNode(n.id, n.w), hS = scNode(n.id, n.h);
            if (mx >= p[0] && mx < p[0] + wS && my >= p[1] && my < p[1] + hS) {
                if (hasShiftDown()) {
                    if (hasChildren(n.id)) {
                        path.add(n.id);
                    }
                    return true;
                }
                if (n.draggable) {
                    editNode = n.id;
                    grabX = mx - p[0];
                    grabY = my - p[1];
                }
                return true;
            }
        }
        return true;
    }

    /** Shift+滚轮 = 缩放鼠标所指元素（当前层，取最上层命中）。 */
    private Node topNodeAt(int mx, int my) {
        List<Node> layer = childrenOf(currentContainer());
        for (int i = layer.size() - 1; i >= 0; i--) {
            Node n = layer.get(i);
            int[] p = absOf(n.id);
            int wS = scNode(n.id, n.w), hS = scNode(n.id, n.h);
            if (mx >= p[0] && mx < p[0] + wS && my >= p[1] && my < p[1] + hS) return n;
        }
        return null;
    }

    /** 调整某元素的缩放并即时生效（级联影响子树，槽位热点反射重排）。 */
    private void scaleNodeAt(String id, float delta) {
        float cur = nodeScaleOf(id);
        float ns = Math.max(0.25f, Math.min(4f, cur + delta));
        int[] off = liveOff(id);
        GuiLayoutConfig.putNode(LAYOUT_KEY, id, off[0], off[1], ns);
        GuiLayoutConfig.save();
        this.menu.applyLiveSlotLayout();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (editMode) {
            if (button == 0 && editNode != null) {
                Node n = NODES.get(editNode);
                int[] pa = absOf(n.parent);
                // 屏幕位移 ÷ 父空间级联因子 → 父空间本地偏移
                float f = parentFactor(editNode);
                int lx = Math.round((float) (mouseX - pa[0] - grabX) / f);
                int ly = Math.round((float) (mouseY - pa[1] - grabY) / f);
                live.put(editNode, new int[]{lx, ly});
                GuiLayoutConfig.putNode(LAYOUT_KEY, editNode, lx, ly);
                this.menu.applyLiveSlotLayout(); // 槽位热点实时跟随（反射改 Slot 坐标）
                return true;
            }
            return true;
        }
        if (button == 0 && dragging) {
            leftPos = Math.max(-EntityProbeGuiSpec.INV_W + 20,
                Math.min(width - 20, (int) Math.round(mouseX - wholeDX)));
            topPos = Math.max(-EntityProbeGuiSpec.INV_H + 20,
                Math.min(height - 20, (int) Math.round(mouseY - wholeDY)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (editMode) {
            // 编辑态：抬起不落到容器；仅更新内存，落盘交给“保存”按钮
            if (button == 0 && editNode != null) {
                saveNodeOverride(editNode);
                editNode = null;
            }
            return true;
        }
        if (button == 0 && dragging) {
            dragging = false;
            GuiLayoutConfig.put(LAYOUT_KEY, leftPos, topPos, this.width, this.height, guiScale);
            return true; // 不自动保存
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        // 不自动保存：未点“保存”则本次布局改动随重开丢弃
        super.onClose();
    }

    // ── 渲染 ──

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        // 槽位贴图按 slot.x-1 对齐（见 gui-bg-offset：AbstractContainerScreen 拼装差 1px）
        int[] inv = absOf("inv");
        scaledBlit(gui, INV_BG, inv[0] - 1, inv[1] - 1,
            EntityProbeGuiSpec.INV_W, EntityProbeGuiSpec.INV_H, nodeFactor("inv"));

        int[] a = absOf("A");
        // A 底 ×2（除 A5 外全部 ×2）
        scaledBlit(gui, MAIN_BG, a[0], a[1],
            EntityProbeGuiSpec.BG_W, EntityProbeGuiSpec.BG_H, 2f * nodeFactor("A"));

        scaledBlitAt(gui, "A1", T_A1);
        scaledBlitAt(gui, "A2", T_A2);
        scaledBlitAt(gui, "A5", T_A5);
        int[] a6 = absOf("A6");
        scaledBlit(gui, T_A6, a6[0] - 1, a6[1] - 1, 90, 18, nodeFactor("A6"));

        drawBar(gui, "A3", T_A3_EMPTY, T_A3, healthPct());
        drawBar(gui, "A4", T_A4_EMPTY, T_A4, bluePct());
        drawA7(gui);

        if (System.currentTimeMillis() < a2FlashUntil) {
            int[] p = absOf("A2");
            gui.fill(p[0], p[1], p[0] + scNode("A2", 27), p[1] + scNode("A2", 16), 0x60FFFFFF);
        }
    }

    /** 在绝对坐标 (x,y) 处以元素视觉因子 f 等比绘制贴图。 */
    private void scaledBlit(GuiGraphics gui, ResourceLocation tex, int x, int y, int w, int h, float f) {
        if (f <= 0.001f) return;
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(f, f, 1f);
        gui.blit(tex, 0, 0, 0, 0, w, h, w, h);
        pose.popPose();
    }

    private void scaledBlitAt(GuiGraphics gui, String id, ResourceLocation tex) {
        Node n = NODES.get(id);
        int[] p = absOf(id);
        scaledBlit(gui, tex, p[0], p[1], n.w, n.h, nodeFactor(id));
    }

    private void drawBar(GuiGraphics gui, String id, ResourceLocation empty, ResourceLocation fill, float pct) {
        Node n = NODES.get(id);
        int[] p = absOf(id);
        float f = nodeFactor(id);
        scaledBlit(gui, empty, p[0], p[1], n.w, n.h, f);
        int fw = Math.round(n.w * f * pct);
        if (fw > 0) {
            gui.enableScissor(p[0], p[1], p[0] + fw, p[1] + Math.round(n.h * f));
            scaledBlit(gui, fill, p[0], p[1], n.w, n.h, f);
            gui.disableScissor();
        }
    }

    // ── A7 属性面板（滚轮浏览）──

    private static final int A7_ROW_H = 17;

    private static final class A7Row {
        final ResourceLocation icon;
        final String label;
        final String value;

        A7Row(ResourceLocation icon, String label, String value) {
            this.icon = icon;
            this.label = label;
            this.value = value;
        }
    }

    private static String num(double v) {
        long t = Math.round(v * 10);
        return t % 10 == 0 ? Long.toString(t / 10) : String.format("%.1f", v);
    }

    private static boolean hasAttr(LivingEntity e, RegistryObject<Attribute> ro) {
        return ro != null && ro.isPresent() && e.getAttribute(ro.get()) != null;
    }

    private static double modAttr(LivingEntity e, RegistryObject<Attribute> ro) {
        var inst = e.getAttribute(ro.get());
        return inst != null ? inst.getValue() : 0;
    }

    /** 9 项按序（生命/护甲/攻击/法防/暴率/暴伤/吸血/法强/法力）；缺失剔除、紧凑补位；护甲/攻击有 vanilla 回退。 */
    private List<A7Row> a7Rows(LivingEntity t) {
        List<A7Row> all = new ArrayList<>();
        String v;
        all.add(new A7Row(I_MAXHEALTH, "最大生命值", num(realMaxHp(t))));

        if (hasAttr(t, YizAttributes.ARMOR)) {
            v = num(modAttr(t, YizAttributes.ARMOR));
        } else if (t.getAttribute(Attributes.ARMOR) != null) {
            v = num(t.getAttribute(Attributes.ARMOR).getValue());
        } else {
            v = null;
        }
        all.add(new A7Row(I_ARMOR, "护甲防御", v));

        if (hasAttr(t, YizAttributes.ATTACK_STRENGTH)) {
            v = num(modAttr(t, YizAttributes.ATTACK_STRENGTH));
        } else if (t.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            v = num(t.getAttribute(Attributes.ATTACK_DAMAGE).getValue());
        } else {
            v = null;
        }
        all.add(new A7Row(I_ATTACK, "攻击力", v));

        all.add(new A7Row(I_SPELLDEF, "法术防御",
            hasAttr(t, YizAttributes.SPELL_DEFENSE) ? num(modAttr(t, YizAttributes.SPELL_DEFENSE)) : null));
        all.add(new A7Row(I_CRITRATE, "暴击概率",
            hasAttr(t, YizAttributes.CRIT_RATE) ? num(modAttr(t, YizAttributes.CRIT_RATE)) + "%" : null));
        all.add(new A7Row(I_CRITDMG, "暴击效果",
            hasAttr(t, YizAttributes.CRIT_DAMAGE) ? num(modAttr(t, YizAttributes.CRIT_DAMAGE)) + "%" : null));
        all.add(new A7Row(null, "全能吸血",
            hasAttr(t, YizAttributes.LIFE_STEAL) ? num(modAttr(t, YizAttributes.LIFE_STEAL)) + "%" : null));
        all.add(new A7Row(I_SPELLPOWER, "法术强度",
            hasAttr(t, YizAttributes.SPELL_POWER) ? num(modAttr(t, YizAttributes.SPELL_POWER)) : null));
        all.add(new A7Row(I_MAXMANA, "最大法力值",
            hasAttr(t, YizAttributes.MAX_MANA) ? num(modAttr(t, YizAttributes.MAX_MANA)) : null));
        List<A7Row> rows = new ArrayList<>();
        for (A7Row r : all) if (r.value != null) rows.add(r);
        return rows;
    }

    /** A7：本体 2× 面板(180×98)。行主序（→ 下一行）紧凑排布，行高固定；图标与文本同行垂直居中。 */
    private void drawA7(GuiGraphics gui) {
        int[] p = absOf("A7");
        scaledBlit(gui, T_A7, p[0], p[1], 90, 49, 2f * nodeFactor("A7"));
        LivingEntity t = targetEntity();
        if (t == null) return;
        List<A7Row> rows = a7Rows(t);
        if (rows.isEmpty()) return;
        int colW = 90;
        int rowH = 18;
        int startY = p[1] + 4;
        gui.enableScissor(p[0], p[1], p[0] + 180, p[1] + 98);
        for (int i = 0; i < rows.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int x = p[0] + (col == 0 ? 4 : 4 + colW);
            int y = startY + row * rowH;
            if (y + rowH > p[1] + 98) break;
            drawA7Cell(gui, rows.get(i), x, y, rowH);
        }
        gui.disableScissor();
    }

    private void drawA7Cell(GuiGraphics gui, A7Row item, int x, int y, int rowH) {
        if (item.value == null) return;
        // 文本与图标按 16px 图标框垂直居中、水平顺序：文本 + 图标 + 数值
        int iconBox = 16;
        int textY = y + (rowH - this.font.lineHeight) / 2;
        int iconY = y + (rowH - iconBox) / 2;
        gui.drawString(this.font, item.label, x, textY, 0xFFFFFFFF);
        int cx = x + this.font.width(item.label) + 3;
        if (item.icon != null) {
            scaledBlit(gui, item.icon, cx, iconY, 32, 32, 0.5f); // 16px
            gui.drawString(this.font, item.value, cx + 18, textY, 0xFFFFFF55);
        } else {
            gui.drawString(this.font, item.value, cx + 2, textY, 0xFFFFFF55);
        }
    }

    private void drawCellText(GuiGraphics gui, String s, float cx, float cy, float f) {
        int tw = this.font.width(s);
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(f, f, 1f);
        gui.drawString(this.font, s, Math.round(-tw / 2f), 0, 0xFFFFFFFF);
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
        drawBarLabels(gui);
        if (editMode) renderEditorOverlay(gui);
    }

    /** 血/蓝条条内居中显示数值（当前值/上限），小字号缩放，随条移动。血量用真实值。 */
    private void drawBarLabels(GuiGraphics gui) {
        LivingEntity t = targetEntity();
        if (t != null) {
            String hp = Math.round(realHp(t)) + "/" + Math.round(realMaxHp(t));
            drawOverlayText(gui, hp, absOf("A3"), NODES.get("A3"), 0xFFFFFFFF);
        }
        if (blueMax > 0) {
            String mp = Math.round(blueCurrent) + "/" + Math.round(blueMax);
            drawOverlayText(gui, mp, absOf("A4"), NODES.get("A4"), 0xFF9CC8FF);
        }
    }

    /** 在条元素内部居中叠印文本；字号 0.85×，不加背景色。 */
    private void drawOverlayText(GuiGraphics gui, String text, int[] p, Node bar, int color) {
        if (bar == null || p == null) return;
        float s = 0.85f;
        int tw = this.font.width(text);
        int flh = this.font.lineHeight;
        float cx = p[0] + bar.w / 2f;
        float cy = p[1] + bar.h / 2f;
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(s, s, 1f);
        gui.drawString(this.font, text, Math.round(-tw / 2f), Math.round(-flh / 2f), color);
        pose.popPose();
    }

    private void renderEditorOverlay(GuiGraphics gui) {
        StringBuilder bc = new StringBuilder("编辑");
        for (String s : path) bc.append(" › ").append(s);
        bc.append(" › 层");
        gui.drawString(this.font, bc.toString(), 4, 4, 0xFF33FFAA);

        gui.drawString(this.font, "拖动=移动元素 / Shift+点=下钻 / Shift+滚轮=缩放所指元素 / DEL+Shift=退出",
            4, this.font.lineHeight + 4, 0xFFCCCCCC);

        for (Node n : childrenOf(currentContainer())) {
            int[] p = absOf(n.id);
            int wS = scNode(n.id, n.w), hS = scNode(n.id, n.h);
            int color = 0xFFFFFFFF;
            gui.fill(p[0] - 1, p[1] - 1, p[0] + wS + 1, p[1] + hS + 1, 0x28FFFFFF);
            gui.fill(p[0] - 1, p[1] - 1, p[0] + wS + 1, p[1], color);
            gui.fill(p[0] - 1, p[1] + hS, p[0] + wS + 1, p[1] + hS + 1, color);
            gui.fill(p[0] - 1, p[1] - 1, p[0], p[1] + hS + 1, color);
            gui.fill(p[0] + wS, p[1] - 1, p[0] + wS + 1, p[1] + hS + 1, color);
            gui.drawString(this.font, n.id, p[0], p[1] - 2 - this.font.lineHeight, color);
        }
        gui.drawString(this.font, "拖动/移动预览不会自动保存；点右上“保存”才落盘，ESC/关闭丢弃", 4, 4 + this.font.lineHeight * 2, 0xFFCCCCCC);

        // 保存 / 重置（右上）
        int sx = this.width - 132;
        gui.fill(sx, 4, sx + 60, 20, 0xCC2E7D32);
        gui.drawString(this.font, "保存", sx + 22, 8, 0xFFFFFFFF);
        int rx = this.width - 64;
        gui.fill(rx, 4, rx + 60, 20, 0xCC333333);
        gui.drawString(this.font, "重置", rx + 22, 8, 0xFFFFAA33);
    }
}
