package gkappa.modernsplash.mixin.early;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.LWJGLException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gkappa.modernsplash.CustomSplash;
import gkappa.modernsplash.StartupTransition;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    // @Shadow public TextureManager renderEngine;

    @Redirect(
        method = "startGame",
        at = @At(value = "INVOKE", remap = false, target = "Lcpw/mods/fml/client/SplashProgress;drawVanillaScreen()V"))
    private void rdDrawVanillaScreen() throws LWJGLException {
        CustomSplash.drawVanillaScreen();
    }

    @Redirect(
        method = "startGame",
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lcpw/mods/fml/client/SplashProgress;clearVanillaResources(Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/util/ResourceLocation;)V"))
    private void rdClear(TextureManager renderEngine, ResourceLocation mojangLogo) {
        CustomSplash.clearVanillaResources(renderEngine, mojangLogo);
    }

    @Inject(method = "startGame", at = @At("RETURN"))
    private void modernsplash$ready(CallbackInfo ci) {
        StartupTransition.ready();
    }

    @Inject(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_147120_f()V"))
    private void modernsplash$transition(CallbackInfo ci) {
        StartupTransition.render();
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void modernsplash$close(CallbackInfo ci) {
        StartupTransition.close();
    }

}
