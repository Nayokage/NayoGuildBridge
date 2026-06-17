package com.nayoguildbridge.mixin;

import com.nayoguildbridge.qol.ChatQoL;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatCopyCaptureMixin {
    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("TAIL")
    )
    private void ngb$captureLine(Component message, CallbackInfo ci) {
        ChatQoL.INSTANCE.setLastChatLine(message.getString());
    }
}
