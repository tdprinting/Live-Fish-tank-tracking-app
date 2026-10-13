"""Generates core/src/main/resources/species/*.json from 02_fish_species / 04_more_species / 03 corals.
Run: python3 -I gen_species.py <repo>/core/src/main/resources/species
"""
import json, os, sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)

F = 0.08  # free NH3 fraction at pH 8.2 / 25 C

def band(optLo=None, optHi=None, tolLo=None, tolHi=None, lethalLo=None, lethalHi=None):
    d = dict(optLo=optLo, optHi=optHi, tolLo=tolLo, tolHi=tolHi, lethalLo=lethalLo, lethalHi=lethalHi)
    return {k: v for k, v in d.items() if v is not None}

def nh3(tol_total=0.1, lethal_total=0.25):
    return band(optHi=0.0, tolHi=round(tol_total * F, 4), lethalHi=round(lethal_total * F, 4))

def animal_tol(temp, sg, no3, ph=(8.1, 8.4, 7.9, 8.5, 7.8, 8.9), no2=(0.1, 0.25), nh=(0.1, 0.25), do=(6.0, 5.0, 3.0), extra=None):
    t = {
        "temp": band(*temp),
        "sg": band(*sg),
        "ph": band(*ph),
        "nh3Free": nh3(*nh),
        "no2": band(optHi=0.0, tolHi=no2[0], lethalHi=no2[1]),
        "no3": band(optHi=no3[0], tolHi=no3[1], lethalHi=no3[2]),
    }
    if do:
        t["do"] = band(optLo=do[0], tolLo=do[1], lethalLo=do[2])
    if extra:
        t.update(extra)
    return t

def coral_tol(temp=(24.5, 27, 23, 29, 20, 31), sg=(1.023, 1.027, 1.021, 1.028, 1.018, 1.031), ph=(8.0, 8.4, 7.9, 8.5, 7.7, 8.9), extra=None):
    t = {
        "temp": band(*temp),
        "sg": band(*sg),
        "ph": band(*ph),
        "alk": band(7.0, 11.0, 6.0, 12.0, 4.0, 14.0),
        "nh3Free": band(optHi=0.0, tolHi=0.02, lethalHi=0.08),
        "no3": band(optHi=25.0, tolHi=50.0, lethalHi=100.0),
        "po4": band(optHi=0.15, tolHi=0.25, lethalHi=0.5),
    }
    if extra:
        t.update(extra)
    return t

def loco(mode, cruise, burst, hz=3.0, turn=240, wl=0.9, a0=0.04, a1=0.09):
    return dict(mode=mode, cruiseBL=cruise, burstBL=burst, tailHzCruise=hz, turnRateDeg=turn, waveLen=wl, ampCruise=a0, ampBurst=a1)

def zone(y0, y1, ow, cov):
    return dict(yMin=y0, yMax=y1, openWater=ow, coverAffinity=cov)

def temper(ag, bo, cu, sk):
    return dict(aggression=ag, boldness=bo, curiosity=cu, skittishness=sk)

S = []

def add(**kw):
    S.append(kw)

FISH_PH = (8.1, 8.4, 7.9, 8.5, 7.8, 8.9)

# ---------------- Fish (02_fish_species.md, 04_more_species.md) ----------------
add(id="amphiprion_ocellaris", kind="FISH", common="Ocellaris clownfish", scientific="Amphiprion ocellaris", family="Pomacentridae",
    maxLengthCm=11, typicalAdultCm=8, growthCmPerYear=2, lifespanYears=8, depthRatio=0.45, massCoeff=0.022,
    locomotion=loco("LABRIFORM", 1.2, 6, 3.5, 300, 1.0, 0.05, 0.10), zone=zone(0.15, 0.6, 0.3, 0.7),
    activity="DIURNAL", territorialRadius=15, homeRadiusCm=20, temperament=temper(0.4, 0.6, 0.8, 0.3),
    diet=["FLAKE", "PELLET", "MYSIS", "BRINE", "ALGAE"], feedsPerDay=2,
    tolerance=animal_tol((24, 27, 22, 29, 18, 30), (1.023, 1.025, 1.021, 1.026, 1.018, 1.028), (20, 40, 60), FISH_PH, no2=(0.2, 0.5), nh=(0.1, 0.5)),
    diseaseSusceptibility={"ICH": 0.5, "VELVET": 0.5, "BROOKLYNELLA": 0.6, "BACTERIAL": 0.3}, hardiness=0.8, starvationDays=21,
    size={"lod0Tris": 4000}, model="models/clown.glb", colors={"primary": "#FF7A1A", "secondary": "#FFFFFF", "accent": "#111111"}, iridescence=0.12,
    brain="clown", minTankGallons=20, careLevel="Easy", reefSafe="Yes",
    dietSummary="Omnivore: pellets, flake, mysis, brine, a little algae.",
    temperamentSummary="Peaceful pair; the female guards the host.",
    careNotes=["Female is the larger fish; males change sex at ~4.4 cm.", "Will adopt the Kenya tree or GSP as a stand-in host."])

