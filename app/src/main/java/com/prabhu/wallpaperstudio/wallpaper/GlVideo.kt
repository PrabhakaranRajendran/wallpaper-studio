package com.prabhu.wallpaperstudio.wallpaper

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Minimal OpenGL ES 2 drawing for the video wallpaper. The video arrives in a
 * SurfaceTexture, whose transform matrix already includes the video's rotation,
 * so portrait videos stand upright. On top of it a bitmap overlay (widgets) is drawn.
 *
 * Everything runs on one thread (the main thread of the wallpaper).
 */
class GlVideo {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var oesProgram = 0
    private var texProgram = 0
    private var oesTex = 0
    private var overlayTex = 0
    private var overlayReady = false

    var surfaceTexture: SurfaceTexture? = null
        private set
    var videoSurface: Surface? = null
        private set
    private var frameAvailable = false

    private val stMatrix = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val flipY = FloatArray(16).also {
        Matrix.setIdentityM(it, 0)
        Matrix.translateM(it, 0, 0f, 1f, 0f)
        Matrix.scaleM(it, 0, 1f, -1f, 1f)
    }
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val pv = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    private val quad: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val tex: FloatBuffer = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    /** Sets up OpenGL on the wallpaper's surface. Returns false if this phone refused. */
    fun init(window: Surface, handler: Handler, onFrame: () -> Unit): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return false
        val config = configs[0] ?: return false
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        if (context == EGL14.EGL_NO_CONTEXT) return false
        surface = EGL14.eglCreateWindowSurface(display, config, window, intArrayOf(EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) return false
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return false

        oesProgram = program(VERTEX, FRAG_OES)
        texProgram = program(VERTEX, FRAG_2D)
        oesTex = newTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        overlayTex = newTexture(GLES20.GL_TEXTURE_2D)

        val st = SurfaceTexture(oesTex)
        st.setOnFrameAvailableListener({
            frameAvailable = true
            onFrame()
        }, handler)
        surfaceTexture = st
        videoSurface = Surface(st)
        return true
    }

    fun setOverlay(bitmap: Bitmap?) {
        if (context == EGL14.EGL_NO_CONTEXT) return
        if (bitmap == null) {
            overlayReady = false
            return
        }
        EGL14.eglMakeCurrent(display, surface, surface, context)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTex)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        overlayReady = true
    }

    /**
     * Draws one frame.
     * @param videoW / videoH the video's size as it should be shown (already upright)
     * @param extraRotation extra clockwise rotation in degrees (crop rotation, or rotation the player didn't apply)
     * @param scale 1 = cover the screen ("fill"), computed by the caller
     * @param dx / dy offset in pixels (screen coordinates, y down)
     * @param tiltX / tiltY 3D tilt in degrees
     */
    fun draw(
        w: Int, h: Int,
        videoW: Int, videoH: Int,
        extraRotation: Int, flip: Boolean,
        scale: Float, dx: Float, dy: Float,
        tiltX: Float, tiltY: Float,
        alpha: Float,
        showOverlay: Boolean,
    ) {
        if (context == EGL14.EGL_NO_CONTEXT) return
        EGL14.eglMakeCurrent(display, surface, surface, context)
        val st = surfaceTexture ?: return
        if (frameAvailable) {
            frameAvailable = false
            try {
                st.updateTexImage()
            } catch (e: Exception) {
                return
            }
        }
        st.getTransformMatrix(stMatrix)

        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // A perspective camera whose z = 0 plane maps exactly onto the screen, in pixels.
        val d = h * 1.2f
        val near = d * 0.1f
        Matrix.frustumM(proj, 0, -w / 2f * near / d, w / 2f * near / d, -h / 2f * near / d, h / 2f * near / d, near, d * 10f)
        Matrix.setIdentityM(view, 0)
        Matrix.translateM(view, 0, 0f, 0f, -d)
        Matrix.multiplyMM(pv, 0, proj, 0, view, 0)

        if (videoW > 0 && videoH > 0) {
            val rot = ((extraRotation % 360) + 360) % 360
            val dw = if (rot % 180 == 0) videoW else videoH
            val dh = if (rot % 180 == 0) videoH else videoW
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, dx, -dy, 0f)
            if (tiltX != 0f) Matrix.rotateM(model, 0, tiltX, 0f, 1f, 0f)
            if (tiltY != 0f) Matrix.rotateM(model, 0, tiltY, 1f, 0f, 0f)
            Matrix.scaleM(model, 0, dw * scale / 2f, dh * scale / 2f, 1f)
            if (rot != 0) Matrix.rotateM(model, 0, -rot.toFloat(), 0f, 0f, 1f)
            if (flip) Matrix.scaleM(model, 0, -1f, 1f, 1f)
            Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)
            drawQuad(oesProgram, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex, stMatrix, alpha, blend = false)
        }

        if (showOverlay && overlayReady) {
            Matrix.setIdentityM(model, 0)
            Matrix.scaleM(model, 0, w / 2f, h / 2f, 1f)
            Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)
            drawQuad(texProgram, GLES20.GL_TEXTURE_2D, overlayTex, flipY, 1f, blend = true)
        }
        EGL14.eglSwapBuffers(display, surface)
    }

    private fun drawQuad(prog: Int, target: Int, texture: Int, texMatrix: FloatArray, alpha: Float, blend: Boolean) {
        GLES20.glUseProgram(prog)
        if (blend) {
            GLES20.glEnable(GLES20.GL_BLEND)
            // Android uploads bitmaps with premultiplied alpha.
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            GLES20.glDisable(GLES20.GL_BLEND)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(target, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "sTex"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uTexM"), 1, false, texMatrix, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(prog, "uAlpha"), alpha)
        val aPos = GLES20.glGetAttribLocation(prog, "aPos")
        val aTex = GLES20.glGetAttribLocation(prog, "aTex")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, tex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    fun release() {
        videoSurface?.release()
        videoSurface = null
        surfaceTexture?.release()
        surfaceTexture = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
        overlayReady = false
    }

    private fun newTexture(target: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(target, ids[0])
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun program(vs: String, fs: String): Int {
        fun shader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    companion object {
        private fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                position(0)
            }

        private val VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uTexM;
            attribute vec4 aPos;
            attribute vec4 aTex;
            varying vec2 vTex;
            void main() {
                gl_Position = uMvp * aPos;
                vTex = (uTexM * aTex).xy;
            }
        """.trimIndent()

        private val FRAG_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES sTex;
            uniform float uAlpha;
            void main() {
                gl_FragColor = texture2D(sTex, vTex) * uAlpha;
            }
        """.trimIndent()

        private val FRAG_2D = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D sTex;
            uniform float uAlpha;
            void main() {
                gl_FragColor = texture2D(sTex, vTex) * uAlpha;
            }
        """.trimIndent()
    }
}
