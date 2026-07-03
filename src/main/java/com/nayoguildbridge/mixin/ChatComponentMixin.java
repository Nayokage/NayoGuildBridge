package com.nayoguildbridge.mixin;

import com.nayoguildbridge.config.NgbConfig;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"), cancellable = true)
    private void ngb$unlimitedChatRefresh(CallbackInfo ci) {
        ngb$cancelIfUnlimited(ci);
    }

    @Inject(method = "removeTrimmedMessages", at = @At("HEAD"), cancellable = true, require = 0)
    private void ngb$unlimitedChatRemove(CallbackInfo ci) {
        ngb$cancelIfUnlimited(ci);
    }

    private static void ngb$cancelIfUnlimited(CallbackInfo ci) {
        if (NgbConfig.INSTANCE.getConfig().getUnlimitedChat()) {
            ci.cancel();
        }
    }
}