add(id="paracanthurus_hepatus", kind="FISH", common="Blue tang", scientific="Paracanthurus hepatus", family="Acanthuridae",
    maxLengthCm=31, typicalAdultCm=25, growthCmPerYear=5, lifespanYears=15, depthRatio=0.52, massCoeff=0.025,
    locomotion=loco("LABRIFORM", 1.5, 7, 4, 240, 0.9, 0.04, 0.09), zone=zone(0.25, 0.95, 0.8, 0.3),
    activity="DIURNAL", homeRadiusCm=60, temperament=temper(0.3, 0.6, 0.7, 0.5),
    diet=["ALGAE", "NORI", "MYSIS", "FLAKE", "PELLET"], feedsPerDay=4, grazer=True,
    tolerance={
        "temp": band(24, 27, 23, 28, 20, 31), "sg": band(1.023, 1.026, 1.021, 1.027, 1.016, 1.030),
        "ph": band(8.0, 8.4, 7.9, 8.5, 7.4, 8.9), "nh3Free": band(optHi=0.0, tolHi=0.01, lethalHi=0.05),
        "no2": band(optHi=0.0, tolHi=0.1, lethalHi=1.0), "no3": band(optHi=20, tolHi=30, lethalHi=80),
        "do": band(optLo=6.0, tolLo=5.0, lethalLo=2.5), "po4": band(optHi=0.08, tolHi=0.1, lethalHi=0.2),
    },
    diseaseSusceptibility={"ICH": 1.0, "VELVET": 0.5, "HLLE": 0.6, "BACTERIAL": 0.4}, hardiness=0.45, starvationDays=21,
    size={"lod0Tris": 4500}, model="models/tang.glb", colors={"primary": "#2B5FD9", "secondary": "#F4D01A", "accent": "#0B0F1A"}, iridescence=0.35,
    brain="tang", minTankGallons=119, careLevel="Moderate", reefSafe="Yes (may nip zoas)",
    dietSummary="Grazer: nori daily, mysis, pellets; vitamin-soaked food every third feed.",
    temperamentSummary="Active open-water swimmer, skittish; the most ich-prone fish in the tank.",
    careNotes=["Adult size 12 in — needs ~120 gal (450 L) or more long term.", "Above 28 °C it stops feeding and ich can appear within 48 h."])

add(id="sphaeramia_nematoptera", kind="FISH", common="Pajama cardinalfish", scientific="Sphaeramia nematoptera", family="Apogonidae",
    maxLengthCm=8, typicalAdultCm=7, growthCmPerYear=2, lifespanYears=4, depthRatio=0.55, massCoeff=0.024,
    locomotion=loco("LABRIFORM", 0.6, 4, 2, 180, 1.0, 0.03, 0.08), zone=zone(0.3, 0.6, 0.4, 0.6),
    activity="CREPUSCULAR", schooling=True, homeRadiusCm=25, temperament=temper(0.05, 0.3, 0.2, 0.7),
    diet=["MYSIS", "BRINE", "FLAKE", "PELLET"], feedsPerDay=2,
    tolerance=animal_tol((24, 28, 22, 29, 20, 30), (1.022, 1.026, 1.020, 1.027, 1.018, 1.030), (20, 30, 50), (8.1, 8.4, 7.9, 8.4, 7.8, 8.9)),
    diseaseSusceptibility={"ICH": 0.3, "VELVET": 0.3, "BACTERIAL": 0.3}, hardiness=0.85, starvationDays=21,
    size={"lod0Tris": 3500}, model="models/cardinal.glb", colors={"primary": "#E8C840", "secondary": "#7A1F1F", "accent": "#F2E6C0"}, iridescence=0.15,
    brain="cardinal", minTankGallons=30, careLevel="Easy", reefSafe="Yes",
    dietSummary="Meaty: mysis, amphipods, brine; flake or pellets occasionally.",
    temperamentSummary="Shy hoverer, most active at dawn and dusk.",
    careNotes=["Male mouthbroods the eggs.", "Easy prey for the eel at night."])

add(id="labroides_dimidiatus", kind="FISH", common="Bluestreak cleaner wrasse", scientific="Labroides dimidiatus", family="Labridae",
    maxLengthCm=11, typicalAdultCm=10, growthCmPerYear=3, lifespanYears=3, depthRatio=0.22, massCoeff=0.015,
    locomotion=loco("LABRIFORM", 1.6, 6, 4, 320, 0.8, 0.05, 0.1), zone=zone(0.1, 0.5, 0.3, 0.6),
    activity="DIURNAL", homeRadiusCm=35, temperament=temper(0.1, 0.8, 0.8, 0.3),
    diet=["PARASITES", "MYSIS", "BRINE", "FLAKE"], feedsPerDay=3,
    tolerance=animal_tol((22, 26, 21, 27, 18, 28), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (10, 20, 40), (8.1, 8.4, 7.9, 8.4, 7.8, 8.9)),
    diseaseSusceptibility={"ICH": 0.3, "BACTERIAL": 0.4}, hardiness=0.4, starvationDays=35,
    size={"lod0Tris": 3500}, model="models/wrasse.glb", colors={"primary": "#3A7BD5", "secondary": "#111111", "accent": "#F2E8C8"}, iridescence=0.5,
    brain="wrasse", minTankGallons=60, careLevel="Expert", reefSafe="Yes",
    dietSummary="Cleans parasites; must learn to take mysis and flake or it starves.",
    temperamentSummary="Busy, bold, runs a cleaning station under the arch.",
    careNotes=["Starvation is the main way they die in home tanks (2–8 weeks).", "Watch body condition: a thin belly is the first warning."])

add(id="echidna_nebulosa", kind="FISH", common="Snowflake moray eel", scientific="Echidna nebulosa", family="Muraenidae",
    maxLengthCm=80, typicalAdultCm=60, growthCmPerYear=8, lifespanYears=12, depthRatio=0.08, massCoeff=0.006,
    locomotion=loco("ANGUILLIFORM", 0.5, 2.5, 1.0, 180, 0.6, 0.06, 0.1), zone=zone(0.0, 0.35, 0.1, 0.9),
    activity="NOCTURNAL", homeRadiusCm=40, temperament=temper(0.5, 0.5, 0.4, 0.3),
    diet=["MYSIS", "PELLET", "SHRIMP", "CRAB", "FISH"], feedsPerDay=0.5,
    tolerance=animal_tol((22, 26, 20, 28, 18, 30), (1.022, 1.024, 1.021, 1.026, 1.020, 1.027), (20, 40, 60), (8.1, 8.4, 7.9, 8.4, 7.8, 8.9)),
    diseaseSusceptibility={"BACTERIAL": 0.3}, hardiness=0.9, starvationDays=60,
    size={"lod0Tris": 6000}, model="models/eel.glb", colors={"primary": "#F2EEE0", "secondary": "#1A1A1A", "accent": "#F2D21A"}, iridescence=0.05,
    brain="eel", minTankGallons=75, careLevel="Moderate", reefSafe="With caution",
    dietSummary="Carnivore: shrimp, crab, fish, frozen meaty foods two to three times a week.",
    temperamentSummary="Den-dweller; hunts the floor at night. Escape artist — keep a tight lid.",
    careNotes=["Adult size 24–32 in — needs 75 gal+ (130 gal suggested).", "Main predator here: shrimp, hermits, cardinals and blennies are at risk."])

