package net.minecraft.client.yiz.xian.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.yiz.xian.menu.LightCompassMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 光明指南针 GUI Screen（1.20.1 移植版）。
 *
 * 严格按用户给定坐标布局（GUI 内相对像素）：
 *   ① 工作槽 3 个        : (8,22) (26,22) (44,22)  ← 由 Menu 的 Slot 自绘
 *   ② 搜索栏 EditBox     : x=80  y=28  w=89  h=11
 *   ③ 展示栏 5列×9行滚动  : 起点 (8,49)，每格 18×18，可见约 4 行，滚动看全部
 *   ④ 玩家快捷栏 9 格     : 起点 (8,143)，由 Menu 的 Slot 自绘
 *   ⑤ 滚动条             : x=174 y=49 w=13 h=111
 */
public class LightCompassScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<LightCompassMenu>
        implements MenuAccess<LightCompassMenu> {

    private static final ResourceLocation BACKGROUND =
        new ResourceLocation("yizxianmod", "textures/gui/container/light_compass.png");

    private static final int DISPLAY_X = 8;
    private static final int DISPLAY_Y = 49;
    private static final int DISPLAY_COLS = 9;
    private static final int SLOT = 18;
    private static final int DISPLAY_ROWS_VISIBLE = 5;

    private static final int SCROLL_X = 174;
    private static final int SCROLL_Y = 49;
    private static final int SCROLL_W = 13;
    private static final int SCROLL_H = 111;

    private EditBox searchBox;

    /** 当前滚动偏移（以「行」为单位，0 表示顶部）。 */
    private int scrollRow = 0;
    private boolean draggingScroll = false;

    /** 过滤后的展示物品（依据搜索词）。 */
    private List<ItemStack> filtered = new ArrayList<>();

    public LightCompassScreen(LightCompassMenu menu, net.minecraft.world.entity.player.Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 195;
        this.imageHeight = 168;
        this.inventoryLabelX = 9999;
        this.titleLabelX = 9999;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(this.font, this.leftPos + 80, this.topPos + 28, 89, 11,
            Component.translatable("container.yizxianmod.light_compass.search"));
        searchBox.setHint(Component.literal("搜索...").withStyle(s -> s.withColor(0x808080)));
        searchBox.setResponder(this::onSearch);
        this.addRenderableWidget(searchBox);

        rebuildFiltered();
    }

    private void onSearch(String query) {
        rebuildFiltered();
        scrollRow = 0;
    }

    private void rebuildFiltered() {
        filtered.clear();
        String q = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT).trim() : "";
        for (ItemStack stack : menu.getDisplayItems()) {
            if (q.isEmpty()) {
                filtered.add(stack);
            } else {
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains(q) || stack.getItem().getDescriptionId().toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(stack);
                }
            }
        }
    }

    private int displayVisibleCount() {
        return DISPLAY_COLS * DISPLAY_ROWS_VISIBLE;
    }
    private int maxScrollRow() {
        int totalRows = (filtered.size() + DISPLAY_COLS - 1) / DISPLAY_COLS;
        return Math.max(0, totalRows - DISPLAY_ROWS_VISIBLE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderDisplayItems(g, mouseX, mouseY);
        ItemStack hovered = getHoveredDisplayItem(mouseX, mouseY);
        if (hovered != null) {
            g.renderTooltip(this.font, hovered, mouseX, mouseY);
        } else {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    private ItemStack getHoveredDisplayItem(double mouseX, double mouseY) {
        int idx = displayIndexAt(mouseX, mouseY);
        if (idx < 0) return null;
        int listIdx = scrollRow * DISPLAY_COLS + idx;
        if (listIdx < 0 || listIdx >= filtered.size()) return null;
        return filtered.get(listIdx);
    }

    private void renderDisplayItems(GuiGraphics g, int mouseX, int mouseY) {
        int ox = this.leftPos - 1;
        int oy = this.topPos - 1;
        int clipLeft = ox + DISPLAY_X;
        int clipTop = oy + DISPLAY_Y;
        int clipRight = ox + DISPLAY_X + DISPLAY_COLS * SLOT;
        int clipBottom = oy + DISPLAY_Y + DISPLAY_ROWS_VISIBLE * SLOT;
        g.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

        int baseIdx = scrollRow * DISPLAY_COLS;
        for (int i = 0; i < displayVisibleCount(); i++) {
            int listIdx = baseIdx + i;
            if (listIdx >= filtered.size()) break;
            int col = i % DISPLAY_COLS;
            int row = i / DISPLAY_COLS;
            int sx = ox + DISPLAY_X + col * SLOT;
            int sy = oy + DISPLAY_Y + row * SLOT;
            ItemStack stack = filtered.get(listIdx);
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(this.font, stack, sx + 1, sy + 1);
            if (mouseX >= sx + 1 && mouseX < sx + 1 + 16 && mouseY >= sy + 1 && mouseY < sy + 1 + 16) {
                g.fill(sx + 1, sy + 1, sx + 1 + 16, sy + 1 + 16, 0x40FFFFFF);
            }
        }
        g.disableScissor();

        drawScrollbar(g, ox, oy);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int lx = this.leftPos - 1;
        int ty = this.topPos - 1;
        if (BACKGROUND != null) {
            g.blit(BACKGROUND, lx, ty, 0.0f, 0.0f, this.imageWidth, this.imageHeight, 195, 168);
        } else {
            g.fill(lx, ty, lx + this.imageWidth, ty + this.imageHeight, 0xFF2B2B2B);
        }
    }

    private void drawScrollbar(GuiGraphics g, int ox, int oy) {
        int x = ox + SCROLL_X;
        int y = oy + SCROLL_Y;
        g.fill(x, y, x + SCROLL_W, y + SCROLL_H, 0xFF222222);
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + SCROLL_H - 1, 0xFF555555);
        int max = maxScrollRow();
        int trackH = SCROLL_H - 4;
        int thumbH = max <= 0 ? trackH : Math.max(10, trackH * DISPLAY_ROWS_VISIBLE / ((filtered.size() + DISPLAY_COLS - 1) / DISPLAY_COLS));
        int thumbY = max <= 0 ? y + 2 : y + 2 + (trackH - thumbH) * scrollRow / max;
        g.fill(x + 2, thumbY, x + SCROLL_W - 2, thumbY + thumbH, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int ox = this.leftPos - 1, oy = this.topPos - 1;
        if (mouseX >= ox + SCROLL_X && mouseX < ox + SCROLL_X + SCROLL_W
                && mouseY >= oy + SCROLL_Y && mouseY < oy + SCROLL_Y + SCROLL_H) {
            draggingScroll = true;
            setScrollByY(mouseY);
            searchBox.setFocused(false);
            return true;
        }

        int dispIdx = displayIndexAt(mouseX, mouseY);
        if (dispIdx >= 0 && button == 0) {
            int listIdx = scrollRow * DISPLAY_COLS + dispIdx;
            if (listIdx < filtered.size()) {
                net.minecraft.client.yiz.xian.network.C2SLightCompassWorkSlotPayload.sendAdd(filtered.get(listIdx));
            }
            searchBox.setFocused(false);
            return true;
        }

        int workIdx = workSlotIndexAt(mouseX, mouseY);
        if (workIdx >= 0 && button == 0) {
            if (this.menu.isWorkSlotOccupied(workIdx)) {
                net.minecraft.client.yiz.xian.network.C2SLightCompassWorkSlotPayload.sendRemove(workIdx);
            }
            searchBox.setFocused(false);
            return true;
        }

        boolean inSearch = mouseX >= searchBox.getX() && mouseX < searchBox.getX() + searchBox.getWidth()
                        && mouseY >= searchBox.getY() && mouseY < searchBox.getY() + searchBox.getHeight();
        if (inSearch) {
            this.setFocused(searchBox);
            searchBox.mouseClicked(mouseX, mouseY, button);
            return true;
        } else {
            this.setFocused(null);
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScroll) {
            setScrollByY(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        int ox = this.leftPos - 1, oy = this.topPos - 1;
        if (mouseX >= ox + DISPLAY_X && mouseX < ox + DISPLAY_X + DISPLAY_COLS * SLOT + SCROLL_W
                && mouseY >= oy + DISPLAY_Y && mouseY < oy + DISPLAY_Y + DISPLAY_ROWS_VISIBLE * SLOT + 4) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(scrollDelta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    private void setScrollByY(double mouseY) {
        int trackTop = (this.topPos - 1) + SCROLL_Y + 2;
        int trackH = SCROLL_H - 4;
        double ratio = (mouseY - trackTop) / trackH;
        ratio = Math.max(0, Math.min(1, ratio));
        scrollRow = (int) Math.round(ratio * maxScrollRow());
    }

    private int displayIndexAt(double mouseX, double mouseY) {
        int ox = this.leftPos - 1, oy = this.topPos - 1;
        int localX = (int) Math.round(mouseX) - (ox + DISPLAY_X);
        int localY = (int) Math.round(mouseY) - (oy + DISPLAY_Y);
        if (localX < 0 || localY < 0) return -1;
        int col = localX / SLOT;
        int row = localY / SLOT;
        if (col < 0 || col >= DISPLAY_COLS || row < 0 || row >= DISPLAY_ROWS_VISIBLE) return -1;
        return row * DISPLAY_COLS + col;
    }

    private int workSlotIndexAt(double mouseX, double mouseY) {
        for (int i = 0; i < LightCompassMenu.WORK_SLOT_COUNT; i++) {
            int sx = this.leftPos + 8 + i * SLOT;
            int sy = this.topPos + 22;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) return i;
        }
        return -1;
    }
}
