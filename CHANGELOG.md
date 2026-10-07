# Changelog

## 1.0.0 (first release)

First public release of Last Aid, a survival-medicine overhaul based on First Aid (ichttt) and First Aid New (maoruiQa).

### New in 1.0.0
- **Bleeding**: scratches, lacerations, deep wounds and arterial bleeding per body zone; blood loss spread over the body.
- **Stuck arrows**: pull them out in the body panel, or they tear out after 5 minutes and leave a worse wound.
- **New items**: rag, tourniquet, sewing kit, medical alcohol, antibiotic, dirty rag / bandage, infected rag.
- **Dressings get dirty**: take them off, wash rags and bandages in water or with a water bottle.
- **Infection** (stages 1-4, fever, sepsis, limb necrosis), antibiotics, alcohol as prevention.
- **Anemia** after heavy blood loss.
- **Blood stains** under bleeding players, blood trail when crawling or dragged.
- **Walking on two splinted broken legs** under morphine (slow, no sprint/jump/fighting, strain).
- Gangrene on the player model is a static dark colour (the pulse stays in the body panel).
- Body panel figure with shaded parts; treatment buttons in the condition details.
- `/lastaid` command (alias `/medical`): new `wound`, `infection`, `dirt` and `log` subcommands.
- Tooltips for all medical items, creative tab "Last Aid: Medicine".
- Looping sounds stop on respawn and dimension change.
- Renamed from the First Aid medical fork: mod id `lastaid`, packages `com.duckerquacker.lastaid`. Can't be installed
  together with the original First Aid.

### Already there (from the medical fork)
- Body status panel (H) with all conditions.
- Concussion I-III, hypothermia, common cold, frostbite per limb, gangrene and amputation (Cold Sweat, Primal Winter).
- Pain levels, painkillers, morphine, overdose of morphine and adrenaline with antidotes.
- Fractures and dislocations, splints, healing wrong without a splint.
- Head bandage look, carrying and dragging wounded players, food held in the teeth.
- Create recipes for medicine.
