package com.reeflive.core.ai

/**
 * A hermit crab changing shells (research 04 §5-6): while [InvertEcology] has it mid-swap or mid-fight
 * it stops, pulled half out of the shell, and carries on afterwards. The decision itself is made by
 * the ecology (slow, once per chemistry tick); this only shows it.
 */
class ShellSwapBehavior(val b: SpeciesBrain) : Behavior("shellswap", "Changing shells") {
    override val committed: Boolean get() = swapping(b.c)
    private fun swapping(c: Ctx) = c.sim.invertEcology.swapping(c.o)
    override fun score(c: Ctx) = if (swapping(c)) 0.9 else 0.0
    override fun update(c: Ctx, out: SteerIntent) {
        out.rest(Rest(c.o.pos, c.o.heading, c.o.up, 6), 1.0)
        c.o.extension = 0.4
        c.o.appendagePhase += c.dt * 6
        if (!swapping(c)) finished = true
    }
    override fun exit(c: Ctx) { c.o.extension = 1.0 }
}
