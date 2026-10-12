# Reef Tank Parameter Effects: Sim Reference (60 gal, ~227 L)

Scope: dose-response, mechanism and time-course for each water parameter, plus light, flow, and fish-to-fish interactions for the species in 02 and 04. Coverage is uneven. Several searches returned only link lists, so many values are **(est.)**. Treat est. values as tunable parameters.

**Source legend.** [NOAA-DHW] NOAA Coral Reef Watch product page, https://coralreefwatch.noaa.gov/product/50km/tutorial/crw24_dhw_product.php. [BOYD] Boyd, ammonia review, Global Seafood Alliance, https://www.globalseafood.org/advocate/ammonia-toxicity-degrades-animal-health-growth/. [FURG-PB] Acute ammonia tolerance and histopathology in juvenile maroon clownfish, https://repositorio.furg.br/handle/1/5889. [HF-ALK] Holmes-Farley alkalinity and pH post, via https://modernreef.eu/?p=47442 (the page cites no primary papers). [CRYPT] Cryptocaryon life-cycle review, https://www.scielo.br/j/rbpv/a/QX3StyV98ZLDxRqswyM4KQd/?lang=en (cites Colorni 1987, Dan et al. 2006). [R2R-ALK] Reef2Reef alkalinity/pH thread, https://www.reef2reef.com/threads/alkalinity-stability-ph-stability-are-they-even-different.711768/ . [SOLUB] Oxygen-solubility tables, https://www.engineeringtoolbox.com/oxygen-solubility-water-d_841.html (not fetched; values below are est. from general solubility knowledge).

**Sourced vs estimated.** Sourced values are the NOAA DHW thresholds, the maroon clownfish ammonia LC50, the Boyd ammonia table and pH/temperature examples, the Cryptocaryon life-cycle times, and the Holmes-Farley bicarbonate percentages. Everything else is est.

---

## 1. Temperature

**Mechanism.**
- Fish: metabolism and O2 demand rise about 2-3x per 10 C (Q10 ~2-3, est.). Above roughly 30-31 C, cardiac and gill function fail first and the fish gasp at the surface.
- Ich (Cryptocaryon): life cycle shortens with temperature. At 24-25 C it runs 8-10 days; at 27 C it completes in about 7 days [CRYPT]. Theronts stay infective for 24-48 h after encystment at 24-27 C [CRYPT]. Warmer water speeds the parasite cycle, so a heat spike is a bad trade for ich-prone species.
- Corals: bleaching occurs from sustained heat over a threshold. For the sim, use the NOAA accumulation logic. Each week at 1 C above the local maximum monthly mean adds about 1 DHW. Bleaching significant at 4 DHW, severe bleaching and mortality at 8 DHW [NOAA-DHW]. In a tank, treat 30-31 C as the bleaching trigger (est.). Expulsion of damaged zooxanthellae is an active, fast response to moderate heat (est. hours to days) [PMC4262390, not reached in full].
- Inverts: shrimp and hermits tolerate heat less well than fish at the same temperature (est.). Molting slows above ~30 C (est.).

**Dose-response (est. unless noted).**

| Level | Duration | Effect |
|---|---|---|
| 26-28 C | sustained | Normal target band. Ich cycle ~7-8 days. |
| 29-30 C | days | Fish metabolic rise, mild O2 drop, ich cycle shortens. |
| 30-31 C | hours to days | Corals begin to stress (polyp retraction, pale tips). Cyano and dinos bloom in warm, high-nutrient water. |
| 31-32 C | 1-3 days | Fish gasp, clamped fins, hiding. Sensitive corals bleach. |
| 32-33 C | 1-2 days | Acute mortality risk for tangs and cardinals. Coral DHW accumulates fast. |
| 4 DHW (~4 weeks at +1 C) | weeks | Significant bleaching for sensitive corals [NOAA-DHW]. |
| 8 DHW | weeks | Severe bleaching and mortality [NOAA-DHW]. |

