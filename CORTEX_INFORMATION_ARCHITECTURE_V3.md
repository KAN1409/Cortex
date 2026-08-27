# Cortex Information Architecture V3

## North star
Cortex is a cognitive operating surface, not a collection of feature pages.

The user-facing mental model is fixed to:

**Now → Inbox → Library → Ask**

Capture is a global action, not a destination.

## Primary surfaces

### Now
Purpose: what deserves attention now.
Only these groups may appear when non-empty:
- Needs you
- Coming up
- Waiting on
- Recently changed

No raw evidence, screenshots, missed calls, or generic memories should surface merely because they are recent.

### Inbox
Purpose: intentional intake.
Anything deliberately sent to Cortex appears here first. Save immediately; enrich asynchronously.
States are user-facing and explicit: Saved / Thinking / Ready / Preserved.

### Library
Purpose: durable organized knowledge.
The four primary object entrances are:
- People
- Projects
- Situations
- Memory

Do not duplicate these entrances with a second tab system. Search spans the whole Library.

### Ask
Purpose: natural-language access to Cortex intelligence and actions.
The normal UI does not expose provider/source routing controls. Cortex selects the route automatically and discloses provenance after the answer.

## Detail pattern
People, Projects, and Situations converge on one detail mental model:
1. Overview
2. Needs attention
3. Timeline
4. Connections
5. Evidence

Evidence is collapsed by default. It explains Cortex; it is not the product surface.

## Navigation rules
- Important information must be reachable within two taps from the primary navigation.
- No user-facing screen may require knowing which ingestion source created an item.
- No duplicate navigation layers for the same concept.
- Technical/test surfaces never appear in the primary navigation.

## Keep / merge / hide policy

### Keep as primary
- CompactTodayActivity → Now
- InboxActivity → Inbox
- ProposalPeopleProjectsActivity → Library
- ProposalAskCortexActivity → Ask

### Keep as secondary
- SettingsActivity
- VaultActivity as Memory/raw archive fallback
- ReviewQueueActivity when judgement is required
- PromptLibraryActivity and CorrectionLearningActivity under Settings

### Hide behind Advanced
- AttentionEvaluationActivity
- RelevanceEvaluationActivity
- CapabilityMatrixActivity
- EnvironmentActivity
- CortexStatusActivity
- CortexAuditActivity
- ExternalModelCheckActivity
- VisualIntelligenceActivity
- OcrTestActivity
- self-review/test surfaces

### Legacy implementation surfaces
Satin/Proposal/base activities may remain as implementation dependencies, but must not create parallel user-facing navigation or competing mental models.

## Product test
A new user should be able to answer these without explanation:
- What needs me now?
- Where do I put something?
- Where do I find a person/project/situation/memory?
- Where do I ask Cortex?
