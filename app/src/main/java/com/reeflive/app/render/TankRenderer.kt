package com.reeflive.app.render

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.reeflive.core.geom.Sdf
import com.reeflive.core.geom.SdfGrid
import com.reeflive.core.geom.Vec3
import com.reeflive.core.sim.CoralPose
import com.reeflive.core.sim.SimSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * The GL-thread renderer (ARCHITECTURE §8.2 `TankRenderer`, frame order §8.6): reads the latest
 * [SimSnapshot], interpolates organisms, updates the camera, then draws opaque → alpha-tested fins/cards →
 * transparent (shafts, particles, water surface, glass) → bloom (High).
 */
class TankRenderer(context: Context, initialQuality: RenderQuality) : GLSurfaceView.Renderer {
    private val app = context.applicationContext
    private val assets = app.assets
    private val main = Handler(Looper.getMainLooper())

    // ---- cross-thread inputs ----
    @Volatile var snapshotProvider: (() -> SimSnapshot?)? = null
    @Volatile var listener: TankViewListener? = null
    /** Draw the circling-fish test harness when no provider is set or it returns null. */
    @Volatile var demoWhenEmpty = true
    @Volatile private var requestedQuality = initialQuality
    @Volatile private var followRequest: String? = null
    @Volatile private var followDirty = false
    @Volatile private var resetRequested = false
    val input = CameraInput()

    private val _stats = MutableStateFlow(RenderStats(quality = initialQuality))
    val stats: StateFlow<RenderStats> = _stats

    // ---- GL-thread state ----
    val camera = Camera()
    private var quality = initialQuality
    private var programs: Programs? = null
    private var frame: FrameUniforms? = null
    private var post: PostFx? = null
    private var particlesGl: ParticleRenderer? = null
    private var shafts: LightShafts? = null
    private var placeholder: Mesh? = null
    private var white: Texture? = null
    private var flatNormal: Texture? = null
    private var neutralRough: Texture? = null
    private var zero: Texture? = null
    private var surfaceTex: Texture? = null
    private var surfaceDims = IntArray(2)
    private var lastSurfaceSnap: SimSnapshot? = null
    private var store = AssetStore(assets, initialQuality)
    private val registry = ModelRegistry()
    private val interp = SnapshotInterpolator()
    private val particles = ParticleField(initialQuality.particles)
    @Volatile private var sdf: Sdf? = null
    private var width = 1; private var height = 1
    private val startNs = System.nanoTime()
    private var lastNs = 0L
    private var drawCalls = 0; private var triangles = 0
    private var fpsFrames = 0; private var fpsStartNs = 0L; private var lastFps = 0f; private var lastFrameMs = 0f
    private val shaderCache = HashMap<String, String>()
    private val source: (String) -> String = { n -> shaderCache.getOrPut(n) { assets.open("shaders/$n").bufferedReader().use { it.readText() } } }

    init {
        Thread({
            sdf = runCatching { SdfGrid.parse(assets.open("sim/rock_sdf.bin").use { it.readBytes() }) }
                .onFailure { Log.w(Gl.TAG, "rock SDF unavailable; follow camera without collision", it) }.getOrNull()
        }, "reef-sdf").start()
    }

    // ------------------------------------------------------------------ public (any thread)

    fun setQuality(q: RenderQuality) { requestedQuality = q }
    fun follow(id: String?) { followRequest = id; followDirty = true }
    fun resetCamera() { resetRequested = true }

