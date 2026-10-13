#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Rock and sand PBR-lite (ARCHITECTURE §8.4 item 1): Lambert + GGX, normal map via a derivative
// cotangent frame (the GLBs carry no tangents), caustics, hair-algae bloom, Beer-Lambert + fog.
// SAND: tiled detail normal and the front-glass green film (COLOR_0.r).
// Algae cover (research 09 A6) from the Frame UBO as patchy world-space noise masks: sand gets diatom dust,
// red cyano mats and khaki dino strands; rock tops hair turf, cyano and dinos; shaded rock coralline crust.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAlbedo;
uniform sampler2D uNormalMap;
uniform sampler2D uRoughAo;
uniform vec4 uBaseColor;
uniform float uHasTexture;
uniform float uRoughness;
uniform float uNormalStrength; // 0 disables the normal map (Low quality)
uniform float uDetailScale;    // normal-map uv multiplier
#ifdef SAND
// Dynamic sand bed (SIMULATION §16, SandFx): r height offset (cm), g freshness, b detritus.
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

// Mean curvature sign of the smooth vertex normal across the pixel quad (1/cm; > 0 convex ridge, < 0 cavity).
float curvature(vec3 n, vec3 p) {
    vec3 sx = dFdx(p), sy = dFdy(p);
    return dot(dFdx(n), sx) / max(dot(sx, sx), 1e-6) + dot(dFdy(n), sy) / max(dot(sy, sy), 1e-6);
}

