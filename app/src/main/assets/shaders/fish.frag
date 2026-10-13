#version 300 es
// A fish never darkens itself with its own contact shadow (lighting.glsl shadeLit).
#define NO_CONTACT_SHADOW 1
#include "common.glsl"
#include "lighting.glsl"
// Fish skin (ARCHITECTURE §8.4 items 5-6). Define FIN for the translucent two-sided fin sheet variant.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAlbedo;
uniform sampler2D uIridLut;
uniform vec4 uBaseColor;     // linear RGBA factor
uniform float uHasTexture;
uniform float uRoughness;
uniform float uIridStrength;
uniform float uDesat;
uniform float uIchSpots;     // 0..1 white spot coverage (ich)
uniform float uEyeCloud;
uniform float uPart;         // 0 body, 1 fin, 2 eye, 3 leg/antenna
uniform float uDead;
// Life stages (LifeStageVisuals.kt, additive): a second body texture painted on the same UV layout, mixed in by
// uStageMix while a fish grows from one stage's pattern into the next (or changes sex). 0 = unchanged.
uniform sampler2D uAlbedoStage;
uniform float uStageMix;

out vec4 fragColor;

// Procedural ich: sparse round white dots in texture space (size ~1-2 texels of a 140-cell grid).
float ichMask(vec2 uv) {
    vec2 g = uv * 140.0;
    vec2 cell = floor(g);
    float h = hash12(cell);
    float on = step(0.92 - uIchSpots * 0.3, h);
    vec2 c = vec2(hash12(cell + 7.1), hash12(cell + 3.7)) * 0.5 + 0.25;
    float r = mix(0.18, 0.34, hash12(cell + 1.3));
    float d = length(fract(g) - c);
    return on * (1.0 - smoothstep(r * 0.6, r, d));
}

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    if (uStageMix > 0.001 && uHasTexture > 0.5) tex = mix(tex, texture(uAlbedoStage, vUv), clamp(uStageMix, 0.0, 1.0));
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    float alpha = tex.a * uBaseColor.a;

    vec3 V = normalize(uCamPos.xyz - vWorld);
    vec3 N = normalize(vNormal);
#ifdef FIN
    if (!gl_FrontFacing) N = -N;
#endif
    if (dot(N, V) < 0.0) N = -N; // two-sided safety for thin parts

    // symptoms
    if (uIchSpots > 0.001 && uPart < 1.5) albedo = mix(albedo, vec3(0.92), ichMask(vUv) * min(uIchSpots * 3.0, 1.0));
    float lum = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
    albedo = mix(albedo, vec3(lum), clamp(uDesat, 0.0, 1.0));
    if (uPart > 1.5 && uPart < 2.5) albedo = mix(albedo, vec3(0.62, 0.64, 0.66), uEyeCloud * 0.85);

    float ndv = max(dot(N, V), 0.0);
    float specScale = mix(1.0, 0.25, uNight) * (1.0 - 0.7 * uDead);
#ifdef FIN
    vec3 col = shadeLit(albedo, N, V, vWorld, max(uRoughness, 0.4), 1.0, 0.03, 0.5, specScale * 0.5);
    // back-lit fins glow a little (thin membrane)
    vec3 Lb;
    vec3 radB = fixtureLight(0, vWorld, Lb) + fixtureLight(1, vWorld, Lb);
    col += albedo * radB * pow(clamp(dot(V, -Lb), 0.0, 1.0), 3.0) * 0.35;
    alpha *= 0.75 + 0.25 * ndv;
    if (uIchSpots > 0.001) col = mix(col, vec3(0.8) * lightIntensity(), ichMask(vUv * 0.5) * uIchSpots * 0.6);
    if (alpha < 0.03) discard;
#else
    vec3 col = shadeLit(albedo, N, V, vWorld, uRoughness, 1.0, 0.04, 0.15, specScale);
    // thin-film iridescence (LUT ramp by view angle) added to the specular response
    vec3 irid = toLinear(texture(uIridLut, vec2(ndv, 0.5)).rgb) * uIridStrength * (1.0 - uDesat);
    vec3 Lk;
    vec3 rad = fixtureLight(0, vWorld, Lk) + fixtureLight(1, vWorld, Lk);
    col += irid * rad * (0.35 + 0.65 * pow(1.0 - ndv, 2.0));
    // Fish were nearly invisible against the rock and the blue water. A soft camera-side fill lets the body
    // pattern read in the shade, and a stronger rim (light colour plus a little of the fish's own colour)
    // separates the silhouette from the background. Both follow the light, so night stays dark.
    vec3 lightSum = (uLightColor[0].rgb + uLightColor[1].rgb) * 0.5;
    col += albedo * 0.10 * (0.15 + lightIntensity());
    float rim = pow(1.0 - ndv, 2.2);
    // the rim is brightest on the top edge (light from the fixtures above) and picks up the water colour below
    float top = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
    col += rim * (0.40 * lightSum * (0.6 + 0.8 * top) + 0.30 * albedo * lightIntensity() + uFog.rgb * 0.6 * (1.0 - top));
    alpha = 1.0;
#endif
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), alpha);
}
