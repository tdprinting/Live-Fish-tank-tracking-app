#version 300 es
#include "common.glsl"
// Marine snow: drifting particulate animated entirely on the GPU (no vertex buffer; position from gl_VertexID and
// time), so it costs no CPU per frame. Flakes sink slowly, wander with the flow and wrap from the sand back to the
// surface. Size is a world size (cm) projected to pixels; sub-pixel flakes fade instead of shrinking below 1 px.

uniform float uViewportH;

out float vAlpha;
out float vSeed;
out vec3 vWorld;

void main() {
    float id = float(gl_VertexID);
    float hx = hash12(vec2(id, 1.7));
    float hy = hash12(vec2(id * 1.31, 9.3));
    float hz = hash12(vec2(id * 0.73, 4.1));
    float hs = hash12(vec2(id * 2.17, 7.7));
    float sand = uParams2.w;
    vec3 box = vec3(uTank.x - 2.0, uWaterLevel - sand - 1.5, uTank.z - 2.0);
    float sink = mix(0.25, 0.9, hs);                         // cm/s
    float y = fract(hy - uTime * sink / box.y);
    vec3 p = vec3(1.0 + hx * box.x, sand + 0.75 + y * box.y, 1.0 + hz * box.z);
    // slow wander with the flow (bigger near the top where the return pump pushes)
    float w = 0.6 + 0.8 * y;
    p.x += sin(uTime * 0.21 + id * 0.37) * 1.8 * w * uFlowStrength;
    p.z += cos(uTime * 0.17 + id * 0.53) * 1.1 * w;
    p.y += sin(uTime * 0.5 + id) * 0.25;
    p.x = clamp(p.x, 0.5, uTank.x - 0.5);
    p.z = clamp(p.z, 0.5, uTank.z - 0.5);
    vec4 clip = uViewProj * vec4(p, 1.0);
    gl_Position = clip;
    float sizeCm = mix(0.05, 0.16, hash12(vec2(id, 3.3)));
    float px = sizeCm * uProj[1][1] * uViewportH * 0.5 / max(clip.w, 0.1);
    gl_PointSize = clamp(px, 1.0, 7.0);
    // fade at the wrap (sand / surface), very close to the lens, and for sub-pixel flakes
    vAlpha = smoothstep(0.0, 0.05, y) * smoothstep(1.0, 0.93, y) * smoothstep(1.5, 6.0, clip.w) * clamp(px * 1.5, 0.25, 1.0);
    vSeed = hs;
    vWorld = p;
}
