#version 300 es
#include "common.glsl"
#include "lighting.glsl"
const float ROOM_EXPOSURE = 0.4;
// My tank's room (HomeRoom.kt, tools/blender/rooms.py) and the stand under the tank (tools/blender/stands.py). The
// room has its own soft light, not the AI Prime fixtures: daylight from the room (uDay: 1 at noon, 0 at night) as a
// warm key from the front left and a hemisphere fill, a floor lamp that glows in the evening, and the tank's own
// light spilling out of its open top onto the wall and ceiling and out of the front glass onto the floor. Corners,
// the floor under the stand and the wall behind the tank are softly shaded; far patterns fade out and colours are a
// little muted, so the room frames the tank without pulling the eye from it.
//
// Kinds (HomeRoomScene.kindOf, from the material name): 0 own colour, 1 stand paint (uPaint), 2 stand trim (uTrim),
// 3 metal (steel, or gold with uBrass), 4 dark insides, 5 glow strip (uGlowOn), 6 sump window, 7 floor, 8 wall,
// 9 ceiling, 10 lamp shade, 11 rug, 12 leaves, 13 clear plastic / glass, 14 a window's outside view (uOutside: the
// scene's view, drawn procedurally and softly, as if a little out of focus), 15 fire. COLOR_0.r is baked occlusion. Walls and
// floor take their pattern and two colours from the room shop (uWallStyle / uFloorStyle, core room/Decor.kt RoomStyle).

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAlbedo;
uniform vec4 uBaseColor;
uniform float uHasTexture;
uniform float uRoughness;
uniform vec3 uEmissive;
uniform float uAlphaCutoff;
uniform float uKind;
uniform float uGrain;         // 1: wood grain (material named *wood* / *plank*)
uniform vec3 uPaint;          // stand colours (linear)
uniform vec3 uTrim;
uniform float uBrass;         // 1: gold handles
uniform float uGlowOn;        // this glow part is lit (the Glow strip extra, or the reef pro's own edge light)
uniform float uGlowStrip;     // the Glow strip extra is on: its light on the floor
uniform float uDay;           // room daylight 0..1
uniform vec4 uLampPos;        // xyz the floor lamp's bulb (cm), w 1 when the room has one
uniform vec3 uLampCol;        // its colour (linear, x brightness)
uniform vec4 uTankBox;        // x length, y top of the glass, z depth, w unused (the glass box: x 0..len, z 0..depth)
uniform vec4 uFoot;           // stand footprint on the floor: x0, x1, z0, z1
uniform vec4 uRoomBox;        // room: x0, x1, back z, floor y
uniform float uFloorStyle;    // RoomStyle: 0 planks, 1 tile, 2 checker, 3 carpet, 4 slate, 5 metal deck, 6 tatami
uniform float uWallStyle;     // RoomStyle: 0 paint, 1 stripes, 2 waves, 3 brick, 4 panels, 5 logs, 6 metal, 7 boards
uniform vec3 uFloorA;         // the floor's two colours (linear)
uniform vec3 uFloorB;
uniform vec3 uWallA;          // the walls' two colours (linear)
uniform vec3 uWallB;
uniform float uOutside;       // kind 14: 1 garden, 2 beach, 3 city, 4 snowy pines, 5 cherry blossom, 6 space

out vec4 fragColor;

const float FLOOR_Y = -82.0;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

// Rounded-box distance in 2D: < 0 inside.
float boxDist(vec2 p, vec2 lo, vec2 hi) {
    vec2 c = (lo + hi) * 0.5, h = (hi - lo) * 0.5;
    vec2 q = abs(p - c) - h;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
}

