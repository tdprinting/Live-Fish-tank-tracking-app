# 07 - Sources for the open gaps in 06 §6

Scope: the five gaps listed in `06_interactions_analysis.md` §6, plus the two pointers it names (Baer 1980, Grutter 1994 ch. 5). Evidence grades: **peer-reviewed**, **aquarium or institutional**, **hobby**. "Not found" means no usable source turned up in this search. File 06 is not edited.

Correction to 06: the lawnmower-blenny chase figures (90 % of blennies, 20 % of damsels) come from a primary paper, Townsend & Tibbetts 2004, not only from TFH (`H-TFH-LMB`). Those figures are percentages of intrusions, not per-hour rates.

---

## Gap 1: Chase rates per hour (dottyback, lawnmower blenny)

**Lawnmower blenny (*Salarias fasciatus*), partly sourced**
- Value found: chases about 90 % of intruding blennies (conspecifics, *Atrosalarias*, *Ecsenius*), about 20 % of intruding damsels, and only blennies of the same family that are the same size or smaller. Rarely chases non-grazers. No per-hour rate.
- Source: Townsend KA, Tibbetts IR (2004) "The ecological significance of the combtoothed blenny in a coral reef ecosystem." *J Fish Biol* 65(1):77-90. https://doi.org/10.1111/j.0022-1112.2004.00426.x (abstract, read via https://research.usc.edu.au/esploro/outputs/journalArticle/The-ecological-significance-of-the-combtoothed/99548308102621)
- Grade: **peer-reviewed** (percentages only, abstract-level; raw counts not seen).
- Suggestion: keep the 0.9 and 0.2 intrusion probabilities as sourced inputs. Derive the per-hour cap from them and the intrusion frequency, which is still [I]. Keep the same-family and size-ceiling rule.

