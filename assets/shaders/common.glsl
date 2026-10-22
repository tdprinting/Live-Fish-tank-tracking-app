// Shared by every ReefLive shader (ARCHITECTURE §8.4). Included right after #version.
precision highp float;
precision highp int;

// std140 `Frame` UBO, binding 0. Keep in sync with FrameUniforms.kt.
layout(std140) uniform Frame {
    mat4 uView;
    mat4 uProj;
    mat4 uViewProj;
    vec4 uCamPos;        // xyz eye (cm), w time (s)
    vec4 uLightPos[2];   // xyz AI Prime position (cm), w cos(half cone)
    vec4 uLightColor[2]; // rgb linear colour x intensity, w intensity
    vec4 uAmbientB;      // rgb ambient (keyColor x 0.18), w blue fraction
    vec4 uFog;           // rgb fog colour, w fog density (1/cm)
    vec4 uParams;        // x water level, y caustic strength, z caustic scale, w quality (0 low, 1 medium, 2 high)
    vec4 uParams2;       // x night (0/1), y algae bloom, z flow strength, w sand top (cm)
    vec4 uAbsorb;        // rgb Beer-Lambert absorption (1/cm), w scattering (1/cm)
    vec4 uTank;          // xyz interior size (cm), w unused
    vec4 uSurfaceDims;   // xy heightfield size, z 1 when heights are present, w unused
    // Algae cover 0..1 per surface (AlgaeCover, research 09 A6); packed by AlgaeVisuals.kt.
    vec4 uAlgaeGlass;    // x green film, y diatoms, z cyano, w dinos
    vec4 uAlgaeSand;     // x diatoms, y cyano, z dinos, w hair
    vec4 uAlgaeTop;      // rock tops: x hair, y cyano, z dinos, w bubble
    vec4 uAlgaeShade;    // shaded rock: x coralline, y cyano, z dinos, w bubble
    // Realism effects by quality tier (FrameUniforms.setEffects / setCasters).
    vec4 uFx;            // x contact-shadow caster count, y light-shaft strength, z caustic dispersion (0/1), w surface ripple
    vec4 uCasters[12];   // fish casting soft contact shadows: xyz mid-body (cm), w body length (cm)
    // Real-time shadow map (ShadowMap.kt): one orthographic depth pass looking down from the fixtures.
    mat4 uShadowMat;     // world -> shadow clip (orthographic, w = 1)
    vec4 uShadow;        // x on (0/1), y texel size (cm), z strength 0..1, w depth bias (cm)
};

#define uTime uCamPos.w
#define uWaterLevel uParams.x
#define uCausticStrength uParams.y
#define uCausticScale uParams.z
#define uQuality uParams.w
#define uNight uParams2.x
#define uAlgaeBloom uParams2.y
#define uFlowStrength uParams2.z
#define uBlueFraction uAmbientB.w
#define uCasterCount uFx.x
#define uShaftStrength uFx.y
#define uCausticDispersion uFx.z
#define uRipple uFx.w

const float PI = 3.14159265;

// Water-surface heightfield (cm above the mean level), x across the tank length, y across the depth.
uniform sampler2D uSurface;

float surfaceHeight(vec2 xz) {
    if (uSurfaceDims.z < 0.5) return 0.0;
    return textureLod(uSurface, clamp(xz / uTank.xz, 0.0, 1.0), 0.0).r;
}

vec2 surfaceSlope(vec2 xz) {
    if (uSurfaceDims.z < 0.5) return vec2(0.0);
    vec2 e = vec2(1.3, 0.0);
    return vec2(surfaceHeight(xz + e.xy) - surfaceHeight(xz - e.xy), surfaceHeight(xz + e.yx) - surfaceHeight(xz - e.yx)) / (2.0 * e.x);
}

// Render-only swell and chop on top of the simulated heightfield: the pumps and the return keep an open-top reef's
// surface rolling (Tee's tank video 2026-10-20). Five travelling waves (wavelength 41..4.6 cm, ~22-30 cm/s), bigger
// with more flow, scaled by quality (uRipple). x: height (cm), yz: slope (dh/dx, dh/dz). Shared by the water surface
// (geometry + normals) and everything lit through it (caustics, flicker), so a choppier surface throws a busier,
// brighter shimmer.
// x, z direction, wavenumber k (rad/cm), amplitude (cm); angular speed in SWELL_W (rad/s)
const vec4 SWELL[5] = vec4[5](vec4(0.94, 0.34, 0.153, 0.26), vec4(-0.6, 0.8, 0.29, 0.13), vec4(0.8, -0.6, 0.52, 0.07),
                              vec4(0.31, 0.95, 0.85, 0.04), vec4(-0.97, -0.24, 1.37, 0.022));
const float SWELL_W[5] = float[5](3.4, 6.6, 12.5, 22.0, 37.0);

// [waves]: how many of the five to sum (lighting takes the three longest; the short chop blurs out with depth).
vec3 swell(vec2 q, int waves) {
    float amp = uRipple * (0.55 + 0.55 * uFlowStrength);
    vec3 r = vec3(0.0);
    for (int i = 0; i < 5; i++) {
        if (i >= waves) break;
        vec4 w = SWELL[i];
        float ph = dot(q, w.xy) * w.z + uTime * SWELL_W[i];
        r += w.w * vec3(sin(ph), w.z * cos(ph) * w.xy);
    }
    return r * amp;
}