// Oak planks: 14 cm boards along x, staggered 120 cm lengths, per-board tone and a stretched grain. [fw] is the
// pixel footprint: seams and grain fade out where they would alias (far away, the "out of focus" floor).
vec3 planks(vec3 p, vec3 base, float fw) {
    float row = floor(p.z / 14.0);
    float off = hash12(vec2(row, 3.0)) * 120.0;
    float col = floor((p.x + off) / 120.0);
    vec2 f = vec2(fract((p.x + off) / 120.0) * 120.0, fract(p.z / 14.0) * 14.0);
    float seam = min(min(f.x, 120.0 - f.x), min(f.y, 14.0 - f.y));
    float aa = clamp(fw * 1.5, 0.05, 6.0);
    float line = 1.0 - smoothstep(0.0, 0.25 + aa, seam);
    float fade = 1.0 - smoothstep(0.6, 2.5, fw);
    float tone = mix(0.86, 1.1, hash12(vec2(row, col)));
#if QUALITY > 0
    float grain = vnoise(vec3(p.x * 0.04, p.z * 1.3, row * 3.1)) * 0.6 + vnoise(vec3(p.x * 0.11, p.z * 3.0, col)) * 0.4;
#else
    float grain = 0.5;   // Low: board tones and seams only
#endif
    vec3 c = base * mix(1.0, tone * mix(0.9, 1.08, grain), fade);
    return c * (1.0 - 0.45 * line * fade);
}

// A seam line [d] cm from the nearest joint, faded out with distance (the pixel footprint [fw]).
float seamLine(float d, float width, float fw) {
    return (1.0 - smoothstep(0.0, width + clamp(fw * 1.5, 0.05, 6.0), d)) * (1.0 - smoothstep(0.6, 2.5, fw));
}

// The floor's pattern (uFloorStyle) in its two colours.
vec3 floorPattern(vec3 p, float fw) {
    int st = int(uFloorStyle + 0.5);
    vec3 a = uFloorA, b = uFloorB;
    float fade = 1.0 - smoothstep(0.6, 2.5, fw);
    if (st == 1 || st == 2) {
        // square tiles with grout (1), or a checkerboard (2)
        float s = st == 1 ? 40.0 : 30.0;
        vec2 c = floor(p.xz / s), f = fract(p.xz / s) * s;
        float g = min(min(f.x, s - f.x), min(f.y, s - f.y));
        vec3 t = st == 1 ? a * mix(0.95, 1.04, hash12(c)) : (mod(c.x + c.y, 2.0) < 0.5 ? a : b);
        return mix(t, st == 1 ? b : t * 0.7, seamLine(g, st == 1 ? 0.5 : 0.15, fw));
    }
    if (st == 3) return a * mix(1.0, mix(0.9, 1.07, vnoise(vec3(p.xz * 0.6, 0.0))) * mix(0.94, 1.04, vnoise(vec3(p.xz * 0.05, 2.0))), fade);
    if (st == 4) {
        // slate: staggered 60 x 30 stones, each its own tone between the two colours
        float row = floor(p.z / 30.0);
        float x = p.x + mod(row, 2.0) * 30.0;
        vec2 c = vec2(floor(x / 60.0), row), f = vec2(fract(x / 60.0) * 60.0, fract(p.z / 30.0) * 30.0);
        float g = min(min(f.x, 60.0 - f.x), min(f.y, 30.0 - f.y));
        vec3 t = mix(a, b, hash12(c)) * mix(1.0, mix(0.92, 1.06, vnoise(vec3(p.xz * 0.15, c.x))), fade);
        return mix(t, t * 0.45, seamLine(g, 0.4, fw));
    }
    if (st == 5) {
        // metal deck: 50 cm plates, a faint diamond tread and darker seams
        vec2 f = fract(p.xz / 50.0) * 50.0;
        float g = min(min(f.x, 50.0 - f.x), min(f.y, 50.0 - f.y));
        vec2 d = fract(vec2(p.x + p.z, p.x - p.z) / 4.0) - 0.5;
        float tread = smoothstep(0.32, 0.22, length(d)) * fade;
        return mix(a * (1.0 + 0.08 * tread), b, seamLine(g, 0.4, fw));
    }
    if (st == 6) {
        // tatami: 90 x 180 mats in a staggered weave, straw lines along each, a dark cloth edge on the long sides
        float row = floor(p.z / 90.0);
        float x = p.x + mod(row, 2.0) * 90.0;
        vec2 f = vec2(fract(x / 180.0) * 180.0, fract(p.z / 90.0) * 90.0);
        float straw = 0.5 + 0.5 * sin(f.y * 3.1);
        vec3 t = a * mix(1.0, mix(0.93, 1.03, straw), fade);
        float edge = min(f.y, 90.0 - f.y);
        t = mix(t, b, 1.0 - smoothstep(3.0, 3.6 + fw, edge));
        return mix(t, t * 0.6, seamLine(min(f.x, 180.0 - f.x), 0.3, fw));
    }
    return planks(p, a, fw);
}

