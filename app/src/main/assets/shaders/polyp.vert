#version 300 es
#include "common.glsl"
// Instanced polyps (ARCHITECTURE §8.4 item 3): GSP, zoas, clove, Kenya tufts, organ-pipe polyps, the
// feather-duster crown. Prototype in cm, base at the origin, axis +Y; COLOR_0.b = tentacle hinge 0..1.

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec4 aColor;
layout(location = 4) in vec4 iPosScale;   // world position (cm), scale
layout(location = 5) in vec4 iQuat;       // local +Y -> surface normal
layout(location = 6) in vec2 iPhaseVar;   // phase 0..1, colour variant 0..1

uniform mat4 uModel;        // identity for coral GLBs (instances are in world cm); places the duster crown
uniform float uExtension;   // colony extension 0..1
uniform vec3 uSwayDir;
uniform float uSwayAmp;     // cm at the tentacle tips
// tree mode (Kenya sprigs, extras.swayMode = 1): the instance rides its branch's treeSway (same uniforms as the body)
uniform float uSwayMode;
uniform vec3 uBase;
uniform float uHeight;
uniform float uPhase;
uniform float uBodySwayAmp;
uniform vec4 uWake;

out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec4 vColor;
out float vVariant;
out float vOpen;

void main() {
    float phase = iPhaseVar.x * 6.2831;
    float open = clamp(uExtension * (0.6 + 0.4 * sin(uTime * 0.3 + phase)), 0.0, 1.0);
    float hinge = aColor.b;
    vec3 p = aPos;
    // close: fold the tentacles in toward the axis and down, and twist about the axis
    float fold = (1.0 - open) * hinge;
    p.xz *= 1.0 - 0.75 * fold;
    p.y *= 1.0 - 0.5 * fold;
    float ang = (1.0 - open) * 1.2 * hinge;
    float c = cos(ang), s = sin(ang);
    p.xz = vec2(c * p.x - s * p.z, s * p.x + c * p.z);
    vec3 n = aNormal;
    n.xz = vec2(c * n.x - s * n.z, s * n.x + c * n.z);

    vec3 w = iPosScale.xyz + qrot(iQuat, p * iPosScale.w);
    if (uSwayMode > 0.5) {
        // ride the branch: the body's extension shrink and tree sway at the instance base (uModel is a pure
        // translation for coral GLBs, so a world-space offset can be added before it), then a quick fringe flutter
        vec3 ip = (uModel * vec4(iPosScale.xyz, 1.0)).xyz;
        vec3 shrunk = uBase + (ip - uBase) * mix(0.55, 1.0, clamp(uExtension, 0.0, 1.0));
        w += shrunk - ip + treeSway(shrunk, uBase, uHeight, uPhase, uSwayDir, uBodySwayAmp, uWake);
        float fl = sin(uTime * 5.7 + phase * 3.0 + dot(iPosScale.xz, vec2(0.9, 0.7)));
        w += uSwayDir * fl * uSwayAmp * hinge * iPosScale.w;
    } else {
        // sway with the flow (coherent phase across a mat)
        float sw = sin(uTime * 1.6 + phase + dot(iPosScale.xz, vec2(0.15, 0.1)));
        w += uSwayDir * sw * uSwayAmp * hinge * iPosScale.w;
    }
    vec4 world = uModel * vec4(w, 1.0);
    vWorld = world.xyz;
    vNormal = normalize(mat3(uModel) * qrot(iQuat, n));
    vUv = aUv;
    vColor = aColor;
    vVariant = iPhaseVar.y;
    vOpen = open;
    gl_Position = uViewProj * world;
}