add(id="cirrhitichthys_falco", kind="FISH", common="Falco hawkfish", scientific="Cirrhitichthys falco", family="Cirrhitidae",
    maxLengthCm=7, typicalAdultCm=6.5, growthCmPerYear=2, lifespanYears=4, depthRatio=0.3, massCoeff=0.02,
    locomotion=loco("PERCHING", 0.8, 3, 3, 240, 1.0, 0.03, 0.09), zone=zone(0.1, 0.7, 0.1, 0.8),
    activity="DIURNAL", territorialRadius=10, homeRadiusCm=40, temperament=temper(0.45, 0.7, 0.6, 0.3),
    diet=["MYSIS", "KRILL", "BRINE", "PELLET"], feedsPerDay=2,
    tolerance=animal_tol((24, 26, 23, 27, 22, 28), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 50), (8.1, 8.4, 7.9, 8.4, 7.8, 8.9)),
    diseaseSusceptibility={"ICH": 0.3, "VELVET": 0.3, "BACTERIAL": 0.3}, hardiness=0.65, starvationDays=28,
    size={"lod0Tris": 3500}, model="models/hawkfish.glb", colors={"primary": "#F4E6E0", "secondary": "#B03A2E", "accent": "#7A1F1F"}, iridescence=0.1,
    brain="hawkfish", minTankGallons=30, careLevel="Easy", reefSafe="Yes (risk to small shrimp)",
    dietSummary="Carnivore: mysis, krill, brine, pellets.",
    temperamentSummary="Perch predator; hops between rock tops and pounces.",
    careNotes=["May eat small shrimp or hermits.", "Competes with the blennies for rock perches."])

add(id="salarias_fasciatus", kind="FISH", common="Lawnmower blenny", scientific="Salarias fasciatus", family="Blenniidae",
    maxLengthCm=13, typicalAdultCm=11, growthCmPerYear=3, lifespanYears=4, depthRatio=0.2, massCoeff=0.012,
    locomotion=loco("PERCHING", 0.7, 4, 2.5, 260, 0.8, 0.04, 0.1), zone=zone(0.0, 0.45, 0.05, 0.9),
    activity="DIURNAL", territorialRadius=20, homeRadiusCm=30, temperament=temper(0.5, 0.5, 0.5, 0.6),
    diet=["ALGAE", "NORI", "SPIRULINA", "PELLET"], feedsPerDay=6, grazer=True,
    tolerance=animal_tol((24, 28, 22, 30, 20, 31), (1.020, 1.025, 1.019, 1.026, 1.018, 1.028), (10, 20, 40), (8.1, 8.4, 7.9, 8.4, 7.8, 8.9), no2=(0.2, 0.5)),
    diseaseSusceptibility={"ICH": 0.2, "BACTERIAL": 0.3}, hardiness=0.75, starvationDays=35,
    size={"lod0Tris": 3500}, model="models/lawnmower_blenny.glb", colors={"primary": "#5E6B3F", "secondary": "#3B3A22", "accent": "#3A8FD8"}, iridescence=0.15,
    brain="blenny", minTankGallons=32, careLevel="Moderate", reefSafe="Yes", onePerTank=True,
    dietSummary="Grazer: algae and biofilm; nori, spirulina, algae pellets if it accepts them.",
    temperamentSummary="Territorial to other blennies; rasps rock all day, darts into holes.",
    careNotes=["Starvation is the leading cause of death — keep algae or nori available.", "Fights the tailspot blenny over rock territory."])

add(id="ecsenius_stigmatura", kind="FISH", common="Tailspot blenny", scientific="Ecsenius stigmatura", family="Blenniidae",
    maxLengthCm=6.4, typicalAdultCm=5.5, growthCmPerYear=2, lifespanYears=4, depthRatio=0.18, massCoeff=0.012,
    locomotion=loco("PERCHING", 0.7, 4, 2.5, 260, 0.8, 0.04, 0.1), zone=zone(0.0, 0.5, 0.05, 0.9),
    activity="DIURNAL", territorialRadius=12, homeRadiusCm=25, temperament=temper(0.35, 0.5, 0.6, 0.6),
    diet=["ALGAE", "SPIRULINA", "NORI", "PELLET"], feedsPerDay=4, grazer=True,
    tolerance=animal_tol((25.5, 27.8, 24, 29, 20, 30), (1.023, 1.025, 1.020, 1.026, 1.018, 1.028), (10, 20, 40), (8.1, 8.4, 7.9, 8.4, 7.8, 8.9), no2=(0.2, 0.5)),
    diseaseSusceptibility={"ICH": 0.2, "BACTERIAL": 0.3}, hardiness=0.7, starvationDays=30,
    size={"lod0Tris": 3000}, model="models/tailspot_blenny.glb", colors={"primary": "#6B5232", "secondary": "#1A1A1A", "accent": "#2E6FD8"}, iridescence=0.2,
    brain="blenny", minTankGallons=10, careLevel="Easy", reefSafe="Yes (nips corals when hungry)", onePerTank=True,
    dietSummary="Herbivore: hair algae, microalgae; spirulina flake, nori, herbivore pellets in small meals.",
    temperamentSummary="Docile to other fish, territorial to blennies. A known jumper.",
    careNotes=["Needs a tight lid — jumps when startled.", "Coloration not confirmed from a photo yet."])

