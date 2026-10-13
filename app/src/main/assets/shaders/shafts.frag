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

out vec4 fragColor;

void main() {
    int li = int(vLight + 0.5);
    float across = 1.0 - abs(vCoord.x);
    across = across * across * (3.0 - 2.0 * across);
    // scrolling noise along the shaft plus a slower surface flicker
    float n = texture(uCaustic, vec2(vCoord.x * 0.15 + vSeed, vCoord.y * 0.6 - uTime * 0.03)).r;
    float flicker = 0.75 + 0.25 * sin(uTime * (0.7 + vSeed) + vSeed * 31.0);
    float along = (1.0 - vCoord.y) * smoothstep(0.0, 0.08, vCoord.y + 0.02);
    vec3 toFrag = vWorld - uCamPos.xyz;
    float dist = length(toFrag);
    float nearFade = smoothstep(6.0, 28.0, dist);
    float topFade = 1.0 - smoothstep(0.55, 0.92, abs(toFrag.y) / max(dist, 1e-3));
    float a = 0.10 * uShaftStrength * uLightColor[li].w * along * across * (0.45 + n) * flicker * nearFade * topFade;
    vec3 col = uLightColor[li].rgb * 1.2;
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), a);
}
