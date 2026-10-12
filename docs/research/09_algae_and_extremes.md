# Algae Growth Model and Extreme Sandbox Ranges (60 gal, ~227 L mixed soft-coral reef)

Scope: Part A gives an implementable growth, succession, uptake, grazing, die-back and rendering model for seven algae groups. Part B gives slider min/max for 14 parameters with the outcome and speed at each extreme. Temperature lethality in the 33-40 C band is covered by a separate research task and is only referenced here.

Method note. Full-text fetches were blocked by the proxy for every host tried (int-res, Springer, PMC, USGS, Frontiers, CSIRO, arXiv, university repositories), so every sourced number below comes from abstract text or search-engine excerpts of the paper named. Numbers tagged **est.** are my derivations or hobby-consensus values and are tunable. Numbers without a tag are quoted from the source in brackets.

**Source legend.**
[MAG15] Magnusson et al. 2015, growth of three Derbesia tenuissima isolates, Algal Research (mirror: marineagronomy.org PDF).
[RUN03] Runcie et al. 2003, Ulva lactuca nitrate and ammonium uptake kinetics, Aquatic Botany.
[LIU25] Liu et al. 2025, Ulva prolifera uptake kinetics in aquaculture effluent, Front. Mar. Sci. 12:1654677.
[LAV91] Lavery and McComb 1991, Chaetomorpha linum nutritional eco-physiology, Peel Inlet (Murdoch repository).
[NIS04a] Nishikawa et al. 2004, Eucampia zodiacus growth Ks, Nippon Suisan Gakkaishi 70(1):31-38.
[NIS04b] Nishikawa et al. 2004, Coscinodiscus wailesii growth Ks, Nippon Suisan Gakkaishi 70(6):872-878.
[LEY09] Leynaert et al., no-limit multiphasic silicic acid uptake by benthic diatoms (hal-00451210).
[KOR] Korean benthic Navicula growth studies (KIOST sciwatch 29687; Navicula incerta CICESE record).
[IBG24] Ibghi et al. 2024, Ostreopsis sp. 9, Prorocentrum lima, Coolia monotis growth, Strait of Gibraltar (PMC10819257).
[FEMS18] FEMS Microbiol. Ecol. 94(1):fix166, P. lima growth vs light.
[AQU12] Aquino-Cruz 2012 PhD thesis, Southampton, Amphidinium carterae growth 5-30 C.
[ELM04] Elmetri and Bell 2004, Lyngbya majuscula growth vs phosphate, MEPS 281:27-35.
[AHE] Ahern et al., L. majuscula Fe and P demand, Deception Bay (CSIRO MF15219 / MF05022).
[BRE07] Breitbarth et al. 2007, Trichodesmium IMS101 growth vs temperature, Biogeosciences 4:53.
[MUL05] Mulholland and Bernhardt 2005, Trichodesmium chemostats at 0.10-0.33 d-1 (ODU repository).
[SYN] Synechococcus phosphate Ks review (PMC4324148).
[MAT89] Matsuda 1989, CCA lateral growth, Ishigaki, Coral Reefs.
[STE07] Steller et al. 2007, Lithophyllum margaritae extension, Gulf of California (cited in Kleven 2024 UOG thesis).
[KUF08] Kuffner et al. 2008, Nature Geoscience 1:114, CCA under elevated CO2 (USGS pub 70160213; NAS summary).
[A&S83] Atkinson and Smith 1983, C:N:P of benthic marine plants, Limnol. Oceanogr. 28:568.
[ULVDEC] Dutch Ulva degradation study (marineinfo 31830) and Ulva rigida 14C decomposition (marineinfo dataset 1116).
[PER25] Percnon gibbesi grazing rate, PMC12287754 and Dryad vq83bk43w.
[K&P89] Klumpp and Pulfrich 1989, Turbo chrysostomus and Trochus pyramis ingestion, Davies Reef (Springer BF00338269).
[TURB] Turbo torquatus consumption, ECU works 775.
[CRA86] Craig 1996, Acanthurus lineatus territory and bites, American Samoa (Springer BF00001695).
[BRU] Bonaire parrotfish allometric intake, Helgol. Mar. Res. 2009 (hmr s10152-009-0177-4).
[DCBD] A. coeruleus Dictyota consumption, dcbd.nl node 3011.
[HUM20] Humphries et al. 2020, Kenya turf grazing (URI favs_facpubs/206).
[CAR94] Carpenter 1994, St Croix turf exclusion (NOAA AOML salt_river94).
[MCC] McClanahan, phosphorus-enriched cage colonisation (UNAM mirror).
[FRA96] Franklin et al. 1996, photoinhibition of reef epilithic algae, Plant Cell Environ. 19:825.
[NEC04] Necchi 2004, Ik of 33 macroalgae.
[BOYD-N] Boyd, nitrite toxicity review, Global Seafood Alliance.
[FLO25] Starry flounder nitrite LC50 (PMC12473854).
[GOOD22] Goodeid nitrate LC50s, Front. Ecol. Evol. 10:1014814.
[MCG06] McGurk et al. 2006, salmonid nitrate LC50s.
[GULF] Gulf coral salinity tolerance (Smithsonian repository; Manzello Porites furcata ms).
[FLA] Flame angelfish copper trials (UF/IFAS FA165); Texas A&M copper-in-marine-systems extension sheet.
[FM] FaunaMarin copper knowledge-base (hobby vendor).
[ARX25] arXiv 2509.18225, copper LC50s across marine invertebrates.
[LIT] Lithodes santolla zoea copper LC50 (CONICET 11336/12797).
[COP] Copepod pH LC50s, elevated-pH study (KU).
[SEAB26] Asian seabass acidification LC50 (news-oceanacidification-icc, 2026).
[TDG] Total dissolved gas criteria (WA Ecology 0810059; BC EAO; Isaksen et al. PLOS ONE).
[HYPX] Hyperoxia and thermal tolerance in fishes (PMC9382203).
[PHOS] Acropora digitifera juvenile skeletal inhibition by phosphate, R. Soc. Open Sci. 2021 (PMC8074908); patch-reef 43 % inhibition quoted via Reefkeeping/Holmes-Farley.
[HF-MG] Holmes-Farley magnesium article (via reefcentral archive).
[05] Existing file docs/research/05_parameter_effects.md.

Unit conversions used throughout: 1 uM NO3 = 0.062 ppm NO3 = 0.014 ppm NO3-N. 1 uM PO4 = 0.095 ppm PO4 = 0.031 ppm PO4-P. Hobby kits report ppm NO3 and ppm PO4. In 227 L, 1 ppm NO3 = 0.227 g NO3 = 0.051 g N; 1 ppm PO4 = 0.227 g PO4 = 0.074 g P. Algal dry weight (DW) is about 10 % of wet weight (est.), and carbon is about 30 % of DW (est.).