**Cold.** Below 22-23 C, fish metabolism and appetite drop. Ich slows but does not stop. Below 20 C, tangs and dottybacks become lethargic (est.). Ich cycle lengthens to 10+ days (est.).

**Rate of change.** Keep swings under 1 C per hour (est.). A 2-3 C per hour drop triggers fish stress and velvet or ich relapse (est.). Heater or chiller failures that drift by 0.5 C/h over 4+ h are usually tolerated (est.).

**Recovery.** Fish recover in hours to a day if they have not been gasping. Corals that bleached but did not die can regain color in weeks to months (est.). Tissue loss is permanent. Ich outbreaks triggered by stress can take 2-4 weeks to clear with treatment (est.).

**Interactions.** Higher temperature lowers O2 solubility and raises fish O2 demand (see section 8). It also raises the NH3 fraction and NH3 toxicity (section 4).

---

## 2. Salinity

**Mechanism.**
- Fish: marine teleosts lose water and gain salt across the gills. They drink and excrete monovalent ions actively. A rapid drop in salinity reduces the osmotic gradient and causes cell swelling. A rapid rise dehydrates them.
- Inverts: shrimp and hermits osmoregulate poorly and molt badly at low or unstable salinity (est.). Snails and starfish are closer to isosmotic but still stressed by sharp swings (est.).
- Corals: low salinity causes tissue swelling and reduced calcification. Sudden freshwater inflow is fatal to many corals within hours (est.).

**Dose-response (est.).**
- Target: SG 1.024-1.026 (35 ppt, 35 PSU).
- Gradual change under 0.001 SG per day: tolerated by fish and corals.
- Drop of 0.004 SG within 1 h: noticeable fish stress (est.). Inverts show lethargy.
- Drop of 0.008-0.010 SG within 1-2 h: osmotic stress in fish (heavy breathing, lying low). Shrimp may fail to molt.
- Drop of 0.015+ SG within hours: fish mortality risk within 24 h. Corals show tissue swelling and polyp closure (est.).
- Rise of 0.005 SG within a few hours: evaporation-driven increase, mild stress. Above 1.028 SG, fish and inverts show high stress (est.).
- Above 1.030 SG: most reef fish and corals are at risk within days (est.).

**Osmotic shock.** Fish tolerate about 0.003-0.005 SG per hour with no visible effect (est.). Shrimp are more sensitive (est. 0.002 SG/h).

**Recovery.** Fish recover in 1-3 days after salinity returns to normal. Inverts can take 1-2 weeks to resume normal molting (est.). Corals take days to weeks (est.).

**Interactions.** Lower salinity raises the NH3 unionized fraction slightly and lowers NH3 toxicity slightly. The effect is small (Boyd: shrimp LC50 rose from 1.2 mg/L at 15 ppt to 1.6 mg/L at 35 ppt) [BOYD].

---

## 3. pH and CO2

**Mechanism.**
- Fish: low pH impairs gill ion exchange and blood O2 carriage. Acidosis develops below about 7.6 (est.).
- Inverts: low pH slows shell and skeleton formation. Snails and hermits show soft shell at pH below 7.8 (est.).
- Corals: calcification falls with lower carbonate saturation. Calcification falls roughly 15-20% per 0.1 pH drop below 8.0 (est.).
- Algae: coralline is hurt by low pH. Cyano and some hair algae can tolerate it better (est.).

**Dose-response (est.).**
- pH 8.0-8.3: target band.
- pH 7.8-7.9: slower coral growth. Fish fine.
- pH 7.6-7.7: fish stress, coral calcification drops sharply, snails and hermits show soft shell.
- pH below 7.5: acute fish stress, risk of death within days. Inverts fail.
- pH above 8.4: coral and fish stress (est.), and high NH3 fraction (see section 4).

**Time course.** pH swings over 0.3 in a day are harmful to corals (est.). A single day at pH 7.8 causes little harm. Two to three weeks at 7.7 causes measurable growth loss (est.).

