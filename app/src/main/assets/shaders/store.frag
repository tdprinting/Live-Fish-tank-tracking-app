#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Fish store interior around the live display tank (StoreRoom.kt, tools/blender/store_rooms.py): a row of bays, each
// with a stand, a display tank under a hanging tank light and a ceiling panel over the aisle. The room has its own
// light rig, not the AI Prime fixtures: those lights repeat every uBay cm along x (so they follow the bays wherever
// the renderer draws them), a cool glow spills from every tank's water onto the stands and the floor, and a
// store-coloured hemisphere fills the rest. Kinds (StoreRoomScene.kindOf, from the material name): 0 paint,
// 1 floor tiles, 2 lamp / screen / neon (emissive), 3 glass, 4 tank water, 5 metal (gold, fixtures), 6 a tank's
// lamp (glows in its tank's light colour), 7 rising bubbles, 8 egg crate, 9 vertex-painted (people, fish, corals).
// uGlowCol is set per bay: the colour of that tank's light (core DisplayLight.glow), so a frag tank glows purple and
// throws a slow caustic shimmer onto its stand and the floor in front of it. Each store has its own floor: 11 sealed
// concrete (Frag Shack), 12 polished marble (Deep Reef). Outside (store_fronts.py, seen while walking in from the street):
// 10 sunlit paint, 13 the sky backdrop.

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
uniform float uBay;           // bay pitch (cm, StoreRoomLayout.BAY)
uniform vec3 uLampCol;        // the store's light colour (linear radiance of a tank light)
uniform vec3 uFillCol;        // hemisphere fill: x sky, the floor bounce is a darker share of it
uniform vec3 uGlowCol;        // this bay's tank light colour: its water, its lamp and the glow it spills
uniform float uRoomX;         // x of the live tank's centre: the bays (and their lights) repeat from it
uniform float uCeil;          // the store's ceiling height (world y): its aisle lights hang just under it
uniform float uFront;         // the front wall's inner face (world z): daylight comes in through its windows
// Soft contact shadows on the floor under the people and the pet (StoreRoomScene): world x, z, radius, strength.
uniform vec4 uShadows[12];
uniform float uShadowN;

out vec4 fragColor;


// A slow caustic ripple (two drifting wave sets): the light that comes out of a tank through its moving surface.
float shimmer(vec3 p) {
    float a = sin(p.x * 0.19 + uTime * 1.3) * sin(p.z * 0.16 - uTime * 1.05);
    float b = sin((p.x + p.z) * 0.11 - uTime * 0.7 + p.y * 0.05);
    return 0.72 + 0.28 * a * a * 2.0 + 0.12 * b;
}

// Bubbles rising up a column or a skimmer: round dots on a grid that scrolls up, each column at its own speed.
float bubbles(vec3 p) {
    vec2 q = vec2(p.x + p.z * 0.7, p.y);
    float col = floor(q.x / 1.1);
    float speed = 9.0 + 6.0 * hash12(vec2(col, 3.1));
    vec2 g = vec2(q.x / 1.1, (q.y - uTime * speed) / 1.6 + hash12(vec2(col, 7.7)) * 4.0);
    vec2 f = fract(g) - 0.5;
    float on = step(0.45, hash12(floor(g) + vec2(0.0, 17.0)));
    float r = 0.18 + 0.16 * hash12(floor(g));
    return on * (1.0 - smoothstep(r - 0.06, r, length(f)));
}

// One bay's lights at bay centre x [bx]: the tank light (down, over the tank) and the ceiling panel over the aisle.
vec3 bayLight(vec3 p, vec3 N, vec3 V, float bx, vec3 diff, vec3 F0, float rough) {
    vec3 c = vec3(0.0);
    // tank light: a 104 x 30 cm panel at y 117.4, over the tank, facing down
    vec3 q = vec3(clamp(p.x, bx - 52.0, bx + 52.0), 117.4, clamp(p.z, 8.0, 38.0));
    vec3 d = q - p;
    float d2 = dot(d, d);
    vec3 L = d * inversesqrt(d2);
    float down = max(L.y, 0.0);   // the panel only lights what is below it
    float ndl = max(dot(N, L), 0.0);
    c += uLampCol * (down * 9000.0 / (d2 + 900.0)) * (diff * ndl + F0 * ggxSpec(N, V, L, max(rough, 0.2)) * ndl);
    // ceiling light over the aisle (80 x 60 cm just under the ceiling, z 140..200)
    q = vec3(clamp(p.x, bx - 40.0, bx + 40.0), uCeil - 1.0, clamp(p.z, 140.0, 200.0));
    d = q - p;
    d2 = dot(d, d);
    L = d * inversesqrt(d2);
    ndl = max(dot(N, L), 0.0);
    c += uLampCol * (max(L.y, 0.0) * 26000.0 / (d2 + 4000.0)) * (diff * ndl + F0 * ggxSpec(N, V, L, max(rough, 0.3)) * ndl * 0.5);
    // the tank's water glow, from its front face, rippling like light through a moving surface
    vec3 t = vec3(clamp(p.x, bx - 60.0, bx + 60.0), 20.0, 46.0) - p;
    float t2 = dot(t, t);
    vec3 Lt = t * inversesqrt(t2);
    c += uGlowCol * (max(dot(N, Lt), 0.0) * 1800.0 / (t2 + 2500.0)) * diff * shimmer(p);
    return c;
}

