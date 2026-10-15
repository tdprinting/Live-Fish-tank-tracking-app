// Fragment-side lighting shared by the opaque shaders (ARCHITECTURE §8.4, SIMULATION §9.7 optics).
uniform sampler2D uCaustic;
// Shadow depth map (ShadowMap.kt, unit 14), compared in hardware (bilinear PCF per tap).
uniform highp sampler2DShadow uShadowMap;

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

// Hue of the live fixture light, normalised so its brightest channel is 1 (follows the AI Prime schedule).
vec3 lightTint() {
    vec3 s = uLightColor[0].rgb + uLightColor[1].rgb;
    return s / max(max(s.r, max(s.g, s.b)), 1e-3);
}

// Stronger caustics, tinted by the live light: two scrolling samples of the shared tile refracted by the surface
// slope. High quality splits the second sample per channel (dispersion: thin colour fringes on the bright lines).
// Faces that look sideways still catch a little of the pattern.
vec3 causticColor(vec3 p, vec3 n) {
    if (uCausticStrength <= 0.0 || p.y > uWaterLevel) return vec3(0.0);
    float depth = max(uWaterLevel - p.y, 0.0);
    vec2 refr = surfaceSlope(p.xz) * depth * 0.6;
    // walls see the pattern projected down their face: shift by height along the normal so it varies (stretched)
    // vertically instead of smearing into stripes
    vec2 uv = (p.xz + refr + n.xz * (p.y * 0.55)) * uCausticScale;
    float t = uTime;
    vec2 uvA = uv + t * vec2(0.013, 0.008);
    vec2 uvB = uv * 1.7 - t * vec2(0.010, -0.014);
    float a = texture(uCaustic, uvA).r;
    vec3 b;
    if (uCausticDispersion > 0.5) {
        vec2 disp = vec2(0.0035, 0.0025) * (1.0 + depth * 0.03);
        b = vec3(texture(uCaustic, uvB + disp).r, texture(uCaustic, uvB).r, texture(uCaustic, uvB - disp).r);
    } else {
        b = vec3(texture(uCaustic, uvB).r);
    }
    vec3 c = pow(a * b, vec3(1.25)) * 5.0;
    // Medium/High: side faces keep ~a third of the shimmer (light arrives steeply but not only from straight above)
    float side = uQuality >= 0.5 ? 0.32 : 0.15;
    float facing = clamp(n.y * (1.0 - side) + side, 0.0, 1.0);
    return c * lightTint() * (facing * lightIntensity() * exp(-0.010 * depth));
}

// Soft contact shadows under the nearest fish (uCasters): a blob below each body that widens and fades with height.
// Static receivers only (fish.frag defines NO_CONTACT_SHADOW so a fish does not shadow itself).
float contactShadow(vec3 p) {
    float occ = 0.0;
    int n = int(uCasterCount + 0.5);
    for (int i = 0; i < 12; i++) {
        if (i >= n) break;
        vec4 c = uCasters[i];
        float h = c.y - p.y;
        if (h < -0.5 || h > 32.0) continue;
        vec2 d = p.xz - c.xz;
        float r = max(c.w * 0.3, 0.8) + max(h, 0.0) * 0.12;
        float d2 = dot(d, d) / (r * r);
        if (d2 > 4.0) continue;
        float blob = exp(-d2 * 1.4);
        float fade = smoothstep(-0.5, 0.6, h) * (1.0 - smoothstep(3.0, 32.0, h));
        occ = max(occ, blob * fade);
    }
    return 1.0 - 0.6 * occ;
}

// Shadow from the fixtures (uShadowMap): 1 lit .. 0 fully shadowed (scaled by uShadow.z). Normal offset and a world
// bias toward the light avoid acne; High takes 8 rotated taps (~1.6 cm penumbra), Medium 4, Low has no map.
float shadowAt(vec3 p, vec3 n) {
    if (uShadow.x < 0.5) return 1.0;
    vec3 q = p + n * (0.25 + 0.5 * (1.0 - abs(n.y))) + vec3(0.0, uShadow.w, 0.0);
    vec3 c = (uShadowMat * vec4(q, 1.0)).xyz * 0.5 + 0.5;
    if (c.x <= 0.0 || c.x >= 1.0 || c.y <= 0.0 || c.y >= 1.0 || c.z >= 1.0) return 1.0;
    // the kernel is in cm (uShadow.y = cm per texel along u), rotated per pixel (interleaved gradient noise)
    float ang = 6.2831 * fract(52.9829 * fract(dot(gl_FragCoord.xy, vec2(0.06711, 0.00584))));
    vec2 cs = vec2(cos(ang), sin(ang));
    mat2 rot = mat2(cs.x, cs.y, -cs.y, cs.x);
    vec2 texel = vec2(1.0) / vec2(textureSize(uShadowMap, 0));
    float lit;
    if (uQuality >= 1.5) {
        vec2 k = texel * (0.8 / max(uShadow.y, 1e-3));
        lit = 0.0;
        lit += textureLod(uShadowMap, vec3(c.xy + rot * vec2(-0.94, -0.40) * k, c.z), 0.0);
        lit += textureLod(uShadowMap, vec3(c.xy + rot * vec2(0.95, -0.77) * k, c.z), 0.0);
        lit += textureLod(uShadowMap, vec3(c.xy + rot * vec2(-0.09, -0.93) * k, c.z), 0.0);
        lit += textureLod(uShadowMap, vec3(c.xy + rot * vec2(0.34, 0.29) * k, c.z), 0.0);
        lit += textureLod(uShadowMap, vec3(c.xy + rot * vec2(-0.82, 0.54) * k, c.z), 0.0);
        lit += textureLod(uShadowMap, vec3(c.xy + rot * vec2(0.54, 0.89) * k, c.z), 0.0);
        lit += textureLod(uShadowMap, vec3(c.xy + rot * vec2(0.97, 0.23) * k, c.z), 0.0);
        lit += textureLod(uShadowMap, vec3(c.xy + rot * vec2(-0.32, 0.12) * k, c.z), 0.0);
        lit *= 0.125;
    } else {
        vec2 k = texel * (0.6 / max(uShadow.y, 1e-3));
        lit = 0.25 * (textureLod(uShadowMap, vec3(c.xy + rot * vec2(-0.7, -0.7) * k, c.z), 0.0)
                    + textureLod(uShadowMap, vec3(c.xy + rot * vec2(0.7, -0.7) * k, c.z), 0.0)
                    + textureLod(uShadowMap, vec3(c.xy + rot * vec2(-0.7, 0.7) * k, c.z), 0.0)
                    + textureLod(uShadowMap, vec3(c.xy + rot * vec2(0.7, 0.7) * k, c.z), 0.0));
    }
    return mix(1.0, lit, uShadow.z);
}

