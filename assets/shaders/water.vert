#version 300 es
#include "common.glsl"
// Water surface grid at the water level (TankDims.WATER_LEVEL_CM, 38.1), displaced by the simulated heightfield (SIMULATION §9.7).

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec4 aColor;

uniform mat4 uModel;

out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec4 vColor;

void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    w.y += surfaceHeight(w.xz) + swell(w.xz, 5).x;
    vWorld = w.xyz;
    vNormal = vec3(0.0, 1.0, 0.0);
    vUv = aUv;
    vColor = aColor;
    gl_Position = uViewProj * w;
}