// Baked-looking soft occlusion, all analytic (no extra pass): the floor darkens into the back wall and under the
// stands, the walls into the floor and ceiling, and a soft blob lies under each person.
float roomAO(vec3 p, vec3 N) {
    float floorish = smoothstep(0.5, 0.9, N.y) * (1.0 - smoothstep(-80.0, -70.0, p.y));
    // floor: the shade of the stands (their fronts end ≤ 67 cm out) and of the back wall
    float a = 1.0 - floorish * 0.45 * (1.0 - smoothstep(30.0, 95.0, p.z));
    // walls and fronts: dark where they meet the floor, a little under the ceiling
    float wallish = 1.0 - abs(N.y);
    a *= 1.0 - wallish * (0.35 * (1.0 - smoothstep(-82.0, -40.0, p.y)) + 0.25 * smoothstep(uCeil - 60.0, uCeil, p.y));
    // contact shadows under people (skipped on Low picture quality, which can be switched to while running)
    if (floorish > 0.01 && uQuality > 0.5) {
        int n = int(uShadowN + 0.5);
        for (int i = 0; i < 12; i++) {
            if (i >= n) break;
            vec4 s = uShadows[i];
            float d = length(p.xz - s.xy);
            a *= 1.0 - floorish * s.w * (1.0 - smoothstep(s.z * 0.25, s.z * 1.5, d));
        }
    }
    return a;
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

    if (kind == 2) {
        // lamps, screens and neon: their own light, a little body colour so they are never flat
        fragColor = vec4(tonemap(uEmissive + albedo * 0.15), alpha);
        return;
    }
    if (kind == 6) {
        // a display tank's lamp: as bright as its emissive, in its tank's light colour
        float e = dot(uEmissive, vec3(0.2126, 0.7152, 0.0722));
        vec3 hue = uGlowCol / max(max(uGlowCol.r, max(uGlowCol.g, uGlowCol.b)), 1e-3);
        fragColor = vec4(tonemap(e * mix(vec3(1.0), hue, 0.7) + albedo * 0.1), alpha);
        return;
    }
    if (kind == 7) {
        // bubbles: only the dots are drawn, bright where the light catches them
        float b = bubbles(p);
        if (b < 0.02) discard;
        fragColor = vec4(tonemap(mix(uFillCol * 2.0, vec3(1.0), 0.6) + uGlowCol), b * 0.75);
        return;
    }
    if (kind == 13) {
        // the sky behind the roofs: a soft blue gradient, paler at the horizon (night: deep blue)
        float h = clamp((p.y + 82.0) / 900.0, 0.0, 1.0);
        vec3 day = mix(vec3(0.62, 0.78, 0.95), vec3(0.22, 0.45, 0.85), h);
        vec3 night = mix(vec3(0.05, 0.07, 0.14), vec3(0.01, 0.015, 0.04), h);
        fragColor = vec4(tonemap(mix(day, night, uNight)), 1.0);
        return;
    }
    if (kind == 10) {
        // outside: sun and sky light (street lamps and lit signs are their own kind-2 glow)
        vec3 sunDir = normalize(vec3(0.35, 0.8, 0.5));
        vec3 sunCol = mix(vec3(2.6, 2.4, 2.1), vec3(0.05, 0.06, 0.1), uNight);
        vec3 skyCol = mix(vec3(0.55, 0.65, 0.8), vec3(0.04, 0.05, 0.09), uNight);
        float ndl = max(dot(N, sunDir), 0.0);
        vec3 o = albedo * (sunCol * ndl + skyCol * (0.55 + 0.45 * N.y)) + uEmissive;
        o += vec3(0.04) * pow(1.0 - max(dot(N, V), 0.0), 5.0) * skyCol;
        // a little haze far down the street
        o = mix(o, skyCol * 0.9, smoothstep(900.0, 2600.0, length(uCamPos.xyz - p)));
        fragColor = vec4(tonemap(o), alpha);
        return;
    }
    if (kind == 9) albedo *= vColor.rgb;
    if (kind == 8) {
        // egg crate: a square grid of white plastic bars (1.3 cm cells) with the dark water showing through
        vec2 f = abs(fract(p.xz / 1.3) - 0.5);
        albedo *= mix(0.25, 1.0, step(0.36, max(f.x, f.y)));
    }
    float rough = uRoughness;
    float metal = kind == 5 ? 1.0 : 0.0;
    if (kind == 1) {
        // floor tiles: 40 cm squares, alternate tiles a shade apart, dark grout, a faint polish
        vec2 cell = floor(p.xz / 40.0);
        vec2 f = fract(p.xz / 40.0);
        float grout = step(0.985, max(f.x, f.y)) + step(max(f.x, f.y), 0.015);
        float alt = mod(cell.x + cell.y, 2.0);
        albedo *= mix(1.0, 0.86, alt) * mix(1.0, 0.55, clamp(grout, 0.0, 1.0)) * mix(0.94, 1.04, hash12(cell));
        rough = mix(0.25, 0.6, clamp(grout, 0.0, 1.0));
    }
    if (kind == 11) {
        // sealed concrete: big poured slabs with saw cuts, a mottled grey and a soft sheen
        vec2 f = fract(p.xz / 120.0);
        float cut = step(0.992, max(f.x, f.y));
        float mottle = hash12(floor(p.xz / 9.0)) * 0.5 + hash12(floor(p.xz / 31.0)) * 0.5;
        albedo *= mix(0.86, 1.08, mottle) * mix(1.0, 0.5, cut);
        rough = 0.5;
    }
    if (kind == 12) {
        // polished marble: wide slabs, soft veins, a mirror shine
        vec2 cell = floor(p.xz / 80.0);
        vec2 f = fract(p.xz / 80.0);
        float grout = step(0.995, max(f.x, f.y)) + step(max(f.x, f.y), 0.005);
        float vein = abs(sin((p.x + p.z * 0.6) * 0.08 + sin(p.z * 0.05 + hash12(cell) * 6.0) * 2.5));
        albedo *= mix(0.82, 1.0, smoothstep(0.0, 0.12, vein)) * mix(1.0, 0.6, clamp(grout, 0.0, 1.0));
        rough = 0.12;
    }
    vec3 diff = albedo * (1.0 - metal);
    vec3 F0 = mix(vec3(0.04), albedo, metal);

    // the two nearest bays' lights (the lights repeat every bay)
    float n = floor((p.x - uRoomX) / uBay);
    vec3 col = bayLight(p, N, V, uRoomX + n * uBay, diff, F0, rough) + bayLight(p, N, V, uRoomX + (n + 1.0) * uBay, diff, F0, rough);
    float ao = roomAO(p, N);
    // daylight through the front windows and door, falling off into the store (none at night)
    float day = (1.0 - uNight) * smoothstep(uFront - 380.0, uFront, p.z);
    col += vec3(0.55, 0.62, 0.72) * day * (max(N.z, 0.0) * 0.8 + max(N.y, 0.0) * 0.5) * diff;
    // hemisphere fill with a warm bounce off the floor (soft, like light that has bounced round the room); people
    // wrap the light a little more so they read soft and round
    float wrap = kind == 9 ? 0.65 : 0.5;
    vec3 amb = mix(uFillCol * 0.45, uFillCol * 1.15, N.y * wrap + (1.0 - wrap)) * diff;
    amb += uLampCol * 0.06 * max(-N.y, 0.0) * diff;
    col = (col * mix(1.0, ao, 0.7) + amb * ao);
    // a soft reflection of the lit room in polished and metal surfaces
    vec3 R = reflect(-V, N);
    float ndv = max(dot(N, V), 1e-3);
    vec3 Fr = F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(1.0 - ndv, 5.0);
    col += Fr * mix(uFillCol * 0.3, uLampCol * 3.0, smoothstep(0.0, 1.0, R.y)) * (kind == 1 ? 0.6 : 1.0);
    if (kind == 4) {
        // a stocked tank's water seen from the room: lit from above in its light's colour, rippling
        float lit = 0.25 + 0.75 * smoothstep(-80.0, 38.0, p.y);
        col = (albedo * 0.35 + uGlowCol * 1.6) * lit * shimmer(p * 1.7) + uEmissive * 0.25;
    } else if (kind == 3) {
        // glass: mostly reflection
        col = Fr * (uLampCol * 2.0 + uFillCol) + albedo * 0.05;
        alpha = clamp(alpha + 0.35 * pow(1.0 - ndv, 3.0), 0.0, 0.8);
    }
    col += uEmissive * (kind == 4 ? 0.0 : 1.0);
    // far bays fade into the store's air
    float dist = length(uCamPos.xyz - p);
    col = mix(col, uFillCol * 0.4, smoothstep(450.0, 1100.0, dist));
    // tonemap() adapts the frame to the tank lights' colour; the room keeps its own white balance
    col *= max(mix(vec3(1.0), lightTint(), 0.6 * (1.0 - uNight) * smoothstep(0.03, 0.25, lightIntensity())), vec3(0.2));
    fragColor = vec4(tonemap(col), alpha);
}