---

## Part A. Algae growth model

### A0. Model skeleton

State per group g: biomass B_g (g DW in the tank) and, for rendering, cover fraction C_g,s on each surface s (glass, sand, rock top, rock shade, dead spot). Biomass drives chemistry; cover drives rendering.

```
dB_g/dt = B_g * mu_g - G_g - D_g - E_g

mu_g = mu_max_g * f_nut * f_light * f_flow * f_temp * f_space
f_nut   = min( N/(Ks_N+N), P/(Ks_P+P), Si/(Ks_Si+Si) [diatoms only] )     # Liebig minimum of Monod terms
          then clamp f_nut >= q_store_g  (internal store, see A3)
f_light = (1 - exp(-PAR_s / Ik_g)) * photoinhib(PAR_s)                     # photoinhib = 1 above Ik, falls to 0.5 at PAR_inhib (est.)
f_flow  = group-specific hump over flow v (table A1)
f_temp  = exp(-((T - Topt_g)/w_g)^2)                                       # w ~ 5 C (est.); lethality from 05 and the heat task
f_space = 1 - sum_over_groups( C_g,s / Cmax_s )                            # competition for surface
G_g     = sum_grazers( rate_z * pref_z,g * B_g / (K_graze + B_g) )          # Holling II, A4
D_g     = B_g * d_g(T, f_nut, f_light)                                      # die-back, A5
E_g     = manual removal, skimmer, water change (owner actions)
```

Cover from biomass per surface: C_g,s = 1 - exp(-B_g,s / b_half_g) where b_half_g is the DW per m2 that gives 63 % cover (table A6). Allocate B_g to surfaces by the affinity weights in A6.

Nutrient drawdown per step: dN = -sum_g (dB_g,growth * n_g), dP = -sum_g (dB_g,growth * p_g), with n_g and p_g the tissue N and P fractions (A3). Release on die-back is the reverse, lagged by a decay constant (A5).

Timestep: 1 h is fine; growth rates below are per day.

### A1. Group parameters

| Group | mu_max (d-1) | doubling (d) | Ks_N (uM NO3) / ppm NO3 | Ks_P (uM PO4) / ppm PO4 | Ik (umol m-2 s-1) | flow preference | Topt (C) | notes |
|---|---|---|---|---|---|---|---|---|
| Diatoms (brown film) | 0.9-1.7 [KOR: Navicula 1.68 at 20 C, N. incerta 0.88] | 0.4-0.8 | 0.9-1.4 [NIS04a,b] / 0.06-0.09 | 0.3-0.4 [NIS04a,b] / 0.03-0.04; benthic strains higher (est. 1 uM) | 50-150 (est., shade-tolerant) | flat (est.); silicate-limited not flow-limited | 21-25 [KOR culture guide]; grows to 30 (est.) | Ks_Si 0.9-6 uM pelagic [NIS04a,b], benthic 54 uM [LEY09]; sim Ks_Si = 5 uM (0.14 ppm SiO2), est. |
| Green hair algae (Derbesia, Bryopsis) | 0.12-0.29 [MAG15: 13-33 %/d] | 2.4-5.7 (est. from MAG15) | 30 [RUN03 Ulva 34; LIU25 29] / 1.9 ppm NO3; NH4 Ks 4.6 uM [LIU25] | no Derbesia value; use 1.0 uM / 0.1 ppm (est., macroalgae P Ks typically 0.5-3 uM) | 150-300 [NEC04: green algae Ik 100-400, sun-adapted] | hump, peak 5-20 cm/s; survives 0-40 (est.) | 24-30 [MAG15 varied T; est. opt 27] | tissue N 2-4 % DW, P 0.2-0.4 % DW (est. from A&S83 and LAV91) |
| Cyanobacteria (red slime, Lyngbya/Oscillatoria) | 0.2-0.3 [BRE07: Trichodesmium 0.25 at 24-30 C; MUL05 doublings 3-10 d] mat area can double in 2-3 d (est., hobby) | 2.5-3.5 | N2-fixer: Ks_N effectively 0 (uses NH4, DON, N2) | growth saturates at 0.18 uM PO4 = 0.017 ppm [ELM04]; Ks_P est. 0.05 uM (0.005 ppm); Synechococcus Ks_P 0.014-67 uM [SYN] | 100-250 (est.) | strong preference for v < 5 cm/s; growth factor 1.0 at 0-3 cm/s, 0.3 at 10, 0.05 at 20 (est.) | 28-32 (est., warm-water mats) | iron and DOC co-limit [AHE]; sediment P release when DO < 3 mg/L [AHE] |
| Dinoflagellates (Ostreopsis, Prorocentrum, Amphidinium, Coolia) | 0.21-0.26 [IBG24 at 24 C, 90 PAR]; 0.23 [FEMS18 at 80-100 PAR]; Amphidinium 0.14-0.55 at 5-25 C [AQU12], 0.59-0.67 div/d at 100-250 PAR (preprint) | 1.5-3.3 | very low, est. 0.3 uM; mixotrophic, eats bacteria | very low, est. 0.03 uM (0.003 ppm); model config value 0.05 uM | 80-150 [IBG24, FEMS18]; Amphidinium tolerates 250 | prefers v < 8 cm/s; Ostreopsis/Prorocentrum go planktonic at night (est.) | 25-30 [AQU12: opt 25-30 for Amphidinium] | the only group whose f_nut is multiplied by a "competitor-absent" bonus (A2) |
| Film / GSA on glass (green dust: Chlorella-like unicells, Ulvella) | 0.5-1.0 (est.; visible film in 2-4 d, hobby) | 0.7-1.4 | 5 uM / 0.3 ppm (est.) | 0.3 uM / 0.03 ppm (est.) | 100-200 (est.) | flat (est.) | 24-30 (est.) | treat as the "always on" background; its cover is the glass-wiping cue |
| Bubble algae (Valonia/Ventricaria) | 0.03-0.06 (est.; "moderate" growth, weeks per cm) | 12-25 | 10 uM / 0.6 ppm (est.) | 0.5 uM / 0.05 ppm (est.) | 100-200 (est.) | prefers stagnant, established rock (hobby); factor 1.0 at < 5 cm/s, 0.5 at 15 (est.) | 24-28 (est.) | spreads by cell division and by rupture: a popped cell seeds 3-8 new cells nearby (est., hobby consensus); 1-4 cm per cell |
| Coralline (CCA) | 0.01-0.02 (est. from lateral 2.9-3.9 mm/month [MAT89]; tips 0.8-5 mm/yr [STE07]) | 35-70 | low N need, 1 uM (est.) | 0.1 uM (est.) but inhibited above 0.1 ppm PO4 (est., 05 sec. 7) | 50-150; shaded CCA grew faster than exposed except in summer (GBR study, MEPS 568) | hump, peak 10-25 cm/s (est.) | 25-28 (est.) | needs Omega_arag: factor 1.0 at pH 8.2/8 dKH, 0.5 at pH 7.9, 0.1 at pH 7.7 (est. from KUF08: -78 % recruitment, -92 % cover at roughly +365 uatm pCO2, pH est. 7.9) |

