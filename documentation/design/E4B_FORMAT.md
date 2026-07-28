# E-mu Emulator IV Bank Format (E4B)

Banks of the E-mu Emulator IV series (Emulator 4, E4X, E4XT, E4K, e-Synth,
e-6400 and the other EOS samplers, 1994-2002) are stored in single `*.e4b`
files which contain all presets, their parameters and the sample data.

The format is not documented by E-mu. The byte-level layout used here was
reverse-engineered by the **mpc2emu** project (GPL-2.0-or-later,
`docs/E4B_FORMAT.md` and `writers/e4b_writer.py`/`parsers/e4b_parser.py` in its
source tree) by differential analysis of hardware-saved E4XT banks (created by
Jan Lentfer), commercial EOS CD-ROMs, the `struct emu3_sample` layout of
[emu3bm](https://github.com/dagargo/emu3bm) by David García Goñi and Phil's E4
format notes (philizound.co.uk). The implementation in
`format/emu/emulator4` is an independent Java implementation based on that
documentation; no code was copied.

## Container

An E4B file is an IFF-like container. All chunk sizes and all indices are
big-endian; the fields inside the E3S1 sample struct are little-endian.

```
FORM <size> E4B0
  TOC1   table of contents, one 32-byte entry per chunk below (not EMSt)
  E4Ma   256-byte MIDI multimap (channel -> preset routing)
  E4P1   one chunk per preset
  E3S1   one chunk per sample (16-bit mono little-endian PCM)
  EMSt   1366-byte master setup, always the last chunk, NOT listed in the TOC
```

Chunks are word-aligned (a pad byte follows an odd-sized chunk). Two quirks of
the `FORM` size field:

* Hardware-saved EOS 4.x banks use the EMU convention `size = filesize - 12`,
  i.e. 4 **less** than the standard IFF value - the 4-byte `E4B0` form type is
  not counted. The declared FORM boundary therefore ends 4 bytes short of the
  file end, inside the trailing zeros of the `EMSt` chunk (which is why `EMSt`
  must be last: when streaming from CD the E4XT clips at the FORM boundary).
* Banks written by older EOS versions exist in the wild **without** an `EMSt`
  chunk and with a FORM size *larger* than the file. The detector therefore
  ignores the FORM size completely and walks the chunks against the real file
  length; the creator writes the EMU convention including the trailing `EMSt`.

The `E4Ma` and `EMSt` blocks are not fully decoded; the creator writes the
defaults captured from hardware-saved banks ("all presets on all channels" and
the "Untitled MSetup" block).

### TOC1 entry (32 bytes)

| Offset | Size | Content |
|---|---|---|
| 0  | 4  | chunk tag (`E4Ma`, `E4P1`, `E3S1`) |
| 4  | 4  | chunk data size |
| 8  | 4  | absolute file offset of the chunk tag |
| 12 | 2  | index: 0 for `E4Ma`, 0-based preset index, **1-based** sample index |
| 14 | 16 | name, space-padded ASCII |
| 30 | 1  | 0 |
| 31 | 1  | MIDI program number (0 = any) |

## Preset (E4P1)

An 82-byte header followed by the voice blocks, packed back-to-back:

| Offset | Size | Content |
|---|---|---|
| 0  | 2  | preset index (0-based) |
| 2  | 16 | name, space-padded ASCII |
| 19 | 1  | 0x52 constant |
| 20 | 2  | number of voices |
| 28 | 1  | preset volume (0x78 = default) |
| 41 | 1  | 0x04 if more than one voice |
| 43 | 1  | 0x01 if more than one voice |
| 52 | 4  | 0x52 0x23 0x00 0x7E constant |
| 56 | 4  | 0xFF 0xFF 0xFF 0xFF (MIDI any note/any channel) |
| 82 |    | voice blocks |

## Voice block

284 fixed bytes followed by `n * 22` bytes of zone entries. There is **no
separator** between voices; the next voice starts right after the zone table.
Only the last voice of a preset is followed by two zero bytes. The 16-bit
value at voice offset 2 is `284 + n * 22` (relative to the voice start), which
is how the number of zones - and the start of the next voice - is found.

```
voice[  0:110]  voice parameters
voice[110:174]  primary zone table (envelopes, LFOs)
voice[174:190]  zero
voice[190:270]  modulation cord table (20 cords of 4 bytes)
voice[270:284]  zero
voice[284:   ]  zone entries (22 bytes each)
```

Used voice parameters (all single bytes unless noted):

| Offset | Content |
|---|---|
| 2:4 | zone table end offset (u16, see above) |
| 4   | number of zones |
| 7   | 0x64 constant |
| 14/17 | key window low/high |
| 18/21 | velocity window low/high - **must mirror** the min/max of the zone entries, otherwise velocity layers stack instead of switching |
| 25  | 0x7F constant |
| 34  | key transpose (signed semitones) |
| 35  | coarse tune (signed semitones) |
| 36  | fine tune (signed, 1/64 semitone units) - only used by a voice with a single zone, see below |
| 38  | 1 = non-transpose (fixed pitch); such a voice needs a populated cord table to be valid |
| 42  | chorus amount (0-127) |
| 51  | 0x80 constant |
| 54  | voice volume (signed dB) - only used by a voice with a single zone, see below |
| 55  | voice panning (signed, -64 = full left ... 0 = centre ... +63 = full right) - only used by a voice with a single zone, see below |
| 58  | filter type (see below) |
| 60  | filter cutoff: 0 = ~57 Hz ... 255 = 20 kHz, exponential |
| 61  | filter resonance 0-127 |

Filter types (`byte = group | variant`, variant in the low 3 bits): low-pass
4/2/6-pole = 0x00/0x01/0x02, high-pass 2nd/4th = 0x08/0x09, band-pass 2nd/4th =
0x10/0x11, contrary band-pass (notch-like) = 0x12. The remaining groups (swept
EQ 0x20+, phaser 0x40+, flanger 0x48, vocal 0x50+, morph 0x60+) are EOS effect
filters without a model equivalent.

### Primary zone table

Two 6-stage envelopes as rate/level byte pairs, plus the LFO parameters. The
amplitude envelope stages are at bytes 0-11 (attack1, attack2, decay1, decay2,
release1, release2), the filter envelope mirrors this at bytes 14-25. Each
stage ramps to its own level in its own time, so the level a voice really
sustains at is the one of **decay 2**, not of decay 1.

Mapping the six stages onto the attack/hold/decay/sustain/release of the model
works exactly as for the Emulator X, whose envelope this is: the two attack
and the two release times are added up, and a decay 1 stage which stays at the
level of attack 2 is a plateau and therefore the hold stage, which leaves
decay 2 as the decay. Only where decay 1 does fall do the two decay times add
up. Taking decay 1 alone loses the tail of 52% of the voices of the Producer
Series Vol. 1 and 2 CD-ROMs, and the 8% whose decay 1 holds the peak (a
plucked instrument which rings and then fades away, e.g. `Nylon Guitar`) end
up sustaining for ever instead of decaying at all.

The rate <-> time law was calibrated on E4XT hardware by mpc2emu:
`seconds = 0.0310 * e^(0.0581 * rate)`, rate 0 = instant, 127 = ~47 s. Levels
are stored as `round(percent * 127 / 100)` (signed for the filter envelope).

The filter envelope shape alone is inert: its depth is the amount of the
"cord 5" modulation route (below).

### Modulation cord table

20 cords of `[source, destination, amount, 0]`; amounts are signed
(`round(percent / 100 * 127)`). The EOS factory default cord set occupies the
first 8 slots. Routes used by the converter:

| Slot | Route | Use |
|---|---|---|
| 0 | velocity (0x0A add / 0x0B centered / 0x0C subtract) -> amp (0x40) | velocity modulation of the amplitude, default amount 0x1E |
| 5 | filter envelope (0x50) -> filter frequency (0x38) | the depth of the filter envelope |
| 6 | key (0x08) -> filter frequency (0x38) | filter key tracking; +100% tracks ~0.713 octave per octave |

An all-zero table is valid for a plain key-tracking voice, but a
**non-transpose voice requires the populated default table** to be recognized
by the hardware, so the creator always writes the factory default set.

### Zone entry (22 bytes)

| Offset | Content |
|---|---|
| 2  | low key |
| 5  | high key |
| 6  | low velocity |
| 9  | high velocity |
| 10:12 | sample index (u16, 1-based) |
| 12:14 | fine tune (signed **big-endian** u16, 1/64 semitone units) - only used by a voice with several zones, see below |
| 14 | root key |
| 15 | volume (signed dB, like the voice volume) - only used by a voice with several zones, see below |
| 16 | panning (signed, -64 = full left ... 0 = centre ... +63 = full right) - only used by a voice with several zones, see below |

The fields at 12, 15 and 16 are the fine tuning, the volume and the panning of
the zone. They are **not** offsets on top of the voice fields 36, 54 and 55 but
the alternative to them, and the two are never combined:

* a voice with a **single** zone stores the three values in its voice
  parameters 36/54/55 and leaves the zone entry fields at zero,
* a voice with **several** zones stores the absolute value of each zone in its
  own zone entry and leaves the voice parameters 36/54/55 at zero.

Editing the aggregated volume, panning or fine tuning of a multi-zone voice on
the front panel only changes the voice parameters and never the zone entries,
so both really are independent.

The three fields were recovered from the E-mu Producer Series CD-ROMs (76057
zones) and then confirmed on E4XT hardware by the mpc2emu project (see
git-moss/ConvertWithMoss#220 and the `E4B_FORMAT.md` of mpc2emu), which also
disproved the offset interpretation they were first read with: a bank written
with the zone entries holding offsets shows the raw offsets on the front panel
instead of the intended sums.

* The two bytes at 12:14 only ever hold 0x00 or 0xFF in the high byte, exactly
  matching the sign of the low byte, which identifies them as one signed
  big-endian value (observed range -59..+48). Measuring the recorded pitch of
  the samples against the root key of their zone shows the field to be a
  correction of that deviation: applying it as 1/64 semitones reduces the
  average pitch error of a bank from 12.3 to 7.7 cents (as cents it would only
  reach 8.7), which also matches the 1/64 semitone resolution the rest of the
  format uses for tuning.
* Byte 15 has the small signed range -17..+10 of a level trim and is used by the
  mixed instrument presets ('Percussion Section') which balance their zones.
* Byte 16 spans the full signed -64..+63 and its extremes dominate; the presets
  which use it are the stereo spreads ('Wide', 'Percussion Section',
  'Congas/Bongos') while the plain single instrument presets leave it at 0.

## Sample (E3S1)

A 94-byte header (2-byte sample index + the 92-byte EOS sample struct known
from emu3bm) followed by 16-bit little-endian mono PCM. All position fields
are byte offsets relative to the struct start (i.e. 92 = first PCM byte):

| Offset | Size | Content |
|---|---|---|
| 0  | 2 | sample index (1-based, big-endian) |
| 2  | 16 | name, space-padded ASCII; the root note is conventionally appended (e.g. `_C3` for MIDI 60, octave = note/12 - 2) |
| 22 | 4 | start, left channel = 92 |
| 26 | 4 | start, right channel |
| 30 | 4 | end, left channel = 92 + PCM bytes - 2 |
| 34 | 4 | end, right channel |
| 38 | 4 | loop start, left channel |
| 42 | 4 | loop start, right channel |
| 46 | 4 | loop end, left channel |
| 50 | 4 | loop end, right channel |
| 54 | 4 | sample rate - informational only, inherited from the EOS 3 struct |
| 58 | 2 | pitch offset in 1/64 semitones (signed) |
| 60 | 2 | options: 0x0001 = forward loop, 0x0020 = holds the left channel, 0x0040 = holds the right channel; 0x0020 = mono, 0x0031 = mono with forward loop |
| 62 | 4 | data offset = 92 |

Every position exists twice, once per channel - the struct is the one of the
Emulator III. A sample which only holds its **right** channel (option 0x0020
clear, e.g. options 0x0059) keeps its positions in the second field of each
pair and leaves the first one with a stale value which does not address the
sample: the loop start reads as the constant 12 and the same loop end appears
verbatim on several samples of different lengths. Reading the left fields
unconditionally therefore gives those samples the loop of an unrelated sample
or drops it altogether. Of 7835 looped samples on three commercial CD-ROMs
(Producer Series Vol. 1 and 2, E4 Ultra Production Set) 1402 are right channel
only, and selecting the pair by the option bit makes all 7835 resolve inside
their sample.

EOS has exactly one loop type (forward, on/off at the sample level). A forward
loop shorter than ~84 frames plays an octave low on the hardware (the E4XT
doubles it silently).

The sampler always plays a sample back at 44100 Hz; the playback pitch of a
sample stored at a different rate is set by the pitch offset at 58 and *not*
by the sample rate field at 54, which the firmware ignores for this. The
offset was reverse-engineered by the mpc2emu project from E4XT hardware
captures (the Sample-Rate-Convert function applied to a set of known rates,
the resulting banks diffed against an untouched one) as

    offset = round (768 * log2 (rate / 44100))

which holds within ±2 units over 11025-48000 Hz. A sample written with an
offset of 0 therefore plays back transposed by the ratio of its rate to
44100 Hz - a 22050 Hz sample sounds one octave too high.

## EOS disk filesystem (CD-ROM and hard disk images)

The EOS samplers do not read standard filesystems from CD-ROM (and only EOS
4.7+ reads FAT hard disks); their media use E-mu's own filesystem, known from
the emu3fs Linux kernel module and reverse-engineered for the Emulator IV by
the mpc2emu project (`docs/EMU3_ISO_FORMAT.md` and
`docs/re_procedures/emu_hdd_fs.md` in its source tree). All values are
little-endian; everything is addressed in 512 byte blocks:

```
Block 0:  superblock - magic 'EMU3', then u32 fields: total blocks - 1,
          root start/blocks, dir-content start/blocks, cluster list ('FAT')
          start/blocks, data start block, total clusters; byte 0x28 = cluster
          size (bytes = 1 << (15 + value)); checksum at 0x1FE = sum of the
          255 u16 words of bytes 0x000-0x1FD (checked by the firmware!)
Block 1:  byte 0 = next free dir-content block
FAT:      u16 per cluster: next cluster of the chain, 0x7FFF = last,
          entry 0 = 0x8000 (reserved)
Root:     32-byte folder entries: name[16], 0, type (0x40 = user folder,
          0x80 = hard disk 'Default Folder'), 7 x u16 dir-content block
          indices (0xFFFF = unused)
Dircon:   16 x 32-byte file entries per block: name[16], 0, id, u16 start
          cluster (1-based), u16 clusters, u16 blocks used in the last
          cluster (a partial block counts as a whole one!), u16 bytes used
          in the last block (1..512, never 0 - see below), type (0x81),
          5 bytes props (0x00 'E4B0')
Data:     cluster c starts at block 'data start' + (c - 1) * blocks/cluster
```

The CD variant uses the fixed geometry FAT=2+5, root=7+4, dircon=11+125,
data=136 with cluster sizes of 512 KB/1 MB/2 MB (the smallest which keeps the
cluster count under the 1279 the 5 FAT blocks can hold; 512 KB is preferred as
larger clusters caused read errors on hardware); the hard disk variant uses
FAT=2+4, root=6+7, dircon=13+169, data=182. The reader takes all geometry
from the superblock and therefore reads both.

The 'bytes used in the last block' of a file entry must be counted together
with the block count, so that it is 1..512 and never 0: a file whose last
cluster happens to be an exact multiple of 512 bytes long would otherwise be
described one block short and the firmware aborts its load with an end of file
error (found and confirmed on an E4XT by the mpc2emu project). The reader
accepts a 0 as a full block, since images written by other tools can contain
it.

## Mapping decisions of the converter

* **Reading:** every preset becomes one multi-sample source, every voice one
  group. The voice transpose and coarse tune are applied to all its zones,
  while the fine tuning, the volume and the panning are taken from the voice
  for a single-zone voice and from the zone entry for a multi-zone voice (see
  above); a non-transpose voice sets key tracking 0. A fully open
  4-pole low-pass without resonance, envelope depth and key tracking is the
  EOS bypass state and creates no filter.
* **Writing:** every zone becomes its own voice with a single zone entry,
  which keeps the per-zone tuning, volume, panning, filter and envelopes
  (hardware banks typically map many zones into one voice; both layouts are
  valid). Since every written voice has exactly one zone, the fine tuning, the
  volume and the panning go into the voice parameters 36/54/55 and the
  matching zone entry fields stay zero.
  Stereo samples are mixed down to mono (the stereo variant of the sample
  struct is not covered by the mpc2emu reverse-engineering); sample rates
  above 48 kHz, the EOS maximum, are down-sampled. Samples are
  de-duplicated by content. Trims (zone start/stop) are not applied; use the
  trim processing option instead.
* Bank limits: 1000 presets and 1000 samples (S000-S999/P000-P999 per the EOS
  manual, which is also why the zone sample index must be 16-bit).

## Status

Read and write are validated against the mpc2emu reference parser and against
hardware-created third-party banks (Ian Wilson's free EOS banks: 198 presets,
1980 zones, 317 samples - all sample PCM byte-identical, all zones resolved).
The voice and zone entry fine tuning, volume and panning were additionally
confirmed on an E4XT by the mpc2emu project. Written banks have **not** been
loaded on real hardware yet.
