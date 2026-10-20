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
uniform vec2 uEnv;         // body-wave envelope: x exponent (stiff front half = high), y head amplitude share (0..1)
uniform vec4 uMotion;      // x pectoral stroke phase (cycles), y stroke sweep (0 = legacy flutter), z C-start bend (+ = toward +Z), w mode (0 fish, 1 brittle-star arm rowing, 2 shrimp, 3 crab legs, 4 bobtail squid, 5 hermit crab, 8 snail, 9 urchin)
uniform float uPick;       // shrimp: picking / feeding (mouth) 0..1; fish with a jaw: mouth opening 0..1
uniform vec4 uJaw;         // fish jaw (GLB extras.jaw): xy hinge (model space), z widest gape (rad), w 1 = has a jaw
uniform vec3 uEyePivot;    // eye centre in model space (extras.pivot)
uniform float uRigid;      // 1: no skinning (sessile / cm-unit models); uModel places them
uniform mat4 uModel;
uniform vec3 uStageShape;   // life stage (LifeStageVisuals.kt, additive): x body depth − 1, y body width − 1; 0 = as built
// Limb pose from the sim (SIMULATION §19, core LimbRig), uLimb[]: shrimp (mode 2) send 15 entries (core ShrimpLimbs), uLimb[0] = (x stride
// phase (cycles), y stepping 0..1, z rock / claw-display signal, w antenna grooming), read by the per-class shrimp
// limb path below. Crawlers (uLimbOn): mode 1 brittle star arms, mode 3 Sally Lightfoot legs + chelae, mode 5 hermit crab.
// The disc / carapace is placed rigidly at the organism (spine station 0); each limb vertex (part 3, COLOR_0.r = t
// along the limb, COLOR_0.b = limb index / 9) is posed about its pivot from the sim's per-limb data.
uniform float uLimbOn;     // 1: the model has extras.limbPivots and the pose carries limbs
uniform vec4 uLimb[40];    // per-limb pose (layout per animal: core BrittleArms (40) / CrabLegs (19) / ShrimpLimbs (15) / HermitLegs (15))
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

// Brittle star arms (mode 1, core BrittleArms; SIMULATION §19.2, §19.7). The sim marches each arm out from the disc in
// STAR_N equal steps fitted to the rock, sand, glass and animals under it and sends, per arm k (uLimb[8k .. 8k + 7]),
// each step's direction psi_j (rad from the arm's rest direction, + = toward the model's left, -Z) and the height y_j
// of its end above the disc's ground plane (model units). Heights 1..8 ride in the x slots as 0.5 + 0.5 y (x is lerped
// as a phase in [0, 1) by the renderer); the plain slots n = 0.. hold psi_1..15, then y_9..15, then the arm's length.
const int STAR_N = 15;

float starSlot(int k, int n) {
    vec4 e = uLimb[8 * k + n / 3];
    int c = n - 3 * (n / 3);
    return c == 0 ? e.y : (c == 1 ? e.z : e.w);
}

float starPsi(int k, int j) { return starSlot(k, j - 1); }

float starY(int k, int j) {
    if (j <= 0) return 0.0;
    if (j <= 8) return 2.0 * uLimb[8 * k + j - 1].x - 1.0;
    return starSlot(k, j + 6);
}

// Nodes j - 1 .. j + 2 of arm k's centreline relative to its root node 0 (the sim's BrittleArms.nodes), in one pass:
// each step runs a step length, of which the height change takes its share (never less than 0.3 of the run, as a
// steep climb keeps). The curve's end tangents are extrapolated (a node outside 0..STAR_N mirrors its neighbour).
void starNodes(int k, int j, float a0, float seg, out vec3 p0, out vec3 p1, out vec3 p2, out vec3 p3) {
    vec3 p = vec3(0.0);
    float yPrev = 0.0;
    p0 = p; p1 = p; p2 = p; p3 = p;
    for (int i = 1; i <= STAR_N; i++) {
        if (i > j + 2) break;
        float psi = a0 + starPsi(k, i);
        float y = starY(k, i);
        float dy = y - yPrev;
        float hz = sqrt(max(seg * seg - dy * dy, 0.09 * seg * seg));
        p += vec3(cos(psi) * hz, dy, -sin(psi) * hz);
        yPrev = y;
        if (i == j - 1) p0 = p;
        if (i == j) p1 = p;
        if (i == j + 1) p2 = p;
        if (i == j + 2) p3 = p;
    }
    if (j == 0) p0 = 2.0 * p1 - p2;
    if (j + 2 > STAR_N) p3 = 2.0 * p2 - p1;
}