// The walls' pattern (uWallStyle) in their two colours. [u] runs along the wall, [y] up it.
vec3 wallPattern(vec3 p, vec3 n, float fw) {
    int st = int(uWallStyle + 0.5);
    vec3 a = uWallA, b = uWallB;
    float u = abs(n.x) > 0.5 ? p.z : p.x;
    float y = p.y - FLOOR_Y;
    float fade = 1.0 - smoothstep(0.6, 2.5, fw);
    float soft = clamp(fw * 1.5, 0.05, 6.0);
    if (st == 1) {
        // wide stripes with a thin pinstripe between
        float f = fract(u / 24.0) * 24.0;
        float band = smoothstep(11.5 - soft, 11.5 + soft, f) * (1.0 - smoothstep(23.5 - soft, 23.5 + soft, f));
        return mix(a, b, band * mix(0.6, 1.0, fade));
    }
    if (st == 2) {
        // wave print: rows of little scallops
        vec2 q = vec2(u / 16.0, y / 10.0);
        float row = floor(q.y);
        vec2 f = vec2(fract(q.x + mod(row, 2.0) * 0.5) - 0.5, fract(q.y));
        float r = length(vec2(f.x, f.y * 0.62));
        float ring = smoothstep(0.08 + soft * 0.05, 0.0, abs(r - 0.42));
        return mix(a, b, ring * fade * 0.6);
    }
    if (st == 3) {
        // brick: 21 x 7 cm in running bond, each its own tone, pale mortar
        float row = floor(y / 7.5);
        float x = u + mod(row, 2.0) * 11.0;
        vec2 c = vec2(floor(x / 22.0), row), f = vec2(fract(x / 22.0) * 22.0, fract(y / 7.5) * 7.5);
        float g = min(min(f.x, 22.0 - f.x), min(f.y, 7.5 - f.y));
        vec3 t = a * mix(0.82, 1.12, hash12(c)) * mix(1.0, mix(0.9, 1.06, vnoise(vec3(u * 0.4, y * 0.4, 1.0))), fade);
        return mix(t, b, seamLine(g, 0.6, fw));
    }
    if (st == 4 || st == 7) {
        // wood panels (vertical boards, 4) or painted boards (horizontal, 7) with dark gaps
        float w = st == 4 ? 15.0 : 16.0;
        float q = st == 4 ? u : y;
        float c = floor(q / w), f = fract(q / w) * w;
        vec3 t = st == 4 ? mix(a, b, hash12(vec2(c, 5.0)) * 0.6) : a * mix(0.97, 1.03, hash12(vec2(c, 7.0)));
        return mix(t, st == 4 ? b * 0.45 : b * 0.85, seamLine(min(f, w - f), st == 4 ? 0.3 : 0.25, fw));
    }
    if (st == 5) {
        // logs: round 22 cm logs, lit across their curve, dark chinking between
        float f = fract(y / 22.0);
        float c = floor(y / 22.0);
        float round = sin(f * 3.14159);
        vec3 t = mix(b, a, sqrt(max(round, 0.0))) * mix(0.9, 1.08, hash12(vec2(c, 9.0)));
        t *= mix(1.0, mix(0.9, 1.06, vnoise(vec3(u * 0.05, c, 3.0))), fade);
        return mix(t, b * 0.35, (1.0 - smoothstep(0.0, 0.07 + soft * 0.02, round)) * fade);
    }
    if (st == 6) {
        // metal: 80 x 50 cm panels with seams and rivets, a darker band at hand height
        vec2 f = vec2(fract(u / 80.0) * 80.0, fract(y / 50.0) * 50.0);
        float g = min(min(f.x, 80.0 - f.x), min(f.y, 50.0 - f.y));
        vec3 t = mix(a, b, smoothstep(88.0, 89.0, y) * (1.0 - smoothstep(118.0, 119.0, y)));
        float rivet = length(vec2(min(f.x, 80.0 - f.x) - 3.0, (fract(y / 10.0) - 0.5) * 10.0));
        t *= 1.0 - 0.25 * smoothstep(0.9, 0.5, rivet) * fade;
        return mix(t, t * 0.5, seamLine(g, 0.4, fw));
    }
    return a * mix(0.97, 1.03, vnoise(p * 0.05));
}

