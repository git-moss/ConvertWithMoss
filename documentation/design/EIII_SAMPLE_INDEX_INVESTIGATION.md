# E-mu EIII: presets of large Formula 4000 banks read the wrong samples

**RESOLVED 2026-07-28 → PR #252** (`fix-eiii-truncated-sample-indices`). The mechanism: the CD
mastering wrote each zone's 16 bit sample number through an 8 bit tool chain - the low byte is the
true slot modulo 256, the high byte is zero or stale garbage (which unifies the "second defect"
below: same root cause). The damage is per preset; intact presets sit in the same banks (the GM
drum kits). There is no page bit, no second table and no header ID anywhere in the bank - the byte
is destroyed - so `Emulator3SampleIndexRepair` infers the page per preset from the note names in
the sample names, the preset name occurring in the sample names and page feasibility. The "two
sample sets" lead below was wrong; the interleaving fell out of per-preset damage. Warning to
future readers: free-shift pitch-class matching aliases on chromatic sample ladders and produced
convincing-looking bank-specific offsets for a while - restricting candidates to multiples of 256
removes the illusion. Measured result: 4756 references in 821 presets of 32 banks repaired,
missing-sample reports 641 -> 37, every other bank bit-identical. Mechanism documentation lives in
`EIII_FORMAT.md` ("Truncated sample numbers on the library CD-ROMs"); the rest of this file is the
original task brief, kept for the investigation history.

Open investigation, opened 2026-07-28. This is a **task brief**, not a specification: the defect is
proven and measured, the mechanism is not yet known. Read the "Already ruled out" section before
starting - a lot of the obvious ground is already covered and re-walking it costs a day.

## The task

Find out why the presets of some large EIII banks resolve to the wrong samples, then fix
`format/emu/emulator3/Emulator3Detector` so they resolve correctly - without regressing the 305
banks which are correct today.

Do **not** ship a fix that is not backed by evidence. A wrong guess here silently remaps thousands
of zones and is far worse than the current state, because the current state at least fails loudly
enough to be heard.

## Reproduce it

Source: `Formula4000_Vol5_Protozoa.iso` (E-mu EIIIX library CD-ROM), source format **EIII**.

Preset `OBX Strings`, in bank `Vintage+InstrmtX`, at preset offset `0x5254` inside the bank. Its
seven zones read as:

```
BBallJones 1S · BBallJones 2S · BBallJones 3S · BBallJones 4S · BBallJones 5S · LzrSnare 2 · LzrKick 2
```

Basketball bounces, a snare and a kick, in a preset called "OBX Strings". Audibly it is a ~10 Hz
percussion loop (`BBallJones 5S` is 2786 frames at 27777 Hz looping 6-2781).

The correct samples are in the **same bank** at slots 458-464:
`OBXStringD2, OBXStringA2, OBXStringE3, OBXStringB3, OBXStringF4, OBXStringC5, OBXStringG#6` -
seven samples for seven zones.

**This is not an audition-only problem.** The plain detection path - the one every conversion uses -
produces the identical wrong zone list. Every conversion of an affected bank is wrong today.

### The proof that 458-464 is correct

The zones' own `original_key` values are 17, 24, 31, 38, 45, 52, 83. Adding
`Emulator3Constants.KEY_OFFSET` (21) gives MIDI 38, 45, 52, 59, 66, 73, 104 - that is
**D2, A2, E3, B3, F#4, C#5, G#7**, exactly the pitch series named in samples 458-464. Seven for
seven. The samples currently returned have no pitch relationship to the keys at all.

`JX3P Chimes` in the same bank is the same story: it reads index 152 and needs 408 (`Jx3pChimG5`).

## Measured facts about the failing bank

Bank `Vintage+InstrmtX` of `Formula4000_Vol5_Protozoa.iso`, format `EMULATOR_3X`:

| Property | Value |
| --- | --- |
| Samples parsed | 558 (of 999 table slots) |
| Presets | 176 |
| Zones | 1216 |
| Zone byte[2] - the index high byte | `0x00` in **all 1216** zones |
| Zone byte[3] - `parameter_a` | `0x1F` in **all 1216** zones |
| Sample indices referenced | 1..255, **all 255 present**, none above |
| Samples never referenced by any zone | **303** - and **302 of those sit at slot > 256** |
| Bank header `nextPreset` / `nextSample` | 99517 / 8377768 |
| Bank header preset/sample/total blocks | 195 / 16363 / 16558 |
| Bank data length | 8477361 bytes |
| Computed `presetAreaSize` / `sampleAreaStart` | 88470 / 99593 |

A bank in which 54% of the samples cannot be addressed by anything is not a bank the sampler could
have played. Whatever the mechanism, the reader's model of it is incomplete.

## Already ruled out - do not re-investigate

Each of these was measured, not reasoned about.

* **A high bit in the zone.** byte[2] is `0x00` in all 1216 zones and byte[3] is `0x1F` in all of
  them. There are no spare bits in the zone carrying a page.
