package com.reeflive.core.sim

import com.reeflive.core.geom.Powerhead
import com.reeflive.core.geom.FlowField
import kotlin.math.pow

/**
 * Simulated water state (SIMULATION §2). Units: °C, SG, pH, dKH, ppm, mg/L, litres.
 * `nh3Total` is total ammonia nitrogen expressed as ppm NH3 (TAN); `detritus` is in ppm
 * TAN-equivalents of decomposable organics. Mutable inside [WaterSim]; snapshots use [copy].
 */
data class WaterState(
    var tempC: Double = 25.5,
    var sg: Double = 1.025,
    var ph: Double = 8.2,
    var alk: Double = 8.5,
    var ca: Double = 420.0,
    var mg: Double = 1300.0,
    var nh3Total: Double = 0.0,
    var no2: Double = 0.0,
    var no3: Double = 5.0,
    var po4: Double = 0.04,
    var doMgL: Double = 6.5,
    var k: Double = 400.0,
    var sr: Double = 8.0,
    var i: Double = 0.06,
    var copper: Double = 0.0,
    var volumeL: Double = TankDims.DISPLAY_LITERS,
    /** Hair-algae cover on rock (0..0.9). Algae covers are headline values of [AlgaeModel] (research 09 A6). */
    var algaeHair: Double = 0.06,
    var macro: Double = 0.3,
    /** Cyanobacteria (red slime) cover on sand and rock (0..1). */
    var cyano: Double = 0.0,
    /** Brown diatom film on the sand (0..1), coralline coverage on rock (0..0.9) (research 05 §14, 09 A6). */
    var diatoms: Double = 0.0,
    var coralline: Double = 0.4,
    /** Dinoflagellate film on sand and rock, green film (GSA) on the glass, bubble algae on rock (0..1; research 09 A1). */
    var dinos: Double = 0.0,
    var film: Double = 0.0,
    var bubble: Double = 0.0,
    /** Silicate, ppm SiO2 (RO water 0.05-0.1), and leaching from new sand/rock in ppm/day (half-life 14 d; 09 A2). */
    var silicate: Double = 0.1,
    var siLeach: Double = 0.0,
    /** Microbial diversity 0..1: rises over the first ~6 months, knocked back by copper; gates dinos (09 A2). */
    var microbes: Double = 1.0,
    var bacteriaAob: Double = 1.0,
    var bacteriaNob: Double = 1.0,
    var detritus: Double = 0.2,
    var lightPar: Double = 0.0,
    var flowOn: Boolean = true,
    var co2Index: Double = 1.0,
    var nh3Free: Double = 0.0,
    var gfoActive: Boolean = false,
    /**
     * Dissolved organic carbon, mg/L (research 01 §3.5: skimmer, carbon and sock remove organics before they
     * mineralise). Mature reef tanks sit around 1-3 (est.); fresh salt mix ~0.3.
     */
    var doc: Double = 1.5,
    /** Soft-coral allelochemicals in the water (Kenya terpenoids, zoa/paly toxins; research 03 D), index 0..; 1 = noticeable. */
    var allelo: Double = 0.0,
    /** Refugium macroalgae (Chaetomorpha) biomass index; 0 = no refugium (research 01 §3.5). */
    var chaeto: Double = 0.0,
    /** Dissolved hydrogen sulfide, mg/L: 0 normally; a pulse when deep anoxic sand is stirred (research 18 §3.3, [BacteriaModel]). */
    var h2s: Double = 0.0,
    /**
     * Holothurin (sea cucumber saponins, research 45 §4), index: 1 = a 25 cm tiger tail's whole store in 227 L, enough
     * to kill fish within hours; carbon takes it out fast, water changes dilute it.
     */
    var holothurin: Double = 0.0,
) {
    /** Salinity in ppt (ARCHITECTURE §4.1 table, linear in SG near seawater). */
    val ppt: Double get() = ChemFns.sgToPpt(sg)

    /** Generic parameter accessor by id (SetParam, HealthModel tolerance keys, HUD). */
    fun get(id: String): Double? = when (id.lowercase()) {
        "temp", "tempc" -> tempC
        "sg", "sal" -> sg
        "ph" -> ph
        "alk" -> alk
        "ca" -> ca
        "mg" -> mg
        "nh3", "nh3total", "tan" -> nh3Total
        "nh3free" -> nh3Free
        "no2" -> no2
        "no3" -> no3
        "po4" -> po4
        "do", "domgl" -> doMgL
        "k" -> k
        "sr" -> sr
        "i" -> i
        "copper", "cu" -> copper
        "algae", "algaehair" -> algaeHair
        "cyano" -> cyano
        "diatoms" -> diatoms
        "coralline" -> coralline
        "dinos" -> dinos
        "film" -> film
        "bubble" -> bubble
        "silicate", "si" -> silicate
        "light" -> lightPar
        "macro" -> macro
        "chaeto" -> chaeto
        "doc" -> doc
        "allelo" -> allelo
        "h2s" -> h2s
        "holothurin" -> holothurin
        else -> null
    }

    companion object {
        /** Ideal mid-points used when reality mode has no logged value (SIMULATION §1.3). */
        fun idealDefaults() = WaterState()

        /** Freshly mixed salt water used by water changes (SIMULATION §2.2). */
        val FRESH_MIX = WaterState(
            tempC = 25.0, sg = 1.025, ph = 8.2, alk = 8.5, ca = 430.0, mg = 1320.0, nh3Total = 0.0, no2 = 0.0, no3 = 0.0,
            po4 = 0.0, doMgL = 6.6, k = 400.0, sr = 8.0, i = 0.06, detritus = 0.0, doc = 0.3,
        )

        /**
         * The shelf's big water change: Red Sea Coral Pro mixed to SG 1.025 (maker's figures at 35 ppt: Ca 440-470,
         * alk 12.2-13 dKH, Mg 1340-1460, K 380-420; scaled to 1.025). A "rich" mix: a 25 % change lifts alk by about
         * 1 dKH, the classic Coral Pro swing. The bucket is heated to 25 °C, half a degree under the display.
         */
        val CORAL_PRO_MIX = FRESH_MIX.copy(alk = 12.0, ca = 445.0, mg = 1370.0, k = 395.0, sr = 8.5, i = 0.06)

        /**
         * The shelf's small water change: Instant Ocean Sea Salt at SG 1.025 (Ca ~400, alk ~10.5 dKH, Mg ~1280,
         * K ~380; hobby lab tests). Close to natural sea water: a 10 % change barely moves the minerals.
         */
        val INSTANT_OCEAN_MIX = FRESH_MIX.copy(alk = 10.5, ca = 400.0, mg = 1280.0, k = 380.0, sr = 7.8, i = 0.05)
    }
}

