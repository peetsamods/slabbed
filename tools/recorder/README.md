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
- `--output FILE` writes atomically; otherwise the report goes to stdout.

Producer artifact basenames are reserved and refused as output targets, even outside the selected
schema-3 child, so a derived report cannot overwrite raw recorder evidence.

Exit codes: 0 valid/no red; 1 valid/red; 2 usage; 3 discovery; 4 unsupported schema; 5 integrity;
6 output; 7 required player pairs absent/incomplete. Schema-2 evidence cannot establish authorship and
is rejected. Proxy-only schema-3 evidence is valid but says `NO_PLAYER_EVIDENCE`.

Full contract: `docs/process/LIVE_RECORDER_ADAPTER.md`.
