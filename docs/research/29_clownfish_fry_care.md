# Ocellaris clownfish (Amphiprion ocellaris) larval and fry husbandry

Research for the aquarium simulation. Values are tagged by source quality:
**[P]** peer-reviewed full text read, **[S]** peer-reviewed abstract or search snippet only,
**[H]** hobby/blog source, **[U]** unverified or not found. Where sources disagree, both are shown.

Key caveat: the best protocol (Roux et al. 2021) describes a 54 L tank with 14 h light. Hobby
guides run smaller tanks and different Artemia timing. There is no single standard protocol.

---

## 1. Larval rearing tank setup

| Parameter | Value | Source |
|---|---|---|
| Tank volume | 54 L (60 x 30 x 30 cm), 30 L water. Also a 500 mL beaker low-volume system | [P] Roux 2021 |
| Hobby tank volume | 20-40 L, bare bottom | [H] Gensou |
| Setiawati 2008 | 200 L tanks, 100 L water, 100 eggs per tank (low density) | [S] Setiawati 2008 |
| Black sides | Yes. Walls wrapped in black plastic to reduce light and add contrast | [P] Roux 2021; [H] Gensou ("black walls") |
| Lid | Black lid from the morning after hatching until metamorphosis ends | [P] Roux 2021 |
| Photoperiod | 14 h light / 10 h dark | [P] Roux 2021 |
| Photoperiod (hobby) | About 16 h light on days 2-4, then unspecified | [H] Gensou |
| 24 h light | Not supported by any source found | [U] |
| Light intensity | Not found in any source. Black walls and lid are used to reduce it | [U] |
| Temperature | 26 C (Roux 2021). Hobby 26-27 C. Rodriguez-Ibarra 2024 found 28-30 C best for culture and harvest quality | [P] [H] [S] |
| Salinity | 34 g/L (Roux 2021). About SG 1.025 (Gensou). Rotifer culture 23 g/L, Artemia hatch 37 g/L (Roux) or 1.020 SG (Gensou) | [P] [H] |
| Aeration | Very low: two bubblers at 15 L/h (Roux classic tank). Low-volume beakers 150 bubbles/min | [P] Roux 2021 |
| Flow | Gentle only. Intense water movement stresses and kills larvae (Roux 2021) | [P] |
| Water exchange | Roux: 30% of volume daily from 2 dph, added slowly (0.6 L/min). Gensou: no water change days 2-4, then 10% daily from days 10-14 | [P] [H] |
| Greenwater | Nannochloropsis (Roux used Nannochloropsis oculata). Dosed twice daily 1-8 dph. Density in cells/mL not reported | [P] Roux 2021 |
| Greenwater density (hobby) | "Tea-coloured" water. The claim that survival halves without greenwater is not supported and should be treated as anecdote | [H] Gensou |
| Greenwater product study | A 2026 Florida abstract found a Nannochloropsis product gave higher survival than Tisochrysis or no algae, with no growth difference | [S] Aquaculture America 2026 |

Stocking note: Roux's low-volume system found more than 10 larvae per 500 mL beaker is fatal.
Setiawati used 100 eggs per 100 L (about 1 per L). Density is an open question for the sim.

---

## 2. Feeding schedule by day post hatch (DPH)

Two main protocols differ on rotifer density, Artemia start, and microdiet timing.

### Protocol A: Roux 2021 [P]

| DPH | Rotifers (per mL) | Enrichment | Artemia | Other feeds | Notes |
|---|---|---|---|---|---|
| 0 | none | | | | Hatch |
| 1-8 | 10/mL, maintained, fed twice daily | RG Complete | none | Nannochloropsis greenwater, twice daily | Rotifer culture fed 5 mL twice daily |
| 3-30 | none | | 3 nauplii/mL (classic tank) | | Low-volume: 15 nauplii per beaker once daily from 3 DPH, twice daily from 8 DPH |
| 12 | none | | | Pellets start at 12 DPH: 80 um, then 200 um | Once daily, 2% body mass rising to 8% |
| about 30 | | | | Large pellets, twice daily, 6-8% body mass | Juveniles moved to husbandry at 30 DPH after 20 min acclimation |

