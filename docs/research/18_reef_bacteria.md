# 17 Reef Bacteria Layer: Research and Implementable Model

Date: 2026-10-13
Scope: 60 gal (~227 L) mixed soft-coral reef, live rock, sand bed, skimmer, ~25-27 C.
Purpose: basis for a "bacteria layer" in the reef simulation (nitrifiers, heterotrophs, anaerobes, cyanobacteria, pathogens, probiotics, counts).

## How to read this file

- Each section gives bullets with numbers and units, then 1-3 sources.
- Source confidence is marked:
  - [S] = a source checked in this session (search result or page summary). Read the original before quoting precise numbers.
  - [K] = general microbiology or aquarium knowledge recalled without a source checked in this session. Treat as a prior to verify, not as a citation.
- Where sources conflict, both values are given. Pick one for the model and document the choice.
- Several research questions had no direct aquarium data. Those gaps are called out, and the model uses explicit assumptions for them.

---

## 1. Nitrifiers (ammonia and nitrite oxidizers)

### 1.1 Ammonia-oxidizing bacteria (AOB) vs archaea (AOA) in marine systems

- AOB: Nitrosomonas, Nitrosococcus (marine lineages). Ammonia (NH3) oxidation to nitrite, NH3 + O2 -> NO2- + ... [K]
- AOA: Nitrosopumilus (N. maritimus strain SCM1 is the best-known marine isolate). [K]
- In live rock from a coral aquarium, AOB were the most abundant ammonia oxidizers, and AOA were under 0.01% of the community. [S] (Chinese study, live rock, coral aquaria; see sources)
- In marine recirculating aquaculture biofilters, AOA were the main ammonia oxidizers and their abundance tracked NOB closely. [S]
- A 2024 study of home aquarium biofilters found no comammox Nitrospira in saltwater biofilters; AOA or AOB were dominant depending on salinity. [S]
- Implication: for a reef, AOB dominates the rock surface in an established tank. AOA is a minor pool with very high ammonia affinity that matters at low NH3 (Martens-Habbena et al. 2009 showed AOA can work at low ammonia). [K]

Numbers for AOA high affinity: apparent half-saturation for NH3 around 0.1 uM (about 1.4 ug N/L) for N. maritimus-type AOA, against roughly 1-10 mg NH3-N/L for typical AOB in bulk culture. [K] (Verify against Martens-Habbena et al. 2009, Nature.)

### 1.2 Nitrite-oxidizing bacteria (NOB)

- Nitrospira: most common marine NOB in aquaria and aquaculture. [S]
- Doubling time: about 12-32 h for Nitrospira in culture. [S] (MicrobeWiki, Nitrospira)
- Nitrospina: marine NOB lineage, slower and less studied. [K]
- Nitrobacter: rare in marine systems; mostly freshwater. [K]
- Practical implication: NOB are the bottleneck for nitrite in new tanks. Nitrite accumulates when NOB have not established.

### 1.3 Growth rates and kinetics at 25-27 C

Working values for the model (Monod form, mu = mu_max * S/(Ks + S) * f(T) * f(pH) * f(O2)):

| Parameter | AOB (Nitrosomonas-type) | AOA (Nitrosopumilus-type) | NOB (Nitrospira) |
|---|---|---|---|
| mu_max at ~25-27 C | 0.5-1.0 d^-1 (doubling about 17-33 h) [K] | 0.1-0.5 d^-1 (doubling about 1.5-7 d) [K] | 0.4-0.8 d^-1 (doubling 20-40 h) [S for 12-32 h range] |
| Ks, NH3-N (or NH4+) | 0.1-5 mg NH3-N/L range in culture; lower in biofilm [K] | ~0.1 uM NH3 (about 1.4 ug N/L) [K] | n/a |
| Ks, NO2-N | n/a | n/a | 0.05-0.5 mg NO2-N/L [K] |
| Yield | ~0.1-0.2 g biomass per g NH4-N oxidized [K] | ~0.02-0.05 g biomass per g NH4-N [K] | ~0.02-0.05 g biomass per g NO2-N [K] |

Notes:
- Doubling times for nitrifiers in culture are slow compared with heterotrophs (typically 1-4 h). This is why new tanks cycle over weeks. [K]
- Model choice: use AOB mu_max = 0.7 d^-1 and NOB mu_max = 0.5 d^-1 at 26 C as a starting point. Rates scale with temperature (see 1.4).

### 1.4 Inhibition factors

- Temperature: rates scale roughly as Q10 = 2-2.5 between 20 and 30 C; optimal near 28-32 C; faster loss above ~35 C. [K]
- pH: AOB activity falls sharply below pH ~7.5; largely stopped below pH ~7.0-7.2 in seawater. Free NH3 fraction rises with pH, which helps substrate supply but makes NH3 itself toxic above ~0.02-0.05 mg NH3-N/L for fish. [K]
- Alkalinity: each mol NH4+ oxidized consumes about 2 mol alkalinity (about 7.1 g CaCO3 per g NH4-N). For a 227 L tank, oxidizing 1 mg/L NH4-N costs about 0.23 meq/L of alkalinity. [K] This is a derived check, not a published rate.
- Oxygen: half-saturation for AOB about 0.2-0.5 mg O2/L; NOB are more sensitive, with Ks,O2 about 0.5-1 mg O2/L. Nitrification slows strongly below ~2 mg/L in biofilm. [K]
- Antibiotics:
  - Erythromycin and other macrolides: inhibit AOB at 1-10 mg/L in culture. [K]
  - Ciprofloxacin: inhibits NOB at low mg/L; strong effect on recovery. [K]
  - Copper: AOB EC50 in the range of 0.1-1 mg Cu/L in freshwater studies; marine nitrifiers are more tolerant at comparable free Cu2+ but still inhibited. [K]
  - Heavy metals and ozone disrupt biofilm nitrifiers. [K]
