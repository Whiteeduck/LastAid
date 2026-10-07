# Last Aid

A survival-medicine overhaul for Minecraft (NeoForge 1.21.1), built on **First Aid** and heavily inspired by
**Project Zomboid** and **The Long Dark**.

Your body is split into parts (head, torso, arms, legs, feet). Wounds bleed, bones break, frostbite eats your
fingers, infections creep in through dirty bandages, and the body panel (**H**) shows every condition and lets you
treat it.

> Based on [First Aid](https://github.com/ichttt/FirstAid) by ichttt and
> [First Aid New](https://github.com/maoruiQa/FIrst-Aid-New) by maoruiQa. Licensed under GPLv3 like the original.

## Features

### Body panel
- **H** opens the panel: body figure, health, hunger, fatigue, body and ambient temperature, pain and every active
  condition. Hover a condition for a short summary, click it for details and treatment buttons.

### Bleeding and wounds
- Four stages: **scratch**, **laceration**, **deep wound**, **arterial bleeding**. Blood loss is spread over the whole
  body: a deep wound kills in about two minutes, an artery in under one.
- Where wounds come from: blades, claws and teeth, arrows (they get **stuck** and must be pulled out), tridents,
  maces, explosions, golems and ravagers, cactus and berry bushes, dripstone.
- Treatment:
  - **Rag**, **plaster**, **bandage**: put on a body part through the First Aid apply screen.
  - **Sewing kit**: stitches deep and arterial wounds.
  - **Tourniquet**: stops arterial bleeding on a limb at once, but the limb dies if it stays on for 15 minutes.
  - **Medical alcohol**: disinfects a wound (-80% infection chance).
- Dressings get **dirty**, faster on worse wounds. Take them off in the panel: rags and bandages come back dirty and
  can be **washed** in water or with a water bottle. A dirty dressing left on a wound for a day starts an infection.
- **Blood stains** on the ground under bleeding players (brighter on snow, fading after a few minutes) and a blood
  trail when someone crawls or gets dragged.
- **Anemia** after heavy blood loss: slowness, weakness, less damage. Meat, bread and sleep help.

### Infection
- Stages 1-4: nausea, then fever, weakness and damage, then **sepsis**. Fever shows on the body temperature.
- Comes from dirty dressings, contaminated bites, untreated open fractures, sleeping with open wounds and gangrene.
- **Antibiotic**: -1 stage and no worsening for a day (one dose per day counts). Stage 1 heals by itself under a
  clean dressing on a disinfected wound.
- Sepsis from an arm or leg kills the limb. Amputating the infected limb removes the source.

### Fractures and dislocations
- Hairline, closed and open fractures, dislocations, per arm and leg, from falls, explosions, maces, golems and more.
- **Splint** (body panel, 5 s). Without a splint the bone heals wrong (a lasting penalty).
- Both legs broken: you crawl. With both legs **splinted and on morphine** you can walk slowly (no sprint, no jump,
  no fighting, strain hurts).
- Dislocations are reduced in the panel (painful).

### Cold (Cold Sweat / Primal Winter)
- Hypothermia stages from the Cold Sweat body temperature, common cold, frostbite per limb up to gangrene.
- Gangrene needs **amputation** (surgical knife). Amputated limbs disappear from the player model, armour and held
  items.

### Concussion, pain and drugs
- Concussion I-III from head hits, falls and explosions; rest, sleep, tea and ice packs help.
- Pain levels 0-5, painkillers and morphine.
- **Overdose**: morphine and adrenaline build up; naloxone and beta blockers are the antidotes.

### Other players
- **Carry** a player who can't walk (empty hand, right click) or **drag** them (sneak + right click).
- If both arms are useless, held food is drawn in the player's teeth.

## Items

| Item | Recipe |
|---|---|
| Rag | 1 wool → 2, or 1 leather armour piece → 3 |
| Bandage | 3 rags + string → 2 |
| Plaster | rag + paper + slime ball → 4 |
| Tourniquet | leather + stick + string |
| Sewing kit | iron nugget + 2 string + leather (5 uses) |
| Medical alcohol | Create mixing (heated): glass bottle, 2 sugar, potato, wheat, water. Without Create: crafting |
| Antibiotic | Create mixing (heated): alcohol, honey bottle, glow lichen, brown and red mushroom. Without Create: crafting |
| Splint | 2 sticks + leather + string |
| Naloxone / beta blocker | Create mixing |

All items are in the **Last Aid: Medicine** creative tab and have tooltips.

## Commands (operators only)

`/lastaid` (alias `/medical`): `heal`, `restore`, `clear`, `info <player>`, `log <player>` (last 20 medical events),
`wound <players> <zone> <0-4> [arrow]`, `infection <players> <0-4> [zone]`, `dirt <players> <zone> <days>`,
`fracture`, `dislocate`, `bonetime`, `concussion`, `cold`, `frostbite`, `amputate`, `overdose`, `sneeze`.

## Configuration

All files are in the `config` folder. Medical server settings: `lastaid-medical-server.toml`, client: `lastaid-medical-client.toml`. The
original First Aid settings are in `lastaid-server.toml` / `lastaid-client.toml`.

New sections in 1.0.0:

- `[bleeding]`: `hpPerSecondLight/Medium/Heavy/Arterial`, `arrowHpPerSecond`, `lightStopsAfterSeconds`,
  `unsuturedDressingMultiplier`, `healDays*`, `dirtyHours*`, `tourniquetWarnMinutes`, `tourniquetNecrosisMinutes`,
  `maxBloodDecals`, `decalFadeMinutes`, `decalFadeSnowMultiplier`, `rag*`
- `[wounds]`: `arrowExtractSeconds`, `arrowAutoEjectMinutes`, `sutureSeconds`, `sharpLacerationChance`,
  `bluntScratchChance`, `biteInfectionChance`, `explosionMinDamage`, `extractPain`, `extractPainSeconds`
- `[infection]`: `stageHours`, `dirtyDressingInfectionDays`, `contaminationHours`, `alcoholPreventionChance`,
  `openFractureInfectionHours`, `openFractureInfectionChance`, `openWoundSleepChance`, `coldSleepMultiplier`,
  `antibioticCooldownDays`, `antibioticStages`, `stage1HealHours`, `sleepHealMultiplier`, `feverStage2/3/4`,
  `stage2/3/4Damage`, `stage2/3/4IntervalSeconds`, `necrosisHours`
- `[anemia]`: `hpThreshold`, `windowSeconds`, `durationDays`, `foodRecoveryBonus`, `attackPenalty`
- `[fracture]`: `allowWalkOnSplintedBrokenLegs`, `walkSpeedWithSplints`, `strainDamageChance`, `strainDamage`,
  `strainIntervalSeconds`, `splintWalkMaxPain`, `strongPainkillerMorphineDoses`
- `[general]`: `debugLogging`, `medicalLogSize`

## Compatibility

- **Required:** NeoForge 21.1.219+ for Minecraft 1.21.1.
- **Optional:** Cold Sweat 2.4+ (body temperature), Primal Winter, Create 6 (recipes), Player Revive.
- **Not compatible with the original First Aid**: Last Aid already contains it. The game refuses to start with both.

## Building

Standard NeoForge MDK project: `./gradlew build`.

## License

GPL-3.0 (see `LICENSE`); the API package (`com.duckerquacker.lastaid.api`) is LGPL-2.1 (see `LICENSE-API`), like
the original First Aid.
