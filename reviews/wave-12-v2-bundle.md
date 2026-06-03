## WAVE 12 v2 — codex follow-up fixes

Three fixes from previous codex review:
  1. Raid + besiege coverage added — mixin now cancels Explosion't ticks for active wars AND raids AND besieges in the level
  2. Optional-mod safety upgraded — added @Pseudo annotation, mixins.json required:false (was true), defaultRequire:0 already there
  3. Documented side-effect analysis of HEAD cancel in mixin javadoc (computeIfAbsent inits are idempotent; dimWasDay staleness is only consulted in the skipped loop)

### DIFF: warntax.mixins.json
```diff
```

### DIFF: WorldTickHandlerMixin.java
```diff
```