- Nitrite toxicity to fish: NO2- at 0.1-0.5 mg NO2-N/L is harmful to many species over days. Chloride protects in freshwater but not seawater. [K]

### 1.5 Colonisation of new rock and surface area

- Surface area of live rock: roughly 1-3 m^2 per kg of rock, depending on porosity and whether it is dry or cured. [K] (Estimate; not measured in this session.)
- Dry (cured) rock: about 0.1-0.5 m^2 per kg in surface area, with much of it on non-porous faces. [K]
- A 60 gal reef with 20-30 kg of live rock has roughly 20-90 m^2 of rock surface. Sand bed surface area is effectively the tank footprint (about 0.3 m^2 at 60 x 24 in), but grain surface area is much larger, about 1-5 m^2 per kg of fine sand. [K]
- Per litre of tank water: live rock provides about 0.1-0.4 m^2 per L of tank; sand provides about 0.5-2 m^2 per L of sand bed when counted as grain surface. [K]
- Colonisation: rock inoculated from a mature tank can establish a functional nitrifying population in about 2-4 weeks. Rock from a dry source takes longer, usually 4-8 weeks. [S partially: a 2019 mSphere study of two new tanks tracked succession over three months and found that adding live rock shifted communities toward typical saltwater assemblages.]
- Live rock needs a food source after introduction to keep its microbes alive. [S, seller guidance; treat as low-quality source.]

Source: [S] Chinese live rock study (ammonium removal 0.141 mg NH4-N per kg per h; AOB dominant; AOA under 0.01%). Link: https://castjournals.cast.org.cn/joweb/aos/EN/PDF/10.1007/s13131-017-1092-1

### 1.6 Comammox (complete ammonia oxidizers)

- Comammox Nitrospira can convert NH3 to NO3- in one organism. [K]
- Freshwater aquarium biofilters: comammox Nitrospira are abundant (2024 McKnight and Neufeld study). [S]
- Saltwater aquarium biofilters: no comammox detected in that study. [S]
- Marine aquaculture data on comammox: some reports of significant activity in coastal aquaculture. [S] (agris record, coastal aquaculture)
- Model choice: do not include comammox in the marine reef layer. Note it as a freshwater-only process. Set comammox = 0 for marine.

Sources:
- [S] https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11267875/ (comammox in aquarium biofilters; freshwater vs saltwater)
- [S] https://microbewiki.kenyon.edu/index.php/Nitrospira (Nitrospira doubling time 12-32 h)
- [S] https://doaj.org/article/d3d472315d8f4b9b9ea017fac4b4f25a (mSphere 2019, new saltwater tanks, live rock and succession)
- [S] https://www.frontiersin.org/journals/microbiology/articles/10.3389/fmicb.2024.1355859/epub (marine RAS, AOA dominant, AOA and NOB covariation)

---

## 2. Heterotrophic bacteria (carbon-driven)

### 2.1 Growth and carbon dosing

- Heterotrophic bacteria use dissolved organic carbon (DOC) as energy and carbon. In reef water, DOC is usually 50-150 uM C (0.6-1.8 mg C/L) in mature tanks. [K] Carbon dosing raises this and drives heterotroph growth.
- Dosing options:
  - Vinegar (acetic acid): about 0.5-1 mL/day per 50 L as a starting point; supplies ~27 mg C per mL of 5% vinegar. [K]
  - Vodka (ethanol): about 0.5-1 mL per 50 L; decays and needs careful dosing. [K]
  - Propylene glycol, methanol, sugars, and commercial mixes (Brightwell NeoNitro, Red Sea Nitrate Factor, Tropic Marin Elimi-NP) are used similarly. [K]
- Carbon stoichiometry: 1 mg/L NO3-N removal needs roughly 4-7 mg/L of carbon if the carbon is fully used in denitrification (C:N ratio around 4-7 by mass for heterotrophic denitrification), but actual uptake is partly used for biomass. [K]
- Bacterial growth yield: about 0.3-0.5 g dry biomass per g of carbon consumed in aerobic growth; lower with anaerobic denitrification. [K]

### 2.2 Redfield and bacterial N:P uptake

- Redfield ratio for phytoplankton: C:N:P = 106:16:1 (mol:mol:mol). Redfield 1958. [K, classic reference]
- Bacterial C:N:P: a commonly cited ratio is 50:10:1 (mol). [S, from a reefs.com forum post; not verified in primary literature here]
- A second source puts heterotrophic uptake at 106:16:1 (same as Redfield). [S, Singapore reef guide; weak source]
- These conflict. For the model, use bacterial C:N:P = 50:10:1 for uptake by heterotrophs in carbon-replete water. Use 106:16:1 as the fallback for mixed communities. [K] Verify with a primary marine microbiology paper (e.g., Cotner and Biddanda marine bacterial stoichiometry literature).
- Interpretation: to grow bacteria on carbon alone, N and P must already be present at about 1/5 and 1/50 of carbon by mole. When N or P is limiting, carbon dosing can produce a bloom only until the nutrient is used up.
- Practical: a "good" carbon-dosed tank with NO3 low and PO4 low is nutrient-limited for heterotrophs. Carbon dosing then lowers NO3 and PO4 together, and the reduction slows when N or P is depleted.

### 2.3 Bacterial bloom (cloudy white water)

- Free-living bacterial counts above about 10^7 cells/mL give visible turbidity (white, milky water). [K] (Approximate threshold; visible cloudiness is also affected by particle size and colloids.)
- Bacterial blooms typically occur within 1-3 days of overdosing carbon or of a large ammonia spike. [K]
- Under-oxygenation: bloom bacteria consume O2 quickly. A bloom can drop O2 by 1-3 mg/L overnight in a tank with low surface agitation. [K]
- Recovery: blooms usually clear in 3-7 days once carbon input stops, as grazing by protists and bacterivores (ciliates, nanoflagellates, copepods) catches up. [K]

