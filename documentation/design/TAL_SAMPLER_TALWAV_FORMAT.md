# TAL-Sampler encrypted samples (*.talwav)

TAL-Sampler uses `.talwav` files for encrypted sample data. Its preset browser can create these files from the WAV files of a preset using the command "Convert wav Samples to talwav". 

A `.talwav` file contains WAV data encrypted using Blowfish in electronic codebook mode (ECB). The decoder is `format/tal/TALSamplerEncryptedWave`, and the cipher implementation is `file/Blowfish`.

## Format

The sample data is encrypted in independent 8-byte blocks using Blowfish.

The format has two details which differ from the conventional representation of Blowfish data:

* Each 8-byte block consists of two 32-bit **little-endian** words. An implementation which normally reads Blowfish block halves as big-endian therefore needs to account for the byte order of each word.
* Only complete 8-byte blocks are encrypted. The final `size mod 8` bytes of the WAV file remain unchanged.

Because ECB encrypts identical blocks identically, repeated sample data may also produce repeated encrypted blocks.

## Blowfish implementation

The format requires a Blowfish implementation which supports the key length used by `.talwav`. This exceeds the maximum key length accepted by the Blowfish implementation provided by the Java Cryptography Architecture, so ConvertWithMoss provides its own implementation in `file/Blowfish`.

The implementation uses the standard Blowfish algorithm: 16 rounds, the standard P-array and S-box constants derived from the hexadecimal digits of pi, and the standard Blowfish key schedule.

The implementation is tested against published Blowfish test vectors, including keys from 1 to 24 bytes.

## Appended WAV copies

Some `.talwav` files contain more than one encrypted copy of the same WAV data. On the test machine, 1,021 of 3,554 files contain between 2 and 170 copies; all copies within each such file are identical.

Only the first WAV is relevant. Its length can be determined from the RIFF chunk size.

The decoder therefore decrypts the beginning of the file sufficiently to obtain the RIFF/WAVE header and RIFF chunk size, determines the end of the first WAV, and then decrypts only the complete blocks belonging to that WAV. Any unencrypted tail bytes belonging to the WAV are included unchanged.

This also prevents data belonging to an appended copy from affecting the decoded first WAV when the first WAV's length is not a multiple of the Blowfish block size.

## Verification

* The Blowfish implementation passes the published Blowfish test vectors, including keys of 1 to 24 bytes.
* All 3,554 `.talwav` files on the test machine decrypt to valid RIFF/WAVE files: PCM with 16, 24 and 32 bits, mono and stereo, and sample rates from 20,047 to 48,000 Hz.
* The play ranges and loops stored by the presets for all 6,596 zones using encrypted samples lie within the decrypted audio; 6,376 of them end exactly at the end of the audio.
* Compatibility testing with TAL-Sampler 4.7.2 (Audio Unit) confirmed that each of the 32 presets in the tested Michael Oakley pack plays MIDI notes 48 and 72 sample-for-sample identically using the .talwav samples and the WAV files returned by the decoder.
* Converted to SFZ, the other 832 presets on the test machine produce 748 newly convertible presets containing 4,506 samples whose audio data is identical to the decrypted samples. The 54 presets which converted previously remain byte-identical. The remaining 30 presets use built-in waveforms and are still not converted.
* The tested Michael Oakley presets convert successfully for Waldorf Quantum/Iridium.
