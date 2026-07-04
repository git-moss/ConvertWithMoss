// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * One patch from the floppy disk patch area. S-550 / W-30 / S-330: 256 bytes per block. S-50
 * family: 512 bytes per block.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxPatch
{
    private final int            index;
    private final String         patchId;
    private final String         name;
    private int                  bendRange;
    private int                  afterTouchSensitivity;
    private int                  keyMode;
    private int                  velocitySwThreshold;
    private final byte []        toneToKey1 = new byte [128];
    private final byte []        toneToKey2 = new byte [128];
    private int                  copySource;
    private int                  octaveShift;
    private int                  outputLevel;
    private final int            modulationDepth;
    private int                  detune;
    private int                  velocityMixRatio;
    private int                  afterTouchAssign;
    private int                  keyAssign;
    private final S5xxOutputJack outputJack;


    /**
     * Constructor.
     *
     * @param index The index of the patch
     * @param patchId The patch ID
     * @param input The input to read from
     * @param type The specific sampler type
     * @throws IOException Could not parse the parameters
     */
    public S5xxPatch (final int index, final String patchId, final InputStream input, final S5xxSamplerType type) throws IOException
    {
        this.index = index;
        this.patchId = patchId;

        this.name = StreamUtils.readAscii (input, 12).replace ((char) 0, ' ').replace ((char) 16, '.').trim ();

        this.bendRange = StreamUtils.readUnsigned8 (input);
        input.skipNBytes (1);
        this.afterTouchSensitivity = StreamUtils.readUnsigned8 (input);

        if (type.isS50 ())
            input.skipNBytes (4);

        this.keyMode = StreamUtils.readUnsigned8 (input);
        this.velocitySwThreshold = StreamUtils.readUnsigned8 (input);

        if (type.isS50 ())
            input.skipNBytes (19);

        // S-50: 128 (12 : 109 : 7), others: 109
        readToneToKey (this.toneToKey1, type, input);
        readToneToKey (this.toneToKey2, type, input);

        this.copySource = StreamUtils.readUnsigned8 (input);
        this.octaveShift = StreamUtils.readUnsigned8 (input);
        this.outputLevel = StreamUtils.readUnsigned8 (input);
        // Only set on S-50
        this.modulationDepth = StreamUtils.readUnsigned8 (input);
        this.detune = StreamUtils.readUnsigned8 (input);
        this.velocityMixRatio = StreamUtils.readUnsigned8 (input);
        this.afterTouchAssign = StreamUtils.readUnsigned8 (input);
        this.keyAssign = StreamUtils.readUnsigned8 (input);
        this.outputJack = S5xxOutputJack.fromByte (StreamUtils.readUnsigned8 (input));

        // Some more dummy bytes
    }


    /**
     * Get the patch index.
     *
     * @return The index of the patch, 0-based
     */
    public int getIndex ()
    {
        return this.index;
    }


    /**
     * Get the patch ID.
     *
     * @return "P1"…"P16" or "P11"…"P28" (S-330)
     */
    public String getPatchId ()
    {
        return this.patchId;
    }


    /**
     * Get the name of the patch.
     *
     * @return The name, bytes 0–11, trimmed
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the bend range.
     *
     * @return The bend range in the range of [0..12]
     */
    public int getBendRange ()
    {
        return this.bendRange;
    }


    /**
     * Set the bend range.
     *
     * @param bendRange The bend range in the range of [0..12]
     */
    public void setBendRange (final int bendRange)
    {
        if (bendRange < 0 || bendRange > 12)
            throw new IllegalArgumentException ("Invalid bend range");
        this.bendRange = bendRange;
    }


    /**
     * Get the after-touch sensitivity.
     *
     * @return The after-touch sensitivity, 0..127
     */
    public int getAfterTouchSensitivity ()
    {
        return this.afterTouchSensitivity;
    }


    /**
     * Set the after-touch sensitivity.
     *
     * @param afterTouchSensitivity The after-touch sensitivity, 0..127
     */
    public void setAfterTouchSensitivity (final int afterTouchSensitivity)
    {
        if (afterTouchSensitivity < 0)
            throw new IllegalArgumentException ("Invalid after touch sensitivity");
        this.afterTouchSensitivity = afterTouchSensitivity;
    }


    /**
     * Get the key mode.
     *
     * <ul>
     * <li>0: [Normal] This turns the S—50 to 16 voice polyphonic, assigning one module and one Tone
     * to a key.
     * <li>1: [Unison] This mode is 8 voice polyphonic, assigning two modules for the same Tone to a
     * key. It is possible to de-tune one of the sounds slightly.
     * <li>2: [V—SW] (Velocity Switch): This is also 16 voice polyphonic, assigning the 1st or 2nd
     * Tones to a key. Playing the key harder than a certain level (=Velocity Switch Threshold) will
     * sound the 2nd Tone, weaker will sound the 1st Tone. Each Tone will sound with a set level
     * curve (see page 61) depending on how hard you play the key.
     * <li>3: [X-Fade] (Velocity Cross Fade): This mode is 8 voice polyphonic, assigning the 1st and
     * 2nd Tones to a key. Depending on how hard you play the key, the volume balance of the 1st and
     * the 2nd Tones differs. The level curve of the 1st Tone is inverted.
     * <li>4: [V-MIX] (Velocity Mix): This mode is also 8 voice polyphonic, assigning the 1st and
     * 2nd Tones to a key. The 1st and the 2nd Tones are played simultaneously, each Tone being
     * played with the volume set by the level curve depending on how hard you play the key.
     *
     * @return The key mode in the range of [0..4]
     */
    public int getKeyMode ()
    {
        return this.keyMode;
    }


    /**
     * Set the key mode.
     *
     * @param keyMode The key mode in the range of [0..4]
     */
    public void setKeyMode (final int keyMode)
    {
        if (keyMode < 0 || keyMode > 4)
            throw new IllegalArgumentException ("Invalid key mode");
        this.keyMode = keyMode;
    }


    /**
     * When the V—SW Key Mode is selected, this determines the threshold level for the two Tones.
     * Higher values require harder playing to sound a different Tone.
     *
     * @return The velocity switch threshold in the range of 0..127
     */
    public int getVelocitySwitchThreshold ()
    {
        return this.velocitySwThreshold;
    }


    /**
     * Set the velocity switch threshold.
     *
     * @param velocitySwitchThreshold The velocity switch threshold in the range of 0..127
     */
    public void setVelocitySwitchThreshold (final int velocitySwitchThreshold)
    {
        if (velocitySwitchThreshold < 0)
            throw new IllegalArgumentException ("Invalid velocity switch threshold");
        this.velocitySwThreshold = velocitySwitchThreshold;
    }


    /**
     * Get the copy source.
     *
     * @return The copy source in the range of 0..31
     */
    public int getCopySource ()
    {
        return this.copySource;
    }


    /**
     * Set the copy source.
     *
     * @param copySource The copy source in the range of 0..31
     */
    public void setCopySource (final byte copySource)
    {
        if (copySource < 0 || copySource > 31)
            throw new IllegalArgumentException ("Invalid copy source");
        this.copySource = copySource;
    }


    /**
     * This can shift the pitch of the entire keyboard from -2 to 2 octaves in octave steps.
     *
     * @return -2, -1, 0, 1, 2
     */
    public int getOctaveShift ()
    {
        return this.octaveShift;
    }


    /**
     * Sets the octave shift.
     *
     * @param octaveShift The octave shift
     */
    public void setOctaveShift (final int octaveShift)
    {
        if (octaveShift < -2 || octaveShift > 2)
            throw new IllegalArgumentException ("Invalid octave shift");
        this.octaveShift = octaveShift;
    }


    /**
     * This can set the output level of each Patch separately. At 127, each Tone assigned to the
     * Patch is played at its set level.
     *
     * @return The output level in the range of 0..127.
     */
    public int getOutputLevel ()
    {
        return this.outputLevel;
    }


    /**
     * Set the output level.
     *
     * @param outputLevel The output level in the range of 0..127.
     */
    public void setOutputLevel (final byte outputLevel)
    {
        if (outputLevel < 0)
            throw new IllegalArgumentException ("Invalid output level");
        this.outputLevel = outputLevel;
    }


    /**
     * When the Unison Key Mode is selected, one of the sounds can be slightly de-tuned. 50 is
     * roughly half of a semi—tone.
     *
     * @return The de-tuning in cents in the range of -50..50
     */
    public int getDetune ()
    {
        return this.detune;
    }


    /**
     * Set the de-tuning of the 2nd layer.
     *
     * @param detune The de-tuning in cents in the range of -50..50
     */
    public void setDetune (final int detune)
    {
        if (detune < -50 || detune > 50)
            throw new IllegalArgumentException ("Invalid detune");
        this.detune = detune;
    }


    /**
     * When the V-MIX Key Mode is selected, the level curve of the 2nd Tone can be changed to
     * different curves. At 0, the volume obtained is exactly as in the set level curve.
     *
     * @return The curve in the range of 0..127
     */
    public int getVelocityMixRatio ()
    {
        return this.velocityMixRatio;
    }


    /**
     * Set the velocity mix ratio.
     *
     * @param velocityMixRatio The curve in the range of 0..127
     */
    public void setVelocityMixRatio (final int velocityMixRatio)
    {
        if (velocityMixRatio < 0)
            throw new IllegalArgumentException ("Invalid velocity mix ratio");
        this.velocityMixRatio = velocityMixRatio;
    }


    /**
     * Get the after-touch assignment.
     *
     * @return 0 = Vibrato, 1 = Volume, 2 = Bend Up, 3 = Bend Down
     */
    public int getAfterTouchAssign ()
    {
        return this.afterTouchAssign;
    }


    /**
     * Sets the after-touch assignment.
     *
     * @param afterTouchAssign 0 = Vibrato, 1 = Volume, 2 = Bend Up, 3 = Bend Down
     */
    public void setAfterTouchAssign (final int afterTouchAssign)
    {
        if (afterTouchAssign < 0 || afterTouchAssign > 3)
            throw new IllegalArgumentException ("Invalid after touch assign");
        this.afterTouchAssign = afterTouchAssign;
    }


    /**
     * Get the key assignment.
     *
     * @return The key assignment in the range of 0..31
     */
    public int getKeyAssign ()
    {
        return this.keyAssign;
    }


    /**
     * Set the key assignment.
     *
     * @param keyAssign The key assignment in the range of 0..31
     */
    public void setKeyAssign (final byte keyAssign)
    {
        if (keyAssign < 0 || keyAssign > 31)
            throw new IllegalArgumentException ("Invalid key assign");
        this.keyAssign = keyAssign;
    }


    /**
     * Get the tone for a key.
     *
     * @param layer The layer 0 or 1
     * @param key The key
     * @return The assigned tone, -1 if negative or larger than 31
     */
    public int getToneToKey (final int layer, final int key)
    {
        if (layer < 0 || layer > 1)
            throw new IllegalArgumentException ("Invalid table");
        if (key < 0 || key > 128)
            throw new IllegalArgumentException ("Invalid key");
        final byte value = layer == 0 ? this.toneToKey1[key] : this.toneToKey2[key];
        return value < 0 || value > 31 ? -1 : value;
    }


    /**
     * Assign a tone to a key
     *
     * @param layer The layer 0 or 1
     * @param key The key
     * @param tone The tone to assign
     */
    public void setToneToKey (final int layer, final int key, final int tone)
    {
        if (key < 0 || key > 108)
            throw new IllegalArgumentException ("Invalid key");
        if (tone < 0 || tone >= 32)
            throw new IllegalArgumentException ("Invalid tone");
        if (layer == 0)
            this.toneToKey1[key] = (byte) tone;
        else if (layer == 1)
            this.toneToKey2[key] = (byte) tone;
        else
            throw new IllegalArgumentException ("Invalid table");
    }


    /**
     * Get the modulation depth of the modulation wheel. Only set on S-50.
     *
     * @return The modulation depth in the range of 0..127
     */
    public int getModulationDepth ()
    {
        return this.modulationDepth;
    }


    /**
     * Get the output jack.
     *
     * @return The output jack
     */
    public S5xxOutputJack getOutputJack ()
    {
        return this.outputJack;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return String.format ("Patch{id=\"%s\", name=\"%s\", jack=\"%s\"}", this.patchId, this.name, this.outputJack.getLabel ());
    }


    private static void readToneToKey (final byte [] array, final S5xxSamplerType type, final InputStream input) throws IOException
    {
        final boolean isS50 = type.isS50 ();
        input.readNBytes (array, isS50 ? 0 : 12, isS50 ? 128 : 109);
        if (isS50)
            return;

        // Fill keys missing on the S-50 with the lowest and highest tone
        for (int i = 0; i < 12; i++)
            array[i] = array[12];
        for (int i = 121; i < 128; i++)
            array[i] = array[120];
    }
}