#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Rock and sand PBR-lite (ARCHITECTURE §8.4 item 1): Lambert + GGX, caustics, hair-algae bloom, Beer-Lambert + fog.
// SAND: tiled detail normal map via a derivative cotangent frame (the GLBs carry no tangents) and the front-glass
// green film (COLOR_0.r). ROCK: distance-based detail levels of procedural relief, plus the small life on it.
// Algae cover (research 09 A6) from the Frame UBO as patchy world-space noise masks: sand gets diatom dust,
// red cyano mats and khaki dino strands; rock tops hair turf, cyano and dinos; shaded rock coralline crust.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAlbedo;
uniform sampler2D uNormalMap;
uniform vec4 uBaseColor;
uniform float uHasTexture;
uniform float uRoughness;
uniform float uNormalStrength; // 0 disables the normal map (Low quality)
uniform float uDetailScale;    // normal-map uv multiplier
// Microfauna on the rock (MicrofaunaState.visible, animals in view per cm² of rock): x harpacticoid copepods,
// y ostracods, z forams (Homotrema); w unused. Zero turns them off.
uniform vec4 uMicro;
#ifndef SAND
// object-space position (static.vert, ROCK): the rock detail is pinned to the rock, so it rides along if a rock moves
in vec3 vObj;
uniform mat4 uModel;
#endif
uniform float uDryRock;        // aquascape builder dry rock: 1 fresh white, 0 fully cured live rock
#ifdef SAND
// Dynamic sand bed (SIMULATION §16, SandFx): r height offset (cm), g freshness, b detritus, a dug cavity under rock.
uniform sampler2D uSandMap;
uniform float uSandOn;
uniform vec2 uSandExtent;
#endif

out vec4 fragColor;

vec3 perturb(vec3 N, vec3 p, vec2 uv, vec3 tn) {
    vec3 dp1 = dFdx(p), dp2 = dFdy(p);
    vec2 du1 = dFdx(uv), du2 = dFdy(uv);
    vec3 dp2perp = cross(dp2, N);
    vec3 dp1perp = cross(N, dp1);
    vec3 T = dp2perp * du1.x + dp1perp * du2.x;
    vec3 B = dp2perp * du1.y + dp1perp * du2.y;
    float invmax = inversesqrt(max(dot(T, T), dot(B, B)) + 1e-12);
    mat3 tbn = mat3(T * invmax, B * invmax, N);
    return normalize(tbn * tn);
}

vec3 hash33(vec3 p) { return vec3(hash13(p), hash13(p + 19.19), hash13(p + 47.31)); }

// Value noise (common.glsl vnoise lattice) with its analytic gradient: xyz = d/dp, for relief normals without
// screen-space derivatives (no 2x2 blockiness up close, no shimmer far away).
vec4 vnoiseD(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    vec3 du = 6.0 * f * (1.0 - f);
    float a = hash13(i), b = hash13(i + vec3(1.0, 0.0, 0.0)), c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0)), e = hash13(i + vec3(0.0, 0.0, 1.0)), g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0)), k = hash13(i + vec3(1.0, 1.0, 1.0));
    float k1 = b - a, k2 = c - a, k3 = e - a, k4 = a - b - c + d, k5 = a - c - e + h, k6 = a - b - e + g;
    float k7 = -a + b + c - d + e - g - h + k;
    float v = a + k1 * u.x + k2 * u.y + k3 * u.z + k4 * u.x * u.y + k5 * u.y * u.z + k6 * u.z * u.x + k7 * u.x * u.y * u.z;
    vec3 gr = du * vec3(k1 + k4 * u.y + k6 * u.z + k7 * u.y * u.z,
                        k2 + k5 * u.z + k4 * u.x + k7 * u.z * u.x,
                        k3 + k6 * u.x + k5 * u.y + k7 * u.x * u.y);
    return vec4(v, gr);
}

