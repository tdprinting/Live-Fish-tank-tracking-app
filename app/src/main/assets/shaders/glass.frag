#version 300 es
#include "common.glsl"
#include "lighting.glsl"
// Front/side glass (ARCHITECTURE §8.4 item 7): Schlick Fresnel (F0 0.04 glass, SIMULATION §9.7) reflecting
// the room environment (equirect room_env.png) and the fixture glow; premultiplied output (rendered last).
// Algae on the inner face (research 09 A6): green film haze and diatom dust, densest diatoms, cyano and dinos
// along the sand line; drawn behind the reflection and only below the water line. On the front pane the film is
// spatial: uGlassMask (GlassMask, 64 x 32 over the wetted pane, 1 = untouched, 0 = scraped bare) thins every group
// where the Flipper has been dragged, and the cleared stripes fill in again as the mask regrows.

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vColor;

uniform sampler2D uEnv;
uniform sampler2D uGlassMask;   // R: film density on the front pane (white 1x1 while the pane is untouched)
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
    // A pane seen from inside the tank (the side, back and bottom panes looked at through the water while the
    // camera is out front) mirrors the lit water by total internal reflection, not the room: without this the
    // grazing side panes picked up the pale room walls at Fresnel ~1 and read as beige/pink panels.
    vec3 Ng = normalize(vNormal);
    vec3 Nout = Ng * (dot(Ng, vWorld - uTank.xyz * 0.5) < 0.0 ? -1.0 : 1.0);
    bool fromInside = dot(Nout, uCamPos.xyz - vWorld) < 0.0;
    if (fromInside) env = uFog.rgb * 1.2 + uAmbientB.rgb * 0.5;
    // highlights of the two fixtures in the pane
    for (int i = 0; i < 2; i++) {
        vec3 L = normalize(uLightPos[i].xyz - vWorld);
        env += uLightColor[i].rgb * pow(max(dot(R, L), 0.0), 400.0) * 3.0;
    }
    vec3 tint = vec3(0.878, 0.973, 0.941) * 0.05 * (0.2 + lightIntensity());
    float a = clamp(F * (fromInside ? 0.35 : 0.6) + 0.02, 0.0, 1.0);
    // edge darkening where the glass is seen nearly edge-on (thickness)
    a += (1.0 - ndv) * (1.0 - ndv) * 0.08;
    vec3 col = tonemap(env * F + tint);
    if (dot(uAlgaeGlass, vec4(1.0)) > 0.001) {
        a = clamp(a, 0.0, 1.0);
        float y = vWorld.y;
        float wet = 1.0 - smoothstep(uWaterLevel - 1.0, uWaterLevel + 0.5, y);
        float nearSand = 1.0 - smoothstep(uParams2.w, uParams2.w + 18.0, y);
        float sandLine = 1.0 - smoothstep(uParams2.w, uParams2.w + 6.0, y);
        float n1 = fbm3(vWorld * 0.12);
        float n2 = fbm3(vWorld * 0.21 + vec3(13.0, 3.0, 29.0));
        // u along the tank, v from the sand line to the water line (core GlassMask layout)
        vec2 muv = vec2(vWorld.x / uTank.x, (y - uParams2.w) / max(uWaterLevel - uParams2.w, 1.0));
        float scraped = texture(uGlassMask, clamp(muv, 0.0, 1.0)).r;
        bool frontPane = abs(Ng.z) > 0.9 && abs(vWorld.z - uTank.z) < 1.0;
        float keep = frontPane ? clamp(scraped, 0.0, 1.0) : 1.0;
        float film = uAlgaeGlass.x * keep;
        float dia = uAlgaeGlass.y * mix(0.45, 1.0, nearSand) * keep;
        float fm = algaePatch(n1, film) * algaeDensity(film) * 0.7 + film * 0.3;
        float dm = algaePatch(n2, dia) * algaeDensity(dia) * 0.6 + dia * 0.2;
        float cm = algaePatch(1.0 - n1, uAlgaeGlass.z * sandLine * keep) * 0.8;
        float km = algaePatch(n2, uAlgaeGlass.w * sandLine * keep) * 0.6;
        vec3 alb = vec3(0.10, 0.26, 0.035);                                // green film
        alb = mix(alb, vec3(0.26, 0.13, 0.03), dm / max(fm + dm, 1e-3));    // diatom brown
        float m = clamp(fm + dm, 0.0, 1.0);
        alb = mix(alb, vec3(0.16, 0.012, 0.016), cm);                     // cyano red
        alb = mix(alb, vec3(0.24, 0.16, 0.06), km);                       // dino khaki
        m = clamp(max(m, max(cm, km)), 0.0, 0.75) * wet;
        // lit from inside the tank: the film faces the water, translucent (wrapped Lambert)
        vec3 Nin = -Nout;
        vec3 ac = tonemap(applyWater(shadeLit(alb, Nin, V, vWorld, 0.8, 1.0, 0.04, 0.8, 0.2), vWorld));
        // straight-alpha "over": reflection layer (col, a) in front of the algae layer (ac, m)
        float outA = a + m * (1.0 - a);
        col = (col * a + ac * m * (1.0 - a)) / max(outA, 1e-4);
        a = outA;
    }
    fragColor = vec4(col, a);
}
