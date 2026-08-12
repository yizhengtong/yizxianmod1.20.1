package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 聊天历史持久化（客户端）— 每次往历史列表加条目（发消息/指令）时保存到本地数据，
 * 配合 ClientChatHistoryHandler 在进世界时灌回，实现退出重进后上下键仍能召回过去指令。
 */
@Mixin(value = ChatComponent.class, priority = Integer.MAX_VALUE)
public abstract class ChatComponentHistoryMixin {

    @Inject(method = "addRecentChat", at = @At("HEAD"))
    private void yizxianmod$saveChatHistory(String string, CallbackInfo ci) {
        net.minecraft.client.yiz.xian.core.ClientChatHistory.record(string);
    }
}