// Limestone voids: the rock is cut through a field of spherical holes (one maybe in each unit cell, jittered, 2x2x2
// search), so where the surface slices a void it shows a round pit whose size depends on how deep the cut goes, as on
// real live rock. Returns the pit depth profile (0 rim .. 1 centre, a hemisphere) and its gradient (per unit of p).
vec4 voids(vec3 p, float density) {
    vec3 b = floor(p - 0.5);
    vec4 best = vec4(0.0);
    for (int z = 0; z < 2; z++)
    for (int y = 0; y < 2; y++)
    for (int x = 0; x < 2; x++) {
        vec3 c = b + vec3(float(x), float(y), float(z));
        float h = hash13(c + 0.37);
        if (h >= density) continue;
        vec3 j = fract((h / max(density, 1e-3)) * vec3(97.13, 41.71, 13.37) + hash13(c + 5.1));
        vec3 dv = p - (c + 0.2 + 0.6 * j);
        float r = 0.28 + 0.22 * fract(h * 531.7);
        float q = dot(dv, dv) / (r * r);
        if (q < 1.0) {
            float dep = sqrt(1.0 - q);
            // d(dep)/dp = -dv / (r² dep); capped at the rim so the wall stays steep but finite
            if (dep > best.x) best = vec4(dep, -dv / (r * r * max(dep, 0.2)));
        }
    }
    return best;
}

// Detail level weight of a relief octave with `freq` cycles per cm at `fp` (length(fwidth), about two pixel widths):
// 1 while a period spans 8+ pixels, 0 below ~3 (it would only alias).
float lodW(float freq, float fp) { return 1.0 - smoothstep(0.15, 0.33, fp * freq); }   // fp ~ 2 pixel widths

const vec3 FORAM_RED = vec3(0.52, 0.04, 0.07);      // Homotrema rubrum test
const vec3 POD_AMBER = vec3(0.62, 0.42, 0.18);      // harpacticoid: translucent tan-amber, darker gut
const vec3 POD_GUT = vec3(0.16, 0.09, 0.05);
const vec3 OSTRACOD = vec3(0.30, 0.27, 0.17);       // ostracod: olive-beige bean-shaped shell

// Small life on the rock, only where it resolves (research 10 / 17): sessile red forams (Homotrema rubrum, 2-8 mm
// red domes, in shaded crevices and under ledges) and crawling pods (harpacticoid copepods ~0.8 mm that dart then
// pause; ostracods ~0.8 mm that glide). Densities come from the sim (uMicro, per cm² of rock in view). Each lives in
// its own lattice cell and stays inside it, so nothing is cut at a cell edge; shapes are drawn in the surface plane.
void rockLife(vec3 p, float fp, float sky, vec3 N, inout vec3 albedo, inout float rough) {
    float wf = 1.0 - smoothstep(0.03, 0.08, fp);
    if (wf > 0.0 && uMicro.z > 0.0) {
        const float FC = 1.2;
        vec3 cc = floor(p / FC);
        vec3 hh = hash33(cc + 71.0);
        // cryptic: prefers crevices and undersides
        float pref = mix(0.25, 1.0, 1.0 - sky) * mix(1.0, 0.35, smoothstep(0.2, 0.8, N.y));
        if (hh.x < min(uMicro.z * FC * FC * 1.5, 0.6) * pref) {
            vec3 c = (cc + 0.3 + 0.4 * hash33(cc + 5.0)) * FC;
            vec3 dv = p - c;
            vec3 dt = dv - N * dot(dv, N);
            float r = 0.1 + 0.22 * hh.y;
            // knobby outline (short branches round the dome)
            vec3 ax = normalize(cross(N, vec3(0.31, 0.57, 0.76)));
            float ang = atan(dot(dt, cross(N, ax)), dot(dt, ax));
            float rr = r * (0.9 + 0.1 * sin(ang * (3.0 + floor(hh.z * 3.0)) + hh.z * 6.0));
            float m = clamp((rr - length(dt)) / max(fp, 1e-4) + 0.5, 0.0, 1.0) * step(abs(dot(dv, N)), FC * 0.5) * wf;
            float dome = sqrt(max(1.0 - dot(dt, dt) / (rr * rr), 0.0));
            albedo = mix(albedo, FORAM_RED * (0.75 + 0.5 * hh.z) * (0.7 + 0.3 * dome), m);
            rough = mix(rough, 0.6, m);
        }
    }
    float wp = 1.0 - smoothstep(0.012, 0.03, fp);
    float pods = uMicro.x + uMicro.y;
    if (wp > 0.0 && pods > 0.0) {
        const float PC = 0.6;
        vec3 cc = floor(p / PC);
        vec3 hh = hash33(cc + 13.0);
        if (hh.x < min(pods * PC * PC * 1.5, 0.85)) {
            bool ostra = hh.z < uMicro.y / pods;
            float t = uTime * (ostra ? 0.3 : 0.8) + hh.y * 17.0;
            float seg = floor(t);
            // harpacticoids: a quick dart, then a pause; ostracods: a steady glide
            float k = ostra ? fract(t) : smoothstep(0.0, 0.15, fract(t));
            vec3 a = cc + 0.2 + 0.6 * hash33(cc + seg * 1.7);
            vec3 b = cc + 0.2 + 0.6 * hash33(cc + (seg + 1.0) * 1.7);
            vec3 dv = p - mix(a, b, k) * PC;
            vec3 dt = dv - N * dot(dv, N);
            vec3 dir = b - a;
            vec3 ax = dir - N * dot(dir, N);
            ax *= inversesqrt(max(dot(ax, ax), 1e-8));
            float along = dot(dt, ax);
            float across = length(dt - ax * along);
            float hl = ostra ? 0.04 : 0.045;   // half length (cm): ~0.8-0.9 mm animals
            float hw = ostra ? 0.028 : 0.018;
            float e = length(vec2(along / hl, across / hw));
            float m = clamp((1.0 - e) * hw / max(fp, 1e-4) + 0.5, 0.0, 1.0) * step(abs(dot(dv, N)), PC * 0.5) * wp;
            vec3 body = ostra ? OSTRACOD : mix(POD_AMBER, POD_GUT, (1.0 - smoothstep(0.0, 0.45, across / hw)) * 0.6 * step(-0.6, along / hl));
            albedo *= 1.0 - 0.35 * wp * (1.0 - smoothstep(1.0, 1.8, e)) * (1.0 - m);   // contact shade round the body
            albedo = mix(albedo, body, m);
            rough = mix(rough, ostra ? 0.3 : 0.45, m);
        }
    }
}

