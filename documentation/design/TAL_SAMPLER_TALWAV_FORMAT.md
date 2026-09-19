# TAL-Sampler encrypted samples (*.talwav)

TAL-Sampler reads every sample file whose name ends with `.talwav` (compared ignoring the case)
through a decryption, and its preset browser creates such files from the WAV files of a preset with
the command 'Convert wav Samples to talwav'. The factory presets 'Classic 80s' and many sample packs
(e.g. the Hollowsun library for TAL Sampler) deliver their samples in this format.

A `.talwav` file is the WAV file encrypted with Blowfish in the electronic code-book mode (ECB) with
a fixed key. The decoder is `format/tal/TALSamplerEncryptedWave`, the cipher `file/Blowfish`.

## Source

Reverse-engineered from TAL-Sampler 4.7.2 for macOS (the arm64 slice of the Audio Unit; the binary
is not stripped):

| Function | Role |
|---|---|
| `TalSampleLoader::createInputStream` | Opens a sample: a file with the extension `.talwav` through `FileCrypto::readEncryptedFile`, any other through a plain `juce::FileInputStream`. The audio format manager then reads the stream. |
| `FileCrypto::readEncryptedFile (juce::File)` | Reads the whole file into a memory block, creates a `juce::BlowFish` with the static 72-byte array `fileCryptokey`, decrypts the block in place and returns it as a `juce::MemoryInputStream`. |
| `PresetBrowser::copySamplesToPresetDirectory (bool)` | Encrypts: only files with the extension `.wav`, read whole, encrypted with a second, identical copy of the key (`fileCryptokey.30`), written as `<name>.talwav` next to the WAV file with a `juce::FileOutputStream`. |

The length of the key is not passed at the call sites: the compiler propagated the constant 72 into
the constructor (`cmp w9, #0x47` wraps the key index after 72 bytes).

## Cipher

* Blowfish by Bruce Schneier: 16 rounds, the P-array and S-boxes start with the hexadecimal digits of
  the fractional part of pi, the standard key schedule packs the key bytes big-endian into the 18
  words of the P-array and repeats the key until the array is filled. The tables of the plug-in are
  the standard ones.
* The key has 72 bytes, the maximum of the algorithm, but more than the 56 bytes which the Blowfish
  cipher of the Java Cryptography Architecture accepts:

  ```
  757F3115 036CF571 6EEFADAE 119E0E74 277822F2 4A2BC80B B3D4DACB 91013F18 384443FD
  720303D5 EE431490 0BD07331 EAE7BAE4 2CF677D5 D54B707E D8B2D80B 8B5503FB 0D0B67DE
  ```

* Each block of 8 bytes is encrypted on its own (ECB). The plug-in processes the memory block as an
  array of 32-bit words in the order of the processor: the left half of a block is the
  **little-endian** word at the offset 0, the right half the one at the offset 4. An implementation
  of Blowfish which reads the halves big-endian must reverse the bytes of each word.
* Only whole blocks are encrypted: the loop runs `for (i = 0; i < size / 4 - 1; i += 2)`, so the last
  `size mod 8` bytes of the file are stored as they are.

Identical blocks encrypt to identical data, e.g. the 8 zero bytes of silence always to
`6A798255 1338E198`, which makes the format easy to recognize.

## Appended copies

A `juce::FileOutputStream` opens an existing file for appending, and the plug-in does not delete the
file first. Converting the samples of a preset again therefore appends another copy of the WAV file,
encrypted on its own. On the test machine 1,021 of 3,554 files contain 2 to 170 copies; in every one of
them all copies are identical.

The plug-in decrypts the whole file in blocks of 8 bytes from its start, and the WAV reader stops at
the end of the RIFF chunk, so the plug-in plays the first copy. A second copy only starts at the
border of a block if the length of the first copy is a multiple of 8, otherwise it cannot be decrypted
this way. In that case the plain bytes at the end of the first copy are decrypted together with the
start of the second one and the plug-in plays them as noise, e.g. the last stereo frame of
'Analog Brass' in Michael Oakley's pack.

The decoder decrypts only the first copy: it decrypts the first two blocks (`RIFF`, the size of the
RIFF chunk, `WAVE` and the header of the format chunk), reads the size of the RIFF chunk, then decrypts
the whole blocks up to its end and returns exactly the WAV file, including its unencrypted tail
bytes.

## Verification

* The tables computed from pi are identical to the tables in the plug-in; the implementation passes
  the published Blowfish test vectors, including the keys of 1 to 24 bytes.
* All 3,554 `.talwav` files on the test machine (the factory presets 'Classic 80s', the Hollowsun
  library and Michael Oakley's pack) decrypt to valid RIFF/WAVE files: PCM with 16, 24 and 32 bits,
  mono and stereo, 20,047 to 48,000 Hz.
* The play ranges and loops which the presets store for the 6,596 zones with an encrypted sample all
  lie within the decrypted audio; 6,376 of them end exactly at its end.
* Rendered offline with the plug-in (4.7.2, the Audio Unit), each of the 32 presets of Michael
  Oakley's pack plays the MIDI notes 48 and 72 sample for sample identically with its encrypted
  samples and with the WAV files which the decoder returns.
* Converted to SFZ, the other 832 presets on the test machine (the factory library, the Hollowsun
  library and 'SOURCE Sample Pack 1'): the 748 which convert now write 4,506 samples whose sound data
  is identical to the decrypted samples, the 54 which converted before are written byte-identically,
  and the 30 which use the built-in waveforms of the plug-in are still not converted.
