#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Supply shelf beside the tank (SupplyShelf.kt, docs/ASSETS.md §5.1, UI_STYLE.md "Supply shelf"). The shelf stands in
// the room, not in the water, so it has its own light rig instead of the AI Prime fixtures: a soft overhead key from
// the front left (shadowed by the board above and by the props, as soft analytic shadows), a cool fill from the
// front right, the warm LED pucks under each board, a faint spill from the lit tank on the left, and a room
// environment (ceiling, walls, floor and the key's softbox) reflected with roughness-aware Fresnel. Contact shadows
// and crease occlusion are analytic (uFoot*, the rack layout); props carry baked vertex AO in COLOR_0.r. Materials
// by uKind: plank and driftwood grain, prints (low specular, so labels stay legible), frosted sea glass, metal.
// The room dims at night; the pucks and the sign stay lit.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAlbedo;
uniform vec4 uBaseColor;
uniform float uHasTexture;
uniform float uRoughness;
uniform vec3 uEmissive;       // linear, x the texture: the lit sign and pucks, the picked prop's highlight (SupplyShelfScene)
uniform float uAlphaCutoff;
uniform float uKind;          // SupplyShelfScene.KIND_*: 0 plain, 1 board, 2 driftwood, 3 print, 4 sea glass, 5 backdrop, 6 lamp, 7 metal
uniform float uSelf;          // footprint index of the prop being drawn, -1 for the rack
uniform vec4 uFoot[28];       // per prop (rows feed, care, tools; slot order): centre x, centre z, half width, half depth (cm)
uniform vec4 uFootY[28];      // per prop: base y, top y (both lifted with the pick), lift 0..1, unused

out vec4 fragColor;

// SupplyShelfLayout / supplies.py
const float X0 = 134.0;
const float X1 = 236.0;
const float Z0 = 2.0;
const float Z1 = 24.0;
const float SLOT_X0 = 136.4;
const float SLOT_X1 = 233.6;

// light rig: unit directions toward each light, linear radiance
const vec3 KEY_DIR = vec3(-0.3506, 0.5009, 0.7913);
const vec3 KEY_X = vec3(0.9143, 0.0, 0.4051);     // softbox frame (KEY_X, KEY_Y, KEY_DIR)
const vec3 KEY_Y = vec3(0.2029, 0.8655, -0.4579);
const vec3 KEY_COL = vec3(1.0, 0.95, 0.87) * 0.78;
const vec3 FILL_DIR = vec3(0.5507, 0.1502, 0.8211);
const vec3 FILL_COL = vec3(0.80, 0.88, 1.0) * 0.20;
const vec3 LED_COL = vec3(1.0, 0.72, 0.42) * 70.0;

// Underside (y) of the board above height y; the pucks hang under the top three.
float undersideAbove(float y) {
    if (y < -5.6) return -5.6;
    if (y < 15.4) return 15.4;
    if (y < 34.4) return 34.4;
    if (y < 52.4) return 52.4;
    return 1e4;
}

// Top (y) of the board under height y (inside the rack).
float boardBelow(float y) {
    if (y >= 52.4) return 54.4;
    if (y >= 35.9) return 36.4;
    if (y >= 16.9) return 17.4;
    return -3.6;
}

// Key light past the board above: the ray toward the light must leave the bay through the open front (or an open
// end) before it reaches the underside; the penumbra widens with the distance to the board.
float boardShadow(vec3 p) {
    float yu = undersideAbove(p.y + 0.05);
    if (yu > 1000.0) return 1.0;
    float dy = yu - p.y;
    vec2 hit = p.xz + KEY_DIR.xz * (dy / KEY_DIR.y);
    float pen = 0.4 + 0.16 * dy;
    float front = smoothstep(Z1 - pen, Z1 + pen, hit.y);
    float end = 1.0 - smoothstep(X0 - pen, X0 + pen, hit.x) * (1.0 - smoothstep(X1 - pen, X1 + pen, hit.x));
    return max(front, end);
}