Metamorphosis finishes around 15 DPH at 26 C (Roux 2021).

### Protocol B: Gensou (hobby blog) [H]

| DPH | Rotifers | Artemia | Other |
|---|---|---|---|
| 0 | none, no feeding | none | Move egg tile 2 h before lights out |
| 1 | 5-10/mL, first feed about 1 h after lights on | none | Nannochloropsis greenwater |
| 2-4 | 5-10/mL, top-up morning and evening | none | No water change |
| 5 | Continue | Start nauplii under 24 h old | |
| 7 | Continue | Enrich with Selco once larvae reliably eat them | |
| 6-9 | Taper once most survivors are on nauplii | Main food | By day 9 most survivors eat only Artemia |
| 10-14 | Stop (not stated exactly) | Continue | Crushed pellet added; 10% daily water change |

Gensou gives metamorphosis at days 8-14 and recommends two staggered Artemia hatcheries for morning and evening batches.

### Other feeding data

| Source | Data | Quality |
|---|---|---|
| MDPI 2024 (search snippet, full text returned 403) | Larvae can move to Artemia as early as 5 DPH with no loss in survival, length, or cortisol. Microdiet at 5 DPH gave the highest survival, so Artemia may not be needed before microdiet | [S] |
| Setiawati 2008 | Artemia start day 6, 10, or 15. Growth did not differ. Survival was highest at day 6 (81%) vs day 10 (62%) and day 15 (67%). Start day 6 is best | [S] |
| Vietnamese A. percula study | Artemia at 7 DPH gave best growth; later start lowered growth. Survival about 40-48% across starts | [S] |
| A. clarkii study | Artemia at 2 DPH gave more metamorphosed fish than a 6-day start, and metamorphosis began 1 day sooner | [S] |
| Hoff (1996), cited in MDPI 2024 | Rotifers 10-15/mL from 1-8 DPH; Artemia 3-30 DPH | [S] |
| DHA enrichment (A. percula) | DHA 50-200 mg/L improved growth, survival, and metamorphosis. 150 mg/L was optimal, 200 mg/L worse | [S] |
| Avella et al. 2007 | Enriched Artemia improved growth by day 11 vs unenriched | [S] |
| Olivotto et al. 2011 | Title only: HUFA-enriched live prey as a limiting factor for A. ocellaris larval development and metamorphosis. Findings not retrieved | [U] |
| Hatchery report | Artemia (enriched with algae) three times a day up to 40 DPH | [S] |

**Summary of the sources:** The starting rotifer density is 5-10/mL (Gensou) or 10/mL (Roux). Artemia starts between day 2 and day 6. Microdiet or pellets start between day 5 and day 12. Feeding is frequent: about 2 times a day for rotifers, 1 to 3 times a day for Artemia.

---

## 3. Water quality and maintenance

| Topic | Finding | Source |
|---|---|---|
| Daily siphon | Remove dead larvae only on days 2-4 (Gensou). Roux 2021 does 30% daily exchange from 2 DPH, which is much more intensive | [P] [H] |
| Water change % | Roux: 30% daily (0.6 L/min). Gensou: 10% daily from days 10-14. Hatcheries: 30-50% daily exchange; one report doubles changes to 80% daily | [P] [H] [S] |
| Ammonia rule (Gensou) | Test every second day from day 3. If above 0.1 ppm, do a slow 10% change matched to temperature and salinity | [H] |
| Ammonia sensitivity (number) | Not found in a source. Roux: larvae are "very sensitive to variations in water parameters" | [P] qualitative |
| Bottom | Gensou: bare bottom. Dirty bottom is a common cause of loss (forum evidence only) | [H] |
| Rotifer crash | A forum breeder had a crash after a water change that was not diluted. Greenwater-fed rotifers ran out and larvae died about a week later | [H] |
| Bubbles / surface film | No peer-reviewed source found. Forum reports are mixed. Very low airflow is used in Roux 2021 | [U] |
| Dissolved oxygen | Forum claim: fish hover at the surface when O2 is low from organics. Not verified | [U] |
| Temperature swings | Roux: heater holds 26 C. Rodriguez-Ibarra 2024: incubation survival 94-96% across temperatures, malformations highest at 34 C | [P] [S] |

