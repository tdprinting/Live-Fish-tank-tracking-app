#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Soft corals, mushrooms, macroalgae, algae cards (ARCHITECTURE §8.4 item 2): wrap diffuse, subsurface
// back-light by thickness (COLOR_0.r), rim, bleaching/browning, fluorescence driven by the blue fraction.
// CARD: alpha-tested hair-algae cards that grow root->tip (COLOR_0.b) with uAlgaeBloom.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAlbedo;
uniform vec4 uBaseColor;
uniform float uHasTexture;
uniform float uRoughness;
uniform vec3 uEmissive;     // material fluorescence colour
uniform float uExtension;
uniform float uBleach;
uniform vec3 uBleachTint;  // what bleached tissue fades to (per species)
uniform float uDead;       // 1 - living tissue: receding colony
uniform float uBrown;
uniform float uAlphaCutoff;
uniform vec3 uSssColor;

out vec4 fragColor;

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    float alpha = tex.a * uBaseColor.a;
#ifdef CARD
    if (uAlgaeBloom < 0.02 || vColor.b > uAlgaeBloom * 1.25) discard;
#endif
    if (uAlphaCutoff > 0.0 && alpha < uAlphaCutoff) discard;
    albedo = mix(albedo, uBleachTint, clamp(uBleach, 0.0, 1.0));
    // receding: patches of a dying colony melt away (world-space noise), the edge of the living tissue goes dull
    // a stony coral's skeleton (COLOR_0.a < 0.5) stays where the tissue has gone: bare white limestone
    float bare = 0.0;
    if (uDead > 0.001) {
        float n = clamp((vnoise(vWorld * 0.45) * 0.7 + vnoise(vWorld * 1.7 + 5.0) * 0.3 - 0.5) * 2.4 + 0.5, 0.0, 1.0);
        if (n < uDead * 1.02) {
            if (vColor.a > 0.5) discard;
            bare = 1.0;
        } else albedo = mix(vec3(0.55, 0.5, 0.42), albedo, smoothstep(uDead, uDead + 0.12, n));
    }
    albedo = mix(albedo, vec3(0.195, 0.102, 0.042), clamp(uBrown, 0.0, 1.0) * 0.4);
    if (bare > 0.5) albedo = vec3(0.80, 0.78, 0.72) * (0.85 + 0.25 * vnoise(vWorld * 3.0));

    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    // tissue micro-relief (polyp pores, wrinkles) where the mesh is smooth, Medium+; fades with pixel footprint
#ifndef CARD
    if (uQuality >= 0.5) {
        float fp = length(fwidth(vWorld));
        float relief = (vnoise(vWorld * 2.6) - 0.5) * 0.09 * (1.0 - smoothstep(0.15, 0.6, fp));
        if (uQuality >= 1.5) relief += (vnoise(vWorld * 7.0 + 3.0) - 0.5) * 0.03 * (1.0 - smoothstep(0.05, 0.25, fp));
        N = bumpH(N, vWorld, relief);
    }
#endif
    // view-angle terms (sheen, rim) use the smooth normal, so the micro-relief does not sparkle white up close
    vec3 Ng = normalize(vNormal);
    if (dot(Ng, V) < 0.0) Ng = -Ng;
    vec3 col = shadeLit(albedo, N, V, vWorld, uRoughness, 1.0, 0.03, 1.0, 0.6);
#ifndef CARD
    // mucus sheen: soft corals and mushrooms read wet in the tank
    col += wetCoat(Ng, V, vWorld, 0.3, 0.35) * gShadow;
#endif
    float thickness = clamp(vColor.r, 0.0, 1.0) * gShadow;   // no back-light glow inside a shadow
    for (int i = 0; i < 2; i++) {
        vec3 L;
        vec3 rad = fixtureAt(i, L);
        float bl = clamp(dot(V, -L), 0.0, 1.0);
        col += rad * uSssColor * albedo * (bl * bl * bl) * thickness;
    }
    float ndv = max(dot(Ng, V), 0.0);
    col += pow(1.0 - ndv, 2.5) * 0.35 * (uLightColor[0].rgb + uLightColor[1].rgb) * 0.5 * albedo;
    // fluorescence: emissive scaled by blue light and extension (bleached tissue loses it)
    col += uEmissive * uBlueFraction * lightIntensity() * mix(0.4, 1.0, uExtension) * (1.0 - uBleach) * (1.0 - bare) * 1.5;
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), 1.0);
}
