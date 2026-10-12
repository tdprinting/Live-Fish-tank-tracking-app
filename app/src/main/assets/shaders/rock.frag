#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Rock and sand PBR-lite (ARCHITECTURE §8.4 item 1): Lambert + GGX, normal map via a derivative
// cotangent frame (the GLBs carry no tangents), caustics, hair-algae bloom, Beer-Lambert + fog.
// SAND: tiled detail normal and the front-glass green film (COLOR_0.r).

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

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    float rough = uRoughness;
    float ao = 1.0;
#ifdef SAND
    albedo = mix(albedo, vec3(0.27, 0.35, 0.12), clamp(vColor.r, 0.0, 1.0) * 0.55);
#else
    vec2 ra = texture(uRoughAo, vUv).rg;
    rough = mix(uRoughness, ra.r, 0.7);
    ao = mix(1.0, ra.g, 0.85);
    // hair algae (albedo alpha mask) blooms toward #3B5B2E on up-facing rock
    float hair = tex.a;
    float bloom = uAlgaeBloom * smoothstep(0.1, 0.7, N.y);
    albedo = mix(albedo, vec3(0.043, 0.105, 0.027), clamp(bloom * (0.25 + 0.75 * hair), 0.0, 1.0));
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
