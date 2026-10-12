# Reef Care Reference: 60 gal mixed soft-coral reef (Android tracker app)

Scope: water parameters, chemistry relationships, maintenance math, livestock health, app feature list, AI Prime HD notes.

**Source legend.** `[V]` = read in a fetched source during this research (URL given). `[K]` = established background knowledge or standard hobby consensus, not re-verified in this session; treat as a default to confirm against the cited primary source before hard-coding in the app. Forum-level numbers vary by 10 to 20 percent between keepers; the app should let users override targets.

Verified sources used:
- `[V]` Charterhouse Aquatics, parameters/test-frequency guide (fetched, body truncated, no numbers extracted): https://charterhouse-aquatics.com/blogs/help-guides/what-should-you-test-for-in-a-reef-aquarium-the-key-parameters-and-frequency
- `[V]` Gensou, NO3:PO4 / Redfield guide: https://gensou.sg/?p=41037
- `[V]` Manta Systems, Marine Ich: https://www.mantasystems.net/a/blog/post/MarineIch
- `[V]` Manta Systems, Hyposalinity: https://www.mantasystems.net/a/blog/post/Hyposalinity
- `[V]` aqion.de free NH3 example (no equations on page): https://aqion.de/sd/108

Not reachable or not usable: AquaIllumination product page (fetch error), Emerson et al. 1975 equation search (returned only R package pages).

---

## 1. Water parameters: targets, units, thresholds

### 1.1 Master table (60 gal, SPS or soft coral)

| Parameter | Unit | FOWLR ideal | Soft coral ideal | SPS ideal | Acceptable | Danger / act now | Test frequency | Common kits | Max safe change |
|---|---|---|---|---|---|---|---|---|---|
| Temperature | °F (°C) | 76 to 79 (24.4 to 26.1) | 76 to 78 (24.4 to 25.6) | 76 to 78 | 74 to 80 | <72 or >84 (<22.2 or >28.9) | Continuous (controller/probe) | Digital probe, Apex/Neptune | 1 °F/hr; ~1 °F/day safe |
| Salinity | SG at 25 °C | 1.024 to 1.026 | 1.024 to 1.026 | 1.025 to 1.026 | 1.023 to 1.027 | <1.020 or >1.029 | Weekly; daily after top-off issues | Refractometer (ATC), hydrometer, conductivity meter | 0.001 SG/day (0.002 max) |
| Salinity | ppt | ~33 to 35 | ~34 to 35 | ~35 | 32 to 36 | <30 or >38 | Same | Convert via temp-corrected table | 1 ppt/day |
| Salinity | mS/cm (conductivity) | ~52 to 54 at 25 °C | Same | Same | ~50 to 56 | Out of range by 5 | Continuous (EC probe) | Apex/Neptune EC | Same |
| pH | pH units (NBS) | 8.1 to 8.3 | 8.1 to 8.4 | 8.1 to 8.4 | 7.9 to 8.5 | <7.8 or >8.6 | Weekly; continuous if controller | Hanna, Apex, Milwaukee | 0.2 pH/day |
| Alkalinity | dKH | 7 to 9 | 8 to 9 | 8 to 9.5 | 7 to 11 | <6 or >12 | 2 to 3x/week while dosing; else weekly | Salifert (titration), Hanna Checker, Red Sea Reef Foundation | 0.5 to 1.0 dKH/day |
| Alkalinity | meq/L | 2.5 to 3.2 | 2.9 to 3.2 | 2.9 to 3.4 | 2.5 to 3.9 | <2.1 or >4.3 | Same | Same | 0.2 to 0.4 meq/L/day |
| Alkalinity | ppm CaCO3 | 125 to 160 | 143 to 160 | 143 to 170 | 125 to 195 | <107 or >214 | Same | Same | 18 to 36 ppm/day |
| Calcium | ppm | 400 to 420 | 400 to 440 | 420 to 450 | 380 to 460 | <350 or >500 | Weekly; 2x/week when dosing | Salifert (titration), Hanna Checker, Red Sea Reef Foundation, ICP | 10 to 20 ppm/day |
| Magnesium | ppm | 1250 to 1350 | 1250 to 1350 | 1280 to 1380 | 1200 to 1400 | <1100 or >1500 | Weekly to monthly; Mg is slow-moving | Salifert, Red Sea, Hanna; ICP | 25 to 50 ppm/day |
| Ammonia | ppm NH3/NH4+ | 0 | 0 | 0 | 0 (detectable = action) | >0.25 ppm (NH3 >0.02 ppm kills fish) | Only in cycling, after events, in trouble | Salifert, API Marine, Hanna | Not applicable; treat |
| Nitrite | ppm NO2- | 0 | 0 | 0 | 0 | >0.25 | Same | Salifert, API Marine, Hanna | Not applicable |
| Nitrate | ppm NO3- | 5 to 20 | 3 to 15 | 2 to 8 (some SPS to 10) | 2 to 25 | <1 (STN/dinos risk) or >25 | Weekly | Salifert, Hanna, Red Sea, API, Hach | 2 to 5 ppm/day (dilution-driven) |
| Phosphate | ppm PO4 3- | 0.03 to 0.10 | 0.02 to 0.06 | 0.02 to 0.05 | up to 0.10 | >0.15 (hair/cyano; slowed SPS), <0.02 with high NO3 is a red flag | Weekly | Salifert, Hanna Low Range, Red Sea, Hach | 0.02 ppm/day |
| Potassium | ppm K+ | 380 to 420 | 380 to 420 | 380 to 420 | 360 to 440 | <340 or >460 | Monthly | Salifert K, Red Sea | 10 ppm/day |
| Strontium | ppm Sr | 8 to 10 | 8 to 9 | 8 to 10 | 7 to 11 | <6 | Monthly to quarterly | Salifert Sr, Red Sea, ICP | 0.5 ppm/day |
| Iodine | ppm I | 0.05 to 0.06 | 0.05 to 0.06 | 0.05 to 0.06 | 0.04 to 0.08 | >0.1 | Quarterly | Salifert I, ICP | Not sharply defined |
| Iron | ppm Fe | 0.001 to 0.005 | 0.001 to 0.005 | 0.001 to 0.005 | 0 to 0.01 | >0.02 (algae driver) | Yearly | ICP; Hanna iron | N/A |
| Silicate | ppm SiO2 | 0 to 0.5 | 0 to 0.5 | 0 to 0.5 | <1 | >1 (diatoms) | Monthly; during diatom bloom | Hanna silica, Red Sea silicate, ICP | N/A |
| ORP | mV | 325 to 375 (typical; ICP-regulated) | 325 to 375 | 340 to 380 | 300 to 400 | <250 or >420 (calibration check) | Continuous or weekly | Probe, Apex/Neptune (calibrate ORP monthly) | N/A |
| Dissolved O2 | mg/L | 6.5 to 7.0 (at 25 °C, 35 ppt) | 6.5 | 6.5 | >5.5 | <5 (corals stress); <4 fish distress | Continuous; spot check | Optical DO probe | N/A |
| TDS RO/DI | ppm | 0 | 0 | 0 | 0 to 5 | >10 (change resin/membrane) | Each batch | TDS pen, inline meter | N/A |

