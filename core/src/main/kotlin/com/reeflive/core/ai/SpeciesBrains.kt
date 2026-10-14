package com.reeflive.core.ai

import com.reeflive.core.sim.BrainKind

/** Brain factory (SIMULATION §4.5): picks the brain from `species.brain` / genus and places the animal. */
object SpeciesBrains {
    fun create(c: Ctx): SpeciesBrain {
        val b = when (c.o.brainKind) {
            BrainKind.CLOWN -> ClownBrain(c)
            BrainKind.TANG -> TangBrain(c)
            BrainKind.CARDINAL -> CardinalBrain(c)
            BrainKind.WRASSE -> WrasseBrain(c)
            BrainKind.EEL -> EelBrain(c)
            BrainKind.ENGINEER_GOBY -> GobyBrain(c)
            BrainKind.HAWK -> HawkBrain(c)
            BrainKind.LAWNMOWER_BLENNY -> LawnmowerBlennyBrain(c)
            BrainKind.PEEKER_BLENNY -> PeekerBlennyBrain(c)
            BrainKind.DOTTYBACK -> DottybackBrain(c)
            BrainKind.BANDED_SHRIMP -> BandedShrimpBrain(c)
            BrainKind.CLEANER_SHRIMP -> CleanerShrimpBrain(c)
            BrainKind.HERMIT -> HermitBrain(c)
            BrainKind.BRITTLE_STAR -> BrittleStarBrain(c)
            BrainKind.SALLY_CRAB -> SallyCrabBrain(c)
            BrainKind.FEATHER_DUSTER -> FeatherDusterBrain(c)
            BrainKind.NASSARIUS -> NassariusBrain(c)
            BrainKind.ASTREA -> AstreaBrain(c)
            BrainKind.ASTERINA -> AsterinaBrain(c)
            BrainKind.DIAMOND_GOBY -> DiamondGobyBrain(c)
            BrainKind.BOBTAIL_SQUID -> BobtailSquidBrain(c)
            BrainKind.GENERIC_FISH -> GenericFishBrain(c)
            BrainKind.GENERIC_INVERT -> GenericInvertBrain(c)
        }
        b.initialPlace(c)
        return b
    }
}