**Dottyback (*Pseudochromis fridmani*)**
- Value found: none. The only quantitative aggression data in the Wittenrich 2023 paper are for breeding (chasing the female out of the pipe, no rates). The paper calls *P. fridmani* "relatively less aggressive" than other dottybacks, without measurements.
- Sources checked: https://www.mdpi.com/2410-3888/8/9/451 (peer-reviewed, breeding only); reefs.com and hobby forum threads (no counts).
- Grade: none quantitative. Hobby sources are qualitative only (https://www.furrycritter.com/pages/articles/fish/dottyback.htm).
- Suggestion: keep the 4-8/h (week 1) and 0.5-2/h values as [I] tunable defaults. Do not describe them as sourced.

---

## Gap 2: Swimming speeds in BL/s per species

**Family-level labriform aggregates (peer-reviewed)**
- Value found: labriform field speeds average up to 68.8 cm/s, lab Ucrit maximum 82.4 cm/s, field about 70 % of Ucrit. For *Acanthurus triostegus* and *A. lineatus* (Acanthuridae), field speed is nearly equal to Ucrit. Body-caudal species average 39.0 cm/s or less. No BL/s values in the text; the table with species values was not accessible.
- Source: Fulton CJ (2007) "Swimming speed performance in coral reef fishes: field validations reveal distinct functional groups." *Coral Reefs* 26:217-228. https://link.springer.com/article/10.1007/s00338-007-0195-0
- Grade: **peer-reviewed**.
- Suggestion: convert the labriform field figure to BL/s using each species' BL (tang 7 cm, clown 8 cm), which gives a sourced cruise ceiling near 1-2 BL/s. Keep burst values as [I].

**Larval Ucrit in BL/s (peer-reviewed, not adult)**
- Value found: late-stage larvae reach about 19 BL/s (*Sphaeramia nematoptera*, apogonid), 34 (*Pomacentrus amboinensis*) and 49 (*Amphiprion melanopus*). The authors say these high relative speeds are "largely a consequence of their small sizes."
- Source: Bellwood DR, Fisher R (2001) "Relative swimming speeds in reef fish larvae." https://researchonline.jcu.edu.au/13327/
- Grade: **peer-reviewed**, larval stage only.
- Suggestion: do not use these for adult burst speeds. They show that relative speed falls with size, which supports a size-scaled burst rule.

**Species-level adult BL/s for the listed species**
- Value found: not found for clowns, tang, cardinal, wrasse, blennies, hawkfish, dottyback. Juvenile surgeonfish are not in the Ucrit dataset with values in the text (Ucrit dataset: https://cob.silverchair.com/jeb/article-pdf/210/14/2436/1539198/2436.pdf, which lists Acanthuridae without numbers, and has no blennies).
- Grade: none.
- Suggestion: keep the [I] per-species BL/s values in 06 §1, and note in the code that they are inferred from Fulton's family-level figure.

---

## Gap 3: Adult engineer goby (*Pholidichthys leucotaenia*) day and night activity

- Value found: no quantitative diel data. Adults are described as "very reclusive and spend all their time hidden in burrows under the sand" (hobby). Juveniles swarm and feed away from the burrow by day, then at night hang from the burrow ceiling by mucus threads and regurgitate plankton to the adults. Whether adults come out at night is not stated.
- Sources:
  - Fishkeeper, "Crafty convicts" (hobby/magazine): https://www.fishkeeper.co.uk/stories/crafty-convicts (the study it cites is unnamed)
  - Monterey Bay Aquarium: https://aquarium.org/animals/engineer-goby/ (institutional; burrow-building and juvenile swarms only, no diel data)
  - Primary literature search returned only taxonomy and the Smithsonian family review (https://repository.si.edu/handle/10088/5213), which I did not open.
- Grade: **hobby** (Fishkeeper); **institutional** (MBA, no activity data).
- Suggestion: adults should be burrow-bound by day and mostly in the burrow at night. The 40 % night patrol in 06 §1.5 has no support; reduce to a low value (about 5 %) and label it [I]. Keep the juvenile night-hanging rule, which the hobby source supports.

---

## Gap 4: Shrimp killing other shrimp (CBS and skunk cleaner)

**Within-species (peer-reviewed), *Lysmata amboinensis***
- Value found: in triplets one shrimp was killed, in quartets two, all of them just after molting. All groups were reduced to a pair within 42 days. Methods: singles N=6, pairs 10 replicates, triplets 10, quartets 10 (45-60 L tanks). Mean intermoult interval about 15 days in pairs. No cross-species or cross-genus tests.
- Sources:
  - Wong JWY, Michiels NK (2011) *Frontiers in Zoology* 8:30. https://frontiersinzoology.biomedcentral.com/articles/10.1186/1742-9994-8-30 (methods read; results via summary at https://reefs.com/?p=175085)
- Grade: **peer-reviewed** (conspecific only).

**Same-sex *Stenopus hispidus* (peer-reviewed-era, abstract only)**
- Value found: same-sex strangers "usually fought to the death"; no appeasement; transfer to new surroundings "seemed to increase the over-all level of aggression." The abstract says *Stenopus* recognises species as well as individuals, but does not clearly state how it treats other species.
- Source: Johnson VR (1969/1977) "Behavior associated with pair formation in the banded shrimp *Stenopus hispidus*." https://scholarspace.manoa.hawaii.edu/items/44fa1883-1822-40c0-8354-a945dba18401
- Grade: **peer-reviewed** (abstract only).

**Cross-species kills (CBS x skunk cleaner)**
- Value found: none. No study tests it. Popular coverage of the 2011 study covers only *Lysmata*-on-*Lysmata*.
- Grade: none.
- Suggestion: keep the 06 §2.14 rule as a sandbox toggle and default it to 0 for cross-species kills, with a molt-window rule only for conspecifics. If it must be non-zero, label the 0.05 as [I] and keep it tied to the molt window.

---

## Gap 5: Sally Lightfoot crab (*Percnon gibbesi*) predation

**Diet study (conference abstract, primary, not peer-reviewed)**
- Value found: animal matter 43.2 % (SD 7.47) of diet across 44 specimens, with 27 food taxa, and two sub-populations (one gastropod-and-*Gelidium*, one crustacean-and-*Cladophora*). Stomach analysis found no described hunting of fish or shrimp. Interactions with wrasses were rare and not predatory. Small and medium crabs forage about 50 % of the time, peaking at dusk.
- Source: CIESM Congress 2004 abstract, "Notes on the biology of *Percnon gibbesi*." https://ciesm.org/online/archives/abstracts/pdf/37/CIESM_Congress_2004_Barcelona_article_0326.htm
- Grade: **institutional/conference** (not peer-reviewed).

**Vendor care statements (hobby/vendor)**
- Value found: "may attempt to catch small, slow, or weakened fish, especially at night"; "in larger reef systems with active fish, predation is extremely rare." "May prey on very small snails, tiny hermits, feather dusters," especially after molt or when food is scarce. "May chase small fish or shrimp in tanks with limited space." No rates, no cited study. The page also mixes up the species (*Grapsus grapsus* in the overview).
- Source: Top Shelf Aquatics, Sally Lightfoot crab. https://topshelfaquatics.com/pages/saltwater-invertebrates/sally-lightfoot-crab
- Grade: **hobby/vendor**.

- Suggestion: the 0.01 per night for a sleeping fish in 06 §2.15 is not supported by any rate. Keep predation on fish as an opportunistic, size-gated event with a low [I] value, and make it clearly tunable. Keep the snail and hermit rates, which the vendor and the diet study both support, and label them [I].

---

## Pointers in 06 §6

- **Baer 1980** (acanthurid agonistic ethogram): record only, text not accessed in this search. Not a source for a number yet.
- **Grutter 1994 ch. 5** (per-client inspection rates): not retrieved in this search. Not a source for a number yet.

---

## Summary

| Gap | Sourced? | Best grade |
|---|---|---|
| Lawnmower chase proportions | Yes (percentages, not per hour) | peer-reviewed |
| Dottyback chase rate per hour | No | none |
| Species swim speeds in BL/s | Partly (family-level cm/s; larval BL/s) | peer-reviewed, not species-level |
| Engineer goby adult diel activity | No (juvenile night behaviour only, hobby) | hobby |
| Shrimp killing shrimp | Partly (conspecific only) | peer-reviewed |
| Sally Lightfoot predation | Partly (diet study; vendor for fish) | conference / vendor |