const vec3 ROCK_BLEACH = vec3(0.50, 0.42, 0.28);   // sun-bleached cream-tan skeleton
const vec3 ROCK_GREY = vec3(0.24, 0.21, 0.16);     // older grey limestone
const vec3 ROCK_FILM = vec3(0.17, 0.19, 0.07);    // olive-green algae film
const vec3 SHELL_PINK = vec3(0.50, 0.32, 0.30);    // shell grit
const vec3 SHELL_GREY = vec3(0.24, 0.21, 0.18);    // dark aragonite / rubble grit

const vec3 ALG_HAIR = vec3(0.043, 0.105, 0.027);   // #3B5B2E
const vec3 ALG_DIATOM = vec3(0.26, 0.13, 0.03);    // tan / golden brown
const vec3 ALG_CYANO = vec3(0.16, 0.012, 0.016);   // burgundy red slime
const vec3 ALG_DINO = vec3(0.24, 0.16, 0.06);      // khaki brown snot
const vec3 ALG_BUBBLE = vec3(0.02, 0.07, 0.03);    // glossy dark green
const vec3 ALG_CORAL_PINK = vec3(0.52, 0.11, 0.19);   // pink-red, as on Tee's rock
const vec3 ALG_CORAL_PURPLE = vec3(0.20, 0.05, 0.27);

// Mats shared by sand and rock: cyano (slimy red, O2 bubbles by day) then dinos (stringy, lift at night).
// Covers are 0..1; n is an fbm3 value (cyano takes the low end, dinos the high end, so they sit apart);
// albedo and roughness are updated in place.
void slimeMats(vec3 p, float n, float cyano, float dinos, inout vec3 albedo, inout float rough) {
    float cy = algaePatch(1.0 - n, cyano) * algaeDensity(cyano);
    albedo = mix(albedo, ALG_CYANO, cy);
    float bub = step(0.94, hash13(floor(p * 2.5))) * cy * (1.0 - uNight) * clamp(lightIntensity() * 2.0, 0.0, 1.0);
    albedo = mix(albedo, vec3(0.55, 0.6, 0.6), bub * 0.6);
    float strand = 1.0 - abs(2.0 * vnoise(p * vec3(0.8, 0.12, 0.8)) - 1.0);
    float di = algaePatch(n, dinos) * algaeDensity(dinos) * mix(0.55, 1.0, strand) * mix(1.0, 0.5, uNight);
    albedo = mix(albedo, ALG_DINO, di);
    rough = mix(rough, 0.3, clamp(max(cy, di), 0.0, 1.0));
}