Notes:
- Salinity conversions: SG 1.025 at 25 °C ≈ 35 ppt. Use a temperature-corrected refractometer; the ATC is calibrated to 25 °C. `[K]`
- Ranges above are a consensus of common reef guidance; the Charterhouse guide `[V]` confirms the parameter list and test priorities but its body was truncated in this session.
- Nitrate/phosphate ideal ranges: see section 2.2 for the ratio relationship `[V]` (Gensou).

### 1.2 Unit conversions (for the app)

| From | To | Factor | Notes |
|---|---|---|---|
| dKH | ppm CaCO3 | ×17.9 | `[K]` |
| ppm CaCO3 | dKH | ÷17.9 | `[K]` |
| dKH | meq/L | ×0.357 | `[K]` |
| meq/L | dKH | ×2.8 | `[K]` |
| ppm CaCO3 | meq/L | ÷50 | `[K]` |
| meq/L | ppm CaCO3 | ×50 | `[K]` |
| °F | °C | (°F − 32) × 5/9 | exact |
| °C | °F | °C × 9/5 + 32 | exact |
| ppm | ppb | ×1000 | `[K]` |
| ppm | mg/L | 1:1 for dilute aqueous solutions | `[K]` |
| SG (25 °C) | ppt | ~ ppt ≈ 1.0 × (SG − 1.000) × 1000 × 1.4 (approx.; use tables) | Approximate; prefer temp-corrected table `[K]` |
| ppt | mS/cm | ~1.5 mS/cm per ppt near 35 ppt, 25 °C | `[K]` |
| ppm elemental | mmol/L | Ca: ÷40.08; Mg: ÷24.31; K: ÷39.10; Sr: ÷87.62 | `[K]` |
| meq/L Ca | ppm Ca | ×20.04 | `[K]` |
| meq/L Mg | ppm Mg | ×12.15 | `[K]` |
| PO4 ppm (as PO4) | PO4-P ppm | ×0.326 | P is 31/95 of PO4 `[K]` |
| NO3 ppm (as NO3) | NO3-N ppm | ×0.226 | `[K]` |
| NO3-N | NO3 | ×4.43 | `[K]` |

