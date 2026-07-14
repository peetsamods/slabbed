# Slabbed recorder adapter

Use the repo-native schema-3 consumers instead of the installed legacy `$record` summarizers:

```bash
tools/recorder/slabbed-recorder-summarize RECORDER_OR_EVIDENCE_DIR
tools/recorder/slabbed-recorder-capsule-json RECORDER_OR_EVIDENCE_DIR
```

Optional arguments:

- `--run-id UUID` selects exactly one discovered schema-3 base/child run by manifest UUID.
- `--require-player-pairs` exits 7 on otherwise non-red valid evidence unless authoritative player
  pairs are present and unambiguous; valid red evidence still exits 1.
- `--require-c3-pair-fields` implies `--require-player-pairs` and exits 7 unless the selected
  schema-3 run advertises the complete extended action header/capability and contains at least one
  unambiguous player-authored door LOWER/UPPER pair plus one bed FOOT/HEAD pair with exact client
  and server live/stored values and raw bits.
- `--output FILE` writes atomically; otherwise the report goes to stdout.

Producer artifact basenames are reserved and refused as output targets, even outside the selected
schema-3 child, so a derived report cannot overwrite raw recorder evidence.

Exit codes: 0 valid/no red; 1 valid/red; 2 usage; 3 discovery; 4 unsupported schema; 5 integrity;
6 output; 7 required player pairs or strict C3 door/bed capability absent/incomplete. Schema-2 evidence
cannot establish authorship and is rejected. Proxy-only schema-3 evidence is valid but says
`NO_PLAYER_EVIDENCE`.

Full contract: `docs/process/LIVE_RECORDER_ADAPTER.md`.

## C3 candidate closure record

- Architecture review: v8.1 `APPROVE`.
- Full proof: server 27/27, client 18/18, adapter 58/58, and sweep 1,054 items / 3,162
  rows / 8 shards.
- Mutation proof: 24/24 killed and byte-perfectly restored.
- Local behavior commit boundary: `fix(c3): harden cross-chunk capture pairs`.
- TEST 20 remains pending. This record does not close the 26.2 campaign; tag, push, profile,
  live, release, and upload work remain undone.