### 2.4 Oxygen demand

- Aerobic heterotrophic respiration: about 0.5-1.5 mg O2/L per day in a mature reef with moderate DOC, rising several-fold during carbon dosing. [K]
- Nitrification O2 demand: about 4.57 g O2 per g NH4-N oxidized (stoichiometric). [K]
- Model: O2 consumption rate = 3.4 mg O2 per mg of carbon oxidized (heterotrophic aerobic). [K] Use with care; in biofilm this is limited by diffusion.

### 2.5 Removal by skimmer

- Protein skimmers remove fine organic colloids and some bacteria. Bacterial removal by skimming is low for free-living bacteria because they are small and do not attach strongly to bubbles, but skimming removes DOC and attached particles. [K]
- Model choice: skimmer removes dissolved organics at k_skim = 0.02-0.1 per hour for DOC, and bacteria at k = 0.002-0.01 per hour. [K] Adjust to skimmer efficiency.

Sources:
- [S] https://www.reefs.com/forum/advanced-reefs/284211-redfield-ratio-revisited-what-we-doing-wrong.html (50:10:1 claim; unverified)
- [S] https://www.reefs.com/?p=208934 (carbon dosing hypothesis; authors not identified)
- [S] https://blog.marinedepot.com/2021/03/liquid-carbon-dosing-breakdown-why-and-how-to-do-it.html (dosing guidance, vendor)

---

## 3. Anaerobic and denitrifying bacteria (deep sand bed, rock interiors)

### 3.1 Denitrification

- Denitrification: NO3- -> NO2- -> NO -> N2O -> N2, by heterotrophic bacteria under anoxic conditions (O2 below about 0.1-0.2 mg/L). [K]
- Field data on reef sediments (Heron Island, carbonate sediments; bg preprint 2016-530): denitrification began at substantial rates only below about 10 uM O2 (about 0.3 mg O2/L). Rates stayed constant above 18 uM NO3-. Denitrification was seen at porewater O2 as high as 50 uM in some stirred reactors, which suggests anoxic microniches in grains. [S]
- Aquarium sand beds: a skeptical aquarium-science article argues deep sand beds do not produce meaningful denitrification or H2S, based on one author's testing. Opinion, not controlled study. [S]
- Hobbyist forums are split. Anecdotal. [S]
- A vendor guide claims 8-10 cm of fine sand can develop anoxic zones and gives sulfur denitrator rates of 5-50 ppm NO3 per day. No cited study; treat as marketing. [S]

Numbers for the model (no peer-reviewed aquarium value was found):
- Denitrification rate per m^2 of sand-bed area, when anoxic: 0.5-3 mmol N/m^2/d (about 7-40 mg N/m^2/d). [K] Reef and coastal sediments range from ~0.1 to 10 mmol N/m^2/d.
- Anoxic layer depth in a fine sand bed (about 0.2-0.5 mm grain size) at moderate flow: 0.5-3 cm. Deeper (5-10 cm) anoxia needs high organic load and low bioturbation. [K]
- Model choice: the sand bed in a mature tank with moderate organic input contributes about 0.5-1 mmol N/m^2/d of denitrification. At 0.3 m^2 footprint, that is about 0.15-0.3 mmol N/d, or about 2-4 mg NO3-N/d for a 227 L tank. That is small relative to a typical mature tank's total denitrification, which is dominated by rock interiors and detritus-rich zones. [K]

### 3.2 Rock interior anoxia

- Live rock interiors with low flow and high detritus can be anoxic within mm to cm. [S, forum claim; plausible.]
- Denitrification in rock may be a larger sink than sand. [K] Verify.

### 3.3 Sulfate reducers and H2S

- Sulfate-reducing bacteria (SRB, e.g., Desulfovibrio, Desulfobacter) use sulfate (about 28 mM in seawater) and produce H2S under anoxia. [K]
- H2S threshold concerns: H2S above about 0.02-0.05 mg/L is toxic to fish and corals; the rotten-egg smell detects it at about 0.01-0.05 mg/L. [K]
- Disturbance (stirring, vacuuming, moving rock) releases H2S and reduces O2. Anoxic sediment flushing produces a rapid spike, often 0.1-1 mg/L H2S in a sand bed of 5-10 cm depth. [K] Verify with source.
- Model: H2S release rate after disturbance = k_release * deep_sand_anoxic_volume, with k_release such that stirring a 5 cm deep sand bed produces 0.1-0.5 mg H2S/L over 1 hour in a 227 L tank. [K]

### 3.4 Anammox

- Anaerobic ammonium oxidation (anammox): NH4+ + NO2- -> N2. Found in marine sediments and some oxygen-minimum zones; growth is slow (doubling 10-30 days). [K]
- Reef aquarium relevance: low. Anammox needs anoxia and both NH4+ and NO2-; in a tank, NO2- is usually low. Include as a minor term (<5% of anaerobic N loss) or omit. [K]
- Model choice: omit or set anammox rate to 0 for the default. Enable it only for a deep sand bed scenario.

Sources:
- [S] https://bg.copernicus.org/preprints/bg-2016-530/bg-2016-530.pdf (Heron Island denitrification, O2 threshold ~10 uM)
- [S] https://aquariumscience.org/index.php/14-2-4-anaerobic-myth (deep sand bed skepticism; single-author opinion)
- [S] https://forums.reefcentral.com/threads/denitrification.1366026 (anecdotal; mixed)

---

## 4. Cyanobacteria (Oscillatoria, Lyngbya, Dichothrix)

