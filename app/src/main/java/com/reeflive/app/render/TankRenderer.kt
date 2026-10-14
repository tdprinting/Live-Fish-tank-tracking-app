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
import com.reeflive.core.sim.AlgaeCover
import com.reeflive.core.sim.CritterKind
import com.reeflive.core.sim.CoralPose
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.TouchSurface
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
    /** Receives the camera matrices after every update, for Compose overlays (null = nobody listening). */
    @Volatile var projection: TankProjection? = null
    /** In-tank feedback bursts from the UI (dye plumes, bubbles, jets…), drained once per frame. */
    @Volatile var bursts: TankBursts? = null
    /** Watch-mode lamp look (render only; the simulation keeps its schedule). */
    @Volatile var viewLight: ViewLight = ViewLight.REAL
    /** The supply shelf beside the tank (Supplies mode, [SupplyShelfControl]); null = no shelf. */
    @Volatile var shelf: SupplyShelfControl? = null
    private val shelfScene = SupplyShelfScene()
    /** Submarine mode: ride inside the sub (first person) instead of chasing it. */
    @Volatile var subFirstPerson = false
    private val subScene = SubmarineScene()
    @Volatile private var requestedQuality = initialQuality
    @Volatile private var followRequest: String? = null
    @Volatile private var followDirty = false
    @Volatile private var resetRequested = false
    @Volatile private var cleaningRequest = false
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
    // dynamic sand bed + suspended clouds (SIMULATION §16, SandVisuals.kt)
    private var sandFx: SandFx? = null
    private var sandClouds: SandCloudRenderer? = null
    private var inkClouds: InkCloudRenderer? = null
    private var shafts: LightShafts? = null
    /** Real-time shadows from the fixtures (ShadowMap.kt); its depth pass runs before the scene, see [renderShadowMap]. */
    private var shadowMap: ShadowMap? = null
    /** Marine snow and the follow lens (camera / lighting work, kept apart from the scene drawing). */
    private var snow: MarineSnow? = null
    /** Bristleworms, amphipods, mysids and the other visible microfauna (core CritterField). */
    private val micro = MicroCritterVisuals()
    private val lens = LensState()
    private var postFailed = false
    private var placeholder: Mesh? = null
    private var white: Texture? = null
    private var flatNormal: Texture? = null
    private var neutralRough: Texture? = null
    private var zero: Texture? = null
    private var surfaceTex: Texture? = null
    private var surfaceDims = IntArray(2)
    private var lastSurfaceSnap: SimSnapshot? = null
    /** Front-glass film mask (GlassMask, R16F) while the Flipper has scraped it; white 1×1 is bound otherwise. */
    private var glassMaskTex: Texture? = null
    private var glassMaskData: FloatArray? = null
    private var glassMaskOn = false

    // ---- clean-glass mode (GL thread) ----
    private var cleaning = false
    /** Flipper pad centre on the front glass (tank cm), where it is drawn; starts at its modelled seat. */
    private val flipper = floatArrayOf(FLIPPER_REST_X, FLIPPER_REST_Y)
    private val strokeFrom = floatArrayOf(FLIPPER_REST_X, FLIPPER_REST_Y)
    private val pointer = FloatArray(2)
    private val flipperModel = FloatArray(16)
    private val shift = M.identity()
    private var store = AssetStore(assets, initialQuality)
    private val registry = ModelRegistry()
    private val interp = SnapshotInterpolator()
    /** Juvenile / adult / sex forms: stage textures and body shape (LifeStageVisuals, life_stages.md). */
    private val lifeStages = LifeStageVisuals(assets)
    private val stageBinding = LifeStageVisuals.Binding()
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

    /** Clean-glass mode on/off (any thread): drags slide the Flipper; the camera squares up to the front glass. */
    fun setCleaning(on: Boolean) {
        if (input.cleaning == on) return
        input.cleaning = on
        if (!on) input.scrapeTouch(0f, 0f, false)
        cleaningRequest = on
    }

    // ------------------------------------------------------------------ GLSurfaceView.Renderer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context: every GL object from a previous one is gone.
        store.onContextLost(quality)
        Texture.resetCaps()
        Gl.currentProgram = 0
        initGl()
        _stats.value = _stats.value.copy(glInfo = "${GLES30.glGetString(GLES30.GL_RENDERER)} / ${GLES30.glGetString(GLES30.GL_VERSION)}")
    }

    private fun initGl() {
        programs = Programs(source, quality)
        frame = FrameUniforms()
        particlesGl = ParticleRenderer()
        sandFx = SandFx(); sandClouds = SandCloudRenderer()
        inkClouds = InkCloudRenderer()
        shafts = LightShafts()
        shadowMap = ShadowMap(quality)
        white = Texture.solid(255, 255, 255)
        flatNormal = Texture.solid(128, 128, 255)
        neutralRough = Texture.solid(217, 255, 0)
        zero = Texture.floatR(1, 1).also { Texture.updateFloatR(it, floatArrayOf(0f)) }
        surfaceTex = null; surfaceDims = IntArray(2); lastSurfaceSnap = null
        glassMaskTex = null; glassMaskData = null; glassMaskOn = false
        val (v, i) = Placeholder.spindle()
        placeholder = Mesh.create(v, i)
        postFailed = false
        post = if (quality.bloom) createPost() else null
        snow = MarineSnow()
        // kick off the static scene and shared textures
        SCENE_MODELS.forEach { store.model(it) }
        SHARED_TEXTURES.forEach { store.texture(it) }
    }

    private fun releaseGl() {
        programs?.release(); programs = null
        frame?.release(); frame = null
        post?.release(); post = null
        particlesGl?.release(); particlesGl = null
        sandFx?.release(); sandFx = null; sandClouds?.release(); sandClouds = null
        inkClouds?.release(); inkClouds = null
        shafts?.release(); shafts = null
        shadowMap?.release(); shadowMap = null
        snow?.release(); snow = null
        placeholder?.release(); placeholder = null
        subScene.release()
        listOf(white, flatNormal, neutralRough, zero, surfaceTex, glassMaskTex).forEach { it?.release() }
        white = null; flatNormal = null; neutralRough = null; zero = null; surfaceTex = null; glassMaskTex = null; glassMaskData = null
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
        val time = (((now - startNs) / 1e9) % 4096.0).toFloat()
        drawCalls = 0; triangles = 0

        applyRequests()
        applyShelf(dt)
        store.pump(2)

        // 1. snapshot + rigs
        val snap = snapshotProvider?.invoke() ?: if (demoWhenEmpty) DemoSnapshots.at(time.toDouble()) else null
        interp.push(snap, now)
        val rigs = interp.update(now)
        val cur = interp.current
        val light = LightingState.from(viewLight.channels ?: cur?.lights ?: DemoSnapshots.DAY)

        // 2. camera (+ input); in Submarine mode the camera rides with the sub
        val fp = subFirstPerson
        camera.rideOn = subScene.update(interp.previous?.submarine, cur?.submarine, interp.alpha(now), fp, sdf)
        if (camera.rideOn) { subScene.eye.copyInto(camera.rideEye); subScene.aim.copyInto(camera.rideAim) }
        val taps = input.drain(camera, width, height)
        camera.update(dt, width, height, sdf, { id -> interp.rigs[id] }) { lost -> main.post { listener?.onFollowLost(lost) } }
        projection?.publish(camera.viewProj, camera.proj, width, height)
        for (t in taps) handleTap(t, rigs)
        handleHeld(now, cur)
        updateLens(dt)
        if (cleaning) dragFlipper(dt)
        micro.update(cur, sdf, dt, camera, quality, if (quality == RenderQuality.HIGH) MicroCritterVisuals.MICRO_TRIS_HIGH else MicroCritterVisuals.MICRO_TRIS_MEDIUM) { k ->
            store.model(k.model)?.parts?.sumOf { it.mesh.triangles } ?: 0
        }

        // 3. per-frame uniforms + shared textures
        updateSurface(cur)
        updateGlassMask(cur)
        sandFx?.update(cur, now)
        val fu = frame ?: return
        val progs = programs ?: return
        fu.setEffects(quality)
        fu.setCasters(rigs, quality.shadowCasters, camera.eye[0], camera.eye[1], camera.eye[2])
        shadowMap?.fill(fu.data, FrameUniforms.SHADOW_OFFSET)
        fu.fill(camera, time, light, quality, cur?.algaeBloom ?: 0f, cur?.flowStrength ?: 1f, surfaceTex != null, surfaceDims[0], surfaceDims[1], cur?.algae ?: AlgaeCover.EMPTY)
        bindShared()
        shadowMap?.let { renderShadowMap(it, fu, progs, rigs, cur) }
        particles.ambientCount = quality.particles
        bursts?.drain { particles.burst(it, cur) }
        particles.step(dt, time, cur, viewProjM = camera.viewProj)

        // 4. draw
        val usePost = post?.ok == true && (quality.bloom || lens.amount > 0f)
        if (usePost) post!!.begin() else { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); Gl.check("bind default"); GLES30.glViewport(0, 0, width, height); Gl.check("viewport") }
        GLES30.glClearColor(0.012f, 0.016f, 0.024f, 1f); Gl.check("glClearColor")
        GLES30.glDepthMask(true) // the post path leaves the depth mask off, which would make glClear skip depth
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT); Gl.check("glClear")

        val tank = store.model(TANK); val rock = store.model(ROCK); val equipment = store.model(EQUIPMENT); val algae = store.model(ALGAE)
        val details = store.model(DETAILS)
        // opaque: back glass, sand, rock, equipment, static reef details (coralline layer, encrusting, mag cleaner)
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.NONE, cull = false)
        tank?.let { drawStatic(it, progs, light, opaque = true) }
        rock?.let { drawStatic(it, progs, light, opaque = true) }
        equipment?.let { drawStatic(it, progs, light, opaque = true) }
        details?.let { drawStatic(it, progs, light, opaque = true) }
        drawShelf(progs)
        // corals: soft bodies then polyps
        val corals = coralModels(cur)
        updateWake(rigs, dt)
        for ((m, pose) in corals) drawCoral(m, pose, progs, cur, polyps = false)
        for ((m, pose) in corals) drawCoral(m, pose, progs, cur, polyps = true)
        // fish bodies, inverts
        for (r in rigs) drawOrganism(r, progs, pass = 0)
        for (r in micro.visible) drawOrganism(r, progs, pass = 0)
        if (subScene.visible) white?.let { drawCalls += subScene.draw(progs.generic, it, fp, time) }
        // alpha-tested/blended fins and algae cards (depth write on, sorted far → near)
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.ALPHA, cull = false)
        algae?.let { drawAlgae(it, progs) }
        // blended details (the back-glass algae film) right after the cards, before particles and the water
        Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ALPHA, cull = false)
        details?.let { drawStatic(it, progs, light, opaque = false) }
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.ALPHA, cull = false)
        val sorted = rigs.sortedByDescending { it.distanceTo(camera.eye[0], camera.eye[1], camera.eye[2]) }
        for (r in sorted) drawOrganism(r, progs, pass = 1)
        for (r in micro.visible) drawOrganism(r, progs, pass = 1)
        // transparent: back glass, then water (first when the eye is under the surface, so the underside never
        // erases the particles and shafts in front of it), shafts, particles, water (eye above), equipment, glass
        Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ALPHA, cull = false)
        tank?.let { drawStatic(it, progs, light, opaque = false, only = MaterialKind.GENERIC) }
        val waterFirst = camera.eye[1] < Camera.WATER_Y
        if (waterFirst) tank?.let { drawStatic(it, progs, light, opaque = false, only = MaterialKind.WATER) }
        if (quality.shafts && light.intensity > 0.03f) {
            Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ADD, cull = false)
            shafts?.let { it.draw(progs.shafts); drawCalls++; triangles += it.triangles }
        }
        Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ALPHA, cull = false)
        particlesGl?.let { drawCalls += it.draw(progs.particles, particles, height) }
        snow?.let { drawCalls += it.draw(progs.snow, quality.snow, height) }
        sandClouds?.let { drawCalls += it.draw(progs.particles, cur?.sandClouds, height) }
        inkClouds?.let { drawCalls += it.draw(progs.particles, rigs, height) }
        white?.let { w ->
            if (!subScene.visible) drawCalls += subScene.draw(progs.generic, w, fp, time)   // the ghost sub, see-through
            Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ADD, cull = false)
            drawCalls += subScene.drawBeam(progs.generic, w)
            Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ALPHA, cull = false)
        }
        if (!waterFirst) tank?.let { drawStatic(it, progs, light, opaque = false, only = MaterialKind.WATER) }
        equipment?.let { drawStatic(it, progs, light, opaque = false) }
        Gl.state(depthTest = true, depthWrite = false, blend = Gl.Blend.ALPHA, cull = false)
        // glass planes coincide with the sand skirts and rock edges: pull them a hair toward the eye to stop z-fighting
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL); GLES30.glPolygonOffset(-1f, -2f)
        tank?.let { drawStatic(it, progs, light, opaque = false, only = MaterialKind.GLASS) }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        Gl.state(depthTest = true, depthWrite = true, blend = Gl.Blend.NONE, cull = false)

        if (usePost) drawCalls += post!!.end(lens.amount, lens.focus, quality.lensBlurPasses)
        publishStats(now, rigs.size, light)
    }

    // ------------------------------------------------------------------ follow lens

    private fun createPost(): PostFx? =
        runCatching { PostFx(source, quality.msaa, quality.bloom).also { it.resize(width, height) } }
            .onFailure { Log.e(Gl.TAG, "PostFx", it); postFailed = true }.getOrNull()

    /** Fades the follow lens in/out and tracks the fish on screen; on Medium the post chain is created on first use. */
    private fun updateLens(dt: Float) {
        val fid = camera.followId
        val rig = if (fid != null) interp.rigs[fid] else null
        lens.update(dt, quality.lens && !cleaning && fid != null, rig, camera)
        if (lens.amount > 0f && post == null && quality.lens && !postFailed) post = createPost()
    }

    // ------------------------------------------------------------------ requests

    private fun applyRequests() {
        if (followDirty) { followDirty = false; camera.follow(followRequest) }
        if (resetRequested) { resetRequested = false; camera.reset() }
        val clean = cleaningRequest
        if (clean != cleaning) {
            cleaning = clean
            if (clean) camera.faceGlass() else camera.reset()
            strokeFrom[0] = flipper[0]; strokeFrom[1] = flipper[1]
        }
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
        (glassMaskTex?.takeIf { glassMaskOn } ?: w).bind(GLASS_MASK_UNIT)
        shadowMap?.bind()
    }

    /** Uploads the front-glass film mask when the snapshot carries a new one (the core shares an array per version). */
    private fun updateGlassMask(s: SimSnapshot?) {
        val mask = s?.algae?.glassMask
        if (mask == null || mask.size < GLASS_MASK_W * GLASS_MASK_H) { glassMaskOn = false; return }
        glassMaskOn = true
        if (mask === glassMaskData && glassMaskTex != null) return
        val t = glassMaskTex ?: Texture.floatR(GLASS_MASK_W, GLASS_MASK_H).also { glassMaskTex = it }
        Texture.updateFloatR(t, mask)
        glassMaskData = mask
    }

    // ------------------------------------------------------------------ clean-glass mode

    /**
     * The Flipper glides after the finger over the front glass (ray ∩ glass plane, clamped so the pad stays on the
     * wetted pane) and every [GlassStroke.MIN_CM] of travel is reported to the listener as a stroke to scrape.
     */
    private fun dragFlipper(dt: Float) {
        if (!input.scrapePointer(pointer)) {
            // finger lifted: report the last bit of travel
            if (strokeFrom[0] != flipper[0] || strokeFrom[1] != flipper[1]) reportStroke()
            return
        }
        val dir = FloatArray(3)
        camera.ray(pointer[0], pointer[1], width, height, dir)
        val t = Picker.rayPlaneZ(camera.eye, dir, Camera.TANK[2]) ?: return
        val hx = (camera.eye[0] + dir[0] * t).coerceIn(FLIPPER_HALF_W + 0.2f, Camera.TANK[0] - FLIPPER_HALF_W - 0.2f)
        val hy = (camera.eye[1] + dir[1] * t).coerceIn(Camera.SAND_Y + FLIPPER_HALF_H + 0.2f, Camera.WATER_Y - FLIPPER_HALF_H + 1f)
        // a magnet pad slides, it does not teleport: quick glide toward the finger (scrapes along the way)
        val a = 1f - kotlin.math.exp(-FLIPPER_FOLLOW * dt)
        flipper[0] += (hx - flipper[0]) * a
        flipper[1] += (hy - flipper[1]) * a
        val dx = flipper[0] - strokeFrom[0]; val dy = flipper[1] - strokeFrom[1]
        if (dx * dx + dy * dy >= GlassStroke.MIN_CM * GlassStroke.MIN_CM) reportStroke()
    }

    private fun reportStroke() {
        val s = GlassStroke(strokeFrom[0], strokeFrom[1], flipper[0], flipper[1])
        strokeFrom[0] = flipper[0]; strokeFrom[1] = flipper[1]
        val l = listener
        main.post { l?.onScrape(s) }
    }

    /** The Flipper node's model matrix: its modelled pose shifted to the current pad position on the glass. */
    private fun flipperMatrix(world: FloatArray): FloatArray {
        shift[12] = flipper[0] - FLIPPER_REST_X; shift[13] = flipper[1] - FLIPPER_REST_Y
        return M.mul(shift, world, flipperModel)
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
            p.setMat4("uModel", if (part.node.name == FLIPPER_NODE) flipperMatrix(part.node.world) else part.node.world)
            part.material.apply(p, w)
            p.set1f("uLedDies", if (part.kind == MaterialKind.EMITTER) 1f else 0f) // AI Prime LED cluster (generic.frag)
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
                    sandFx?.bind(p)
                }
                // soft bodies in a static scene model (reef_details.glb): coral shading without sway or retraction
                MaterialKind.SOFT_CORAL -> staticCoralUniforms(p, part)
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
            part.material.apply(p, w, alphaTestOverride = part.alphaTest ?: 0.4f)
            // full extension: the shader scales every vertex toward the model's base by uExtension, and this model
            // spans the whole tank (0.3 pulled tufts up to ~15 cm off the rock)
            coralUniforms(p, m, null, 1f)
            part.mesh.draw(); drawCalls++; triangles += part.mesh.triangles
        }
    }

    /** Soft-coral program uniforms for a static part: anchored at its own centre, no sway, fully open. */
    private fun staticCoralUniforms(p: ShaderProgram, part: DrawPart) {
        val c = part.center
        p.set3f("uBase", c[0], c[1], c[2])
        p.set1f("uHeight", 1f)
        p.set1f("uExtension", 1f)
        p.set1f("uPhase", 0f)
        p.set1f("uBleach", 0f)
        p.set1f("uBrown", 0f)
        p.set3f("uSwayDir", 1f, 0f, 0f)
        p.set1f("uSwayAmp", 0f)
        p.set1f("uSwayMode", 0f)
        p.set4f("uWake", 0f, 0f, 0f, 0f)
        p.set3f("uSssColor", 0.9f, 0.55f, 0.45f)
    }

    // ------------------------------------------------------------------ shadow map (ShadowMap.kt)

    /**
     * Depth pass from the fixtures into [sm]: rock, in-water equipment and opaque reef details, coral bodies, and on
     * High the fish (their own skinning, LOD1 when shipped). The casters' vertex shaders run unchanged while the Frame
     * UBO's `uViewProj` holds [ShadowMap.matrix]; the camera matrix is restored afterwards. Self-contained: it binds
     * its own target and leaves the scene target / viewport to the normal frame code that follows.
     */
    private fun renderShadowMap(sm: ShadowMap, fu: FrameUniforms, progs: Programs, rigs: Collection<FishRig>, cur: SimSnapshot?) {
        val signature = store.loadedCount.toLong() * 1_000_003L + (cur?.corals?.size ?: 0)
        if (!sm.needsUpdate(signature)) return
        fu.overrideViewProj(sm.matrix)
        // High: rock, equipment and still details live in a cached layer, redrawn only when the scene changes
        if (sm.cachesStatic) {
            if (sm.staticStale(signature)) { sm.beginStatic(); drawStaticShadowCasters(progs) }
            sm.begin()
        } else { sm.begin(); drawStaticShadowCasters(progs) }
        // the magnet cleaner moves with the finger
        val p = progs.shadow
        p.use()
        store.model(EQUIPMENT)?.parts?.forEach { part ->
            if (part.node.name == FLIPPER_NODE && (part.kind == MaterialKind.ROCK || part.kind == MaterialKind.GENERIC) && castsShadow(part)) {
                p.setMat4("uModel", flipperMatrix(part.node.world)); part.mesh.draw(); drawCalls++
            }
        }
        val pc = progs.shadowCoral
        pc.use()
        for ((m, c) in coralModels(cur)) {
            val copy = c != null && c.isCopy
            if (c != null && copy) { coralShift[12] = c.offsetX; coralShift[13] = c.offsetY; coralShift[14] = c.offsetZ }
            for (part in m.parts) {
                if (part.kind != MaterialKind.SOFT_CORAL || !castsShadow(part)) continue
                if (c != null && copy && !nearHome(part, c)) continue
                pc.setMat4("uModel", if (copy) M.mul(coralShift, part.node.world, coralModel) else part.node.world)
                coralUniforms(pc, m, c)
                part.mesh.draw(); drawCalls++
            }
        }
        if (sm.dynamic) {
            val pf = progs.shadowFish
            pf.use()
            for (r in rigs) {
                if (!r.visible) continue
                val path = CritterKind.of(r.speciesId)?.model ?: registry.modelFor(r.speciesId, r.variant) ?: continue
                val lod1 = ModelRegistry.lod1(path)
                val m = (if (store.exists(lod1)) store.model(lod1) else null) ?: (if (store.exists(path)) store.model(path) else null) ?: continue
                fishUniforms(pf, r, m.rigid, m.limbPivots)
                for (part in m.parts) {
                    if (part.kind != MaterialKind.FISH_SKIN) continue
                    val code = when (part.part) { "eye" -> 2f; "leg", "antenna" -> 3f; else -> 0f }
                    pf.set1f("uPart", code)
                    pf.set4f("uShape", r.thin, code, r.eyeYaw, part.finKind)
                    pf.set3fv("uEyePivot", part.eyePivot)
                    part.mesh.draw(); drawCalls++
                }
            }
        }
        sm.end()
        fu.overrideViewProj(null)
        sm.bind()
    }

    /** Shadow casters that never move: rock, equipment (bar the magnet cleaner) and the still reef details. */
    private fun drawStaticShadowCasters(progs: Programs) {
        val p = progs.shadow
        p.use()
        for (key in SHADOW_STATIC) {
            val m = store.model(key) ?: continue
            for (part in m.parts) {
                if ((part.kind != MaterialKind.ROCK && part.kind != MaterialKind.GENERIC) || !castsShadow(part) || part.node.name == FLIPPER_NODE) continue
                p.setMat4("uModel", part.node.world)
                part.mesh.draw(); drawCalls++
            }
        }
        val pc = progs.shadowCoral
        pc.use()
        store.model(DETAILS)?.let { m ->
            for (part in m.parts) {
                if (part.kind != MaterialKind.SOFT_CORAL || !castsShadow(part)) continue
                pc.setMat4("uModel", part.node.world)
                staticCoralUniforms(pc, part)
                part.mesh.draw(); drawCalls++
            }
        }
    }

    private fun castsShadow(part: DrawPart): Boolean =
        !part.material.src.blended && part.material.alphaCutoff <= 0f && part.center[1] < ShadowMap.CASTER_MAX_Y

    // ------------------------------------------------------------------ corals

    private var coralsFor: SimSnapshot? = null
    private var coralsLoaded = -1
    private var coralsCache: List<Pair<GpuModel, CoralPose?>> = emptyList()

    /** [buildCoralModels] for this snapshot, rebuilt only when the snapshot or the set of loaded models changes. */
    private fun coralModels(s: SimSnapshot?): List<Pair<GpuModel, CoralPose?>> {
        if (s !== coralsFor || store.loadedCount != coralsLoaded || s == null) {
            coralsCache = buildCoralModels(s); coralsFor = s; coralsLoaded = store.loadedCount
        }
        return coralsCache
    }

    private fun buildCoralModels(s: SimSnapshot?): List<Pair<GpuModel, CoralPose?>> {
        val out = ArrayList<Pair<GpuModel, CoralPose?>>()
        if (s == null || s.organisms.firstOrNull()?.id?.startsWith("demo_") == true && s.corals.isEmpty()) {
            for (path in DEMO_CORALS) store.model(path)?.let { out += it to null }
            return out
        }
        val used = HashSet<String>()
        for (c in s.corals) {
            val paths = registry.coralModels(c.speciesId)
            if (paths.isNotEmpty() && paths.none { store.exists(it) }) {
                // own GLB not shipped yet (hairy mushroom): borrow a stand-in model, moved to this colony's spot
                val fb = paths.firstNotNullOfOrNull { ModelRegistry.FALLBACK_MODELS[it] }?.let { store.model(it) } ?: continue
                val home = fb.parts.firstOrNull()?.center ?: continue
                var ox = c.homeX + c.offsetX - home[0]
                val oy = c.homeY + c.offsetY - home[1]
                val oz = c.homeZ + c.offsetZ - home[2]
                if (ox == 0f && oy == 0f && oz == 0f) ox = 1e-3f
                out += fb to c.copy(offsetX = ox, offsetY = oy, offsetZ = oz, homeX = home[0], homeY = home[1], homeZ = home[2])
                continue
            }
            val candidates = paths.mapNotNull { store.model(it) }
            val anchored = candidates.firstOrNull { it.rootAnchor != null && it.rootAnchor == c.anchor }
            if (c.isCopy) {
                // sandbox copy (Command.AddMany): the anchored model again, moved to the new spot
                val copy = anchored ?: candidates.firstOrNull() ?: continue
                out += copy to c
                continue
            }
            val m = anchored ?: candidates.firstOrNull { it.path !in used } ?: continue
            if (used.add(m.path)) out += m to c
        }
        return out
    }

    /** Translation for the coral copy being drawn, and scratch for its part matrices. */
    private val coralShift = M.identity()
    private val coralModel = FloatArray(16)

    /** A copy keeps only the clump around its home anchor (zoa and mushroom models hold two clumps). */
    private fun nearHome(part: DrawPart, c: CoralPose): Boolean {
        val dx = part.center[0] - c.homeX; val dy = part.center[1] - c.homeY; val dz = part.center[2] - c.homeZ
        return dx * dx + dy * dy + dz * dz < COPY_RADIUS_CM * COPY_RADIUS_CM
    }

    private val flowScratch = FloatArray(3)
    private val gustScratch = FloatArray(3)

    private fun coralUniforms(p: ShaderProgram, m: GpuModel, c: CoralPose?, defaultExtension: Float = 1f) {
        // branching trees (Kenya, extras.swayMode = 1): smaller, measured sway in treeSway (common.glsl) + fish ripple,
        // about the real colony base (bodyBounds: m.bounds also spans the polyp prototype at the origin)
        val tree = m.isTree
        val b = if (tree) m.bodyBounds else m.bounds
        val height = (b[4] - b[1]).coerceAtLeast(1f)
        val ox = c?.offsetX ?: 0f; val oy = c?.offsetY ?: 0f; val oz = c?.offsetZ ?: 0f
        // a copy grows from its own spot (the moved home anchor), not from the centre of the whole model
        if (c != null && c.isCopy) p.set3f("uBase", c.homeX + ox, c.homeY + oy, c.homeZ + oz)
        else p.set3f("uBase", (b[0] + b[3]) * 0.5f, b[1], (b[2] + b[5]) * 0.5f)
        p.set1f("uHeight", height)
        p.set1f("uExtension", c?.extension ?: defaultExtension)
        p.set1f("uPhase", c?.swayPhase ?: 0f)
        p.set1f("uBleach", c?.bleach ?: 0f)
        p.set1f("uBrown", c?.brown ?: 0f)
        val flow = c?.flow ?: 8f
        val v = flowScratch
        ParticleField.flowAt(interp.current?.flowGrid, (b[0] + b[3]) * 0.5f + ox, b[4] + oy, (b[2] + b[5]) * 0.5f + oz, 0f, v)
        // a touch puff passing the colony leans it over and makes it sway harder
        var puff = 0f
        val gusts = interp.current?.gusts
        if (gusts != null && gusts.isNotEmpty()) {
            val g = gustScratch
            BurstField.gustAt(gusts, (b[0] + b[3]) * 0.5f + ox, (b[1] + b[4]) * 0.5f + oy, (b[2] + b[5]) * 0.5f + oz, g)
            v[0] += g[0] * 3f; v[2] += g[2] * 3f
            puff = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        }
        val l = sqrt(v[0] * v[0] + v[2] * v[2])
        if (l > 1e-3f) p.set3f("uSwayDir", v[0] / l, 0f, v[2] / l) else p.set3f("uSwayDir", 0.95f, 0f, 0.3f)
        val amp = (if (tree) TREE_SWAY else 0.08f) * minOf(height, 25f) * ((flow + puff) / 12f).coerceIn(0.15f, 2.2f)
        p.set1f("uSwayAmp", amp)
        p.set1f("uBodySwayAmp", amp)
        p.set1f("uSwayMode", if (tree) 1f else 0f)
        if (tree) setWake(p, b, ox, oy, oz) else p.set4f("uWake", 0f, 0f, 0f, 0f)
        p.set3f("uSssColor", 0.9f, 0.55f, 0.45f)
    }

    /** Head position and speed (cm/s) of every organism this frame, for the tree-coral fish ripple. */
    private val wakeHeads = HashMap<String, FloatArray>()
    private val wakeSeen = HashSet<String>()

    private fun updateWake(rigs: Collection<FishRig>, dt: Float) {
        wakeSeen.clear()
        for (r in rigs) {
            val x = r.headX(); val y = r.headY(); val z = r.headZ()
            val h = wakeHeads.getOrPut(r.id) { floatArrayOf(x, y, z, 0f, 0f) }
            val dx = x - h[0]; val dy = y - h[1]; val dz = z - h[2]
            val speed = if (dt > 1e-4f) sqrt(dx * dx + dy * dy + dz * dz) / dt else 0f
            h[0] = x; h[1] = y; h[2] = z
            h[3] = h[3] + (speed - h[3]) * 0.3f                       // smoothed: snapshot interpolation is steppy
            h[4] = if (r.alive && r.visible) 1f else 0f
            wakeSeen.add(r.id)
        }
        wakeHeads.keys.retainAll(wakeSeen)
    }

    /**
     * uWake for a tree coral: the nearest organism head within [WAKE_RANGE_CM] of the colony's bounds (moved by the
     * copy offset), strength = proximity × speed (a fish swimming past ripples the branches it brushes; one resting
     * in the crown barely does).
     */
    private fun setWake(p: ShaderProgram, b: FloatArray, ox: Float, oy: Float, oz: Float) {
        var best = 0f; var bx = 0f; var by = 0f; var bz = 0f
        for (h in wakeHeads.values) {
            if (h[4] < 0.5f) continue
            val dx = maxOf(b[0] + ox - h[0], 0f, h[0] - (b[3] + ox))
            val dy = maxOf(b[1] + oy - h[1], 0f, h[1] - (b[4] + oy))
            val dz = maxOf(b[2] + oz - h[2], 0f, h[2] - (b[5] + oz))
            val d = sqrt(dx * dx + dy * dy + dz * dz)
            if (d >= WAKE_RANGE_CM) continue
            val s = (1f - d / WAKE_RANGE_CM) * (0.15f + 0.85f * (h[3] / WAKE_SPEED_CM_S).coerceIn(0f, 1f))
            if (s > best) { best = s; bx = h[0]; by = h[1]; bz = h[2] }
        }
        p.set4f("uWake", bx, by, bz, best)
    }

    private fun drawCoral(m: GpuModel, c: CoralPose?, progs: Programs, s: SimSnapshot?, polyps: Boolean) {
        val w = white ?: return
        val copy = c != null && c.isCopy
        if (c != null && copy) { coralShift[12] = c.offsetX; coralShift[13] = c.offsetY; coralShift[14] = c.offsetZ }
        for (part in m.parts) {
            val isPolyp = part.kind == MaterialKind.POLYP
            if (isPolyp != polyps) continue
            if (c != null && copy && !nearHome(part, c)) continue
            val p = when (part.kind) {
                MaterialKind.POLYP -> progs.polyp
                MaterialKind.CARD -> progs.card
                else -> progs.softCoral
            }
            p.use()
            p.setMat4("uModel", when {
                !copy -> if (isPolyp) IDENTITY else part.node.world
                isPolyp -> coralShift
                else -> M.mul(coralShift, part.node.world, coralModel)
            })
            part.material.apply(p, w, alphaTestOverride = part.alphaTest ?: if (part.material.src.blended) 0.3f else null)
            CORAL_TINT[part.material.src.name]?.let { t ->
                val bc = part.material.src.baseColor
                p.set4f("uBaseColor", bc[0] * t[0], bc[1] * t[1], bc[2] * t[2], bc.getOrElse(3) { 1f })
            }
            coralUniforms(p, m, c)
            if (isPolyp) p.set1f("uSwayAmp", (if (m.isTree) TREE_FLUTTER else 0.25f) * ((c?.flow ?: 8f) / 12f).coerceIn(0.2f, 1.5f))
            part.mesh.draw(); drawCalls++; triangles += part.mesh.triangles
        }
    }

    // ------------------------------------------------------------------ organisms

    private val tmpModel = FloatArray(16)

    /** pass 0: opaque parts; pass 1: fins (blended). */
    private fun drawOrganism(r: FishRig, progs: Programs, pass: Int) {
        stageBinding.clear()
        if (!r.visible) return
        val path = CritterKind.of(r.speciesId)?.model ?: registry.modelFor(r.speciesId, r.variant)
        val far = r.distanceTo(camera.eye[0], camera.eye[1], camera.eye[2]) > RenderQuality.LOD1_DISTANCE_CM
        var m: GpuModel? = null
        if (path != null && store.exists(path)) {
            val lod1 = ModelRegistry.lod1(path)
            m = if (far && store.exists(lod1)) store.model(lod1) ?: store.model(path) else store.model(path) ?: if (store.exists(lod1)) store.model(lod1) else null
            if (m == null) {
                if (store.hasFailed(path) && pass == 0) drawPlaceholder(r, progs)
                return // still loading
            }
        }
        if (m == null) { if (pass == 0) drawPlaceholder(r, progs); return }
        val w = white ?: return
        // life stage: body texture forms A/B and their mix, body depth/width scale (core LifeForm via LifeFormChannel)
        val sb = stageBinding
        if (path != null && CritterKind.of(r.speciesId) == null) {
            lifeStages.resolve(r.speciesId, path, r.scale, com.reeflive.core.sim.LifeFormChannel.of(interp.current)[r.id], sb)
        } else sb.clear()
        var uniformsSet: ShaderProgram? = null
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
                p.set1f("uSwayMode", 0f)
                p.set1f("uBleach", 0f); p.set1f("uBrown", 0f)
                part.mesh.draw(); drawCalls++; triangles += part.mesh.triangles
                continue
            }
            val p = if (isFin) progs.fin else progs.fish
            p.use()
            // per-organism uniforms persist in the program: upload them once, not again for every part
            if (p !== uniformsSet) { fishUniforms(p, r, m.rigid, m.limbPivots); uniformsSet = p }
            val partCode = when (part.part) { "fin" -> 1f; "eye" -> 2f; "leg", "antenna" -> 3f; else -> 0f }
            p.set1f("uPart", partCode)
            p.set4f("uShape", r.thin, partCode, r.eyeYaw, part.finKind)
            p.set3fv("uEyePivot", part.eyePivot)
            part.material.apply(p, w)
            bindStage(p, w, when { isFin -> sb.fin; part.part == "body" -> sb.body; else -> null })
            p.set1f("uIridStrength", if (partCode == 0f) m.iridescence else 0f)
            p.set1f("uRoughness", m.roughness?.takeIf { partCode == 0f } ?: part.material.src.roughness)
            part.mesh.draw(); drawCalls++; triangles += part.mesh.triangles
        }
    }

    /**
     * Stage texture blend for a fish body or fin part (LifeStageVisuals): form A replaces the GLB albedo on unit 0 once loaded
     * (none = the GLB's own texture), form B goes to [LifeStageVisuals.UNIT] mixed by `uStageMix`. Textures load
     * asynchronously; until then the GLB albedo shows. Other parts get `uStageMix` 0.
     */
    private fun bindStage(p: ShaderProgram, w: Texture, b: LifeStageVisuals.TexPair?) {
        p.set1i("uAlbedoStage", LifeStageVisuals.UNIT)
        val ta = b?.texA?.let { store.texture(it) }
        val tb = b?.texB?.let { store.texture(it) }
        val mix = b?.mix ?: 0f
        if (ta != null) { ta.bind(0); p.set1f("uHasTexture", 1f) }
        // texA null = the GLB's own body albedo stays on unit 0 (the model's base form)
        if (tb != null && mix > 0.001f) { tb.bind(LifeStageVisuals.UNIT); p.set1f("uStageMix", mix) }
        else { w.bind(LifeStageVisuals.UNIT); p.set1f("uStageMix", 0f) }
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

    private fun fishUniforms(p: ShaderProgram, r: FishRig, rigid: Boolean, limbPivots: FloatArray? = null) {
        p.set4fv("uSpine", r.spine, 16)
        p.set4fv("uFrame", r.frames, 16)
        p.set1f("uScale", r.scale)
        p.set4f("uSwim", r.phase, r.amp, r.waveLen, r.appendage)
        p.set4f("uFin", r.pectoral, r.finClamp, r.gill, r.gillRate)
        // x: pectoral stroke phase (cycles, wrapped to [0,1) so float precision holds), y: stroke sweep, z: C-start bend, w: mode
        p.set4f("uMotion", r.pecPhase - kotlin.math.floor(r.pecPhase), r.pecAmp, r.bend, r.motionMode.toFloat())
        // shrimp picking / feeding strokes (mouth)
        p.set1f("uPick", r.mouth)
        p.set1f("uDesat", r.desat)
        p.set1f("uIchSpots", r.ich)
        p.set1f("uEyeCloud", r.eyeCloud)
        p.set1f("uDead", if (r.alive) 0f else 1f)
        // life-stage body proportions (depth, width offsets from the GLB; 0 = as built), set for every fish draw
        p.set3f("uStageShape", stageBinding.depth, stageBinding.width, 0f)
        p.set1f("uRigid", if (rigid) 1f else 0f)
        if (rigid) { rigidModel(r, tmpModel); p.setMat4("uModel", tmpModel) } else p.setMat4("uModel", IDENTITY)
        // limb pose from the core LimbRig (SIMULATION §19): every entry the sim sent goes to uLimb[]; shrimp send one
        // (x stride phase, y stepping, z rock / claw signal, w grooming), crawlers ten. Crawler models with pivots are
        // posed about them (uLimbOn); everything else reads uLimb[0] only, zeroed when the pose carries no limbs.
        if (r.limbCount > 0) p.set4fv("uLimb", r.limbs, r.limbCount) else p.set4f("uLimb", 0f, 0f, 0f, 0f)
        // bobtail squid skin (motion mode 4, core SquidRig limbs[2]): chromatophore expansion, passing cloud, glow, 1 + cloud phase
        if (r.motionMode == 4 && r.limbCount >= 3) p.set4f("uChroma", r.limbs[9], r.limbs[10], r.limbs[11], 1f + r.limbs[8])
        else p.set4f("uChroma", 0f, 0f, 0f, 0f)
        val limbsOn = !rigid && limbPivots != null && r.limbCount >= 10
        p.set1f("uLimbOn", if (limbsOn) 1f else 0f)
        if (limbsOn) p.set4fv("uLimbPivot", limbPivots!!, 10)
    }

    // ------------------------------------------------------------------ supply shelf (SupplyShelf.kt)

    /** Open / row requests from the UI, the shelf model (loaded once a shelf is attached) and the props' lift. */
    private fun applyShelf(dt: Float) {
        val c = shelf
        shelfScene.apply(c, camera)
        input.instantTaps = camera.shelfOn
        if (c != null) store.model(SupplyShelfLayout.MODEL)
        // a tap on a prop's name tag (UI) picks it here, so it lifts like a tapped prop and reports its top point
        val req = c?.takePickRequest()
        if (c != null && req != null && camera.shelfOn) {
            val m = store.model(SupplyShelfLayout.MODEL)
            val cb = c.onPick
            if (m != null && cb != null) {
                shelfScene.bind(m)
                val top = FloatArray(3)
                if (shelfScene.pickId(req, top)) main.post { cb(req, top[0], top[1], top[2]) }
            }
        }
        shelfScene.update(dt)
    }

    /** The shelf stands outside the default view: drawn only while the camera is there or on its way. */
    private fun drawShelf(progs: Programs) {
        if (camera.shelfMix <= 0f) return
        val m = store.model(SupplyShelfLayout.MODEL) ?: return
        val w = white ?: return
        drawCalls += shelfScene.draw(m, progs.shelf, w, shelf?.locked ?: emptySet())
        triangles += m.triangles
    }

    /** In Supplies mode a tap (or double tap) picks the prop under the finger; the other gestures do nothing. */
    private fun shelfTap(t: CameraInput.Tap) {
        if (t.kind != CameraInput.TapKind.PICK && t.kind != CameraInput.TapKind.ZOOM) return
        val m = store.model(SupplyShelfLayout.MODEL) ?: return
        shelfScene.bind(m)
        val dir = FloatArray(3)
        camera.ray(t.x, t.y, width, height, dir)
        val top = FloatArray(3)
        val id = shelfScene.pick(camera.eye, dir, top) ?: return
        val cb = shelf?.onPick ?: return
        main.post { cb(id, top[0], top[1], top[2]) }
    }

    // ------------------------------------------------------------------ taps

    private fun handleTap(t: CameraInput.Tap, rigs: Collection<FishRig>) {
        if (camera.shelfOn) { shelfTap(t); return }
        val dir = FloatArray(3)
        camera.ray(t.x, t.y, width, height, dir)
        val o = camera.eye.copyOf()
        val l = listener
        when (t.kind) {
            CameraInput.TapKind.PICK -> {
                // the Flipper rests on the glass: tapping it hands the finger over to clean-glass mode
                if (!cleaning && camera.followId == null && hitsFlipper(o, dir)) { main.post { l?.onFlipperTap() }; return }
                val id = Picker.pick(o, dir, rigs)
                val following = camera.followId
                // while following, tapping another fish switches the follow to it
                if (following != null && id != null && id != following) main.post { l?.onFollowRequest(id) }
                // Submarine mode: no fingertip on the tank while the sub is out (the stick steers, taps only pick)
                else if (id == null && following == null && !cleaning && !camera.rideOn) {
                    // no animal under the finger: a fingertip on the tank (glass, sand or rock) where it landed
                    // (not in clean-glass mode, where the finger is on the Flipper)
                    val hit = touchHit(o, dir)
                    // a coral: lean in for a closer look as its polyps pull in
                    if (hit != null && hit.second == TouchSurface.CORAL) camera.peekAt(hit.first.x.toFloat(), hit.first.y.toFloat(), hit.first.z.toFloat())
                    if (hit != null) main.post { l?.onTouchTank(hit.first, hit.second) } else main.post { l?.onPick(null) }
                }
                else main.post { l?.onPick(id) }
            }
            CameraInput.TapKind.ZOOM -> {
                // double tap: a fish → fly in and follow it; while following → closer / normal framing;
                // otherwise glide in to the rock / sand / water point under the finger (again to glide back out)
                val id = Picker.pick(o, dir, rigs)
                when {
                    id != null && id != camera.followId -> main.post { l?.onFollowRequest(id) }
                    camera.followId != null -> camera.toggleFollowCloseUp()
                    else -> surfacePoint(o, dir)?.let { p -> camera.focusOn(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()) }
                }
            }
            CameraInput.TapKind.GLASS -> {
                val tz = Picker.rayPlaneZ(o, dir, Camera.TANK[2]) ?: return
                val p = Vec3((o[0] + dir[0] * tz).toDouble().coerceIn(0.0, Camera.TANK[0].toDouble()), (o[1] + dir[1] * tz).toDouble().coerceIn(Camera.SAND_Y.toDouble(), Camera.WATER_Y.toDouble()), Camera.TANK[2].toDouble())
                main.post { l?.onTapGlass(p) }
            }
            CameraInput.TapKind.LONG_PRESS -> {
                // on the glass or the water's top: a fingertip held there (slide, rest, flick); on rock / sand: food
                // (nothing while the submarine is out: the hold would only fight the sub's own controls)
                if (camera.rideOn) { holdMode = HOLD_ORBIT; return }
                val hit = if (camera.followId == null && !cleaning) touchHit(o, dir) else null
                if (hit != null && (hit.second == TouchSurface.GLASS || hit.second == TouchSurface.SURFACE)) { startFinger(hit.first, hit.second); return }
                holdMode = HOLD_ORBIT
                val p = surfacePoint(o, dir) ?: return
                main.post { l?.onLongPress(p) }
            }
        }
    }

    /** True when the ray meets the glass plane within a finger-friendly margin of the Flipper pad. */
    private fun hitsFlipper(o: FloatArray, d: FloatArray): Boolean {
        val t = Picker.rayPlaneZ(o, d, Camera.TANK[2]) ?: return false
        val x = o[0] + d[0] * t; val y = o[1] + d[1] * t
        return kotlin.math.abs(x - flipper[0]) <= FLIPPER_HALF_W + 2f && kotlin.math.abs(y - flipper[1]) <= FLIPPER_HALF_H + 2f
    }

    /**
     * What a single tap with no fish under it touches: the first rock point along the ray (ROCK; the sim turns it into
     * CORAL when a colony sits there), else the sand when the ray reaches the floor (SAND), else the glass where the
     * ray enters the water (GLASS, the fingertip on the pane). Null when the ray misses the tank.
     */
    private fun touchHit(o: FloatArray, d: FloatArray): Pair<Vec3, TouchSurface>? {
        var t0 = 0f; var t1 = 1000f
        val lo = floatArrayOf(0f, Camera.SAND_Y, 0f); val hi = floatArrayOf(Camera.TANK[0], Camera.WATER_Y, Camera.TANK[2])
        for (k in 0 until 3) {
            if (kotlin.math.abs(d[k]) < 1e-6f) { if (o[k] < lo[k] || o[k] > hi[k]) return null; continue }
            var a = (lo[k] - o[k]) / d[k]; var b = (hi[k] - o[k]) / d[k]
            if (a > b) { val s = a; a = b; b = s }
            t0 = maxOf(t0, a); t1 = minOf(t1, b)
        }
        if (t0 > t1) return null
        fun at(t: Float) = Vec3((o[0] + d[0] * t).toDouble(), (o[1] + d[1] * t).toDouble(), (o[2] + d[2] * t).toDouble())
        val g = sdf
        if (g != null) {
            val tr = t0 + Camera.marchClear(g, o[0] + d[0] * t0, o[1] + d[1] * t0, o[2] + d[2] * t0, d[0], d[1], d[2], t1 - t0, 0f)
            if (tr < t1 - 0.05f) {
                val r = at(tr)
                // from above, the finger meets the water before the rock under it
                if (o[1] + d[1] * t0 >= Camera.WATER_Y - 0.01f) return at(t0).let { Vec3(it.x, Camera.WATER_Y.toDouble(), it.z) } to TouchSurface.SURFACE
                return r to (if (r.y <= Camera.SAND_Y + 0.6) TouchSurface.SAND else if (onCoral(r)) TouchSurface.CORAL else TouchSurface.ROCK)
            }
        }
        val end = at(t1)
        val entry = at(t0)
        // through the top face, or the glass right at the waterline: a tap on the water's surface
        if (entry.y >= Camera.WATER_Y - SURFACE_BAND_CM) return Vec3(entry.x, Camera.WATER_Y.toDouble(), entry.z.coerceIn(0.0, Camera.TANK[2].toDouble())) to TouchSurface.SURFACE
        if (end.y <= Camera.SAND_Y + 0.5) return end to TouchSurface.SAND
        return Vec3(entry.x.coerceIn(0.0, Camera.TANK[0].toDouble()), entry.y.coerceIn(Camera.SAND_Y + 0.5, Camera.WATER_Y - 0.5), entry.z) to TouchSurface.GLASS
    }

    /** True when [p] lies on a drawn coral colony (its model bounds, moved by a copy's offset, padded a little). */
    private fun onCoral(p: Vec3): Boolean {
        val x = p.x.toFloat(); val y = p.y.toFloat(); val z = p.z.toFloat()
        for ((m, c) in coralModels(interp.current)) {
            val b = m.bounds
            val ox = c?.offsetX ?: 0f; val oy = c?.offsetY ?: 0f; val oz = c?.offsetZ ?: 0f
            if (x >= b[0] + ox - CORAL_PAD && x <= b[3] + ox + CORAL_PAD && y >= b[1] + oy - CORAL_PAD && y <= b[4] + oy + CORAL_PAD &&
                z >= b[2] + oz - CORAL_PAD && z <= b[5] + oz + CORAL_PAD) return true
        }
        return false
    }

    // ------------------------------------------------------------------ a fingertip held on the glass

    private var holdMode = HOLD_NONE
    private var fingerSurface = TouchSurface.GLASS
    private val fingerAt = FloatArray(3)
    private val fingerSent = FloatArray(3)
    private val fingerTrail = FloatArray(3)
    private var fingerSentNs = 0L
    private var fingerMovedNs = 0L
    private var fingerSpeed = 0f
    private val heldBuf = FloatArray(6)
    private val rayBuf = FloatArray(3)

    /** A long press landed on the glass or the water's top: the finger is on the tank there until it lifts. */
    private fun startFinger(p: Vec3, surface: TouchSurface) {
        holdMode = HOLD_FINGER
        fingerSurface = surface
        fingerAt[0] = p.x.toFloat(); fingerAt[1] = p.y.toFloat(); fingerAt[2] = p.z.toFloat()
        fingerAt.copyInto(fingerSent); fingerAt.copyInto(fingerTrail)
        fingerSpeed = 0f
        val now = System.nanoTime()
        fingerSentNs = now; fingerMovedNs = now
        val kind = if (surface == TouchSurface.SURFACE) BurstKind.SURFACE_RIPPLE else BurstKind.GLASS_RIPPLE
        particles.burst(TankBurst(kind, fingerAt[0], fingerAt[1], fingerAt[2], amount = 0.6f), interp.current)
        val l = listener
        main.post { l?.onFinger(p, 0f, surface) }
    }

    /**
     * The held finger this frame ([CameraInput.drainHeld]). A fingertip: follow it over the pane, ripple a trail every
     * [TRAIL_CM], report it (≤ 20 Hz moving, 4 Hz resting), and on lift a fast enough release is a flick (a puff of
     * water along the pane and a little into the tank). Any other hold (food dropped, the shelf, no long press seen)
     * drags and flings the camera exactly as a plain drag would.
     */
    private fun handleHeld(now: Long, cur: SimSnapshot?) {
        val r = input.drainHeld(heldBuf)
        val l = listener
        if (holdMode != HOLD_FINGER) {
            if (r and 1 != 0) camera.orbit(heldBuf[0], heldBuf[1], height.toFloat())
            if (r and 2 != 0) {
                val vx = heldBuf[4]; val vy = heldBuf[5]
                if (vx * vx + vy * vy >= GestureTracker.MIN_FLING * GestureTracker.MIN_FLING) camera.fling(vx, vy, height.toFloat())
                holdMode = HOLD_NONE
            }
            return
        }
        if (r and 1 != 0 && fingerPoint(heldBuf[2], heldBuf[3], rayBuf)) {
            val dx = rayBuf[0] - fingerAt[0]; val dy = rayBuf[1] - fingerAt[1]; val dz = rayBuf[2] - fingerAt[2]
            val dtS = ((now - fingerMovedNs) / 1e9f).coerceAtLeast(1e-3f)
            val inst = sqrt(dx * dx + dy * dy + dz * dz) / dtS
            fingerSpeed += (inst - fingerSpeed) * (dtS * 8f).coerceAtMost(1f)
            fingerMovedNs = now
            rayBuf.copyInto(fingerAt)
            val tx = fingerAt[0] - fingerTrail[0]; val ty = fingerAt[1] - fingerTrail[1]; val tz = fingerAt[2] - fingerTrail[2]
            if (tx * tx + ty * ty + tz * tz >= TRAIL_CM * TRAIL_CM) {
                fingerAt.copyInto(fingerTrail)
                val kind = if (fingerSurface == TouchSurface.SURFACE) BurstKind.SURFACE_RIPPLE else BurstKind.GLASS_RIPPLE
                particles.burst(TankBurst(kind, fingerAt[0], fingerAt[1], fingerAt[2], amount = 0.3f), cur)
            }
        } else if (now - fingerMovedNs > 120_000_000L) fingerSpeed *= 0.5f   // resting: the speed falls away
        if (r and 2 != 0) {
            holdMode = HOLD_NONE
            flick(heldBuf[2], heldBuf[3], heldBuf[4], heldBuf[5], cur)
            val surface = fingerSurface
            main.post { l?.onFinger(null, 0f, surface) }
            return
        }
        val sx = fingerAt[0] - fingerSent[0]; val sy = fingerAt[1] - fingerSent[1]; val sz = fingerAt[2] - fingerSent[2]
        val moved = sx * sx + sy * sy + sz * sz > 0.09f
        if (moved && now - fingerSentNs >= 50_000_000L || now - fingerSentNs >= 250_000_000L) {
            fingerAt.copyInto(fingerSent)
            fingerSentNs = now
            val p = Vec3(fingerAt[0].toDouble(), fingerAt[1].toDouble(), fingerAt[2].toDouble())
            val sp = fingerSpeed; val surface = fingerSurface
            main.post { l?.onFinger(p, sp, surface) }
        }
    }

    /** A release at ([x], [y]) moving ([vx], [vy]) px/s: fast enough over the pane, a puff of water that way. */
    private fun flick(x: Float, y: Float, vx: Float, vy: Float, cur: SimSnapshot?) {
        if (vx * vx + vy * vy < GestureTracker.MIN_FLING * GestureTracker.MIN_FLING) return
        val a = FloatArray(3); val b = FloatArray(3)
        if (!fingerPoint(x, y, a) || !fingerPoint(x + vx * FLICK_LOOK_S, y + vy * FLICK_LOOK_S, b)) return
        var wx = (b[0] - a[0]) / FLICK_LOOK_S; var wy = (b[1] - a[1]) / FLICK_LOOK_S; var wz = (b[2] - a[2]) / FLICK_LOOK_S
        val along = sqrt(wx * wx + wy * wy + wz * wz)
        if (along < FLICK_MIN_CM_S) return
        // the hand pushes water along the pane and a little into the tank
        val inn = inwardOf(a)
        wx += inn[0] * 0.4f * along; wy += inn[1] * 0.4f * along; wz += inn[2] * 0.4f * along
        val sp = sqrt(wx * wx + wy * wy + wz * wz)
        val k = minOf(1f, FLICK_MAX_CM_S / sp)
        wx *= k; wy *= k; wz *= k
        // start a little inside the water, in front of the pane
        val px = a[0] + inn[0] * 1.5f; val py = a[1] + inn[1] * 1.5f; val pz = a[2] + inn[2] * 1.5f
        particles.burst(TankBurst(BurstKind.SWISH, px, py, pz, r = wx / FLICK_MAX_CM_S, g = wy / FLICK_MAX_CM_S, b = wz / FLICK_MAX_CM_S, amount = (sp * k / FLICK_MAX_CM_S).coerceIn(0.3f, 1f)), cur)
        val p = Vec3(px.toDouble(), py.toDouble(), pz.toDouble()); val v = Vec3(wx.toDouble(), wy.toDouble(), wz.toDouble())
        val l = listener
        main.post { l?.onFlick(p, v) }
    }

    /** Unit vector into the water from the pane (or the surface) nearest [p]. */
    private fun inwardOf(p: FloatArray): FloatArray {
        val dz0 = p[2]; val dz1 = Camera.TANK[2] - p[2]; val dx0 = p[0]; val dx1 = Camera.TANK[0] - p[0]; val dy1 = Camera.WATER_Y - p[1]
        val m = minOf(minOf(dz0, dz1), minOf(minOf(dx0, dx1), dy1))
        return when (m) {
            dy1 -> floatArrayOf(0f, -1f, 0f)
            dz1 -> floatArrayOf(0f, 0f, -1f)
            dz0 -> floatArrayOf(0f, 0f, 1f)
            dx0 -> floatArrayOf(1f, 0f, 0f)
            else -> floatArrayOf(-1f, 0f, 0f)
        }
    }

    /** Where the ray under screen point ([x], [y]) enters the water box (the pane the finger is on), into [out]. */
    private fun fingerPoint(x: Float, y: Float, out: FloatArray): Boolean {
        val d = FloatArray(3)
        camera.ray(x, y, width, height, d)
        val o = camera.eye
        var t0 = 0f; var t1 = 1000f
        val lo = floatArrayOf(0f, Camera.SAND_Y, 0f); val hi = floatArrayOf(Camera.TANK[0], Camera.WATER_Y, Camera.TANK[2])
        for (k in 0 until 3) {
            if (kotlin.math.abs(d[k]) < 1e-6f) { if (o[k] < lo[k] || o[k] > hi[k]) return false; continue }
            var a = (lo[k] - o[k]) / d[k]; var b = (hi[k] - o[k]) / d[k]
            if (a > b) { val q = a; a = b; b = q }
            t0 = maxOf(t0, a); t1 = minOf(t1, b)
        }
        if (t0 > t1) return false
        out[0] = (o[0] + d[0] * t0).coerceIn(0.3f, Camera.TANK[0] - 0.3f)
        out[1] = if (fingerSurface == TouchSurface.SURFACE) Camera.WATER_Y else (o[1] + d[1] * t0).coerceIn(Camera.SAND_Y + 0.5f, Camera.WATER_Y - 0.5f)
        out[2] = (o[2] + d[2] * t0).coerceIn(0f, Camera.TANK[2])
        return true
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
        /** Static reef and glass details (ASSETS.md §4.1): coralline layer, encrusting colony, mushrooms, glass film. */
        const val DETAILS = "models/reef_details.glb"
        /** equipment.glb node of the Flipper magnet scraper (both halves), seated at x 2–8, y 13–33 on the front glass. */
        const val FLIPPER_NODE = "Flipper"
        const val FLIPPER_REST_X = 5f
        const val FLIPPER_REST_Y = 23f
        const val FLIPPER_HALF_W = GlassStroke.FLIPPER_W / 2
        const val FLIPPER_HALF_H = GlassStroke.FLIPPER_H / 2
        /** Glide rate (1/s) of the pad toward the finger. */
        const val FLIPPER_FOLLOW = 18f
        /** Texture unit of `uGlassMask` ([ShaderProgram.SAMPLER_UNITS]) and the core GlassMask grid. */
        const val GLASS_MASK_UNIT = 11
        const val GLASS_MASK_W = com.reeflive.core.sim.GlassMask.W
        const val GLASS_MASK_H = com.reeflive.core.sim.GlassMask.H
        val SCENE_MODELS = listOf(TANK, ROCK, EQUIPMENT, ALGAE, DETAILS)
        /** Static models whose rock / equipment parts cast into the shadow map (sand only receives). */
        val SHADOW_STATIC = listOf(ROCK, EQUIPMENT, DETAILS)
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
        /** Parts of a coral model within this distance of its home anchor make up one colony (sandbox copies). */
        const val COPY_RADIUS_CM = 25f

        /**
         * Tree-coral motion (Kenya, extras.swayMode = 1), from Tee's clip (research/video/coral_kenya.md [H],
         * 2026-10-13): tip sway amplitude per cm of colony height at flow 12 (≤ ±3 % of height was seen, so 0.035
         * at full flow and ≈ 0.023 at the tank's usual 8), the sprigs' fringe flutter (cm at the sprig tip), and the
         * fish ripple: reach from the colony bounds and the speed that gives full strength.
         */
        const val TREE_SWAY = 0.035f
        /** A tap within this band below the waterline (cm) counts as a tap on the water's surface. */
        const val SURFACE_BAND_CM = 2.5f
        /** Padding (cm) around a coral model's bounds for a tap to count as on the coral. */
        const val CORAL_PAD = 1.0f
        private const val HOLD_NONE = 0
        private const val HOLD_ORBIT = 1
        private const val HOLD_FINGER = 2
        /** Fingertip trail ripple spacing (cm), flick look-ahead (s), slowest flick and strongest puff (cm/s). */
        const val TRAIL_CM = 1.5f
        const val FLICK_LOOK_S = 0.05f
        const val FLICK_MIN_CM_S = 20f
        const val FLICK_MAX_CM_S = 45f
        const val TREE_FLUTTER = 0.12f
        const val WAKE_RANGE_CM = 6f
        const val WAKE_SPEED_CM_S = 10f

        /**
         * Linear albedo multipliers by GLB material name, for coral colours the baked textures get wrong under
         * the tank light (no re-export needed). Zoa orange: the #E0792A disc read yellow under the blue-heavy light;
         * deepened to ≈ #E0671D. (Kenya's tint went with the 2026-10-13 rebuild: its textures now carry the clip's
         * pinkish tan directly, body ≈ #A27D70 with cream #C9B3A8 stalk and #D8C4BC tips.)
         */
        val CORAL_TINT: Map<String, FloatArray> = mapOf(
            "zoa_polyp_orange" to floatArrayOf(1.0f, 0.70f, 0.55f),
        )
    }
}