// What a window looks onto (uOutside), along [dir] from the eye: soft shapes only, so it reads as out of focus.
vec3 outsideView(vec3 dir, float day) {
    int v = int(uOutside + 0.5);
    float az = atan(dir.x, -dir.z);
    float el = dir.y / max(length(dir.xz), 1e-3);
    float night = 1.0 - day;
    vec3 zen = mix(vec3(0.010, 0.016, 0.045), vec3(0.22, 0.46, 0.80), day);
    vec3 hor = mix(vec3(0.030, 0.040, 0.080), vec3(0.72, 0.82, 0.90), day);
    vec3 sky = mix(hor, zen, smoothstep(-0.05, 0.7, el));
    float stars = step(0.9965, hash12(floor(vec2(az, el) * 260.0))) * night * smoothstep(0.0, 0.2, el);
    if (v == 6) {
        // space: stars and the slowly turning Earth below, with its thin blue air
        vec3 c = vec3(0.004, 0.006, 0.014) + step(0.992, hash12(floor(vec2(az, el) * 300.0))) * vec3(0.9);
        vec2 e = vec2(az + 0.3, el - 0.12);
        float r = length(e) / 0.3;
        if (r < 1.0) {
            vec2 g = e / 0.3;
            vec3 sp = vec3(g, sqrt(1.0 - r * r));
            float lon = atan(sp.x, sp.z) + uTime * 0.01;
            vec3 q = vec3(lon * 2.0, sp.y * 3.0, 0.0);
            float land = smoothstep(0.55, 0.62, vnoise(q * 1.3) * 0.7 + vnoise(q * 3.1) * 0.3);
            float cloud = smoothstep(0.55, 0.75, vnoise(q * 2.3 + vec3(uTime * 0.005, 0.0, 4.0)));
            vec3 ground = mix(vec3(0.03, 0.12, 0.32), vec3(0.20, 0.30, 0.14), land);
            float lit = smoothstep(-0.25, 0.4, dot(sp, normalize(vec3(-0.7, 0.4, 0.6))));
            c = mix(ground, vec3(0.75), cloud * 0.8) * (0.05 + 0.65 * lit);
        }
        c += vec3(0.15, 0.35, 0.9) * exp(-abs(r - 1.0) * 30.0) * 0.6;
        return c;
    }
    if (v == 3) {
        // city: two rows of towers with lit windows (more of them at night), a warm glow over the streets
        vec3 c = sky + stars + vec3(0.12, 0.06, 0.03) * night * exp(-max(el, 0.0) * 6.0);
        for (int layer = 0; layer < 2; layer++) {
            float n = layer == 0 ? 26.0 : 12.0;
            float x = az * n + float(layer) * 7.3;
            float id = floor(x);
            float h = (layer == 0 ? 0.05 : 0.0) + hash12(vec2(id, float(layer))) * (layer == 0 ? 0.28 : 0.42) - 0.08;
            if (el < h) {
                vec3 wall = mix(vec3(0.02, 0.025, 0.04), hor * 0.55, day) * (layer == 0 ? 1.25 : 1.0);
                vec2 w = vec2(fract(x) * 7.0, el * 120.0);
                vec2 wc = floor(w);
                float inWin = step(0.25, fract(w.x)) * step(0.35, fract(w.y)) * step(0.8, fract(x) * 7.0) * step(fract(x) * 7.0, 6.2);
                float on = step(mix(0.9, 0.78, night), hash12(wc + id * 13.0));
                c = wall + inWin * on * mix(vec3(0.04, 0.05, 0.06), vec3(1.0, 0.72, 0.38) * (layer == 0 ? 0.3 : 0.5), night);
            }
        }
        return c;
    }
    vec3 c = sky + stars;
    if (v == 2) {
        // beach: sun or moon, the sea with glints, foam rolling in, then sand
        vec2 sd = vec2(az - 0.45, el - 0.28);
        c += mix(vec3(0.9, 0.9, 1.0) * 0.6, vec3(1.0, 0.85, 0.55) * 1.5, day) * smoothstep(0.06, 0.035, length(sd));
        c += mix(vec3(0.0), vec3(1.0, 0.8, 0.5) * 0.25, day) * exp(-length(sd) * 6.0);
        if (el < 0.0) {
            float t = clamp(-el / 0.13, 0.0, 1.0);
            vec3 sea = mix(mix(vec3(0.01, 0.03, 0.06), vec3(0.16, 0.48, 0.66), day), mix(vec3(0.02, 0.05, 0.08), vec3(0.20, 0.62, 0.66), day), t);
            float glint = step(0.985, hash12(floor(vec2(az * 220.0, el * 900.0 + uTime * 0.7)))) * mix(0.3, 1.0, day);
            sea += glint * vec3(0.9) * smoothstep(0.4, 0.0, abs(az - 0.45));
            float shore = -0.13 + 0.008 * sin(uTime * 0.5 + az * 6.0);
            vec3 sand = mix(vec3(0.05, 0.045, 0.04), vec3(0.86, 0.76, 0.58), day);
            c = el > shore ? sea : sand * (1.0 + 0.06 * vnoise(vec3(az * 30.0, el * 60.0, 0.0)));
            c = mix(c, mix(vec3(0.15), vec3(0.95), day), smoothstep(0.012, 0.0, abs(el - shore - 0.004)) * 0.8);
        }
        return c;
    }
    vec3 ground = v == 4 ? mix(vec3(0.05, 0.06, 0.09), vec3(0.88, 0.91, 0.95), day) : mix(vec3(0.01, 0.02, 0.01), vec3(0.28, 0.48, 0.18), day);
    if (v == 4) c = mix(c, mix(vec3(0.04, 0.05, 0.08), vec3(0.70, 0.74, 0.80), day), 0.6);       // overcast
    // distant hills, then a row of trees (pines in the snow, round crowns in the garden, pink blossom)
    float hill = 0.03 + 0.04 * vnoise(vec3(az * 2.0, 0.0, 1.0));
    if (el < hill) c = mix(ground, mix(c, ground, 0.5), smoothstep(-0.02, hill, el)) * (v == 4 ? 1.0 : 0.85);
    float x = az * 7.0;
    float id = floor(x), f = fract(x) - 0.5;
    float top = 0.12 + 0.12 * hash12(vec2(id, 3.0));
    if (v == 4) {
        float w = (top - el) * 0.9;
        if (el > -0.02 && abs(f) < w) {
            float snowy = step(0.5, fract((top - el) * 18.0)) * 0.5 + 0.2;
            c = mix(mix(vec3(0.01, 0.02, 0.02), vec3(0.10, 0.20, 0.16), day), ground, snowy);
        }
        // falling snow
        float flake = step(0.993, hash12(floor(vec2(az * 90.0, el * 90.0 + uTime * 0.6))));
        c += flake * mix(0.25, 0.9, day);
    } else {
        vec2 q = vec2(f * 1.4, (el - top + 0.06) * 6.0);
        float crown = length(q) + 0.25 * vnoise(vec3(az * 30.0, el * 30.0, id));
        float trunk = step(abs(f), 0.04) * step(-0.01, el) * step(el, top - 0.1);
        vec3 leaf = v == 5 ? mix(vec3(0.06, 0.03, 0.04), vec3(0.96, 0.66, 0.76), day) : mix(vec3(0.01, 0.02, 0.01), vec3(0.18, 0.38, 0.14), day);
        leaf *= mix(0.85, 1.1, vnoise(vec3(az * 40.0, el * 40.0, 1.0)));
        if (trunk > 0.5) c = mix(vec3(0.01), vec3(0.22, 0.15, 0.10), day);
        if (crown < 0.5) c = leaf;
        if (v == 5) {
            float petal = step(0.994, hash12(floor(vec2(az * 80.0 + uTime * 0.2, el * 80.0 + uTime * 0.4))));
            c = mix(c, vec3(1.0, 0.75, 0.82) * mix(0.2, 1.0, day), petal);
        }
    }
    return c;
}