### 1.3 Parameter descriptions for the app

- **Temperature:** Warm water holds less O2 and speeds bacterial and parasite cycles (ich tomont 3 to 10 days warm vs 3 to 28 cool; `[V]` Manta Ich).
- **Salinity:** Track SG and evaporation; top-off RO/DI is salt-free so SG rises between top-offs. Daily trend chart is more useful than the point value.
- **pH:** Daily swing 0.2 to 0.3 is normal; large swings suggest CO2/alk or flow issues.
- **Alkalinity:** The most important dosed parameter. Consumption rate is the key derived metric (dKH/day).
- **Calcium/Mg:** Track Ca:Mg:alk balance (section 2.1).
- **Nitrogen series, phosphate:** See section 2.2.
- **Trace elements:** Strontium, iodine, potassium: monthly or quarterly; ICP results most useful at 1 to 2 per year.

### 1.4 Testing kits (what is commonly used)

| Brand | Typical parameters | Notes |
|---|---|---|
| Salifert | Alk, Ca, Mg, NO3, PO4, K, Sr, I, Fe | Titration (drop count); widely used; alk and Ca titrations are high reference points on forums `[K]` |
| Hanna Checker / Photometer | pH, alk, Ca, Mg, NO3, PO4, silica, Fe | Photometric; faster; calibration sensitive; ~5 to 10% spread from titrations is common `[K]` |
| Red Sea Reef Foundation / Pro | Alk, Ca, Mg, NO3, PO4, K, Sr, I | Drop/colorimetric; user-friendly |
| API Marine | Ammonia, nitrite, nitrate, pH, alk, Ca | Mostly for cycling and quick checks; lower precision |
| Hach | Lab-grade NO3, PO4, Si | Precise, expensive |
| ICP-OES (Triton, Ocean Riots?) | Full panel: 40+ elements | 1 to 2 times/year; use as calibration for home kits `[K]` |

Forum consensus on conflict: Salifert and Hanna can differ on Ca and Mg by 5 to 10%; pick one kit per parameter and log the brand in the app (test-method metadata). `[K]`

### 1.5 Test frequency

| Frequency | Parameters |
|---|---|
| Continuous | Temp, salinity (EC), ORP, pH (probe, if controller), DO (optional) |
| Daily or 2 to 3x/week | Alk during dosing and during trouble; Ca when dosing |
| Weekly | pH, alk, Ca, Mg, NO3, PO4 |
| Monthly | K, Sr, Fe (optional), silicate during algae events |
| Quarterly | I, Sr adjust |
| Yearly | ICP full panel |
| Event-based | Ammonia, nitrite (after death, failed filter, new fish, cycling) |

---

## 2. Relationships and formulas

### 2.1 Alk, Ca, Mg balance

- Coral skeleton needs Ca and carbonate (alk). Alk is measured as carbonate alkalinity in dKH or meq/L.
- Mg stabilizes Ca and alk. Low Mg causes Ca precipitation during dosing (white deposits, CaCO3 precipitation). Keep Mg at roughly 3 times Ca in ppm terms, or 3:1 mole ratio. `[K]`
- Ca:Mg molar ratio at seawater ~1:3.2; practical target Mg/Ca ≈ 3.1 to 3.4 in ppm terms (1280/420 ≈ 3.05). `[K]`
- Mixed-dosing rule: dose alk and Ca separately (not together) with at least 30 to 60 minutes between dosing events. `[K]`
- Alk/Ca balance in practice: A Ca:alk "consumption ratio" of about 1 ppm Ca per 0.02 to 0.03 dKH is typical for LPS/SPS. Use the app to derive this from the user's own log (consumption ratio). `[K]`

### 2.2 Nitrate:phosphate ratio (Redfield)

