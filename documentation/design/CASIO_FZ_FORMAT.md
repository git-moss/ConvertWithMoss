# Casio FZ-1/FZ-10M/FZ-20M Format Notes

The implementation in `format/casio` follows the official document *"Casio Digital Sampling
Keyboard Model FZ-1 Data Structures (For Software Developers)"*, Casio Tokyo, March 18, 1987.
This note only records the details which the document leaves open and the decisions taken for
the conversion. All multi-byte values are little-endian; in the C listings of the document
`short` is 1 byte, `int` 2 bytes and `long` 4 bytes.

## Disk

* Double sided, 80 tracks, 8 sectors/track, 1,024 bytes/sector = 1,310,720 bytes.
  Logical sector `loc = 16 * track + 8 * head + (sector - 1)`.
* Sector 0: disk name (12 chars + `00 00 02 00`), password (12 chars + 4 zero bytes), from
  offset 128 the cluster allocation table (one bit per sector, LSB first, 1 = used).
* Sector 1: directory, 64 entries of 16 bytes: name (12), `ext` (u16: low byte = type 0-5,
  high byte = disk number for files spanning two disks), start sector (u16).
* A file = one head sector (64 sector ranges of `start`/`end` u16 pairs, terminated by 0/0;
  work area; the last 6 bytes hold three u16 counters) + the content sectors.
* Counters per type: full dump (0): voices/banks/wave blocks; voice (1): 1/0/wave blocks;
  bank (2): voices/1/wave blocks.
* Content order: bank blocks (656 bytes each, effect parameters at offset 960 of the first
  block, 24 bytes), voice blocks (4 voices of 192 bytes per block, 256 bytes stride), wave
  blocks (512 16-bit samples per block, little-endian).

## Decisions and approximations

* **Wave addresses**: `wavst`/`waved`/`genst`/`gened` and the loop addresses are word
  addresses. Full dumps store the wave memory from address 0, so the addresses index the wave
  pool directly. If the addresses of a file do not fit into its wave pool (bare voice dumps of
  other tools), they are treated as relative to the smallest wave start instead.
* **`vp` of a bank**: contains the voice number (0-63) in files, per the document.
* **Envelope rates**: the rate law (0-127) is not documented. It is approximated as: a full
  swing over the complete level range takes 60 s at rate 0 and halves every 8 steps (~1 ms at
  rate 127); a stage scales with its level distance. See `CasioFZVoice.stageSeconds`.
* **Filter cutoff**: the `dcf` law (0-127, 127 = open) is not documented. It is approximated
  exponentially from ~20 Hz to ~18 kHz. The filter envelope is converted as a cutoff envelope
  modulator with the maximum stop level as its depth, so presets which park the cutoff low and
  open it with the envelope do not convert to silence.
* **Sample rates on write**: samples keep their data unchanged; the difference between their
  actual rate and the declared FZ rate (36/18/9 kHz) is compensated with `dcp` (1/256 semitone
  steps), which restores both pitch and speed since pitch equals playback speed on the FZ.
* **Password**: written as 12 spaces (blank, like unset names in the directory).
* **Multi-loops**: only the sustain loop (`loop_sus` 0-7) is converted; the timed transition
  loops before it have no model equivalent. Reversed mode (`0x101D`) maps to a reversed zone,
  cue mode (`0x2014`) plays as normal.
* Writing has not been verified on real hardware yet; the reader and an independent
  re-implementation of the layout agree on every field of written images.
