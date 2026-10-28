#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Polyp shading: wrap diffuse, per-instance tint jitter, fluorescent emissive scaled by the blue fraction
// (GSP pop under actinics), optional alpha test (clove / duster feathers).

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;
in float vVariant;
in float vOpen;
in float vTip;
in float vGlow;
in float vRing;

uniform sampler2D uAlbedo;
uniform vec4 uBaseColor;
uniform float uHasTexture;
uniform float uRoughness;
uniform vec3 uEmissive;
uniform float uAlphaCutoff;
uniform float uBleach;
uniform vec3 uBleachTint;  // what bleached tissue fades to (per species)
uniform float uDead;       // 1 - living tissue: receding colony
uniform float uBrown;

out vec4 fragColor;

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    float alpha = tex.a * uBaseColor.a;
    if (uAlphaCutoff > 0.0) {
        // (duster feathers) mipmapping averages the fine barbs' alpha down, so as the crown shrinks on screen the
        // alpha test ate them into a shimmering hatch / moire: scale alpha back up by the mip level being sampled
        if (uHasTexture > 0.5) {
            vec2 tc = vUv * vec2(textureSize(uAlbedo, 0));
            vec2 d = max(abs(dFdx(tc)), abs(dFdy(tc)));
            alpha *= 1.0 + 0.25 * max(0.0, log2(max(d.x, d.y)));
        }
        if (alpha < uAlphaCutoff) discard;
    }
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    albedo *= 0.85 + 0.3 * vVariant;
    // bubble-tip anemone: magenta tips on swollen bulbs (Tee's photos 2026-10-15)
    albedo = mix(albedo, vec3(0.95, 0.12, 0.5), vTip * 0.85);
    albedo = mix(albedo, vec3(0.86, 0.84, 0.78), vRing * 0.65);
    albedo = mix(albedo, uBleachTint, clamp(uBleach, 0.0, 1.0));
    // receding: patches of a dying colony melt away (world-space noise), the edge of the living tissue goes dull
    if (uDead > 0.001) {
        float n = clamp((vnoise(vWorld * 0.45) * 0.7 + vnoise(vWorld * 1.7 + 5.0) * 0.3 - 0.5) * 2.4 + 0.5, 0.0, 1.0);
        if (n < uDead * 1.02) discard;
        albedo = mix(vec3(0.55, 0.5, 0.42), albedo, smoothstep(uDead, uDead + 0.12, n));
    }
    albedo = mix(albedo, vec3(0.195, 0.102, 0.042), clamp(uBrown, 0.0, 1.0) * 0.4);
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    vec3 col = shadeLit(albedo, N, V, vWorld, uRoughness, 1.0, 0.03, 0.8, 0.5);
    // glossy oral discs / tentacle tips (High: second, tighter lobe)
    if (uQuality >= 1.5) col += wetCoat(N, V, vWorld, 0.28, 0.3) * gShadow;
    col += uEmissive * (0.4 + 2.4 * uBlueFraction) * lightIntensity() * (0.3 + 0.7 * vOpen) * (1.0 - uBleach) * vGlow;
    col += vec3(0.9, 0.1, 0.55) * vTip * (0.15 + 0.9 * uBlueFraction) * lightIntensity() * (1.0 - uBleach);
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), 1.0);
}