Flow in cm/s is the local velocity on the surface, not pump turnover. Map tank turnover to local velocity with the zone table in 05 section 16 (dead spots 0-3 cm/s, sand bed 3-8, rock tops 10-25, near powerhead 30-60; est.).

### A2. Succession and triggers

Observed sequence in a new tank (hobby consensus; durations est. unless noted):

| Stage | Start (days after fill) | Peak | Fade | Trigger on | Trigger off |
|---|---|---|---|---|---|
| 1. Diatom film (brown dust on sand and glass) | 7-14 | 21-35 | 28-56 ("2-8 weeks", hobby guides; "4-8 weeks as filtration matures") | silicate leached from new sand, rock and tap water (tap 1-5 ppm SiO2 reported) plus first NH4/NO3 | silicate exhausted; rule: fade when Si < 0.05 ppm (est.) |
| 2. Green film / GSA on glass | 14-21 | ongoing | never; wiped every 3-7 d | any NO3 > 1 ppm and light | glass cleaning (owner action) |
| 3. Green hair algae (Derbesia, then Bryopsis) | 21-60 | 60-120 | 90-180 with grazers and nutrient export | NO3 > 5-10 ppm or PO4 > 0.05-0.1 ppm [05 sec. 6-7], light > 150 PAR, flow 5-20 cm/s | NO3 < 2 ppm and PO4 < 0.03 ppm, or grazing > growth |
| 4a. Cyanobacteria | any time after week 4; common at 60-180 | 2-3 weeks after onset | 1-3 weeks after flow and DOC fix | low flow (< 5 cm/s), high DOC (overfeeding, die-off, carbon dosing), PO4 available, warm (> 28 C), low NO3 relative to PO4 (N:P < 5 by atoms; est.) | flow restored, PO4 < 0.03 ppm, lights-out 3 d, antibiotic (owner action) |
| 4b. Dinoflagellates | typically month 3-12, after an aggressive nutrient crash | 1-2 weeks after onset | weeks to months; often recurs | NO3 < 1 ppm AND PO4 < 0.02 ppm (ultra-low), low biodiversity (new sand, after GFO/carbon dosing), long photoperiod, warm | NO3 raised to 2-5 ppm and PO4 to 0.03-0.1 ppm, UV, 3-day blackout, dosing bacteria/phyto (owner action) |
| 5. Bubble algae | month 3+ | slow | months | mature rock, low flow pockets, moderate nutrients, pH <= 8.0 (hobby) | manual removal, emerald crab (not in this stock list) |
| 6. Coralline | month 2-6 | 6-18 months | never (steady state) | stable Ca/alk/Mg, pH > 8.0, moderate light, seed from live rock | low pH, PO4 > 0.1 ppm, copper, grazers scrape it but do not clear it |

Implementation of the triggers:

- **N:P ratio switch.** Compute atomic N:P = (ppm NO3 / 0.062) / (ppm PO4 / 0.095). Green hair algae and diatoms take the Liebig minimum as written. Cyano gets a bonus factor 1 + 0.5 * clamp((8 - N:P)/8, 0, 1): it fixes N2, so it wins when NO3 is low but PO4 is still present. Dinos get a bonus when both are near zero (next bullet). Hobby rule in 05 section 7 (N:P near 10-15:1 keeps algae in check) stays as the "balanced" band.
- **Ultra-low-nutrient dino trigger.** Define ULN = (NO3 < 1 ppm) and (PO4 < 0.02 ppm). While ULN holds, set dino f_nut floor to 0.6 (they are mixotrophic and eat bacteria and dissolved organics), and multiply every other group's f_nut by its own Monod terms, which collapse toward 0. Net effect: dinos out-compete everything within 1-2 weeks of a nutrient crash. Add a "competitor index" = sum of other groups' biomass plus a bacterial-diversity term that rises 0 to 1 over the first 6 months and drops by 0.3 after any antibiotic or copper event; dino mu is multiplied by (1.3 - 0.8 * competitor index) (est.).
- **Low-flow cyano trigger.** f_flow for cyano as in A1. Add DOC proxy: DOC rises with feeding above the stock's ration, with any die-off (05 section 11) and with carbon dosing; cyano mu_max is multiplied by 1 + DOC_index (0-1). Mats form first in the flow shadow behind rock and on the sand near the base of the rock.
- **Silicate.** Track Si as a pool. Initial Si = 1-3 ppm SiO2 for dry rock/dry sand starts, 0.3-1 for live-rock starts, 0.05-0.1 for RO/DI top-off water (est.). Leach rate from new sand: 0.1 ppm/day declining with half-life 14 d (est.). Diatom mu uses Ks_Si = 5 uM (0.14 ppm) (est., between pelagic and benthic values). Each g DW of diatoms removes about 0.3 g SiO2 (diatom frustules are 20-40 % SiO2 by DW; est.). Tap-water top-off re-seeds Si.
- **Light.** Diatoms and cyano and dinos are shade-tolerant (Ik 80-150). Hair algae wants more (Ik 150-300). Reef lighting of 200-400 PAR on rock tops (05 section 14) saturates all groups; the dimmer sand (50-150 PAR) still saturates diatoms and dinos. A blackout (PAR = 0) for 3 days cuts dino and cyano biomass by 60-80 % (est., hobby practice) while hair algae loses 10-20 %.

### A3. Nutrient uptake and competition with corals

**Stoichiometry.** Macroalgae median C:N:P = 550:30:1 by atoms [A&S83]; microalgae (diatoms, dinos, film) ~106:16:1 (Redfield). Converting to mass fractions of DW:

| Group | N % DW | P % DW | N:P (atoms) | ppm NO3 removed per g DW grown (227 L) | ppm PO4 removed per g DW grown |
|---|---|---|---|---|---|
| Hair algae, bubble algae | 2.5 (range 1.2 critical [LAV91] to 4 replete, est.) | 0.15 (0.05 critical [LAV91] to 0.3, est.) | 30 | 0.49 | 0.020 |
| Diatoms, film, dinos | 5 (est., Redfield) | 0.7 (est.) | 16 | 0.98 | 0.095 |
| Cyanobacteria | 6 (est.) | 0.5 (est.) | 20-25 | 0.2 (only the non-fixed share; est. 20 % of N from NO3/NH4) | 0.068 |
| Coralline | 0.5 (est.) | 0.03 (est.) | 30 | 0.10 | 0.004 |