// Soft key shadows of the neighbouring props (each a box silhouette in its centre plane) and the contact darkening
// round their feet. Only the props in this row's slot and the slots either side are tested.
void propTerms(vec3 p, out float keyVis, out float contact) {
    keyVis = 1.0;
    contact = 1.0;
    int row = p.y >= 35.9 ? 0 : (p.y >= 16.9 ? 1 : 2);
    int base = row == 0 ? 0 : (row == 1 ? 13 : 23);
    int count = row == 0 ? 13 : (row == 1 ? 10 : 5);
    float pitch = (SLOT_X1 - SLOT_X0) / float(count);
    int slot = int(floor((p.x - SLOT_X0) / pitch));
    int own = int(floor(uSelf + 0.5));
    for (int k = -1; k <= 1; k++) {
        int j = slot + k;
        if (j < 0 || j >= count) continue;
        int i = base + j;
        if (i == own) continue;
        vec4 f = uFoot[i];
        vec4 fy = uFootY[i];
        if (f.z <= 0.0) continue;
        float t = (f.y - p.z) / KEY_DIR.z;
        if (t > 0.0) {
            vec2 q = p.xy + KEY_DIR.xy * t;
            float pen = 0.25 + 0.09 * t;
            float sx = 1.0 - smoothstep(f.z * 0.85 - pen, f.z * 0.85 + pen, abs(q.x - f.x));
            float sy = smoothstep(fy.x - pen, fy.x + pen, q.y) * (1.0 - smoothstep(fy.y - pen, fy.y + pen, q.y));
            keyVis = min(keyVis, 1.0 - 0.88 * sx * sy);
        }
        vec2 d = abs(p.xz - f.xy) - f.zw;
        float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
        float near = exp(-max(dist, 0.0) * 0.9) * exp(-max(p.y - fy.x, 0.0) * 0.7);
        contact = min(contact, 1.0 - 0.7 * near * (1.0 - 0.7 * fy.z));
    }
}

// Crease occlusion inside the rack: deep in a bay sees less of the room, and the corners where the boards, the back
// panel and the uprights meet are darker (weighted by the normal, so a face is not darkened by its own plane).
float bayOcc(vec3 p, vec3 N) {
    if (p.x < X0 || p.x > X1 || p.z > Z1 + 0.5 || p.y > 52.4 || p.y < -5.6) return 1.0;
    float depth = clamp((p.z - Z0) / (Z1 - Z0), 0.0, 1.0);
    float o = mix(0.6, 1.0, sqrt(depth));
    vec3 a = 1.0 - abs(N);
    float yb = boardBelow(p.y), yu = undersideAbove(p.y + 0.05);
    o *= 1.0 - 0.4 * a.y * clamp(exp(-(p.y - yb) * 0.55) + exp(-(yu - p.y) * 0.55), 0.0, 1.0);
    o *= 1.0 - 0.4 * a.z * exp(-max(p.z - Z0 - 0.6, 0.0) * 0.55);
    o *= 1.0 - 0.3 * a.x * clamp(exp(-max(p.x - X0 - 2.4, 0.0) * 0.5) + exp(-max(X1 - 2.4 - p.x, 0.0) * 0.5), 0.0, 1.0);
    return o;
}

// The four warm LED pucks under the board above (supplies.py: x = X0 + 14 + 24.67 k, z = Z1 - 5), Lambert emitters
// facing down.
vec3 ledLight(vec3 p, vec3 N) {
    float yu = undersideAbove(p.y + 0.05);
    if (yu < 15.0 || yu > 60.0) return vec3(0.0);
    float e = 0.0;
    for (int k = 0; k < 4; k++) {
        vec3 d = vec3(148.0 + 24.667 * float(k), yu - 0.4, 19.0) - p;
        float d2 = dot(d, d);
        vec3 L = d * inversesqrt(d2);
        e += max(dot(N, L), 0.0) * max(L.y, 0.0) / (d2 + 8.0);
    }
    return LED_COL * e;
}

