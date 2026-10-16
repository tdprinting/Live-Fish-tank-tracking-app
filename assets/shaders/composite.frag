#version 300 es
precision highp float;
// Final composite: resolved scene + bloom (High), and the follow-mode lens (PostFx): everything outside the
// followed fish's focus circle blends toward a half-resolution blur (cheap depth-of-field look), with a soft
// vignette and a little chromatic aberration that grows toward the corners. uLens.x fades the whole lens.
in vec2 vUv;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform sampler2D uDof;
uniform float uBloomStrength;
uniform vec4 uLens;   // x amount 0..1, y vignette strength, z chromatic aberration (uv at the corners), w aspect (w/h)
uniform vec3 uFocus;  // xy focus centre (uv), z sharp radius (v units)
out vec4 fragColor;

void main() {
    vec3 c;
    float amt = uLens.x;
    if (amt > 0.001) {
        vec2 fromC = vUv - 0.5;
        float edge = dot(fromC, fromC);                 // 0 at the centre, 0.5 in the corners
        vec2 ca = fromC * (uLens.z * amt * edge * 4.0);
        vec3 sharp = vec3(texture(uScene, vUv - ca).r, texture(uScene, vUv).g, texture(uScene, vUv + ca).b);
        vec3 soft = vec3(texture(uDof, vUv - ca * 1.5).r, texture(uDof, vUv).g, texture(uDof, vUv + ca * 1.5).b);
        vec2 d = (vUv - uFocus.xy) * vec2(uLens.w, 1.0);
        float k = smoothstep(uFocus.z, uFocus.z * 2.2 + 0.06, length(d)) * amt;
        c = mix(sharp, soft, k);
        float vig = 1.0 - uLens.y * amt * smoothstep(0.45, 1.0, length(fromC) * 1.414);
        c *= vig;
    } else {
        c = texture(uScene, vUv).rgb;
    }
    // screen blend: highlights bloom into a soft halo instead of clipping to flat white
    if (uBloomStrength > 0.0) c = 1.0 - (1.0 - c) * (1.0 - min(texture(uBloom, vUv).rgb * uBloomStrength, vec3(1.0)));
    fragColor = vec4(min(c, vec3(1.0)), 1.0);
}
