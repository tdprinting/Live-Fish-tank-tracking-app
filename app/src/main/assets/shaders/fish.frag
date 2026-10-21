#version 300 es
// A fish never darkens itself with its own contact shadow (lighting.glsl shadeLit).
#define NO_CONTACT_SHADOW 1
#include "common.glsl"
#include "lighting.glsl"
// Fish skin (ARCHITECTURE §8.4 items 5-6). Define FIN for the translucent two-sided fin sheet variant.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAlbedo;
uniform sampler2D uIridLut;
uniform vec4 uBaseColor;     // linear RGBA factor
uniform float uHasTexture;
uniform float uRoughness;
uniform float uIridStrength;
uniform float uDesat;
uniform float uTone;         // own colour change, -1 pale .. +1 dark and mottled (lawnmower blenny, research 51)
uniform float uIchSpots;     // 0..1 white spot coverage (ich)
uniform float uEyeCloud;
uniform float uPart;         // 0 body, 1 fin, 2 eye, 3 leg/antenna
uniform float uDead;
uniform float uFade;         // microfauna: 0 full contrast .. 1 lost in the water (MicroCritterVisuals)
uniform vec4 uChroma;        // bobtail squid (core SquidRig): x chromatophore expansion, y passing-cloud strength, z light-organ glow, w 1 + cloud phase (0 = off)
// Life stages (LifeStageVisuals.kt, additive): a second body texture painted on the same UV layout, mixed in by
// uStageMix while a fish grows from one stage's pattern into the next (or changes sex). 0 = unchanged.
uniform sampler2D uAlbedoStage;
uniform float uStageMix;

out vec4 fragColor;

// Procedural ich: sparse round white dots in texture space (size ~1-2 texels of a 140-cell grid).
float ichMask(vec2 uv) {
    vec2 g = uv * 140.0;
    vec2 cell = floor(g);
    float h = hash12(cell);
    float on = step(0.92 - uIchSpots * 0.3, h);
    vec2 c = vec2(hash12(cell + 7.1), hash12(cell + 3.7)) * 0.5 + 0.25;
    float r = mix(0.18, 0.34, hash12(cell + 1.3));
    float d = length(fract(g) - c);
    return on * (1.0 - smoothstep(r * 0.6, r, d));
}

