#version 300 es
#include "common.glsl"
// Static geometry: rock, sand, tank, equipment. With SOFTCORAL defined: soft-coral / macro / algae-card
// sway and extension (ARCHITECTURE §8.4 item 2). Coral COLOR_0: R thickness, G sway phase, B sway weight, A softness.

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec4 aColor;

uniform mat4 uModel;
#ifdef SOFTCORAL
uniform vec3 uBase;        // attachment point (world cm)
uniform float uHeight;     // colony height (cm)
uniform float uExtension;  // 0 retracted .. 1 open
uniform float uPhase;      // per-colony sway phase
uniform vec3 uSwayDir;     // unit, horizontal flow direction
uniform float uSwayAmp;    // cm at the tips (0.08 x height x flow; tree mode: 0.035 x height x flow)
uniform float uSwayMode;   // 0 legacy sway, 1 branching tree (treeSway in common.glsl; Kenya)
uniform vec4 uWake;        // tree mode: nearest fish (xyz world cm) and ripple strength (w 0..1)
#endif
#ifdef SAND
// Dynamic sand bed (SIMULATION §16, SandFx): r height offset (cm), g freshness, b detritus; xz / uSandExtent -> uv.
uniform sampler2D uSandMap;
uniform float uSandOn;
uniform vec2 uSandExtent;
#endif

out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec4 vColor;
#if defined(ROCK) || defined(GSPMAT)
out vec3 vObj;   // object-space position: rock.frag pins its detail to the rock
#endif

void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vec3 p = w.xyz;
#ifdef SOFTCORAL
    // COLOR_0.a: how soft the tissue is (1 soft coral; 0 a stony coral's skeleton, which neither shrinks nor sways)
    float soft = aColor.a;
    // extension: shrink toward the base (retracted colonies are ~55% size)
    p = uBase + (p - uBase) * (1.0 - soft * (1.0 - mix(0.55, 1.0, clamp(uExtension, 0.0, 1.0))));
    float h = clamp((p.y - uBase.y) / max(uHeight, 1.0), 0.0, 1.0);
    if (uSwayMode > 0.5) {
        p += soft * treeSway(p, uBase, uHeight, uPhase, uSwayDir, uSwayAmp, uWake);
    } else {
        float weight = max(aColor.b, h * 0.5) * soft;
        float ph = uTime * 1.3 + p.y * 0.15 + uPhase + aColor.g * 6.2831;
        p += uSwayDir * sin(ph) * uSwayAmp * weight;
        p.y -= 0.15 * uSwayAmp * weight * (0.5 + 0.5 * sin(ph * 1.7));
    }
#endif
#ifdef SAND
    // pits, mounds and trails: lift the top surface only (the skirt sits at y = -0.5)
    if (uSandOn > 0.5 && p.y > 0.5) {
        vec2 suv = clamp(p.xz / max(uSandExtent, vec2(1.0)), 0.0, 1.0);
        p.y += textureLod(uSandMap, suv, 0.0).r;
    }
#endif
    vNormal = normalize(mat3(uModel) * aNormal);
#ifdef GSPMAT
    // the stolon mat is a skin about a millimetre thick over the rock it copies
    p += vNormal * 0.07;
#endif
    vWorld = p;
    vUv = aUv;
    vColor = aColor;
#if defined(ROCK) || defined(GSPMAT)
    vObj = aPos;
#endif
    gl_Position = uViewProj * vec4(p, 1.0);
}
