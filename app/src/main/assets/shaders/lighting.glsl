// Fragment-side lighting shared by the opaque shaders (ARCHITECTURE §8.4, SIMULATION §9.7 optics).
uniform sampler2D uCaustic;

const vec3 HEMI_TOP = vec3(0.0103, 0.0356, 0.0823);    // #1A3550 linear
const vec3 HEMI_BOTTOM = vec3(0.0019, 0.0052, 0.0116); // #06101C linear
const vec3 NIGHT_FLOOR = vec3(0.0025, 0.0045, 0.0095);

// Radiance from AI Prime i at p (point light, cosine cone pointing down), attenuated through the water
// column above p (Beer-Lambert). L returns the unit direction toward the fixture.
vec3 fixtureLight(int i, vec3 p, out vec3 L) {
    vec3 d = uLightPos[i].xyz - p;
    float dist = max(length(d), 1.0);
    L = d / dist;
    float c = uLightPos[i].w;
    float cone = smoothstep(c - 0.2, c + 0.25, L.y) * max(L.y, 0.0);
    float fall = 4500.0 / (dist * dist + 1500.0);
    float depth = max(uWaterLevel - p.y, 0.0) / max(L.y, 0.25);
    vec3 T = exp(-(uAbsorb.rgb + uAbsorb.w) * depth);
    return uLightColor[i].rgb * (cone * fall) * T;
}

float lightIntensity() { return max(uLightColor[0].w, uLightColor[1].w); }

vec3 hemiAmbient(vec3 n) {
    float h = n.y * 0.5 + 0.5;
    return uAmbientB.rgb + mix(HEMI_BOTTOM, HEMI_TOP, h) * (0.6 + 2.0 * lightIntensity()) + NIGHT_FLOOR;
}

// GGX specular term (D * G / (4 NdotV NdotL)), Schlick-GGX geometry.
float ggxSpec(vec3 N, vec3 V, vec3 L, float rough) {
    vec3 H = normalize(V + L);
    float a = max(rough * rough, 0.02);
    float a2 = a * a;
    float ndh = max(dot(N, H), 0.0);
    float dd = ndh * ndh * (a2 - 1.0) + 1.0;
    float D = a2 / (PI * dd * dd);
    float ndl = max(dot(N, L), 1e-3);
    float ndv = max(dot(N, V), 1e-3);
    float k = a * 0.5;
    float G = (ndl / (ndl * (1.0 - k) + k)) * (ndv / (ndv * (1.0 - k) + k));
    return D * G / (4.0 * ndv * ndl);
}

float fresnelSchlick(float cosT, float f0) { return f0 + (1.0 - f0) * pow(1.0 - clamp(cosT, 0.0, 1.0), 5.0); }

// Animated caustics: two scrolling samples of the shared tile, refracted by the live surface slope,
// projected from above (normal.y) and scaled by light intensity and the water column.
float causticAt(vec3 p, vec3 n) {
    if (uCausticStrength <= 0.0 || p.y > uWaterLevel) return 0.0;
    float depth = max(uWaterLevel - p.y, 0.0);
    vec2 refr = surfaceSlope(p.xz) * depth * 0.6;
    vec2 uv = (p.xz + refr) * uCausticScale;
    float t = uTime;
    float a = texture(uCaustic, uv + t * vec2(0.013, 0.008)).r;
    float b = texture(uCaustic, uv * 1.7 - t * vec2(0.010, -0.014)).r;
    float c = pow(a * b, 1.1) * 4.0;
    return c * max(n.y, 0.0) * lightIntensity() * exp(-0.012 * depth);
}

// Lambert (optionally wrapped) + GGX from both fixtures, hemispherical ambient and caustics.
vec3 shadeLit(vec3 albedo, vec3 N, vec3 V, vec3 p, float rough, float ao, float f0, float wrap, float specScale) {
    vec3 col = hemiAmbient(N) * albedo * ao;
    for (int i = 0; i < 2; i++) {
        vec3 L;
        vec3 rad = fixtureLight(i, p, L);
        float ndl = dot(N, L);
        float diff = max((ndl + wrap) / (1.0 + wrap), 0.0);
        float spec = ggxSpec(N, V, L, rough) * fresnelSchlick(max(dot(normalize(V + L), V), 0.0), f0) * max(ndl, 0.0);
        col += rad * (albedo * diff + vec3(spec * specScale));
    }
    col += albedo * causticAt(p, N) * uCausticStrength;
    return col;
}

// Beer-Lambert spectral attenuation along the in-water part of the view path + depth fog toward the
// (light-tinted) fog colour: fog = 1 - exp(-density x dist).
vec3 applyWater(vec3 col, vec3 p) {
    float d = waterPath(uCamPos.xyz, p);
    vec3 T = exp(-(uAbsorb.rgb + uAbsorb.w) * d);
    float f = 1.0 - exp(-uFog.w * d);
    return mix(col * T, uFog.rgb, f);
}

// Filmic curve (Hejl-style x/(x+0.155)*1.019); it already includes the display gamma, so no extra pow.
vec3 tonemap(vec3 x) {
    x = max(x, vec3(0.0));
    return x / (x + 0.155) * 1.019;
}
