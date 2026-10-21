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
            BrainKind.ARROW_CRAB -> ArrowCrabBrain(c)
            BrainKind.EMERALD_CRAB -> EmeraldCrabBrain(c)
            BrainKind.FEATHER_DUSTER -> FeatherDusterBrain(c)
            BrainKind.NASSARIUS -> NassariusBrain(c)
            BrainKind.ASTREA -> AstreaBrain(c)
            BrainKind.TROCHUS -> TrochusBrain(c)
            BrainKind.CONCH -> ConchBrain(c)
            BrainKind.URCHIN -> UrchinBrain(c)
            BrainKind.ASTERINA -> AsterinaBrain(c)
            BrainKind.DIAMOND_GOBY -> DiamondGobyBrain(c)
            BrainKind.LEOPARD_WRASSE -> LeopardWrasseBrain(c)
            BrainKind.FAIRY_WRASSE -> FairyWrasseBrain(c)
            BrainKind.BOBTAIL_SQUID -> BobtailSquidBrain(c)
            BrainKind.FIREFISH -> FirefishBrain(c)
            BrainKind.CHROMIS -> ChromisBrain(c)
            BrainKind.ANEMONE -> AnemoneBrain(c)
            BrainKind.PEPPERMINT_SHRIMP -> PeppermintShrimpBrain(c)
            BrainKind.FILE_CLAM -> FileClamBrain(c)
            BrainKind.GRAMMA -> GrammaBrain(c)
            BrainKind.WATCHMAN_GOBY -> WatchmanGobyBrain(c)
            BrainKind.GENERIC_FISH -> GenericFishBrain(c)
            BrainKind.GENERIC_INVERT -> GenericInvertBrain(c)
        }
        b.initialPlace(c)
        return b
    }
}
