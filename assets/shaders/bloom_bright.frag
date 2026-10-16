#version 300 es
precision highp float;
// Bloom bright pass (High quality): soft threshold 0.8..1 on the tonemapped scene, written at half resolution.
in vec2 vUv;
uniform sampler2D uScene;
out vec4 fragColor;
void main() {
    vec3 c = texture(uScene, vUv).rgb;
    float l = max(c.r, max(c.g, c.b));
    // soft knee from 0.8 so lens cores, glints and the hottest caustics glow without a hard threshold edge
    float k = smoothstep(0.8, 1.0, l);
    k *= k;
    fragColor = vec4(c * k, 1.0);
}