void main() {
    vec2 uv0 = vUv;
#ifdef SAND
    // The skirt (the sand cross-section seen through the front / side glass) shares its top ring with the bed in
    // tank.glb, so its uv is constant down the wall (vertical smears) and its smooth vertex normals tilt. Take the
    // geometric normal there and project the bed texture onto the wall plane at the bed's own scale (30.475 cm tile).
    vec3 Ng = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
    if (dot(Ng, uCamPos.xyz - vWorld) < 0.0) Ng = -Ng;
    float wall = 1.0 - smoothstep(0.35, 0.6, abs(Ng.y));
    vec2 wp2 = abs(Ng.z) > abs(Ng.x) ? vWorld.xy : vWorld.zy;   // cm on the wall plane
    if (wall > 0.5) uv0 = wp2 / 30.475;
#endif
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, uv0) : vec4(1.0);
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    float rough = uRoughness;
    float ao = 1.0;
    float bumpHt = 0.0;   // procedural height (cm) for bumpH: rock pits, sand ripples
    float glint = 0.0;    // sand sparkle weight
    vec3 glintN = N;
#ifdef SAND
    albedo = mix(albedo, vec3(0.27, 0.35, 0.12), clamp(vColor.r, 0.0, 1.0) * 0.55 * (1.0 - wall));
    {
        // aragonite bed: per-grain tone, sparse shell / rubble grit, broad tone drift, flow ripples, damp contact slopes.
        // Grain detail fades with the pixel footprint so it settles to its mean instead of shimmering at a distance.
        float fp = length(fwidth(vWorld));
        float grainVis = 1.0 - smoothstep(0.06, 0.3, fp);
        vec3 gc = floor(vWorld * 7.0);
        float g = hash13(gc);
        albedo *= mix(1.0, 0.84 + 0.32 * g, grainVis * mix(0.35, 0.85, max(smoothstep(0.2, 0.6, N.y), wall)));
        if (wall > 0.0) {
            // side profile against the glass: round 0.5-2 mm aragonite grains with dark gaps, a few shell bits, faint
            // horizontal layering, darker and damper with depth, pod / worm burrows, and a green-brown film near the
            // lit top edge
            N = normalize(mix(N, Ng, wall));
            // two jittered grain layers (~1.6 and ~0.9 mm) so no grid shows
            vec2 q = wp2 * 6.2;
            vec3 gh = hash33(vec3(floor(q), 7.0));
            float grain = 1.0 - smoothstep(0.2 + 0.18 * gh.z, 0.32 + 0.18 * gh.z, length(fract(q) - 0.5 - (gh.xy - 0.5) * 0.6));
            vec2 q2 = mat2(0.8, 0.6, -0.6, 0.8) * wp2 * 11.0 + 3.7;
            vec3 gh2 = hash33(vec3(floor(q2), 13.0));
            float grain2 = 1.0 - smoothstep(0.18 + 0.15 * gh2.z, 0.3 + 0.15 * gh2.z, length(fract(q2) - 0.5 - (gh2.xy - 0.5) * 0.6));
            float wv = wall * (1.0 - smoothstep(0.03, 0.12, fp));
            float body = max(grain * (0.92 + 0.3 * gh.z), grain2 * (0.85 + 0.3 * gh2.z));
            albedo *= mix(1.0, mix(0.72, 1.0, body), wv);
            albedo = mix(albedo, gh.x > 0.5 ? SHELL_PINK : SHELL_GREY, step(0.975, gh.y) * grain * wv * 0.8);
            float layer = sin(wp2.y * 4.2 + vnoise(vec3(wp2.x * 0.25, wp2.y * 0.6, 3.0)) * 4.0);
            albedo *= 1.0 + 0.07 * layer * wall;
            // depth darkening starts a cm below the bed's top edge, so the top of the skirt carries the bed's own tone and
            // the sand meets the front glass without a dark seam
            float deep = smoothstep(uParams2.w - 0.8, -0.5, wp2.y);
            float topEdge = wall * smoothstep(uParams2.w - 1.2, uParams2.w + 0.4, wp2.y);
            albedo = mix(albedo, albedo * vec3(1.12, 1.1, 1.04), topEdge * 0.5);
            albedo *= mix(1.0, 0.85, deep * wall);
            rough = mix(rough, rough * 0.7, deep * wall);
            float worm = 1.0 - abs(2.0 * vnoise(vec3(wp2.x * 0.32, wp2.y * 0.85, 11.0)) - 1.0);
            float pod = smoothstep(0.8, 0.86, vnoise(vec3(wp2 * 0.75, 23.0)));
            // only a few stretches of the pane have tunnels, and they settle into the sand tone at a distance
            float colony = smoothstep(0.55, 0.7, vnoise(vec3(wp2.x * 0.06, 0.0, 41.0)));
            float burrow = max(smoothstep(0.92, 0.96, worm) * colony, pod) * smoothstep(uParams2.w + 0.2, uParams2.w - 1.0, wp2.y);
            albedo *= 1.0 - 0.6 * burrow * wall * (1.0 - smoothstep(0.1, 0.35, fp));
            float film = smoothstep(uParams2.w - 0.3, uParams2.w + 1.8, wp2.y) * (0.3 + 0.7 * vnoise(vec3(wp2 * 0.4, 31.0)));
            albedo = mix(albedo, vec3(0.16, 0.17, 0.07), film * wall * 0.18);
        }
        // shell / rubble chips: a round, jittered blob in ~1 of 25 half-cm cells (spheres cut by the bed surface)
        vec3 cp = vWorld * 2.2;
        vec3 ch = hash33(floor(cp) + 3.7);
        float chipD = length(fract(cp) - 0.5 - (ch - 0.5) * 0.4);
        float chip = step(0.96, ch.x) * smoothstep(0.1 + 0.12 * ch.y, 0.05 + 0.12 * ch.y, chipD);
        float gritVis = (1.0 - smoothstep(0.2, 0.7, fp)) * smoothstep(0.2, 0.6, N.y);
        albedo = mix(albedo, ch.z > 0.6 ? SHELL_PINK : SHELL_GREY, chip * gritVis * 0.7);
        albedo *= 0.9 + 0.2 * vnoise(vWorld * vec3(0.09, 0.2, 0.09));
        float up = smoothstep(0.6, 0.95, N.y);
        if (uQuality >= 0.5) {
            // ripples across the main flow (along x), wavelength ~2.6 cm, warped and in patches; sharp crests
            float warp = vnoise(vWorld * vec3(0.18, 0.1, 0.32));
            float ph = (vWorld.z + warp * 5.0 + vWorld.x * 0.12) * 2.4;
            float r = 1.0 - abs(sin(ph));
            float patchR = smoothstep(0.35, 0.7, vnoise(vWorld * 0.05 + vec3(7.0, 0.0, 3.0)));
            float rip = r * r * patchR * up * (1.0 - smoothstep(0.4, 1.2, fp));
            bumpHt = rip * 0.22;
            albedo *= 1.0 + 0.05 * (rip - 0.3 * patchR * up);
        }
        // mounded sand banked against rock and holes is damper and darker
        float slope = 1.0 - smoothstep(0.75, 0.98, N.y);
        float bank = smoothstep(uParams2.w + 0.8, uParams2.w + 4.5, vWorld.y);
        albedo *= 1.0 - 0.22 * max(slope * step(0.3, N.y), bank * 0.6) * (1.0 - wall);
        // glints: one grain in ~25 is a cleaved facet that flashes when it lines up with a fixture
        if (uQuality >= 0.5) {
            vec3 gr = hash33(gc + 11.0);
            glint = step(0.96, gr.x) * grainVis * up;
            glintN = normalize(N + (gr - 0.5) * vec3(0.9, 0.0, 0.9));
        }
    }
    if (uSandOn > 0.5) {
        vec2 ext = max(uSandExtent, vec2(1.0));
        vec2 suv = clamp(vWorld.xz / ext, 0.0, 1.0);
        vec4 sm = texture(uSandMap, suv);
        vec2 ts = vec2(textureSize(uSandMap, 0));
        vec2 cell = ext / ts;
        vec2 du = vec2(1.0 / ts.x, 0.0);
        vec2 dv = vec2(0.0, 1.0 / ts.y);
        float hx = (texture(uSandMap, suv + du).r - texture(uSandMap, suv - du).r) / (2.0 * cell.x);
        float hz = (texture(uSandMap, suv + dv).r - texture(uSandMap, suv - dv).r) / (2.0 * cell.y);
        // slope of the displaced surface (the mesh normal is the flat bed's)
        N = normalize(N + vec3(-hx, 0.0, -hz) * step(0.5, N.y));
        // freshly turned sand: the damp, darker layer from below, speckled, a little glossier
        float wet = clamp(sm.g, 0.0, 1.0);
        float speck = hash13(floor(vWorld * 6.0));
        albedo *= mix(1.0, 0.66 + 0.1 * speck, wet);
        rough = mix(rough, rough * 0.65, wet);
        // mulm collects as a brown film; sifted patches come up clean and bright
        albedo = mix(albedo, vec3(0.16, 0.11, 0.06), smoothstep(0.35, 1.5, sm.b) * 0.45);
        albedo *= mix(1.06, 1.0, smoothstep(0.0, 0.2, sm.b));
        // a, dug cavity under rock (SandBed.cavity): the burrow floor sits in the rock's shadow, darkest deep in
        albedo *= 1.0 - 0.55 * clamp(sm.a, 0.0, 1.0);
    }
    if (dot(uAlgaeSand, vec4(1.0)) > 0.001) {
        vec3 sp = vWorld * 0.07;
        float n1 = fbm3(sp);
        float n2 = fbm3(sp * 1.7 + vec3(31.0, 7.0, 13.0));
        // diatoms: powdery brown dust, plus a faint even haze at high cover
        float dia = algaePatch(n2, uAlgaeSand.x) * algaeDensity(uAlgaeSand.x) * 0.8 + uAlgaeSand.x * 0.2;
        albedo = mix(albedo, ALG_DIATOM, clamp(dia, 0.0, 1.0));
        // stray hair tufts (sand is a poor substrate)
        albedo = mix(albedo, ALG_HAIR, algaePatch(n1, uAlgaeSand.w) * 0.6);
        slimeMats(vWorld, n1, uAlgaeSand.y, uAlgaeSand.z, albedo, rough);
    }
