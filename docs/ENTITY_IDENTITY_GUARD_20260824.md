# Cortex People / Projects Identity Guard — 2026-08-24

Source: on-device review of PRIME People / Projects and Archive screenshots.

## Product law

**A mention is not an identity. An inferred phrase is not a project.**

Cortex must distinguish:
- mentioned
- inferred candidate
- identified
- explicitly confirmed

## Observed failures

- Name-only Person nodes could aggregate unrelated phone-number evidence under one display name.
- People UI also deduped rows by normalized display name, creating a second over-merge path.
- Legacy `entities` migration promoted PERSON / PROJECT extraction directly to active `entity_nodes`.
- Project regex extraction had sentence-fragment failure modes, creating labels such as partial phrases rather than project names.
- Legacy inferred Projects appeared beside real Projects without a confirmation distinction.
- Contact titles could leak source suffixes such as `Phone` into the person display name.
- Users had no correction controls on the PRIME entity surface.

## Implemented guard

### People
- Legacy migrated name-only PERSON / CONTACT nodes are quarantined from the PRIME surface.
- Android Contacts are rebuilt as stable Person nodes.
- New imports preserve Android `CONTACT_ID`; this becomes the preferred entity anchor so multiple phone numbers for one Android contact can resolve to one Person.
- Old contact memories without `CONTACT_ID` fall back conservatively to canonical phone identity rather than unsafe name matching.
- Original names and phone values remain aliases/evidence.
- `Phone`, `Web Phone`, and `Facebook Phone` suffixes are removed from the Person display name only; stored evidence remains unchanged.
- People UI no longer deduplicates by name.
- Rename and Hide Person corrections are available and persist to the graph.

### Projects
- Active legacy inferred Project nodes are quarantined.
- PRIME Projects shows only explicitly confirmed Project nodes.
- Inference may create `PROJECT_CANDIDATE`, never Project.
- Candidate names pass `EntityQualityPolicy` to reject obvious fragments/generic garbage.
- Old weak pending candidates are dismissed by idempotent graph maintenance.
- `Create project` validates the name again at the confirmation boundary.
- Explicit confirmation can promote a matching quarantined legacy node into a confirmed Project.
- `Not a project` feedback dismisses candidates/entities.

### Provenance
- Notification provenance now has package-label fallbacks for common apps such as Gmail, Google Messages, Truecaller, WhatsApp, Messenger and Instagram.
- Notification subtype can be inferred conservatively from known source app when Android metadata is missing.

## Validation required

Code-complete is not runtime-verified. After compilation/install, verify:
1. The previous name-only People nodes no longer show aggregated unrelated numbers.
2. Same-name contacts are not merged merely because the names match.
3. Legacy garbage Projects disappear from confirmed Projects.
4. Only clean candidates appear as candidates, with explicit Create / Not a project actions.
5. Renaming a person survives screen reload/bootstrap.
6. Gmail / Google Messages / Truecaller show friendly provenance instead of raw package names where possible.