### 4.1 Triggers

- Phosphate is the most consistent growth driver for marine filamentous cyanobacteria in the sources checked. [S] (Lyngbya majuscula thesis, Guam; Oscillatoria studies)
- Nitrate effects are inconsistent and depend on species. Several Lyngbya species show response to N enrichment; others are N-fixers and do not need NO3-. [S] (Moreton Bay MEPS study: N2 fixation in L. majuscula only in dark, incompatible with photosynthesis in same cell)
- Hobbyist triggers (not controlled): low NO3 with measurable PO4; high DOC; dead flow spots; high light with stagnation; late-day O2 low. [S, anecdotal]
- Dichothrix: a common reef-tank cyanobacteria; tends to show in low-flow areas and in tanks with high PO4. [K]
- Cyano growth in the Guam thesis: cyanobacteria took up most dissolved nutrients from the water column within 90 minutes. [S] This suggests cyano can outcompete other phototrophs for pulsed nutrients.

### 4.2 Growth rates and cover

- Doubling time for filamentous marine cyanobacteria: about 1-3 days under good light and nutrients. [K]
- Mat cover can go from 0 to 20-50% of exposed surfaces in 2-4 weeks under sustained PO4 above 0.1 mg P/L with low flow. [K] (Approximate.)
- Model: cyanobacteria growth rate mu_cy = 0.3-0.8 d^-1 * f(PO4) * f(light) * f(flow dead), with a half-saturation for PO4 around 0.02-0.05 mg P/L. [K]

### 4.3 Colour and look

- Red and purple: Oscillatoria, Lyngbya-type mats, Dichothrix; red-brown tufts or sheets.
- Green: some filamentous Chlorophyta, which are algae, not cyano; a microscope check helps.
- Blue-green sheets: Lyngbya and Oscillatoria.
- Black: Lyngbya and dead cyano.

Sources:
- [S] https://www.uog.edu/_resources/files/ml/theses/MLThesis_MilonovichW.L.pdf (Lyngbya majuscula nutrient response, Guam, 2003 thesis)
- [S] https://int-res.com/abstracts/meps/v281/p27-35 (Moreton Bay Lyngbya N2 fixation, MEPS 2004)

---

## 5. Pathogens

### 5.1 Vibrio spp.

- Vibrio harveyi: luminous vibriosis in marine fish and shrimp; optimal growth around 25-30 C. Opportunistic in stressed fish and larvae. [K]
- Vibrio alginolyticus: opportunistic, ear and wound infections, ulcers; grows in warm seawater and at lower salinity. [K]
- Vibrio coralliilyticus: coral pathogen; virulence increases with temperature. Infection of Pocillopora damicornis was induced by temperature rise, not at constant low temperature. [S] Some studies find V. coralliilyticus abundance rose by four orders of magnitude at 31 C in a natural community experiment. [S]
- Temperature threshold for coral infection: most reports at or above 28-31 C. [S] (Oculina patagonica aquarium experiments: bleaching at 31 C; antibiotic reduced bleaching.)
- Aquarium at 25-27 C: V. coralliilyticus risk is lower but not zero; a temperature spike above 28-29 C over days raises it. [K] Verify threshold with primary coral pathology papers.

Risk factors:
- Organic load (DOC, detritus, high bacterial counts), warm temperature spikes, stress, damaged tissue, low O2 at night. [K]

### 5.2 Aeromonas

- Mostly freshwater. Marine occurrence is rare and typically opportunistic, causing ulcers and fin rot in stressed fish. [K]

### 5.3 Mycobacterium marinum

- Waterborne, photochromogenic nontuberculous mycobacterium; infects >200 fish species across subarctic to tropical marine, brackish and freshwater. [S] (Review: Mycobacterium marinum infection in fish and man)
- Fish signs: granulomas (visible lumps on the body or internal), emaciation, skin ulcers, fin damage, abnormal swimming, loss of colour, chronic weight loss. [S/K]
- Human infection: "fish-tank granuloma," skin lesions after minor trauma from aquarium work. Sporotrichoid pattern in some cases; treatable with minocycline or clarithromycin-based regimens. [S, case series]
- Model: M. marinum is a chronic, low-prevalence risk; infection rate tied to stress and tank hygiene. Does not spread quickly across a tank.
- Antibiotics: slow to clear; often need extended treatment; environmental resistance noted in case reports. [S]

### 5.4 Tenacibaculum (fin rot, mouth rot, "yellow mouth")

- Tenacibaculum maritimum: marine fish pathogen causing tenacibaculosis. Signs: mouth erosion, skin ulcers, fin necrosis, tail rot. [S] (Mabrok et al. 2023 review)
- Also referred to as "yellow mouth" in some hobby sources. [S]
- Hosts include gilthead sea bream, European sea bass, lumpsucker. [S]
- Risk: biofilm formation, antibiotic resistance, poor water quality. [S]
- Model: fish disease risk term includes T. maritimum-like risk increasing with DOC, nitrite spikes, and skin damage.

### 5.5 Bacterial coral diseases

- White syndromes (WS-type): often associated with Vibrio and other bacteria; signs include tissue loss from a lesion front. [K]
- Rapid tissue necrosis (RTN) and Stony coral tissue necrosis (STN): caused by a mix of bacteria and possibly other organisms. Etiology not fully resolved. [K]
- Brown jelly disease: the dominant hypothesis is a ciliate (ciliate-associated), not a bacterium. Name and genus are uncertain here. [K]
- Model: coral disease risk is a composite. Use bacteria-associated risk factors (organic load, temperature spike, low O2 at night, tissue damage) and separate ciliate or other terms as options.

