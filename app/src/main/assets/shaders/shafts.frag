#version 300 es
#include "common.glsl"
#include "lighting.glsl"

in vec2 vCoord;
in float vSeed;
in float vLight;
in vec3 vWorld;

out vec4 fragColor;

void main() {
    int li = int(vLight + 0.5);
    float across = 1.0 - abs(vCoord.x);
    across = across * across;
    // scrolling noise along the shaft
    float n = texture(uCaustic, vec2(vCoord.x * 0.15 + vSeed, vCoord.y * 0.6 - uTime * 0.03)).r;
    float a = 0.08 * uLightColor[li].w * (1.0 - vCoord.y) * across * (0.5 + n);
    vec3 col = uLightColor[li].rgb * 1.2;
    col = applyWater(col, vWorld);
    fragColor = vec4(tonemap(col), a);
}
