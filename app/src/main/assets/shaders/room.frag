#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// My tank's room (HomeRoom.kt, tools/blender/rooms.py) and the stand under the tank (tools/blender/stands.py). The
// room has its own soft light, not the AI Prime fixtures: daylight from the room (uDay: 1 at noon, 0 at night) as a
// warm key from the front left and a hemisphere fill, a floor lamp that glows in the evening, and the tank's own
// light spilling out of its open top onto the wall and ceiling and out of the front glass onto the floor. Corners,
// the floor under the stand and the wall behind the tank are softly shaded; far patterns fade out and colours are a
// little muted, so the room frames the tank without pulling the eye from it.
//
// Kinds (HomeRoomScene.kindOf, from the material name): 0 own colour, 1 stand paint (uPaint), 2 stand trim (uTrim),
// 3 metal (steel, or gold with uBrass), 4 dark insides, 5 glow strip (uGlowOn), 6 sump window, 7 floor, 8 wall,
// 9 ceiling, 10 lamp shade, 11 rug, 12 leaves, 13 clear plastic / glass. COLOR_0.r is baked occlusion.

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
uniform float uFloorStyle;    // 0 oak planks (room styles add more)
uniform float uWallStyle;     // 0 paint

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
    else if (kind == 7 && uFloorStyle < 0.5) albedo = planks(p, albedo, fw);
    if (uGrain > 0.5 && (kind == 1 || kind == 2 || kind == 0)) albedo *= mix(1.0, woodGrain(p, N), 1.0 - smoothstep(0.4, 2.0, fw));
    if (kind == 11) {
        // wool rug: a soft woven texture and a darker border band
        vec2 q = p.xz;
        float edge = min(min(q.x - (uFoot.x + uFoot.y) * 0.5 + 110.0, (uFoot.x + uFoot.y) * 0.5 + 110.0 - q.x), min(q.y - 70.0, 230.0 - q.y));
        albedo *= mix(0.72, 1.0, smoothstep(9.0, 11.0, edge)) * mix(1.0, 0.82, smoothstep(14.0, 15.5, edge) * (1.0 - smoothstep(17.0, 18.5, edge)));
        albedo *= mix(0.93, 1.05, vnoise(vec3(q * 0.9, 0.0)));
    }
    if (kind == 8) albedo *= mix(0.97, 1.03, vnoise(p * 0.05));

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
    col *= max(mix(vec3(1.0), lightTint(), 0.6 * (1.0 - uNight) * smoothstep(0.03, 0.25, lightIntensity())), vec3(0.2));
    fragColor = vec4(tonemap(col), alpha);
}