Sources:
- [S] https://pmc.ncbi.nlm.nih.gov/articles/PMC9853564 (tenacibaculosis review, Mabrok et al. 2023)
- [S] https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6831007/ (Mycobacterium marinum review)
- [S] https://cob.silverchair.com/jeb/article-pdf/214/9/1533/1279286/1533.pdf (Oculina patagonica, aquarium heating, bleaching; Vibrio role)
- [S] https://www.nature.com/articles/ismej201433 (Vibrio coralliilyticus temperature and virulence; ISME J)

---

## 6. Bottled bacteria products and probiotics

### 6.1 Commercial products

- Dr Tim's (Waste-Away, various): Nitrosomonas and Nitrobacter-type claims. No independent peer-reviewed validation found in this session. [S]
- Fritz Turbostart: marketed as rapid nitrifier startup. No independent peer-reviewed validation found. [S, not checked]
- MicroBacter7: multi-strain nitrifier product, marketed for marine. No independent peer-reviewed validation found. [S, not checked]
- Bio-Spira: marketed as freshwater and marine nitrifier inoculant; claims of a shortened cycle. No independent peer-reviewed validation found. [S, not checked]
- Key limitation: product counts in labels are seldom validated. Viability in shipment is uncertain. Establishment depends on food and conditions. [K]

Evidence summary:
- Bottled bacteria can speed cycling in some reports, but controlled comparisons are rare. [K]
- Rock and sand already contain nitrifiers. A cycle with live rock is typically faster than dry rock. [S, mSphere 2019 suggests live rock shifts communities, not that products are proven.]
- Model choice: a bacteria dose adds nitrifier biomass with a probability of establishment that depends on food (DOC), temperature, and O2. Give product dose a lower effective start value than the label count.

### 6.2 Probiotics

- Pseudoalteromonas McH1-7: coral probiotic with antibacterial activity against SCTLD-associated bacteria. Produces korormicin and tetrabromopyrrole. Slowed or arrested disease in lab trials; detectable in aquarium water for at least 1 day after dosing; no bloom of the probiotic strain, but establishment after repeated dosing possible. [S] (Chemical and genomic characterization study)
- Ruegeria: mixed results. Some strains beneficial; in one Great Barrier Reef study, a Ruegeria candidate caused tissue loss in juvenile Acropora and was dropped. [S]
- Red Sea probiotic study: treatment increased beneficial taxa (Ruegeria, Limosilactobacillus) and decreased Vibrio. [S]
- Off-target effects: Red Sea study found no detectable off-target effect on surrounding seawater and sediment microbiomes at the scale studied. [S]
- Model choice: probiotic dose adds a beneficial bacteria pool that competes with Vibrio (competition term). Strength is low to moderate; not a guaranteed cure.

### 6.3 UV sterilizers

- UV-C at 254 nm: free-living bacteria are killed at doses around 20-40 mJ/cm^2 for >90% kill (reef UV units are often marginal at high flow). [K]
- Effect in a reef tank: UV can lower free-living bacterial counts by 50-90% at moderate flow, but biofilms on rock and sand are not affected. [K]
- Effect on nitrifiers: free-living nitrifiers in water are reduced, but rock biofilm nitrifiers remain. [K]
- Effect on the bacterial bloom: UV can shorten a bloom, but the bloom usually resumes when carbon or nutrients stay high. [K]
- Model: UV on removes free-living bacteria at k_UV = 0.5-2 per hour at 25-30 W for a 227 L tank with a typical flow rate; biofilm not affected.

Sources:
- [S] https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10079959/ (Pseudoalteromonas McH1-7 chemistry and aquarium trial)
- [S] https://researchonline.jcu.edu.au/87907/ (juvenile Acropora kenti, probiotic candidates including Ruegeria)
- [S] https://marinemicrobiomeslab.kaust.edu.sa/publications/detail/probiotics-reshape-the-coral-microbiome-in-situ-without-detectable-off-target-effects-in-the-surrounding-environment (Red Sea probiotic, no off-target effects)

---

## 7. Typical counts and microscope appearance

### 7.1 Free-living bacteria

- Open-ocean oligotrophic seawater: about 10^5 cells/mL (0.1 million per mL). [K]
- Coastal and reef seawater: about 10^5-10^6 cells/mL. [K]
- Mature reef aquaria: about 10^5-10^6 cells/mL typical; 10^6-10^7 cells/mL after carbon dosing or stress. [K]
- Bloom (cloudy water): above about 10^7 cells/mL. [K]
- Counting method: flow cytometry with SYBR Green I (BCO-DMO / Carlson protocol). [S]
- Reef field data: Xisha Islands, South China Sea (2022) used flow cytometry; Prochlorococcus about 5.8 x 10^2 to 5.44 x 10^3 cells/mL. This is picoplankton, not total bacteria. [S]
- Reef field datasets: USVI heterotrophic bacteria, flow cytometry, 1 mL samples at 2 m above reef. Numeric values not retrieved. [S]

### 7.2 Biofilm

- Biofilm cells on surfaces: about 10^8-10^10 cells/cm^2 in biofilms on rock and glass in mature systems. [K]
- Biofilm thickness on glass: 10-200 um in a mature tank; visible as a film or slime. [K]
- Biofilm on sand grains: 10^7-10^9 cells per cm^3 sediment in the top few mm. [K]
- Model: biofilm volume per m^2 of surface ~ 0.02-0.2 mm equivalent thickness.

### 7.3 Microscope appearance at 400-1000x

- Rods: most common free-living bacteria in water; 0.5-1.5 um long. Look like small dashes. [K]
- Cocci: round, 0.5-1 um; often in pairs or chains. [K]
- Spirilla and vibrios: curved rods, Vibrio are comma-shaped. [K]
- Filaments: cyanobacteria (Oscillatoria, Lyngbya) appear as long chains of cells with visible cross-walls, often 5-10 um wide; they move with a gliding motion. [K]
- Colonies: aggregates of bacteria in flocs; stained or unstained.
- Protists and grazers (nanoflagellates, ciliates) appear larger and move actively. [K]

