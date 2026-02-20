@file:Suppress("UseCheckOrError")

package com.onyx.android.ink.gl

import android.opengl.GLES20
import android.util.Log

internal class ShaderProgram(
    vertexShaderSource: String,
    fragmentShaderSource: String,
) {
    val programId: Int =
        run {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val info = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw IllegalStateException("Failed to link shader program: $info")
            }
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            program
        }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
        }
    }

    private fun compileShader(
        type: Int,
        source: String,
    ): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Failed to compile shader: $info")
        }
        return shader
    }

    companion object {
        private const val TAG = "ShaderProgram"

        fun safeRelease(program: ShaderProgram?) {
            runCatching { program?.release() }
                .onFailure { error ->
                    Log.w(TAG, "Failed to release shader program", error)
                }
        }
    }
}
