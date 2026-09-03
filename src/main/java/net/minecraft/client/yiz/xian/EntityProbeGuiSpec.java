package net.minecraft.client.yiz.xian;

/**
 * 实体探查 GUI 几何规格（菜单与界面共用，避免槽位/纹理数值漂移）。
 *
 * <p>坐标模型：界面 image = INV_W×INV_H，由 AbstractContainerScreen 自动居中；
 * vanilla 的 (leftPos, topPos) 恰为 0-背包面板左上角。父元素 A（main.png 96×172）
 * 相对背包以固定偏移 (A_OX, A_OY) 叠在背包上方（A_OY 为负）。子元素坐标全部相对 A 左上角。
 * 槽位 x/y 是相对 (leftPos,topPos) 的容器空间坐标，构造时一次定死（Slot.x/y 为 final）。</p>
 */
public final class EntityProbeGuiSpec {

    private EntityProbeGuiSpec() {}

    // 界面 image 与 0-背包（0.png 168×82，居中，image 高度使背包落在屏底）
    public static final int IMAGE_W = 168;
    public static final int IMAGE_H = 176;
    public static final int INV_W = 168;
    public static final int INV_H = 82;
    public static final int INV_PAD_X = 3;
    public static final int INV_PAD_Y = 3;
    public static final int SLOT = 18;

    // 父元素 A 显示为 2×（底图 192×344），整卡放在 0-背包正上方（留 14px），确保 A6 槽热点可达
    public static final int BG_W = 96;
    public static final int BG_H = 172;
    public static final int INV_OFF_X = 0;
    public static final int INV_OFF_Y = 0;
    public static final int A_OX = (INV_W - BG_W) / 2; // 36
    public static final int A_OY = -(BG_H * 2 + 14);  // -358，2× 卡整体放到背包上方

    // A6 装备槽：保持原尺寸 90×18，居中于 2× 卡片(192 宽)下方
    public static final int A6_COUNT = 5;
    public static final int A6_X = (BG_W * 2 - 90) / 2; // 51，水平居中
    public static final int A6_Y = 284;
    public static final int A6_CELL = 18;

    /** 第 i 个 A6 槽的容器空间 x（= 相对 leftPos）。 */
    public static int eqX(int i) {
        return A_OX + A6_X + i * SLOT;
    }

    /** A6 槽容器空间 y。 */
    public static int eqY() {
        return A_OY + A6_Y;
    }

    /** 0-背包主格(27)槽容器空间坐标。 */
    public static int invMainX(int idx) {
        return INV_PAD_X + (idx % 9) * SLOT;
    }

    public static int invMainY(int idx) {
        return INV_PAD_Y + (idx / 9) * SLOT;
    }

    /** 0-背包快捷栏(9)槽容器空间坐标。 */
    public static int invHotX(int col) {
        return INV_PAD_X + col * SLOT;
    }

    /** 快捷栏与上方 27 格之间留 4px 间隔（用户确认的间距）。 */
    public static int invHotY() {
        return INV_PAD_Y + 3 * SLOT + 4;
    }
}