### 7.4 Colour clues

- Red and purple mats: Oscillatoria, Lyngbya, Dichothrix; check microscope for filaments.
- Biofilm on glass: thin brown, grey or green film; slimy. Diatoms appear brown; green algae appear green.
- White bacterial bloom: milky white; no filaments.

Sources:
- [S] https://www.bco-dmo.org/parameter/1973 (BCO-DMO flow cytometry parameter; methods)
- [S] https://jouc.xml-journal.net/article/doi/10.1007/s11802-022-5107-y (Xisha Islands picoplankton flow cytometry, 2022)

---

## 8. Summary of open gaps

- No peer-reviewed measurement of denitrification per m^2 in an aquarium sand bed was found. Use the range in 3.1 and calibrate.
- No measured Nitrosopumilus doubling time in a marine aquarium was found. Use the literature range.
- Carbon:nitrogen:phosphate uptake ratios (50:10:1 vs 106:16:1) conflict. Verify in primary marine microbiology.
- No independent validation found for bottled bacteria products.
- Vibrio coralliilyticus threshold at 27 C is not established.
- Cyanobacteria reef-specific thresholds are not established.

---

## Implementable model

### A. State variables

Each row: name, units, range, starting value for mature tank, starting value for new tank.

| Variable | Symbol | Units | Range | Mature start | New tank start |
|---|---|---|---|---|---|
| AOB biomass (rock + sand, surface-scaled) | B_AOB | mg biomass C per m^2 surface | 0 to 50 | 20 | 0.5 (dry rock) / 5 (live rock) |
| NOB biomass | B_NOB | mg C per m^2 surface | 0 to 50 | 15 | 0.2 (dry) / 3 (live) |
| AOA biomass | B_AOA | mg C per m^2 surface | 0 to 5 | 0.2 | 0.05 |
| Heterotroph (free-living) | B_het_w | cells/mL | 1e4 to 1e8 | 5e5 | 1e5 |
| Heterotroph (biofilm) | B_het_b | cells per cm^2 | 1e7 to 1e10 | 3e9 | 1e8 |
| Anaerobic denitrifier pool (sand + rock interior) | B_den | mg C per m^2 bed | 0 to 100 | 20 | 1 |
| SRB pool | B_SRB | mg C per m^2 bed | 0 to 20 | 2 | 0.1 |
| Cyanobacteria cover | A_cy | fraction of exposed surface | 0 to 1 | 0.02 | 0.00 (until PO4 rises) |
| Pathogen load (Vibrio-type) | B_vib | cells/mL | 1e2 to 1e6 | 1e3 | 1e3 |
| Beneficial probiotic pool | B_prob | cells/mL | 0 to 1e6 | 0 | 0 (unless dosed) |
| Free-living bacterial count (total) | N_w | cells/mL | 1e5 to 1e7 | 5e5 | 2e5 |
| Biofilm thickness equivalent | h_b | mm | 0 to 0.2 | 0.05 | 0.01 |

Note: the existing sim already holds nitrifier biomass as a 0..1 logistic. Map that to the surface-normalized biomass by a fixed scale. The mapping ratio is a model choice, not a measured fact.

### B. Equations and rates

Nitrification (AOB, NOB), Monod with inhibition:

- r_AOB = mu_AOB * B_AOB * (NH3 / (K_NH3 + NH3)) * (O2 / (K_O2a + O2)) * f_T * f_pH * f_inh
- r_NOB = mu_NOB * B_NOB * (NO2 / (K_NO2 + NO2)) * (O2 / (K_O2n + O2)) * f_T * f_pH * f_inh
- Parameters at 26 C:
  - mu_AOB = 0.7 d^-1, K_NH3 = 0.05 mg NH3-N/L, K_O2a = 0.3 mg/L
  - mu_NOB = 0.5 d^-1, K_NO2 = 0.1 mg NO2-N/L, K_O2n = 0.7 mg/L
  - Yield: Y_AOB = 0.15 g C per g NH4-N, Y_NOB = 0.05 g C per g NO2-N
- Temperature: f_T = Q10^((T - 26)/10) with Q10 = 2.2, capped at 1.0 above 30 C and reduced to 0 at 36 C.
- pH: f_pH = 1 / (1 + 10^(7.2 - pH)) (drops below pH 7.2).
- Inhibition: f_inh = 1 - C_inh / (C_inh + Kinh) for antibiotic or copper, with Kinh = 0.5 mg/L for erythromycin and 0.1 mg/L Cu (model choice).
- Stoichiometric pulls: dNH4/dt -= r_AOB * (1 / Y_AOB); dNO2/dt = r_AOB - r_NOB; dNO3/dt += r_NOB; dAlk/dt -= 2 * r_AOB * (50 / 14) (mg CaCO3 per L per mg NH4-N); dO2/dt -= 4.57 * r_AOB + 1.14 * r_NOB.

Heterotrophy (free-living and biofilm):

- r_het = mu_het * B_het_w * DOC / (K_DOC + DOC) * (NH4 + NO3 / K_N) * f_T * (O2 / (K_O2h + O2))
- mu_het = 2.0 d^-1 at 26 C (doubling about 8 h; slower than bulk culture). K_DOC = 0.1 mg C/L, K_O2h = 0.2 mg/L.
- Nutrient limitation uses min(N, P * 5) term with Redfield-like 50:10:1 bacterial stoichiometry, so bacteria grow only where N and P are both available.
- Uptake: dNO3/dt -= r_het * (10/50) ... applied as uptake for biomass (stoichiometric pull).
- Carbon dose: DOC += dose_C (mg C per L), with dose_C = 1-5 mg C/L per day in a managed tank (vinegar at ~27 mg C per mL of 5%, so 0.2-1 mL per day per 227 L); decays with first-order k_DOC = 0.05-0.2 per day.

