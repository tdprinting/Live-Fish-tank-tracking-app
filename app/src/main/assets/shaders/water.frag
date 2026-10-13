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
    // ripple layer: three travelling wavelets (cm, s) from the return pump's chop, scaled by uRipple (quality)
    vec2 q = vWorld.xz;
    float tr = uTime;
    vec2 rip = vec2(0.8, 0.6) * cos(dot(q, vec2(0.8, 0.6)) * 0.9 + tr * 2.1) * 0.05
             + vec2(-0.5, 0.86) * cos(dot(q, vec2(-0.5, 0.86)) * 1.45 + tr * 2.7) * 0.035
             + vec2(0.97, -0.26) * cos(dot(q, vec2(0.97, -0.26)) * 2.3 + tr * 3.4) * 0.02;
    rip *= uRipple * (0.6 + 0.4 * uFlowStrength);
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
        // the mirror shimmers: bright caustic-like bands drift over the underside of the surface
        float sh = texture(uCaustic, vWorld.xz * 0.03 + rip * 0.4 + uTime * vec2(0.02, 0.013)).r;
        refl *= 0.75 + 0.7 * sh * sh;
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
        }
        col += pool;
        col = applyWater(col, vWorld);
        alpha = 1.0;
    } else {
        float cosI = clamp(dot(-I, N), 0.0, 1.0);
        float F = fresnelSchlick(cosI, 0.02);
        vec3 R = reflect(I, N);
        vec3 env = toLinear(texture(uEnv, equirect(R)).rgb) * 0.4 + ROOM;
        for (int i = 0; i < 2; i++) {
            vec3 L = normalize(uLightPos[i].xyz - vWorld);
            env += uLightColor[i].rgb * (pow(max(dot(R, L), 0.0), 300.0) * 4.0 + pow(max(dot(R, L), 0.0), 1500.0) * 12.0 * uRipple);
        }
        col = env * F + uFog.rgb * 0.15 + pool * 0.6;
        alpha = clamp(F + 0.12 + dot(pool, vec3(0.3)), 0.0, 1.0);
    }
    fragColor = vec4(tonemap(col), alpha);
}