Worked example: 10 g DW of hair algae (roughly a fist-sized clump, 100 g wet) holds about 5 ppm NO3 and 0.2 ppm PO4 worth of nutrient in a 227 L tank. Growing at 20 %/d it removes about 1 ppm NO3 and 0.04 ppm PO4 per day. That is the same order as a 10 % water change, which is why visible hair algae can pin NO3 near zero on the test kit while the tank still has a high nutrient input.

**Uptake kinetics.** Use the Monod terms in A1 for growth and add luxury uptake: each group has an internal quota q in [q_min, q_max]. Uptake rate = V_max * S/(Ks+S) * (1 - q/q_max); growth uses q via Droop, mu = mu_max * (1 - q_min/q). For hair algae V_max(NO3) = 116-161 umol g-1 DW h-1 [RUN03, LIU25] which is 1.7-2.4 ppm NO3 per day per 10 g DW in 227 L when NO3 is saturating; V_max(PO4) for Chaetomorpha 667 ug P g-1 DW h-1 [LAV91] = 0.5 ppm PO4 per day per 10 g DW. Nitrogen-starved thalli take up nitrate several-fold faster (Ulva, Ecologia 2010), which is why a crop "resets" fast after a water change. Luxury P storage up to 10x initial tissue P was recorded in lagoon macroalgae (Sci. Mar. 66(4), 2002). If the sim skips Droop, keep the simpler clamp in A0: f_nut >= q_store where q_store decays from 1 to 0 over 7 days of starvation (hair algae), 2 days (diatoms, dinos), 4 days (cyano) (est.).

**Ammonia preference.** All groups take NH4 before NO3 (Ulva NH4 Ks 4.6 uM vs NO3 29 uM [LIU25]). In the sim, route TAN to algae first with a Ks of 5 uM (0.07 ppm NH3-N) before nitrifiers see it; that is why a cycling tank's diatom bloom tracks the ammonia peak.

**Competition with corals.**
- Nutrient competition: soft corals (Sarcophyton, Sinularia, zoanthids) take dissolved N and P too, but at far lower rates per gram. In the sim, give corals a fixed draw of 0.02 ppm NO3 and 0.002 ppm PO4 per day per 100 g coral wet weight (est.) and let them show pale, shrunken tissue after 2-3 weeks below 0.5 ppm NO3 (05 section 6). Algae do not starve corals directly; the ULN state the owner creates while fighting algae does.
- Space and shading: hair algae at > 40 % cover on a rock face reduces PAR reaching corals on that face by 30-60 % (est.) and abrades polyps. Macroalgal contact raised coral tissue-loss rate under all CO2 treatments (Sci. Rep. 2016, 7:41053), and surface-bound allelochemicals of common macroalgae kill coral tissue on contact (PMC3203809). Sim rule: coral tissue in contact with hair algae or cyano mat loses 1 % per day (est.); cyano mat over a coral base kills tissue under it in 5-10 days (est., hobby).
- Phosphate and calcification: PO4 at 0.19 ppm (2 uM) for 3 h/day cut patch-reef calcification 43 % (quoted via Holmes-Farley; primary not reached). Juvenile Acropora skeletal formation is clearly inhibited by high seawater phosphate [PHOS]. Reuse 05 section 7 thresholds: coralline slows above 0.1 ppm, coral calcification impaired above 0.3 ppm.
- Dinos and cyano toxins: Ostreopsis blooms are associated with benthic invertebrate die-offs in the field (Mediterranean reports) and palytoxin-group compounds kill Artemia at bloom concentrations. Sim rule: while dino cover on rock exceeds 30 %, snails and hermits take a 2 %/day mortality hazard and corals a polyp-retraction state (est., hobby reports widely describe snail losses during dino outbreaks).

### A4. Grazing pressure