Denitrification (anaerobic zones):

- r_den = mu_den * B_den * (NO3 / (K_NO3 + NO3)) * (K_O2d / (K_O2d + O2)) * f_T
- mu_den = 0.3 d^-1, K_NO3 = 0.2 mg NO3-N/L, K_O2d = 0.1 mg O2/L.
- Denitrification per m^2 of sand: 0.5-1 mmol N/m^2/d (mature), scaled by anoxic fraction.
- Sulfate reduction: when O2 < 0.1 mg/L and NO3 < 0.1 mg/L, r_SRB = mu_SRB * B_SRB * (SO4 / (K_SO4 + SO4)) * f_T with mu_SRB = 0.1 d^-1; H2S production = r_SRB * 1.0 (mol per mol SO4 reduced; convert units).
- Disturbance: H2S release = k_release * Anoxic_volume * (time since last disturbance)^-0 for a single event; k_release such that a 5 cm anoxic sand bed raises H2S by 0.1-0.5 mg/L in 1 h.

Cyanobacteria:

- dA_cy/dt = mu_cy * A_cy * (1 - A_cy) * PO4 / (K_PO4 + PO4) * f_light * f_flow - m_cy * A_cy
- mu_cy = 0.5 d^-1; K_PO4 = 0.03 mg P/L; f_flow = 1 at dead flow, lower at high flow (e.g., 1 - flow_norm); m_cy = 0.05 d^-1 with flow or grazing.
- NO3 effect: none in the default (N-fixation capability). Add an optional NO3 term for Oscillatoria-type.

Pathogens (Vibrio-type):

- dB_vib/dt = mu_vib * B_vib * f_T_vib * DOC_factor * (1 - B_vib / K_vib) - k_comp * B_prob * B_vib - k_die * B_vib
- mu_vib = 0.5 d^-1 at 28 C; f_T_vib = 1 at 28-31 C, reduced at 25-27 C (e.g., 0.3 at 25 C); DOC_factor = 1 + DOC / DOC_ref.
- k_comp = 0.001 per (cell/mL * d) (probiotic competition), k_die = 0.1 d^-1.

Free-living bacterial count:

- dN_w/dt = sum of growth - grazing - UV - skimmer - settling, with:
  - UV: - k_UV * N_w * UV_on (k_UV = 0.5-2 per h).
  - Skimmer: - k_skim_b * N_w (k = 0.002-0.01 per h).
  - Grazing: - g * N_w * protist_term (g = 0.1-0.5 per d; model choice).
  - Settling to biofilm: - s * N_w (s = 0.1-0.5 per d).

Probiotic pool:

- dB_prob/dt = mu_prob * B_prob * (1 - B_prob / K_prob) - k_die_prob * B_prob + dose_prob
- mu_prob = 0.5 d^-1; K_prob = 1e6 cells/mL; k_die_prob = 0.2 d^-1; dose_prob added on event (e.g., 1e8 cells per dose).

Fish disease risk coupling:

- Risk_disease = base + a * max(0, NO2 - 0.1) + b * H2S + c * DOC_excess + d * (T - 28) + e * B_vib + f * (1 - O2 / O2_ref) + g * stress
- Use a logistic: P_disease = 1 / (1 + exp(-(Risk - R0))). Set R0 so that a mature tank with stable parameters gives P_disease < 0.02 per month.
- Mycobacterium marinum: slow chronic risk term, weight 0.001 per month, rising with stress.

Coral health coupling:

- Coral_health reduction from B_vib above threshold 1e4 cells/mL and T above 28 C.
- Coral_health reduction from H2S above 0.02 mg/L.
- Coral_health reduction from cyano cover above 0.1 (smothering).

### C. Couplings to existing variables

| Existing variable | Coupled through | Direction |
|---|---|---|
| NH3 / NH4 | AOB uptake, het uptake | sink |
| NO2 | AOB production, NOB uptake | source/sink |
| NO3 | NOB production, denitrification, cyano (optional), het uptake | source/sink |
| PO4 | cyano growth, het uptake, probiotic indirect | sink |
| DOC | het growth, Vibrio growth, denitrifier substrate | source/sink |
| O2 | nitrification, het respiration, denitrification switch, SRB switch | sink |
| Temperature | all rates via Q10 and f_T; Vibrio threshold | input |
| pH / alkalinity | AOB pH factor; alkalinity consumed by nitrification | input/sink |
| Fish disease risk | NO2, H2S, DOC, Vibrio, O2, temperature, stress | output |
| Coral health | Vibrio, H2S, cyano cover, temperature | output |

### D. Sandbox actions

1. Dose bacteria (nitrifier product): add B_AOB and B_NOB by a dose amount (e.g., 1e10 cells equivalent). Establishment probability = 0.3 at low DOC, 0.6 with food; model as random draw with a fixed seed in the sim.
2. Carbon dose: DOC += dose_C (default 2 mg C/L per day when active), with max 5 mg C/L per day guard. Watch O2 and free-living count.
3. Antibiotic (e.g., erythromycin, cipro): sets a temporary inhibition on AOB (cipro for NOB) with half-life 1-3 days; kills a fraction of free-living bacteria; gives a recovery time.
4. UV sterilizer on/off: sets k_UV in N_w equation; biofilm unaffected.
5. Stir sand bed: sets H2S release event; increases O2 in sand bed temporarily; reduces anoxic fraction.
6. Probiotic dose: adds B_prob pool with competition term.
7. Copper dose: sets Cu concentration with inhibition on AOB/NOB.
8. Skimmer change: changes k_skim for DOC and bacteria.