add(id="pseudochromis_indigo", kind="FISH", common="Indigo dottyback", scientific="Pseudochromis fridmani × P. sankeyi", family="Pseudochromidae",
    maxLengthCm=7.6, typicalAdultCm=6.5, growthCmPerYear=2, lifespanYears=5, depthRatio=0.32, massCoeff=0.018,
    locomotion=loco("LABRIFORM", 0.8, 4, 3, 300, 0.9, 0.04, 0.1), zone=zone(0.1, 0.5, 0.15, 0.8),
    activity="DIURNAL", territorialRadius=15, homeRadiusCm=25, temperament=temper(0.5, 0.6, 0.5, 0.4),
    diet=["MYSIS", "BRINE", "PELLET"], feedsPerDay=2,
    tolerance=animal_tol((24, 27, 22, 29, 20, 30), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 50), (8.1, 8.4, 7.9, 8.4, 7.8, 8.9)),
    diseaseSusceptibility={"ICH": 0.2, "VELVET": 0.3, "BACTERIAL": 0.3}, hardiness=0.85, starvationDays=28,
    size={"lod0Tris": 3200}, model="models/dottyback.glb", colors={"primary": "#3B3FA8", "secondary": "#0E0E14", "accent": "#5B63C8"}, iridescence=0.25,
    brain="dottyback", minTankGallons=30, careLevel="Easy", reefSafe="Yes", onePerTank=True,
    dietSummary="Carnivore: mysis, brine, meaty foods; pellets over time.",
    temperamentSummary="Semi-aggressive ledge holder; mild to shrimp, may chase the blennies.",
    careNotes=["Captive-bred hybrid (ORA 'Indigo').", "Keep one per tank."])

# ---------------- Invertebrates ----------------
INV_PH = (8.1, 8.4, 7.9, 8.4, 7.8, 8.9)
COPPER = {"copper": band(optHi=0.0, tolHi=0.0, lethalHi=0.1)}

add(id="stenopus_hispidus", kind="INVERT", common="Coral banded shrimp", scientific="Stenopus hispidus", family="Stenopodidae",
    maxLengthCm=6.3, typicalAdultCm=5.5, growthCmPerYear=1.5, lifespanYears=3, depthRatio=0.25, massCoeff=0.01,
    locomotion=loco("WALKING", 0.15, 8, 0, 120, 1, 0, 0), zone=zone(0.05, 0.5, 0.0, 0.95),
    activity="NOCTURNAL", territorialRadius=10, homeRadiusCm=20, temperament=temper(0.4, 0.5, 0.4, 0.5),
    diet=["MYSIS", "FLAKE", "MEATY", "DETRITUS"], feedsPerDay=1,
    tolerance=animal_tol((22, 26, 21, 27, 18, 28), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (10, 20, 40), INV_PH, do=None,
                         extra=dict(COPPER, alk=band(8.0, 12.0, 7.0, 12.5, 5.0, 14.0))),
    hardiness=0.75, starvationDays=40, size={"lod0Tris": 3000}, model="models/shrimp_banded.glb",
    colors={"primary": "#F4F0E8", "secondary": "#C8282C"}, brain="shrimp_banded", minTankGallons=30, careLevel="Easy",
    reefSafe="Yes", copperSensitive=True, onePerTank=True,
    dietSummary="Scavenger: mysis, flake and meaty scraps; picks settled food at night.",
    temperamentSummary="Hangs under ledges; can bully other shrimp.",
    careNotes=["Molts every few weeks; stable Ca/Mg and iodine help.", "Intolerant of high nitrate and copper."])

add(id="lysmata_amboinensis", kind="INVERT", common="Skunk cleaner shrimp", scientific="Lysmata amboinensis", family="Lysmatidae",
    maxLengthCm=8, typicalAdultCm=6.5, growthCmPerYear=2, lifespanYears=2.5, depthRatio=0.2, massCoeff=0.01,
    locomotion=loco("WALKING", 0.15, 8, 0, 120, 1, 0, 0), zone=zone(0.05, 0.6, 0.05, 0.85),
    activity="DIURNAL", homeRadiusCm=20, temperament=temper(0.05, 0.7, 0.8, 0.4),
    diet=["MYSIS", "BRINE", "PELLET", "FLAKE", "PARASITES"], feedsPerDay=1,
    tolerance=animal_tol((24, 27, 22, 28, 18, 30), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 40), INV_PH, do=None, extra=COPPER),
    hardiness=0.7, starvationDays=30, size={"lod0Tris": 3000}, model="models/shrimp_skunk.glb",
    colors={"primary": "#E9D8C6", "secondary": "#C62B2B", "accent": "#F4F4F0"}, brain="shrimp_cleaner", minTankGallons=20,
    careLevel="Easy", reefSafe="Yes", copperSensitive=True,
    dietSummary="Opportunistic omnivore; once-daily mysis or pellets plus cleaning.",
    temperamentSummary="Peaceful cleaner; runs a station and will clean a still hand.",
    careNotes=["Molts every 3–6 weeks and hides for 1–2 days afterwards.", "Hawkfish and eel are real threats."])

add(id="clibanarius_tricolor", kind="INVERT", common="Blue-leg hermit crab", scientific="Clibanarius tricolor", family="Diogenidae",
    maxLengthCm=2, typicalAdultCm=1.8, lifespanYears=2, depthRatio=0.6, massCoeff=0.02,
    locomotion=loco("WALKING", 0.15, 0.6, 0, 120, 1, 0, 0), zone=zone(0.0, 0.4, 0.0, 0.7),
    activity="NOCTURNAL", homeRadiusCm=30, temperament=temper(0.3, 0.5, 0.5, 0.6),
    diet=["DETRITUS", "ALGAE", "CYANO", "FLAKE"], feedsPerDay=2, grazer=True,
    tolerance=animal_tol((24, 27, 22, 28, 18, 30), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 50), INV_PH, do=None, extra=COPPER),
    hardiness=0.85, starvationDays=40, model="models/hermit_blueleg.glb",
    colors={"primary": "#3A6FD0", "secondary": "#C8282C", "accent": "#E0A07A"}, brain="hermit", minTankGallons=5,
    careLevel="Easy", reefSafe="Yes", copperSensitive=True,
    dietSummary="Detritivore: algae, cyano, leftovers; sifts sand.",
    temperamentSummary="Clean-up crew; shell fights with other hermits; may attack snails.",
    careNotes=["Keep spare shells so they leave the snails alone."])

