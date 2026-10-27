#version 300 es
#include "common.glsl"
// Bubble algae (BubbleAlgaeDraw; research 09 A6): one unit sphere instanced on the rock. Each instance sits on its rock
// spot, flattened a little against the rock and sunk a third into it; iPhaseVar.x is its growth rank, so the cells
// up to uCount show and the next one swells in as the cover rises.

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec4 aColor;
layout(location = 4) in vec4 iPosScale;   // rock spot (world cm), radius (cm)
layout(location = 5) in vec4 iQuat;       // local +Y -> rock normal
layout(location = 6) in vec2 iPhaseVar;   // growth rank, colour variant 0..1

uniform float uCount;      // fractional count of cells showing (AlgaeVisuals.bubbleCount)

out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec4 vColor;

const float FLAT = 0.85;   // a turgid cell leaning on the rock is a little squat

void main() {
    float grow = clamp(uCount - iPhaseVar.x, 0.0, 1.0);
    float r = iPosScale.w * mix(0.35, 1.0, grow) * step(0.001, grow);
    vec3 lp = vec3(aPos.x, aPos.y * FLAT + 0.6, aPos.z) * r;
    vec3 p = iPosScale.xyz + qrot(iQuat, lp);
    vNormal = qrot(iQuat, normalize(vec3(aNormal.x, aNormal.y / FLAT, aNormal.z)));
    vWorld = p;
    vUv = aUv;
    vColor = vec4(iPhaseVar.y, grow, aPos.y, 1.0);
    gl_Position = uViewProj * vec4(p, 1.0);
}
