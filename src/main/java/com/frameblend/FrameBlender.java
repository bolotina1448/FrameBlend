package com.frameblend;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

/**
 * Frame blending (a.k.a. motion blur by accumulation).
 *
 * Every frame:  history = current * (1 - w) + history * w
 * and the screen then shows {@code history}.
 *
 * Two paths:
 *  - FBO path (normal case): the history lives in a 16-bit-per-channel texture, so trails
 *    fade out cleanly instead of leaving faint "stuck" ghosts from 8-bit rounding.
 *  - Copy path (framebuffers disabled, e.g. OptiFine Fast Render): the history texture is
 *    blended over the back buffer, then the result is copied back into the texture.
 *
 * All GL state is changed with raw GL calls inside glPushAttrib/glPopAttrib, so Minecraft's
 * GlStateManager cache never goes out of sync.
 */
public class FrameBlender {
    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_RGBA16 = 0x805B;

    private final Minecraft mc = Minecraft.getMinecraft();

    private int historyTex = -1;
    private int historyFbo = -1;
    private int texW, texH;
    private boolean texForFbo;
    private boolean fboBroken;

    private boolean needsReset = true;
    private boolean blendedThisFrame;
    private long lastFrameNanos;
    private World lastWorld;

    public void reset() {
        needsReset = true;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            blendedThisFrame = false;
        } else if (!blendedThisFrame) {
            // Reached when the HUD is hidden (F1) or when blendHud is on.
            blend();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOverlay(RenderGameOverlayEvent.Pre event) {
        // Fires right after the world + hand are drawn and before the HUD,
        // so blending here keeps the HUD sharp.
        if (event.type == RenderGameOverlayEvent.ElementType.ALL && !FrameBlendConfig.blendHud) {
            blend();
        }
    }

    private void blend() {
        blendedThisFrame = true;

        if (!FrameBlendConfig.enabled || FrameBlendConfig.strength <= 0 || mc.theWorld == null) {
            needsReset = true;
            return;
        }
        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            needsReset = true;
        }

        long now = System.nanoTime();
        double dt = (now - lastFrameNanos) / 1.0e9;
        lastFrameNanos = now;
        if (dt <= 0 || dt > 0.25) needsReset = true; // game froze / alt-tabbed: don't smear an old frame

        double s = FrameBlendConfig.strength / 100.0;
        double w = FrameBlendConfig.fpsIndependent ? Math.pow(s, dt * 60.0) : s;
        w = Math.max(0.0, Math.min(0.99, w));

        boolean useFbo = OpenGlHelper.isFramebufferEnabled() && !fboBroken;
        Framebuffer main = mc.getFramebuffer();
        int width = useFbo ? main.framebufferWidth : mc.displayWidth;
        int height = useFbo ? main.framebufferHeight : mc.displayHeight;
        if (width <= 0 || height <= 0) return;

        boolean gl20 = GLContext.getCapabilities().OpenGL20;
        int prevProgram = gl20 ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
        int prevFbo = OpenGlHelper.isFramebufferEnabled() ? GL11.glGetInteger(GL_FRAMEBUFFER_BINDING) : 0;
        if (prevProgram != 0) GL20.glUseProgram(0);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        pushMatrices();
        try {
            if (!ensureResources(width, height, useFbo)) {
                // FBO setup failed on this GPU; the copy path takes over next frame.
                return;
            }
            setupState();
            if (useFbo) {
                blendWithFbo(main, w);
            } else {
                blendWithCopy(width, height, w);
            }
            needsReset = false;
        } finally {
            popMatrices();
            GL11.glPopAttrib();
            if (OpenGlHelper.isFramebufferEnabled()) {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFbo);
            }
            if (prevProgram != 0) GL20.glUseProgram(prevProgram);
        }
    }

    // ---------------------------------------------------------------- paths

    private void blendWithFbo(Framebuffer main, double w) {
        // 1) history = current * (1 - w) + history * w
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, historyFbo);
        GL11.glViewport(0, 0, texW, texH);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, main.framebufferTexture);
        setBlend(needsReset ? -1 : (float) (1.0 - w));
        drawQuad((float) main.framebufferWidth / main.framebufferTextureWidth,
                 (float) main.framebufferHeight / main.framebufferTextureHeight);

        // 2) screen = history
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, main.framebufferObject);
        GL11.glViewport(0, 0, main.framebufferWidth, main.framebufferHeight);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
        setBlend(-1);
        drawQuad(1f, 1f);
    }

    private void blendWithCopy(int width, int height, double w) {
        GL11.glViewport(0, 0, width, height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
        if (!needsReset) {
            // screen = history * w + current * (1 - w)
            setBlend((float) w);
            drawQuad(1f, 1f);
        }
        // history = screen
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    // ------------------------------------------------------------- helpers

    /** alpha < 0 disables blending; otherwise result = src * alpha + dst * (1 - alpha). */
    private static void setBlend(float alpha) {
        if (alpha < 0) {
            GL11.glDisable(GL11.GL_BLEND);
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL14.glBlendColor(0f, 0f, 0f, alpha);
        GL11.glBlendFunc(GL14.GL_CONSTANT_ALPHA, GL14.GL_ONE_MINUS_CONSTANT_ALPHA);
    }

    private static void drawQuad(float u, float v) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(u, 0f);  GL11.glVertex2f(1f, 0f);
        GL11.glTexCoord2f(u, v);   GL11.glVertex2f(1f, 1f);
        GL11.glTexCoord2f(0f, v);  GL11.glVertex2f(0f, 1f);
        GL11.glEnd();
    }

    private static void setupState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glColorMask(true, true, true, false);

        // Make sure only texture unit 0 is active (the lightmap lives on unit 1).
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void pushMatrices() {
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
    }

    private static void popMatrices() {
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    /** (Re)creates the history texture/FBO when the size or path changes. */
    private boolean ensureResources(int width, int height, boolean useFbo) {
        if (historyTex != -1 && texW == width && texH == height && texForFbo == useFbo) return true;

        deleteResources();
        texW = width;
        texH = height;
        texForFbo = useFbo;
        needsReset = true;

        if (!useFbo) {
            historyTex = createTexture(width, height, GL11.GL_RGBA8);
            return true;
        }

        historyFbo = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, historyFbo);

        // Prefer 16-bit for clean fades; fall back to 8-bit if the driver refuses.
        int[] formats = {GL_RGBA16, GL11.GL_RGBA8};
        for (int format : formats) {
            historyTex = createTexture(width, height, format);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, historyTex, 0);
            if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
                return true;
            }
            GL11.glDeleteTextures(historyTex);
            historyTex = -1;
        }

        deleteResources();
        fboBroken = true;
        FrameBlendMod.chat("Couldn't create a framebuffer on this GPU, using the fallback method.");
        return false;
    }

    private static int createTexture(int width, int height, int internalFormat) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return tex;
    }

    private void deleteResources() {
        if (historyFbo != -1) {
            OpenGlHelper.glDeleteFramebuffers(historyFbo);
            historyFbo = -1;
        }
        if (historyTex != -1) {
            GL11.glDeleteTextures(historyTex);
            historyTex = -1;
        }
    }
}
