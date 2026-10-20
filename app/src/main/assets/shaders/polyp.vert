#version 300 es
#include "common.glsl"
// Instanced polyps (ARCHITECTURE §8.4 item 3): GSP, zoas, clove, Kenya tufts, organ-pipe polyps, the
// feather-duster crown. Prototype in cm, base at the origin, axis +Y; COLOR_0.b = tentacle hinge 0..1.
// COLOR_0.r = 1 marks long curling tentacles (long-tentacle GSP, Tee's photos 2026-10-13): they writhe slowly,
// retract by shortening into the stalk (the whole polyp sinks into the mat) and sway more toward the tips.

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
// anemone mode (uSwayMode 2, the bubble-tip anemone, research 40): x bubble 0..1, y contraction 0..1 (deflated),
// z feeding curl 0..1, w curl direction (radians about the disc axis, model space)
uniform vec4 uAnem;
// fish contacts A and B (core AnemoneRig): world point (xyz) and radius (cm, w); drift = the fish's smoothed velocity (cm/s)
uniform vec4 uAnemPush;
uniform vec4 uAnemPushB;
uniform vec4 uAnemDrift;
uniform vec4 uAnemDriftB;
// x contact A strength (springy, about 0..1), y model scale (world cm per model cm), z contact B strength, w unused
uniform vec4 uAnemMisc;

out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec4 vColor;
out float vVariant;
out float vOpen;
out float vTip;
out float vGlow;
out float vRing;

// Rodrigues rotation of v about the unit axis k
vec3 rotAxis(vec3 v, vec3 k, float a) {
    float c = cos(a), s = sin(a);
    return v * c + cross(k, v) * s + k * dot(k, v) * (1.0 - c);
}

// Tentacle radius (fraction of the stringy length) and length factor at hinge h; tools/blender/anemone.py
// tentacle_profile is the same formula. Stringy: long taper. Bubbled: shorter, a bulb at bp and a narrow tip.
vec2 anemoneProfile(float h, float bubble, float bp) {
    float lf = 1.0 - 0.5 * bubble;
    float rs = 0.085 * (1.0 - 0.5 * h);
    float bq = (h - bp) / 0.25;
    float bulb = exp(-bq * bq);
    float rb = 0.045 * (1.0 - 0.3 * h) + 0.13 * bulb;
    float nip = smoothstep(bp + 0.16, bp + 0.32, h);
    rb = mix(rb, 0.035, nip);
    float r = mix(rs, rb, bubble);
    float cq = (h - 0.93) / 0.07;
    if (h > 0.93) r *= sqrt(clamp(1.0 - cq * cq, 0.0, 1.0));
    return vec2(r, lf);
}

// Sideways tip offset (world cm) one fish contact asks of a tentacle: away from the fish, plus a little of the water it
// drags along. Evaluated once per tentacle (at a point along its axis), so the whole tentacle bends as one piece.
vec3 anemoneContact(vec3 q, vec3 ax, vec3 outward, float len, vec4 c, float k, vec3 drift) {
    vec3 d = q - c.xyz;
    float R = c.w + 0.35 * len;
    float near = 1.0 - smoothstep(0.3 * R, R, length(d));
    // sideways part only, nudged outward from the disc so a tentacle right under the fish still has a smooth way out
    vec3 side = d - ax * dot(d, ax) + outward * (0.2 * R);
    float sl = length(side);
    vec3 dir = sl > 1e-4 ? side / sl : outward;
    vec3 dr = drift * 0.22;
    float dm = length(dr);
    if (dm > 0.5 * len) dr *= 0.5 * len / dm;
    return (dir * (0.85 * R) + dr) * (k * near);
}