add(id="paguristes_cadenati", kind="INVERT", common="Scarlet hermit crab", scientific="Paguristes cadenati", family="Diogenidae",
    maxLengthCm=3.5, typicalAdultCm=3, lifespanYears=3, depthRatio=0.6, massCoeff=0.02,
    locomotion=loco("WALKING", 0.15, 0.6, 0, 120, 1, 0, 0), zone=zone(0.0, 0.5, 0.0, 0.7),
    activity="DIURNAL", homeRadiusCm=35, temperament=temper(0.15, 0.5, 0.5, 0.5),
    diet=["ALGAE", "DETRITUS", "FLAKE", "MEATY"], feedsPerDay=2, grazer=True,
    tolerance=animal_tol((24, 27, 22, 28, 18, 30), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 50), INV_PH, do=None, extra=COPPER),
    hardiness=0.85, starvationDays=40, model="models/hermit_scarlet.glb",
    colors={"primary": "#D32F2F", "secondary": "#F2C230"}, brain="hermit", minTankGallons=5,
    careLevel="Easy", reefSafe="Yes", copperSensitive=True,
    dietSummary="Eats almost anything; known for hair and slime algae.",
    temperamentSummary="Hardy maintenance crab; mild about taking shells.",
    careNotes=["Provide larger spare shells as it grows."])

add(id="ophiocoma_sp", kind="INVERT", common="Black brittle star", scientific="Ophiocoma sp. (likely O. echinata)", family="Ophiocomidae",
    maxLengthCm=30, typicalAdultCm=20, lifespanYears=3, depthRatio=0.1, massCoeff=0.004,
    locomotion=loco("WALKING", 0.04, 0.3, 0, 90, 1, 0, 0), zone=zone(0.0, 0.5, 0.0, 0.95),
    activity="NOCTURNAL", homeRadiusCm=30, temperament=temper(0.0, 0.2, 0.3, 0.7),
    diet=["DETRITUS", "MYSIS", "PLANKTON"], feedsPerDay=1,
    tolerance=animal_tol((24, 27, 22, 28, 18, 30), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 50), INV_PH, do=None, extra=COPPER),
    hardiness=0.8, starvationDays=60, model="models/brittle_star.glb",
    colors={"primary": "#1A1A1A", "secondary": "#3B3B3B"}, brain="brittle_star", minTankGallons=30,
    careLevel="Easy", reefSafe="Yes", copperSensitive=True,
    dietSummary="Scavenger: detritus and small particles passed to the mouth by the arms.",
    temperamentSummary="Hides under rock by day; arms appear at feeding time and at night.",
    careNotes=["Species not confirmed (black-disc Ophiocoma).", "Lost arms regrow."])

add(id="sabellastarte_sp", kind="INVERT", common="Brown feather duster", scientific="Sabellastarte sp. (S. indica type)", family="Sabellidae",
    maxLengthCm=25, typicalAdultCm=15, lifespanYears=4, depthRatio=0.1, massCoeff=0.004,
    locomotion=loco("SESSILE", 0, 0, 0, 0, 1, 0, 0), zone=zone(0.0, 0.3, 0.0, 0.5),
    activity="DIURNAL", homeRadiusCm=0, temperament=temper(0.0, 0.3, 0.0, 0.8),
    diet=["PLANKTON", "PHYTOPLANKTON"], feedsPerDay=0.5,
    tolerance=animal_tol((22, 28, 21, 29, 18, 30), (1.024, 1.026, 1.023, 1.027, 1.020, 1.030), (20, 30, 50), (8.1, 8.2, 8.0, 8.3, 7.9, 8.8), do=None,
                         extra=dict(COPPER, ca=band(380, 440, 370, 460, 350, 520), alk=band(6.5, 9.5, 6.0, 11.0, 5.0, 13.0))),
    hardiness=0.55, starvationDays=45, model="models/feather_duster.glb",
    colors={"primary": "#8B3A24", "secondary": "#F4F0E8"}, brain="feather_duster", minTankGallons=10,
    careLevel="Moderate", reefSafe="Yes", copperSensitive=True,
    coral=dict(lightLo=0.0, lightHi=1.0, flow="MODERATE", growth="Slow", aggression="None", fluorescence=0.0, photosynthetic=False),
    dietSummary="Filter feeder: target-feed plankton under the crown every other day.",
    temperamentSummary="Sessile; crown snaps shut when disturbed or fed from above.",
    careNotes=["Source suggests alk near 2.5 meq/L (7 dKH); typical reef alk is tolerated.", "A dropped crown regrows in 1–2 months."])

add(id="nassarius_sp", kind="INVERT", common="Nassarius snail", scientific="Nassarius vibex / N. distortus", family="Nassariidae",
    maxLengthCm=2, typicalAdultCm=1.5, lifespanYears=1.5, depthRatio=0.6, massCoeff=0.03,
    locomotion=loco("CRAWLING", 0.05, 1.0, 0, 90, 1, 0, 0), zone=zone(0.0, 0.05, 0.0, 0.5),
    activity="NOCTURNAL", homeRadiusCm=60, temperament=temper(0.0, 0.3, 0.6, 0.4),
    diet=["DETRITUS", "MEATY", "FLAKE"], feedsPerDay=1,
    tolerance=animal_tol((24, 27, 22, 28, 18, 30), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 50), INV_PH, do=None, extra=COPPER),
    hardiness=0.85, starvationDays=21, model="models/nassarius.glb",
    colors={"primary": "#C9B79A", "secondary": "#3D2F22"}, brain="nassarius", minTankGallons=5,
    careLevel="Easy", reefSafe="Yes", copperSensitive=True,
    dietSummary="Carnivorous scavenger: leftovers and carrion; does not eat algae.",
    temperamentSummary="Lives buried with only the siphon showing; erupts when food lands.",
    careNotes=["Needs 2–3 cm of sand.", "Hermits may prey on small ones."])

