// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.disting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavCreator;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;


/**
 * Stores WAV files with multi-sample setting encoded in the sample filename. All samples are stored
 * in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class DistingExCreator extends WavCreator
{
    private final Map<Integer, Integer> velocityLayerIndices = new HashMap<> ();
    private String                      filenamePrefix;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DistingExCreator (final INotifier notifier)
    {
        super ("Expert Sleepers Disting EX", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.loadWavChunkSettings (config, "Disting");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.saveWavChunkSettings (config, "Disting");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.prepareKeyAndVelocityRanges (multisampleSource);

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        this.filenamePrefix = StringUtils.fixASCII (sampleName);
        final String safeSampleFolderName = sampleName.length () > 20 ? sampleName.substring (0, 20) : sampleName;

        final File multiFile = new File (destinationFolder, sampleName + ".dexpreset");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }
        storeMultisample (multisampleSource, multiFile, safeSampleFolderName);

        this.notifier.log ("IDS_NOTIFY_STORING", safeSampleFolderName);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a dexpreset file.
     *
     * @param multisampleSource The multi-sample to store in the file
     * @param multiFile The file of the dexpreset
     * @param safeSampleFolderName The name of the sample sub-folder
     * @throws IOException Could not store the file
     */
    private static void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final String safeSampleFolderName) throws IOException
    {
        try (final OutputStream out = new FileOutputStream (multiFile))
        {
            StreamUtils.writeASCII (out, "DEXBPRST", 8);

            // Version
            StreamUtils.writeSigned32 (out, 1, false);

            // All empty
            StreamUtils.writeEmpty (out, 500);

            // Preset version
            StreamUtils.writeSigned32 (out, 0x14, false);

            final String name = StringUtils.rightPadSpaces (StringUtils.optimizeName (StringUtils.fixASCII (multisampleSource.getName ()), 16), 16);
            StreamUtils.writeASCII (out, name, 16);

            // Unknown
            StreamUtils.writeSigned32 (out, 0, false);

            // Skip dual mode settings (all 0 in single mode)
            StreamUtils.writeEmpty (out, 32);

            // SD-Algorithm
            StreamUtils.writeSigned32 (out, 2, false);

            // Fill parameters
            final int [] parameters = createDefaultParameters ();
            final List<IGroup> groups = multisampleSource.getGroups ();
            if (!groups.isEmpty ())
            {
                final List<ISampleZone> zones = groups.get (0).getSampleZones ();
                if (!zones.isEmpty ())
                {
                    final ISampleZone zone = zones.get (0);
                    final int bendUp = zone.getBendUp ();
                    parameters[18] = bendUp > 0 ? bendUp : 2;

                    // Octave + Transpose + fine tune
                    double tune = zone.getTune ();
                    final int octaves = (int) (tune / 12);
                    parameters[11] = octaves;
                    tune -= octaves * 12;
                    parameters[12] = (int) tune;
                    tune -= parameters[12];
                    parameters[13] = (int) (tune * 100.0);

                    // Gain in the range of -40..24 dB
                    parameters[14] = MathUtils.clamp ((int) Math.round (zone.getGain ()), -40, 24);

                    final Optional<IEnvelopeModulator> modulator = multisampleSource.getGlobalAmplitudeModulator ();
                    if (modulator.isPresent ())
                    {
                        final IEnvelopeModulator envelopeModulator = modulator.get ();
                        if (envelopeModulator.hashCode () > 0)
                        {
                            final IEnvelope envelope = envelopeModulator.getSource ();
                            parameters[7] = MathUtils.clamp ((int) Math.round (Math.log (envelope.getAttackTime () / 0.001) / 0.0757), 0, 127);
                            parameters[8] = MathUtils.clamp ((int) Math.round (Math.log (envelope.getDecayTime () / 0.02) / 0.0521), 0, 127);
                            parameters[10] = MathUtils.clamp ((int) Math.round (Math.log (envelope.getReleaseTime () / 0.01) / 0.0630), 0, 127);
                        }
                    }
                }
            }

            for (final int parameter: parameters)
                StreamUtils.writeSigned16 (out, MathUtils.toSignedComplement (parameter), false);

            // Weird workaround for the issue that the string contains 0xFF after the null byte
            final ByteArrayOutputStream byteOut = new ByteArrayOutputStream ();
            StreamUtils.writeASCII (byteOut, safeSampleFolderName, 21);
            final byte [] byteArray = byteOut.toByteArray ();
            boolean makeFF = false;
            for (int i = 0; i < byteArray.length; i++)
            {
                if (makeFF)
                    byteArray[i] = (byte) 0xFF;
                else if (byteArray[i] == 0)
                    makeFF = true;
            }
            out.write (byteArray);

            // Unknown
            out.write (0);
            // Folder 2
            StreamUtils.writeEmpty (out, 21);
            // Folder 3
            StreamUtils.writeEmpty (out, 21);

            // Unknown
            StreamUtils.writeEmpty (out, 4);

            // Unknown
            out.write (0);
            out.write (0x80);
            out.write (0xBB);
            out.write (0x46);

            // Unknown
            StreamUtils.writeEmpty (out, 76);

            // Unknown
            out.write (0x01);
            out.write (0x07);
            out.write (0x00);
            out.write (0x7F);
            out.write (0x00);

            // Unknown
            StreamUtils.writeEmpty (out, 139);
        }
    }


    /**
     * Ensure that all zones are ordered by their upper key-range in ascending order. Create
     * velocity indices by grouping zones with the same upper velocity.
     *
     * @param multisampleSource The multi-sample source
     */
    private void prepareKeyAndVelocityRanges (final IMultisampleSource multisampleSource)
    {
        this.velocityLayerIndices.clear ();
        final Set<Integer> highVelocities = new TreeSet<> ();
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone sampleZone: group.getSampleZones ())
                highVelocities.add (Integer.valueOf (limitToDefault (sampleZone.getVelocityHigh (), 127)));
        int index = 0;
        for (final Integer velocity: highVelocities)
        {
            this.velocityLayerIndices.put (velocity, Integer.valueOf (index));
            index++;
        }
    }


    /** {@inheritDoc} */
    @Override
    protected String createSampleFilename (final ISampleZone zone, final int zoneIndex, final String fileEnding)
    {
        // Format: Samplename_Note_Switch_VelocityIndex_RoundRobinIndex.wav

        final StringBuilder sb = new StringBuilder (this.filenamePrefix);
        final int keyRoot = zone.getKeyRoot ();
        if (keyRoot >= 0)
            sb.append ('_').append (NoteParser.formatNoteSharps (keyRoot));

        final int keyHigh = limitToDefault (zone.getKeyHigh (), 127);
        sb.append ("_SW").append (keyHigh);

        if (this.velocityLayerIndices.size () > 1)
        {
            final int velocityHigh = limitToDefault (zone.getVelocityHigh (), 127);
            final Integer index = this.velocityLayerIndices.get (Integer.valueOf (velocityHigh));
            if (index != null)
                sb.append ("_V").append (index.intValue () + 1);
        }

        final PlayLogic playLogic = zone.getPlayLogic ();
        if (playLogic == PlayLogic.ROUND_ROBIN)
            sb.append ("_RR").append (zoneIndex + 1);

        return sb.append (fileEnding).toString ();
    }


    private static int [] createDefaultParameters ()
    {
        final int [] parameters = new int [80];

        parameters[0] = 100; // Attenuverter 1
        parameters[1] = 100; // Attenuverter 2
        parameters[2] = 100; // Attenuverter 3
        parameters[3] = 100; // Attenuverter 4
        parameters[4] = 100; // Attenuverter 5
        parameters[5] = 100; // Attenuverter 6
        parameters[6] = 0; // Folder
        parameters[7] = 0; // Attack time
        parameters[8] = 60; // Decay time
        parameters[9] = 127; // Sustain level
        parameters[10] = 77; // Release time
        parameters[11] = 0; // Octave
        parameters[12] = 0; // Transpose
        parameters[13] = 0; // Fine tune
        parameters[14] = 0; // Gain
        parameters[15] = 1; // Saturation
        parameters[16] = 0; // Sustain
        parameters[17] = 8; // Max voices
        parameters[18] = 2; // Bend range
        parameters[19] = 0; // Pitch bend input
        parameters[20] = 0; // Voice 1 detune
        parameters[21] = 0; // Voice 2 detune
        parameters[22] = 0; // Voice 3 detune
        parameters[23] = 0; // Voice 4 detune
        parameters[24] = 0; // Voice 5 detune
        parameters[25] = 0; // Voice 6 detune
        parameters[26] = 0; // Voice 7 detune
        parameters[27] = 0; // Voice 8 detune
        parameters[28] = 0; // Chord enable
        parameters[29] = 0; // Chord key
        parameters[30] = 0; // Chord scale
        parameters[31] = 0; // Chord shape
        parameters[32] = 0; // Chord inversion
        parameters[33] = 0; // Arpeggio 1 mode
        parameters[34] = 0; // Arpeggio 2 mode
        parameters[35] = 0; // Arpeggio 3 mode
        parameters[36] = 1; // Arpeggio 1 range
        parameters[37] = 1; // Arpeggio 2 range
        parameters[38] = 1; // Arpeggio 3 range
        parameters[39] = 0; // Scala/MTS
        parameters[40] = 0; // Scala KBM
        parameters[41] = -1; // Folder 2
        parameters[42] = -1; // Folder 3
        parameters[43] = 0; // Min note 1
        parameters[44] = 127; // Max note 1
        parameters[45] = 0; // Min note 2
        parameters[46] = 127; // Max note 2
        parameters[47] = 0; // Min note 3
        parameters[48] = 127; // Max note 3
        parameters[49] = 0; // Output spread
        parameters[50] = 0; // Delay mode
        parameters[51] = -3; // Delay level
        parameters[52] = 500; // Delay time
        parameters[53] = 50; // Delay feedback
        parameters[54] = 0; // Tone bass
        parameters[55] = 0; // Tone treble
        parameters[56] = 0; // Break time
        parameters[57] = 0; // Break direction
        parameters[58] = 0; // Voice 1 bend input
        parameters[59] = 0; // Voice 2 bend input
        parameters[60] = 0; // Voice 3 bend input
        parameters[61] = 0; // Voice 4 bend input
        parameters[62] = 0; // Voice 5 bend input
        parameters[63] = 0; // Voice 6 bend input
        parameters[64] = 0; // Voice 7 bend input
        parameters[65] = 0; // Voice 8 bend input
        parameters[66] = 0; // Output mode
        parameters[67] = 0; // Input mode
        parameters[68] = 0; // Sustain mode
        parameters[69] = 0; // MIDI velocity curve
        parameters[70] = 0; // Arp reset input
        parameters[71] = 0; // Gate offset
        parameters[72] = 0; // Round robin mode

        // The remaining entries are 'Unused'
        for (int i = 73; i < 80; i++)
            parameters[i] = 0;

        return parameters;
    }
}