#else
    // Rock detail levels: the mesh normals are smoothed over ~2 cm (tools/blender/rock_surface.py), and the relief
    // comes back here as procedural octaves with analytic gradients. Each octave fades out once its period spans fewer
    // than ~4 pixels (lodW), so a close camera sees pores, vugs and grit while a far one pays for two octaves only.
    // COLOR_0 (rock_surface.py): r cavity, g ridge, b open sky (SDF ambient occlusion), a broad blotch noise.
    float fp = length(fwidth(vWorld));   // cm per pixel (about two pixel widths)
    float fpL = uQuality >= 0.5 ? fp : 100.0;   // Low quality: lumps only
    float cavity = vColor.r, ridge = vColor.g, sky = vColor.b;
    rough = uRoughness;
    ao = (0.3 + 0.7 * sky) * (1.0 - 0.35 * cavity);
    vec3 op = vObj;            // detail coordinates: object space
    mat3 toW = mat3(uModel);   // rigid (no scale): object -> world for gradients and normals
    vec3 No = N * toW;         // world normal in object space (transpose of a rotation)
    vec3 relief = vec3(0.0);   // gradient of the relief height in object space (cm / cm)
    float pores = 0.0, grit = 0.5, lost = 0.0;
    // lumps (2 cm period): always on, and its value doubles as the mid-scale tone noise
    vec4 n0 = vnoiseD(op * 0.5 + vec3(1.7, 9.2, 4.4));
    relief += n0.yzw * (0.5 * 0.45);
    float mid = n0.x;
    float big = vColor.a;   // fbm3(vWorld * 0.05 + (3.1, 7.7, 1.3)), baked per vertex
    {
        // big holes (~1-2 cm across, sparse): what gives live rock its texture from across the room
        vec4 v0 = voids(op * 0.45 + vec3(2.0, 17.0, 9.0), 0.34 + (big - 0.5) * 0.4);
        pores = v0.x;
        relief -= v0.yzw * (0.45 * 0.8);
        // pores (~7 mm): rounded pits, denser in some blotches than others
        float w1 = lodW(1.4, fpL);
        lost += 1.0 - w1;
        if (w1 > 0.0) {
            vec4 v1 = voids(op * 1.4 + vec3(11.0, 5.0, 2.0), 0.3 + (big - 0.5) * 0.6);
            pores = max(pores, v1.x * (0.6 + 0.4 * cavity) * w1);
            relief -= v1.yzw * (1.4 * 0.22 * w1);
        }
        // vugs and boring holes (~3 mm)
        float w2 = lodW(3.6, fpL);
        lost += 1.0 - w2;
        if (w2 > 0.0) {
            vec4 v2 = voids(op * 3.6 + vec3(4.0, 13.0, 8.0), 0.35);
            pores = max(pores, v2.x * 0.8 * w2);
            relief -= v2.yzw * (3.6 * 0.08 * w2);
        }
        // grit (~1 mm) and micro grain (~0.4 mm): close-ups only
        float w3 = lodW(9.0, fpL);
        lost += 1.0 - w3;
        if (w3 > 0.0) {
            vec4 n3 = vnoiseD(op * 9.0 + vec3(7.0, 2.0, 19.0));
            relief += n3.yzw * (9.0 * 0.03 * w3);
            grit = mix(0.5, n3.x, w3);
            float w4 = lodW(23.0, fpL);
            if (w4 > 0.0) {
                vec4 n4 = vnoiseD(op * 23.0 + vec3(3.0, 29.0, 5.0));
                relief += n4.yzw * (23.0 * 0.009 * w4);
                grit = mix(grit, 0.5 * (grit + n4.x), w4);
            }
        }
    }
    // tilt the smooth normal by the relief slope (tangential part only)
    relief = toW * relief;
    N = normalize(N - (relief - N * dot(relief, N)));
    // detail the far view can no longer resolve turns into a slightly rougher, flatter-toned surface
    rough = mix(rough, min(rough + 0.12, 1.0), lost / 3.0);
    {
        // Live-rock character, independent of the algae covers below: broad bleached / grey limestone blotches,
        // porous pits, cavity AO and bright worn ridges, a faint coralline baseline (real live rock always carries
        // some) and a soft wet sheen.
        float lum = dot(albedo, vec3(0.3, 0.59, 0.11));
        float bl = smoothstep(0.5, 0.68, big + (mid - 0.5) * 0.25);
        float gr = smoothstep(0.46, 0.32, big);
        albedo = mix(albedo, ROCK_BLEACH * (0.6 + 3.0 * lum), bl * 0.5 + 0.28);
        albedo = mix(albedo, ROCK_GREY * (0.6 + 3.0 * lum), gr * 0.15);
        // thin olive film of turf / diatoms that live rock always carries, heavier on lit, up-facing crusts
        float film = smoothstep(0.3, 0.7, mid) * mix(0.3, 0.65, smoothstep(0.0, 0.8, N.y));
        albedo = mix(albedo, ROCK_FILM * (0.7 + 2.0 * lum), film * 0.34);
        // the baked albedo's coralline zones were mauve / purple; Tee's rock carries pink-red coralline: pull the blue
        // out of purple tones
        float purple = clamp((albedo.b - albedo.g) * 6.0, 0.0, 1.0) * clamp((albedo.r - albedo.g) * 6.0, 0.0, 1.0);
        albedo.b = mix(albedo.b, albedo.g * 1.15, purple * 0.7);
        // pores too small to resolve keep their average darkening, so the far tone matches the close one
        albedo *= (1.0 - 0.5 * pores * pores) * (1.0 - 0.07 * lost / 3.0) * (0.8 + 0.4 * grit) * (0.8 + 0.4 * mid) * (1.0 - 0.25 * cavity);
        albedo = mix(albedo, ROCK_BLEACH * 1.1, ridge * 0.2 * (1.0 - pores));
        ao *= 1.0 - 0.6 * pores * pores;   // deep pore bottoms see little of the water above
        // coralline crust in lit-but-shaded spots, with pale growth margins and its own fine bumps up close
        float cbn = vnoise(op * 0.13 + vec3(19.0, 3.0, 7.0));
        float cb = smoothstep(0.76, 0.86, cbn) * (1.0 - 0.6 * smoothstep(0.1, 0.7, N.y)) * smoothstep(0.15, 0.5, sky);
        float margin = cb * (1.0 - smoothstep(0.25, 0.8, cb));
        // mostly pink-red, as on Tee's rock; purple only in a few deep-shade patches
        albedo = mix(albedo, mix(ALG_CORAL_PINK, ALG_CORAL_PURPLE, smoothstep(0.6, 0.9, mid) * (1.0 - sky)) * (0.65 + 0.35 * grit), cb * 0.3 * (1.0 - pores));
        albedo = mix(albedo, vec3(0.62, 0.52, 0.58), margin * 0.3);
        rough = mix(rough, 0.42, 0.45 * smoothstep(0.45, 0.8, mid) * (1.0 - pores));
    }
    if (uDryRock > 0.0) {
        // bleached white aragonite fresh from the bag; a tan bacterial / diatom film comes in partway through the cure,
        // then the live-rock colour and coralline above take over
        float dn = vnoise(vWorld * 0.4 + vec3(2.0, 5.0, 9.0));
        vec3 dry = vec3(0.80, 0.77, 0.70) * (0.86 + 0.22 * dn);
        dry = mix(dry, vec3(0.42, 0.33, 0.20), 4.0 * uDryRock * (1.0 - uDryRock) * 0.6 * smoothstep(0.3, 0.7, dn));
        albedo = mix(albedo, dry, uDryRock);
        rough = mix(rough, 0.85, uDryRock);
    }
    float hair = tex.a; // baked hair-algae mask in the albedo alpha
    float top = smoothstep(0.1, 0.7, N.y);
    vec4 cv = mix(uAlgaeShade, uAlgaeTop, top);
    float hairC = max(uAlgaeTop.x, uAlgaeBloom) * top;
    float coralC = uAlgaeShade.x * mix(1.0, 0.25, top);
    if (hairC + coralC + cv.y + cv.z + cv.w > 0.001) {
        vec3 rp = op * 0.09;
        float n1 = fbm3(rp);
        float n2 = fbm3(rp * 1.9 + vec3(5.0, 23.0, 11.0));
        // coralline: pink/purple matte crust with pale growth margins
        float cr = algaePatch(n2, coralC);
        float edge = cr * (1.0 - smoothstep(0.35, 0.9, cr));
        vec3 crust = mix(ALG_CORAL_PINK, ALG_CORAL_PURPLE, smoothstep(0.35, 0.65, n1));
        albedo = mix(albedo, crust, cr * algaeDensity(coralC));
        albedo = mix(albedo, vec3(0.6, 0.5, 0.58), edge * 0.35);
        rough = mix(rough, 0.9, cr);
        // hair turf toward #3B5B2E on up-facing rock, hugging the baked hair mask
        float ht = algaePatch(n1 + (hair - 0.5) * 0.12, hairC) * algaeDensity(hairC) * (0.6 + 0.4 * hair);
        albedo = mix(albedo, ALG_HAIR, clamp(ht + hairC * 0.25 * hair, 0.0, 1.0));
        // bubble algae: a glossy dark-green tint in small clustered spots (no geometry)
        float bb = algaePatch(0.5 + (vnoise(op * 0.6) - 0.5) * 0.63, cv.w) * algaeDensity(cv.w);
        albedo = mix(albedo, ALG_BUBBLE, bb);
        rough = mix(rough, 0.15, bb);
        slimeMats(op, n1, cv.y, cv.z, albedo, rough);
    }
    // the animals and forams on top of whatever covers the rock
    if (uQuality >= 0.5) rockLife(op, fp, sky, No, albedo, rough);
