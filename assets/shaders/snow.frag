#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Marine snow flake: soft disc lit by the fixtures, with a forward-scatter glint when the flake sits between the
// camera and a light (it sparkles in the beams), then water absorption + fog like everything else.

in float vAlpha;
in float vSeed;
in vec3 vWorld;

out vec4 fragColor;

void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(c, c);
    if (r2 > 1.0) discard;
    float soft = 1.0 - smoothstep(0.2, 1.0, r2);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    vec3 L0;
    vec3 L1;
    vec3 rad = fixtureLight(0, vWorld, L0);
    vec3 rad1 = fixtureLight(1, vWorld, L1);
    float g0 = pow(max(dot(-V, L0), 0.0), 8.0);
    float g1 = pow(max(dot(-V, L1), 0.0), 8.0);
    vec3 col = vec3(0.75, 0.8, 0.78) * ((rad + rad1) * 0.8 + hemiAmbient(vec3(0.0, 1.0, 0.0)) * 1.5)
             + (rad * g0 + rad1 * g1) * 2.5;
    col = applyWater(col, vWorld);
    float a = vAlpha * soft * mix(0.22, 0.38, vSeed) * mix(1.0, 0.45, uNight);
    fragColor = vec4(tonemap(col), a);
}