- Redfield seawater N:P ratio 16:1 (atomic). In reef NO3:PO4 mass terms this is ~100:1 `[V]` (Gensou).
- Gensou targets: nitrate 3 to 8 ppm and phosphate 0.03 to 0.08 ppm, which lands near 100:1 `[V]`.
- Gensou reports: NO3 below 1 ppm (with PO4 below 0.02) linked to dinoflagellates, STN, pale-out; NO3 above 25 ppm linked to brown tint and algae; PO4 above 0.15 ppm linked to hair algae, slowed SPS growth, cyano; low PO4 with high NO3 linked to purple SPS with stunted growth; high PO4 with low NO3 linked to green SPS with algae `[V]`.
- Use the app to plot the ratio NO3:PO4 (mass ratio) over time; flag below 50:1 or above 150:1 as an inconsistency to investigate. Threshold choice `[K]`.

### 2.3 pH vs CO2

- Carbonate system: dissolved CO2 in equilibrium with atmosphere (~420 ppm) sets pH at roughly 8.0 to 8.2 in the absence of other drivers; reef tanks run higher due to photosynthesis.
- Higher CO2 (poor aeration, algae or fish respiration, dark period) lowers pH; photosynthesis raises pH during the light period; the daily pH swing is a proxy for CO2 cycling.
- Alkalinity buffers pH; alk above 9 dKH with heavy consumption can raise pH above 8.4 at night reductions. `[K]`
- Nighttime pH drop of 0.2 to 0.3 units is normal; tracking it helps detect poor gas exchange. `[K]`

### 2.4 Ammonia toxicity vs pH and temperature

Fraction of un-ionized (free) NH3 (Emerson et al. 1975, widely used in aquaculture; verify source) `[K]`:

- pKa(T) = 0.09018 + 2729.92 / T_K, with T_K = T_C + 273.15
- f_NH3 = 1 / (1 + 10^(pKa − pH))
- NH3 (ppm) = f_NH3 × total ammonia-N (TAN, ppm NH3-N, numerically same basis if converted)
- Worked example at 25 °C, pH 8.2: pKa = 9.2465; 10^(1.0465) = 11.12; f = 1 / 12.12 = 0.0825 (8.25%). If TAN = 0.1 ppm NH3-N then NH3 ≈ 0.0083 ppm, which exceeds the fish-toxicity concern.
- Qualitative confirmation from aqion `[V]`: NH3 rises exponentially with pH; higher T and pH is worse. At 15 °C, pH 8.5, total ammonia 0.03 mM gives NH3 ~8% of total (0.00238 mM); pH 9.0 gives ~21% at 15 °C. `[V]`
- Salinity correction: seawater pKa is shifted slightly; for reef targets, using the freshwater Emerson formula gives conservative (higher-than-real) NH3 estimates. `[K]`
- App implementation: compute NH3 from user-entered TAN, pH, temperature; alert when NH3 >0.02 ppm.

### 2.5 Oxygen solubility vs temperature and salinity

- Saturation DO (mg/L) falls with temperature and salinity. Approximate values: fresh water ~8.3 mg/L at 25 °C; seawater 35 ppt ~6.5 mg/L at 25 °C; ~7.5 mg/L at 20 °C (seawater). `[K]`
- Rule of thumb: DO drops about 0.2 mg/L per +1 °C near 25 °C. `[K]`
- Reef target: DO above 5.5 mg/L; reef tanks can super-saturate in strong light; night DO minimum is the critical value.
- App: show "percent saturation" computed from DO and temperature; alert below 5.5 mg/L.

### 2.6 Other relationships
- Calcium precipitates when Ca × CO3 product exceeds solubility; alkalinity and Ca must be balanced to avoid Mg-free precipitate. `[K]`
- Phosphate binds to iron hydroxide, so iron and phosphate interact. `[K]`
- Potassium, strontium, iodine consumption is slow; ICP results drive dosing. `[K]`

---

## 3. Maintenance and dosing

### 3.1 Water changes

Standard: 10 to 15% every 1 to 2 weeks for a mixed reef; 5 to 10% weekly for heavy-load tanks or those with excess NO3/PO4. `[K]`

Dilution math (single change):

    C_after = C_before × (1 − f) + C_new × f
    where f = fraction replaced (0.10 for 10%), C_new = concentration in fresh saltwater (typically 0 for NO3/PO4; Ca/Mg/alk as mixed).

Example (60 gal, 10% change, nitrate 10 ppm, fresh mix NO3 = 0):
- C_after = 10 × 0.9 + 0 × 0.1 = 9 ppm (drop of 1 ppm per 10% water change).

Example (alk 9 dKH, fresh mix 8 dKH, 10% change): C_after = 9 × 0.9 + 8 × 0.1 = 8.9 dKH (tiny effect; this is why mix alk is important).