#endif
#ifdef SAND
    if (uNormalStrength > 0.0) {
        vec3 tn = texture(uNormalMap, uv0 * uDetailScale).xyz * 2.0 - 1.0;
        tn.xy *= uNormalStrength;
        N = perturb(N, vWorld, uv0 * uDetailScale, normalize(tn));
    }
    if (uQuality >= 0.5) N = bumpH(N, vWorld, bumpHt); // uniform branch: derivatives stay defined
    // the cross-section against the glass is lit by light scattered through the bed and the pane: wrap it
    vec3 col = shadeLit(albedo, N, V, vWorld, rough, ao, 0.04, wall * 0.7, 1.0);
    if (glint > 0.0) {
        // sparkle: a tight specular lobe on the grain's own facet, from both fixtures (none in rock/fish shadow)
        for (int i = 0; i < 2; i++) {
            vec3 L;
            vec3 rad = fixtureLight(i, vWorld, L);
            float s = pow(max(dot(glintN, normalize(V + L)), 0.0), 90.0);
            col += rad * s * glint * 1.6 * gShadow;
        }
    }
#else
    // slight wrap: crusts and turf scatter light past the terminator
    vec3 col = shadeLit(albedo, N, V, vWorld, rough, ao, 0.04, 0.25, 1.0);
#endif
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), 1.0);
}
