package com.nayoguildbridge.mixin;

import com.nayoguildbridge.config.NgbConfig;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    /** 1.21.10+: was removeTrimmedMessages in older versions */
    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"), cancellable = true)
    private void ngb$unlimitedChat(CallbackInfo ci) {
        if (NgbConfig.INSTANCE.getConfig().getUnlimitedChat()) {
            ci.cancel();
        }
    }
}