**Holmes-Farley bicarbonate numbers** [HF-ALK]. At fixed alkalinity 8 dKH, bicarbonate changes about 3.7% per 0.1 pH at pH 8.00 and about 5% per 0.1 pH at pH 8.15. A 0.3-0.4 pH daily swing moves bicarbonate about 13% in one day. Carbonate changes far more than bicarbonate at a fixed alkalinity, by about 128% for a pH 7.9-8.3 swing at 8 dKH.

**Rapid-change threshold.** One Reef2Reef-discussed rule of thumb is that pH steps of about 0.1 can cause coral stress (est., not sourced).

**CO2.** Elevated CO2 lowers pH, so a CO2 leak or a failed skimmer or refugium pump in a closed room drives pH down. Use the same thresholds above.

**Recovery.** Fish recover within 1-2 days. Coral calcification recovers in days to weeks (est.). Snails and hermits can take weeks to regrow shell (est.).

---

## 4. Ammonia (NH3/NH4+)

**Mechanism.**
- Fish: un-ionized NH3 diffuses across the gill membrane and damages gill epithelium, kidney, liver, and brain. Histopathology in juvenile maroon clownfish at 96 h shows gill lamellar hyperplasia and shortening, kidney tubular necrosis, liver lipid deposition, and brain glial proliferation with neuronal damage [FURG-PB].
- Inverts: ammonia is toxic at similar or lower levels. Shrimp and hermits molt poorly, and snails are damaged by ammonia (est.).
- Corals: ammonia uptake is possible but corals are relatively tolerant (est.).
- Algae: ammonia is taken up readily by cyano and hair algae (est.).

**Dose-response.**
- **Clownfish (maroon, Premnas biaculeatus):** 96 h LC50 for un-ionized NH3-N is 0.89 mg/L. The 24 h LC50 is 1.68 mg/L NH3-N [FURG-PB]. Maroon values are used here as a proxy for ocellaris (est.).
- **Warmwater fish, general:** 96 h LC50 is 1.0-3.0 mg/L NH3-N. Coldwater is below 1.0 mg/L [BOYD].
- **Shrimp (penaeids):** 96 h LC50 is 0.69-2.95 mg/L NH3-N [BOYD]. Growth falls about 50% at 0.45 mg/L NH3-N for five penaeid species [BOYD].
- **Sublethal safe long-term** (Boyd: 0.05 x 96 h LC50): about 0.045 mg/L NH3-N for coldwater, 0.05-0.15 mg/L for warmwater [BOYD]. For clownfish, est. safe limit is 0.04 mg/L NH3-N, which is about 0.05 mg/L NH3.
- **Total ammonia:** at 1.0 mg/L total-N at pH 7.4 and 26 C, NH3-N is 0.015 mg/L. At pH 8.8 and 28 C, NH3-N is 0.306 mg/L, which is about 20 times higher [BOYD].
- **Typical marine ratios (est.):** at pH 8.0 and 25 C, about 5% of TAN is NH3. At pH 8.3, about 14%. At pH 8.5, about 25%.

**Time course (est. unless noted).**
- 0.05-0.1 mg/L NH3-N for days: gill changes, subtle appetite loss. Inverts start to molt poorly.
- 0.25-0.5 mg/L NH3-N for 1-3 days: lethargy, gasping, clamped fins, lying low, gill hyperplasia. Inverts start to die.
- 0.5-1.0 mg/L NH3-N for 24 h: significant fish mortality risk, especially for tangs and cardinals.
- 1.0 mg/L NH3-N or more for hours: lethal to most marine fish and inverts (consistent with 96 h LC50 of about 0.9-3.0).

**Observable signs (fish, in order).** Rapid gill movement, surface gasping, hiding in rockwork, reduced feeding, clamped fins, lying on bottom, erratic swimming, death. Cloudy eyes and red gills can appear with prolonged exposure (est.).

**Recovery.** After a 24-48 h NH3 spike, fish recover in days if the spike was below 0.5 mg/L NH3-N. Gill damage may need weeks to heal (est.). Inverts that lost molting capacity need a full molt cycle to recover.

**Interactions.** High pH and high temperature sharply raise NH3 fraction and toxicity. A die-off releases ammonia, which can cascade into a tank crash (see section 11).

