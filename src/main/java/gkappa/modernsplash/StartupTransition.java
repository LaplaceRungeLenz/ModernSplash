package gkappa.modernsplash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import cpw.mods.fml.common.ProgressManager.ProgressBar;

/** Startup-only GPU snapshots, handed to the render thread by the splash worker's join. LGPL-2.1. */
public final class StartupTransition {

    private static int brandTexture;
    private static int detailsTexture;
    private static GuiScreen screen;
    private static long startedAt;
    private static boolean started;

    private StartupTransition() {}

    static void capture(CustomSplash.FrameRenderer renderer, ProgressBar first, ProgressBar penult, ProgressBar last) {
        try {
            renderer.drawBackgroundAndLogo();
            brandTexture = snapshot();
            renderer.drawCompletedFrame(first, penult, last);
            detailsTexture = snapshot();
            GL11.glFlush();
        } catch (RuntimeException | LinkageError e) {
            close();
            ModernSplash.LOGGER.warn("Cannot capture startup transition; opening the menu directly", e);
        }
    }

    private static int snapshot() {
        int texture = GL11.glGenTextures();
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int buffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glCopyTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGB8,
                0,
                0,
                Display.getWidth(),
                Display.getHeight(),
                0);
            CustomSplash.checkGLError("Startup transition capture");
            return texture;
        } catch (RuntimeException | LinkageError e) {
            GL11.glDeleteTextures(texture);
            throw e;
        } finally {
            GL11.glReadBuffer(buffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
        }
    }

    public static void ready() {
        if (detailsTexture == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        screen = mc.currentScreen;
        if (screen == null || mc.theWorld != null) close();
    }

    /** Called after the menu is rendered and composited, immediately before the display swap. */
    public static void render() {
        if (detailsTexture == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (screen == null || mc.currentScreen != screen || mc.theWorld != null) {
            close();
            return;
        }
        if (mc.skipRenderWorld) return;
        long now = System.nanoTime();
        if (!started) {
            startedAt = now;
            started = true;
        }
        double phase = (now - startedAt) / (CustomSplash.fadeOutDurationMs * 500000.0);
        if (phase >= 2.0) {
            close();
            return;
        }
        try (State state = new State()) {
            draw(brandTexture, Math.min(1.0, 2.0 - phase));
            if (phase < 1.0) draw(detailsTexture, 1.0 - phase);
        } catch (RuntimeException | LinkageError e) {
            close();
            ModernSplash.LOGGER.warn("Cannot render startup transition; opening the menu directly", e);
        }
    }

    private static void draw(int texture, double alpha) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4d(1, 1, 1, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(0, Display.getHeight());
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(Display.getWidth(), Display.getHeight());
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(Display.getWidth(), 0);
        GL11.glEnd();
    }

    public static void close() {
        if (Display.isCreated()) {
            if (brandTexture != 0) GL11.glDeleteTextures(brandTexture);
            if (detailsTexture != 0) GL11.glDeleteTextures(detailsTexture);
        }
        brandTexture = detailsTexture = 0;
        screen = null;
        started = false;
    }

    /** Preserve the menu's fixed-function state, texture matrices and active shader. */
    private static final class State implements AutoCloseable {

        private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final boolean shaders = GLContext.getCapabilities().OpenGL20;
        private final int program = shaders ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;

        State() {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            if (shaders) GL20.glUseProgram(0);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0, Display.getWidth(), Display.getHeight(), 0, -1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColorMask(true, true, true, true);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        }

        @Override
        public void close() {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            if (shaders) GL20.glUseProgram(program);
            GL11.glPopAttrib();
            OpenGlHelper.setActiveTexture(activeTexture);
            GL11.glMatrixMode(matrixMode);
        }
    }
}