    // ------------------------------------------------------------------ GLSurfaceView.Renderer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context: every GL object from a previous one is gone.
        store.onContextLost(quality)
        Texture.resetCaps()
        initGl()
        _stats.value = _stats.value.copy(glInfo = "${GLES30.glGetString(GLES30.GL_RENDERER)} / ${GLES30.glGetString(GLES30.GL_VERSION)}")
    }

    private fun initGl() {
        programs = Programs(source, quality)
        frame = FrameUniforms()
        particlesGl = ParticleRenderer()
        shafts = LightShafts()
        white = Texture.solid(255, 255, 255)
        flatNormal = Texture.solid(128, 128, 255)
        neutralRough = Texture.solid(217, 255, 0)
        zero = Texture.floatR(1, 1).also { Texture.updateFloatR(it, floatArrayOf(0f)) }
        surfaceTex = null; surfaceDims = IntArray(2); lastSurfaceSnap = null
        val (v, i) = Placeholder.spindle()
        placeholder = Mesh.create(v, i)
        post = if (quality.bloom) runCatching { PostFx(source, quality.msaa) }.onFailure { Log.e(Gl.TAG, "PostFx", it) }.getOrNull() else null
        post?.resize(width, height)
        // kick off the static scene and shared textures
        SCENE_MODELS.forEach { store.model(it) }
        SHARED_TEXTURES.forEach { store.texture(it) }
    }

    private fun releaseGl() {
        programs?.release(); programs = null
        frame?.release(); frame = null
        post?.release(); post = null
        particlesGl?.release(); particlesGl = null
        shafts?.release(); shafts = null
        placeholder?.release(); placeholder = null
        listOf(white, flatNormal, neutralRough, zero, surfaceTex).forEach { it?.release() }
        white = null; flatNormal = null; neutralRough = null; zero = null; surfaceTex = null
        store.release()
        store.onContextLost(quality)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = maxOf(w, 1); height = maxOf(h, 1)
        GLES30.glViewport(0, 0, width, height); Gl.check("glViewport")
        post?.resize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 1f / 60f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.25f)
        lastNs = now
        val time = ((now - startNs) / 1e9).toFloat()
        drawCalls = 0; triangles = 0

        applyRequests()
        store.pump(2)

        // 1. snapshot + rigs
        val snap = snapshotProvider?.invoke() ?: if (demoWhenEmpty) DemoSnapshots.at(time.toDouble()) else null
        interp.push(snap, now)
        val rigs = interp.update(now)
        val cur = interp.current
        val light = LightingState.from(cur?.lights ?: DemoSnapshots.DAY)

        // 2. camera (+ input)
        val taps = input.drain(camera, height.toFloat())
        camera.update(dt, width, height, sdf, { id -> interp.rigs[id] }) { lost -> main.post { listener?.onFollowLost(lost) } }
        for (t in taps) handleTap(t, rigs)

        // 3. per-frame uniforms + shared textures
        updateSurface(cur)
        val fu = frame ?: return
        val progs = programs ?: return
        fu.fill(camera, time, light, quality, cur?.algaeBloom ?: 0f, cur?.flowStrength ?: 1f, surfaceTex != null, surfaceDims[0], surfaceDims[1])
        bindShared()
        particles.ambientCount = quality.particles
        particles.step(dt, time, cur)

        // 4. draw
        val usePost = post?.ok == true
        if (usePost) post!!.begin() else { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); Gl.check("bind default"); GLES30.glViewport(0, 0, width, height); Gl.check("viewport") }
        GLES30.glClearColor(0.012f, 0.016f, 0.024f, 1f); Gl.check("glClearColor")
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT); Gl.check("glClear")

        val tank = store.model(TANK); val rock = store.model(ROCK); val equipment = store.model(EQUIPMENT); val algae = store.model(ALGAE)
        // opaque: back glass, sand, rock, equipment
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.NONE, cull = false)
        tank?.let { drawStatic(it, progs, light, opaque = true) }
        rock?.let { drawStatic(it, progs, light, opaque = true) }
        equipment?.let { drawStatic(it, progs, light, opaque = true) }
        // corals: soft bodies then polyps
        val corals = coralModels(cur)
        for ((m, pose) in corals) drawCoral(m, pose, progs, cur, polyps = false)
        for ((m, pose) in corals) drawCoral(m, pose, progs, cur, polyps = true)
        // fish bodies, inverts
        for (r in rigs) drawOrganism(r, progs, pass = 0)
        // alpha-tested/blended fins and algae cards (depth write on, sorted far → near)
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.ALPHA, cull = false)
        algae?.let { drawAlgae(it, progs) }
        val sorted = rigs.sortedByDescending { it.distanceTo(camera.eye[0], camera.eye[1], camera.eye[2]) }
        for (r in sorted) drawOrganism(r, progs, pass = 1)
        // transparent: shafts, particles, water surface, blended equipment, glass
        if (quality.shafts && light.intensity > 0.03f) {
            Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ADD, cull = false)
            shafts?.let { it.draw(progs.shafts); drawCalls++; triangles += it.triangles }
        }
        Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ALPHA, cull = false)
        particlesGl?.let { drawCalls += it.draw(progs.particles, particles, height) }
        tank?.let { drawStatic(it, progs, light, opaque = false, only = MaterialKind.WATER) }
        equipment?.let { drawStatic(it, progs, light, opaque = false) }
        Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ALPHA, cull = false)
        tank?.let { drawStatic(it, progs, light, opaque = false, only = MaterialKind.GLASS) }
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.NONE, cull = false)

        if (usePost) drawCalls += post!!.end()
        publishStats(now, rigs.size, light)
    }

    // ------------------------------------------------------------------ requests

    private fun applyRequests() {
        if (followDirty) { followDirty = false; camera.follow(followRequest) }
        if (resetRequested) { resetRequested = false; camera.reset() }
        val q = requestedQuality
        if (q != quality) {
            quality = q
            releaseGl()
            store = AssetStore(assets, q).also { store.shutdown() }
            initGl()
            post?.resize(width, height)
        }
    }

    // ------------------------------------------------------------------ shared textures / surface

    private fun bindShared() {
        val w = white ?: return
        (store.texture(CAUSTIC) ?: w).bind(3)
        (surfaceTex ?: zero ?: w).bind(4)
        (store.texture(IRID) ?: w).bind(5)
        (store.texture(ROOM_ENV) ?: w).bind(6)
        (store.texture(WATER_N1) ?: flatNormal ?: w).bind(7)
        (store.texture(WATER_N2) ?: flatNormal ?: w).bind(8)
    }

    private fun updateSurface(s: SimSnapshot?) {
        if (s == null || s === lastSurfaceSnap) return
        lastSurfaceSnap = s
        val sx = s.surfaceDims.getOrElse(0) { 0 }; val sz = s.surfaceDims.getOrElse(1) { 0 }
        if (sx <= 1 || sz <= 1 || s.surfaceHeights.size < sx * sz) return
        if (surfaceTex == null || surfaceDims[0] != sx || surfaceDims[1] != sz) {
            surfaceTex?.release()
            surfaceTex = Texture.floatR(sx, sz); surfaceDims = intArrayOf(sx, sz)
        }
        Texture.updateFloatR(surfaceTex!!, s.surfaceHeights)
    }

    // ------------------------------------------------------------------ static scene

    private fun drawStatic(m: GpuModel, progs: Programs, light: LightingState, opaque: Boolean, only: MaterialKind? = null) {
        val w = white ?: return
        for (part in m.parts) {
            if (only != null && part.kind != only) continue
            val transparent = part.kind == MaterialKind.GLASS || part.kind == MaterialKind.WATER || part.material.src.blended
            if (transparent == opaque) continue
            val p = when (part.kind) {
                MaterialKind.ROCK -> progs.rock
                MaterialKind.SAND -> progs.sand
                MaterialKind.GLASS -> progs.glass
                MaterialKind.WATER -> progs.water
                MaterialKind.SOFT_CORAL -> progs.softCoral
                else -> progs.generic
            }
            p.use()
            p.setMat4("uModel", part.node.world)
            part.material.apply(p, w)
            when (part.kind) {
                MaterialKind.ROCK -> {
                    (if (quality.normalMaps) store.texture(ROCK_NORMAL) else null)?.bind(1) ?: flatNormal?.bind(1)
                    (store.texture(ROCK_ROUGH_AO) ?: neutralRough)?.bind(2)
                    p.set1f("uNormalStrength", if (quality.normalMaps && store.texture(ROCK_NORMAL) != null) 1f else 0f)
                    p.set1f("uDetailScale", 1f)
                }
                MaterialKind.SAND -> {
                    (if (quality.normalMaps) store.texture(SAND_NORMAL) else null)?.bind(1) ?: flatNormal?.bind(1)
                    neutralRough?.bind(2)
                    p.set1f("uNormalStrength", if (quality.normalMaps && store.texture(SAND_NORMAL) != null) 0.6f else 0f)
                    p.set1f("uDetailScale", 4f)
                }
                MaterialKind.EMITTER -> {
                    // AI Prime lens: emissive follows the live light colour
                    val c = light.lightColor()
                    p.set3f("uEmissive", c[0] * 3f, c[1] * 3f, c[2] * 3f)
                }
                else -> Unit
            }
            part.mesh.draw()
            drawCalls++; triangles += part.mesh.triangles
        }
    }

    private fun drawAlgae(m: GpuModel, progs: Programs) {
        val w = white ?: return
        val p = progs.card
        p.use()
        for (part in m.parts) {
            p.setMat4("uModel", part.node.world)
            part.material.apply(p, w, alphaTestOverride = part.node.float("alphaTest") ?: 0.4f)
            coralUniforms(p, m, null, 0.3f)
            part.mesh.draw(); drawCalls++; triangles += part.mesh.triangles
        }
    }

    // ------------------------------------------------------------------ corals

    private fun coralModels(s: SimSnapshot?): List<Pair<GpuModel, CoralPose?>> {
        val out = ArrayList<Pair<GpuModel, CoralPose?>>()
        if (s == null || s.organisms.firstOrNull()?.id?.startsWith("demo_") == true && s.corals.isEmpty()) {
            for (path in DEMO_CORALS) store.model(path)?.let { out += it to null }
            return out
        }
        val used = HashSet<String>()
        for (c in s.corals) {
            val candidates = registry.coralModels(c.speciesId).mapNotNull { store.model(it) }
            val m = candidates.firstOrNull { it.rootAnchor != null && it.rootAnchor == c.anchor }
                ?: candidates.firstOrNull { it.path !in used } ?: continue
            if (used.add(m.path)) out += m to c
        }
        return out
    }

    private fun coralUniforms(p: ShaderProgram, m: GpuModel, c: CoralPose?, defaultExtension: Float = 1f) {
        val b = m.bounds
        val height = (b[4] - b[1]).coerceAtLeast(1f)
        p.set3f("uBase", (b[0] + b[3]) * 0.5f, b[1], (b[2] + b[5]) * 0.5f)
        p.set1f("uHeight", height)
        p.set1f("uExtension", c?.extension ?: defaultExtension)
        p.set1f("uPhase", c?.swayPhase ?: 0f)
        p.set1f("uBleach", c?.bleach ?: 0f)
        p.set1f("uBrown", c?.brown ?: 0f)
        val flow = c?.flow ?: 8f
        val v = FloatArray(3)
        ParticleField.flowAt(interp.current?.flowGrid, (b[0] + b[3]) * 0.5f, b[4], (b[2] + b[5]) * 0.5f, 0f, v)
        val l = sqrt(v[0] * v[0] + v[2] * v[2])
        if (l > 1e-3f) p.set3f("uSwayDir", v[0] / l, 0f, v[2] / l) else p.set3f("uSwayDir", 0.95f, 0f, 0.3f)
        p.set1f("uSwayAmp", 0.08f * minOf(height, 25f) * (flow / 12f).coerceIn(0.15f, 1.5f))
        p.set3f("uSssColor", 0.9f, 0.55f, 0.45f)
    }

    private fun drawCoral(m: GpuModel, c: CoralPose?, progs: Programs, s: SimSnapshot?, polyps: Boolean) {
        val w = white ?: return
        for (part in m.parts) {
            val isPolyp = part.kind == MaterialKind.POLYP
            if (isPolyp != polyps) continue
            val p = when (part.kind) {
                MaterialKind.POLYP -> progs.polyp
                MaterialKind.CARD -> progs.card
                else -> progs.softCoral
            }
            p.use()
            p.setMat4("uModel", if (isPolyp) IDENTITY else part.node.world)
            part.material.apply(p, w, alphaTestOverride = part.node.float("alphaTest") ?: if (part.material.src.blended) 0.3f else null)
            coralUniforms(p, m, c)
            if (isPolyp) p.set1f("uSwayAmp", 0.25f * ((c?.flow ?: 8f) / 12f).coerceIn(0.2f, 1.5f))
            part.mesh.draw(); drawCalls++; triangles += part.mesh.triangles
        }
    }

    // ------------------------------------------------------------------ organisms

    private val tmpModel = FloatArray(16)

    /** pass 0: opaque parts; pass 1: fins (blended). */
    private fun drawOrganism(r: FishRig, progs: Programs, pass: Int) {
        if (!r.visible) return
        val path = registry.modelFor(r.speciesId, r.variant)
        val far = r.distanceTo(camera.eye[0], camera.eye[1], camera.eye[2]) > RenderQuality.LOD1_DISTANCE_CM
        var m: GpuModel? = null
        if (path != null && store.exists(path)) {
            val lod1 = ModelRegistry.lod1(path)
            m = if (far && store.exists(lod1)) store.model(lod1) ?: store.model(path) else store.model(path) ?: if (store.exists(lod1)) store.model(lod1) else null
            if (m == null) return // still loading
        }
        if (m == null) { if (pass == 0) drawPlaceholder(r, progs); return }
        val w = white ?: return
        for (part in m.parts) {
            val isFin = part.kind == MaterialKind.FIN_SHEET
            if ((pass == 1) != isFin) continue
            if (part.kind == MaterialKind.POLYP) {
                // feather-duster crown (cm model): one polyp at the organism
                val p = progs.polyp
                p.use()
                rigidModel(r, tmpModel)
                p.setMat4("uModel", tmpModel)
                part.material.apply(p, w, alphaTestOverride = 0.3f)
                p.set1f("uExtension", r.extension); p.set3f("uSwayDir", 0.95f, 0f, 0.3f); p.set1f("uSwayAmp", 0.4f)
                p.set1f("uBleach", 0f); p.set1f("uBrown", 0f)
                part.mesh.draw(); drawCalls++; triangles += part.mesh.triangles
                continue
            }
            val p = if (isFin) progs.fin else progs.fish
            p.use()
            fishUniforms(p, r, m.rigid)
            val partCode = when (part.part) { "fin" -> 1f; "eye" -> 2f; "leg", "antenna" -> 3f; else -> 0f }
            p.set1f("uPart", partCode)
            p.set4f("uShape", r.thin, partCode, r.eyeYaw, part.finKind)
            p.set3fv("uEyePivot", part.eyePivot)
            part.material.apply(p, w)
            val iri = m.glb.rootFloat("iridescence") ?: 0.1f
            p.set1f("uIridStrength", if (partCode == 0f) iri else 0f)
            p.set1f("uRoughness", m.glb.rootFloat("roughness")?.takeIf { partCode == 0f } ?: part.material.src.roughness)
            part.mesh.draw(); drawCalls++; triangles += part.mesh.triangles
        }
    }

    private fun drawPlaceholder(r: FishRig, progs: Programs) {
        val mesh = placeholder ?: return
        val p = progs.fish
        p.use()
        fishUniforms(p, r, rigid = false)
        p.set1f("uPart", 0f)
        p.set4f("uShape", r.thin, 0f, 0f, 0f)
        p.set3f("uEyePivot", 0.4f, 0.05f, 0f)
        white?.bind(0)
        val c = Placeholder.colorFor(r.speciesId)
        p.set4f("uBaseColor", c[0], c[1], c[2], 1f)
        p.set1f("uHasTexture", 0f)
        p.set1f("uRoughness", 0.5f)
        p.set1f("uIridStrength", 0f)
        mesh.draw(); drawCalls++; triangles += mesh.triangles
    }

    private fun rigidModel(r: FishRig, out: FloatArray) {
        M.identity().copyInto(out)
        out[12] = r.spine[0]; out[13] = r.spine[1]; out[14] = r.spine[2]
    }

    private fun fishUniforms(p: ShaderProgram, r: FishRig, rigid: Boolean) {
        p.set4fv("uSpine", r.spine, 16)
        p.set4fv("uFrame", r.frames, 16)
        p.set1f("uScale", r.scale)
        p.set4f("uSwim", r.phase, r.amp, r.waveLen, r.appendage)
        p.set4f("uFin", r.pectoral, r.finClamp, r.gill, r.gillRate)
        p.set1f("uDesat", r.desat)
        p.set1f("uIchSpots", r.ich)
        p.set1f("uEyeCloud", r.eyeCloud)
        p.set1f("uDead", if (r.alive) 0f else 1f)
        p.set1f("uRigid", if (rigid) 1f else 0f)
        if (rigid) { rigidModel(r, tmpModel); p.setMat4("uModel", tmpModel) } else p.setMat4("uModel", IDENTITY)
    }

    // ------------------------------------------------------------------ taps

    private fun handleTap(t: CameraInput.Tap, rigs: Collection<FishRig>) {
        val dir = FloatArray(3)
        camera.ray(t.x, t.y, width, height, dir)
        val o = camera.eye.copyOf()
        val l = listener
        when (t.kind) {
            CameraInput.TapKind.PICK -> {
                val id = Picker.pick(o, dir, rigs)
                main.post { l?.onPick(id) }
            }
            CameraInput.TapKind.GLASS -> {
                val tz = Picker.rayPlaneZ(o, dir, Camera.TANK[2]) ?: return
                val p = Vec3((o[0] + dir[0] * tz).toDouble().coerceIn(0.0, 121.9), (o[1] + dir[1] * tz).toDouble().coerceIn(3.0, 56.0), Camera.TANK[2].toDouble())
                main.post { l?.onTapGlass(p) }
            }
            CameraInput.TapKind.LONG_PRESS -> {
                val p = surfacePoint(o, dir) ?: return
                main.post { l?.onLongPress(p) }
            }
        }
    }

    /** First rock / sand / water-surface point along a ray, for food drops. */
    private fun surfacePoint(o: FloatArray, d: FloatArray): Vec3? {
        // enter the water box
        var t0 = 0f; var t1 = 1000f
        val lo = floatArrayOf(0f, Camera.SAND_Y, 0f); val hi = floatArrayOf(Camera.TANK[0], Camera.WATER_Y, Camera.TANK[2])
        for (k in 0 until 3) {
            if (kotlin.math.abs(d[k]) < 1e-6f) { if (o[k] < lo[k] || o[k] > hi[k]) return null; continue }
            var a = (lo[k] - o[k]) / d[k]; var b = (hi[k] - o[k]) / d[k]
            if (a > b) { val s = a; a = b; b = s }
            t0 = maxOf(t0, a); t1 = minOf(t1, b)
        }
        if (t0 > t1) return null
        val g = sdf
        val hitT = if (g != null) {
            val sx = o[0] + d[0] * t0; val sy = o[1] + d[1] * t0; val sz = o[2] + d[2] * t0
            t0 + Camera.marchClear(g, sx, sy, sz, d[0], d[1], d[2], t1 - t0, 0f)
        } else t1
        return Vec3((o[0] + d[0] * hitT).toDouble(), (o[1] + d[1] * hitT).toDouble(), (o[2] + d[2] * hitT).toDouble())
    }

    // ------------------------------------------------------------------ stats

    private fun publishStats(now: Long, organisms: Int, light: LightingState) {
        fpsFrames++
        lastFrameMs = ((System.nanoTime() - now) / 1e6f)
        if (fpsStartNs == 0L) fpsStartNs = now
        val span = now - fpsStartNs
        if (span >= 500_000_000L) {
            lastFps = fpsFrames * 1e9f / span
            fpsFrames = 0; fpsStartNs = now
            _stats.value = _stats.value.copy(
                fps = lastFps, frameMs = lastFrameMs, drawCalls = drawCalls, triangles = triangles, glErrors = Gl.errors.get(),
                modelsLoaded = store.loadedCount, modelsTotal = store.requestedCount, quality = quality, following = camera.followId,
                organisms = organisms, lightIntensity = light.intensity,
            )
        }
    }

    /** GL thread, on view detach. */
    fun shutdown() {
        store.shutdown()
    }

    companion object {
        const val TANK = "models/tank.glb"
        const val ROCK = "models/rockscape.glb"
        const val EQUIPMENT = "models/equipment.glb"
        const val ALGAE = "models/algae_tufts.glb"
        val SCENE_MODELS = listOf(TANK, ROCK, EQUIPMENT, ALGAE)
        val DEMO_CORALS = listOf(
            "models/kenya.glb", "models/gsp_neon.glb", "models/gsp_mat.glb", "models/zoa.glb", "models/clove.glb",
            "models/mushroom.glb", "models/macro_red.glb", "models/organ_pipe.glb",
        )
        const val CAUSTIC = "textures/caustic_512.png"
        const val IRID = "textures/irid_lut.png"
        const val ROOM_ENV = "textures/room_env.png"
        const val WATER_N1 = "textures/water_n1.png"
        const val WATER_N2 = "textures/water_n2.png"
        const val ROCK_NORMAL = "textures/rock_normal.png"
        const val ROCK_ROUGH_AO = "textures/rock_rough_ao.png"
        const val SAND_NORMAL = "textures/sand_normal.png"
        val SHARED_TEXTURES = listOf(CAUSTIC, IRID, ROOM_ENV, WATER_N1, WATER_N2, ROCK_NORMAL, ROCK_ROUGH_AO, SAND_NORMAL)
        val IDENTITY = M.identity()
    }
}