/** Small chemistry helpers. Track A's `chem` package has the canonical versions; these are kept
 *  internal to the sim so Track C does not depend on Track A's in-flight code. Same formulas. */
object ChemFns {
    /** Emerson: pKa = 0.09018 + 2729.92/T(K); f = 1/(1 + 10^(pKa − pH)). */
    fun freeNh3Fraction(ph: Double, tempC: Double): Double {
        val pKa = 0.09018 + 2729.92 / (tempC + 273.15)
        return 1.0 / (1.0 + 10.0.pow(pKa - ph))
    }

    fun freeNh3(tan: Double, ph: Double, tempC: Double) = tan * freeNh3Fraction(ph, tempC)

    private val satT = doubleArrayOf(20.0, 22.0, 24.0, 25.0, 26.0, 28.0, 30.0)
    private val satV = doubleArrayOf(7.4, 7.1, 6.8, 6.6, 6.5, 6.2, 5.9)

    /** DO saturation (mg/L), ARCHITECTURE §4.4 table at 35 ppt, +0.05 mg/L per ppt below 35. */
    fun oxygenSaturation(tempC: Double, ppt: Double): Double {
        val base = when {
            tempC <= satT.first() -> satV.first() + (satT.first() - tempC) * 0.2
            tempC >= satT.last() -> satV.last() - (tempC - satT.last()) * 0.2
            else -> {
                var i = 0
                while (tempC > satT[i + 1]) i++
                val t = (tempC - satT[i]) / (satT[i + 1] - satT[i])
                satV[i] + (satV[i + 1] - satV[i]) * t
            }
        }
        return (base + (35.0 - ppt).coerceAtLeast(0.0) * 0.05).coerceAtLeast(0.5)
    }

    /** SG→ppt via the ARCHITECTURE §4.1 table (1.020→26.7 … 1.030→39.8, linear). */
    fun sgToPpt(sg: Double): Double = 26.7 + (sg - 1.020) * (39.8 - 26.7) / 0.010

    fun massGrams(lengthCm: Double, massCoeff: Double) = massCoeff * lengthCm.pow(3)
}

/** Scheduled feeding (reality mode / healthy-tank tests): minute of day, food, grams, optional anchor. */
data class FeedTime(val minuteOfDay: Int, val kind: FoodKind, val grams: Double, val anchor: String? = null, val everyDays: Int = 1)

data class TankConfig(
    val lengthCm: Double = TankDims.LENGTH_CM,
    /** Front-to-back depth (z). */
    val widthCm: Double = TankDims.DEPTH_CM,
    val heightCm: Double = TankDims.HEIGHT_CM,
    val waterLevelCm: Double = TankDims.WATER_LEVEL_CM,
    val sandTopCm: Double = TankDims.SAND_TOP_CM,
    val systemLiters: Double = TankDims.DISPLAY_LITERS,
    val heaterSetC: Double = 25.5,
    val heaterWatts: Double = 200.0,
    val roomC: Double = 22.0,
    val evapLitersPerDay: Double = 3.0,
    val atoEnabled: Boolean = false,
    val powerheads: List<Powerhead> = FlowField.defaultJets(),
    /** Automatic feeding schedule (empty = only manual Feed commands). */
    val autoFeed: List<FeedTime> = DEFAULT_FEEDING,
    /** Predation can kill livestock (SIMULATION §4.5 eel: ON in sandbox, OFF in reality). */
    val predationEnabled: Boolean = false,
    /** Routine water change (assumed weekly 10 % until the app passes Tee's logged routine; 0 disables). */
    val waterChangeEveryDays: Int = 7,
    val waterChangeFraction: Double = 0.10,
    val seed: Long = 1L,
) {
    companion object {
        /** Tee-style routine: flake in the morning, mysis in the evening, eel target-fed every 3rd day,
         *  plankton for the feather duster every other day. */
        val DEFAULT_FEEDING = listOf(
            FeedTime(9 * 60 + 30, FoodKind.FLAKE, 0.35),
            FeedTime(17 * 60 + 30, FoodKind.MYSIS, 0.6),
            FeedTime(17 * 60 + 35, FoodKind.MYSIS, 1.2, anchor = "CAVE_EEL", everyDays = 3),
            FeedTime(17 * 60 + 40, FoodKind.PODS, 0.3, anchor = "DUSTER_TUBE", everyDays = 2),
        )
    }
}
