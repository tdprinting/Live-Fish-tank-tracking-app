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
uniform vec4 uMotion;      // x pectoral stroke phase (cycles), y stroke sweep (0 = legacy flutter), z C-start bend (+ = toward +Z), w mode (0 fish, 1 brittle-star arm rowing, 2 shrimp, 3 crab legs)
uniform float uPick;       // shrimp: picking / feeding (mouth) 0..1
uniform vec3 uEyePivot;    // eye centre in model space (extras.pivot)
uniform float uRigid;      // 1: no skinning (sessile / cm-unit models); uModel places them
uniform mat4 uModel;
// Limb pose from the sim (SIMULATION §19, core LimbRig), uLimb[]: shrimp (mode 2) send one entry, uLimb[0] = (x stride
// phase (cycles), y stepping 0..1, z rock / claw-display signal, w antenna grooming), read by the per-class shrimp
// limb path below. Crawlers (uLimbOn): mode 1 brittle star arms, mode 3 Sally Lightfoot legs + chelae.
// The disc / carapace is placed rigidly at the organism (spine station 0); each limb vertex (part 3, COLOR_0.r = t
// along the limb, COLOR_0.b = limb index / 9) is posed about its pivot from the sim's per-limb data.
uniform float uLimbOn;     // 1: the model has extras.limbPivots and the pose carries limbs
uniform vec4 uLimb[10];    // per-limb pose (layout per animal: core BrittleArms / CrabLegs / ShrimpLimbs)
uniform vec4 uLimbPivot[10]; // model-space limb root xyz, w = rest limb length (model units)

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

vec3 rotAxis(vec3 v, vec3 a, float ang) {
    float c = cos(ang), s = sin(ang);
    return v * c + cross(a, v) * s + a * dot(a, v) * (1.0 - c);
}

// Brittle star arm direction angles at s (0 root .. 1 tip): yaw offset from the rest direction (rad, + = toward the
// model's left, -Z) and pitch (rad, + = tip up). Base swing with a lagging distal part, a bend wave travelling root ->
// tip, tip curl (loops round food when feeding), and coiling when the arm is drawn in (reach -> 0).
void starAngles(int k, float s, out float yaw, out float pitch) {
    vec4 A = uLimb[k];
    vec4 X = uLimb[k + 5];
    float alt = (k % 2 == 0) ? 1.0 : -1.0;
    float coil = 1.0 - clamp(A.w, 0.0, 1.0);
    yaw = A.y * (1.0 - 0.55 * s) + X.y * s * sin(2.0 * PI * (1.3 * s - A.x))
        + X.z * smoothstep(0.5, 1.0, s) * 2.0 + alt * coil * 4.0 * s;
    pitch = A.z * s * (0.4 + 0.6 * s) - coil * 0.15 * s;
}

vec3 starDir(float psi, float phi) {
    return vec3(cos(phi) * cos(psi), sin(phi), -cos(phi) * sin(psi));
}

// Poses one brittle star arm vertex: the rest arm is straight from the pivot along a0 = atan(-P.z, P.x); the bent
// centreline is integrated from the direction angles, the cross-section rides on its frame. Vertices past the arm's
// current length (autotomy, regrowth) collapse onto the stump.
void starArm(int k, inout vec3 local, inout vec3 n) {
    vec4 P = uLimbPivot[k];
    float a0 = atan(-P.z, P.x);
    vec3 d0 = vec3(cos(a0), 0.0, -sin(a0));
    vec3 l0 = vec3(-sin(a0), 0.0, -cos(a0));
    vec3 off = local - P.xyz;
    float along = dot(off, d0);
    float lat = dot(off, l0);
    float up = off.y;
    float lenK = clamp(uLimb[k + 5].x, 0.1, 1.0);
    float s = clamp(aColor.r, 0.0, 1.0);
    float keep = 1.0 - step(lenK + 0.001, s);
    s = min(s, lenK);
    float ax = along - s * P.w;
    // integrate the centreline (8 midpoint steps)
    vec3 c = P.xyz;
    float h = s / 8.0;
    float yaw, pitch;
    for (int i = 0; i < 8; i++) {
        starAngles(k, (float(i) + 0.5) * h, yaw, pitch);
        c += starDir(a0 + yaw, pitch) * (h * P.w);
    }
    c.y = max(c.y, 0.004);
    starAngles(k, s, yaw, pitch);
    float psi = a0 + yaw;
    vec3 T = starDir(psi, pitch);
    vec3 L = vec3(-sin(psi), 0.0, -cos(psi));
    vec3 U = cross(T, L);
    local = c + (T * ax + L * lat + U * up) * keep;
    n = T * dot(n, d0) + L * dot(n, l0) + U * n.y;
}

