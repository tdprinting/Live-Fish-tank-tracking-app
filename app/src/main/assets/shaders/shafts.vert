#version 300 es
#include "common.glsl"
// Light shafts (ARCHITECTURE §8.4 item 8): 6 vertical quads per fixture, billboarded about Y, fanning
// out with the 120 deg cone below the surface; index 6 is the beam through the air from the lens down to the water,
// index 7 a camera-facing glow halo on the lens.

layout(location = 0) in vec4 aCorner;   // x -1..1 across, y 0 (top) .. 1 (bottom), z quad index 0..7, w light index
layout(location = 1) in vec4 aShaft;    // x,z offset at the surface (cm), y width (cm), w seed

out vec2 vCoord;
out float vSeed;
out float vLight;
out vec3 vWorld;
out float vKind;

void main() {
    int li = int(aCorner.w + 0.5);
    vec3 lp = uLightPos[li].xyz;
    vKind = aCorner.z;
    vSeed = aShaft.w;
    vLight = float(li);
    vCoord = vec2(aCorner.x, aCorner.y);
    if (aCorner.z > 6.5) {
        // halo: a square facing the eye just under the lens
        vec3 right = vec3(uView[0][0], uView[1][0], uView[2][0]);
        vec3 up = vec3(uView[0][1], uView[1][1], uView[2][1]);
        vec3 c = lp - vec3(0.0, 1.2, 0.0);
        vec3 p = c + (right * aCorner.x + up * (aCorner.y * 2.0 - 1.0)) * 9.0 + normalize(uCamPos.xyz - c) * 2.0;
        vWorld = p;
        gl_Position = uViewProj * vec4(p, 1.0);
        return;
    }
    if (aCorner.z > 5.5) {
        // beam in the air: from the lens down to the water, widening with the 120 deg cone (billboarded about Y)
        float yb = mix(lp.y - 1.0, uWaterLevel, aCorner.y);
        vec3 cb = vec3(lp.x, yb, lp.z);
        vec3 tc = uCamPos.xyz - cb;
        vec3 rb = normalize(vec3(tc.z, 0.0, -tc.x) + vec3(1e-4, 0.0, 0.0));
        float wb = mix(6.0, 2.0 * 1.2 * (lp.y - uWaterLevel), aCorner.y);
        vec3 p = cb + rb * aCorner.x * wb * 0.5;
        vWorld = p;
        gl_Position = uViewProj * vec4(p, 1.0);
        return;
    }
    float depth = aCorner.y;
    float sand = uParams2.w;
    float y = mix(uWaterLevel, sand, depth);
    // spread outward with depth (cone), drift slowly
    vec2 off = aShaft.xz * (1.0 + depth * 1.6) + vec2(sin(uTime * 0.07 + aShaft.w * 6.0), cos(uTime * 0.05 + aShaft.w * 4.0)) * 2.0;
    // refraction at the moving surface tilts each shaft: offset grows with depth below the water line
    off += surfaceSlope(lp.xz + aShaft.xz) * (uWaterLevel - y) * 0.6;
    vec3 center = vec3(lp.x + off.x, y, clamp(lp.z + off.y, 1.0, uTank.z - 1.0));
    vec3 toCam = uCamPos.xyz - center;
    vec3 right = normalize(vec3(toCam.z, 0.0, -toCam.x) + vec3(1e-4, 0.0, 0.0));
    float w = aShaft.y * (1.0 + depth * 0.8);
    vec3 p = center + right * aCorner.x * w * 0.5;
    vWorld = p;
    gl_Position = uViewProj * vec4(p, 1.0);
}
