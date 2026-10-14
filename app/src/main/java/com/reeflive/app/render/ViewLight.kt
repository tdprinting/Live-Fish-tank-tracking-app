package com.reeflive.app.render

/**
 * Watch-mode looks for the lamps (Day, Dusk, Night, Moonlight), drawn by the renderer only: the real tank's simulation
 * keeps its own light schedule (corals still photosynthesise on the real clock), the owner just sees the tank in
 * another light. [REAL] follows the schedule. Channel order as core LightSchedule: white, blue, royal, red, green,
 * violet, uv, moon.
 */
enum class ViewLight(val label: String, val channels: FloatArray?) {
    REAL("Real clock", null),
    DAY("Day", floatArrayOf(0.85f, 0.9f, 1f, 0.45f, 0.45f, 0.6f, 0.3f, 0f)),
    DUSK("Dusk", floatArrayOf(0.12f, 0.35f, 0.55f, 0.4f, 0.08f, 0.3f, 0.05f, 0f)),
    NIGHT("Night", floatArrayOf(0f, 0.04f, 0.1f, 0f, 0f, 0.05f, 0f, 0.35f)),
    MOON("Moonlight", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)),
}
