#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Equipment, rim, back glass, emitters (ARCHITECTURE §8.4 item 7 back glass: dark, low-roughness
// reflection of the two key lights). Blended materials (hose, feed ring) output straight alpha.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAlbedo;
uniform vec4 uBaseColor;
uniform float uHasTexture;
uniform float uRoughness;
uniform vec3 uEmissive;       // linear, already scaled (emitters follow the light colour)
uniform float uAlphaCutoff;   // > 0: alpha test

out vec4 fragColor;

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    float alpha = tex.a * uBaseColor.a;
    if (uAlphaCutoff > 0.0 && alpha < uAlphaCutoff) discard;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    vec3 col = shadeLit(albedo, N, V, vWorld, uRoughness, 1.0, 0.04, 0.0, 1.0);
    col += uEmissive;
    // things above the water (fixtures, rim) are not in the water column; applyWater handles that
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), alpha);
}
