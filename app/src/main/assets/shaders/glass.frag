#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Front/side glass (ARCHITECTURE §8.4 item 7): Schlick Fresnel (F0 0.04 glass, SIMULATION §9.7) reflecting
// the room environment (equirect room_env.png) and the fixture glow; premultiplied output (rendered last).

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uEnv;
uniform vec4 uBaseColor;

out vec4 fragColor;

vec2 equirect(vec3 d) {
    return vec2(atan(d.z, d.x) / (2.0 * PI) + 0.5, acos(clamp(d.y, -1.0, 1.0)) / PI);
}

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos.xyz - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    float ndv = max(dot(N, V), 0.0);
    float F = fresnelSchlick(ndv, 0.04);
    vec3 R = reflect(-V, N);
    vec3 env = toLinear(texture(uEnv, equirect(R)).rgb) * 0.6;
    // highlights of the two fixtures in the pane
    for (int i = 0; i < 2; i++) {
        vec3 L = normalize(uLightPos[i].xyz - vWorld);
        env += uLightColor[i].rgb * pow(max(dot(R, L), 0.0), 400.0) * 3.0;
    }
    vec3 tint = vec3(0.878, 0.973, 0.941) * 0.05 * (0.2 + lightIntensity());
    float a = clamp(F * 0.6 + 0.02, 0.0, 1.0);
    // edge darkening where the glass is seen nearly edge-on (thickness)
    a += (1.0 - ndv) * (1.0 - ndv) * 0.08;
    vec3 col = tonemap(env * F + tint);
    fragColor = vec4(col, a);
}