* **A per-preset flag.** All 142 bytes of the preset header were compared between affected and
  unaffected presets. **No byte separates the two groups.**
* **A sample-header ID like the E4 format uses.** All 92 bytes of the EIII sample header were
  scanned as u8, u16LE and u16BE. None equals the slot, none equals slot-256, none is a unique
  small identifier.
* **Sample-table corruption.** The table is dense and strictly monotonic, has no duplicate entries,
  all 558 headers parse (0 malformed), and the zero slots are all at the end (559-999). Slot 1's
  entry is `0x400000`, so the table start `0x1BD2` is right.
* **Two bank files concatenated by the ISO reader.** There is exactly **one** bank identifier in
  the whole 8.4 MB blob.
* **A global +256 shift.** Affected and unaffected presets are interleaved by preset index.
* **The note-zone map.** `zoneOffset` = end of the note zones, and the note zone's primary /
  secondary byte indexes that zone array. Verified against `OBX Strings`' own offsets.
* **The preset-link merge path.** All seven zones come from `OBX Strings`' own preset offset; no
  linked preset contributes.
* **`BANK_SAMPLE_BLOCKS` (0x40).** A size field, nothing more.
* **Narrowing `ZONE_SAMPLE_INDEX_MASK` from `0x3FFF` to `0x3FF`.** Tested and **rejected**: it
  brings the out-of-range banks down but not into range (`Tine Strings X` holds 10 samples and
  still reports 522).
* **`emu3bm` as an independent check.** It is not one. Same table address `0x1bd2`, same
  `sample_start + addr - SAMPLE_OFFSET` formula, same `MAX_SAMPLES_EMU_3X` of 999. It resolves
  identically, so both implementations share whatever the misconception is.

## The reader is not broken at the 8-bit boundary

Two independent results say the >255 path itself works:

1. **Real data uses it.** The ESI General MIDI banks of `Vol14_ESI32_GeneralMidi.iso` reference
   sample indices up to **511** and read correctly.
2. **Our own round trip survives it.** `EIIIX_Vol08_Vintage.iso` converted to a single EIII preset
   library produced a bank with **272 distinct samples** - past the boundary - and reading it back
   gave **63 of 64 comparable presets with identical zone-to-sample name lists**. The single
   difference is the creator's duplicate-name suffix (`Mid Fast F#1` becomes `Mid Fast F#12`), not
   a wrong sample.

So the affected banks are anomalous **as data** under the current reading, rather than tripping a
generic off-by-N in the reader.

## Cross-format ground truth

Twelve bank names exist in **both** `EMULATOR_THREE` (on `EIIIX_Vol08_Vintage.iso`) and
`ESI_32_V3` (on `F4000_Vol3_AnalogOdyssey.iso`). `OB Synths` has 62 samples in both, and its
`OBX Strings` preset is encoded identically in the two formats - raw index `0x0014`..`0x001A`,
original keys 22/29/36/43/50/57/64, resolving to `OB Strings G1`..`C#5`, correct in both. No zone
in either bank sets the upper byte (2084 zones, all `0x00`).

So the two bank formats agree on how a sample index is encoded, and the
`0x4000` / `0x8000` upper-bit behaviour mentioned in the `ZONE_SAMPLE_INDEX_MASK` comment does not
appear in this pair.

## Blast radius

Across the eight EIIIX library images, 340 banks were read. **Only 29 hold more than 255 samples**,
and only those can be affected:

| Image | Banks over 255 samples |
| --- | --- |
| `Formula4000_Vol5_Protozoa.iso` | 13 of 16 |
| `Vol14_ESI32_GeneralMidi.iso` | 5 of 8 |
| `F4000_Vol2_TechnoTrance.iso` | 5 of 32 |
| `F4000_Vol3_AnalogOdyssey.iso` | 4 of 21 |
| `EIIIX_Vol13_Dance2000.iso` | 2 of 54 |
| `EIIIX_Vol08_Vintage.iso` | 0 of 14 |
| `EIIIX_Vol10_ElementsOfSound_1MB.iso` | 0 of 100 |
| `EIIIX_Vol11_ElementsOfSound_2MB.iso` | 0 of 95 |

The classic EIIIX volumes are clean; the damage is concentrated in the Formula 4000 discs. The
largest banks are `Vintage Keys X` (743 samples), `Vintage PresetsX` (734) and `VK Instruments X`
(661).

## Leading hypothesis

The affected banks are named in pairs - `Vintage PresetsX` / `Vintage InstrmtX`,
`Orbit Presets 4K` / `Orbit Instrmt X`, `Phatt Presets X` - which is the Formula 4000 convention of
shipping a presets bank alongside an instruments bank. `Vintage+InstrmtX` says so in its own name.

That suggests such a bank carries **two sample sets**, with each preset indexing within its own set,
and the reader is walking one flat table across both. It would explain why the needed samples sit
exactly one set-length away from the referenced index, and why only large banks are affected.

