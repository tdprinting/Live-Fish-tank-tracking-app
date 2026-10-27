package com.reeflive.core.sim

/**
 * Sandbox triggers for the three nuisance outbreaks of research 09 A2 (SIMULATION §11.2): each sets up the conditions
 * that start one in a real reef and plants the seed the model grows from. Shared by the web and Android sandboxes and
 * checked by `AlgaeOutbreaksTest`, so the presets and the model stay in step.
 */
object AlgaeOutbreaks {
    /**
     * Dinoflagellates (A2 stage 4b): nutrients stripped to ultra-low and held there (NO3 < 1, PO4 < 0.02: the aggressive
     * export that starts them), a young, low-diversity microbial community, and a light brown dusting to grow from.
     * Release the held nitrate and phosphate (or reset) to let the tank recover.
     */
    fun dinos(): List<Command> = listOf(
        Command.RampParam("no3", ULN_NO3, 0.0, hold = true),
        Command.RampParam("po4", ULN_PO4, 0.0, hold = true),
        Command.SetParam("nh3", 0.0),
        Command.SetParam("microbes", YOUNG_MICROBES),
        Command.SetParam("dinos", DINO_SEED),
    )

    /**
     * Cyanobacteria (A2 stage 4a): a dead spot over the sand and the shaded rock, dissolved organics held high (food and
     * waste rotting faster than the skimmer takes them), phosphate held present while nitrate is cut low (an N:P that
     * favours the nitrogen fixers), and a first red spot. Release "doc" and "po4" (or reset) and switch the dead spot
     * off ("deadspot" -1) to let the sand clear.
     */
    fun cyano(w: WaterState): List<Command> = listOf(
        Command.SetParam("deadspot", DEAD_SPOT_CM_S),
        Command.RampParam("doc", CYANO_DOC, 0.0, hold = true),
        Command.RampParam("po4", maxOf(w.po4, CYANO_PO4), 0.0, hold = true),
        Command.SetParam("no3", minOf(w.no3, CYANO_NO3)),
        Command.SetParam("cyano", maxOf(w.cyano, CYANO_SEED)),
    )

    /**
     * Bubble algae (A2 stage 5: "mature rock, low flow pockets, moderate nutrients, pH <= 8.0"): a patch of cells on the
     * shaded, mature rock, phosphate held moderate and pH held at the low end of the safe band. They spread slowly over
     * weeks; crabs and the tang pick at the open patch but not the crevices. Pull them off by hand
     * ([Command.RemoveBubbleAlgae]); release "po4" and "ph" (or reset) to take the push away.
     */
    fun bubble(w: WaterState): List<Command> = listOf(
        Command.SetParam("microbes", 1.0),
        Command.RampParam("po4", maxOf(w.po4, BUBBLE_PO4), 0.0, hold = true),
        Command.RampParam("ph", minOf(w.ph, BUBBLE_PH), 0.0, hold = true),
        Command.SetParam("bubble", maxOf(w.bubble, BUBBLE_SEED)),
    )

    const val ULN_NO3 = 0.2
    const val ULN_PO4 = 0.005
    const val YOUNG_MICROBES = 0.15
    const val DINO_SEED = 0.04
    /** cm/s: under the 5 cm/s cyano prefers (A1), in the 0-3 cm/s dead-spot band of 05 §16. */
    const val DEAD_SPOT_CM_S = 2.0
    /** mg C/L: a full DOC index for the cyano ([AlgaeModel.DOC_CLEAN] + [AlgaeModel.DOC_SPAN]). */
    const val CYANO_DOC = 6.0
    const val CYANO_NO3 = 2.0
    const val CYANO_PO4 = 0.12
    const val CYANO_SEED = 0.03
    const val BUBBLE_SEED = 0.08
    /** Moderate phosphate (A2 stage 5: "moderate nutrients"). */
    const val BUBBLE_PO4 = 0.06
    /** pH at or under 8.0 favours bubble algae (hobby, A2); 7.95 is still inside the safe band for the corals. */
    const val BUBBLE_PH = 7.95
}