// Wood grain for stand surfaces: rings along the board's long axis (the larger of x / z on a face).
float woodGrain(vec3 p, vec3 n) {
    vec3 q = abs(n.y) > 0.5 ? p.xzy : (abs(n.x) > 0.5 ? p.zyx : p.xyz);
    float g = vnoise(vec3(q.x * 0.05, q.y * 0.9, q.z * 0.9)) * 0.7 + vnoise(vec3(q.x * 0.2, q.y * 3.0, 1.7)) * 0.3;
    return mix(0.85, 1.12, g);
}

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    float alpha = tex.a * uBaseColor.a;
    int kind = int(uKind + 0.5);
    vec3 p = vWorld;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - p);
    if (dot(N, V) < 0.0 && kind != 12) N = -N;
    float fw = length(fwidth(p));
    float ao = clamp(vColor.r, 0.0, 1.0);
    if (vColor.a < 0.01) ao = 1.0;

    // ---- surface colour
    float rough = uRoughness;
    float metal = 0.0;
    if (kind == 1) albedo = uPaint;
    else if (kind == 2) albedo = uTrim;
    else if (kind == 3) { albedo = uBrass > 0.5 ? vec3(0.95, 0.68, 0.28) : vec3(0.60, 0.61, 0.63); metal = 1.0; rough = uBrass > 0.5 ? 0.22 : 0.3; }
    else if (kind == 7) albedo = floorPattern(p, fw);
    else if (kind == 8) albedo = wallPattern(p, N, fw);
    if (uGrain > 0.5 && (kind == 1 || kind == 2 || kind == 0)) albedo *= mix(1.0, woodGrain(p, N), 1.0 - smoothstep(0.4, 2.0, fw));
    if (kind == 11) {
        // wool rug: a soft woven texture and a darker border band
        vec2 q = p.xz;
        float edge = min(min(q.x - (uFoot.x + uFoot.y) * 0.5 + 110.0, (uFoot.x + uFoot.y) * 0.5 + 110.0 - q.x), min(q.y - 70.0, 230.0 - q.y));
        albedo *= mix(0.72, 1.0, smoothstep(9.0, 11.0, edge)) * mix(1.0, 0.82, smoothstep(14.0, 15.5, edge) * (1.0 - smoothstep(17.0, 18.5, edge)));
        albedo *= mix(0.93, 1.05, vnoise(vec3(q * 0.9, 0.0)));
    }

    // ---- light
    float day = uDay;
    vec3 sky = mix(vec3(0.034, 0.038, 0.052), vec3(0.50, 0.49, 0.46), day);
    vec3 bounce = mix(vec3(0.010, 0.010, 0.012), vec3(0.24, 0.20, 0.16), day);
    vec3 keyCol = vec3(1.0, 0.91, 0.78) * 1.25 * day;
    vec3 Lk = normalize(vec3(-0.45, 0.75, 0.5));
    vec3 diff = albedo * (1.0 - metal);
    vec3 F0 = mix(vec3(0.04), albedo, metal);

    float occ = ao;
    // the floor under and round the stand, the wall behind the tank and stand: soft contact shade
    if (kind == 7 || kind == 11) {
        float d = boxDist(p.xz, uFoot.xz, uFoot.yw);
        occ *= mix(0.35, 1.0, smoothstep(-4.0, 30.0, d));
    }
    if (kind == 8 && p.z < uRoomBox.z + 3.0) {
        float d = boxDist(p.xy, vec2(uFoot.x, FLOOR_Y), vec2(uFoot.y, uTankBox.y));
        occ *= mix(0.6, 1.0, smoothstep(-10.0, 45.0, d));
    }
    // the stand's own underside and recesses get less of the sky
    float shadowKey = 1.0;
    if (kind <= 6) shadowKey = mix(0.5, 1.0, smoothstep(FLOOR_Y, FLOOR_Y + 40.0, p.y));

    float ndl = max(dot(N, Lk), 0.0);
    vec3 col = keyCol * (diff * ndl + F0 * ggxSpec(N, V, Lk, max(rough, 0.12)) * ndl) * shadowKey * mix(0.6, 1.0, occ);
    col += mix(bounce, sky, N.y * 0.5 + 0.5) * diff * occ;

    // the tank's light: out of the open top (onto the wall above, the ceiling) and through the front glass
    vec3 tankCol = (uLightColor[0].rgb + uLightColor[1].rgb) * 0.5 * vec3(0.62, 0.86, 1.0);
    {
        vec3 q = vec3(clamp(p.x, 0.0, uTankBox.x), uTankBox.y, clamp(p.z, 0.0, uTankBox.z));
        vec3 d = q - p;
        float d2 = dot(d, d);
        vec3 L = d * inversesqrt(max(d2, 1.0));
        float up = max(-L.y, 0.0);                    // only what is above the tank sees its open top
        col += tankCol * diff * max(dot(N, L), 0.0) * up * 2600.0 / (d2 + 1600.0) * occ;
        q = vec3(clamp(p.x, 0.0, uTankBox.x), clamp(p.y, 0.0, uTankBox.y), uTankBox.z);
        d = q - p;
        d2 = dot(d, d);
        L = d * inversesqrt(max(d2, 1.0));
        float front = max(-L.z, 0.0);                 // only what is in front of the front glass
        col += tankCol * diff * max(dot(N, L), 0.0) * front * 900.0 / (d2 + 2500.0) * occ;
    }
    // the floor lamp
    if (uLampPos.w > 0.5) {
        vec3 d = uLampPos.xyz - p;
        float d2 = dot(d, d);
        vec3 L = d * inversesqrt(max(d2, 1.0));
        float down = kind == 9 ? 0.35 : 1.0;          // the drum shade throws most of its light down and out
        col += uLampCol * diff * max(dot(N, L), 0.0) * down * 3600.0 / (d2 + 900.0) * mix(0.7, 1.0, occ);
    }
    // the glow strip's light pooled on the floor in front of the stand
    if (uGlowStrip > 0.5 && (kind == 7 || kind == 11 || kind == 4 || kind == 1 || kind == 2)) {
        float d = boxDist(p.xz, vec2(uFoot.x + 1.0, uFoot.w - 1.0), vec2(uFoot.y - 1.0, uFoot.w + 1.0));
        float h = max(p.y - FLOOR_Y, 0.0);
        col += vec3(0.25, 0.62, 1.0) * albedo * exp(-max(d, 0.0) / 16.0) * exp(-h / 6.0) * 1.6;
    }
    // a soft reflection of the room in polished surfaces (gloss paint, metal)
    if (rough < 0.35 || metal > 0.5) {
        vec3 R = reflect(-V, N);
        float ndv = max(dot(N, V), 1e-3);
        vec3 env = mix(bounce * 1.4, sky * 1.2 + tankCol * 0.15, smoothstep(-0.2, 0.4, R.y));
        vec3 Fr = F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(1.0 - ndv, 5.0);
        col += Fr * env * mix(0.5, 1.0, occ);
    }

    if (kind == 5) {
        if (uGlowOn < 0.5) discard;
        col = vec3(0.35, 0.78, 1.0) * 2.2;
    } else if (kind == 6) {
        // the sump window: dark glass with the blue-lit sump and its rising bubbles behind it
        float bub = step(0.93, hash12(floor(vec2(p.x * 0.8, p.y * 0.6 - uTime * 3.0))));
        col = vec3(0.01, 0.03, 0.05) + vec3(0.05, 0.25, 0.45) * (0.5 + 0.5 * smoothstep(FLOOR_Y + 10.0, FLOOR_Y + 50.0, p.y)) + bub * 0.12;
        col += pow(1.0 - max(dot(N, V), 0.0), 4.0) * sky;
    } else if (kind == 10) {
        // lamp shade: lit from inside in the evening, a warm fabric in the day
        float on = clamp(uLampPos.w * (1.0 - day * 0.85), 0.0, 1.0);
        col = mix(col, albedo * uLampCol * 0.8, on);
    } else if (kind == 12) {
        // leaves: the key shines through them a little
        col += diff * keyCol * 0.25 * max(dot(-N, Lk), 0.0);
    } else if (kind == 15) {
        // fire: flickering flames, hottest at the bottom
        float h = clamp((p.y - FLOOR_Y - 12.0) / 28.0, 0.0, 1.0);
        float flick = 0.75 + 0.25 * sin(uTime * 9.0 + p.x * 0.7) * sin(uTime * 5.3 + p.x * 0.3);
        col = mix(vec3(1.0, 0.75, 0.3), vec3(0.9, 0.25, 0.05), h) * 2.2 * flick * (1.0 - h * 0.6);
        alpha = 1.0;
    } else if (kind == 14) {
        // a window: the view outside, a little dimmer than real so it frames the tank, and a faint reflection
        col = outsideView(normalize(p - uCamPos.xyz), day) * 0.8;
        col += pow(1.0 - max(dot(N, V), 0.0), 4.0) * 0.08 * (sky + tankCol * 0.2);
    } else if (kind == 13) {
        col = col * 0.5 + pow(1.0 - max(dot(N, V), 0.0), 3.0) * (sky + tankCol * 0.1);
        alpha = clamp(alpha + 0.2 * pow(1.0 - max(dot(N, V), 0.0), 2.0), 0.0, 0.85);
    }
    col += uEmissive;

    // the room steps back: walls, floor and props a little muted and dimmer than the tank, more so far away
    if (kind >= 7 || kind == 0) {
        float far = smoothstep(150.0, 600.0, length(p.xz - vec2(uTankBox.x * 0.5, uTankBox.z * 0.5)));
        col = mix(col, vec3(luma(col)), 0.12 + 0.15 * far) * (0.92 - 0.12 * far);
    }
    // tonemap() adapts the frame to the tank lights' colour; the room keeps its own white balance
    col *= max(mix(vec3(1.0), lightTint(), 0.9 * (1.0 - uNight) * smoothstep(0.03, 0.25, lightIntensity())), vec3(0.2));
    // exposed for the tank, as in Tee's photos: the room sits darker than the lit reef (research 11 §10)
    col *= ROOM_EXPOSURE;
    fragColor = vec4(tonemap(col), alpha);
}
