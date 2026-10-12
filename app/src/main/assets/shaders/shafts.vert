#version 300 es
#include "common.glsl"
// Light shafts (ARCHITECTURE §8.4 item 8): 6 vertical quads per fixture, billboarded about Y, fanning
// out with the 120 deg cone below the surface.

layout(location = 0) in vec4 aCorner;   // x -1..1 across, y 0 (surface) .. 1 (sand), z shaft index 0..5, w light index
layout(location = 1) in vec4 aShaft;    // x,z offset at the surface (cm), y width (cm), w seed

out vec2 vCoord;
out float vSeed;
out float vLight;
out vec3 vWorld;

void main() {
    int li = int(aCorner.w + 0.5);
    vec3 lp = uLightPos[li].xyz;
    float depth = aCorner.y;
    float sand = uParams2.w;
    float y = mix(uWaterLevel, sand, depth);
    // spread outward with depth (cone), drift slowly
    vec2 off = aShaft.xz * (1.0 + depth * 1.6) + vec2(sin(uTime * 0.07 + aShaft.w * 6.0), cos(uTime * 0.05 + aShaft.w * 4.0)) * 2.0;
    vec3 center = vec3(lp.x + off.x, y, clamp(lp.z + off.y, 1.0, uTank.z - 1.0));
    vec3 toCam = uCamPos.xyz - center;
    vec3 right = normalize(vec3(toCam.z, 0.0, -toCam.x) + vec3(1e-4, 0.0, 0.0));
    float w = aShaft.y * (1.0 + depth * 0.8);
    vec3 p = center + right * aCorner.x * w * 0.5;
    vCoord = vec2(aCorner.x, depth);
    vSeed = aShaft.w;
    vLight = float(li);
    vWorld = p;
    gl_Position = uViewProj * vec4(p, 1.0);
}
