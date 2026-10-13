#version 300 es
#include "common.glsl"
#include "lighting.glsl"

// Point-sprite shapes (keep the ids in step with FoodVisuals.kt `Shape`): every food has its own silhouette, and the
// action bursts (dye plumes, bubbles, jets, sparkles) theirs. Shapes are 2D signed distances in the sprite's
// rotated coordinates, anti-aliased with fwidth; sprites under ~5 px fall back to the soft dot.

in vec4 vColor;
in vec3 vWorld;
flat in float vShapeId;
in vec3 vShape;
in float vPx;

out vec4 fragColor;

float sdSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}
float sdEll(vec2 p, vec2 r) { return (length(p / r) - 1.0) * min(r.x, r.y); }
float fill(float d) { return clamp(0.5 - d / max(fwidth(d), 1e-3), 0.0, 1.0); }
float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(c, c);
    int shape = int(vShapeId + 0.5);
    if (shape != 5 && shape != 14 && shape != 15 && vPx < 5.0) shape = 0;   // too small to read: a dot
    float seed = vShape.x;
    float ca = cos(vShape.y), sa = sin(vShape.y);
    vec2 p = vec2(ca * c.x + sa * c.y, -sa * c.x + ca * c.y);
    float param = vShape.z;

    vec3 base = vColor.rgb;
    float m = 0.0;          // coverage
    float aMul = 1.0;
    bool lit = true;

    if (shape == 0) {                       // soft dot (ambient specks, bubbles, sand clouds)
        if (r2 > 1.0) discard;
        m = 1.0 - smoothstep(0.35, 1.0, r2);
    } else if (shape == 1) {                // flake: thin, irregular, translucent, tumbling (param = visible width)
        vec2 q = vec2(p.x, p.y / max(param, 0.12));
        float th = atan(q.y, q.x);
        float r = 0.62 + 0.15 * sin(th * 3.0 + seed * 21.0) + 0.08 * sin(th * 5.0 + seed * 37.0);
        float d = length(q) - r;
        m = fill(d * max(param, 0.12));
        base *= mix(1.08, 0.72, smoothstep(-0.25, 0.0, d));
        aMul = 0.82;
    } else if (shape == 2) {                // pellet: a lit hard sphere
        float r = 0.85;
        float d = length(c) - r;
        m = fill(d);
        vec3 n = vec3(c / r, sqrt(max(0.0, 1.0 - r2 / (r * r))));
        float shade = 0.45 + 0.55 * max(dot(n, normalize(vec3(-0.4, 0.65, 0.65))), 0.0);
        base = base * shade + vec3(0.35) * pow(max(dot(n, normalize(vec3(-0.3, 0.5, 0.8))), 0.0), 20.0);
    } else if (shape == 3) {                // mysis: a pale, curled, see-through shrimp with dark eyes
        float rr = length(p);
        float th = atan(p.y, p.x);
        float s = clamp((th + 2.3) / 4.6, 0.0, 1.0);
        float d = abs(rr - 0.52) - mix(0.07, 0.19, s);
        if (abs(th) > 2.3) d = max(d, 0.3);
        vec2 eye = 0.6 * vec2(cos(2.2), sin(2.2));
        float eyes = 1.0 - smoothstep(0.06, 0.1, length(p - eye));
        m = max(fill(d), eyes);
        float gut = (rr - 0.52) / 0.05;
        base = mix(base * (1.0 - 0.3 * exp(-gut * gut) * s), vec3(0.03), eyes);
        aMul = 0.78;
    } else if (shape == 4) {                // copepod: teardrop body, tail and two antennae
        float d = sdEll(p - vec2(0.1, 0.0), vec2(0.45, 0.26));
        d = min(d, sdSeg(p, vec2(-0.3, 0.0), vec2(-0.85, 0.0)) - 0.05);
        d = min(d, sdSeg(p, vec2(0.45, 0.05), vec2(0.85, 0.55)) - 0.035);
        d = min(d, sdSeg(p, vec2(0.45, -0.05), vec2(0.85, -0.55)) - 0.035);
        m = fill(d);
        float eye = 1.0 - smoothstep(0.05, 0.09, length(p - vec2(0.42, 0.0)));
        base = mix(base, vec3(0.6, 0.05, 0.02), eye * param);
        aMul = 0.92;
    } else if (shape == 5) {                // haze puff: phyto, coral powder, dye, silt
        if (r2 > 1.0) discard;
        m = exp(-r2 * 3.2) * (1.0 - smoothstep(0.75, 1.0, r2));
        m *= 0.8 + 0.2 * sin(p.x * 5.0 + seed * 10.0) * sin(p.y * 4.0 + seed * 7.0);
    } else if (shape == 6) {                // adult brine shrimp: slender, orange, forked tail (param = wiggle)
        vec2 q = vec2(p.x, p.y + param * 0.35 * p.x * p.x);
        float t = clamp((q.x + 0.7) / 1.35, 0.0, 1.0);
        float d = sdSeg(q, vec2(-0.7, 0.0), vec2(0.62, 0.0)) - mix(0.05, 0.14, t);
        d = min(d, sdSeg(q, vec2(-0.68, 0.0), vec2(-0.92, 0.16)) - 0.03);
        d = min(d, sdSeg(q, vec2(-0.68, 0.0), vec2(-0.92, -0.16)) - 0.03);
        float eyes = 1.0 - smoothstep(0.045, 0.08, min(length(q - vec2(0.64, 0.11)), length(q - vec2(0.64, -0.11))));
        m = max(fill(d), eyes);
        base = mix(base * (0.85 + 0.15 * sin(q.x * 30.0)), vec3(0.02), eyes);
        aMul = 0.88;
    } else if (shape == 7) {                // krill piece: curved, banded, black eyes
        vec2 q = vec2(p.x, p.y + 0.28 * p.x * p.x);
        float t = clamp((q.x + 0.75) / 1.35, 0.0, 1.0);
        float d = sdSeg(q, vec2(-0.75, 0.0), vec2(0.58, 0.0)) - mix(0.09, 0.22, t);
        d = min(d, sdSeg(q, vec2(-0.72, 0.0), vec2(-0.95, 0.17)) - 0.06);
        d = min(d, sdSeg(q, vec2(-0.72, 0.0), vec2(-0.95, -0.17)) - 0.06);
        float eyes = 1.0 - smoothstep(0.07, 0.11, length(q - vec2(0.58, 0.12)));
        m = max(fill(d), eyes);
        base = mix(base * (0.82 + 0.18 * sin(q.x * 24.0)), vec3(0.01), eyes);
    } else if (shape == 8) {                // prawn chunk: blocky pink lump with pale flesh bands
        float th = atan(p.y, p.x);
        float r = 0.68 + 0.12 * sin(th * 3.0 + seed * 17.0) + 0.07 * sin(th * 5.0 + seed * 29.0);
        float l = mix(length(p), max(abs(p.x), abs(p.y)) * 1.12, 0.55);
        m = fill(l - r);
        float band = smoothstep(0.4, 0.9, sin(p.x * 8.0 + p.y * 3.0 + seed * 10.0));
        base = mix(base, vec3(0.92, 0.84, 0.78), 0.45 * band) * (0.8 + 0.2 * (1.0 - l));
    } else if (shape == 9) {                // algae wafer: dark green disc, rim, speckles (param = visible width)
        vec2 q = vec2(p.x, p.y / max(param, 0.15));
        float d = length(q) - 0.86;
        m = fill(d * max(param, 0.15));
        float speck = step(0.82, hash2(floor(q * 7.0) + seed * 13.0));
        base = mix(base * mix(1.0, 0.6, smoothstep(-0.18, 0.0, d)), base * 2.2 + vec3(0.03, 0.03, 0.0), speck * 0.6);
    } else if (shape == 10) {               // cyclops: tiny red teardrop with a bright eye spot
        float d = sdEll(p - vec2(0.1, 0.0), vec2(0.5, 0.36));
        d = min(d, sdSeg(p, vec2(-0.3, 0.0), vec2(-0.8, 0.0)) - 0.06);
        m = fill(d);
        float eye = 1.0 - smoothstep(0.07, 0.12, length(p - vec2(0.38, 0.0)));
        base = mix(base, vec3(1.0, 0.05, 0.02), eye);
    } else if (shape == 11) {               // frozen cube: frosted rounded block, edges going ragged as it thaws
        float th = atan(p.y, p.x);
        vec2 q = abs(p) - vec2(0.6);
        float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.12;
        d += param * 0.16 * sin(th * 7.0 + seed * 30.0);
        m = fill(d);
        float frost = smoothstep(-0.35, 0.0, d) * (1.0 - 0.7 * param);
        base = mix(base, vec3(0.88, 0.92, 0.98), 0.6 * frost) + vec3(0.1) * step(p.x + p.y, -0.5);
        aMul = 0.95 - 0.35 * param;
    } else if (shape == 12) {               // nori: a translucent dark-green sheet tile with torn edges and fibres
        vec2 q = abs(p) - vec2(0.8);
        float d = max(q.x, q.y) + 0.1 * sin(p.x * 9.0 + seed * 40.0) * sin(p.y * 7.0 + seed * 17.0);
        m = fill(d);
        base *= 0.82 + 0.18 * sin((p.x * 0.6 + p.y) * 25.0 + seed * 50.0);
        aMul = 0.8;
    } else if (shape == 13) {               // bubble: thin bright ring with a highlight
        float d = abs(length(c) - 0.72) - 0.13;
        float hi = 1.0 - smoothstep(0.08, 0.2, length(c - vec2(-0.3, -0.3)));
        m = max(fill(d), hi);
        base = mix(base, vec3(1.0), hi);
        aMul = 0.7;
    } else if (shape == 14) {               // glow: emissive soft core (sparkles, UV, sparks)
        if (r2 > 1.0) discard;
        m = exp(-r2 * 5.0);
        lit = false;
    } else if (shape == 15) {               // streak: a soft line along the sprite's x (jets, swirls, falling grains)
        float d = sdSeg(p, vec2(-0.9, 0.0), vec2(0.9, 0.0));
        m = (1.0 - smoothstep(0.0, 0.16, d)) * (0.35 + 0.65 * clamp(p.x * 0.5 + 0.5, 0.0, 1.0));
    } else {
        if (r2 > 1.0) discard;
        m = 1.0 - smoothstep(0.35, 1.0, r2);
    }
    if (m < 0.01) discard;

    vec3 col;
    if (lit) {
        vec3 L;
        vec3 rad = fixtureLight(0, vWorld, L) + fixtureLight(1, vWorld, L);
        col = base * (rad * 0.9 + hemiAmbient(vec3(0.0, 1.0, 0.0)) * 2.0);
    } else {
        col = base * 2.5;
    }
    col = applyWater(col, vWorld);
    float a = vColor.a * m * aMul * mix(1.0, 0.5, uNight);
    fragColor = vec4(tonemap(col), a);
}
