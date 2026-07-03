package com.nayoguildbridge.mixin;

import com.nayoguildbridge.qol.ChatQoL;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatCopyCaptureMixin {
    @Inject(method = "addClientSystemMessage", at = @At("TAIL"))
    private void ngb$captureClientSystemLine(Component message, CallbackInfo ci) {
        ChatQoL.INSTANCE.setLastChatLine(message.getString());
    }

    @Inject(method = "addServerSystemMessage", at = @At("TAIL"))
    private void ngb$captureServerSystemLine(Component message, CallbackInfo ci) {
        ChatQoL.INSTANCE.setLastChatLine(message.getString());
    }

    @Inject(method = "addPlayerMessage", at = @At("TAIL"))
    private void ngb$capturePlayerLine(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        ChatQoL.INSTANCE.setLastChatLine(message.getString());
    }
}
