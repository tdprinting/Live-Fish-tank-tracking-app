#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// God rays under the two AI Prime fixtures: soft-edged additive quads (shafts.vert) in the live light colour,
// flickering with the surface (scrolling caustic noise), strength by quality tier (uShaftStrength). They fade
// toward the sand, near the camera (no hard quad edges in close-ups) and when seen from above, where a camera-facing
// sheet would read as a flat plane.

in vec2 vCoord;
in float vSeed;
in float vLight;
in vec3 vWorld;
in float vKind;

out vec4 fragColor;

void main() {
    int li = int(vLight + 0.5);
    if (vKind > 6.5) {
        // lens halo: a hot core and a wide soft bloom in the lamp's colour, so the fixtures read as lit (and their
        // colour shows what the lamps are doing: white daylight, blue actinics, dim moonlight)
        float r2 = dot(vec2(vCoord.x, vCoord.y * 2.0 - 1.0), vec2(vCoord.x, vCoord.y * 2.0 - 1.0));
        float g = exp(-r2 * 18.0) * 1.4 + exp(-r2 * 4.0) * 0.35;
        g *= 1.0 - smoothstep(0.7, 1.0, r2);
        vec3 c = uLightColor[li].rgb / max(uLightColor[li].w, 1e-3);
        fragColor = vec4(tonemap(c * 1.5), g * clamp(uLightColor[li].w * 3.0, 0.0, 1.0) * (0.6 + 0.4 * uShaftStrength));
        return;
    }
    if (vKind > 5.5) {
        // beam in the air: faint haze lit by the lamp, brightest near the lens and along the cone's axis
        float ac = 1.0 - abs(vCoord.x);
        float a = ac * ac * (1.0 - vCoord.y * 0.6) * 0.07 * uShaftStrength * uLightColor[li].w;
        fragColor = vec4(tonemap(uLightColor[li].rgb * 1.2), a);
        return;
    }
    float across = 1.0 - abs(vCoord.x);
    across = across * across * (3.0 - 2.0 * across);
    // scrolling noise along the shaft plus a slower surface flicker
    float n = texture(uCaustic, vec2(vCoord.x * 0.15 + vSeed, vCoord.y * 0.6 - uTime * 0.03)).r;
    float flicker = 0.75 + 0.25 * sin(uTime * (0.7 + vSeed) + vSeed * 31.0);
    // surface focus above this shaft (blurred caustic read, drifts like the floor caustics); High only, the cheaper
    // presets keep the plain flicker (the shafts are big blended quads, so every read is paid over a lot of pixels)
    if (uQuality >= 1.5) {
        vec2 su = (vWorld.xz + surfaceSlope(vWorld.xz) * 20.0) * uCausticScale * 0.45 + uTime * vec2(0.017, -0.012);
        flicker *= 0.6 + 0.4 * clamp(texture(uCaustic, su, 3.0).r * 28.0, 0.0, 2.5);
    } else flicker *= 0.9;
    float along = (1.0 - vCoord.y) * smoothstep(0.0, 0.08, vCoord.y + 0.02);
    vec3 toFrag = vWorld - uCamPos.xyz;
    float dist = length(toFrag);
    float nearFade = smoothstep(6.0, 28.0, dist);
    float topFade = 1.0 - smoothstep(0.55, 0.92, abs(toFrag.y) / max(dist, 1e-3));
    float a = 0.6 * uShaftStrength * uLightColor[li].w * along * across * (0.45 + n) * flicker * nearFade * topFade;
    vec3 col = uLightColor[li].rgb * 1.2;
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), a);
}