Key: a water change restores all mix-level ions toward premix concentrations; large differences between mix and tank (e.g., Ca 400 mix vs 430 tank) cause downward drift.

Water change volume (gallons) for a 60 gal display with 15 gal sump: total ~75 gal; base volume for percentage use total system volume. `[K]`

### 3.2 Salt mixing
- Mix RO/DI water at 1.025 to 1.026 SG at 25 °C, aerated, mixed 24 hrs before use, pH check and alk/Ca tested for consistency. `[K]`
- Premix Ca typically 400 to 420 ppm; Mg 1250 to 1350; alk 7 to 8 dKH (product-dependent). `[K]`
- Use fresh premix within days; test every batch for Ca/Mg/alk so changes are predictable.

### 3.3 Dosing: formulas

General formula for elemental dose (mass of product, grams):

    g_product = ΔC (ppm) × V (gal) × 3.785 (L/gal) / (1000 × w)

where w = mass fraction of the element in the product:
- Ca in CaCl2 anhydrous: w = 0.361 (40.08 / 110.98)
- Ca in CaCl2·2H2O: w = 0.273 (40.08 / 147.01)
- Mg in MgCl2·6H2O: w = 0.120 (24.31 / 203.3)
- Mg in MgSO4·7H2O: w = 0.099 (24.31 / 246.47)

Per-gallon constants (verified arithmetic, `[K]`):
- 1 ppm Ca in 1 gal = 3.785 mg Ca = 10.5 mg anhydrous CaCl2
- 60 gal, +10 ppm Ca: 600 ppm·gal × 3.785 mg/ppm·gal = 2,271 mg Ca → 6.3 g anhydrous CaCl2 (or 8.3 g dihydrate)
- Alk (NaHCO3, baking soda, MW 84.01; 1 meq per mmol): +1 meq/L in 1 gal = 0.318 g NaHCO3 per gal (≈ 1 g per 3 gal). 60 gal, +1 meq/L → 19.1 g; +1 dKH = 0.357 meq/L → 6.8 g per dKH in 60 gal `[K]`
- Alk via Na2CO3 (soda ash, MW 105.99, 2 meq per mol): +1 meq/L in 1 gal = 0.200 g per gal (60 gal, +1 meq/L → 12.0 g) `[K]`
- Note: Carbonate alk from baking soda is slower to shift pH; soda ash shifts pH faster; either way, dosing rate ≤ 1 dKH/day.

Mg (MgCl2·6H2O): +1 ppm Mg in 1 gal ≈ 3.785 mg Mg → 3.785 / 0.120 = 31.5 mg MgCl2·6H2O per gal per ppm; 60 gal, +50 ppm Mg → 94.5 g. `[K]`

Calcium alternatives: calcium hydroxide (kalkwasser), calcium chloride (fast), reactors (not in app scope). Phosphate-based dosing not in scope.

BRS 2-part (Ca + alk pre-mixed): BRS publishes a dosing calculator; product-specific concentrations vary by batch. App should accept product concentration (g/L or ppm) as user input, not hard-code. `[K]`

Typical per-day change caps (apply in dosing engine, user adjustable):
- Alk: 1 dKH per day max; 0.5 dKH/day for sensitive or SPS; 2 dKH is a stress event.
- Ca: 10 to 20 ppm per day; `[K]`
- Mg: 25 to 50 ppm per day. `[K]`

### 3.4 Top-off and evaporation
- Evaporation rate: typical 0.5 to 1.5 gal/day for a 60 gal in a room at 75 °F with 40 to 60% RH; actual depends on lid, flow, and heat. `[K]`
- Use an ATO (auto top-off) with RO/DI, not salt water. Track salinity rise between top-offs.
- App: input evaporation volume, compute daily salt concentration drift.

### 3.5 Skimmer, filtration, media
- Skimmer: clean collection cup weekly; empty; clean pump impeller and needle wheel monthly; check cup level and foam. `[K]`
- Filter sock: change every 2 to 3 days (for active use) or replace weekly; watch for NO3/PO4 release. `[K]`
- Carbon / GFO: change carbon every 2 to 4 weeks; GFO every 1 to 2 months per product, or when PO4 rebounds. `[K]`
- Chaetomorpha refugium: harvest 1 to 2 months; note nutrient export. `[K]`