Stock in this tank: juvenile palette tang (est. 7-10 cm, 20-40 g), Sally Lightfoot crab (Percnon gibbesi is the trade's "Sally Lightfoot"; the Grapsus species of the same common name has no feeding data), snails (Trochus, Turbo, Nassarius/Cerith as mixed), hermits (Clibanarius, Paguristes), plus the lawnmower blenny and tailspot blenny from 02/04 which eat film and short turf.

| Grazer | Sourced rate | Sim rate (g DW algae per animal per day) | Preference weights (diatom film / GSA film / hair algae / cyano / dinos / bubble / coralline) | Notes |
|---|---|---|---|---|
| Palette tang, juvenile | A. lineatus adult: 17,000 bites/day [CRA86]; A. coeruleus adult eats ~44 g wet Dictyota/day [DCBD]; allometric daily C intake = 0.0342 * W^0.816 (W in g wet) from Caribbean grazers [BRU] | 0.5-1.5 for a 30 g fish (est.: allometric gives 0.5 g C/d which is ~1.6 g DW, discounted because juvenile Paracanthurus is a weak hair-algae grazer and prefers nori) | 0.3 / 0.5 / 1.0 (short Derbesia only; Bryopsis 0.1) / 0 / 0 / 0.05 / 0 | bite rate rises with temperature (higher at 25 than 20 C, aquarium trials); feeds daylight only, peak afternoon [CRA86]; satiates: if fed nori > 0.5 g DW/d, grazing drops 50 % (est.) |
| Sally Lightfoot crab (Percnon gibbesi) | 3.83 g wet macroalgae/crab/day; 2.71 g/d at optimum 26.3-31.1 C [PER25] | 0.3-0.4 | 0.5 / 0.5 / 1.0 / 0.3 / 0.1 / 0.3 (picks at bubbles) / 0 | nocturnal and crepuscular; grows into an opportunist that nips coral and small fish above ~5 cm carapace (hobby) |
| Trochus snail | Trochus pyramis 54 mg C/animal/day [K&P89] = ~0.18 g DW | 0.15 | 1.0 / 1.0 / 0.4 (only new fuzz) / 0.2 / 0.1 / 0 / 0.1 (scrapes but does not remove) | feeds continuously [K&P89] |
| Turbo snail | Turbo chrysostomus 35 mg C/day [K&P89]; T. torquatus 150-450 mg wet/day [TURB]; Turbo twice as effective as Tegula/Tectus on Ulva in a 24 h aquarium trial | 0.12 | 1.0 / 1.0 / 0.6 / 0.1 / 0.1 / 0 / 0.1 | nocturnal periodicity [K&P89]; large Turbo topple frags (hobby) |
| Nassarius / Cerith (if present) | no algae rate; Nassarius is a detritivore | 0.02 film, plus detritus 0.05 | 0.5 / 0.3 / 0 / 0 / 0 / 0 / 0 | sand stirring reduces cyano on sand by est. 20 % |
| Hermit crab (Clibanarius, Paguristes) | no measured rate; vendor notes Paguristes eats green hair algae readily, cyano only when starved; Clibanarius may eat cyano | 0.03-0.05 | 0.6 / 0.5 / 0.8 / 0.3 / 0 / 0.1 / 0 | also eats detritus and leftover food (counts against DOC pool) |
| Lawnmower blenny | no rate; hobby: clears film and short turf, starves on bare rock | 0.2-0.4 (est.) | 1.0 / 1.0 / 0.5 / 0 / 0 / 0 / 0 | if film + short turf biomass < 0.5 g DW it loses condition (05 fish notes) |
| Tailspot blenny | | 0.05-0.1 (est.) | 0.8 / 0.8 / 0.2 / 0 / 0 / 0 / 0 | |

Areal equivalents for rendering: a film of diatoms or GSA on glass is about 1-3 g DW per m2 at the "visible haze" stage and 5-10 g DW m-2 when opaque (est., benthic microalgae standing stock). One Trochus therefore clears est. 100-300 cm2 of film per day, which matches the hobby rule of thumb that 1 Trochus per 5-10 gallons keeps glass readable. Hair algae turf is 20-100 g DW m-2 at 50-100 % cover (est., turf standing stocks in grazing-exclusion studies run 2-5x grazed biomass [CAR94]); a juvenile tang at 1 g DW/day clears about 100-500 cm2 of dense turf per day, so it keeps up with hair algae only while cover is low.

Grazer-side rules:
- Holling II with K_graze = 1 g DW for snails and hermits, 3 g DW for the tang, so grazing is near zero on a clean tank and saturates on a lawn.
- Palatability falls with algae age: Derbesia older than 14 days and all Bryopsis get a 0.1-0.3 multiplier (est.). Cyano and dinos are avoided by everything except hermits and Clibanarius at 0.3.
- Grazing never exceeded 2 g DW m-2 d-1 and removed under half of turf production in young fishery closures [HUM20]: cap total grazing at 3 g DW m-2 d-1 of rock surface so a heavy cleanup crew cannot zero a lawn overnight.
- Copper, salinity shock or low DO (Part B) remove inverts from the grazer sum; dinos (A3) remove snails over days.

### A5. Die-back and nutrient release

Triggers: nutrients cut (ULN), light cut (blackout, lamp failure), flow change (cyano starves when flow is raised; hair algae tears off above 40 cm/s, est.), chemical (fluconazole for Bryopsis, erythromycin/chemiclean for cyano, hydrogen peroxide, copper), grazer surge.

Death rate d_g (per day) once the trigger holds:

| Group | Starvation (f_nut < 0.1) | Dark (PAR < 10) | Chemical | Remark |
|---|---|---|---|---|
| Diatoms | 0.3 | 0.3 | 0.5 (peroxide) | crash within a week when Si is gone (hobby: bloom "fades in 1-2 weeks") |
| GSA film | 0.15 | 0.2 | 0.5 | |
| Hair algae | 0.05 (lives off internal store 1-2 weeks first) | 0.1 | 0.3 (fluconazole on Bryopsis: whitens and stops growing by end of a 14 d course, hobby) | after 2 weeks of ULN it turns pale, then sloughs |
| Cyano | 0.2 when flow raised or PO4 < 0.01 ppm | 0.4 (3-day blackout removes most) | 0.5-0.7 (antibiotic, 2-3 days) | mat lifts off in sheets |
| Dinos | 0.05 under ULN (they are the survivors), 0.3 once nutrients are raised AND competitors return | 0.4 | UV: 0.3/day on the water-column fraction only | often recurs: keep a hidden seed pool of 1 % cover |
| Bubble algae | 0.02 | 0.05 | 0.2 | removal pops cells: +3-8 new cells unless removed intact |
| Coralline | 0.01 under low Ca/alk, 0.05 at pH < 7.7 | 0.02 | copper 0.1-0.3 | turns white (bleached) before it sloughs |

Release on decay. Dead algal tissue remineralises first-order: Ulva k = 0.2 d-1 in the first week, slowing to 0.025 d-1 by day 52; carbon half-time 7.6-8.1 d [ULVDEC]. N release was much higher under oxic than anoxic conditions [ULVDEC]. Sim: dead biomass pool M_g with dM/dt = D_g - k_d * M, k_d = 0.15 d-1 (est. average), and each g DW remineralised returns its tissue N as TAN (not NO3) and tissue P as PO4, i.e. 0.49 ppm NO3-equivalent (as 0.11 ppm NH3-N) and 0.02 ppm PO4 per g DW of hair algae in 227 L. A 50 g DW hair-algae die-off therefore puts about 5 ppm NH3-N-equivalent through the nitrifiers over two weeks and 1 ppm PO4 back in the water, which is the "I killed the algae and then cyano/dinos took over" sequence that owners report. Decay also consumes O2: about 1.3 g O2 per g DW (est., C 30 % DW oxidised), so a 50 g die-off draws 65 g O2 over two weeks versus a tank inventory of about 1.4 g at saturation; without surface exchange the night-time DO dip in 05 section 8 deepens by 1-2 mg/L (est.). Antibiotic treatment of a heavy cyano mat should trigger the ammonia path in 05 section 11.

### A6. Visual cues for rendering

| Group | First appears on | Spreads to | Colour and texture | Cover timeline (untreated, moderate nutrients) | Cover limit per surface | b_half (g DW m-2 for 63 % cover) |
|---|---|---|---|---|---|---|
| Diatoms | sand surface and glass near the sand line, low-flow zones; rock next | all sand, all glass, rock shade | tan to golden-brown dusting, powdery, lifts in a cloud if disturbed; brown "snot" strands in low flow at peak | 0 at day 7; 30 % sand by day 14; 70-90 % sand and 40 % glass by day 21-28; fades to < 10 % by day 42-56 | sand 100 %, glass 60 %, rock 40 % | 2 (est.) |
| GSA / green film | glass, brightest panel first (front/side nearest light), then powerhead bodies and overflow | all glass, acrylic, equipment | bright green dots that merge into a haze; opaque in 5-7 days; coralline-like hard green spots on old glass | linear: 10 % per day after wiping, opaque at 5-7 d at NO3 5 ppm; 2x slower at NO3 1 ppm (est.) | glass 100 % | 3 (est.) |
| Green hair algae | rock tops and edges in strong light with moderate flow, frag plugs, overflow teeth; not sand | rock faces, coral bases, equipment | Derbesia: fine bright-green fuzz 2-5 mm, sways with flow; grows to 1-3 cm matted strands that trap detritus and bubbles at midday (O2). Bryopsis: feathery, darker, 2-5 cm fronds, iridescent sheen | 1 % day 21; 10 % day 35; 40 % day 60; 70 % by day 90 at NO3 10 / PO4 0.1 (est., logistic r = 0.12 d-1) | rock 90 %, glass 20 % (only below the wipe line), sand 5 % | 40 (est.) |
| Cyanobacteria | sand in flow shadow of rock, sand-rock junction, detritus pockets; then rock shelves facing away from flow | sand bed, rock, over coral bases, glass at the sand line | burgundy to black-red sheet with a slimy, mucilaginous surface; peels in sheets; bubbles trapped under it in the afternoon; dark green or black in low light | spot 2 cm by day 1 of trigger; 10 % sand in 4-5 d; 60 % sand and 20 % rock in 2-3 weeks (est., logistic r = 0.3 d-1); dies back within a week once flow fixed | sand 100 %, rock 60 %, glass 20 % | 10 (est.) |
| Dinoflagellates | sand and rock in low flow, often first on the sand surface as a light brown dusting, then on hair algae and coral bases | everywhere including glass; Ostreopsis forms strings in the water column | light brown to khaki snotty film with long stringy strands that hold bright bubbles at the tips; lifts off at night and reappears at lights-on; sand looks "dirty" | 5 % day 5; 30 % day 10; 70 % by day 15-20 (est., r = 0.25 d-1) | sand 100 %, rock 80 %, glass 50 % | 5 (est.) |
| Bubble algae | shaded undersides and crevices of mature rock, frag plug bases, low flow | rock faces, under coral skirts | glossy dark-green spheres 2-30 mm, singly then in clusters; mirror-like highlights | 1 cell per rock per month at onset; cluster doubling every 3-4 weeks (est.) | rock 30 % | 20 (est.) |
| Coralline | shaded rock, pump bodies, overflow, glass that is not wiped, snail shells; pink first in low light | all hard surfaces | pink, magenta, purple; some red or green. Crust texture, matte, with white growth margins; whitens (bleaches) when stressed, flakes when dead | pinhead spots month 2; 5 % rock month 4; 30 % month 9; 60 % month 18 (est., r = 0.012 d-1) | rock 90 %, glass 50 % (back and sides), sand 0 % | 100 (est.) |

Rendering helpers: light zones (rock tops 200-400 PAR, mid-rock 100-200, sand 50-150, overhangs < 50) and flow zones from A1 give per-surface growth multipliers. A gradient of the same group should render darker and thicker where f_light * f_flow is highest for that group. Cover grows from the trigger point outward, so store a seed coordinate per surface per group and render the cover fraction as a radial or noise-based mask with radius proportional to sqrt(C).

### A7. Acceptance checks for the algae model

1. **New-tank diatom pulse.** Dry rock, dry sand, RO water, Si = 2 ppm, no grazers: diatom cover on sand exceeds 50 % between day 10 and day 28 and falls below 10 % by day 60 with Si < 0.05 ppm. Adding 0.5 ppm Si on day 60 restarts a pulse within 7 days.
2. **Nutrient drawdown is stoichiometric.** Start NO3 10 ppm, PO4 0.2 ppm, 2 g DW hair algae, no grazers, no feeding: after the algae reach 20 g DW, NO3 has fallen by 8.5-9.5 ppm and PO4 by 0.3-0.4 ppm (the PO4 hits its floor first, so growth becomes P-limited; check f_nut reports P as the limiter).
3. **ULN produces dinos, not hair algae.** Hold NO3 at 0.2 ppm and PO4 at 0.005 ppm from a clean start: dino cover reaches 30 % within 21 days; hair algae cover stays under 5 %; raising NO3 to 5 and PO4 to 0.05 and holding them stops dino growth within 7 days and dino cover halves within 21 days.
4. **Low flow produces cyano on sand.** Set the sand-bed zone to 2 cm/s with PO4 0.1 ppm and DOC index 0.5: cyano sand cover exceeds 30 % within 14 days and it appears on sand before rock. Raising sand flow to 15 cm/s (no other change) drops it below 10 % within 10 days.
5. **Grazers hold film but not a lawn.** Four Trochus plus one juvenile tang: glass film cover stays under 30 % indefinitely at NO3 5 ppm, but if hair algae is seeded at 30 % rock cover with NO3 20 / PO4 0.2 the cover still rises to 60 % within 30 days (grazing rate caps at about 3 g DW m-2 d-1 while growth exceeds it).
6. **Die-off releases nutrients and ammonia.** Kill 40 g DW of hair algae at t = 0 in a tank with NO3 1 ppm, PO4 0.02: within 14 days the TAN peak reaches at least 0.1 ppm NH3-N-equivalent (or NO3 rises by 10-20 ppm if nitrification is fast), PO4 rises to at least 0.5 ppm, and night DO drops at least 1 mg/L below the pre-kill night value.
7. **Coralline tracks carbonate chemistry.** From pH 8.2 and 8 dKH with 30 % coralline cover, drop pH to 7.8 and hold: coralline growth rate falls by at least 60 % and cover declines (white margins) within 30 days; restore pH and the growth rate returns within 14 days.
8. **Blackout selectivity.** Three days of PAR = 0: dinos and cyano lose 60-80 % biomass, hair algae loses 10-20 %, coralline loses under 5 %, and GSA film regrows to visible (10 %) within 4 days of lights-on.

---

## Part B. Extreme sandbox ranges

Convention: slider range [min, max]; "normal" is the 05 target band; outcome text gives what the sim should do at or near each extreme and how fast. Reused 05 numbers are marked [05]. Temperatures above 33 C are owned by the heat task; the row below only states the slider and hands off.

| Parameter | Slider min | Slider max | Normal [05] | At the minimum: outcome and speed | At the maximum: outcome and speed |
|---|---|---|---|---|---|
| Temperature | 10 C | 40 C | 26-28 C | 20 C: tangs and dottybacks lethargic, ich cycle > 10 d [05]. 15 C: fish stop feeding within hours, corals retract within a day, soft corals melt in 3-7 d (est.). 10 C: tropical fish lose equilibrium in 1-6 h and die within 24 h (est.); all corals and inverts dead within 2-3 d (est.). Algae: diatoms and GSA keep growing down to ~15 C, hair algae stops below 18 C, cyano and dinos stop below 20 C (est.). | 30-33 C: as 05 (gasping at 30-31, acute tang/cardinal mortality risk at 32-33 within 1-2 d, DHW bleaching). 33-40 C: see the heat task. Algae: cyano and dinos accelerate to 32 C then crash; hair algae peaks 27-30 then bleaches above 33 (est.). DO saturation falls to ~5.3 mg/L at 35 C (est.). |
| Salinity (SG at 25 C) | 1.000 (0 ppt) | 1.045 (60 ppt) | 1.024-1.026 | 1.020 (27 ppt): corals swell, inverts stop molting over days [05]. 1.015 (20 ppt): coral LC50 is 19 PSU for Florida scleractinians (study cited in GULF search); soft corals necrose in 1-3 d, snails and shrimp die in 12-48 h, fish survive but ich-prone (hyposalinity treatment range). 1.010: fish osmotic stress, mortality in 1-3 d for tangs/cardinals (est.). 1.000: all marine life dead within hours (fish 1-6 h, est.). Algae: hair algae and diatoms tolerate to 1.015; cyano survives lower (est.). | 1.030: fish and invert stress within days [05]. 1.035 (46 ppt): Gulf corals survive up to ~50 ppt in nature but after slow acclimation [GULF]; a step change kills soft corals in 2-5 d and shrimp/snails in 1-3 d (est.). 1.040+: fish dehydrate, gasping and death in 1-3 d (est.); above 1.045 everything dies within 24 h (est.). Precipitation of CaCO3 from the salt mix fogs the water above ~1.035 if alk is also high (est.). |
| pH | 6.5 | 9.5 | 8.0-8.3 | 7.5: acute fish stress, death within days [05]. 7.0: coralline and coral skeletons dissolve (Omega < 1); fish acidosis, death in 1-2 d (est.); Asian seabass fry 96 h LC50 was pH 5.88 [SEAB26] so hardy fish may last longer. 6.5: fish dead in hours, all CaCO3 dissolving visibly (sand fizz), aragonite loss est. 1 % per day. Algae: cyano and dinos tolerate to 7.0; hair algae to 7.3; coralline dies below 7.5 (est.). CO2 at these pHs is the lethal agent, not H+ (est.). | 8.5: NH3 fraction ~25 % [05], coral and fish stress. 9.0: copepod LC50s span pH 8.4-9.5 [COP]; NH3 fraction > 50 %, so any TAN > 0.2 ppm is lethal in hours; CaCO3 precipitates as a white haze on glass and pumps within hours (kalk-overdose look); soft corals close for days. 9.5: fish mucus and gill damage, death in 6-24 h (est.); all inverts dead in 1-2 d (est.); massive precipitation drops alk and Ca by 2-4 dKH and 50-100 ppm within a day (est.). |
| Alkalinity (dKH) | 0 | 25 | 7-9 | 6: slow coral growth, soft shells [05]. 4: pH swings 0.5/day, coral tissue stress in 1-2 weeks [05, est.]. 2: pH crashes below 7.5 at night, fish stress in days. 0: pH uncontrolled (drops to 6.5-7 with CO2), everything in the pH-6.5 row within 1-3 d (est.). | 12: precipitation risk, SPS tissue stress [05]. 16: fast abiotic CaCO3 precipitation ("snowstorm") when Ca > 400 and Mg < 1300: water turns cloudy in minutes to hours, pumps and heaters crust over days, alk and Ca then fall back toward 10 dKH/350 ppm in 1-3 days (hobby reports, HF-MG). 20-25: pH spikes to 8.6-8.8 on dosing, burn (tissue loss) on soft corals and zoanthids in 1-3 d, snails stop moving (est.). Hair algae and cyano unaffected directly. |
| Calcium (ppm) | 100 | 800 | 400-450 | 350: calcification loss [05]. 250: coralline stops, snail and hermit shells erode over weeks; corals (soft) mostly tolerate (est.). 100: hard corals and coralline dissolve; fish unaffected directly (est.). | 480: precipitation risk [05]. 600: with alk > 8, snowstorm as in the alkalinity row, within hours. 800: as above plus osmotic/ionic imbalance in inverts, shrimp and snails lethargic in 1-3 d (est.). Dose shock (jump > 100 ppm/h) causes coral retraction for 1-2 d (est.). |
| Magnesium (ppm) | 600 | 2500 | 1250-1350 | 1200: alk and Ca precipitate [05]. 1000: alk cannot be held above 7 dKH, coralline stops, slow cloudy precipitation over days [HF-MG]. 600: alk and Ca crash in 1-3 d, pH falls, hard corals and coralline stop calcifying; soft corals and fish tolerate for weeks (est.). | 1500: no effect. 2000: dosing-salt impurities are the usual harm; model as mild invert stress (est.). 2500: osmotic contribution raises SG by ~0.002; fish fine, shrimp molt failure over weeks (est.). Mg overdose with Epsom salt (sulfate) harms inverts faster than MgCl (est.). |
| Ammonia (ppm NH3-N, un-ionised) | 0 | 5 | < 0.02 | 0: nothing; algae are N-limited only if NO3 is also 0. | 0.05-0.1: gill change, molt issues over days [05]. 0.5: gasping, mortality in 1-3 d [05]. 1.0: lethal to most fish and inverts within hours to 96 h (maroon clownfish 96 h LC50 0.89, 24 h LC50 1.68) [05]. 2-5: fish dead in 1-6 h, inverts in hours, corals bleach and necrose in 1-2 d (est.). Algae: diatoms, GSA and cyano bloom on the TAN within 3-5 days after the spike; hair algae follows in 2 weeks (est.). Slider should expose TAN too (0-50 ppm) and compute NH3 from pH and T per 05 section 4. |
| Nitrite (ppm NO2-N) | 0 | 100 | < 0.1 | none | 0.5-1: as 05 (brown blood, lethargy; mortality risk above 1 ppm over days, est.). Sourced marine fish 96 h LC50s are 100-1000 mg/L NO2-N [BOYD-N] and 574 mg/L for starry flounder [FLO25], so the 05 thresholds are conservative. Sim: at 10 ppm, sensitive fish (tang, cardinal) show stress in 1-2 d and 20 % mortality over a week; shrimp 50 % mortality in 3-5 d (est.). 100 ppm: fish 50 % mortality in 2-4 d, inverts dead in 1-2 d (est.). Nitrite is also taken up by algae (est. same Ks as NO3). |
| Nitrate (ppm NO3) | 0 | 500 | 1-10 | 0 (true zero): soft corals pale in 2-3 weeks [05]; ULN dino trigger if PO4 is also < 0.02 (A2) within 1-2 weeks; hair algae starves (A5). | 30: algae bloom and night DO dips [05]. 100: hair algae and GSA at maximum growth rate, film opaque in 3-4 d, coral colour browns in 2-3 weeks (est.). 500 ppm NO3 (113 ppm NO3-N): fish stress only; freshwater 96 h LC50s are 470-1900 mg/L NO3-N and adults are "relatively insensitive" [GOOD22, MCG06]. Sim: at 500 ppm NO3 fish lose appetite over a week, shrimp 20 % mortality over 2 weeks, soft corals shrink, corals brown fully (est.). Osmotic contribution to SG is negligible. |
| Phosphate (ppm PO4) | 0 | 10 | 0.03-0.1 | 0: coral pallor, ULN dino trigger with low NO3 [05, A2]; coralline fine. | 0.3: strong algae, calcification impaired [05]. 1.0: coralline stops and whitens over 2-4 weeks; coral calcification down 40 %+ (patch-reef 43 % at 0.19 ppm intermittent, via Holmes-Farley; [PHOS]); hair algae and cyano at max rate. 10: phosphate precipitates as Ca-phosphate (cloudiness, alk and Ca drop), coral skeleton growth stops, tissue recedes over 2-4 weeks; fish unaffected (est.). |
| Dissolved O2 (mg/L) | 0 | 20 | 5.5-6.5 (saturation 6.3-6.5 at 25 C) | 4-5: stress [05]. 3: mortality within hours to days [05]. 1: fish dead in 1-3 h, shrimp and snails in 1-2 h, corals in 6-24 h (est.). 0: everything aerobic dead in < 1 h; sediment goes anoxic and releases PO4 and Fe (benthic release occurred once DO < 3 mg/L [AHE]), so cyano gets a nutrient pulse when flow returns. | 8-10 (120-150 % sat): no harm to fish; bubbles collect on algae and glass. 12-15 (180-230 % sat): gas-bubble disease risk; freshwater criterion is 110 % total dissolved gas [TDG] but that counts N2; pure O2 supersaturation is far better tolerated (trout fine at 200 % O2 [HYPX]). Sim: above 150 % sat for > 24 h, 5 % daily fish mortality with bubbles in fins and eyes (est.); 20 mg/L: emboli in fish within hours, 50 % mortality in a day (est.). Photosynthesis in a hair-algae lawn can hit 130-150 % sat by late afternoon on its own (est.). |
| Light (PAR at rock top, umol m-2 s-1) | 0 | 1500 | 200-400 | 0 for 3 d: blackout, see A7 check 8; soft corals tolerate 3-5 d, shrink after 10 d, die after 3-4 weeks (est.). Fish fine. Night-only tank: DO never recovers, so the DO row applies. | 500: high-light corals bleach in weeks [05]. 800: photoinhibition and bleaching of shaded-adapted soft corals in 3-7 d; 600 PAR at 32 C bleached sensitive corals in 8 h (lab study, MEPS 301); 1100 PAR for 3 h caused photoinhibition at 25-31 C (MEPS 268). 1500: noon reef-flat level; shallow reef algae halve Fv/Fm by midday at 1400 [FRA96] but recover; corals not pre-acclimated bleach in 1-3 d and lose tissue in 1-2 weeks (est.). Algae: hair algae and GSA saturate at 300 and show no net gain above; cyano and dinos lose ground above 600 (est.); diatoms photoinhibited above 400 (est.). Photoperiod slider 0-24 h: > 14 h gives algae a 20-40 % growth bonus and corals a slow decline (est.). |
| Flow (tank turnover, x/h, and local velocity) | 0 | 100x (local 0-80 cm/s) | 20-40x (local 5-25 cm/s) | 0: as 05 section 15 (DO dip, cyano bloom risk within hours to days, detritus settles). Beyond 6-12 h without flow or surface agitation, DO falls below 3 mg/L in a stocked 227 L tank (est.) and the DO row takes over; fish deaths in 12-24 h, corals in 1-2 d, cyano explodes in 3-5 d once flow and light return (est.). | 60x (local 40-50 cm/s): forereef flows reach and exceed 50 cm/s naturally, so corals adapted to it survive, but soft corals in a direct powerhead blast close, abrade and necrose over 1-2 weeks (hobby guides; est.). 100x (local 60-80 cm/s): sand blows into drifts and clouds the water in minutes, hair algae and cyano tear off (export), frags and snails get tossed, fish hide and stop feeding, soft corals detach in days (est.). Cyano cannot establish above 20 cm/s local (A1). |
| Copper (ppm Cu, free + chelated as the test reads) | 0 | 2.0 | 0 | none | 0.005: no visible effect [05]. 0.02: partial coral death reported from ~20 ug/L (hobby vendor [FM], est.). 0.05-0.1: shrimp, snails, hermits lethargic then dead over 1-3 d [05]; crab larvae LC50 299 ug/L [LIT], adult hermits are among the most copper-tolerant inverts (marine invert LC50s span 31-5747 ug/L [ARX25]), so hermits die last (est. 3-7 d at 0.2). 0.15-0.25: therapeutic band; fish tolerate 2-4 weeks (yellowtail tang case in 05); flame angelfish were "acutely sensitive" in 0.05-0.25 trials [FLA], so dwarf angels and wrasses get 10-30 % mortality over 2 weeks (est.). 0.5: fish gill damage, gasping, 30-50 % mortality in 3-7 d; all inverts and corals dead within 1-3 d; algae growth cut 50 % (est.). 1.0-2.0: fish dead in 1-3 d; everything else within a day; hair algae and cyano die (whiten) in 2-5 d and release nutrients per A5 (est.). Copper binds to rock and sand and leaches back for months, so the slider should be sticky: after any exposure > 0.1, the tank holds a residual 0.01-0.02 ppm until rock is replaced (est.). |

Cross-effects the sandbox should wire in: temperature changes DO saturation and NH3 fraction [05]; pH changes NH3 fraction [05] and CaCO3 precipitation (alk/Ca/Mg rows); any mass death feeds the die-off cascade in 05 section 11 and the algae release pool in A5; any invert death removes grazers from A4; copper and antibiotics drop the biodiversity index that gates dinos in A2.

---

## Gaps

- No Monod constants exist for Derbesia or Bryopsis; Ulva values stand in. No measured P Ks for benthic dinos; model-config values used.
- Snail, hermit and tang rates are converted from carbon intake of wild adults; juvenile palette tang grazing on hair algae is a guess.
- Diatom film standing stock (g DW m-2) and the coverage-vs-biomass constants (b_half) are all est. and should be fitted to a few owner photos.
- Kuffner 2008 treatment pH was not confirmed (likely ~7.9); the 43 % calcification figure at 0.19 ppm PO4 is quoted second-hand.
- Marine ornamental LC50s for nitrite, nitrate, high pH, hyperoxia and copper were not found; the extremes table extrapolates from food fish, freshwater fish and crustaceans.