// Poses one brittle star arm vertex: the rest arm is straight from the pivot along a0 = atan(-P.z, P.x); the drawn
// centreline is the Catmull-Rom curve through the sim's nodes (end tangents extrapolated), the cross-section rides on
// its frame. Vertices past the arm's current length (autotomy, regrowth) collapse onto the stump.
void starArm(int k, inout vec3 local, inout vec3 n) {
    vec4 P = uLimbPivot[k];
    float a0 = atan(-P.z, P.x);
    vec3 d0 = vec3(cos(a0), 0.0, -sin(a0));
    vec3 l0 = vec3(-sin(a0), 0.0, -cos(a0));
    vec3 off = local - P.xyz;
    float along = dot(off, d0);
    float lat = dot(off, l0);
    float up = off.y;
    float lenK = clamp(starSlot(k, 22), 0.1, 1.0);
    float s = clamp(aColor.r, 0.0, 1.0);
    float keep = 1.0 - step(lenK + 0.001, s);
    s = min(s, lenK);
    float ax = along - s * P.w;
    float seg = P.w / float(STAR_N);
    float sN = s * float(STAR_N);
    int j = int(min(floor(sN), float(STAR_N - 1)));
    float t = sN - float(j);
    vec3 p0, p1, p2, p3;
    starNodes(k, j, a0, seg, p0, p1, p2, p3);
    vec3 c = P.xyz + catmull(p0, p1, p2, p3, t);
    float t2 = t * t;
    vec3 T = normalize(0.5 * ((-p0 + p2) + 2.0 * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t + 3.0 * (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t2) + d0 * 1e-4);
    vec3 L = normalize(vec3(T.z, 0.0, -T.x) + l0 * 1e-4);
    vec3 U = cross(T, L);
    local = c + (T * ax + L * lat + U * up) * keep;
    n = T * dot(n, d0) + L * dot(n, l0) + U * n.y;
}

// Poses one Sally Lightfoot limb vertex about its pivot. Legs (k 0..7; uLimb = phase, signed extension stroke, foot
// lift, tuck): swing in the first half of the step cycle (foot lifted), stance in the second (foot planted while the
// leg flexes or extends along its length); tuck draws the legs in and down. Chelae (8, 9; uLimb = pick phase,
// stroke, reach): dip to the rock and come back to the mouth, alternating. Seated on a surface (core CrawlerSeat,
// uLimb[18].w = 1) the legs are planted instead: uLimb[10 + k] rotates the rest leg about its coxa onto the foothold
// (axis x angle), shortens it by the flex and raises the knee, A.w folds a leg that has nowhere clear to stand, and
// uLimb[18].y / .z cap the left / right chela's dip at the surface.
void crabLimb(int k, inout vec3 local, inout vec3 n) {
    vec4 P = uLimbPivot[k];
    vec4 A = uLimb[k];
    vec3 off = local - P.xyz;
    bool planted = uLimb[18].w > 0.5;
    if (k < 8 && planted) {
        vec4 IK = uLimb[10 + k];
        float ang = length(IK.yzw);
        if (ang > 1e-5) {
            vec3 ax = IK.yzw / ang;
            off = rotAxis(off, ax, ang);
            n = rotAxis(n, ax, ang);
        }
        float t = clamp(aColor.r, 0.0, 1.0);
        float flex = (IK.x - 0.3) / 0.5;
        off *= 1.0 - flex * t;
        float r = 1.0 - flex;
        off.y += 0.35 * sqrt(max(1.0 - r * r, 0.0)) * P.w * sin(PI * t);
        // no clear foothold: folded against the carapace rim
        off *= 1.0 - 0.6 * A.w * t;
        off.y += 0.1 * A.w * t * P.w;
    } else if (k < 8) {
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
        if (planted) down = min(down, k == 8 ? uLimb[18].y : uLimb[18].z);
        off.x += 0.12 * (A.z - 0.5) - 0.07 * A.y * (1.0 - w);
        off = rotAxis(off, vec3(0.0, 0.0, 1.0), -down);
        n = rotAxis(n, vec3(0.0, 0.0, 1.0), -down);
    }
    local = P.xyz + off;
}

// Hermit crab (mode 5, core HermitLegs). Walking legs (k 0..3; uLimb = step phase, fore-aft stroke, foot lift,
// withdrawal): forward walking in diagonal pairs, the foot swung forward lifted for the first 40 % of the cycle and
// pushed back planted for the rest. Chelae (4 left, 5 right; pick phase, pick, walking support, withdrawal): dip to
// the substrate and come back to the mouth, alternating. 6 = shell: rolls and bobs about its ground point with the
// stride. 7 = shield and eyestalks (COLOR_0.g = 1: the stalks scan out of step). 8 antennae sweep, 9 antennules
// flick. uLimb.w pulls every part but the shell toward uLimbPivot[7], just inside the aperture (the larger left
// chela stays at the opening). Seated on a surface (core CrawlerSeat), uLimb[10..13] plant the walking legs on their
// footholds, cap the chelae's dip at the surface and 6.w / 8.z / 9.z bend the eyestalks and feelers away from rock.
void hermitLimb(int k, inout vec3 local, inout vec3 n) {
    vec4 P = uLimbPivot[k];
    vec4 A = uLimb[k];
    vec3 off = local - P.xyz;
    float t = clamp(aColor.r, 0.0, 1.0);
    float side = local.z >= 0.0 ? 1.0 : -1.0;
    bool planted = uLimb[14].w > 0.5;
    if (k < 4 && planted) {
        // planted on the surface by the sim (core CrawlerSeat): rotate the rest leg about the hip onto its foothold
        // (axis x angle), shorten it toward the hip by the flex and raise the knee by as much as the leg folds
        vec4 IK = uLimb[10 + k];
        float ang = length(IK.yzw);
        if (ang > 1e-5) {
            vec3 ax = IK.yzw / ang;
            off = rotAxis(off, ax, ang);
            n = rotAxis(n, ax, ang);
        }
        float flex = (IK.x - 0.3) / 0.5;
        off *= 1.0 - flex * t;
        float r = 1.0 - flex;
        off.y += 0.35 * sqrt(max(1.0 - r * r, 0.0)) * P.w * sin(PI * t);
    } else if (k < 4) {
        float ph = A.x;
        float sw = 1.0 - step(0.4, ph);
        float q = ph / 0.4;
        float e = sw > 0.5 ? -cos(PI * q) : 1.0 - 2.0 * (ph - 0.4) / 0.6;
        float yaw = A.y * e * (P.z >= 0.0 ? 1.0 : -1.0);
        off = rotAxis(off, vec3(0.0, 1.0, 0.0), yaw);
        n = rotAxis(n, vec3(0.0, 1.0, 0.0), yaw);
        off.y += A.z * sw * sin(PI * q) * smoothstep(0.25, 1.0, t) * 0.22 * P.w;
    } else if (k < 6) {
        float w = 0.5 - 0.5 * cos(2.0 * PI * A.x);   // 0 at the mouth .. 1 at the substrate
        float sd = k == 4 ? -1.0 : 1.0;
        float down = A.y * (0.55 * w - 0.15) + 0.1 * A.z;
        // never below the surface under the claw (the sim's limit, uLimb[14].y left / .z right)
        if (planted) down = min(down, k == 4 ? uLimb[14].y : uLimb[14].z);
        off = rotAxis(off, vec3(0.0, 0.0, 1.0), -down);
        n = rotAxis(n, vec3(0.0, 0.0, 1.0), -down);
        off.x -= 0.25 * P.w * A.y * (1.0 - w) * t;
        off.z -= sd * 0.1 * P.w * A.y * (1.0 - w) * t;
    } else if (k == 6) {
        float roll = A.y * sin(2.0 * PI * A.x);
        off = rotAxis(off, vec3(1.0, 0.0, 0.0), roll);
        n = rotAxis(n, vec3(1.0, 0.0, 0.0), roll);
        off = rotAxis(off, vec3(0.0, 0.0, 1.0), A.z);
        n = rotAxis(n, vec3(0.0, 0.0, 1.0), A.z);
    } else if (k == 7) {
        off.y += A.z * 0.02 * P.w;
        float em = step(0.5, aColor.g);
        float a = A.y * 0.3 * sin(2.0 * PI * A.x + side * 1.7) * em;
        off.z += t * a * P.w;
        off.x -= t * abs(a) * 0.3 * P.w;
        // eyestalks lean back and down, away from rock just ahead or overhead (uLimb[6].w, core CrawlerSeat)
        float eb = uLimb[6].w * em;
        off.x -= t * eb * 0.5 * P.w;
        off.y -= t * eb * 0.25 * P.w;
    } else if (k == 8) {
        float ph = 2.0 * PI * A.x + side * 1.3;
        float h2 = t * t;
        off.y += h2 * A.y * 0.18 * P.w * sin(ph - t * 3.0);
        off.z += h2 * A.y * 0.14 * P.w * cos(0.7 * ph - t * 2.5) * side;
        off.x += h2 * A.y * 0.08 * P.w * sin(0.5 * ph);
        // bent up and back from the rock they would reach into (A.z, core CrawlerSeat)
        if (A.z > 0.0) {
            off = rotAxis(off, vec3(0.0, 0.0, 1.0), A.z * 1.1);
            off = rotAxis(off, vec3(0.0, 1.0, 0.0), -side * A.z * 0.6);
        }
    } else {
        float f = pow(max(sin(2.0 * PI * A.x + side * 0.9), 0.0), 8.0);
        off.y -= t * t * A.y * f * 0.3 * P.w;
        if (A.z > 0.0) {
            off = rotAxis(off, vec3(0.0, 0.0, 1.0), A.z * 1.1);
            off = rotAxis(off, vec3(0.0, 1.0, 0.0), -side * A.z * 0.6);
        }
    }
    vec3 v = P.xyz + off;
    if (k != 6) {
        vec3 c = uLimbPivot[7].xyz;
        float s = k == 4 ? 0.6 : (k == 5 ? 0.45 : (k < 4 ? 0.25 : 0.2));
        v = mix(v, c + (v - c) * s, A.w);
    }
    local = v;
}

// Bobtail squid (mode 4, core SquidRig): uLimb[0] fins (x wave phase, y stroke, z mantle contraction, w wrap),
// uLimb[1] arms (x wave phase, y spread, z dorsal-pair raise, w tentacle strike). The ear fins (part 1, B = hinge)
// beat in a wave running along the fin and wrap against the mantle in a jet; the mantle behind the collar narrows as
// it contracts; the arms (part 3, COLOR_0.r = class / 8, g = index / 8, b = hinge) flare or bundle into a cone, the
// dorsal pair rises in alarm or to aim, and the two tentacles shoot forward and are drawn back.
void squidPose(float part, float hinge, inout vec3 local) {
    vec4 Fn = uLimb[0];
    vec4 Ar = uLimb[1];
    if (part < 0.5) {
        local.yz *= 1.0 - 0.14 * Fn.z * smoothstep(0.36, 0.5, aColor.a);
    } else if (part < 1.5) {
        float side = local.z >= 0.0 ? 1.0 : -1.0;
        local.y += 0.07 * Fn.y * hinge * sin(2.0 * PI * Fn.x + local.x * 18.0) * (1.0 - Fn.w);
        local.z -= side * hinge * 0.1 * Fn.w;
        local.y -= hinge * 0.03 * Fn.w;
    } else {
        float k = floor(aColor.g * 8.0 + 0.5);
        float h = hinge;
        float rl = length(local.yz);
        vec2 rd = rl > 1e-4 ? local.yz / rl : vec2(0.0);
        if (aColor.r < 0.19) {
            float dors = step(k, 1.5);
            local.yz += rd * h * (Ar.y - 0.3) * 0.14;
            local.y += dors * Ar.z * h * 0.14;
            local.x -= dors * Ar.z * h * 0.04;
            local.y += 0.012 * h * h * sin(2.0 * PI * Ar.x + k * 0.8 - h * 3.0);
            local.z += 0.01 * h * h * cos(2.0 * PI * Ar.x + k * 1.1 - h * 2.5);
        } else {
            local.x += Ar.w * 0.55 * h;
            local.yz *= 1.0 - 0.6 * Ar.w * h;
            local.y += 0.008 * h * sin(2.0 * PI * Ar.x + k * 2.0) * (1.0 - Ar.w);
        }
    }
}

// Snails (motion mode 8, core SnailRig: trochus, fighting conch). The shell is rigid at the organism; COLOR_0.r = class
// (0 shell, 0.25 foot, 0.4 operculum, 0.5 head / snout, 0.75 tentacle, 1 eye), b = t along an appendage, a = phase seed.
// uLimb[0] = (pedal-wave phase, body out 0..1, rasp 0..1, righting reach 0..1), uLimb[1] = (tentacle wave phase,
// aperture xyz), uLimb[2] = (leap phase, vault 0..1, eyestalks out 0..1, -), uLimb[3] = (-, operculum tip xyz).
// Withdrawn parts gather at the aperture; the conch's shell vaults forward over its operculum tip, tail up.
void snailPose(inout vec3 local, inout vec3 n) {
    float cls = aColor.r;
    float t = aColor.b;
    float seed = aColor.a;
    vec4 A = uLimb[0];
    vec4 B = uLimb[1];
    vec4 Lp = uLimb[2];
    vec4 T = uLimb[3];
    vec3 ap = B.yzw;
    bool foot = cls > 0.2 && cls < 0.45;
    if (cls > 0.1) {
        if (cls > 0.6) {
            // tentacles and eyestalks wave slowly, each on its own phase, the tips most
            float ph = 2.0 * PI * (B.x + seed) - 2.5 * t;
            local.y += 0.025 * t * t * sin(ph);
            local.z += 0.02 * t * t * cos(ph * 0.8);
        } else if (cls > 0.45) {
            // snout: the radula's rasping strokes (~1.5 Hz), the tip working side to side and down
            float r = A.z * t;
            local.z += 0.02 * r * sin(2.0 * PI * (1.5 * uTime + seed));
            local.y -= 0.012 * r * (0.5 + 0.5 * sin(2.0 * PI * 3.0 * uTime));
        } else if (cls < 0.3) {
            // sole: a faint ripple of the pedal waves along the rim
            local.y += 0.006 * sin(2.0 * PI * (A.x * 3.0 + local.x * 3.0));
        }
        if (foot && T.y * T.y + T.z * T.z > 0.0) {
            // conch: the foot reaches forward and down to plant the operculum before each vault
            float ph = Lp.x;
            float fr = smoothstep(0.0, 0.4, ph) * (1.0 - smoothstep(0.55, 0.85, ph));
            local.x += 0.1 * t * fr;
            local.y -= 0.02 * t * fr;
        }
        // righting: foot and head swing over the side about the body axis to grip, stretching out
        float reach = clamp(A.w, 0.0, 1.0);
        if (reach > 0.001) {
            vec3 r = local - ap;
            float ang = reach * 1.9;
            float c = cos(ang), s = sin(ang);
            r = vec3(r.x * (1.0 + 0.25 * reach), c * r.y - s * r.z, s * r.y + c * r.z);
            local = ap + r * (1.0 + 0.3 * reach);
            n = vec3(n.x, c * n.y - s * n.z, s * n.y + c * n.z);
        }
        // in or out of the shell (the conch pulls its eyes in first)
        float e = cls > 0.6 ? min(A.y, Lp.z) : A.y;
        local = ap + (local - ap) * mix(0.1, 1.0, clamp(e, 0.0, 1.0));
    }
    // conch vault: all but the foot swings forward over the operculum tip, nose down and tail up
    float v = clamp(Lp.y, 0.0, 1.0);
    if (v > 0.001 && !foot) {
        vec3 piv = T.yzw;
        vec3 r = local - piv;
        float ang = -0.45 * v;
        float c = cos(ang), s = sin(ang);
        local = piv + vec3(c * r.x - s * r.y, s * r.x + c * r.y, r.z) + vec3(0.0, 0.06 * v, 0.0);
        n = vec3(c * n.x - s * n.y, s * n.x + c * n.y, n.z);
    }
}

// Tuxedo urchin (motion mode 9, core UrchinRig). COLOR_0.r = class (0 test, 0.25 spine, 0.5 tube foot, 0.75 debris,
// 1 teeth), b = t along a spine / foot (debris: piece index / 7), a = phase seed. uLimb[0] = (tube-foot wave phase,
// feet reaching 0..1, cover 0..1, bristle 0..1), uLimb[1] = (spine sway phase, test centre xyz), uLimb[2] = (-, poke
// direction xyz). Spines sway and point toward a poke; tube feet wave or draw in; debris pieces past the cover shrink
// away into the test.
void urchinPose(inout vec3 local, inout vec3 n) {
    float cls = aColor.r;
    float t = aColor.b;
    float seed = aColor.a;
    vec4 A = uLimb[0];
    vec4 B = uLimb[1];
    vec3 cen = B.yzw;
    if (cls > 0.15 && cls < 0.35) {
        vec3 rn = normalize(local - cen);
        float sw = 0.004 * sin(2.0 * PI * (B.x + seed));
        vec3 dir = uLimb[2].yzw;
        vec3 toward = vec3(0.0);
        if (A.w > 0.001 && dot(dir, dir) > 1e-4) {
            vec3 d = normalize(dir);
            toward = A.w * (d - rn * dot(d, rn)) * 0.03 * max(dot(d, rn) + 0.3, 0.0);
        }
        local += t * (vec3(sw, 0.0, -sw) + toward);
    } else if (cls > 0.4 && cls < 0.6) {
        float ph = 2.0 * PI * (A.x + seed) - 2.0 * t;
        local += t * vec3(0.012 * sin(ph), 0.01 * cos(ph), 0.012 * cos(ph * 0.7));
        local = cen + (local - cen) * mix(1.0 - 0.35 * t, 1.0, clamp(A.y, 0.0, 1.0));
    } else if (cls > 0.65 && cls < 0.85) {
        float shown = clamp(A.z * 7.0 - t * 7.0, 0.0, 1.0);
        local = cen + (local - cen) * shown;
    }
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
        vColor = vec4(aColor.r, 0.0, aColor.ba);
        gl_Position = uViewProj * w;
        return;
    }

    float mode = uMotion.w;
    if (uLimbOn > 0.5) {
        // limb-rigged crawler: rigid body at the organism, limbs posed from the sim (no spline skinning)
        if (mode > 4.5) {
            // hermit: every part (shell, shield, eyes, limbs) carries its limb index
            hermitLimb(int(floor(aColor.b * 9.0 + 0.5)), local, n);
        } else if (part > 2.5) {
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
        vColor = vec4(aColor.r, 0.0, aColor.ba);
        gl_Position = uViewProj * vec4(wl, 1.0);
        return;
    }

    if (mode > 7.5 && mode < 9.5) {
        // snails and the urchin: a rigid body at the organism, soft parts posed from the sim (no spine)
        if (mode < 8.5) snailPose(local, n); else urchinPose(local, n);
        vec3 wl = uSpine[0].xyz + qrot(uFrame[0], local * uScale);
        vWorld = wl;
        vNormal = normalize(qrot(uFrame[0], n));
        vUv = aUv;
        vColor = vec4(aColor.r, 0.0, aColor.ba);
        gl_Position = uViewProj * vec4(wl, 1.0);
        return;
    }

    float u = aColor.a;
    float mouthDark = 0.0;
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

    if (mode > 3.5 && (part < 1.5 || part > 2.5)) {
        squidPose(part, hinge, local);
    } else if (part < 0.5) {
        // body: wrasse thinness and gill flare (opercular mask in G)
        local.yz *= 1.0 - 0.25 * uShape.x;
        float flare = 0.03 * uFin.z * aColor.g * (0.5 + 0.5 * sin(time * uFin.w * 2.0 * PI));
        local.z += sign(local.z) * flare;
        // opening jaw (models built with a mouth, uJaw.w = 1; uPick = the sim's mouth, 0 shut .. 1 wide): the lower jaw
        // (COLOR_0.b = 1) turns down about the hinge under the eye, the mouth lining's inner folds (0.5) half as far, and
        // the lining (COLOR_0.r) shades dark (vColor.g, read by fish.frag)
        if (uJaw.w > 0.5 && mode < 0.5) {
            float a = -clamp(uPick, 0.0, 1.0) * uJaw.z * aColor.b;
            vec2 r = aPos.xy - uJaw.xy;
            float c = cos(a), sn = sin(a);
            local.xy += vec2(c * r.x - sn * r.y, sn * r.x + c * r.y) - r;
            n.xy = vec2(c * n.x - sn * n.y, sn * n.x + c * n.y);
            mouthDark = aColor.r;
        }
    } else if (part < 1.5) {
        // fins: clamp folds toward the body (hinge-weighted, up to ~60 deg) and flutter
        float clampA = uFin.y * hinge;
        local.x -= clampA * 0.06;
        local.y *= 1.0 - 0.45 * clampA * step(abs(local.z), abs(local.y));
        local.z *= 1.0 - 0.45 * clampA * step(abs(local.y), abs(local.z));
        float flutter = 0.006 * sin(time * 9.0 + u * 6.0) * hinge * (1.0 - 0.7 * uFin.y);
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
            // On a surface the sim plants each foot instead (uLimb[1..6] = swing lift, foot offset; uLimb[11].y =
            // planted): the leg bends from its rest tip onto the foothold and the knee arches as it lifts or folds.
            float pl = uLimb[11].y;
            float ph = 2.0 * PI * (S.x + 0.5 * mod(pr + (side > 0.0 ? 1.0 : 0.0), 2.0));
            local.x += h * ((1.0 - pl) * 0.05 * S.y * sin(ph) + 0.004 * sin(time * 0.7 + pr * 1.7 + side));
            local.y += h * (1.0 - pl) * 0.03 * S.y * max(cos(ph), 0.0);
            if (pr > 1.5) {
                vec4 W = uLimb[1 + int(pr - 2.0) * 2 + (side > 0.0 ? 1 : 0)];
                local += W.yzw * h;
                local.y += sin(PI * h) * (0.125 * W.x + 0.6 * max(W.z, 0.0));   // W.x = lift × 0.4
            }
        } else if (cls < 2.5) {
            // picking legs (P1-P2): alternate left/right strokes to the mouth while feeding or cleaning; the
            // front pair rocks rapidly back and forth as the skunk cleaner's signal; P1 combs the antenna in grooming
            // While cleaning or picking at rock the sim drives each claw tip (uLimb[7..10] = grip, tip offset,
            // uLimb[11].x = weight): out to the skin, a tug, and back to the mouth.
            float pk = uLimb[11].x;
            vec4 C = uLimb[7 + int(min(pr, 1.0)) * 2 + (side > 0.0 ? 1 : 0)];
            local += C.yzw * h;
            local.y += sin(PI * h) * 0.03 * pk;
            float k = 0.5 + 0.5 * sin(2.0 * PI * 2.2 * time + (side > 0.0 ? 0.0 : PI) + pr * 1.3);
            float pick = max(uPick, 0.0) * (1.0 - pk);
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
            // The sim drives each claw tip while it probes crevices or strikes (uLimb[13] left, [14] right).
            local += uLimb[side > 0.0 ? 14 : 13].yzw * h;
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
            // lifted clear of rock just ahead, draped along a client's flank while cleaning (uLimb[11].z left, .w right)
            local.y += h2 * 0.25 * (side > 0.0 ? uLimb[11].w : uLimb[11].z);
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

    // Life-stage proportions (a juvenile engineer goby is stubbier than the eel-like adult): scale depth and width
    // about the body axis before the wave and skinning; normals take the inverse scale.
    if (abs(uStageShape.x) + abs(uStageShape.y) > 0.0001) {
        vec2 k = vec2(1.0) + uStageShape.xy;
        local.y *= k.x; local.z *= k.y;
        n = normalize(vec3(n.x, n.y / k.x, n.z / k.y));
    }

    // Lateral body wave: z += A(u) sin(2 pi (u waveLen - phase)), A(u) = amp (h + (1 - h) u^k), h and k per body type (uEnv). The amplitude is
    // capped and faded while the body is strongly curved (head vs tail heading) so the wave cannot fold the tail
    // back into the body on a tight turn.
    vec3 fwdHead = qrot(uFrame[0], vec3(1.0, 0.0, 0.0));
    vec3 fwdTail = qrot(uFrame[15], vec3(1.0, 0.0, 0.0));
    float bendFade = mix(0.35, 1.0, smoothstep(-0.2, 0.85, dot(fwdHead, fwdTail)));
    float amp = min(uSwim.y, MAX_WAVE_AMP) * bendFade;
    float A = amp * (uEnv.y + (1.0 - uEnv.y) * pow(uw, uEnv.x));
    float arg = 2.0 * PI * (uw * uSwim.z - uSwim.x);
    local.z += A * sin(arg);
    // slope dz/du tilts the normal (cheap analytic correction instead of a finite difference)
    float dzdu = amp * (1.0 - uEnv.y) * uEnv.x * pow(uw, uEnv.x - 1.0) * sin(arg) + A * cos(arg) * 2.0 * PI * uSwim.z;
    // Escape C-start (Domenici & Blake 1997): head and tail curl to the same side, the middle the other way.
    // Shrimp (mode 2) flex the abdomen ventrally instead (caridoid tail-flip).
    float bend = uMotion.z;
    if (mode > 1.5 && mode < 3.5) {
        float ab = max(uw - 0.4, 0.0);
        local.y -= 0.9 * max(bend, 0.0) * ab * ab;
        local.x -= 0.4 * max(bend, 0.0) * ab * ab;
    }
    if (mode > 1.5 && mode < 2.5) {
        // shrimp seated on its legs (core ShrimpLimbs, uLimb[12] = drop, pitch, roll): the body settles onto the
        // footholds under it
        vec4 St = uLimb[12];
        local.y -= St.y + St.z * (local.x + 0.5 - uw + 0.1) + St.w * local.z;
    } else if (mode < 0.5 && abs(bend) > 0.001) {
        float e = uw - 0.35;
        local.z += bend * 0.55 * (e * e - 0.04);
        dzdu += bend * 0.55 * 2.0 * e;
    }
    // u runs snout → tail (u = 0.5 - x), so dz/dx = -dz/du and the surface normal tilts by +dz/du
    n = normalize(vec3(n.x + dzdu * n.z, n.y, n.z));

    vec3 sp;
    vec4 q;
    sampleSpine(uw, sp, q);
    vec3 world = sp + qrot(q, local * uScale);
    vWorld = world;
    vNormal = normalize(qrot(q, n));
    vUv = aUv;
    vColor = aColor;
    vColor.g = mouthDark;
    gl_Position = uViewProj * vec4(world, 1.0);
}