---

## 5. Nitrite (NO2-)

**Mechanism.** Nitrite enters fish gills through chloride channels. It oxidizes hemoglobin to methemoglobin, which carries no O2, causing brown-blood disease in fish (est.). Chloride (salt) protects against nitrite. In seawater, nitrite toxicity is much lower than in freshwater. Chloride is about 19 g/L in seawater, which blocks uptake (est.).

**Dose-response (est., no marine values were sourced).**
- Under 0.1 mg/L NO2-N: effectively harmless in saltwater.
- 0.1-0.5 mg/L NO2-N for days: mild gill stress, reduced appetite in sensitive species.
- Above 0.5 mg/L NO2-N for days: methemoglobin rises, brown or chocolate blood, lethargy, gasping.
- Above 1.0 mg/L NO2-N: high mortality risk within days (est.).

**Inverts.** Invertebrates are less well studied. Shrimp are sensitive to nitrite at lower levels than fish (est.).

**Recovery.** Fish recover within days after nitrite drops. Methemoglobin reverses over a few days.

---

## 6. Nitrate (NO3-)

**Mechanism.** Nitrate is mostly harmless to fish at aquarium levels. High nitrate fuels algae and cyanobacteria, which then shade corals and stress inverts through oxygen swings at night (est.).

**Dose-response (est.).**
- 0-1 ppm: fine for most species. Very low nitrate (under 0.5 ppm) can starve soft corals and some zooxanthellate LPS of dissolved N (est.).
- 1-10 ppm: target band for most reef systems. Growth is not limited.
- 10-30 ppm: algae growth accelerates, especially hair algae, cyano, and diatoms (est.). Corals show slower growth and higher algal competition.
- Above 30 ppm: high risk of algae overgrowth, nighttime O2 dips, and reduced coral health (est.).

**Low nitrate.** Under 0.5 ppm nitrate, soft corals and some LPS show pale or shrinking tissue after weeks (est.).

**Recovery.** Algae regrow in weeks. Coral recovery is slow (weeks to months, est.).

---

## 7. Phosphate (PO4 3-)

**Mechanism.** Phosphate is the main limiting nutrient for algae in reefs. High phosphate fuels hair algae and cyano and inhibits coralline calcification (est.). Phosphate also competes with calcium carbonate formation and damages coral skeletal growth (est.). Very low phosphate starves some corals (est.).

**Dose-response (est.).**
- 0 ppm: normal for some tanks, but may limit zooxanthellate coral growth and coral color (est.).
- 0.03-0.1 ppm: target band.
- 0.1-0.3 ppm: hair algae and cyano bloom, coralline slows (est.).
- Above 0.3 ppm: strong algae blooms, coral growth and calcification impaired (est.).

**Nitrate:phosphate ratio.** Algae control tends to be better when N:P is near 10-15:1 (est.). A low N with high P favors cyano (est.).

**Recovery.** Algae drop within weeks after phosphate is reduced. Coral recovery takes weeks to months (est.).

---

## 8. Dissolved Oxygen (DO)

**Mechanism.** Fish extract O2 from water across the gills. Warmer water holds less O2 and fish need more. Hypoxia causes fish to gasp, stop feeding, and eventually suffocate. Inverts such as shrimp and hermits are often more sensitive to low DO than fish (est.). Corals and algae produce O2 in the light and consume it at night.

**Solubility (est. from standard tables [SOLUB]).** At 35 ppt and 25 C, DO saturation is about 6.3-6.5 mg/L. At 28 C about 5.9 mg/L. At 30 C about 5.7 mg/L (est.).

**Hypoxia thresholds (est.).**
- Above 5.0 mg/L: healthy.
- 4.0-5.0 mg/L: stress in tangs and cardinals, reduced appetite (est.).
- 3.0-4.0 mg/L: gasping, clamped fins, lying on bottom (est.).
- Below 3.0 mg/L: fish mortality risk within hours to days (est.).
- Shrimp and hermits: stress begins above 5 mg/L, mortality risk below 3-4 mg/L (est.).

