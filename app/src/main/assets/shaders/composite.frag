#version 300 es
precision highp float;
// Final composite: resolved scene + bloom.
in vec2 vUv;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uBloomStrength;
out vec4 fragColor;
void main() {
    vec3 c = texture(uScene, vUv).rgb + texture(uBloom, vUv).rgb * uBloomStrength;
    fragColor = vec4(min(c, vec3(1.0)), 1.0);
}
