#version 300 es
#include "common.glsl"
#include "lighting.glsl"

in vec4 vColor;
in vec3 vWorld;

out vec4 fragColor;

void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(c, c);
    if (r2 > 1.0) discard;
    float soft = 1.0 - smoothstep(0.35, 1.0, r2);
    vec3 L;
    vec3 rad = fixtureLight(0, vWorld, L) + fixtureLight(1, vWorld, L);
    vec3 col = vColor.rgb * (rad * 0.9 + hemiAmbient(vec3(0.0, 1.0, 0.0)) * 2.0);
    col = applyWater(col, vWorld);
    float a = vColor.a * soft * mix(1.0, 0.5, uNight);
    fragColor = vec4(tonemap(col), a);
}
