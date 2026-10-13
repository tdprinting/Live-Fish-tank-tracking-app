#version 300 es
#include "common.glsl"
// GL_POINTS particles (ARCHITECTURE §8.4 item 9): suspended particulate, detritus, bubbles, food and action bursts.
// Positions are advected on the CPU; size is a world size (cm) projected to pixels, clamped to a px range.
// aShape picks the sprite drawn in the fragment shader (0 = the plain soft dot; see particles.frag and
// FoodVisuals.kt). A VAO that does not feed location 2 gets the GL default (0, 0, 0, 1): the plain dot.

layout(location = 0) in vec4 aPosSize;  // xyz cm, w size (cm)
layout(location = 1) in vec4 aColor;    // linear rgb, alpha
layout(location = 2) in vec4 aShape;    // x shape id, y seed 0..1, z screen angle (rad), w shape parameter

uniform float uViewportH;
uniform vec2 uSizeClamp;                // min/max point size (px)

out vec4 vColor;
out vec3 vWorld;
flat out float vShapeId;
out vec3 vShape;                        // seed, angle, parameter
out float vPx;

void main() {
    vec4 clip = uViewProj * vec4(aPosSize.xyz, 1.0);
    gl_Position = clip;
    float px = aPosSize.w * uProj[1][1] * uViewportH * 0.5 / max(clip.w, 0.1);
    gl_PointSize = clamp(px, uSizeClamp.x, uSizeClamp.y);
    vColor = aColor;
    vWorld = aPosSize.xyz;
    vShapeId = aShape.x;
    vShape = aShape.yzw;
    vPx = gl_PointSize;
}