// Slow light flicker of the direct beam as the rippled surface focuses and spreads it (a heavily blurred, scrolling
// read of the caustic tile, ~1 around its mean). Medium and High.
float surfaceFlicker(vec3 p) {
    if (uQuality < 0.5 || p.y > uWaterLevel) return 1.0;
    vec2 uv = (p.xz + surfaceSlope(p.xz) * (uWaterLevel - p.y) * 0.6) * uCausticScale * 0.45;
    float f = texture(uCaustic, uv + uTime * vec2(0.017, -0.012), 3.5).r;
    return 0.82 + 0.18 * clamp(f * 28.0, 0.0, 2.2);
}

// Bump from a scalar height h (cm) via screen-space derivatives (surface gradient, no tangents). Callers keep it in
// uniform control flow (derivatives).
vec3 bumpH(vec3 N, vec3 p, float h) {
    vec3 sx = dFdx(p), sy = dFdy(p);
    vec3 r1 = cross(sy, N), r2 = cross(N, sx);
    float det = dot(sx, r1);
    vec3 g = sign(det) * (dFdx(h) * r1 + dFdy(h) * r2);
    vec3 r = abs(det) * N - g;
    float l = dot(r, r);
    return l > 1e-20 ? r * inversesqrt(l) : N;   // degenerate (edge-on / sub-pixel) triangles: no NaN speckles
}

// Wet clear-coat lobe (mucus / water film): a tight GGX highlight from both fixtures on top of the base response.
vec3 wetCoat(vec3 N, vec3 V, vec3 p, float rough, float strength) {
    vec3 s = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        vec3 L;
        vec3 rad = fixtureLight(i, p, L);
        float ndl = max(dot(N, L), 0.0);
        s += rad * ggxSpec(N, V, L, rough) * fresnelSchlick(max(dot(normalize(V + L), V), 0.0), 0.02) * ndl;
    }
    return s * strength;
}

// Shadow-map visibility from the last shadeLit call: extra light lobes added after it (glints, sheen, back-light)
// reuse it instead of sampling the PCF kernel again.
float gShadow = 1.0;

// Lambert (optionally wrapped) + GGX from both fixtures, hemispherical ambient and caustics; direct light and
// caustics are dimmed under nearby fish (contact shadows).
vec3 shadeLit(vec3 albedo, vec3 N, vec3 V, vec3 p, float rough, float ao, float f0, float wrap, float specScale) {
    vec3 col = hemiAmbient(N) * albedo * ao;
    vec3 direct = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        vec3 L;
        vec3 rad = fixtureLight(i, p, L);
        float ndl = dot(N, L);
        float diff = max((ndl + wrap) / (1.0 + wrap), 0.0);
        float spec = ggxSpec(N, V, L, rough) * fresnelSchlick(max(dot(normalize(V + L), V), 0.0), f0) * max(ndl, 0.0);
        direct += rad * (albedo * diff + vec3(spec * specScale));
    }
    direct *= surfaceFlicker(p);
    direct += albedo * causticColor(p, N) * uCausticStrength;
#ifndef NO_CONTACT_SHADOW
    if (uCasterCount > 0.5) direct *= contactShadow(p);
#endif
    gShadow = shadowAt(p, N);
    direct *= gShadow;
    return col + direct;
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
// Shared grade, so every shader lands in one look: partial white balance toward the fixture colour (the eye / a
// camera adapts to the AI Prime mix, so cream rock reads cream under a blue-heavy daylight, while dusk keeps more blue
// and moonlight stays blue), then a gentle S-curve and a touch of saturation.
vec3 tonemap(vec3 x) {
    x = max(x, vec3(0.0));
    vec3 adapt = mix(vec3(1.0), lightTint(), 0.6 * (1.0 - uNight) * smoothstep(0.03, 0.25, lightIntensity()));
    x /= max(adapt, vec3(0.2));
    vec3 c = x / (x + 0.155) * 1.019;
    c = clamp(c, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.18);
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return clamp(mix(vec3(l), c, 1.06), 0.0, 1.0);
}
