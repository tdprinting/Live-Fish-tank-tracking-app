// Shared by every ReefLive shader (ARCHITECTURE §8.4). Included right after #version.
precision highp float;
precision highp int;

// std140 `Frame` UBO, binding 0. Keep in sync with FrameUniforms.kt.
layout(std140) uniform Frame {
    mat4 uView;
    mat4 uProj;
    mat4 uViewProj;
    vec4 uCamPos;        // xyz eye (cm), w time (s)
    vec4 uLightPos[2];   // xyz AI Prime position (cm), w cos(half cone)
    vec4 uLightColor[2]; // rgb linear colour x intensity, w intensity
    vec4 uAmbientB;      // rgb ambient (keyColor x 0.18), w blue fraction
    vec4 uFog;           // rgb fog colour, w fog density (1/cm)
    vec4 uParams;        // x water level, y caustic strength, z caustic scale, w quality (0 low, 1 medium, 2 high)
    vec4 uParams2;       // x night (0/1), y algae bloom, z flow strength, w sand top (cm)
    vec4 uAbsorb;        // rgb Beer-Lambert absorption (1/cm), w scattering (1/cm)
    vec4 uTank;          // xyz interior size (cm), w unused
    vec4 uSurfaceDims;   // xy heightfield size, z 1 when heights are present, w unused
};

#define uTime uCamPos.w
#define uWaterLevel uParams.x
#define uCausticStrength uParams.y
#define uCausticScale uParams.z
#define uQuality uParams.w
#define uNight uParams2.x
#define uAlgaeBloom uParams2.y
#define uFlowStrength uParams2.z
#define uBlueFraction uAmbientB.w

const float PI = 3.14159265;

// Water-surface heightfield (cm above the mean level), x across the tank length, y across the depth.
uniform sampler2D uSurface;

float surfaceHeight(vec2 xz) {
    if (uSurfaceDims.z < 0.5) return 0.0;
    return texture(uSurface, clamp(xz / uTank.xz, 0.0, 1.0)).r;
}

vec2 surfaceSlope(vec2 xz) {
    if (uSurfaceDims.z < 0.5) return vec2(0.0);
    vec2 e = vec2(1.3, 0.0);
    return vec2(surfaceHeight(xz + e.xy) - surfaceHeight(xz - e.xy), surfaceHeight(xz + e.yx) - surfaceHeight(xz - e.yx)) / (2.0 * e.x);
}

vec3 toLinear(vec3 srgb) { return pow(max(srgb, vec3(0.0)), vec3(2.2)); }

// Rotate v by unit quaternion q (xyzw).
vec3 qrot(vec4 q, vec3 v) { return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v); }

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Length of the segment a->b that lies inside the water volume (box 0..tank, below the surface).
float waterPath(vec3 a, vec3 b) {
    vec3 d = b - a;
    vec3 lo = vec3(0.0);
    vec3 hi = vec3(uTank.x, uWaterLevel, uTank.z);
    vec3 inv = 1.0 / mix(d, vec3(1e-5), vec3(lessThan(abs(d), vec3(1e-5))));
    vec3 t0 = (lo - a) * inv;
    vec3 t1 = (hi - a) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    float tn = max(max(tmin.x, tmin.y), max(tmin.z, 0.0));
    float tf = min(min(tmax.x, tmax.y), min(tmax.z, 1.0));
    return max(tf - tn, 0.0) * length(d);
}