void anemone(float phase) {
    float bubble = clamp(uAnem.x, 0.0, 1.0);
    float ext = 1.0 - clamp(uAnem.y, 0.0, 1.0);
    float k = mix(0.45, 1.0, ext);
    vec3 p;
    vec3 n;
    float h = aColor.b;
    vTip = 0.0;
    vRing = 0.0;
    if (aColor.g > 0.5) {
        // oral disc + column: the disc shrinks and the column squats as the animal deflates
        p = aPos * vec3(k, mix(0.75, 1.0, ext), k);
        n = normalize(aNormal * vec3(1.0 / k, 1.0, 1.0 / k));
        vGlow = aColor.r;
        h = 0.0;
    } else {
        float bp = 0.55 + 0.2 * iPhaseVar.y;
        float rl = length(aPos.xz);
        vec2 dir = rl > 1e-5 ? aPos.xz / rl : vec2(0.0);
        vec2 pr = anemoneProfile(h, bubble, bp);
        vec2 pr2 = anemoneProfile(min(h + 0.02, 1.0), bubble, bp);
        float len = 0.35 + 0.65 * ext;
        p = vec3(dir.x * pr.x, h * pr.y, dir.y * pr.x) * len;
        float drdy = (pr2.x - pr.x) / (0.02 * pr.y);
        n = rl > 1e-5 ? normalize(vec3(dir.x, -drdy, dir.y)) : vec3(0.0, 1.0, 0.0);
        // slow writhe about a per-tentacle side axis, larger for long stringy tentacles; the wave travels base to tip
        vec3 side = vec3(cos(phase), 0.0, sin(phase));
        float wr = h * h * (0.08 + 0.3 * (1.0 - bubble)) * sin(uTime * 0.55 + phase * 3.0 - h * 2.2);
        p = rotAxis(p, side, wr);
        n = rotAxis(n, side, wr);
        vGlow = 1.0 - smoothstep(0.1, 0.3, h);
        vTip = bubble * smoothstep(0.84, 0.96, h);
        // no bulb: a pale ring where its equator would be (Fautin 2007 via SeaLifeBase)
        float rq = (h - bp) / 0.045;
        vRing = (1.0 - bubble) * exp(-rq * rq);
    }
    vec3 base = iPosScale.xyz * vec3(k, 1.0, k);
    vec3 v = qrot(iQuat, p * iPosScale.w);
    vec3 nn = qrot(iQuat, n);
    vec3 ax = qrot(iQuat, vec3(0.0, 1.0, 0.0));
    vec2 rad = length(iPosScale.xz) > 1e-4 ? normalize(iPosScale.xz) : vec2(1.0, 0.0);
    if (aColor.g < 0.5) {
        // tentacles tip inward as the animal balls up, and the ones on the food's side curl to the mouth
        vec3 axis = vec3(-rad.y, 0.0, rad.x);
        float sector = smoothstep(0.2, 0.9, cos(atan(rad.y, rad.x) - uAnem.w));
        float a = 0.7 * (1.0 - ext) + 1.5 * clamp(uAnem.z, 0.0, 1.0) * sector;
        v = rotAxis(v, axis, a);
        nn = rotAxis(nn, axis, a);
        ax = rotAxis(ax, axis, a);
    }
    vec4 world = uModel * vec4(base + v, 1.0);
    vec3 wn = normalize(mat3(uModel) * nn);
    if (aColor.g < 0.5) {
        vec3 wBase = (uModel * vec4(base, 1.0)).xyz;
        vec3 wAx = normalize(mat3(uModel) * ax);
        vec3 wOut = normalize(mat3(uModel) * vec3(rad.x, 0.0, rad.y));
        float tl = (0.35 + 0.65 * ext) * (1.0 - 0.5 * bubble) * iPosScale.w * uAnemMisc.y;
        float hw = h * h * iPosScale.w * uAnemMisc.y;
        // flow: bulbs are stiffer than stringy tentacles (phase from the tentacle's base, so it moves as one piece)
        float sw = sin(uTime * 1.3 + phase + dot(wBase.xz, vec2(0.21, 0.13)));
        world.xyz += uSwayDir * (uSwayAmp * (1.0 - 0.6 * bubble) * (0.6 + 0.4 * sw)) * hw;
        // fish pressing in (a nestling clown pair, a blundering tang): each tentacle bends away from them about its base,
        // more toward the tip, so it curves smoothly instead of its vertices being shoved apart
        vec3 q = wBase + wAx * (0.65 * tl);
        vec3 bend = anemoneContact(q, wAx, wOut, tl, uAnemPush, uAnemMisc.x, uAnemDrift.xyz)
                  + anemoneContact(q, wAx, wOut, tl, uAnemPushB, uAnemMisc.z, uAnemDriftB.xyz);
        bend -= wAx * dot(bend, wAx);
        float bl = length(bend);
        if (bl > 1e-4 && tl > 1e-3) {
            float th = min(1.25 * bl / tl, 1.4) * (1.0 - 0.3 * bubble);
            vec3 bax = normalize(cross(wAx, bend));
            float g = th * h;
            world.xyz = wBase + rotAxis(world.xyz - wBase, bax, g);
            wn = rotAxis(wn, bax, g);
        }
    }
    vWorld = world.xyz;
    vNormal = wn;
    vUv = aUv;
    vColor = aColor;
    vVariant = iPhaseVar.y;
    vOpen = ext;
    gl_Position = uViewProj * world;
}

void main() {
    float phase = iPhaseVar.x * 6.2831;
    if (uSwayMode > 1.5) { anemone(phase); return; }
    vTip = 0.0;
    vRing = 0.0;
    vGlow = 1.0;
    float hinge = aColor.b;
    bool longTent = aColor.r > 0.5;
    vec3 p = aPos;
    vec3 n = aNormal;
    float open;
    float swayW = hinge;
    if (longTent) {
        // long tentacles stay out: only a small breathing pulse on top of the colony extension
        open = clamp(uExtension * (0.88 + 0.12 * sin(uTime * 0.3 + phase)), 0.0, 1.0);
        // writhe: each strand bends slowly about its own side axis, most at the tip
        float rl = length(p.xz);
        if (rl > 1e-4) {
            vec2 rad = p.xz / rl;
            vec3 side = vec3(-rad.y, 0.0, rad.x);
            float wr = hinge * hinge * 0.45 * sin(uTime * 0.8 + phase * 3.0 + atan(rad.y, rad.x) * 3.0);
            p = rotAxis(p, side, wr);
            n = rotAxis(n, side, wr);
        }
        // retract: tentacles shorten toward the stalk and the stalk sinks into the mat
        float shut = 1.0 - open;
        p.xz *= 1.0 - 0.9 * shut * hinge;
        p.y *= 1.0 - 0.85 * shut;
        // a long strand bends in the flow like a beam: tips swing ~2.5x a short polyp's
        swayW = 2.5 * hinge * hinge;
    } else {
        open = clamp(uExtension * (0.6 + 0.4 * sin(uTime * 0.3 + phase)), 0.0, 1.0);
        // close: fold the tentacles in toward the axis and down, and twist about the axis
        float fold = (1.0 - open) * hinge;
        p.xz *= 1.0 - 0.75 * fold;
        p.y *= 1.0 - 0.5 * fold;
        float ang = (1.0 - open) * 1.2 * hinge;
        float c = cos(ang), s = sin(ang);
        p.xz = vec2(c * p.x - s * p.z, s * p.x + c * p.z);
        n.xz = vec2(c * n.x - s * n.z, s * n.x + c * n.z);
    }

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
        w += uSwayDir * sw * uSwayAmp * swayW * iPosScale.w;
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