### 3.6 Glass, pumps, lights
- Glass cleaning: weekly (algae magnet), use a razor for scratched glass; avoid scratching acrylic. `[K]`
- Pumps: clean every 1 to 3 months; flow reduction after 3 months is a sign. `[K]`
- Light: clean lens/glass cover monthly; check fan operation.
- Light photoperiod (see section 6).

### 3.7 Feeding schedule
- Feed reefs 1 to 2x daily; soft-coral reefs typically 1x daily or every other day for coral food; fish: small portions twice daily. `[K]`
- Feed for 2 to 3 min; remove uneaten food within 10 min; uneaten food = NO3/PO4 spike. `[K]`
- Frozen mysis, brine, nori for the tang and blenny. Blue tang juvenile: heavy algae and nori diet. `[K]`
- Feeding log in app: timing, food type, quantity estimate.

### 3.8 Quarantine
- All new fish: 4 to 6 weeks in a separate tank (QT); ideal for copper or hyposalinity treatment. `[K]`
- Hyposalinity QT: 1.009 to 1.010 SG, hold 4 to 6 weeks, ramp down 0.002 SG/day; raise back at 0.002/day. `[V]` Manta Hyposalinity.
- Copper QT: 14 to 28 days minimum; keep out of tanks with invertebrates or live rock. Copper dosing targets not verified in this session (Manta gives no numeric range) `[V]`.
- Tank transfer (ich): every 72 h to a clean cycled tank, 4 transfers, 12 days total `[V]` Manta Ich.

### 3.9 Cycling a new tank (marine, fishless or with live rock)

| Stage | Days (typical) | What happens |
|---|---|---|
| 1 | 0 to 7 | Ammonia introduced (fish food or pure ammonia); bacteria start |
| 2 | 7 to 28 | Ammonia-oxidizing bacteria (Nitrosomonas) convert NH3 to NO2; NO2 peaks |
| 3 | 21 to 42 | Nitrite-oxidizers (Nitrospira/Nitrobacter) convert NO2 to NO3; NO2 drops to 0 |
| 4 | 28 to 56 | Stable: ammonia and nitrite 0 with NO3 accumulating |

Total: 4 to 8 weeks typical for fishless, faster with cured live rock or bottled bacteria (2 to 4 weeks). `[K]`

Cycle check: after 3 consecutive days of 0 ammonia and 0 nitrite with a feed dose, the tank is cycled. App: build a cycling tracker with these checkpoints.

---

## 4. Livestock care

### 4.1 Compatibility and stocking (60 gal)

| Species (user) | Adult size | Issue for 60 gal | Notes |
|---|---|---|---|
| Ocellaris clownfish x2 | 3 to 4 in | OK | Pair; compatible with soft corals (Kenya tree, green star polyps, zoanthids); may host in anemone (none) |
| Juvenile blue tang | Adult 12 in | Outgrows 60 gal; grows fast | Plan rehome or upgrade (125+ gal); many keepers rehome at 6 to 8 in `[K]` |
| Pajama cardinalfish | 3 in | OK | Peaceful; zoo-tank bread-and-butter |
| Bluestreak cleaner wrasse | 4 in | OK (usually) | Needs copepods or enough food; may starve if not fed `[K]` |
| Snowflake eel | 24 to 36 in | Too large for 60 gal long-term | Needs 75 gal+ and secure lid; eat shrimp/small fish `[K]` |
| Hawkfish (unspecified) | 3 to 5 in | OK | Perch predator; may eat small fish or shrimp; avoid with cleaner shrimp `[K]` |
| Blenny (unspecified) | 3 to 5 in | OK | Algae grazer; fine `[K]` |
| Banded coral shrimp | 2 in | Eel and hawkfish may prey | Keep in view; compatibility depends on eel `[K]` |

Stocking: rule of thumb 1 in fish per 5 to 10 gal is outdated; bioload depends on filtration; for 60 gal, 6 to 8 small-to-medium fish plus eel is at the upper edge. `[K]`

Critical flags for the app: blue tang and snowflake eel adult sizes exceed 60 gal; the app should show a future-size warning based on species DB.

### 4.2 Acclimation (drip)
- Float bag 15 to 20 min for temp match.
- Drip acclimation: 1 to 2 drops per second, 60 to 90 min, or until the bag volume is roughly 4x; slower for sensitive species. `[K]`
- Do not add bag water to the display; net to QT.

### 4.3 Common diseases