void main() {
    vec4 tex = uHasTexture > 0.5 ? texture(uAlbedo, vUv) : vec4(1.0);
    if (uStageMix > 0.001 && uHasTexture > 0.5) tex = mix(tex, texture(uAlbedoStage, vUv), clamp(uStageMix, 0.0, 1.0));
    vec3 albedo = toLinear(tex.rgb) * uBaseColor.rgb;
    float alpha = tex.a * uBaseColor.a;

    vec3 V = normalize(uCamPos.xyz - vWorld);
    vec3 N = normalize(vNormal);
#ifdef FIN
    if (!gl_FrontFacing) N = -N;
#endif
    if (dot(N, V) < 0.0) N = -N; // two-sided safety for thin parts

    // symptoms
    if (uIchSpots > 0.001 && uPart < 1.5) albedo = mix(albedo, vec3(0.92), ichMask(vUv) * min(uIchSpots * 3.0, 1.0));
    float lum = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
    albedo = mix(albedo, vec3(lum), clamp(uDesat, 0.0, 1.0));
    if (abs(uTone) > 0.01 && (uPart < 1.5)) {
        // melanophores spread in soft blotches over the back first: dark goes on mottled, pale washes the blotches out
        vec2 gb = vUv * vec2(9.0, 5.0);
        vec2 cb = floor(gb);
        float blot = 1.0 - smoothstep(0.15, 0.5, length(fract(gb) - (vec2(hash12(cb + 4.2), hash12(cb + 8.6)) * 0.5 + 0.25)));
        float k = clamp(uTone, -1.0, 1.0);
        if (k > 0.0) albedo *= 1.0 - k * (0.35 + 0.4 * blot);
        else albedo = mix(albedo, vec3(lum) * 0.5 + vec3(0.55, 0.52, 0.45) * 0.5, -k * 0.6);
    }
#ifndef FIN
    // the mouth lining (fish.vert writes it to vColor.g for a model with a jaw): dark flesh, seen in shadow
    if (uPart < 0.5) albedo = mix(albedo, vec3(0.09, 0.035, 0.04), 0.9 * clamp(vColor.g, 0.0, 1.0));
#endif
    if (uPart > 1.5 && uPart < 2.5) albedo = mix(albedo, vec3(0.62, 0.64, 0.66), uEyeCloud * 0.85);

    // Squid skin: procedural chromatophores over the texture that expand (dark) and retract (pale) within a fraction
    // of a second, dark "passing clouds" running along the body, and the ventral light-organ glow (body only).
    float sqGlow = 0.0;
    if (uChroma.w > 0.5 && (uPart < 0.5 || uPart > 2.5)) {
        float dorsal = uPart < 0.5 ? vColor.r : 0.7;
        float cloud = uChroma.y * smoothstep(0.3, 1.0, sin(2.0 * PI * (vColor.a * 2.5 - (uChroma.w - 1.0))));
        float ex = clamp(uChroma.x + 0.5 * cloud, 0.0, 1.0);
        vec2 g = vUv * vec2(70.0, 200.0);
        vec2 cell = floor(g);
        vec2 cc = vec2(hash12(cell + 2.3), hash12(cell + 5.9)) * 0.6 + 0.2;
        float on = step(1.0 - (0.25 + 0.6 * dorsal), hash12(cell));
        float r = mix(0.08, 0.46, ex) * mix(0.6, 1.0, hash12(cell + 9.1));
        float dt = on * (1.0 - smoothstep(r * 0.65, r, length(fract(g) - cc)));
        // far away the cells are sub-pixel: fade to their mean coverage instead of shimmering
        dt = mix(dt, on * 2.6 * r * r, smoothstep(0.35, 0.9, max(fwidth(g.x), fwidth(g.y))));
        vec3 pig = mix(vec3(0.16, 0.05, 0.015), vec3(0.45, 0.2, 0.05), hash12(cell + 4.4));
        albedo = mix(albedo, pig, clamp(dt, 0.0, 1.0) * 0.9);
        albedo *= 1.0 - 0.25 * ex * dorsal;
        albedo = mix(albedo, albedo * 1.15 + 0.03, (1.0 - ex) * 0.3);
        if (uPart < 0.5) sqGlow = uChroma.z * (1.0 - smoothstep(0.1, 0.45, dorsal));
    }

    // scale / shell micro-relief: offset rows of rounded scales in texture space (body only, Medium+), faded out
    // once a scale is smaller than a pixel so it never sparkles
    if (uQuality >= 0.5 && uChroma.w < 0.5) {   // squid skin (uChroma) is scaleless
        vec2 g = vUv * vec2(70.0, 38.0);
        g.x += 0.5 * mod(floor(g.y), 2.0);
        vec2 f = fract(g) - vec2(0.5, 0.35);
        // soft dome per scale: a wide falloff keeps the edge slopes gentle, so up close the scale rows read as relief
        // in the diffuse light instead of a bright net of specular glints along every scale edge
        float scale = 1.0 - smoothstep(0.05, 0.4, dot(f, f));
        float vis = (1.0 - smoothstep(0.2, 0.5, length(fwidth(g)))) * step(uPart, 0.5);
        N = bumpH(N, vWorld, scale * vis * 0.006);
    }
    // view-angle terms (rim, fresnel, iridescence, clear coat) use the smooth body normal: on the scale relief they
    // light up every scale edge as a white grid when the camera is close
    vec3 Ng = normalize(vNormal);
#ifdef FIN
    if (!gl_FrontFacing) Ng = -Ng;
#endif
    if (dot(Ng, V) < 0.0) Ng = -Ng;
    float ndv = max(dot(Ng, V), 0.0);
    float specScale = mix(1.0, 0.25, uNight) * (1.0 - 0.7 * uDead);
#ifdef FIN
    vec3 col = shadeLit(albedo, N, V, vWorld, max(uRoughness, 0.4), 1.0, 0.03, 0.5, specScale * 0.5);
    // back-lit fins glow a little (thin membrane)
    // (each fixture with its own direction; none inside a shadow)
    vec3 Lb0, Lb1;
    vec3 rad0 = fixtureLight(0, vWorld, Lb0), rad1 = fixtureLight(1, vWorld, Lb1);
    col += albedo * (rad0 * pow(clamp(dot(V, -Lb0), 0.0, 1.0), 3.0) + rad1 * pow(clamp(dot(V, -Lb1), 0.0, 1.0), 3.0))
        * 0.35 * gShadow;
    alpha *= 0.75 + 0.25 * ndv;
    if (uIchSpots > 0.001) col = mix(col, vec3(0.8) * lightIntensity(), ichMask(vUv * 0.5) * uIchSpots * 0.6);
    if (alpha < 0.03) discard;
#else
    vec3 col = shadeLit(albedo, N, V, vWorld, uRoughness, 1.0, 0.04, 0.15, specScale);
    // thin-film iridescence (LUT ramp by view angle) added to the specular response
    vec3 irid = toLinear(texture(uIridLut, vec2(ndv, 0.5)).rgb) * uIridStrength * (1.0 - uDesat);
    vec3 Lk;
    vec3 rad = fixtureLight(0, vWorld, Lk) + fixtureLight(1, vWorld, Lk);
    float shade = gShadow;
    col += irid * rad * (0.35 + 0.65 * pow(1.0 - ndv, 2.0)) * shade;
    // wet skin / shell: a tight clear-coat highlight over the body's own roughness
    col += wetCoat(Ng, V, vWorld, 0.14, 0.55 * specScale) * shade;
    // Fish were nearly invisible against the rock and the blue water. A soft camera-side fill lets the body
    // pattern read in the shade, and a stronger rim (light colour plus a little of the fish's own colour)
    // separates the silhouette from the background. Both follow the light, so night stays dark.
    vec3 lightSum = (uLightColor[0].rgb + uLightColor[1].rgb) * 0.5;
    col += albedo * 0.10 * (0.15 + lightIntensity());
    // up close the silhouette needs no help and an unattenuated rim reads as a pale outline: ease it in with distance
    float rim = pow(1.0 - ndv, 2.2) * mix(0.45, 1.0, smoothstep(15.0, 60.0, distance(uCamPos.xyz, vWorld)));
    // the rim is brightest on the top edge (light from the fixtures above) and picks up the water colour below
    float top = clamp(Ng.y * 0.5 + 0.5, 0.0, 1.0);
    col += rim * (0.40 * lightSum * (0.6 + 0.8 * top) + 0.30 * albedo * lightIntensity() + uFog.rgb * 0.6 * (1.0 - top));
    // inside the open mouth is in its own shadow: no sheen or rim from the lights
    col *= 1.0 - 0.8 * clamp(vColor.g, 0.0, 1.0);
    alpha = 1.0;
    // counter-illumination: a faint blue-green glow from the ventral light organ, strongest seen from below
    col += vec3(0.35, 0.62, 0.95) * sqGlow * 0.06 * (0.3 + 0.7 * smoothstep(0.0, -0.7, N.y));
#endif
    col = applyWater(col, vWorld);
    // distant microfauna sink into the water colour, so specks melt away instead of popping out
    col = mix(col, uFog.rgb, uFade * 0.85);
    fragColor = vec4(tonemap(col), alpha);
}
