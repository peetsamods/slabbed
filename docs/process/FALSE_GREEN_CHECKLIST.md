# Slabbed False-Green Checklist

Use this when a test, recorder, build, or audit is green but Julia or live proof still shows wrong behavior.

## First Question

What exactly is green, and what exactly is still red?

Write both in the report before patching.

## Checklist

| Check | Question | Action |
|---|---|---|
| Product surface | Is the proof checking the surface Julia sees: model, outline, raycast/interaction, placement result, collision, or persistence? | Add or switch to the proof surface that matches the symptom. |
| Freshness | Is the fixture fresh, or can stale anchor/frozen/compound state affect it? | Move to fresh coordinates and separate `/setblock` from player placement. |
| Authority | Which owner should change: geometry, anchor, placement intent, model render, outline/raycast, collision, or profile setup? | Patch only that owner first. |
| Donor mismatch | Did the donor fix solve the same mechanism or only the same visible symptom? | Re-read the donor code and map the target mechanism before porting. |
| Registration | Is the new mixin/class/test actually registered and run? | Verify source set, mixin JSON, test discovery, and runtime logs as needed. |
| Live profile | Was the correct jar/profile/mod set tested? | Re-run live preflight and record jar/profile facts. |
| Visual triad | Do model, outline, and raycast/interaction all agree? | Capture a triad proof or use recorder counters that check all three. |
| Survival/update/reload | Does the behavior survive neighbor updates, reload, break/replace, and hand placement? | Add the smallest lifecycle check relevant to the symptom. |

## Required Response

1. Name the false-green surface.
2. Name the still-red surface.
3. Add or identify a symptom-specific RED proof.
4. Patch one authority boundary.
5. Run the named proof.
6. Stop after two failed patch attempts and ask for or run a read-only audit.

## Do Not Accept As Closure

- A build when the bug is visual or behavioral.
- A gametest when the bug only appears in live rendering or targeting.
- A screenshot when raycast/interaction/collision is the reported problem.
- A donor commit that was copied without proving it is registered and active on the target line.