**Night dips.** In a heavily lit tank with high biomass, DO can drop several mg/L overnight (est.). Keep flow and surface agitation adequate.

**Recovery.** Fish recover within minutes to hours after DO returns above 5 mg/L, if they did not suffer damage (est.).

---

## 9. Alkalinity (dKH)

**Mechanism.** Alkalinity buffers pH and supplies carbonate and bicarbonate for coral calcification. Coral calcification is strongly linked to alkalinity (est.). Fish are largely unaffected directly. Inverts with calcium carbonate shells (snails, hermits, crabs) are affected through reduced calcification (est.).

**Dose-response (est. unless noted).**
- 7-9 dKH: target band.
- 6-7 dKH: slower coral growth, soft shell in snails.
- Below 6 dKH: coral tissue stress, faster pH swings.
- Above 12 dKH: precipitation risk, calcium carbonate deposits, coral tissue stress (est.).

**Swings.** Rapid jumps of around 1 dKH or more can stress sensitive corals, especially SPS (est.; this rule of thumb is widely quoted but not backed by a primary citation in the source reached) [HF-ALK]. Keep daily swings under 0.5 dKH (est.).

**Recovery.** Coral recovery takes days to weeks after alkalinity returns to range (est.).

---

## 10. Calcium (Ca2+) and Magnesium (Mg2+)

**Mechanism.** Calcium is the main skeletal ion for corals and coralline algae. Calcium drives calcification alongside alkalinity. Magnesium stabilizes carbonate chemistry and prevents calcium carbonate precipitation (est.).

**Dose-response (est.).**
- Calcium 400-450 ppm: target band.
- Calcium 380-400 ppm: slower coral growth.
- Calcium below 350 ppm: strong calcification loss and soft shell in snails and hermits (est.).
- Calcium above 480 ppm: precipitation risk, reduced alkalinity (est.).
- Magnesium 1250-1350 ppm: target band.
- Magnesium below 1200 ppm: alkalinity and calcium precipitate, coral growth impaired (est.).

**Recovery.** Weeks for coral and coralline after Ca or Mg return to range.

---

## 11. Die-off and Tank-Crash Cascade

**Sequence (est.).** A fish or invert die-off releases organic waste. Bacteria break it down to ammonia. Ammonia rises above 0.25 mg/L NH3-N within 1-3 days in a small or stocked tank (est.). Fish gasp and die. Algae and cyano then bloom on the released nutrients. Nighttime O2 dips occur as decomposition consumes oxygen. Corals and inverts can die within days.

**Mitigation.** Keep biomass low (sized to the 60 gal tank), use a biological filter with adequate surface area, and monitor ammonia and nitrite weekly.

---

## 12. Copper and Metals (Inverts)

**Mechanism.** Copper binds gill membranes and disrupts ion transport in invertebrates. Copper also damages enzymes and can cause tissue necrosis (est.). Invertebrates are far more sensitive than fish (est.). Ornamental shrimp, hermits, snails, starfish, and feather dusters are all highly sensitive (est.).

**Dose-response (est.).** Specific marine LC50s for ornamental inverts were not reached. For the sim, use est. values.
- Copper below 0.005 mg/L: no visible effect on most inverts (est.).
- 0.01-0.05 mg/L for days: lethargy and failed molting in shrimp, snail retraction (est.).
- Above 0.1 mg/L for hours to days: high invert mortality (est.).

**Case data point.** A single yellowtail tang treatment case used copper at 0.24 mg/L on day 3 and 0.16 mg/L maintenance by day 15, with no reported fish deaths [CRYPT]. This shows that the fish tolerate treatment levels, but this does not say how inverts fare, and invertebrates should never be exposed to copper (est.).

**Recovery.** Inverts seldom recover fully after copper exposure at these levels (est.).

---

## 13. Iodine and Potassium (brief)

**Mechanism.** Iodine supports invert molting and coral metabolism. Potassium balances ion transport.

**Dose-response (est.).** Iodine overdose causes coral tissue stress and invert molting failure. Potassium is needed at ~380-420 ppm (est.). Low K is less common in reef tanks.

