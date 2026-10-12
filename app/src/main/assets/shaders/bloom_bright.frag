#version 300 es
precision highp float;
// Bloom bright pass (High quality): threshold 0.8 on the tonemapped scene, written at half resolution.
in vec2 vUv;
uniform sampler2D uScene;
out vec4 fragColor;
void main() {
    vec3 c = texture(uScene, vUv).rgb;
    float l = max(c.r, max(c.g, c.b));
    float k = smoothstep(0.8, 1.0, l);
    fragColor = vec4(c * k, 1.0);
}