vec3 toLinear(vec3 srgb) { return pow(max(srgb, vec3(0.0)), vec3(2.2)); }

// Rotate v by unit quaternion q (xyzw).
vec3 qrot(vec4 q, vec3 v) { return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v); }

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// Smooth 3D value noise in 0..1.
float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash13(i), hash13(i + vec3(1.0, 0.0, 0.0)), u.x),
                  mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), u.x), u.y);
    float b = mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), u.x),
                  mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), u.x), u.y);
    return mix(a, b, u.z);
}

// Three-octave value noise, mean ~0.5, sd ~0.12.
float fbm3(vec3 p) {
    return vnoise(p) * 0.5 + vnoise(p * 2.03 + 17.1) * 0.3 + vnoise(p * 4.11 + 41.7) * 0.2;
}

// Patchy algae mask: about `cover` of the surface area lies inside a patch (fbm3 value n is remapped to
// ~uniform with a logistic approximation of its CDF, then thresholded at 1 - cover with soft edges).
float algaePatch(float n, float cover) {
    float c = clamp(cover, 0.0, 1.0);
    if (c < 0.001) return 0.0;
    float u = 1.0 / (1.0 + exp(-14.5 * (n - 0.5)));
    float t = 1.0 - c;
    return max(smoothstep(t - 0.04, t + 0.04, u), smoothstep(0.85, 1.0, c));
}

// Opacity inside a patch: thin film at low cover, dense mat at bloom levels.
// Green star polyp mat margin (GspMatDraw, gsp_mat.frag, polyp.vert): the mat reaches [r] cm along the rock, with a
// lobed edge and thin stolon runners reaching ahead of it ([op] = position in the rock's own frame).
float gspEdge(vec3 op, float r) {
    float lobes = (vnoise(op * 0.21) - 0.5) * 4.0;              // value noise sits near 0.5: about -1..1
    // runners: the 0.5 level lines of a finer noise (2-3 mm wide), only where a broad mask lets one grow
    float line = 1.0 - smoothstep(0.03, 0.09, abs(vnoise(op * 0.6 + vec3(7.0, 3.0, 1.0)) - 0.5));
    float run = line * smoothstep(0.52, 0.62, vnoise(op * 0.13 + vec3(2.0, 9.0, 4.0)));
    return r + min(r * 0.15, 1.4) * lobes + min(r * 0.35, 2.4) * run;
}

float algaeDensity(float cover) { return mix(0.45, 0.92, smoothstep(0.0, 0.5, cover)); }

// Length of the segment a->b that lies inside the water volume (box 0..tank, below the surface).
float waterPath(vec3 a, vec3 b) {
    vec3 d = b - a;
    vec3 lo = vec3(0.0);
    vec3 hi = vec3(uTank.x, uWaterLevel, uTank.z);
    vec3 inv = 1.0 / mix(d, vec3(1e-5), vec3(lessThan(abs(d), vec3(1e-5))));
    vec3 t0 = (lo - a) * inv;
    vec3 t1 = (hi - a) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    float tn = max(max(tmin.x, tmin.y), max(tmin.z, 0.0));
    float tf = min(min(tmax.x, tmax.y), min(tmax.z, 1.0));
    return max(tf - tn, 0.0) * length(d);
}

// Branching-tree sway (Kenya tree, extras.swayMode = 1; measured from Tee's clip, research/video/coral_kenya.md [H]):
// a slow whole-crown lean (period ~7 s) plus a quicker branch-tip sway (~2 s) whose phase lags with distance from the
// base, a little cross-flow wander, and a brief local ripple round a passing fish (wake.xyz world cm, wake.w 0..1).
// The stalk and trunk bases (r < ~25 % of the height from uBase) stay put. static.vert (body) and polyp.vert (sprigs,
// evaluated at the instance base) call it with the same arguments, so the sprigs ride their branch.
vec3 treeSway(vec3 p, vec3 base, float height, float phase, vec3 dir, float amp, vec4 wake) {
    float r = clamp(length(p - base) / max(height, 1.0), 0.0, 1.2);
    float wgt = pow(smoothstep(0.2, 1.0, r), 1.5);
    float sp = dot(p.xz, vec2(0.23, 0.17));          // neighbouring branches move together, distant ones drift apart
    float lean = sin(uTime * 0.9 + phase);
    float tip = sin(uTime * 3.14 + phase * 1.7 + sp - 1.6 * wgt);
    vec3 side = vec3(-dir.z, 0.0, dir.x);
    vec3 off = dir * (amp * wgt * (0.65 * lean + 0.35 * tip))
             + side * (amp * wgt * 0.2 * sin(uTime * 2.3 + sp * 1.3 + phase));
    off.y -= 0.1 * amp * wgt * (0.5 + 0.5 * lean);
    if (wake.w > 0.0) {
        vec3 q = p - wake.xyz;
        float dq = length(q);
        float fall = exp(-dq * dq / 30.0);           // ~5.5 cm radius
        off += (q / max(dq, 0.5)) * (wake.w * fall * wgt * 0.7 * (0.55 + 0.45 * sin(uTime * 7.0 - dq * 1.1)));
    }
    return off;
}
