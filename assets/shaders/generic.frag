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
uniform float uLedDies;       // 1: an AI Prime lens (multi-colour LED cluster under the optic), set by TankRenderer

out vec4 fragColor;

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    float alpha = tex.a * uBaseColor.a;
    if (uAlphaCutoff > 0.0 && alpha < uAlphaCutoff) discard;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    // moulded-plastic / anodised micro texture on equipment below the water (High), fades with distance
    if (uQuality >= 1.5) {
        float fp = length(fwidth(vWorld));
        N = bumpH(N, vWorld, (vnoise(vWorld * 4.0) - 0.5) * 0.02 * (1.0 - smoothstep(0.08, 0.3, fp)));
    }
    vec3 col = shadeLit(albedo, N, V, vWorld, uRoughness, 1.0, 0.04, 0.0, 1.0);
    if (uLedDies > 0.5) {
        // AI Prime HD: the lens mesh is the LED cluster itself (16 dies, ~1.2 cm pitch). Each die keeps its channel
        // colour (white, blue, royal, red, green, violet, UV), scaled by the live intensity; at night only the moon
        // (blue) dies glow faintly. Saturated cores with a whiter hot centre, so they read as coloured LEDs.
        int li = distance(vWorld.xz, uLightPos[0].xz) < distance(vWorld.xz, uLightPos[1].xz) ? 0 : 1;
        vec2 d = vWorld.xz - uLightPos[li].xz;
        float h = hash12(floor(d / 1.18 + 0.5) + float(li) * 7.0);
        vec3 dc = h < 0.25 ? vec3(1.0, 0.92, 0.85) : h < 0.5 ? vec3(0.1, 0.45, 1.0) : h < 0.75 ? vec3(0.12, 0.16, 1.0)
            : h < 0.81 ? vec3(1.0, 0.12, 0.06) : h < 0.87 ? vec3(0.15, 1.0, 0.25) : h < 0.94 ? vec3(0.5, 0.12, 1.0) : vec3(0.3, 0.06, 0.75);
        float on = uNight > 0.5 ? (h >= 0.25 && h < 0.75 ? 0.06 : 0.0) : 1.0;
        float level = uLightColor[li].w;
        col = albedo * 0.02 + dc * on * (0.12 + 1.6 * level);
    } else {
        col += uEmissive;
    }
    // things above the water (fixtures, rim) are not in the water column; applyWater handles that
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), alpha);
}
