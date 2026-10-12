#version 300 es
#include "common.glsl"
// GL_POINTS particles (ARCHITECTURE §8.4 item 9): suspended particulate, detritus, bubbles and food.
// Positions are advected on the CPU; size is a world size (cm) projected to pixels, clamped to a px range.

layout(location = 0) in vec4 aPosSize;  // xyz cm, w size (cm)
layout(location = 1) in vec4 aColor;    // linear rgb, alpha

uniform float uViewportH;
uniform vec2 uSizeClamp;                // min/max point size (px)

out vec4 vColor;
out vec3 vWorld;

void main() {
    vec4 clip = uViewProj * vec4(aPosSize.xyz, 1.0);
    gl_Position = clip;
    float px = aPosSize.w * uProj[1][1] * uViewportH * 0.5 / max(clip.w, 0.1);
    gl_PointSize = clamp(px, uSizeClamp.x, uSizeClamp.y);
    vColor = aColor;
    vWorld = aPosSize.xyz;
}