add(id="lithopoma_tectum", kind="INVERT", common="Astrea snail", scientific="Lithopoma tectum", family="Turbinidae",
    maxLengthCm=6, typicalAdultCm=3.5, lifespanYears=4, depthRatio=0.8, massCoeff=0.04,
    locomotion=loco("CRAWLING", 0.02, 0.05, 0, 60, 1, 0, 0), zone=zone(0.0, 1.0, 0.0, 0.3),
    activity="DIURNAL", homeRadiusCm=60, temperament=temper(0.0, 0.5, 0.1, 0.3),
    diet=["ALGAE", "DIATOMS", "CYANO"], feedsPerDay=6, grazer=True,
    tolerance=animal_tol((22, 24, 22, 27, 18, 28), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 50), INV_PH, do=None),
    hardiness=0.85, starvationDays=21, model="models/astrea.glb",
    colors={"primary": "#C4522B", "secondary": "#F2EFE6", "accent": "#6E6B2E"}, brain="snail_grazer", minTankGallons=5,
    careLevel="Easy", reefSafe="Yes", copperSensitive=True,
    dietSummary="Herbivore: film algae, diatoms, cyano, hair algae.",
    temperamentSummary="Glass and rock grazer; cannot right itself easily when flipped.",
    careNotes=["Flip upside-down snails back over — hermits eat stranded ones.", "Prefers the cooler side (22–24 °C optimal)."])

add(id="asterina_sp", kind="INVERT", common="Asterina starfish", scientific="Asterina / Aquilonastra sp.", family="Asterinidae",
    maxLengthCm=2, typicalAdultCm=1.2, lifespanYears=1.5, depthRatio=0.2, massCoeff=0.01,
    locomotion=loco("CRAWLING", 0.03, 0.05, 0, 60, 1, 0, 0), zone=zone(0.0, 0.8, 0.0, 0.8),
    activity="NOCTURNAL", homeRadiusCm=30, temperament=temper(0.0, 0.3, 0.1, 0.3),
    diet=["ALGAE", "DETRITUS"], feedsPerDay=4, grazer=True,
    tolerance=animal_tol((24, 27, 22, 28, 18, 30), (1.023, 1.025, 1.021, 1.026, 1.020, 1.028), (20, 30, 50), INV_PH, do=None),
    hardiness=0.95, starvationDays=60, model="models/asterina.glb",
    colors={"primary": "#A8A59A", "secondary": "#C8C2B2"}, brain="asterina", minTankGallons=1,
    careLevel="Easy", reefSafe="Usually (some eat coral)",
    dietSummary="Not well documented; likely film algae and detritus.",
    temperamentSummary="Hitchhiker that multiplies by splitting.",
    careNotes=["Population can boom — remove some if jagged white marks appear at coral bases."])

# ---------------- Corals and algae (03_corals_rock_light.md, SIMULATION §3.6, 04 §12) ----------------
def coral(**kw):
    kw.setdefault("activity", "DIURNAL")
    kw.setdefault("locomotion", loco("SESSILE", 0, 0, 0, 0, 1, 0, 0))
    kw.setdefault("diet", ["LIGHT"])
    kw.setdefault("feedsPerDay", 0)
    kw.setdefault("minTankGallons", None)
    kw.setdefault("hardiness", 0.8)
    kw.setdefault("starvationDays", 9999)
    add(**kw)

coral(id="capnella_sp", kind="CORAL", common="Kenya tree coral", scientific="Capnella sp.", family="Nephtheidae",
      maxLengthCm=30, typicalAdultCm=15, growthCmPerYear=10, lifespanYears=20,
      tolerance=coral_tol(temp=(25, 27, 24, 29, 20, 30.5)), model="models/coral_kenya.glb",
      colors={"primary": "#E8DCC4", "secondary": "#8A6A4F", "accent": "#6FA57A"}, brain="coral_soft",
      coral=dict(lightLo=0.3, lightHi=0.7, flow="MODERATE", growth="Fast", aggression="Allelopathy; keep downstream", fluorescence=0.1),
      careLevel="Easy", reefSafe="Yes", dietSummary="Photosynthetic; absorbs dissolved nutrients.",
      temperamentSummary="Fast grower; drops branches to propagate.",
      careNotes=["Too much direct flow closes the polyps.", "Partly closes at night."])

coral(id="briareum_sp", kind="CORAL", common="Green star polyps", scientific="Briareum sp. (Pachyclavularia)", family="Briareidae",
      maxLengthCm=60, typicalAdultCm=15, growthCmPerYear=20, lifespanYears=20,
      tolerance=coral_tol(), model="models/coral_gsp.glb",
      colors={"primary": "#7DFF4F", "secondary": "#7A4E9C"}, brain="coral_polyp",
      coral=dict(lightLo=0.3, lightHi=0.9, flow="HIGH", growth="Very fast", aggression="Overgrows neighbours", fluorescence=0.8),
      careLevel="Easy", reefSafe="Yes", dietSummary="Photosynthetic.",
      temperamentSummary="Encrusting mat that advances several cm per month.",
      careNotes=["Keep a 10 cm gap to other corals.", "Retracts in stagnant water."])

