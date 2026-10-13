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
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    float rough = uRoughness;
    float ao = 1.0;
#ifdef SAND
    albedo = mix(albedo, vec3(0.27, 0.35, 0.12), clamp(vColor.r, 0.0, 1.0) * 0.55);
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
        vec3 tn = texture(uNormalMap, vUv * uDetailScale).xyz * 2.0 - 1.0;
        tn.xy *= uNormalStrength;
        N = perturb(N, vWorld, vUv * uDetailScale, normalize(tn));
    }
    vec3 col = shadeLit(albedo, N, V, vWorld, rough, ao, 0.04, 0.0, 1.0);
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), 1.0);
}