// Poses one Sally Lightfoot limb vertex about its pivot. Legs (k 0..7; uLimb = phase, signed extension stroke, foot
// lift, tuck): swing in the first half of the step cycle (foot lifted), stance in the second (foot planted while the
// leg flexes or extends along its length); tuck draws the legs in and down. Chelae (8, 9; uLimb = pick phase,
// stroke, reach): dip to the rock and come back to the mouth, alternating.
void crabLimb(int k, inout vec3 local, inout vec3 n) {
    vec4 P = uLimbPivot[k];
    vec4 A = uLimb[k];
    vec3 off = local - P.xyz;
    if (k < 8) {
        vec3 d = normalize(vec3(P.x, 0.0, P.z) + vec3(0.0001, 0.0, 0.0));
        float ph = A.x;
        float swing = 1.0 - step(0.5, ph);
        float q = ph * 2.0;
        float p = (ph - 0.5) * 2.0;
        float e = swing > 0.5 ? -cos(PI * q) : 1.0 - 2.0 * p;
        float t = clamp(aColor.r, 0.0, 1.0);
        float radial = dot(off, d);
        // extension / flexion along the leg (distal segments move most), drawn in by the tuck
        off += d * radial * (0.2 * A.y * e * t - 0.3 * A.w * t);
        vec3 ax = vec3(-d.z, 0.0, d.x);
        float lift = A.z * swing * sin(PI * q) - 0.12 * A.w;
        off = rotAxis(off, ax, lift);
        n = rotAxis(n, ax, lift);
        // small fore-aft swing of the whole leg
        float yaw = 0.08 * A.y * sin(2.0 * PI * ph);
        off = rotAxis(off, vec3(0.0, 1.0, 0.0), yaw);
        n = rotAxis(n, vec3(0.0, 1.0, 0.0), yaw);
    } else {
        float w = 0.5 - 0.5 * cos(2.0 * PI * A.x);   // 0 at the mouth .. 1 at the rock
        float down = A.y * 0.5 * w + 0.15 * (A.z - 0.5);
        off.x += 0.12 * (A.z - 0.5) - 0.07 * A.y * (1.0 - w);
        off = rotAxis(off, vec3(0.0, 0.0, 1.0), -down);
        n = rotAxis(n, vec3(0.0, 0.0, 1.0), -down);
    }
    local = P.xyz + off;
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

    float mode = uMotion.w;
    if (uLimbOn > 0.5) {
        // limb-rigged crawler: rigid body at the organism, limbs posed from the sim (no spline skinning)
        if (part > 2.5) {
            int k = int(floor(aColor.b * 9.0 + 0.5));
            if (mode < 1.5) starArm(k, local, n); else crabLimb(k, local, n);
        } else if (part > 1.5) {
            vec3 r = local - uEyePivot;
            float c = cos(uShape.z), s = sin(uShape.z);
            local = uEyePivot + vec3(c * r.x + s * r.z, r.y, -s * r.x + c * r.z);
            n = vec3(c * n.x + s * n.z, n.y, -s * n.x + c * n.z);
        }
        vec3 wl = uSpine[0].xyz + qrot(uFrame[0], local * uScale);
        vWorld = wl;
        vNormal = normalize(qrot(uFrame[0], n));
        vUv = aUv;
        vColor = aColor;
        gl_Position = uViewProj * vec4(wl, 1.0);
        return;
    }

    float u = aColor.a;
    if (mode > 0.5 && mode < 1.5 && part > 2.5) {
        // Brittle star arm rowing (Astley 2012): the arm pointing forward (+X) leads and barely moves; the arm pairs
        // either side make mirrored power strokes in the disc plane, the trailing pair drags. Rotate about the disc.
        float phi = atan(local.z, local.x);
        float r = length(local.xz);
        float side = abs(phi);
        float w = smoothstep(0.3, 1.2, side) * (1.0 - 0.6 * smoothstep(2.0, 2.8, side));
        float d = sign(phi) * w * uMotion.y * sin(2.0 * PI * uMotion.x - 2.5 * r) * smoothstep(0.02, 0.2, r);
        float c = cos(d), s = sin(d);
        local = vec3(c * local.x - s * local.z, local.y + 0.02 * w * uMotion.y * r * cos(2.0 * PI * uMotion.x), s * local.x + c * local.z);
        n = vec3(c * n.x - s * n.z, n.y, s * n.x + c * n.z);
    }
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
            if (uMotion.y > 0.001) {
                // labriform rowing from the sim's stroke phase (core MotionProfile): fore-aft sweep with a
                // feathering lift, amplitude set by speed (Drucker & Jensen 1996)
                float ph = 2.0 * PI * uMotion.x - u * 1.5;
                local.x += 0.09 * uMotion.y * hinge * sin(ph) * spread;
                local.y += 0.03 * uMotion.y * hinge * cos(ph) * spread;
            } else {
                local.x += 0.04 * hinge * sin(time * 7.0 + u * 3.0) * spread;
            }
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
    } else if (mode > 1.5 && mode < 2.5 && aColor.r > 0.06) {
        vec4 S = uLimb[0];   // ShrimpLimbs: stride phase, stepping, rock / claw signal, grooming
        // Shrimp limbs (research 17). COLOR_0.r = limb class / 8, .g = pair / 8, .b = hinge (0 base .. 1 tip).
        float cls = floor(aColor.r * 8.0 + 0.5);
        float pr = floor(aColor.g * 8.0 + 0.5);
        float side = local.z >= 0.0 ? 1.0 : -1.0;
        float h = hinge;
        if (cls < 1.5) {
            // walking legs: alternating gait, neighbours and opposite legs half a stride apart; the foot swings
            // forward lifted and pushes back planted. Idle legs shift their footing now and then.
            float ph = 2.0 * PI * (S.x + 0.5 * mod(pr + (side > 0.0 ? 1.0 : 0.0), 2.0));
            local.x += h * (0.05 * S.y * sin(ph) + 0.004 * sin(time * 0.7 + pr * 1.7 + side));
            local.y += h * 0.03 * S.y * max(cos(ph), 0.0);
        } else if (cls < 2.5) {
            // picking legs (P1-P2): alternate left/right strokes to the mouth while feeding or cleaning; the
            // front pair rocks rapidly back and forth as the skunk cleaner's signal; P1 combs the antenna in grooming
            float k = 0.5 + 0.5 * sin(2.0 * PI * 2.2 * time + (side > 0.0 ? 0.0 : PI) + pr * 1.3);
            float pick = max(uPick, 0.0);
            local.x -= h * 0.06 * pick * k;
            local.y += h * 0.035 * pick * k;
            local.z -= side * h * 0.03 * pick * k;
            float rw = pr < 0.5 ? 1.0 : 0.35;
            float rock = sin(2.0 * PI * 3.0 * time);
            local.x += h * 0.05 * S.z * rw * rock;
            local.y += h * 0.025 * S.z * rw * abs(rock);
            local.x += h * 0.03 * S.w * (pr < 0.5 ? 1.0 : 0.0) * sin(2.0 * PI * 1.4 * time + side);
            local.y += h * 0.02 * S.w * (pr < 0.5 ? 1.0 : 0.0);
            local.x += h * 0.012 * S.y * sin(2.0 * PI * S.x + pr * PI);
        } else if (cls < 3.5) {
            // banded cheliped: held raised; it swings up and out toward an intruder (display), draws in to feed
            float dsp = S.z;
            local.z += side * h * 0.12 * dsp;
            local.y += h * (0.07 * dsp + 0.008 * sin(time * 0.9 + side));
            local.x -= h * 0.05 * max(uPick - 0.3, 0.0) * (0.5 + 0.5 * sin(2.0 * PI * 1.2 * time + side * 1.5));
            local.y -= h * 0.012 * S.y * sin(2.0 * PI * S.x);
        } else if (cls < 4.5) {
            // long antennae: slow whip-like sweeps that travel to the tip, faster when the appendage phase runs
            // (advertising, cleaning); lash with the skunk cleaner's leg rock; grooming draws one down to the mouth
            float h2 = h * h;
            float ph = uSwim.w + pr * 2.1 + side * 1.3;
            local.y += h2 * (0.06 * sin(ph - h * 3.0) + 0.1 * S.z * sin(2.0 * PI * 3.0 * time - h * 6.0 + side));
            local.z += h2 * 0.07 * cos(0.7 * ph - h * 2.5);
            float g = S.w * step(0.0, side);
            local.y -= g * h * 0.25;
            local.x -= g * h * 0.3 * (0.6 + 0.4 * sin(2.0 * PI * 0.8 * time));
            local.z -= g * side * h * 0.2;
        } else if (cls < 5.5) {
            // antennule flagella: sniffing flicks, a quick down-stroke about once a second (lobster analogue
            // 0.5-2 Hz, research 17) out of step between whips, over a small sway
            float f = pow(max(sin(2.0 * PI * 1.1 * time + pr * 2.0 + side * 0.9), 0.0), 12.0);
            local.y -= h * 0.05 * f;
            local.z += h * 0.02 * sin(uSwim.w * 0.8 + pr + side);
            local.y -= S.w * (1.0 - step(0.0, side)) * h * 0.08;
        } else if (cls < 6.5) {
            // pleopods: metachronal beat, the hindmost pair leads and the wave runs forward (uMotion: phase, amp)
            float ph = 2.0 * PI * (uMotion.x + 0.2 * pr);
            local.x += h * 0.035 * uMotion.y * sin(ph);
            local.y += h * 0.01 * uMotion.y * cos(ph);
        } else {
            // third maxillipeds: a slow idle flutter, quick picking strokes when feeding or grooming
            float a = 0.004 + 0.02 * max(uPick, S.w);
            local.y += h * a * sin(2.0 * PI * 4.0 * time + (side > 0.0 ? 0.0 : PI));
            local.x -= h * 0.6 * a * cos(2.0 * PI * 4.0 * time + (side > 0.0 ? 0.0 : PI));
        }
    } else if (mode < 0.5 || mode > 1.5) {
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
    // Escape C-start (Domenici & Blake 1997): head and tail curl to the same side, the middle the other way.
    // Shrimp (mode 2) flex the abdomen ventrally instead (caridoid tail-flip).
    float bend = uMotion.z;
    if (mode > 1.5) {
        float ab = max(uw - 0.4, 0.0);
        local.y -= 0.9 * max(bend, 0.0) * ab * ab;
        local.x -= 0.4 * max(bend, 0.0) * ab * ab;
    } else if (mode < 0.5 && abs(bend) > 0.001) {
        float e = uw - 0.35;
        local.z += bend * 0.55 * (e * e - 0.04);
        dzdu += bend * 0.55 * 2.0 * e;
    }
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
