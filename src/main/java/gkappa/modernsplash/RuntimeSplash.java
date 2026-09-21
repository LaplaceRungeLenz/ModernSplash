package gkappa.modernsplash;

import net.minecraft.util.ResourceLocation;

/**
 * LGPL-2.1, as derived runtime support for CustomSplash; see LICENSE.
 *
 * Optional runtime reload API v1. Call only on the client render thread after startup.
 * The caller owns GL state preservation, framebuffer selection, display swaps and transitions.
 * Resources are private to each session and never use the reloading texture manager.
 */
public final class RuntimeSplash {

    private static CustomSplash.Texture font;
    private static CustomSplash.Texture logo;
    private static CustomSplash.Texture forge;
    private static CustomSplash.FrameRenderer renderer;
    private static Thread owner;

    private RuntimeSplash() {}

    public static int apiVersion() {
        return 1;
    }

    public static boolean begin() {
        if (owner != null) throw new IllegalStateException("Runtime splash already active");
        if (!CustomSplash.enabled || CustomSplash.config == null
            || !CustomSplash.done
            || (CustomSplash.thread != null && CustomSplash.thread.isAlive())) return false;
        owner = Thread.currentThread();
        try {
            font = new CustomSplash.Texture(
                new ResourceLocation(CustomSplash.config.getProperty("fontTexture", "textures/font/ascii.png")));
            logo = new CustomSplash.Texture(
                new ResourceLocation(
                    CustomSplash.config.getProperty("logoTexture", "modernsplash:textures/gui/title/mojang.png")));
            if (CustomSplash.forgeLogo) {
                forge = new CustomSplash.Texture(
                    new ResourceLocation(
                        CustomSplash.config.getProperty("forgeTexture", "fml:textures/gui/forge.gif")));
            }
            renderer = new CustomSplash.FrameRenderer(new CustomSplash.SplashFontRenderer(font), logo, forge);
            return true;
        } catch (RuntimeException | LinkageError e) {
            end();
            throw e;
        }
    }

    public static void render(String title, String detail, int completed, int total) {
        checkOwner();
        if (renderer == null) throw new IllegalStateException("Runtime splash not started");
        renderer.drawFrame(
            null,
            null,
            null,
            title == null ? "Reloading" : title,
            detail == null ? "" : detail,
            completed,
            total);
    }

    public static void end() {
        if (owner == null) return;
        checkOwner();
        try {
            if (font != null) font.delete();
            if (logo != null) logo.delete();
            if (forge != null) forge.delete();
        } finally {
            font = logo = forge = null;
            renderer = null;
            owner = null;
        }
    }

    private static void checkOwner() {
        if (owner != Thread.currentThread()) throw new IllegalStateException("Wrong runtime splash thread");
    }
}
