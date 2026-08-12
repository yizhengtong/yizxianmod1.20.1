package net.minecraft.client.yiz.xian.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端聊天/指令历史持久化 — 记录最近 100 条发送的聊天与指令，保存到本地数据
 * （游戏目录 {@code yizxianmod_chat_history.json}），退出存档不丢失，重进后聊天框上下键可召回。
 */
public final class ClientChatHistory {

    private static final int MAX = 100;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_FILE = FMLPaths.GAMEDIR.get().resolve("yizxianmod_chat_history.json");

    /** 内存缓存（时间序：旧→新）。 */
    private static final List<String> HISTORY = new ArrayList<>();

    private ClientChatHistory() {}

    /** 记录一条聊天/指令（ChatComponent.addRecentChat 时调用）。 */
    public static void record(String msg) {
        if (msg == null || msg.isBlank()) return;
        synchronized (HISTORY) {
            HISTORY.add(msg);
            while (HISTORY.size() > MAX) HISTORY.remove(0);
        }
        save();
    }

    /** 读取全部历史（时间序：旧→新）。 */
    public static List<String> load() {
        synchronized (HISTORY) {
            if (!HISTORY.isEmpty()) return new ArrayList<>(HISTORY);
        }
        if (!Files.exists(SAVE_FILE)) return List.of();
        try {
            String json = Files.readString(SAVE_FILE);
            Type type = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = GSON.fromJson(json, type);
            if (loaded == null) return List.of();
            synchronized (HISTORY) {
                HISTORY.clear();
                HISTORY.addAll(loaded);
                while (HISTORY.size() > MAX) HISTORY.remove(0);
                return new ArrayList<>(HISTORY);
            }
        } catch (IOException e) {
            LOGGER.warn("加载聊天历史失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static void save() {
        try {
            List<String> snapshot;
            synchronized (HISTORY) {
                snapshot = new ArrayList<>(HISTORY);
            }
            Files.createDirectories(SAVE_FILE.getParent());
            Files.writeString(SAVE_FILE, GSON.toJson(snapshot));
        } catch (IOException e) {
            LOGGER.warn("保存聊天历史失败: {}", e.getMessage());
        }
    }
}
