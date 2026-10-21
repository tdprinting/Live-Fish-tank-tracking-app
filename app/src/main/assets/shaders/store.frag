#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Fish store interior around the live display tank (StoreRoom.kt, tools/blender/store_rooms.py): a row of bays, each
// with a stand, a display tank under a hanging tank light and a ceiling panel over the aisle. The room has its own
// light rig, not the AI Prime fixtures: those lights repeat every uBay cm along x (so they follow the bays wherever
// the renderer draws them), a cool glow spills from every tank's water onto the stands and the floor, and a
// store-coloured hemisphere fills the rest. Kinds (StoreRoomScene.kindOf, from the material name): 0 paint,
// 1 floor tiles, 2 lamp / screen / neon (emissive), 3 glass, 4 tank water, 5 metal (gold, fixtures).

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
uniform vec3 uGlowCol;        // tank water glow spilling into the room

out vec4 fragColor;

const float CX = 60.95;       // tank centre x (TankDims.LENGTH / 2)

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
    // ceiling panel over the aisle (80 x 60 cm at y 299, z 140..200)
    q = vec3(clamp(p.x, bx - 40.0, bx + 40.0), 299.0, clamp(p.z, 140.0, 200.0));
    d = q - p;
    d2 = dot(d, d);
    L = d * inversesqrt(d2);
    ndl = max(dot(N, L), 0.0);
    c += uLampCol * (max(L.y, 0.0) * 26000.0 / (d2 + 4000.0)) * (diff * ndl + F0 * ggxSpec(N, V, L, max(rough, 0.3)) * ndl * 0.5);
    // the tank's water glow, from its front face
    vec3 t = vec3(clamp(p.x, bx - 60.0, bx + 60.0), 20.0, 46.0) - p;
    float t2 = dot(t, t);
    vec3 Lt = t * inversesqrt(t2);
    c += uGlowCol * (max(dot(N, Lt), 0.0) * 1800.0 / (t2 + 2500.0)) * diff;
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

    if (kind == 2) {
        // lamps, screens and neon: their own light, a little body colour so they are never flat
        fragColor = vec4(tonemap(uEmissive + albedo * 0.15), alpha);
        return;
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
    vec3 diff = albedo * (1.0 - metal);
    vec3 F0 = mix(vec3(0.04), albedo, metal);

    // the two nearest bays' lights (the lights repeat every bay)
    float n = floor((p.x - CX) / uBay);
    vec3 col = bayLight(p, N, V, CX + n * uBay, diff, F0, rough) + bayLight(p, N, V, CX + (n + 1.0) * uBay, diff, F0, rough);
    // hemisphere fill; corners near the floor a little darker
    vec3 amb = mix(uFillCol * 0.35, uFillCol, N.y * 0.5 + 0.5) * diff;
    amb *= mix(0.7, 1.0, smoothstep(-82.0, -60.0, p.y));
    col += amb;
    // a soft reflection of the lit room in polished and metal surfaces
    vec3 R = reflect(-V, N);
    float ndv = max(dot(N, V), 1e-3);
    vec3 Fr = F0 + (max(vec3(1.0 - rough), F0) - F0) * pow(1.0 - ndv, 5.0);
    col += Fr * mix(uFillCol * 0.3, uLampCol * 3.0, smoothstep(0.0, 1.0, R.y)) * (kind == 1 ? 0.6 : 1.0);
    if (kind == 4) {
        // a stocked tank's water seen from the room: lit from above, glowing in the store's water colour
        col = albedo * (0.25 + 0.75 * smoothstep(0.0, 38.0, p.y)) + uEmissive;
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