| Disease | Pathogen | Signs | Treatment | Notes |
|---|---|---|---|---|
| Marine ich (white spot) | Cryptocaryon irritans | White salt-grain spots on fins, gills, body; rapid breathing; rubbing; lethargy `[V]` | Copper (QT, 14 to 28 days), hyposalinity 1.009 to 1.010 SG for 4 to 6 weeks `[V]`, tank transfer every 72 h x4 `[V]`, UV on display water (free-swimming theront killed) `[V]`, temperature raise to 82 to 84 °F (speeds cycle) `[V]`, chemical baths (formalin 12 drops/gal 30 to 60 min; methylene blue 16 drops/gal; H2O2 3% 20 mL/gal 5 to 10 min) `[V]` | Life cycle: trophont 3 to 7 d on fish; tomont 3 to 28 d; theront 24 to 48 h survival; empty tank 72 days `[V]` |
| Marine velvet | Amyloodinium ocellatum | Gold/yellow dust, clamped fins, flashing, rapid breathing | Copper, formalin, hyposalinity not effective (per Manta) `[V]` | Keep fish in QT; dimming display during treatment `[K]` |
| Brooklynella | Brooklynella hostilis | Heavy mucus, clamped fins, gasping; high mortality | Freshwater dip; formalin baths; copper not typically effective `[K]` | Quick action needed |
| Uronema (marine) | Uronema marinum (ciliate) | Erratic swimming, rapid decline, skin lesions, high mortality | Copper or formalin treatments in QT; early isolation `[K]` | Fast progression; verify |
| Flukes (gill/skin) | Monogenean flukes | Flashing, scraping, gill fanning | Praziquantel (prazi) in QT `[K]` | Repeat dosing per label |
| HLLE (head and lateral line erosion) | Undetermined; nutritional/water quality link | Pits on head and lateral line | Improve water quality, carotenoid/vitamin supplements, reduce carbon and phosphate `[K]` | Often linked to low ORP, low Ca/Mg/carbon `[K]` |

Note for app: disease records should log symptom, treatment, dose, start/stop, and outcome. Dosing values above are reference only; always verify against product labels.

### 4.4 Pests

| Pest | Signs | Control | Notes |
|---|---|---|---|
| Aiptasia | Anemone-like pest, spreads by budding | Peppermint shrimp, Berghia nudibranch, Joe's Juice injections, Aiptasia-X; manual removal | Lumicals-type light changes do little `[K]` |
| Bristle worms (Hermodice/Eunice type) | Visible segments, hairs, stinging | Manual removal, trapping, wrasse (predator dependent) `[K]` | Some bristle worms are scavengers and harmless |
| Flatworms (Convolutriloba/Pseudobiceros) | Brown/black flat worms, coral stress | Manual removal, Lysaric or Flatworm Exit (product-dependent), Acropora flatworm specialists `[K]` | Product efficacy varies |
| Vermetid snails | Tube-like calcareous structures, cover rock | Manual removal; avoid tube-scraping damage | Hard to control; sometimes ignored `[K]` |
| Aiptasia-eating fish | Some wrasses/Fire goby | Fish-based control | Verify compatibility |

### 4.5 Algae

| Algae | Causes | Fixes | Notes |
|---|---|---|---|
| Hair algae | High PO4, NO3 imbalance, low grazers | Lower PO4 (GFO, water change), grazers (tang, blenny, Mexican turbo snail), raise flow `[K]` | Gensou ties PO4 >0.15 ppm to hair algae `[V]` |
| Cyanobacteria (red slime) | Low flow, high organics, low O2 at night, high PO4/NO3 | Increase flow, reduce feed, siphon, clean, lower PO4; reduce nutrient; antibiotic (erythromycin) as last resort `[K]` | Often low flow dead spots |
| Dinoflagellates (dino) | Low NO3/PO4 (low nutrients), unstable lighting, low flow in corners | Raise NO3/PO4 to target; blackout 2 to 3 days (dark period); Dino-specific treatment; flow `[K]` | Gensou: NO3 below 1 ppm linked to dino bloom `[V]` |
| Diatoms (brown) | New tanks, high silicate, low light | Silicate removal (GFO/resin), grazers, flow; they often self-clear `[K]` | Silicate above 1 ppm `[K]` |
| Bubble (Valonia) | Low nutrients or high nutrient imbalance, reef-wide | Manual removal; carefully remove holdfasts; grazers less effective `[K]` | Hard to eradicate |
| Red macroalgae (already in tank) | Nutrient availability | Manage nutrients; sometimes benign `[K]` | Note: user has red macroalgae present |

---

## 5. App feature list (for a serious reefer)