coral(id="zoanthus_sp", kind="CORAL", common="Zoanthids", scientific="Zoanthus sp.", family="Zoanthidae",
      maxLengthCm=30, typicalAdultCm=10, growthCmPerYear=8, lifespanYears=20,
      tolerance=coral_tol(), model="models/coral_zoa.glb",
      colors={"primary": "#E0792A", "secondary": "#5FBF5A", "accent": "#6B4A2E"}, brain="coral_polyp",
      coral=dict(lightLo=0.3, lightHi=0.8, flow="MODERATE", growth="Moderate", aggression="Palytoxin; may sting neighbours", fluorescence=0.6),
      careLevel="Easy", reefSafe="Yes", dietSummary="Photosynthetic; takes fine particulate food.",
      temperamentSummary="Polyps open in light, close at night and under stress.",
      careNotes=["Handle with gloves and eye protection (palytoxin)."])

coral(id="clavularia_sp", kind="CORAL", common="Clove polyps", scientific="Clavularia sp.", family="Clavulariidae",
      maxLengthCm=30, typicalAdultCm=8, growthCmPerYear=6, lifespanYears=20,
      tolerance=coral_tol(), model="models/coral_clove.glb",
      colors={"primary": "#F0B0C8", "secondary": "#3A6FD8"}, brain="coral_polyp",
      coral=dict(lightLo=0.2, lightHi=0.7, flow="HIGH", growth="Slow", aggression="None", fluorescence=0.2),
      careLevel="Easy", reefSafe="Yes", dietSummary="Photosynthetic.",
      temperamentSummary="Wispy tufts that sway in strong flow.",
      careNotes=["Detritus settling at the base causes die-back."])

coral(id="discosoma_sp", kind="CORAL", common="Green mushroom", scientific="Discosoma sp.", family="Discosomatidae",
      maxLengthCm=8, typicalAdultCm=5, growthCmPerYear=2, lifespanYears=20,
      tolerance=coral_tol(), model="models/coral_mushroom.glb",
      colors={"primary": "#3FA7D6", "secondary": "#2D7F8F"}, brain="coral_soft",
      coral=dict(lightLo=0.2, lightHi=0.6, flow="LOW", growth="Moderate", aggression="Mild", fluorescence=0.7),
      careLevel="Easy", reefSafe="Yes", dietSummary="Photosynthetic.",
      temperamentSummary="Disc inflates by day and closes at night.",
      careNotes=["Bleaches under very strong light (over ~90% intensity)."])

coral(id="rhodactis_sp", kind="CORAL", common="Hairy green mushroom", scientific="Rhodactis sp.", family="Discosomatidae",
      maxLengthCm=10, typicalAdultCm=6, growthCmPerYear=2, lifespanYears=20,
      tolerance=coral_tol(), model="models/mushroom_hairy.glb",
      colors={"primary": "#5FBF4A", "secondary": "#2F7A3A", "accent": "#B8E07A"}, brain="coral_soft",
      coral=dict(lightLo=0.2, lightHi=0.55, flow="LOW", growth="Moderate", aggression="Moderate", fluorescence=0.6),
      careLevel="Easy", reefSafe="Yes", dietSummary="Photosynthetic; also traps small particles and mysis on the disc.",
      temperamentSummary="Flat green discs covered in short branched tentacles; stays open after lights-out. Hosts the clownfish in this tank.",
      careNotes=["Low to moderate light and gentle flow; discs shrink and detach under strong light or flow.",
                 "Can sting or engulf neighbours that touch the disc; give it a few cm of space.",
                 "Captive clownfish often adopt hairy mushrooms as a substitute host; the pair rubs and nestles low over the discs."])

coral(id="tubipora_musica", kind="CORAL", common="Organ pipe coral", scientific="Tubipora musica", family="Tubiporidae",
      maxLengthCm=30, typicalAdultCm=15, growthCmPerYear=2, lifespanYears=30,
      tolerance=coral_tol(temp=(25, 28, 24, 29, 20, 30), sg=(1.024, 1.026, 1.023, 1.027, 1.020, 1.030), ph=(8.1, 8.4, 8.0, 8.4, 7.9, 8.8)),
      model="models/coral_organpipe.glb", colors={"primary": "#9A2A2A", "secondary": "#B8C97A", "accent": "#E8E0C8"}, brain="coral_polyp",
      coral=dict(lightLo=0.5, lightHi=0.9, flow="MODERATE", growth="Slow", aggression="None", fluorescence=0.2),
      hardiness=0.55, careLevel="Moderate", reefSafe="Yes", dietSummary="Photosynthetic; baby brine and rotifers help.",
      temperamentSummary="Red calcite tubes topped with feathery 8-tentacle polyps.",
      careNotes=["Needs bright light and moderate flow that does not batter it.", "Previously logged as aiptasia at the Kenya base."])

coral(id="gracilaria_sp", kind="ALGAE", common="Red macroalgae", scientific="Gracilaria sp.", family="Gracilariaceae",
      maxLengthCm=30, typicalAdultCm=15, growthCmPerYear=30, lifespanYears=5,
      tolerance=coral_tol(extra={"no3": band(optHi=40.0, tolHi=80.0, lethalHi=200.0), "po4": band(optHi=0.3, tolHi=0.6, lethalHi=1.5)}),
      model="models/macro_red.glb", colors={"primary": "#B5294F", "secondary": "#7A1F3A"}, brain="macroalgae",
      coral=dict(lightLo=0.2, lightHi=0.6, flow="MODERATE", growth="Fast", aggression="None", fluorescence=0.3),
      careLevel="Easy", reefSafe="Yes", dietSummary="Photosynthetic; uses nitrate and phosphate.",
      temperamentSummary="Branching red fronds that sway in flow; tang snack.",
      careNotes=["Can be damaged by intense light."])

# ---------------- write ----------------
ids = []
for s in S:
    s = {k: v for k, v in s.items() if v is not None}
    ids.append(s["id"])
    with open(os.path.join(out, s["id"] + ".json"), "w") as f:
        json.dump(s, f, indent=2, ensure_ascii=False)
        f.write("\n")
with open(os.path.join(out, "index.json"), "w") as f:
    json.dump({"species": ids}, f, indent=2)
    f.write("\n")