// The room as seen in a reflection: floor, walls, ceiling, the key's softbox and the lit tank on the left.
vec3 roomEnv(vec3 R, float rough) {
    vec3 c = mix(vec3(0.16, 0.15, 0.14), vec3(0.36, 0.35, 0.34), smoothstep(-0.1, 0.8, R.y));
    c = mix(vec3(0.045, 0.036, 0.03), c, smoothstep(-0.4, -0.02, R.y));
    float w = dot(R, KEY_DIR);
    vec2 uv = vec2(dot(R, KEY_X), dot(R, KEY_Y)) / max(w, 0.1);
    float e = 0.03 + rough * 0.7;
    float box = (1.0 - smoothstep(0.42 - e, 0.42 + e, abs(uv.x))) * (1.0 - smoothstep(0.3 - e, 0.3 + e, abs(uv.y)));
    c += KEY_COL * (box * step(0.0, w) * 5.0 / (1.0 + rough * 5.0));
    c += lightTint() * (lightIntensity() * 0.45 * smoothstep(0.35, 0.95, -R.x));
    return c;
}

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    float alpha = tex.a * uBaseColor.a;
    if (uAlphaCutoff > 0.0 && alpha < uAlphaCutoff) discard;
    vec3 p = vWorld;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - p);
    if (dot(N, V) < 0.0) N = -N;
    int kind = int(uKind + 0.5);

    // ---- material: procedural detail (no textures), roughness, specular / reflection weights ----
    float rough = uRoughness;
    float specK = 1.0;
    float envK = 1.0;
    float wrap = 0.0;
    float metal = 0.0;
    float h = 0.0;                                     // bump height (cm)
    float fp = length(fwidth(p));                      // cm per pixel: fine detail fades out with distance
    float fine = 1.0 - smoothstep(0.03, 0.12, fp);
    if (kind == 1) {
        // planks: growth rings across the board, streaming along x, and fine fibres
        float warp = fbm3(vec3(p.x * 0.035, p.y * 0.3, p.z * 0.3));
        float ring = 0.5 + 0.5 * sin((p.z * 0.8 + p.y * 1.4 + warp * 9.0) * 3.1);
        float fib = mix(0.5, vnoise(vec3(p.x * 0.5, p.y * 9.0, p.z * 9.0)), fine);
        float g = 0.6 * smoothstep(0.55, 1.0, ring) + 0.4 * fib;
        albedo *= mix(1.1, 0.66, g);
        rough = clamp(rough + (g - 0.5) * 0.2, 0.35, 1.0);
        h = (fib - 0.5) * 0.012 - smoothstep(0.8, 1.0, ring) * 0.008;
        specK = 0.7;
        envK = 0.6;
    } else if (kind == 2) {
        // driftwood: weathered fibres along the length (y), paler on the ridges
        float warp = fbm3(vec3(p.x * 0.4, p.y * 0.05, p.z * 0.4));
        float fib = 0.6 * vnoise(vec3(p.x * 5.0, p.y * 0.3, p.z * 5.0)) + 0.4 * mix(0.5, vnoise(vec3(p.x * 13.0, p.y * 0.9, p.z * 13.0)), fine);
        albedo *= mix(1.12, 0.7, smoothstep(0.25, 0.8, fib)) * mix(0.9, 1.06, warp);
        rough = 0.85;
        h = (fib - 0.5) * 0.05;
        specK = 0.5;
        envK = 0.4;
    } else if (kind == 3) {
        // printed labels: satin, and never a mirror (the print must stay legible)
        rough = max(rough, 0.5);
        specK = 0.3;
        envK = 0.3;
    } else if (kind == 4) {
        // frosted sea glass: light scatters through it (wrapped diffuse), a soft sheen
        rough = 0.32;
        wrap = 0.5;
        envK = 1.2;
    } else if (kind == 5) {
        // the painted back panel: matte, a faint brush texture
        rough = 0.9;
        h = (vnoise(p * vec3(0.6, 2.5, 0.6)) - 0.5) * 0.006 * fine;
        specK = 0.2;
        envK = 0.2;
    } else if (kind == 7) {
        metal = 1.0;
        rough = max(rough, 0.25);
    }
    if (uQuality >= 0.5 && (kind == 1 || kind == 2 || kind == 5)) N = bumpH(N, p, h * (1.0 - smoothstep(0.08, 0.3, fp)));

    // ---- visibility ----
    float keyVis, contact;
    propTerms(p, keyVis, contact);
    keyVis *= boardShadow(p);
    float occ = clamp(vColor.r, 0.0, 1.0) * bayOcc(p, N) * contact;

    // ---- the room rig ----
    float day = smoothstep(0.03, 0.5, lightIntensity());
    float room = uNight > 0.5 ? 0.28 : mix(0.6, 1.0, day);
    vec3 roomTint = uNight > 0.5 ? vec3(0.72, 0.84, 1.0) : vec3(1.0);
    vec3 diff = albedo * (1.0 - metal);
    vec3 F0 = mix(vec3(0.04), albedo, metal);
    float ndv = max(dot(N, V), 1e-3);

    float ndk = dot(N, KEY_DIR);
    vec3 Hk = normalize(V + KEY_DIR);
    vec3 Fk = F0 + (1.0 - F0) * pow(clamp(1.0 - dot(Hk, V), 0.0, 1.0), 5.0);
    vec3 key = KEY_COL * keyVis * (diff * max((ndk + wrap) / (1.0 + wrap), 0.0)
        + Fk * (ggxSpec(N, V, KEY_DIR, max(rough, 0.18)) * max(ndk, 0.0) * specK));

    float ndf = dot(N, FILL_DIR);
    vec3 fill = FILL_COL * (0.5 + 0.5 * occ) * (diff * max((ndf + 0.3 + wrap) / (1.3 + wrap), 0.0)
        + F0 * (ggxSpec(N, V, FILL_DIR, max(rough, 0.25)) * max(ndf, 0.0) * specK));

    vec3 amb = mix(vec3(0.05, 0.045, 0.04), vec3(0.15, 0.15, 0.16), N.y * 0.5 + 0.5) * diff * occ;

    vec3 R = reflect(-V, N);
    vec3 Fr = F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(clamp(1.0 - ndv, 0.0, 1.0), 5.0);
    vec3 env = roomEnv(R, rough) * Fr * (envK * clamp(occ * 1.3, 0.0, 1.0));

    vec3 col = (key + fill + amb + env) * room * roomTint;
    // practical lights: the pucks under the board above (shaded a little by the props' feet) and the tank's spill
    col += ledLight(p, N) * (diff * (0.5 + 0.5 * contact) + F0 * (specK * 0.5));
    vec3 ts = vec3(118.0, 34.0, 16.0) - p;
    float td2 = dot(ts, ts);
    col += lightTint() * (lightIntensity() * 0.9 + 0.03) * (max(dot(N, ts * inversesqrt(td2)) * 0.8 + 0.2, 0.0) * 450.0 / (td2 + 1400.0)) * diff * occ;
    col += uEmissive * toLinear(tex.rgb);

    // the same water / grade path as the tank, but the room keeps its own white balance (tonemap() adapts the frame
    // to the AI Prime colour; undo that here so wood stays wood under a blue daylight)
    col = applyWater(col, p);
    col *= max(mix(vec3(1.0), lightTint(), 0.6 * (1.0 - uNight) * smoothstep(0.03, 0.25, lightIntensity())), vec3(0.2));
    fragColor = vec4(tonemap(col), alpha);
}
