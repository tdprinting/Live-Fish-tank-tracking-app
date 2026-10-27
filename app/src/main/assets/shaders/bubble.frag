#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Bubble algae cells (research 09 A6): "glossy dark-green spheres, mirror-like highlights". A thin, very smooth cell
// wall over dark green sap: GGX highlights from both fixtures, a rim that picks up the water's colour, and the sap a
// shade lighter toward the top where the light comes through. Variant (vColor.x) runs from bottle green to olive.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    float v = vColor.x;
    vec3 sap = mix(vec3(0.010, 0.050, 0.022), vec3(0.030, 0.060, 0.012), v);
    sap *= 0.8 + 0.5 * clamp(vColor.z * 0.5 + 0.5, 0.0, 1.0);
    vec3 col = shadeLit(sap, N, V, vWorld, 0.08, 1.0, 0.04, 0.3, 1.6);
    // the cell wall: mirror-like lobes and a fresnel rim of the lit water around it
    if (uQuality >= 0.5) col += wetCoat(N, V, vWorld, 0.05, 1.2);
    float rim = pow(1.0 - max(dot(N, V), 0.0), 4.0);
    col += rim * (uFog.rgb * 2.5 + uAmbientB.rgb) * (0.3 + 0.7 * lightIntensity());
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), 1.0);
}
