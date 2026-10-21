#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Water surface (ARCHITECTURE §8.4 item 8, SIMULATION §9.7 optics). Seen from below: Snell refraction
// (n 1.34 -> 1.0) shows the dark room and the AI Prime glow discs; above the critical angle
// asin(1/1.34) = 48.3 deg the surface is a total-internal-reflection mirror of the lit water. Schlick
// Fresnel F0 0.02 blends the two. Seen from above: reflection of the room over the transmitted scene.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uWaterN1;
uniform sampler2D uWaterN2;
uniform sampler2D uEnv;

out vec4 fragColor;

const float ETA_WATER = 1.34;
const vec3 ROOM = vec3(0.0032, 0.0048, 0.0103); // #0A0F1A linear

vec2 equirect(vec3 d) { return vec2(atan(d.z, d.x) / (2.0 * PI) + 0.5, acos(clamp(d.y, -1.0, 1.0)) / PI); }

void main() {
    // detail normals: two scrolling maps (cm/s speeds), 20 cm tiles, plus the simulated slope
    vec2 uv = vWorld.xz / 20.0;
    vec3 n1 = texture(uWaterN1, uv + uTime * vec2(1.5, 0.8) / 20.0).xyz * 2.0 - 1.0;
    vec3 n2 = texture(uWaterN2, uv * 1.3 + uTime * vec2(-1.0, 1.2) / 20.0).xyz * 2.0 - 1.0;
    vec2 slope = surfaceSlope(vWorld.xz);
    // swell and chop (common.glsl swell): the same waves that lift the vertices bend the normals and the caustics
    vec2 rip = -swell(vWorld.xz, 5).yz;
    vec3 N = normalize(vec3((n1.x + n2.x) * 0.12 - slope.x + rip.x, 1.0, (n1.y + n2.y) * 0.12 - slope.y + rip.y));

    vec3 I = normalize(vWorld - uCamPos.xyz);
    bool below = uCamPos.y < uWaterLevel;
    // where each fixture's cone meets the surface: microbubbles and surface film scatter a soft glowing pool that
    // shimmers with the ripples (Medium+)
    vec3 pool = vec3(0.0);
    if (uQuality >= 0.5) {
        float sh = texture(uCaustic, vWorld.xz * 0.05 + N.xz * 0.6 + uTime * vec2(0.015, -0.01)).r;
        for (int i = 0; i < 2; i++) {
            vec2 d = vWorld.xz - uLightPos[i].xz;
            pool += uLightColor[i].rgb * exp(-dot(d, d) / 450.0) * (0.05 + 0.6 * sh);
        }
    }
    // whitewater where the return splashes in (equipment.glb ReturnNozzle, back middle): churned froth that fades out
    // a few cm away, only while the pumps run
    vec2 fd = (vWorld.xz - vec2(73.0, 2.0)) * vec2(1.0, 1.6);
    float fr = exp(-dot(fd, fd) / 40.0) * clamp(uFlowStrength, 0.0, 1.0);
    float foam = 0.0;
    if (fr > 0.02) foam = fr * smoothstep(0.04, 0.3, texture(uCaustic, vWorld.xz * 0.12 + uTime * vec2(0.3, 0.45)).r + 0.3 * fr);
    vec3 foamCol = lightTint() * (0.15 + 0.85 * lightIntensity()) + uAmbientB.rgb;
    vec3 col;
    float alpha;
    if (below) {
        vec3 Nd = -N; // facing the camera (down)
        float cosI = clamp(dot(-I, Nd), 0.0, 1.0);
        vec3 T = refract(I, Nd, ETA_WATER);
        vec3 R = reflect(I, Nd);
        // reflected underwater scene approximation: lit water colour + the fixtures' reflected glow
        vec3 refl = uFog.rgb * 1.6 + (uLightColor[0].rgb + uLightColor[1].rgb) * 0.04;
        refl += uAmbientB.rgb * 0.5;
        // the mirror shimmers like the real tank seen through the front glass: a crinkled net of bright ripple
        // facets catching the lamps, two chop-warped reads of the caustic tile drifting against each other
        vec2 cuv = vWorld.xz * 0.045 + N.xz * 0.5;
        float ca = texture(uCaustic, cuv + uTime * vec2(0.035, 0.021)).r;
        float cb = texture(uCaustic, cuv * 1.7 - N.xz * 0.3 - uTime * vec2(0.026, -0.031)).r;
        float crinkle = smoothstep(0.03, 0.4, ca + cb);
        refl = refl * (0.7 + 0.5 * crinkle) + lightTint() * lightIntensity() * crinkle * crinkle * 0.5 * uRipple;
        if (dot(T, T) < 1e-4) {
            col = refl; // total internal reflection
        } else {
            // transmitted: the room above + glow discs of the two fixtures
            vec3 trans = ROOM + toLinear(texture(uEnv, equirect(T)).rgb) * 0.08;
            for (int i = 0; i < 2; i++) {
                vec3 L = normalize(uLightPos[i].xyz - vWorld);
                float d = max(dot(T, L), 0.0);
                trans += uLightColor[i].rgb * (pow(d, 120.0) * 6.0 + pow(d, 12.0) * 0.25);
                // glitter: tiny sharp highlights where the rippled surface points straight at a fixture
                trans += uLightColor[i].rgb * pow(d, 900.0) * 14.0 * uRipple;
            }
            // Fresnel at the water side, cosT from Snell
            float F = fresnelSchlick(cosI, 0.02);
            float cosT = sqrt(max(1.0 - ETA_WATER * ETA_WATER * (1.0 - cosI * cosI), 0.0));
            F = mix(F, 1.0, smoothstep(0.15, 0.0, cosT));
            col = mix(trans, refl, F);
            col += lightTint() * lightIntensity() * crinkle * crinkle * 0.12 * uRipple; // the window ripples too
        }
        col += pool;
        col = mix(col, foamCol, foam * 0.7);
        col = applyWater(col, vWorld);
        alpha = 1.0;
    } else {
        float cosI = clamp(dot(-I, N), 0.0, 1.0);
        float F = fresnelSchlick(cosI, 0.02);
        vec3 R = reflect(I, N);
        vec3 env = toLinear(texture(uEnv, equirect(R)).rgb) * 0.4 + ROOM;
        // glitter (the real tank from above): the chop tilts countless tiny facets to the lamps, so short white
        // streaks flicker all over the lit surface, stretched along the current. Two warped reads of the caustic
        // tile, kept only where both lines cross a threshold, under a broad lobe toward each fixture. The steep facets
        // that make them reflect far more than the flat-surface Fresnel, so they add on top of it.
        vec2 gq = vWorld.xz + N.xz * 8.0;
        float g1 = texture(uCaustic, gq * vec2(0.035, 0.11) + uTime * vec2(0.06, -0.035)).r;
        float g2 = texture(uCaustic, gq * vec2(0.05, 0.08) - uTime * vec2(0.041, 0.067)).r;
        float glitter = smoothstep(0.06, 0.3, g1) * smoothstep(0.02, 0.12, g2) * uRipple;
        vec3 glint = vec3(0.0);
        vec3 sparkle = vec3(0.0);
        for (int i = 0; i < 2; i++) {
            vec3 L = normalize(uLightPos[i].xyz - vWorld);
            float rl = max(dot(R, L), 0.0);
            glint += uLightColor[i].rgb * (pow(rl, 300.0) * 4.0 + pow(rl, 1500.0) * 12.0 * uRipple);
            sparkle += uLightColor[i].rgb * (0.25 + rl * rl) * glitter;
        }
        float sp = min(dot(sparkle, vec3(0.3)), 0.6);
        alpha = clamp(F + 0.12 + dot(pool, vec3(0.3)) + sp, 0.0, 1.0);
        // the fixture glints reach the eye at their Fresnel weight once (straight alpha would scale them by F twice)
        col = env * F + glint * (F / alpha) + uFog.rgb * 0.15 + pool * 0.6 + sparkle * (1.5 / alpha);
        col = mix(col, foamCol, foam);
        alpha = max(alpha, foam * 0.85);
    }
    fragColor = vec4(tonemap(col), alpha);
}