---

## 14. Light Intensity, Photoperiod, and Spectrum

**Mechanism.** Light drives zooxanthellae photosynthesis and coral growth. Too much light causes photoinhibition and bleaching. Too little light starves corals. Light also drives algae growth, especially cyano and hair algae (est.).

**Photoperiod.** Most reef tanks use 8-12 h of light per day (est.). Longer photoperiods (over 12 h) increase algae growth (est.). Shorter photoperiods (under 6 h) reduce coral growth (est.).

**Intensity.** Light intensity around 200-400 umol photons/m2/s at coral depth is typical (est.). Above about 500 umol, high-light corals can bleach in weeks (est.). Below 150 umol, LPS and SPS grow slowly (est.).

**Algae response (est.).**
- Hair algae and diatoms bloom under high nutrients and moderate light.
- Cyanobacteria bloom under low flow, high nutrients, and high light.
- Coralline grows best under moderate light and moderate nutrients.

**Light-change shock.** Sudden jumps in intensity or photoperiod cause coral bleaching or polyp retraction within days (est.). Ramp light changes over 1-2 weeks.

**Recovery.** Corals adapt to new light levels over 2-4 weeks (est.).

---

## 15. Flow Loss

**Mechanism.** Flow removes waste, supplies O2 and food, and reduces detritus. Loss of flow reduces O2 transfer, lets detritus settle, and lets cyano bloom (est.). Fish and corals lose food delivery and waste removal.

**Dose-response (est.).**
- Flow above 10 cm/s: good coral feeding and polyp extension.
- 5-10 cm/s: acceptable for most reef fish and corals.
- Below 5 cm/s: detritus settles, cyano and hair algae risk rises, corals show reduced polyp extension.
- Flow loss of 30 min or more: reduced O2 and detritus stirring. Fish may show mild stress at high biomass (est.).
- Flow loss of several hours: cyano bloom risk, DO dips at night, coral polyp retraction (est.).

**Recovery.** Once flow returns, most fish and corals recover in hours. Cyano and detritus blooms take days to weeks to clear (est.).

---

## 16. Pump Flow Strength (fish holding, polyp extension, detritus)

**Fish swimming effort (est.).**
- Tang: holds station in open flow areas. Prefers mid-to-high flow zones and swims continuously (est.).
- Clownfish: low-to-mid flow near anemones or soft coral base (est.).
- Cardinal: mid flow, school-like hovering (est.).
- Cleaner wrasse: low-to-mid flow near reef face (est.).
- Moray: low flow, rock crevices (est.).
- Hawkfish and blennies: low flow, perched on rock or coral (est.).
- Hermits and shrimp: low flow, mostly on substrate (est.).

**Coral polyp extension (est.).** LPS and soft corals extend best at 10-20 cm/s. SPS prefer strong flow above 15 cm/s. Low flow reduces feeding and polyp extension.

**Detritus and cyano (est.).** Flow above 10 cm/s keeps detritus suspended and carried to skimmer. Low flow lets detritus settle and feeds cyano.

---

## 17. Fish-to-Fish Interaction Matrix

**Species key.** OC = ocellaris clownfish (x2). PT = palette/blue tang (juvenile). PC = pajama cardinal. BC = bluestreak cleaner wrasse. SM = snowflake moray. FH = Falco hawkfish. LB = lawnmower blenny. TB = tailspot blenny. ID = indigo dottyback.

**Interaction codes.** A = aggression. C = chasing. T = territorial. H = hierarchy/dominance. CL = cleaning. P = predation. X = tolerance (no notable interaction). (est. unless noted.)

