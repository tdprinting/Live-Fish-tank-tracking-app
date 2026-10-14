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

uniform sampler2D uAlbedo;
uniform vec4 uBaseColor;
uniform float uHasTexture;
uniform float uRoughness;
uniform vec3 uEmissive;
uniform float uAlphaCutoff;
uniform float uBleach;
uniform float uBrown;

out vec4 fragColor;

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    float alpha = tex.a * uBaseColor.a;
    if (uAlphaCutoff > 0.0 && alpha < uAlphaCutoff) discard;
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    albedo *= 0.85 + 0.3 * vVariant;
    albedo = mix(albedo, vec3(0.89), clamp(uBleach, 0.0, 1.0));
    albedo = mix(albedo, vec3(0.195, 0.102, 0.042), clamp(uBrown, 0.0, 1.0) * 0.4);
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    vec3 col = shadeLit(albedo, N, V, vWorld, uRoughness, 1.0, 0.03, 0.8, 0.5);
    // glossy oral discs / tentacle tips (High: second, tighter lobe)
    if (uQuality >= 1.5) col += wetCoat(N, V, vWorld, 0.28, 0.3) * shadowAt(vWorld, N);
    col += uEmissive * (0.4 + 2.4 * uBlueFraction) * lightIntensity() * (0.3 + 0.7 * vOpen) * (1.0 - uBleach);
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), 1.0);
}
