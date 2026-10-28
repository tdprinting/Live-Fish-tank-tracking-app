#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Green star polyp stolon mat (GspMatDraw): drawn on a copy of the rock's own triangles, so it lies exactly on the
// rock you see. vUv.x = distance along the rock from where the colony was placed (cm, slower through shade), the
// mat covers everything inside gspEdge (common.glsl: lobed margin and thin runners ahead of it). Purple stolon
// tissue with closed-calyx pits; the newest growth at the margin is thin and paler (research 41_green_star_polyps.md).

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;
in vec3 vObj;

uniform float uMatRadius;   // cm along the rock
uniform float uBleach;
uniform float uBrown;
uniform float uTissue;      // living tissue 0..1: a dying mat goes patchy

out vec4 fragColor;

const vec3 STOLON = vec3(0.105, 0.052, 0.150);       // #5A3F6B-ish purple, linear
const vec3 STOLON_DARK = vec3(0.050, 0.022, 0.060);
const vec3 MARGIN = vec3(0.30, 0.20, 0.34);          // fresh lavender runner tissue
const vec3 CALYX_RIM = vec3(0.10, 0.20, 0.05);       // green of a closed polyp's rim

void main() {
    vec3 op = vObj;
    float edge = gspEdge(op, uMatRadius);
    float e = edge - vUv.x;                    // cm inside the margin
    // a dying mat recedes in patches
    float patchy = vnoise(op * 0.35 + vec3(3.0, 11.0, 5.0));
    if (e < 0.0 || patchy > uTissue + 0.15) discard;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    float n1 = fbm3(op * 0.9);
    vec3 albedo = mix(STOLON_DARK, STOLON, 0.55 + 0.45 * n1);
    // closed calyces: small dark pits with a green rim, spaced like the polyps (~2-4 mm)
    float pit = vnoise(op * 3.3 + vec3(1.7, 4.1, 2.3));
    float hole = smoothstep(0.66, 0.78, pit);
    float rim = smoothstep(0.56, 0.66, pit) * (1.0 - hole);
    albedo = mix(albedo, CALYX_RIM, rim * 0.45);
    albedo *= 1.0 - 0.6 * hole;
    // the margin: thin, paler new stolon a few mm wide
    float margin = 1.0 - smoothstep(0.0, 0.7, e);
    albedo = mix(albedo, MARGIN * (0.8 + 0.4 * n1), margin * 0.7);
    albedo = mix(albedo, vec3(0.75, 0.7, 0.72), clamp(uBleach, 0.0, 1.0) * 0.8);
    albedo = mix(albedo, vec3(0.195, 0.102, 0.042), clamp(uBrown, 0.0, 1.0) * 0.4);
    // cavity from the rock's own COLOR_0.r (rock_surface.py / DryRockMesh) keeps the mat's crevices dark
    float ao = 1.0 - 0.5 * clamp(vColor.r, 0.0, 1.0);
    N = normalize(N + 0.25 * (vec3(vnoise(op * 2.1), vnoise(op * 2.1 + 9.0), vnoise(op * 2.1 + 17.0)) - 0.5));
    // a soft, slightly glossy tissue skin
    vec3 col = shadeLit(albedo, N, V, vWorld, 0.55, ao, 0.03, 0.4, 0.6);
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), 1.0);
}
