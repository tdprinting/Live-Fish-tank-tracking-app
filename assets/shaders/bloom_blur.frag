#version 300 es
precision highp float;
// Separable 9-tap Gaussian blur; uDir = texel step along x or y.
in vec2 vUv;
uniform sampler2D uScene;
uniform vec2 uDir;
out vec4 fragColor;
void main() {
    float w[5] = float[5](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    vec3 c = texture(uScene, vUv).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        c += texture(uScene, vUv + uDir * float(i)).rgb * w[i];
        c += texture(uScene, vUv - uDir * float(i)).rgb * w[i];
    }
    fragColor = vec4(c, 1.0);
}