### Common killers (mostly forum evidence)

- **Starvation** when rotifers or greenwater run out. This is the best documented cause (Roux/Gensou both emphasise feeding).
- **Rotifer or algae crash** after water changes or without a backup culture.
- **Water parameter swings** (Roux says larvae are very sensitive).
- **Excess movement or flow**, stated by Roux.
- **Overcrowding in small beakers** (more than 10 per 500 mL is fatal in Roux's system).
- **Juvenile fighting**: up to 90% mortality in crowded juvenile groups (Roux 2021, [P]).
- **Day 3-4 die-off, metamorphosis-day losses (day 7-12)**: reported by hobbyists (forum). No source found that quantifies a crash day, so treat "crash days 3-4" as anecdotal.

---

## 4. Metamorphosis and grow-out transfer

### Metamorphosis signs

- **Timing:** day 8-14 in hobby sources (Gensou, forum); around 15 DPH at 26 C in Roux 2021. Other species: Premnas biaculeatus 11-12 days; A. percula 13-15 days; A. clarkii first white band day 15-17 and complete by day 25. [S] Madhu et al. / A. clarkii study.
- **Head bar first.** A diffuse bar behind the eyes appears first, then a mid-body bar, then the tail bar (Gensou [H]; Roux 2019 staging describes seven stages to 30 DPH, [S] only).
- **Behaviour:** larvae drop to the bottom and act like adults.

### Transfer to grow-out

- **Roux 2021:** juveniles moved to husbandry at 30 DPH after a 20 min acclimation [P].
- **Forum advice:** do not move larvae close to or during metamorphosis (very stressful). Move after the first group has metamorphosed [H].
- **Grow-out tank conditions:** no source with verified numbers was found. Roux 2021 notes juvenile fighting and mortality (up to 90%) when juveniles are crowded [P].

### Stripes and aggression

- **Stripes complete:** not found. Roux 2019 staging (HAL hal-02272895) tracks pigmentation to 30 DPH but the full table was not retrieved [S].
- **Aggression onset:** Roux 2021 gives fighting-related mortality but no day [P]. Adult aggression studies (BIOS 2024, Bristol 2026) are on adults, not juveniles [S].

---

## 5. Growth and size

| Age | Total length | Weight | Source |
|---|---|---|---|
| Hatch | about 2-3 mm larva (not in sources; general) | | [U] |
| Metamorphosis, about day 15 | Not stated in Roux text | | [P] |
| 30 DPH | Setiawati reports larval TL about 10 mm at day 6-15 starts (not 30 days) | | [S] |
| 30 DPH (Vietnamese study) | 12.84 cm length, 0.0376 g weight. **Implausible** (length and weight cannot both be right). Likely a units error. Do not use | | [S] flagged |
| Day 40 (black morph, ICAR-CMFRI) | About 18.9 mm juvenile, metamorphosed from 5 mm larvae at day 40 | | [S] (colour variant) |
| 60 days | Not found | | [U] |
| 90 days | Not found | | [U] |
| 6 months | Not found | | [U] |
| Adult | About 11 cm, females larger than males | | [S] general reference |

**Growth caveat:** the solid numbers stop at day 40 (about 19 mm), and even that is one colour variant. For the sim, use the Roux 2019 staging or a hatchery grow-out report to set size at 30, 60, 90 days. Do not extrapolate from the numbers above.

### Community display readiness

- Not found in any source (size, predator risk, age). Treat any size as a placeholder and mark it as unverified.

---

## 6. Special needs and common mistakes

- **Black sides and lid** are standard. Light is dimmed rather than removed.
- **Greenwater** is a live-feed enrichment and darkens the water, improving prey visibility. Keep it dosed daily (Roux 1-8 DPH).
- **Feed backup cultures.** Two independent rotifer bottles are the common advice (Gensou). Greenwater-fed rotifers can run out.
- **Do not overfeed.** Roux notes microdiet at 2-8% of body mass.
- **Enrichment matters.** HUFA/DHA enrichment (Olivotto 2011, Vietnamese DHA trial) improves growth and metamorphosis.
- **Do not handle larvae during metamorphosis.** Transfers are stressful.
- **Keep flow low.** Stated by Roux.
- **Temperature:** 26 C is standard. Above 30 C affected development in one study.
- **Avoid crowding** in small containers (more than 10 larvae per 500 mL is fatal in Roux's beaker system).

---

## Sources

Full text read (peer-reviewed):
- Roux N. et al. (2021). "A star is born again: Methods for larval rearing of an emerging model organism, the False clownfish Amphiprion ocellaris." J. Exp. Zool. B. https://pmc.ncbi.nlm.nih.gov/articles/PMC8248105 (doi 10.1002/jez.b.23028)

Abstracts or snippets only (peer-reviewed):
- Setiawati K.M. (2008). Jurnal Perikanan UGM. https://journal.ugm.ac.id/jfs/article/view/8946
- MDPI 2024 clownfish weaning study (full text returned 403): https://www.mdpi.com/2410-3888/10/9/461
- Rodriguez-Ibarra et al. (2024). Lat. Am. J. Aquat. Res. 52(3). https://www.lajar.cl/index.php/rlajar/article/view/vol52-issue3-fulltext-3151
- Roux N. et al. (2019). "Staging and normal table of postembryonic development of the clownfish (Amphiprion ocellaris)." Developmental Dynamics. https://hal.sorbonne-universite.fr/hal-02272895 (full text not retrieved)
- Vietnamese A. percula Artemia-start study. https://jad.hcmuaf.edu.vn/index.php/jad/article/view/165
- Vietnamese A. ocellaris HUFA study. https://vjst.vast.vn/jmst/article/view/14584
- Olivotto I. et al. (2011). "Live prey enrichment, with particular emphasis on HUFAs, as limiting factor in false percula clownfish larval development and metamorphosis." Comp. Biochem. Physiol. A. (title only, via search)
- Avella M.A. et al. (2007). Live prey enrichment and pigmentation (via search)
- ICAR-CMFRI captive breeding report (black morph, day 40 size): https://epubs.icar.org.in/index.php/IJF/article/download/102354/44497/467821 (search snippet, attribution to this exact paper not confirmed)
- Aquaculture America 2026, Florida Nannochloropsis greenwater abstract: https://www.was.org/Meeting/Program/PaperDetail/168128
- World Aquaculture Magazine, Sept 2014 (commercial 30-50% daily exchange): https://www.was.org/Magazine/2014/03/44/ (snippet only)

Hobby and blog sources (not peer-reviewed):
- Gensou (Singapore), "Clownfish Larvae Rearing with Rotifers: First 14 Days": https://gensou.sg/?p=32429
- Utah Reefs and Marine Colorado forum threads on rotifer crashes, bubbles, and transfers (anecdotal): https://utahreefs.com/FORUM/printer_friendly_posts.asp?TID=75613

Not retrieved (requested, unavailable):
- Joyce Wilkerson, *Clownfishes*. The book text was not accessible; forum references only.
- ORA (Ocean Reef Aquarium) clownfish protocol. Not found.
- Reef2Reef breeding threads. Search returned none.
- Full Roux 2019 staging table, Olivotto 2011 results, and the hatchery pellet trial (Lampung, 43 days, TetraBits 98% survival; source link not confirmed).
