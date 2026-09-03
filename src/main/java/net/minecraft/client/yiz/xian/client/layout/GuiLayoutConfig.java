package net.minecraft.client.yiz.xian.client.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GUI 布局持久化（玩家可覆盖层）。
 *
 * <p>仿 hud_positions.json 约定：文件存 {@code config/yizxianmod/gui_layouts.json}，结构
 * {@code {_guiW, _guiH, guis: {"<id>": {x, y, scale}}}}。x/y 为保存那一刻、在 _guiW×_guiH
 * 分辨率下的绝对像素；其它分辨率读取时按 {@code 值 × 当前宽/_guiW} 换算（同 HUD 位置做法）。</p>
 *
 * <p>定位：这是<b>玩家覆盖层</b>。无覆盖时 Screen 走代码默认（内置默认常量）。开发期可在游戏内
 * 拖动/缩放 GUI 后把本文件交给开发者，开发者把保存值按 960×540 基准反算固化为代码默认。</p>
 *
 * <p>⚠️ 坑（继承 hud_positions）：本文件<b>只在保存动作时写入</b>，不要在无内容时覆盖空对象，
 * 否则 GUI 会全部回默认位。排障时若 GUI 不在预期位置，先读本文件看 guis 是否为 {}。</p>
 */
public final class GuiLayoutConfig {

    /** 单条布局。x/y 为 _guiW×_guiH 分辨率下的绝对像素。 */
    public record Layout(float x, float y, float scale) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path FILE;
    private static JsonObject ROOT;

    private GuiLayoutConfig() {}

    private static Path file() {
        if (FILE == null) {
            FILE = FMLPaths.CONFIGDIR.get().resolve("yizxianmod").resolve("gui_layouts.json");
        }
        return FILE;
    }

    private static JsonObject root() {
        if (ROOT == null) {
            Path f = file();
            if (Files.isReadable(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    JsonElement el = GSON.fromJson(r, JsonElement.class);
                    if (el != null && el.isJsonObject()) ROOT = el.getAsJsonObject();
                } catch (Exception ignored) {
                    ROOT = null; // 读失败按空处理，避免启动时写坏
                }
            }
            if (ROOT == null) ROOT = new JsonObject();
        }
        return ROOT;
    }

    /** 已保存布局的原始（保存时分辨率）条目；没有返回 null。 */
    public static Layout raw(String id) {
        JsonObject root = root();
        if (!root.has("guis") || !root.get("guis").isJsonObject()) return null;
        JsonObject g = root.getAsJsonObject("guis");
        if (!g.has(id)) return null;
        JsonObject o = g.getAsJsonObject(id);
        try {
            return new Layout(o.get("x").getAsFloat(), o.get("y").getAsFloat(), o.get("scale").getAsFloat());
        } catch (Exception e) {
            return null;
        }
    }

    /** 保存条目的分辨率。 */
    public static int savedGuiW() {
        JsonObject root = root();
        return root.has("_guiW") ? root.get("_guiW").getAsInt() : 0;
    }

    public static int savedGuiH() {
        JsonObject root = root();
        return root.has("_guiH") ? root.get("_guiH").getAsInt() : 0;
    }

    /**
     * 更新某 GUI 布局并实时写入内存缓存（调用方在拖放结束后调 {@link #save()} 落盘）。
     *
     * @param x 当前分辨率下的绝对 x
     * @param y 当前分辨率下的绝对 y
     * @param guiW 当前 GUI 宽度（作为本条目基准分辨率）
     * @param guiH 当前 GUI 高度
     */
    public static void put(String id, int x, int y, int guiW, int guiH, float scale) {
        JsonObject root = root();
        if (!root.has("guis") || !root.get("guis").isJsonObject()) {
            root.add("guis", new JsonObject());
        }
        JsonObject g = root.getAsJsonObject("guis");
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("scale", scale);
        g.add(id, o);
        root.addProperty("_guiW", guiW);
        root.addProperty("_guiH", guiH);
    }

    /** 丢弃未保存的内存改动，重新从文件加载（打开 GUI 时调用）。 */
    public static void reload() {
        ROOT = null;
        file();
        root();
    }

    /** 立即把内存缓存写入文件（仅存在有内容时才写，避免空对象覆盖）。 */
    public static void save() {
        JsonObject root = root();
        if (!root.has("guis") || root.get("guis").getAsJsonObject().size() == 0) return;
        Path f = file();
        try {
            if (f.getParent() != null) Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            // 写失败不致命：GUI 布局仅回默认，不崩游戏
        }
    }

    /** 清空某 GUI 布局（用于重置为默认；随后可 save 或删文件）。 */
    public static void remove(String id) {
        JsonObject root = root();
        if (root.has("guis") && root.get("guis").isJsonObject()) {
            root.getAsJsonObject("guis").remove(id);
        }
    }

    // ── 元素树子节点（每个 GUI 内各元素相对其父的偏移）──

    /** 读取某 GUI 内某元素的原始偏移；没有返回 null。 */
    public static Layout rawNode(String guiId, String nodeId) {
        JsonObject root = root();
        if (!root.has("guis") || !root.get("guis").isJsonObject()) return null;
        JsonObject gui = root.getAsJsonObject("guis").getAsJsonObject(guiId);
        if (gui == null || !gui.has("nodes") || !gui.get("nodes").isJsonObject()) return null;
        JsonObject o = gui.getAsJsonObject("nodes").getAsJsonObject(nodeId);
        if (o == null) return null;
        try {
            float scale = o.has("scale") ? o.get("scale").getAsFloat() : 1f;
            return new Layout(o.get("x").getAsFloat(), o.get("y").getAsFloat(), scale);
        } catch (Exception e) {
            return null;
        }
    }

    /** 记录某 GUI 内某元素相对其父的偏移与缩放（内存），由调用方在交互结束时 save()。 */
    public static void putNode(String guiId, String nodeId, int x, int y) {
        putNode(guiId, nodeId, x, y, 1f);
    }

    public static void putNode(String guiId, String nodeId, int x, int y, float scale) {
        JsonObject root = root();
        if (!root.has("guis") || !root.get("guis").isJsonObject()) {
            root.add("guis", new JsonObject());
        }
        JsonObject gui = root.getAsJsonObject("guis").getAsJsonObject(guiId);
        if (gui == null) {
            gui = new JsonObject();
            root.getAsJsonObject("guis").add(guiId, gui);
        }
        if (!gui.has("nodes") || !gui.get("nodes").isJsonObject()) {
            gui.add("nodes", new JsonObject());
        }
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("scale", scale);
        gui.getAsJsonObject("nodes").add(nodeId, o);
    }
}
