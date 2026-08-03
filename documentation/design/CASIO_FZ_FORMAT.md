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

## Bare dump files

* A bare dump per the document is the file head followed by the content blocks.
* The files written by the `fzdump` MIDI utility - the format in which the circulating
  libraries (factory, Soundwaves, Shareware, Livewire, Swedish Users Club) come - have **no**
  file head: the content starts directly with the first block. The layout is recovered from
  three u16 counters in the system area at the end of the first 1,024-byte block: number of
  voices at offset 1008, content length in sectors at 1010 and wave blocks at 1012; bank
  blocks = content sectors - wave blocks - voice blocks. These bytes lie in the padding after
  the block parameters (bank = 656, voice = 4 x 192 bytes), so they are present no matter
  which block type occupies the first sector: `fzf` (first bank block), `fzb` (the bank
  block), `fzv` (the voice block; voices = 1, banks = 0) and even full dumps without any
  banks (`MN_15/Full Dump.fzf` of the Swedish Users Club CDs). Verified against ~9,700
  files: 36 factory + 197 Soundwaves/Shareware/Livewire `fzf`, 8,840 `fzv` and 652 `fzb` of
  the Swedish Users Club CDs, where the counters match the file size exactly.
* The two layouts are distinguished per type: for `fzv`/`fzb` the counters must be
  consistent with the type (exactly 1 voice/0 banks resp. 1 bank); for `fzf` additionally
  the first block pointer pair decides: a file head holds a valid sector range
  (2 <= start <= end < 1280) or zeroed pointers with plausible counters, a bank block starts
  with the area count followed by the ascending high keys, which cannot form such a range.
* Some circulating files are truncated (shorter than the counters claim, e.g. `Tenor-Alto
  Sax.fzf` of factory disk FL-6 and several Soundwaves disks); the missing wave data is read
  as silence. Empty 1-sector voice dumps exist as well (a no-sound voice, no wave data).
* The effect parameters (offset 960) are only valid in full dumps: all surveyed `fzf` files
  hold a plausible pitch bend range there (<= 96 = 12 semitones, mostly the default 24),
  while ~65% of the `fzv`/`fzb` files hold stale memory (179, 213, 255, ...). The bend range
  is therefore only read from full dumps and only when it is at most 96.

## Decisions and approximations

* **Wave addresses**: `wavst`/`waved`/`genst`/`gened` and the loop addresses are word
  addresses. Full dumps store the wave memory from address 0, so the addresses index the wave
  pool directly. If the addresses of a file do not fit into its wave pool (bare voice dumps of
  other tools), they are treated as relative to the smallest wave start instead.
* **`vp` of a bank**: contains the voice number (0-63) in files, per the document. Bit 15
  marks an area without an assigned voice (the low bits then point beyond the stored voices,
  volume is 0 and the generator count 255, e.g. the placeholder areas of the 'YOUR TURN'
  banks of the Soundwaves library); such areas are skipped.
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
* **Sounding mode is a bit field**: factory voices also contain undocumented values (e.g.
  `0x0157` = normal without bit 7). Only `0x0000` is treated as silent and only `0x101D` as
  reversed, everything else plays normal.
* Writing has not been verified on real hardware yet; the reader and an independent
  re-implementation of the layout agree on every field of written images.