### E. Acceptance checks

1. New tank cycle: starting from dry rock, B_AOB and B_NOB must reach 50% of mature values within 4-6 weeks at 26 C with a steady NH4 source (e.g., 0.5 mg NH4-N/L per day from fish food). NO2 peak should be around 2-3 weeks, falling to <0.1 mg/L by week 6.
2. Live rock cycle: with live rock start, cycle completes in 2-4 weeks.
3. Carbon dosing: at DOC dose 2 mg C/L per day with NO3 and PO4 present, NO3 should fall by about 0.5-1.5 mg NO3-N/L per week in a tank with moderate denitrifier biomass, slowing as NO3 falls or PO4 limits.
4. Overdosing check: DOC dose above 5 mg C/L per day for 5 days should produce a free-living count above 1e7 cells/mL and O2 drop of at least 1 mg/L overnight.
5. Deep sand disturbance: stirring a 5 cm sand bed with anoxic zone raises H2S by 0.1-0.5 mg/L within 1 h; a shallow 2 cm bed raises it by less than 0.05 mg/L.
6. UV: UV on for 24 h reduces free-living count by 50-90%, leaves biofilm and nitrification rate change under 10%.
7. Temperature: raising T from 26 to 29 C for 3 days with DOC above 0.5 mg/L increases B_vib by at least 10x and raises coral stress; holding at 26 C keeps B_vib below 1e4.
8. Cyanobacteria: PO4 at 0.1 mg P/L with NO3 at 0.5 mg N/L and dead flow spots should show A_cy growth from 0.02 to above 0.1 within 3-4 weeks.
9. Mass balance: total N (NH4 + NO2 + NO3 + biomass N) stays within 5% per week after accounting for exports and water change input.
10. Disease risk: a stable mature tank with DOC below 1 mg/L and NO2 below 0.05 mg/L yields P_disease below 0.02 per month.

### F. Recommended defaults for the sim

- Use the "mature start" column for a fully cycled reef.
- Use the "new tank start" column with dry rock to test cycle timing.
- Put Vibrio and M. marinum behind an explicit flag; default off unless the user adds a stress event.
- Keep anammox and comammox off for marine default.
- Report each parameter as [S] or [K] in UI tooltips so users know what is sourced.

---

## Sources (consolidated)

Checked in this session ([S]):
- https://microbewiki.kenyon.edu/index.php/Nitrospira (Nitrospira doubling time 12-32 h)
- https://castjournals.cast.org.cn/joweb/aos/EN/PDF/10.1007/s13131-017-1092-1 (live rock, Chinese coral aquaria study)
- https://doaj.org/article/d3d472315d8f4b9b9ea017fac4b4f25a (mSphere 2019, new saltwater tanks)
- https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11267875/ (comammox, aquarium biofilters, 2024)
- https://www.frontiersin.org/journals/microbiology/articles/10.3389/fmicb.2024.1355859/epub (marine RAS nitrifiers)
- https://bg.copernicus.org/preprints/bg-2016-530/bg-2016-530.pdf (Heron Island denitrification; preprint)
- https://aquariumscience.org/index.php/14-2-4-anaerobic-myth (deep sand bed opinion)
- https://forums.reefcentral.com/threads/denitrification.1366026 (anecdotal)
- https://www.reefs.com/forum/advanced-reefs/284211-redfield-ratio-revisited-what-we-doing-wrong.html (50:10:1 claim; unverified)
- https://www.reefs.com/?p=208934 (carbon dosing hypothesis)
- https://blog.marinedepot.com/2021/03/liquid-carbon-dosing-breakdown-why-and-how-to-do-it.html (vendor)
- https://www.uog.edu/_resources/files/ml/theses/MLThesis_MilonovichW.L.pdf (Lyngbya thesis)
- https://int-res.com/abstracts/meps/v281/p27-35 (Lyngbya majuscula N2 fixation, MEPS 2004)
- https://pmc.ncbi.nlm.nih.gov/articles/PMC9853564 (tenacibaculosis review, 2023)
- https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6831007/ (Mycobacterium marinum review)
- https://cob.silverchair.com/jeb/article-pdf/214/9/1533/1279286/1533.pdf (Oculina patagonica aquarium heating)
- https://www.nature.com/articles/ismej201433 (Vibrio coralliilyticus, ISME J)
- https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10079959/ (Pseudoalteromonas McH1-7)
- https://researchonline.jcu.edu.au/87907/ (Acropora kenti probiotic candidates)
- https://marinemicrobiomeslab.kaust.edu.sa/publications/detail/probiotics-reshape-the-coral-microbiome-in-situ-without-detectable-off-target-effects-in-the-surrounding-environment (Red Sea probiotics)
- https://www.bco-dmo.org/parameter/1973 (flow cytometry method)
- https://jouc.xml-journal.net/article/doi/10.1007/s11802-022-5107-y (Xisha Islands picoplankton, 2022)

Background ([K], not checked in this session; verify before publication):
- Redfield, A.C. (1958). The biological control of chemical factors in the environment. American Scientist 46:205-221.
- Martens-Habbena, W. et al. (2009). Ammonia oxidation by marine archaea. Nature 461:976-979.
- Konneke, M. et al. (2005). Isolation of an autotrophic ammonia-oxidizing marine archaeon. Nature 437:543-546.
- Carlson, C. (2017). BIOS flow cytometry protocol (BCO-DMO dataset documentation).

## Caveats

- This file is a research summary with explicit gaps. Numbers marked [K] are estimates or recalled knowledge and must be verified before they are presented as facts in the app.
- The sim should show a "source confidence" tag on each parameter.
- Links were taken from search results; they were not all opened and read in full.
