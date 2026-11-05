package com.tamerslab.ar

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import timber.log.Timber

/**
 * Draws the ARCore camera image as a full-screen background.
 *
 * Ported from the ARCore SDK `hello_ar` sample `BackgroundRenderer` (Copyright 2017 Google LLC,
 * Apache License 2.0), adapted to an OpenGL ES 3.0 context with GLSL ES 3.00 shaders and inline
 * shader sources instead of the sample's asset files.
 *
 * All methods must run on the GL thread (the [android.opengl.GLSurfaceView] renderer callbacks).
 */
internal class ArBackgroundRenderer {

    /** `GL_TEXTURE_EXTERNAL_OES` name handed to [com.google.ar.core.Session.setCameraTextureName]. */
    var textureId: Int = -1
        private set

    val isReady: Boolean
        get() = program != 0 && textureId != 0 && textureId != -1

    private val quadCoords: FloatBuffer = directBuffer(QUAD_COORDS)
    private val quadTexCoords: FloatBuffer = directBuffer(QUAD_TEXCOORDS)

    private var program = 0
    private var positionAttrib = -1
    private var texCoordAttrib = -1
    private var textureUniform = -1

    /** Creates the external camera texture and the shader program. Idempotent. */
    fun createOnGlThread() {
        if (isReady) return
        release()

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        checkGlError("create camera texture")

        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) GLES30.glDeleteShader(vertexShader)
            if (fragmentShader != 0) GLES30.glDeleteShader(fragmentShader)
            return
        }

        val created = GLES30.glCreateProgram()
        GLES30.glAttachShader(created, vertexShader)
        GLES30.glAttachShader(created, fragmentShader)
        GLES30.glLinkProgram(created)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(created, GLES30.GL_LINK_STATUS, linkStatus, 0)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        if (linkStatus[0] == 0) {
            Timber.tag(TAG).e("Could not link the camera background program: %s", GLES30.glGetProgramInfoLog(created))
            GLES30.glDeleteProgram(created)
            return
        }

        program = created
        positionAttrib = GLES30.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES30.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES30.glGetUniformLocation(program, "u_Texture")
        checkGlError("create program")
        Timber.tag(TAG).i("Camera background renderer ready (texture=%d)", textureId)
    }

    /**
     * Draws the camera image for [frame]. UVs are re-queried whenever ARCore reports a display
     * geometry change (rotation or view size).
     */
    fun draw(frame: Frame) {
        if (!isReady) return
        if (frame.hasDisplayGeometryChanged()) {
            updateUvs(frame)
        }
        // ARCore can return timestamp 0 before the first camera image arrives; drawing then would
        // show stale/uninitialized texture data. Clear to black for those frames instead.
        if (frame.timestamp == 0L) {
            clearBackground()
            return
        }

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        clearBackground()

        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUniform1i(textureUniform, 0)

        quadCoords.position(0)
        GLES30.glEnableVertexAttribArray(positionAttrib)
        GLES30.glVertexAttribPointer(positionAttrib, 2, GLES30.GL_FLOAT, false, 0, quadCoords)

        quadTexCoords.position(0)
        GLES30.glEnableVertexAttribArray(texCoordAttrib)
        GLES30.glVertexAttribPointer(texCoordAttrib, 2, GLES30.GL_FLOAT, false, 0, quadTexCoords)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(positionAttrib)
        GLES30.glDisableVertexAttribArray(texCoordAttrib)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glUseProgram(0)

        // Restore the state later draw passes expect.
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        checkGlError("draw camera background")
    }

    /** Releases the GL objects. Safe to call on the GL thread after context loss. */
    fun release() {
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
        if (textureId != -1 && textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
        textureId = -1
        positionAttrib = -1
        texCoordAttrib = -1
        textureUniform = -1
    }

    private fun updateUvs(frame: Frame) {
        quadCoords.position(0)
        quadTexCoords.position(0)
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            quadCoords,
            Coordinates2d.TEXTURE_NORMALIZED,
            quadTexCoords,
        )
        quadTexCoords.position(0)
    }

    private fun clearBackground() {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            Timber.tag(TAG).e("Could not create GL shader (type=%d)", type)
            return 0
        }
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Timber.tag(TAG).e(
                "Could not compile camera background shader: %s",
                GLES30.glGetShaderInfoLog(shader),
            )
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun checkGlError(operation: String) {
        var error = GLES30.glGetError()
        while (error != GLES30.GL_NO_ERROR) {
            Timber.tag(TAG).e("GL error after %s: 0x%x", operation, error)
            error = GLES30.glGetError()
        }
    }

    private companion object {
        const val TAG = "ArBackgroundRenderer"

        /**
         * Full-screen quad in OpenGL normalized device coordinates, drawn as a triangle strip:
         * v0 -> v1 -> v2 then v2 -> v1 -> v3.
         */
        val QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
        )

        /** Placeholder UVs; replaced by ARCore via `transformCoordinates2d` every rotation change. */
        val QUAD_TEXCOORDS = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
        )

        /**
         * Shader sources must start with `#version` as the first token: in the ES profile even a
         * leading newline/indentation is rejected (`#version must occur first in shader`), which
         * made both programs fail to compile on device.
         */
        val VERTEX_SHADER = """
            #version 300 es
            in vec4 a_Position;
            in vec2 a_TexCoord;
            out vec2 v_CameraTexCoord;
            void main() {
                gl_Position = a_Position;
                v_CameraTexCoord = a_TexCoord;
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            in vec2 v_CameraTexCoord;
            uniform samplerExternalOES u_Texture;
            out vec4 o_FragColor;
            void main() {
                o_FragColor = texture(u_Texture, v_CameraTexCoord);
            }
        """.trimIndent()

        fun directBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(values)
                    position(0)
                }
    }
}