# ---------------- interactions (02 §9, 04 §13–14) ----------------
CL, TA, CA, WR, EE, HF = "amphiprion_ocellaris", "paracanthurus_hepatus", "sphaeramia_nematoptera", "labroides_dimidiatus", "echidna_nebulosa", "cirrhitichthys_falco"
LB, TB, DB = "salarias_fasciatus", "ecsenius_stigmatura", "pseudochromis_indigo"
CBS, SK, BH, SH, BS, FD, NA, AS, AT = "stenopus_hispidus", "lysmata_amboinensis", "clibanarius_tricolor", "paguristes_cadenati", "ophiocoma_sp", "sabellastarte_sp", "nassarius_sp", "lithopoma_tectum", "asterina_sp"
OP, ZO, KE, GSP = "tubipora_musica", "zoanthus_sp", "capnella_sp", "briareum_sp"

I = []
def r(a, b, risk, note, threat=None):
    d = {"a": a, "b": b, "risk": risk, "note": note}
    if threat:
        d["threat"] = threat
    I.append(d)

# 02 §9
r(CL, WR, "L", "Clowns visit the cleaning station")
r(CL, EE, "M", "Eel may strike clowns near the floor at night", EE)
r(CL, HF, "L", "Hawkfish rarely bothers clowns")
r(TA, WR, "L", "Tang visits the cleaning station")
r(TA, EE, "L", "Tang is too large and fast for the eel")
r(TA, HF, "L", "No real conflict")
r(CA, EE, "H", "Eel hunts cardinals at night", EE)
r(CA, HF, "M", "Hawkfish may eat small cardinals", HF)
r(CA, CBS, "L", "Banded shrimp can pick at a resting cardinal")
r(WR, EE, "H", "Eel is a long-term threat to the wrasse", EE)
r(WR, HF, "M", "Hawkfish chases the wrasse near its perch", HF)
r(WR, CBS, "L", "Wrasse may pick at shrimp antennae")
r(EE, LB, "MH", "Eel may take a blenny from its hole", EE)
r(EE, TB, "MH", "Eel may take a blenny from its hole", EE)
r(EE, CBS, "H", "Eels eat shrimp", EE)
r(EE, HF, "L", "Little overlap")
r(HF, LB, "M", "Perch competition; hawkfish may chase blennies", HF)
r(HF, TB, "M", "Perch competition; hawkfish may chase blennies", HF)
r(HF, CBS, "M", "Hawkfish may prey on small shrimp", HF)
# 04 §13-14
r(LB, TB, "H", "Blennies fight over rock territory")
r(LB, LB, "H", "One lawnmower blenny per tank")
r(TB, TB, "H", "One tailspot blenny per tank unless a bonded pair")
r(DB, DB, "H", "Keep one dottyback")
r(DB, LB, "M", "Dottyback may chase blennies from its ledge", DB)
r(DB, TB, "M", "Dottyback may chase blennies from its ledge", DB)
r(DB, CA, "L", "Dottyback may bully a small cardinal", DB)
r(DB, HF, "M", "Two ledge predators compete for rock", None)
r(DB, CL, "L", "Clowns may chase the dottyback near the host", CL)
r(DB, EE, "L", "Dottyback avoids the eel den")
r(SK, HF, "MH", "Hawkfish treat shrimp as food", HF)
r(SK, EE, "H", "Eels eat shrimp", EE)
r(SK, WR, "M", "Two cleaners split the clients")
r(SK, CBS, "M", "Banded shrimp may bully the cleaner shrimp", CBS)
r(SK, BH, "L", "Hermits may take a freshly molted shrimp", BH)
r(SK, SH, "L", "Hermits may take a freshly molted shrimp", SH)
r(SK, FD, "M", "Shrimp may pick at the feather duster crown", SK)
r(BH, SH, "M", "Shell competition between hermits")
r(BH, BH, "M", "Shell fights")
r(BH, NA, "M", "Hermits may kill small nassarius", BH)
r(SH, NA, "M", "Hermits may kill small nassarius", SH)
r(BH, AS, "H", "Hermits eat flipped astrea snails", BH)
r(SH, AS, "H", "Hermits eat flipped astrea snails", SH)
r(BH, EE, "H", "Eels eat crabs", EE)
r(SH, EE, "H", "Eels eat crabs", EE)
r(BH, HF, "M", "Hawkfish may pick at hermits", HF)
r(SH, HF, "M", "Hawkfish may pick at hermits", HF)
r(BH, WR, "L", "Wrasse may pick at hermits", WR)
r(BS, EE, "L", "Eel may eat a slow brittle star", EE)
r(BS, HF, "L", "Hawkfish may nip arms", HF)
r(BS, WR, "L", "Wrasse may nip arms", WR)
r(FD, WR, "H", "Wrasses pick at feather duster crowns", WR)
r(FD, HF, "M", "Hawkfish may pick at the crown", HF)
r(FD, TA, "L", "Tang may nip the crown", TA)
r(FD, EE, "L", "Eel may strike the crown", EE)
r(FD, CBS, "M", "Shrimp may pick at the crown", CBS)
r(NA, WR, "M", "Wrasse may eat small nassarius", WR)
r(NA, HF, "L", "Hawkfish may pick at snails", HF)
r(AS, CBS, "M", "Banded shrimp can attack astrea", CBS)
r(AS, TA, "L", "Competes for algae")
r(AT, OP, "M", "Some Asterina eat coral polyps", AT)
r(AT, AT, "M", "Population grows by splitting")
r(OP, TA, "L", "Tang may nip polyps", TA)
r(ZO, TA, "L", "Tang may nip zoanthids", TA)
r(LB, TA, "L", "Competes for algae")
r(TB, ZO, "L", "A hungry tailspot may nip polyps", TB)
r(GSP, KE, "L", "GSP can overgrow the Kenya base", GSP)

with open(os.path.join(out, "interactions.json"), "w") as f:
    json.dump(I, f, indent=1, ensure_ascii=False)
    f.write("\n")
print(len(ids), "species,", len(I), "interactions")
