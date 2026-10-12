#version 300 es
#include "common.glsl"
// Spline skinning on the GPU (ARCHITECTURE §8.4 item 4, SIMULATION §4.8). Models are unit length, facing
// +X, snout at x = +0.5; COLOR_0.a = spine coordinate u (0 snout .. 1 tail), COLOR_0.g = gill mask,
// COLOR_0.b = fin hinge (0 base .. 1 tip).

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec4 aColor;

uniform vec4 uSpine[16];   // xyz world cm, head -> tail
uniform vec4 uFrame[16];   // quaternion: model X(forward)/Y(up)/Z(side) -> world
uniform float uScale;      // body length (cm)
uniform vec4 uSwim;        // x phase (cycles), y amp (BL), z waveLen, w appendage phase
uniform vec4 uFin;         // x pectoral (0 folded..1 spread), y clamp, z gill, w gill rate (Hz)
uniform vec4 uShape;       // x thin, y part (0 body, 1 fin, 2 eye, 3 leg/antenna), z eye yaw, w fin kind (0 median, 1 pectoral, 2 caudal, 3 pelvic)
uniform vec3 uEyePivot;    // eye centre in model space (extras.pivot)
uniform float uRigid;      // 1: no skinning (sessile / cm-unit models); uModel places them
uniform mat4 uModel;

// Anti-clipping limits (the renderer also caps the spine bend per segment, FishRig.limitBend).
const float MAX_WAVE_AMP = 0.12;   // lateral swim wave at the tail, body lengths

out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec4 vColor;

vec3 catmull(vec3 p0, vec3 p1, vec3 p2, vec3 p3, float t) {
    float t2 = t * t, t3 = t2 * t;
    return 0.5 * ((2.0 * p1) + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
}

void sampleSpine(float u, out vec3 pos, out vec4 q) {
    float f = clamp(u, 0.0, 1.0) * 15.0;
    int i = int(min(floor(f), 14.0));
    float t = f - float(i);
    vec3 p0 = uSpine[max(i - 1, 0)].xyz;
    vec3 p1 = uSpine[i].xyz;
    vec3 p2 = uSpine[i + 1].xyz;
    vec3 p3 = uSpine[min(i + 2, 15)].xyz;
    // extrapolate the end tangents so the head/tail are not pinched
    if (i == 0) p0 = 2.0 * p1 - p2;
    if (i == 14) p3 = 2.0 * p2 - p1;
    pos = catmull(p0, p1, p2, p3, t);
    vec4 qa = uFrame[i];
    vec4 qb = uFrame[i + 1];
    if (dot(qa, qb) < 0.0) qb = -qb;
    q = normalize(mix(qa, qb, t));
}

void main() {
    vec3 local = aPos;
    vec3 n = aNormal;
    float part = uShape.y;
    float hinge = aColor.b;
    float time = uTime;

    if (uRigid > 0.5) {
        vec4 w = uModel * vec4(local, 1.0);
        vWorld = w.xyz;
        vNormal = normalize(mat3(uModel) * n);
        vUv = aUv;
        vColor = aColor;
        gl_Position = uViewProj * w;
        return;
    }

    float u = aColor.a;
    // Offset of this vertex from its spine station along the body axis (fins/eyes carry the u of their base).
    local.x -= (0.5 - u);

    if (part < 0.5) {
        // body: wrasse thinness and gill flare (opercular mask in G)
        local.yz *= 1.0 - 0.25 * uShape.x;
        float flare = 0.03 * uFin.z * aColor.g * (0.5 + 0.5 * sin(time * uFin.w * 2.0 * PI));
        local.z += sign(local.z) * flare;
    } else if (part < 1.5) {
        // fins: clamp folds toward the body (hinge-weighted, up to ~60 deg) and flutter
        float clampA = uFin.y * hinge;
        local.x -= clampA * 0.06;
        local.y *= 1.0 - 0.45 * clampA * step(abs(local.z), abs(local.y));
        local.z *= 1.0 - 0.45 * clampA * step(abs(local.y), abs(local.z));
        float flutter = 0.02 * sin(time * 12.0 + u * 6.0) * hinge * (1.0 - 0.7 * uFin.y);
        if (uShape.w > 0.5 && uShape.w < 1.5) {
            // pectorals: fan open and row
            float spread = mix(0.25, 1.0, uFin.x);
            local.z *= mix(0.35, 1.0, spread);
            local.x += 0.04 * hinge * sin(time * 7.0 + u * 3.0) * spread;
            local.z += sign(local.z) * flutter;
        } else {
            local.z += flutter;
        }
    } else if (part < 2.5) {
        // eyes swivel about their pivot (model space)
        vec3 pv = uEyePivot;
        pv.x -= (0.5 - u);
        vec3 r = local - pv;
        float c = cos(uShape.z), s = sin(uShape.z);
        r = vec3(c * r.x + s * r.z, r.y, -s * r.x + c * r.z);
        local = pv + r;
        n = vec3(c * n.x + s * n.z, n.y, -s * n.x + c * n.z);
    } else {
        // legs / antennae: sweep with the appendage phase along the hinge (B) coordinate
        float sweep = sin(uSwim.w * 2.0 * PI + hinge * 4.0 + u * 5.0);
        local.y += 0.04 * hinge * sweep;
        local.z += 0.03 * hinge * cos(uSwim.w * 2.0 * PI + hinge * 3.0);
    }

    // Fins carry the u of their base, so a fin that trails back (pectorals, pelvics, long dorsal/anal rays) was
    // skinned rigidly to its base station and cut into the body on sharp bends and big tail beats. Re-station
    // fin vertices at the spine coordinate they actually lie over, so they follow the same bend and wave as
    // the body beneath them (the caudal fin keeps its overhang past u = 1).
    float uw = u;
    if (part > 0.5 && part < 1.5) {
        uw = clamp(u - local.x, 0.0, 1.0);
        local.x += uw - u;
    }

    // Lateral body wave: z += A(u) sin(2 pi (u waveLen - phase)), A(u) = amp (0.1 + 0.9 u^2). The amplitude is
    // capped and faded while the body is strongly curved (head vs tail heading) so the wave cannot fold the tail
    // back into the body on a tight turn.
    vec3 fwdHead = qrot(uFrame[0], vec3(1.0, 0.0, 0.0));
    vec3 fwdTail = qrot(uFrame[15], vec3(1.0, 0.0, 0.0));
    float bendFade = mix(0.35, 1.0, smoothstep(-0.2, 0.85, dot(fwdHead, fwdTail)));
    float amp = min(uSwim.y, MAX_WAVE_AMP) * bendFade;
    float A = amp * (0.1 + 0.9 * uw * uw);
    float arg = 2.0 * PI * (uw * uSwim.z - uSwim.x);
    local.z += A * sin(arg);
    // slope dz/du tilts the normal (cheap analytic correction instead of a finite difference)
    float dzdu = amp * (1.8 * uw) * sin(arg) + A * cos(arg) * 2.0 * PI * uSwim.z;
    n = normalize(vec3(n.x - dzdu * n.z, n.y, n.z));

    vec3 sp;
    vec4 q;
    sampleSpine(uw, sp, q);
    vec3 world = sp + qrot(q, local * uScale);
    vWorld = world;
    vNormal = normalize(qrot(q, n));
    vUv = aUv;
    vColor = aColor;
    gl_Position = uViewProj * vec4(world, 1.0);
}