| | OC | PT | PC | BC | SM | FH | LB | TB | ID |
|---|---|---|---|---|---|---|---|---|---|
| **OC** | self: A-T with pair partner (est.) | X | X | X | X | X | X | X | Low A, mostly ignored. Dottyback may challenge OC near anemone (est.). |
| **PT** | X | self: H, A-C in tight tanks (est.) | X | CL: cleaner visits tang (est.) | X | X | X | X | X |
| **PC** | X | X | self: H within school (est.) | X | P: moray may eat cardinals (est.) | X | X | X | X |
| **BC** | X | CL | X | self: T (est.), A with other cleaners | X | X | X | X | X |
| **SM** | X | X | P | X | self: none | X | X | X | X |
| **FH** | X | X | P: may eat small cardinals (est.) | X | X | self: T (est.) | X | X | Low C, may attack dottyback (est.) |
| **LB** | X | X | X | X | X | X | self: T | Low A, territorial rock competition (est.) | X |
| **TB** | X | X | X | X | X | X | Low A (est.) | self: T | X |
| **ID** | Low A (est.) | X | X | X | X | Low C (est.) | X | X | self: T (est.) |

**Notes.** Pairings are est. from general reef-fish behavior. Frequency of chasing or aggression is est. per day: moderate for OC and PT, low for others. Cleaning by BC is quantified in 02 [FB-mirror]. Cardinals in a school are at risk of moray predation only if the moray is large and the cardinal is alone (est.).

---

## 18. Compact Parameter Table (for coding)

Format: {parameter, level, organism group, effect, onset time, mortality probability/time, recovery time}. Est. values unless marked.

| parameter | level | organism group | effect | onset time | mortality probability/time | recovery time |
|---|---|---|---|---|---|---|
| temperature | 30-31 C | fish | gasping, reduced appetite | hours | low | hours to days |
| temperature | 32-33 C | fish | acute stress | 1-2 days | high in tangs/cardinals | not recoverable if lethal |
| temperature | 4 DHW | coral | significant bleaching [NOAA-DHW] | weeks | low to moderate | months |
| temperature | 8 DHW | coral | severe bleaching, mortality [NOAA-DHW] | weeks | high | not recoverable |
| salinity | SG drop 0.004 in 1 h | fish, inverts | stress | hours | low | 1-3 days |
| salinity | SG drop 0.015+ | fish | osmotic shock | hours | high within 24 h | not recoverable if lethal |
| pH | 7.7 | fish, coral | fish stress, coral calcification drop | 2-3 weeks | low | weeks |
| pH | below 7.5 | fish, inverts | acute stress | days | high | not recoverable |
| ammonia | 0.05 mg/L NH3-N | fish, inverts | gill change, molt issues | days | low | weeks |
| ammonia | 0.5 mg/L NH3-N | fish | gasping, mortality | 1-3 days | moderate to high | days |
| ammonia | 1.0 mg/L NH3-N | fish, inverts | lethal | hours to 96 h | high (96 h LC50 0.89 for maroon clownfish [FURG-PB]) | not recoverable |
| nitrite | 0.5 mg/L NO2-N | fish | methemoglobin, brown blood | days | moderate | days |
| nitrate | 30+ ppm | algae, coral | algae bloom, coral stress | weeks | low | weeks to months |
| phosphate | 0.3+ ppm | algae, coral | hair algae bloom, coralline slows | weeks | low | weeks to months |
| DO | 3.0 mg/L | fish | mortality risk | hours | high | minutes to hours if survived |
| alkalinity | 1 dKH rapid jump | coral (SPS) | stress | hours to days | low | days to weeks |
| calcium | below 350 ppm | coral, inverts | calcification loss | weeks | low | weeks |
| copper | 0.1+ mg/L | inverts | lethal | hours to days | high | not recoverable |
| light | sudden +200 umol | coral | bleaching, retraction | days | low | 2-4 weeks |
| flow | loss of 30+ min | fish, coral | mild stress, detritus | minutes to hours | low | hours to days |

---

## 19. Gaps and Notes

- Copper, nitrite, and DO sources returned only link lists. The numbers marked est. are placeholders to be checked against primary toxicology data before tuning the sim.
- The coral expulsion paper (PMC4262390) was not reached in full.
- Light, flow, and fish-interaction values are est. from general reef knowledge, not from fetched studies.
- The ich cycle temperature dependence is sourced from [CRYPT] only for 24-27 C.
