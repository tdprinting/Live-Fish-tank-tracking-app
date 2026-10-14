#version 300 es
precision mediump float;
// Shadow-map depth pass (ShadowMap.kt): paired with static.vert / fish.vert while the Frame UBO's uViewProj holds the
// fixtures' orthographic projection. Depth only; the colour output is discarded (no colour attachment).
out vec4 fragColor;

void main() {
    fragColor = vec4(1.0);
}