vec3 hash33(vec3 p) { return vec3(hash13(p), hash13(p + 19.19), hash13(p + 47.31)); }

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
const vec3 ALG_CORAL_PINK = vec3(0.45, 0.10, 0.32);
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
            float deep = smoothstep(uParams2.w + 0.5, -0.5, wp2.y);
            albedo *= mix(1.0, 0.85, deep * wall);
            rough = mix(rough, rough * 0.7, deep * wall);
            float worm = 1.0 - abs(2.0 * vnoise(vec3(wp2.x * 0.32, wp2.y * 0.85, 11.0)) - 1.0);
            float pod = smoothstep(0.8, 0.86, vnoise(vec3(wp2 * 0.75, 23.0)));
            // only a few stretches of the pane have tunnels, and they settle into the sand tone at a distance
            float colony = smoothstep(0.55, 0.7, vnoise(vec3(wp2.x * 0.06, 0.0, 41.0)));
            float burrow = max(smoothstep(0.92, 0.96, worm) * colony, pod) * smoothstep(uParams2.w + 0.2, uParams2.w - 1.0, wp2.y);
            albedo *= 1.0 - 0.6 * burrow * wall * (1.0 - smoothstep(0.1, 0.35, fp));
            float film = smoothstep(uParams2.w - 0.3, uParams2.w + 1.8, wp2.y) * (0.3 + 0.7 * vnoise(vec3(wp2 * 0.4, 31.0)));
            albedo = mix(albedo, vec3(0.16, 0.17, 0.07), film * wall * 0.3);
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
    vec2 ra = texture(uRoughAo, vUv).rg;
    rough = mix(uRoughness, ra.r, 0.7);
    ao = mix(1.0, ra.g, 0.85);
    {
        // Live-rock character, independent of the algae covers below: broad bleached / grey limestone blotches,
        // porous pits (dark, bumped), cavity AO and bright worn ridges from the vertex-normal curvature, a faint
        // coralline baseline (real live rock always carries some) and a soft wet sheen.
        float big = fbm3(vWorld * 0.05 + vec3(3.1, 7.7, 1.3));
        float mid = vnoise(vWorld * 0.33 + vec3(1.7, 9.2, 4.4));
        float lum = dot(albedo, vec3(0.3, 0.59, 0.11));
        float bl = smoothstep(0.5, 0.68, big + (mid - 0.5) * 0.25);
        float gr = smoothstep(0.46, 0.32, big);
        albedo = mix(albedo, ROCK_BLEACH * (0.6 + 3.0 * lum), bl * 0.5 + 0.28);
        albedo = mix(albedo, ROCK_GREY * (0.6 + 3.0 * lum), gr * 0.15);
        // thin olive film of turf / diatoms that live rock always carries, heavier on lit, up-facing crusts
        float film = smoothstep(0.35, 0.75, mid) * mix(0.25, 0.55, smoothstep(0.0, 0.8, N.y));
        albedo = mix(albedo, ROCK_FILM * (0.7 + 2.0 * lum), film * 0.22);
        float cv = clamp(curvature(normalize(vNormal), vWorld) * 2.5, -1.0, 1.0);
        float cavity = max(-cv, 0.0);
        float ridge = max(cv, 0.0);
        float pitN = vnoise(vWorld * 0.85 + vec3(11.0, 5.0, 2.0));
        float pt = 0.6 + (big - 0.5) * 0.5; // denser pitting in some blotches than others
        float pits = smoothstep(pt, pt + 0.18, pitN) * (0.6 + 0.4 * cavity);
        if (uQuality >= 1.5) pits = max(pits, smoothstep(0.7, 0.86, vnoise(vWorld * 2.1 + vec3(4.0, 13.0, 8.0))) * 0.7);
        bumpHt = -pits * 0.35;
        albedo *= 1.0 - 0.55 * pits;
        albedo = mix(albedo, ROCK_BLEACH * 1.1, ridge * 0.18 * (1.0 - pits));
        ao *= (1.0 - 0.5 * cavity) * (1.0 - 0.35 * pits);
        float cb = smoothstep(0.6, 0.72, fbm3(vWorld * 0.11 + vec3(19.0, 3.0, 7.0))) * (1.0 - 0.6 * smoothstep(0.1, 0.7, N.y));
        albedo = mix(albedo, mix(ALG_CORAL_PINK, ALG_CORAL_PURPLE, mid) * 0.75, cb * 0.45 * (1.0 - pits));
        rough = mix(rough, 0.42, 0.45 * smoothstep(0.45, 0.8, mid) * (1.0 - pits));
    }
    float hair = tex.a; // baked hair-algae mask in the albedo alpha
    float top = smoothstep(0.1, 0.7, N.y);
    vec4 cv = mix(uAlgaeShade, uAlgaeTop, top);
    float hairC = max(uAlgaeTop.x, uAlgaeBloom) * top;
    float coralC = uAlgaeShade.x * mix(1.0, 0.25, top);
    if (hairC + coralC + cv.y + cv.z + cv.w > 0.001) {
        vec3 rp = vWorld * 0.09;
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
        float bb = algaePatch(0.5 + (vnoise(vWorld * 0.6) - 0.5) * 0.63, cv.w) * algaeDensity(cv.w);
        albedo = mix(albedo, ALG_BUBBLE, bb);
        rough = mix(rough, 0.15, bb);
        slimeMats(vWorld, n1, cv.y, cv.z, albedo, rough);
    }
#endif
    if (uNormalStrength > 0.0) {
        vec3 tn = texture(uNormalMap, uv0 * uDetailScale).xyz * 2.0 - 1.0;
        tn.xy *= uNormalStrength;
        N = perturb(N, vWorld, uv0 * uDetailScale, normalize(tn));
    }
    if (uQuality >= 0.5) N = bumpH(N, vWorld, bumpHt); // uniform branch: derivatives stay defined
#ifdef SAND
    // the cross-section against the glass is lit by light scattered through the bed and the pane: wrap it
    vec3 col = shadeLit(albedo, N, V, vWorld, rough, ao, 0.04, wall * 0.7, 1.0);
    if (glint > 0.0) {
        // sparkle: a tight specular lobe on the grain's own facet, from both fixtures
        for (int i = 0; i < 2; i++) {
            vec3 L;
            vec3 rad = fixtureLight(i, vWorld, L);
            float s = pow(max(dot(glintN, normalize(V + L)), 0.0), 90.0);
            col += rad * s * glint * 1.6;
        }
    }
#else
    // slight wrap: crusts and turf scatter light past the terminator
    vec3 col = shadeLit(albedo, N, V, vWorld, rough, ao, 0.04, 0.25, 1.0);
#endif
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), 1.0);
}
