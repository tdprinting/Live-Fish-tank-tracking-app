package com.reeflive.app.render

import android.opengl.GLES30

/**
 * Marine snow (ARCHITECTURE §8.4): [count] drifting flakes drawn as GL_POINTS from an empty VAO; snow.vert places
 * each one from gl_VertexID and the frame time, so there is no buffer upload and no CPU work per frame. Drawn in
 * the transparent pass (depth test on, no depth write, alpha blend).
 */
class MarineSnow {
    private val vao = Gl.genVertexArray()

    /** Returns the draw calls issued. */
    fun draw(p: ShaderProgram, count: Int, viewportH: Int): Int {
        if (count <= 0) return 0
        p.use()
        p.set1f("uViewportH", viewportH.toFloat())
        GLES30.glBindVertexArray(vao); Gl.check("glBindVertexArray(snow)")
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count); Gl.check("glDrawArrays(snow)")
        GLES30.glBindVertexArray(0); Gl.check("unbind")
        return 1
    }

    fun release() = Gl.deleteVertexArray(vao)
}