| Feature | Rationale |
|---|---|
| Parameter log (all parameters in section 1) with date/time, test kit brand, method | Core data; brand matters for accuracy `[K]` |
| Trend graphs with 7/30/90/365-day views and overlay (e.g., alk vs Ca) | Shows consumption, drift, and recent changes |
| Target range bands with color zones | At-a-glance status |
| Rate-of-change alerts (alk >1 dKH/day, Ca >20 ppm/day, pH >0.2/day, SG >0.002/day) | Prevents shock |
| Danger alerts (ammonia >0 ppm, nitrite >0, temp >84 °F, pH <7.8, SG outside range) | Immediate |
| Reminders: water change, filter sock swap, carbon/GFO swap, skimmer cleaning, glass cleaning, pump cleaning, ICP send | Maintenance cadence |
| Dosing calculator (alk, Ca, Mg; baking soda, soda ash, CaCl2, BRS 2-part, Mg products; product concentration input) | Core utility; formulas in section 3.3 |
| Water change calculator (fraction, mix concentrations, post-change prediction) | Section 3.1 |
| Salt mix tracker (mix batch, SG, Ca, Mg, alk per batch) | Premix consistency |
| Top-off/evaporation tracker with salinity drift | Section 3.4 |
| Equipment log: skimmer, pumps, heaters, lights (AI Prime HD channels, hours), controller, ATO | Maintenance and warranty |
| Light schedule and intensity log (AI Prime HD photoperiod and channels) | Light acclimation tracking |
| Livestock inventory: species, acquisition date, size, price, status, disease history | Core for reef keepers |
| Stocking and future-size warnings (blue tang, snowflake eel) | Section 4.1 |
| Acclimation and quarantine tracker (QT start/end dates, treatment logs) | Section 3.8 |
| Disease tracker (symptoms, treatment, dose, outcome) | Section 4.3 |
| Cycle tracker for new tanks | Section 3.9 |
| Photo journal with tag per coral/fish and date | Coral growth, color progress |
| ICP import (CSV from Triton/ICP-OES) with comparison to home kit values | Calibrates home kits; reduces guesswork |
| Cost tracking (livestock, salt, dosing products, equipment) and running total | Budget |
| Maintenance task checklist (weekly/monthly) | Routine |
| Feeding log | Nutrient source tracking |
| Notes/event timeline (e.g., "added new coral", "power outage") | Context for trends |
| Export (CSV/PDF) and backup | Data portability |
| Offline-first with sync | Tanks are used in basements/garages with spotty connectivity |
| Multiple tanks support | Future expansion |

---

## 6. AI Prime HD (lighting)

Verification status: The AquaIllumination product page fetch failed, and the search returned only retailer listings. Specific values below are not verified in this session. Confirm against AquaIllumination's official spec page before shipping any number in the app.

| Item | Status | Note |
|---|---|---|
| Spectrum channels | Unverified | Prime HD uses multi-channel LED arrays (the Prime HD line is marketed as multi-channel). Check the spec sheet for exact channel names and counts. |
| Max wattage per module | Unverified | Confirm from spec page. |
| Coverage per module | Unverified | Confirm mounting guidance for 48 in tanks. |
| Recommended count for 48 in tank | Unverified | User runs 2 units on a 60 gal, which is a common setup; confirm coverage against AI guidance. |
| Soft-coral intensity schedule | `[K]` | Typical soft coral (zoanthids, Kenya tree, green star polyps, clove polyps) grows under moderate intensity: 30 to 60% peak output for 8 to 10 hours; ramp-up and ramp-down over 1 to 2 hours; daily photoperiod 8 to 10 h. Acclimate new corals over 1 to 2 weeks. |
| Light acclimation | `[K]` | Increase intensity by 5 to 10% per week for new or sensitive corals. |
| Red macroalgae | Note | Some reds can be damaged by intense light; monitor. |
| Lunar/moon mode | Unverified | Confirm feature availability. |

App hook: store light schedules as channel intensity curves per timeline; log intensity changes with date to correlate with coral growth or bleaching.

---

## Open items for follow-up (not resolved here)

- Confirm Emerson 1975 NH3 constants and salinity correction against the primary paper (not fetched).
- Confirm AI Prime HD spec sheet numbers (fetch failed).
- Confirm copper target ranges for marine ich (Manta gave none).
- Confirm dosing product concentrations for BRS 2-part and Mg products before using in app.
- Fetch Charterhouse parameter guide again for full body (truncated).
- Verify Redfield-ratio targets against a second, more authoritative source (only one forum-style article was fetched).