It does **not** yet explain why affected and unaffected presets are interleaved by preset index, so
treat it as a lead rather than an answer.

## Experiments worth running, sharpest first

1. **Is the offset always exactly 256, or is it bank-specific?** This is the discriminator. If every
   affected preset in every affected bank needs exactly +256, the cause is a bit or a page. If the
   offset differs per bank and equals a count belonging to that bank (for example the number of
   samples in its first set), the two-sample-set hypothesis is right and the boundary can be
   computed. Use the original-key-versus-sample-name-pitch test described below on
   `Orbit Instrmt X` (633 samples), `Vintage PresetsX` (734) and `Vintage Keys X` (743), not just on
   `Vintage+InstrmtX`.
2. **Inspect the boundary.** Slot 256 is the only unreferenced slot at or below 256 in the failing
   bank. Dump its header and name and compare with slots 255 and 257 - a set boundary may be marked.
3. **Compare the paired banks.** Do the sample names of `Vintage InstrmtX` (590 samples) appear as a
   contiguous run inside `Vintage+InstrmtX` (558) or `Vintage PresetsX` (734)? If one bank's sample
   list is a prefix or suffix of another's, the composition becomes visible directly.
4. **Read the E-mu documentation on bank loading.** The EIIIX Operations Manual is on the Internet
   Archive; the searchable form is the OCR `_djvu.txt`, since the PDF misreports its page count.
   The question to answer is whether the sampler renumbers samples when a bank is loaded on top of
   another, and how a presets bank refers to an instruments bank.

## A second, separate defect found on the way

Five banks emit sample indices far beyond any possible sample, and those zones are silently dropped
as "sample missing":

| Bank | Samples | Highest index referenced |
| --- | --- | --- |
| `Phatt Presets  X` | 493 | 12415 |
| `Conga Set      X` | 36 | 1815 |
| `Rock PercussionX` | 34 | 1802 |
| `Tine Strings   X` | 10 | 1541 |
| `Orbit Presets 4K` | 558 | 2445 |

This is worth its own fix and its own pull request. Note that simply narrowing the mask does not
solve it (see "Already ruled out").

## Contrast: the E4 reader does not have this problem, by design

`format/emu/emulator4/Emulator4Detector` keys its sample map by an index that **each sample chunk
stores inside itself** (`Emulator4Constants.getU16BE (data, offset)`), never by the sample's position
in a table. `Emulator3Detector` does `samplesByIndex.put (i + 1, sample)` - position.

That difference is load-bearing in real data, not theoretical: on the *3-D Audio Collection*,
`3DYamahaC7` has 20 of 73 and `3DPACymbals` 5 of 5 samples whose stored index differs from their
ordinal position. Had the E4 reader keyed by position, those banks would mismap exactly the way the
EIII banks do.

If an equivalent self-identifying field exists somewhere in the EIII bank, keying on it is the
natural fix. The 92-byte sample header has already been scanned for one without success, so it
would have to live elsewhere.

## Tooling notes

There is **no automated test suite** in this repository. Verify by building and running a
conversion, or by driving the backend from a scratch harness.

The instrumentation used so far was scratch-only and has been reverted. To recreate it, add
temporary `System.err.println` calls to `Emulator3Detector`, gated on a system property so a normal
run stays quiet:

* In `parseBank`, after `samplesByIndex` is filled: dump every slot with its table entry, computed
  address, name and frame count; and count zero entries, malformed entries and the highest used
  slot.
* In `parsePreset`, after the name is decoded: record the preset name, its index, its offset and a
  hex dump of its 142-byte header.
* In `parseZone`, after the sample lookup: record the preset name, the raw unmasked u16 at
  `offset + ZONE_SAMPLE_INDEX`, the masked index, the zone's `original_key` and the resolved sample
  name.

A headless harness that drives the real detector through `IDetector.detect (...)` - the plain
conversion path, not the audition path - is enough to collect all of it; run it against a folder
holding a symbolic link to the one ISO under test.

The **original-key-versus-sample-name-pitch test** is the reliable classifier and should be used in
preference to matching preset names against sample names, which produces false positives on
libraries whose samples share name prefixes. For each zone, parse a trailing note name out of the
sample name (`OBXStringD2` gives D), and compare its pitch class with
`(original_key + KEY_OFFSET) % 12`. A preset is affected when its zones match at a shift and not at
zero.

## Definition of done

* The mechanism is explained, not merely compensated for - the fix must say why the index means what
  it means.
* All 29 large banks resolve to samples whose named pitches match their zones' original keys.
* The 305 banks with 255 samples or fewer are unchanged; re-run the before case and compare, do not
  assume.
* `OBX Strings` and `JX3P Chimes` on `Formula4000_Vol5_Protozoa.iso` play their own samples.
* A CHANGELOG entry under *E-mu Emulator III* describing what was wrong and how many zones it moved.
