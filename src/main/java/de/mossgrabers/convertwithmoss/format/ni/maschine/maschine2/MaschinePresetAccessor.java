// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can read and write one Maschine sound to a Preset Chunk.
 *
 * @author Jürgen Moßgraber
 */
public class MaschinePresetAccessor
{
    @SuppressWarnings("unused")
    private static final int                      PRE_X0D_START_OF_SOUND         = 91;
    @SuppressWarnings("unused")
    private static final int                      PRE_X0D_NAME                   = 92;
    private static final int                      PRE_X0D_PLUGIN_INFO            = 109;
    private static final int                      PRE_X0D_NUMBER_OF_SAMPLES      = 110;
    private static final int                      PRE_X0D_FIRST_ZONE             = 111;
    private static final int                      PRE_X0D_FIRST_ZONE_PLUGIN_NAME = 126;
    private static final int                      PRE_X0D_ZONE_SIZE              = 59;

    private static final int                      PRE_X0D_ZONE_SAMPLE_START      = 3;                               // 114
    private static final int                      PRE_X0D_ZONE_SAMPLE_END        = 5;                               // 116
    private static final int                      PRE_X0D_ZONE_LOOP_ENABLED      = 13;                              // 124
    private static final int                      PRE_X0D_ZONE_LOOP_START        = 8;                               // 119
    private static final int                      PRE_X0D_ZONE_LOOP_END          = 10;                              // 121
    private static final int                      PRE_X0D_ZONE_LOOP_CROSSFADE    = 16;                              // 127
    private static final int                      PRE_X0D_ZONE_ROOT_KEY          = 25;                              // 136
    private static final int                      PRE_X0D_ZONE_LOW_KEY           = 28;                              // 139
    private static final int                      PRE_X0D_ZONE_HIGH_KEY          = 30;                              // 141
    private static final int                      PRE_X0D_ZONE_VELOCITY_LOW      = 33;                              // 144
    private static final int                      PRE_X0D_ZONE_VELOCITY_HIGH     = 35;                              // 146
    private static final int                      PRE_X0D_ZONE_GAIN              = 38;                              // 149
    private static final int                      PRE_X0D_ZONE_PANNING           = 41;                              // 152
    private static final int                      PRE_X0D_ZONE_TUNE              = 44;                              // 155

    @SuppressWarnings("unused")
    private static final int                      X0D_START_OF_SOUND             = 642;
    private static final int                      X0D_NAME                       = 643;
    private static final int                      X0D_PLUGIN_INFO                = 665;
    private static final int                      X0D_NUMBER_OF_SAMPLES          = 666;
    private static final int                      X0D_FIRST_ZONE                 = 667;
    private static final int                      X0D_FIRST_ZONE_PLUGIN_NAME     = 682;
    private static final int                      X0D_ZONE_SIZE                  = 80;

    private static final int                      X0D_ZONE_SAMPLE_START          = 4;                               // 671
    private static final int                      X0D_ZONE_SAMPLE_END            = 7;                               // 674
    private static final int                      X0D_ZONE_LOOP_ENABLED          = 18;                              // 685
    private static final int                      X0D_ZONE_LOOP_START            = 11;                              // 678
    private static final int                      X0D_ZONE_LOOP_END              = 14;                              // 681
    private static final int                      X0D_ZONE_LOOP_CROSSFADE        = 22;                              // 689
    private static final int                      X0D_ZONE_ROOT_KEY              = 34;                              // 701
    private static final int                      X0D_ZONE_LOW_KEY               = 38;                              // 705
    private static final int                      X0D_ZONE_HIGH_KEY              = 41;                              // 708
    private static final int                      X0D_ZONE_VELOCITY_LOW          = 45;                              // 712
    private static final int                      X0D_ZONE_VELOCITY_HIGH         = 48;                              // 715
    private static final int                      X0D_ZONE_GAIN                  = 52;                              // 719
    private static final int                      X0D_ZONE_PANNING               = 56;                              // 723
    private static final int                      X0D_ZONE_TUNE                  = 60;                              // 727
    private static final int                      X0D_ZONE_SAMPLE_LENGTH         = 75;                              // 742
    private static final int                      X0D_ZONE_LAST_ROW              = 79;                              // 746

    private static final int                      X0D_GLOBAL_PITCHBEND           = 13;
    private static final int                      X0D_GLOBAL_TUNING              = 31;
    private static final int                      X0D_GLOBAL_AMP_ENVELOPE_TYPE   = 23;

    private static final char []                  PLUGIN_HOST_ROW_PARAM_TYPES    =
    {
        'i',
        'i',
        'i',
        'i',
        'i',
        's',
        's'
    };
    private static final float                    MIN_DB                         = -82.3f;
    private static final float                    GAIN                           = 80.05f;
    /** Results to 0dB. */
    private static final float                    REFERENCE_DB                   = 0.75f;
    private static final String                   PLUGIN_MASCHINE_SAMPLER        = "Sampler";
    private static final String                   PLUGIN_HOST                    = "PluginHost";

    private static final Map<Integer, FilterType> FILTER_TYPE_LOOKUP             = new HashMap<> ();
    private static final Map<FilterType, Integer> INV_FILTER_TYPE_LOOKUP         = new EnumMap<> (FilterType.class);
    static
    {
        FILTER_TYPE_LOOKUP.put (Integer.valueOf (1), FilterType.LOW_PASS);
        FILTER_TYPE_LOOKUP.put (Integer.valueOf (2), FilterType.BAND_PASS);
        FILTER_TYPE_LOOKUP.put (Integer.valueOf (3), FilterType.HIGH_PASS);
        INV_FILTER_TYPE_LOOKUP.put (FilterType.LOW_PASS, Integer.valueOf (1));
        INV_FILTER_TYPE_LOOKUP.put (FilterType.BAND_PASS, Integer.valueOf (2));
        INV_FILTER_TYPE_LOOKUP.put (FilterType.HIGH_PASS, Integer.valueOf (3));
    }

    /** The pitch modulation of an LFO amount of 100%, which is 1 octave up and down. */
    public static final double          LFO_PITCH_RANGE_CENTS  = 1200.0;
    /** The cutoff modulation of an LFO amount of 100%, assumed to be the full cutoff range. */
    public static final double          LFO_CUTOFF_RANGE_CENTS = 1200.0 * 5.9497 / Math.log (2);

    /** The LFO waveforms in the order of their values. */
    private static final LfoWaveform [] LFO_WAVEFORMS          =
    {
        LfoWaveform.SINE,
        LfoWaveform.TRIANGLE,
        LfoWaveform.SQUARE,
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.RANDOM
    };

    /**
     * Normalized LFO speed values and the rates in Hertz they display in Maschine. The inner points
     * were read in Maschine, the end points are extrapolated. Rates in between are interpolated
     * logarithmically.
     */
    private static final double []      LFO_SPEED_VALUES       =
    {
        0.0,
        0.11,
        0.3,
        0.5,
        0.76,
        0.85,
        1.0
    };
    private static final double []      LFO_SPEED_RATES        =
    {
        0.031,
        0.07,
        0.29,
        1.14,
        3.03,
        4.85,
        10.6
    };

    private final INotifier             notifier;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public MaschinePresetAccessor (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /**
     * Reads a Maschine 2+ preset chunk structure and the contained Maschine sounds.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file to convert
     * @param data The preset data
     * @return The read multi-sample source
     * @throws IOException Could not parse the data
     */
    public Optional<IMultisampleSource> readMaschinePreset (final File sourceFolder, final File sourceFile, final byte [] data) throws IOException
    {
        final MaschinePresetParameterArray parameterArray = new MaschinePresetParameterArray (data);
        final boolean isOldFormat = parameterArray.isOldFormat ();
        final Offsets offsets = new Offsets (isOldFormat);
        final List<byte []> parameterArrayRaw = parameterArray.getRawData ();

        if (!this.findMaschineSampler (parameterArray, isOldFormat, offsets))
            return Optional.empty ();

        final int [] values = parameterArray.readIntegers (offsets.offsetNumberOfSamples, isOldFormat ? 7 : 6);
        final int numberOfSampleZones = values[isOldFormat ? 6 : 5];

        // Create the multi-sample with 1 group
        final String name = FileUtils.getNameWithoutType (sourceFile);
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFolder, name);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, name);
        final IGroup group = new DefaultGroup ();
        multisampleSource.setGroups (Collections.singletonList (group));

        int offsetZone = isOldFormat ? PRE_X0D_ZONE_SIZE : X0D_ZONE_SIZE;
        int zoneOffset = 0;
        final List<String> filePaths = new ArrayList<> ();
        int sampleIndex = 0;
        while (sampleIndex < numberOfSampleZones)
        {
            // Sample info parameter: 4 bytes - 5 bytes for libraries; path with a max. of 256
            // characters; 3 more bytes (01 01 00)
            final ByteArrayInputStream sampleInfoIn = new ByteArrayInputStream (parameterArrayRaw.get (offsets.offsetFirstZone + zoneOffset));

            if (!this.checkSample (sampleInfoIn, sampleIndex))
            {
                if (filePaths.isEmpty ())
                    return Optional.empty ();
                break;
            }

            final String samplePath = MaschinePresetParameterArray.readString (sampleInfoIn);

            // Quick and dirty workaround for some 2.0.0.0 zones being 1 row longer...
            if ((samplePath.isEmpty () || samplePath.charAt (0) == 0) && sampleIndex == 1 && offsetZone == PRE_X0D_ZONE_SIZE)
            {
                offsetZone = 60;
                zoneOffset = 60;
                // Don't increment sampleIndex!
                continue;
            }

            filePaths.add (samplePath);

            final ISampleZone zone = new DefaultSampleZone ();
            group.addSampleZone (zone);
            readZoneParameters (zone, offsets.offsetFirstZone + zoneOffset, parameterArray, isOldFormat);

            zoneOffset += offsetZone;
            sampleIndex++;
        }

        assignSampleFile (sourceFile, group, filePaths, this.notifier);

        int readPosition = offsets.offsetFirstZone + zoneOffset;
        readPosition = readLibraryReferences (parameterArray, readPosition);
        readPosition = readGlobalParameters (multisampleSource, parameterArray, readPosition, isOldFormat);
        int soundinfoIndex = findSoundinfo (parameterArrayRaw, readPosition);
        if (soundinfoIndex == -1)
        {
            this.notifier.logError ("IDS_NI_MASCHINE_NO_SOUNDINFO");
            return Optional.empty ();
        }
        readSoundinfo (multisampleSource, parameterArrayRaw.get (soundinfoIndex), parts);
        // There is a 2nd one at the end
        soundinfoIndex = findSoundinfo (parameterArrayRaw, soundinfoIndex + 1);
        if (soundinfoIndex == -1)
        {
            this.notifier.logError ("IDS_NI_MASCHINE_NO_SOUNDINFO");
            return Optional.empty ();
        }

        offsets.update (soundinfoIndex + 1);

        return Optional.of (multisampleSource);
    }


    private boolean checkSample (final InputStream sampleInfoIn, final int sampleIndex) throws IOException
    {
        if (sampleIndex == 0)
        {
            final byte [] introBytes = sampleInfoIn.readNBytes (2);
            if (introBytes.length == 0)
            {
                this.notifier.logError ("IDS_NI_MASCHINE_SAMPLER_HAS_NO_SAMPLES");
                return false;
            }
            if (introBytes[0] != 0 || introBytes[1] != 0)
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Unknown Sampleinfo structure"));
        }

        if (sampleInfoIn.available () == 0)
        {
            this.notifier.logError ("IDS_NI_MASCHINE_NO_MORE_SAMPLES");
            return false;
        }

        final boolean isLibrary = sampleInfoIn.read () > 0;
        if (isLibrary)
            sampleInfoIn.skipNBytes (1);

        return true;
    }


    /**
     * Update the given template data with the data of the multi-sample source. Always expects and
     * writes x0D and later format.
     *
     * @param source The multi-sample source to insert
     * @param templateData The template data array
     * @param safeSampleFolderName The folder which contains the samples
     * @return The update data array
     * @throws IOException Could not update data
     */
    public byte [] writeMaschinePreset (final IMultisampleSource source, final byte [] templateData, final String safeSampleFolderName) throws IOException
    {
        final MaschinePresetParameterArray parameterArray = new MaschinePresetParameterArray (templateData);
        final List<byte []> data = parameterArray.getRawData ();

        parameterArray.writeString (X0D_NAME, source.getName ());

        // Extract the template (first zone)
        final List<byte []> templateZone = new ArrayList<> (data.subList (X0D_FIRST_ZONE, X0D_FIRST_ZONE + X0D_ZONE_SIZE));

        // Remove both existing zones which are contained in the template
        data.subList (X0D_FIRST_ZONE, X0D_FIRST_ZONE + X0D_ZONE_SIZE * 2).clear ();

        // There are no groups, therefore, collect all sample zones
        final List<ISampleZone> sampleZones = source.getAllSampleZones (true);

        // Update the number of samples
        final int maxZones = sampleZones.size ();
        parameterArray.writeIntegers (X0D_NUMBER_OF_SAMPLES, 0, 0, 0, 0, 0, 0, maxZones, 1, 0, 1, 41, 1, 2);

        // Generate new zones using the template
        final List<byte []> newZones = new ArrayList<> ();
        for (int i = 0; i < maxZones; i++)
            newZones.addAll (fillZone (i, maxZones, templateZone, sampleZones.get (i), safeSampleFolderName));

        // Insert all regenerated zones back in the correct position
        data.addAll (X0D_FIRST_ZONE, newZones);

        fillGlobalParameters (X0D_FIRST_ZONE + maxZones * X0D_ZONE_SIZE + 4, data, source, sampleZones.get (0));

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.write (new byte []
        {
            (byte) 0xFF,
            0x01,
            0x00,
            0x00,
            0x01,
            0x3F
        });
        MaschinePresetParameterArray.writeInteger (out, 887 + maxZones * X0D_ZONE_SIZE);
        out.write (new byte []
        {
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x01,
            0x01,
            0x01,
            0x00,
            0x01,
            0x01,
            0x01,
            0x48,
            0x01,
            0x01,
            0x00
        });
        data.set (897 + maxZones * X0D_ZONE_SIZE, out.toByteArray ());

        final ByteArrayOutputStream out2 = new ByteArrayOutputStream ();
        out2.write (new byte []
        {
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x48
        });
        MaschinePresetParameterArray.writeInteger (out2, 898 + maxZones * X0D_ZONE_SIZE);
        out2.write (new byte []
        {
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            (byte) 0xFF,
            0x01
        });
        data.set (907 + maxZones * X0D_ZONE_SIZE, out2.toByteArray ());

        return parameterArray.serialize ();
    }


    private static void fillGlobalParameters (final int globalOffset, final List<byte []> data, final IMultisampleSource source, final ISampleZone firstSampleZone) throws IOException
    {
        final float normalizedPitchbend = (float) Math.clamp (Math.sqrt (firstSampleZone.getBendUp () / 1200f), 0, 1);
        writeFloatValueRow (globalOffset + X0D_GLOBAL_PITCHBEND, data, normalizedPitchbend);

        final int isReversed = firstSampleZone.isReversed () ? 1 : 0;
        MaschinePresetParameterArray.writeIntegers (globalOffset + 27, data, 0, isReversed, isReversed, 0, 0, 0);

        // X0D_GLOBAL_TUNING not used, tuning is already set in zones

        final Optional<IEnvelopeModulator> globalAmplitudeModulator = source.getGlobalAmplitudeModulator ();
        if (globalAmplitudeModulator.isPresent ())
        {
            final IEnvelopeModulator envelopeModulator = globalAmplitudeModulator.get ();
            final IEnvelope ampEnvelope = envelopeModulator.getSource ();

            // Always set to ADSR envelope type
            MaschinePresetParameterArray.writeIntegers (globalOffset + X0D_GLOBAL_AMP_ENVELOPE_TYPE, data, 0, 2, 2, 0, 0, 0);
            // Velocity to volume is the velocity modulation amount, not the envelope depth
            writeFloatValueRow (globalOffset + 92, data, (float) firstSampleZone.getAmplitudeVelocityModulator ().getDepth ());

            final double attackTime = Math.max (0, ampEnvelope.getAttackTime ());
            final double decayTime = Math.max (0, ampEnvelope.getHoldTime ()) + Math.max (0, ampEnvelope.getDecayTime ());
            final double sustainLevel = ampEnvelope.getSustainLevel ();
            final double releaseTime = Math.max (0, ampEnvelope.getReleaseTime ());
            writeFloatValueRow (globalOffset + 112, data, attackMillisToInput ((float) (attackTime * 1000.0)));
            writeFloatValueRow (globalOffset + 120, data, decayAndReleaseMillisToInput ((float) (decayTime * 1000.0)));
            writeFloatValueRow (globalOffset + 124, data, (float) (sustainLevel < 0 ? 1 : sustainLevel));
            writeFloatValueRow (globalOffset + 128, data, decayAndReleaseMillisToInput ((float) (releaseTime * 1000.0)));
        }

        // There is only 1 modulation envelope, prefer the filter envelope if present
        IEnvelope modEnvelope = null;
        ILfoModulator cutoffLfoModulator = null;
        final Optional<IFilter> globalFilter = source.getGlobalFilter ();
        if (globalFilter.isPresent ())
        {
            final IFilter filter = globalFilter.get ();
            final Integer filterType = INV_FILTER_TYPE_LOOKUP.get (filter.getType ());
            if (filterType != null)
            {
                MaschinePresetParameterArray.writeIntegers (globalOffset + 56, data, 0, filterType.intValue (), filterType.intValue (), 0, 0, 0);
                writeFloatValueRow (globalOffset + 60, data, frequencyToNorm (filter.getCutoff ()));
                writeFloatValueRow (globalOffset + 64, data, (float) filter.getResonance ());

                final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                final double cutoffModulationIntensity = cutoffEnvelopeModulator.getDepth ();
                if (cutoffModulationIntensity > 0)
                {
                    modEnvelope = cutoffEnvelopeModulator.getSource ();
                    writeFloatValueRow (globalOffset + 172, data, (float) cutoffModulationIntensity);
                }
                writeFloatValueRow (globalOffset + 88, data, (float) filter.getCutoffVelocityModulator ().getDepth ());
                cutoffLfoModulator = filter.getCutoffLfoModulator ();
            }
        }

        // If the modulation envelope was not used for the filter use it for pitch
        if (modEnvelope == null)
        {
            final IEnvelopeModulator pitchModulator = firstSampleZone.getPitchEnvelopeModulator ();
            final double pitchModulationIntensity = pitchModulator.getDepth ();
            if (pitchModulationIntensity > 0)
            {
                modEnvelope = pitchModulator.getSource ();
                writeFloatValueRow (globalOffset + 168, data, (float) pitchModulationIntensity);
            }
        }

        if (modEnvelope != null)
        {
            final double attackTime = Math.max (0, modEnvelope.getAttackTime ());
            final double decayTime = Math.max (0, modEnvelope.getHoldTime ()) + Math.max (0, modEnvelope.getDecayTime ());
            final double sustainLevel = modEnvelope.getSustainLevel ();
            final double releaseTime = Math.max (0, modEnvelope.getReleaseTime ());
            writeFloatValueRow (globalOffset + 148, data, attackMillisToInput ((float) (attackTime * 1000.0)));
            writeFloatValueRow (globalOffset + 156, data, decayAndReleaseMillisToInput ((float) (decayTime * 1000.0)));
            writeFloatValueRow (globalOffset + 160, data, (float) (sustainLevel < 0 ? 1 : sustainLevel));
            writeFloatValueRow (globalOffset + 164, data, decayAndReleaseMillisToInput ((float) (releaseTime * 1000.0)));
        }

        writeLfoParameters (globalOffset, data, firstSampleZone.getPitchLfoModulator (), cutoffLfoModulator);
    }


    /**
     * Fill in the LFO parameters. There is only 1 LFO which can modulate pitch and filter cutoff,
     * the pitch LFO is preferred. The modulation wheel scales the depth of both modulations.
     *
     * @param globalOffset The offset to the global parameters
     * @param data The parameter rows
     * @param pitchLfoModulator The pitch LFO modulator
     * @param cutoffLfoModulator The filter cutoff LFO modulator, might be null
     * @throws IOException Could not write the parameters
     */
    private static void writeLfoParameters (final int globalOffset, final List<byte []> data, final ILfoModulator pitchLfoModulator, final ILfoModulator cutoffLfoModulator) throws IOException
    {
        ILfo lfo = null;
        double modWheelAmount = -1;
        if (pitchLfoModulator.isActive ())
        {
            lfo = pitchLfoModulator.getSource ();
            modWheelAmount = getLfoModWheelAmount (pitchLfoModulator);
            writeFloatValueRow (globalOffset + 202, data, (float) getLfoAmount (pitchLfoModulator, LFO_PITCH_RANGE_CENTS));
        }
        if (cutoffLfoModulator != null && cutoffLfoModulator.isActive () && (lfo == null || lfo.equals (cutoffLfoModulator.getSource ())))
        {
            lfo = cutoffLfoModulator.getSource ();
            if (modWheelAmount < 0)
                modWheelAmount = getLfoModWheelAmount (cutoffLfoModulator);
            writeFloatValueRow (globalOffset + 206, data, (float) getLfoAmount (cutoffLfoModulator, LFO_CUTOFF_RANGE_CENTS));
        }
        if (lfo == null)
            return;

        writeFloatValueRow (globalOffset + 104, data, (float) modWheelAmount);

        final int lfoType = getLfoWaveformIndex (lfo.getWaveform ());
        MaschinePresetParameterArray.writeIntegers (globalOffset + 185, data, 0, lfoType, lfoType, 0, 0, 0);
        if (lfo.getRate () > 0)
            writeFloatValueRow (globalOffset + 189, data, lfoRateToInput (lfo.getRate ()));
        // The phase is in the range of [-0.5..0.5]
        final double startPhase = Math.max (0, lfo.getStartPhase ());
        writeFloatValueRow (globalOffset + 193, data, (float) (startPhase > 0.5 ? startPhase - 1 : startPhase));
        // Sync: Free
        MaschinePresetParameterArray.writeIntegers (globalOffset + 198, data, 0, 0, 0, 0, 0, 0);
    }


    private static List<byte []> fillZone (final int zoneIndex, final int maxZones, final List<byte []> templateZone, final ISampleZone sampleZone, final String safeSampleFolderName) throws IOException
    {
        // Clone the zone
        final List<byte []> newZone = new ArrayList<> (X0D_ZONE_SIZE);
        for (int i = 0; i < templateZone.size (); i++)
            newZone.add (templateZone.get (i).clone ());

        // Update the data
        newZone.set (0, createSamplePathRow (zoneIndex, sampleZone, safeSampleFolderName));
        if (zoneIndex > 0)
            newZone.set (1, new byte [] {});

        final int start = sampleZone.getStart ();
        // End seems to be off by 1, looks like a bug in Maschine to me...
        final int stop = Math.max (0, sampleZone.getStop () - 1);
        MaschinePresetParameterArray.writeIntegers (X0D_ZONE_SAMPLE_START, newZone, 0, start, start, 0, 0, 0);
        MaschinePresetParameterArray.writeIntegers (X0D_ZONE_SAMPLE_END, newZone, 0, stop, stop, stop + 1, 0, 0);

        final Optional<ISampleData> sampleData = sampleZone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException ("Empty sample data in zone: " + sampleZone.getName ());
        final int length = sampleData.get ().getAudioMetadata ().getNumberOfSamples ();
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        MaschinePresetParameterArray.writeIntegers (out, 0, length - 1, length - 1);
        out.write (new byte []
        {
            (byte) 0xFF,
            0x01,
            0x00,
            0x00
        });
        newZone.set (X0D_ZONE_SAMPLE_LENGTH, out.toByteArray ());

        final List<ISampleLoop> loops = sampleZone.getLoops ();
        final boolean hasLoop = !loops.isEmpty ();
        MaschinePresetParameterArray.writeIntegers (X0D_ZONE_LOOP_ENABLED, newZone, 0, hasLoop ? 1 : 0, hasLoop ? 1 : 0, 0, 0, 0);
        if (hasLoop)
        {
            final ISampleLoop loop = loops.get (0);
            final int loopStart = loop.getStart ();
            final int loopEnd = loop.getEnd ();
            final int crossfade = loop.getCrossfadeInSamples ();
            // Not sure about the 3rd numbers...
            MaschinePresetParameterArray.writeIntegers (X0D_ZONE_LOOP_START, newZone, 0, loopStart, loopStart, loopStart, 0, 0);
            MaschinePresetParameterArray.writeIntegers (X0D_ZONE_LOOP_END, newZone, 0, loopEnd, loopEnd, 0, 0, 0);
            MaschinePresetParameterArray.writeIntegers (X0D_ZONE_LOOP_CROSSFADE, newZone, 0, crossfade, crossfade, Math.max (0, crossfade - 1), 0, 0);
        }
        else
        {
            MaschinePresetParameterArray.writeIntegers (X0D_ZONE_LOOP_START, newZone, 0, start, start, 0, 0, 0);
            MaschinePresetParameterArray.writeIntegers (X0D_ZONE_LOOP_END, newZone, 0, stop, stop, 0, 0, 0);
            MaschinePresetParameterArray.writeIntegers (X0D_ZONE_LOOP_CROSSFADE, newZone, 0, 0, 0, 0, 0, 0);
        }

        final int keyRoot = sampleZone.getKeyRoot ();
        final int keyLow = sampleZone.getKeyLow ();
        final int keyHigh = sampleZone.getKeyHigh ();
        final int velocityLow = sampleZone.getVelocityLow ();
        final int velocityHigh = sampleZone.getVelocityHigh ();
        // Not sure about the 3rd numbers...
        MaschinePresetParameterArray.writeIntegers (X0D_ZONE_ROOT_KEY, newZone, 0, keyRoot, keyRoot, keyRoot - 1, 0, 0);
        MaschinePresetParameterArray.writeIntegers (X0D_ZONE_LOW_KEY, newZone, 0, keyLow, keyLow, keyLow - 1, 0, 0);
        MaschinePresetParameterArray.writeIntegers (X0D_ZONE_HIGH_KEY, newZone, 0, keyHigh, keyHigh, keyHigh + 1, 0, 0);
        MaschinePresetParameterArray.writeIntegers (X0D_ZONE_VELOCITY_LOW, newZone, 0, velocityLow, velocityLow, 0, 0, 0);
        MaschinePresetParameterArray.writeIntegers (X0D_ZONE_VELOCITY_HIGH, newZone, 0, velocityHigh, velocityHigh, 0, 0, 0);

        writeFloatValueRow (X0D_ZONE_GAIN, newZone, dbToInput (sampleZone.getGain ()));
        writeFloatValueRow (X0D_ZONE_PANNING, newZone, (float) sampleZone.getPanning ());
        writeFloatValueRow (X0D_ZONE_TUNE, newZone, (float) sampleZone.getTuning ());

        newZone.set (X0D_ZONE_LAST_ROW, createLastRow (zoneIndex, maxZones, sampleZone));

        return newZone;
    }


    private static byte [] createLastRow (final int zoneIndex, final int maxZones, final ISampleZone sampleZone) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        MaschinePresetParameterArray.writeInteger (out, 0);
        final List<ISampleLoop> loops = sampleZone.getLoops ();
        final int loopStart = loops.isEmpty () ? -1 : loops.get (0).getStart ();
        final int value = loopStart == -1 ? Math.max (0, sampleZone.getStop () - 1) : loopStart;
        final boolean isLastZone = zoneIndex + 1 == maxZones;
        if (zoneIndex == 0 && !isLastZone)
            MaschinePresetParameterArray.writeIntegers (out, value, value, 0, 0, 0);
        else
            MaschinePresetParameterArray.writeIntegers (out, 0, 0, value);

        if (zoneIndex == 0 && maxZones < 2)
            out.write (new byte []
            {
                0x00,
                0x00
            });

        out.write (new byte []
        {
            0x00,
            0x00,
            0x01,
            0x08,
            0x0A,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x29
        });

        if (isLastZone)
        {
            final int pos = X0D_FIRST_ZONE + zoneIndex * X0D_ZONE_SIZE;
            MaschinePresetParameterArray.writeInteger (out, pos);
            out.write (new byte []
            {
                0x00,
                0x00,
                0x01,
                0x01,
                0x00,
                0x01,
                0x29
            });
            MaschinePresetParameterArray.writeInteger (out, pos);
        }
        return out.toByteArray ();
    }


    private static byte [] createSamplePathRow (final int zoneIndex, final ISampleZone sampleZone, final String safeSampleFolderName) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        if (zoneIndex == 0)
        {
            MaschinePresetParameterArray.writeInteger (out, 0);
            MaschinePresetParameterArray.writeInteger (out, 0);
        }
        MaschinePresetParameterArray.writeInteger (out, 0);
        final String samplePath = safeSampleFolderName + "/" + sampleZone.getName () + ".wav";
        MaschinePresetParameterArray.writeString (out, samplePath);
        if (zoneIndex == 0)
        {
            MaschinePresetParameterArray.writeInteger (out, 1);
            MaschinePresetParameterArray.writeInteger (out, 0);
        }
        return out.toByteArray ();
    }


    private boolean findMaschineSampler (final MaschinePresetParameterArray parameterArray, final boolean isOldFormat, final Offsets offsets) throws IOException
    {
        boolean firstRun = true;
        boolean foundSampler = false;
        do
        {
            // Search for "NI::MASCHINE::DATA::Sampler" (force to search from the beginning
            // at the first run to not miss shorter header like in version 0x0D)
            final Pair<Integer, String> nextMaschineDevice = parameterArray.findNextMaschineDevice (firstRun ? 0 : offsets.offsetPluginInfo);
            firstRun = false;
            final int newOffsetPluginInfo = nextMaschineDevice.getKey ().intValue ();
            if (newOffsetPluginInfo < 0)
            {
                this.notifier.logError ("IDS_NI_MASCHINE_NO_DEVICE");
                return false;
            }
            if (newOffsetPluginInfo != offsets.offsetPluginInfo)
                // Adjust all offsets
                offsets.update (newOffsetPluginInfo - (isOldFormat ? PRE_X0D_PLUGIN_INFO : X0D_PLUGIN_INFO));

            final String deviceName = nextMaschineDevice.getValue ();
            foundSampler = PLUGIN_MASCHINE_SAMPLER.equals (deviceName);
            if (!foundSampler)
            {
                String infoName = deviceName;
                if (PLUGIN_HOST.equals (deviceName))
                {
                    final List<Object> parameterValues = parameterArray.readParameters (offsets.offsetPluginName, PLUGIN_HOST_ROW_PARAM_TYPES);
                    if (parameterValues.size () == PLUGIN_HOST_ROW_PARAM_TYPES.length)
                        infoName = parameterValues.get (PLUGIN_HOST_ROW_PARAM_TYPES.length - 1).toString ();
                }
                this.notifier.logError ("IDS_NI_MASCHINE_NO_SAMPLER", infoName);
                offsets.offsetPluginInfo++;
            }
        } while (!foundSampler);
        return true;
    }


    private static int readLibraryReferences (final MaschinePresetParameterArray parameterArray, final int offset)
    {
        if (parameterArray.isOldFormat ())
            return offset;

        final int newOffset = offset + 4;
        try
        {
            final int [] references = parameterArray.readIntegers (newOffset, 10);
            return newOffset + 32 * references[9];
        }
        catch (final IOException _)
        {
            return newOffset;
        }
    }


    /**
     * File paths are either absolute or relative to a library root (which is not known). Therefore,
     * the sample files need to be located.
     *
     * @param filePaths The read paths
     * @param sourceFile The location of the Maschine file
     * @param group The group which contains the sample zones of the files
     * @param notifier For notifications
     * @throws IOException Could not access the files
     */
    public static void assignSampleFile (final File sourceFile, final IGroup group, final List<String> filePaths, final INotifier notifier) throws IOException
    {
        final List<File> files = lookupFiles (filePaths, sourceFile.getParent (), notifier);
        final List<ISampleZone> sampleZones = group.getSampleZones ();
        for (int fileIndex = 0; fileIndex < files.size (); fileIndex++)
        {
            final File file = files.get (fileIndex);
            final ISampleZone zone = sampleZones.get (fileIndex);
            zone.setName (FileUtils.getNameWithoutType (file));
            zone.setSampleData (AbstractDetector.createSampleData (file, notifier));
        }
    }


    private static int findSoundinfo (final List<byte []> parameterArray, final int startIndex)
    {
        for (int i = startIndex; i < parameterArray.size (); i++)
        {
            final byte [] data = parameterArray.get (i);
            // Crude hack which assumes that the SoundInfo content is longer than all other ones
            if (data != null && data.length > 100 && data[0] != 0)
                return i;
        }
        return -1;
    }


    private static int readGlobalParameters (final IMultisampleSource multisampleSource, final MaschinePresetParameterArray parameterArray, final int globalOffset, final boolean isOldFormat) throws IOException
    {
        int offset = globalOffset;
        int first = offset + (isOldFormat ? 9 : X0D_GLOBAL_PITCHBEND);

        // Workaround for sometimes the global data is 4 rows later (found with 2.2.3.2452)
        if (parameterArray.getRawData ().get (first).length == 0)
        {
            offset += 4;
            first += 4;
            if (parameterArray.getRawData ().get (first).length == 0)
                return globalOffset + 1;
        }

        float [] floatValues = parameterArray.readFloat (first, 1);

        // Translate the pitch-bend from normalized 0..1 to 0..1200 cents
        final float normalizedPitchbend = Math.clamp (floatValues[0], 0, 1f);
        final int pitchbend = Math.round (1200f * normalizedPitchbend * normalizedPitchbend);

        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 23 : X0D_GLOBAL_TUNING), 3);
        final float tuning = floatValues[0] * 36f;

        // Amp-Envelope: 0 = One-shot, 1 = AHD, 2 = ADSR
        final int envelopeType = parameterArray.readInteger (offset + (isOldFormat ? 17 : X0D_GLOBAL_AMP_ENVELOPE_TYPE));
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 84 : 112), 3);
        final double attackTime = mapToAttackMillis (floatValues[0]) / 1000.0;
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 87 : 116), 3);
        final double holdTime = mapToAttackMillis (floatValues[0]) / 1000.0;
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 90 : 120), 3);
        final double decayTime = mapToDecayAndRelease (floatValues[0]) / 1000.0;
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 93 : 124), 3);
        final double sustainLevel = floatValues[0];
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 96 : 128), 3);
        final double releaseTime = mapToDecayAndRelease (floatValues[0]) / 1000.0;

        final int reverse = parameterArray.readInteger (offset + (isOldFormat ? 20 : 27));

        // Velocity modulation
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 66 : 88), 3);
        final double velocityToCutoff = floatValues[0];
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 69 : 92), 3);
        final double velocityToVolume = floatValues[0];

        // Modulation envelope
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 111 : 148), 3);
        final double modulationAttackTime = mapToAttackMillis (floatValues[0]) / 1000.0;
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 117 : 156), 3);
        final double modulationDecayTime = mapToDecayAndRelease (floatValues[0]) / 1000.0;
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 120 : 160), 3);
        final double modulationSustainLevel = floatValues[0];
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 123 : 164), 3);
        final double modulationReleaseTime = mapToDecayAndRelease (floatValues[0]) / 1000.0;

        // Modulation destinations
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 126 : 168), 3);
        final double pitchModulationIntensity = floatValues[0];
        floatValues = parameterArray.readFloat (offset + (isOldFormat ? 129 : 172), 3);
        final double cutoffModulationIntensity = floatValues[0];

        // LFO, the parameters are only known for the new format
        double lfoPitchAmount = 0;
        double lfoCutoffAmount = 0;
        double lfoModWheel = 0;
        int lfoType = 0;
        double lfoSpeed = 0.5;
        double lfoPhase = 0;
        if (!isOldFormat)
            try
            {
                lfoType = parameterArray.readInteger (offset + 185);
                lfoSpeed = parameterArray.readFloat (offset + 189, 3)[0];
                // -0.5..0.5
                lfoPhase = parameterArray.readFloat (offset + 193, 3)[0];
                lfoPitchAmount = parameterArray.readFloat (offset + 202, 3)[0];
                lfoCutoffAmount = parameterArray.readFloat (offset + 206, 3)[0];
                // Modulation wheel to LFO depth
                lfoModWheel = Math.clamp (parameterArray.readFloat (offset + 104, 3)[0], 0, 1);
            }
            catch (final IOException _)
            {
                // Ignore the LFO if the parameters are not present in this version
                lfoPitchAmount = 0;
                lfoCutoffAmount = 0;
            }
        // Tempo synchronized speeds (Retrig and Lock, parameter 198) select a note value. Since the
        // table of the note values is not known, the speed is converted like an unsynchronized one
        final LfoWaveform lfoWaveform = getLfoWaveform (lfoType);
        final double lfoRate = mapToLfoRate (lfoSpeed);
        final double lfoStartPhase = lfoPhase < 0 ? lfoPhase + 1 : lfoPhase;

        // Filter: 0 = Off, 1 = LP2, 2 = BP2, 3 = HP2, 4 = EQ
        final int filterType = parameterArray.readInteger (offset + (isOldFormat ? 42 : 56));
        if (filterType > 0 && filterType < 4)
        {
            floatValues = parameterArray.readFloat (offset + (isOldFormat ? 45 : 60), 3);
            final double cutoff = mapToFrequency (floatValues[0]);
            floatValues = parameterArray.readFloat (offset + (isOldFormat ? 48 : 64), 3);
            final double resonance = floatValues[0];

            final IFilter filter = new DefaultFilter (FILTER_TYPE_LOOKUP.get (Integer.valueOf (filterType)), 2, cutoff, Math.clamp (resonance, 0, 1));
            if (cutoffModulationIntensity != 0)
            {
                final IEnvelope globalFilterEnvelope = new DefaultEnvelope ();
                globalFilterEnvelope.setAttackTime (modulationAttackTime);
                globalFilterEnvelope.setDecayTime (modulationDecayTime);
                globalFilterEnvelope.setSustainLevel (modulationSustainLevel);
                globalFilterEnvelope.setReleaseTime (modulationReleaseTime);

                final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                cutoffModulator.setDepth (cutoffModulationIntensity);
                cutoffModulator.setSource (globalFilterEnvelope);

                filter.getCutoffVelocityModulator ().setDepth (velocityToCutoff);
            }

            if (lfoCutoffAmount != 0)
                applyLfo (filter.getCutoffLfoModulator (), lfoCutoffAmount, LFO_CUTOFF_RANGE_CENTS, lfoModWheel, lfoWaveform, lfoRate, lfoStartPhase);

            multisampleSource.setGlobalFilter (filter);
        }

        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setReversed (reverse == 1);
                zone.setBendUp (pitchbend);
                zone.setBendDown (-pitchbend);
                zone.setTuning (tuning + zone.getTuning ());

                // Amplitude envelope
                final IEnvelopeModulator amplitudeEnvelopeModulator = zone.getAmplitudeEnvelopeModulator ();
                final IEnvelope ampEnvelope = amplitudeEnvelopeModulator.getSource ();
                // Since there is no one-shot setting, crank up the release
                if (envelopeType == 0)
                    ampEnvelope.setReleaseTime (10);
                else
                {
                    ampEnvelope.setAttackTime (attackTime);
                    if (envelopeType == 1)
                        ampEnvelope.setHoldTime (holdTime);
                    ampEnvelope.setDecayTime (decayTime);
                    if (envelopeType == 2)
                    {
                        ampEnvelope.setReleaseTime (releaseTime);
                        ampEnvelope.setSustainLevel (sustainLevel);
                    }
                }
                zone.getAmplitudeVelocityModulator ().setDepth (velocityToVolume);

                // Pitch envelope
                if (pitchModulationIntensity != 0)
                {
                    final IEnvelope pitchEnvelope = new DefaultEnvelope ();
                    pitchEnvelope.setAttackTime (modulationAttackTime);
                    pitchEnvelope.setDecayTime (modulationDecayTime);
                    pitchEnvelope.setSustainLevel (modulationSustainLevel);
                    pitchEnvelope.setReleaseTime (modulationReleaseTime);

                    final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
                    pitchModulator.setDepth (pitchModulationIntensity);
                    pitchModulator.setSource (pitchEnvelope);
                }

                // Pitch LFO
                if (lfoPitchAmount != 0)
                    applyLfo (zone.getPitchLfoModulator (), lfoPitchAmount, LFO_PITCH_RANGE_CENTS, lfoModWheel, lfoWaveform, lfoRate, lfoStartPhase);
            }

        return offset + (isOldFormat ? 130 : 173);
    }


    /**
     * Read the parameters for a sample zone.
     *
     * @param zone The sample zone to fill
     * @param zoneOffset The offset to the zone
     * @param parameterArray The parameters
     * @param isOldFormat True if the file format version is older than 0x0E
     * @throws IOException Could not read the zone data
     */
    private static void readZoneParameters (final ISampleZone zone, final int zoneOffset, final MaschinePresetParameterArray parameterArray, final boolean isOldFormat) throws IOException
    {
        zone.setStart (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_SAMPLE_START : X0D_ZONE_SAMPLE_START)));
        zone.setStop (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_SAMPLE_END : X0D_ZONE_SAMPLE_END)));

        if (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_LOOP_ENABLED : X0D_ZONE_LOOP_ENABLED)) == 1)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_LOOP_START : X0D_ZONE_LOOP_START)));
            loop.setEnd (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_LOOP_END : X0D_ZONE_LOOP_END)));
            loop.setCrossfadeInSamples (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_LOOP_CROSSFADE : X0D_ZONE_LOOP_CROSSFADE)));
            zone.addLoop (loop);
        }

        zone.setKeyRoot (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_ROOT_KEY : X0D_ZONE_ROOT_KEY)));
        zone.setKeyLow (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_LOW_KEY : X0D_ZONE_LOW_KEY)));
        zone.setKeyHigh (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_HIGH_KEY : X0D_ZONE_HIGH_KEY)));
        zone.setVelocityLow (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_VELOCITY_LOW : X0D_ZONE_VELOCITY_LOW)));
        zone.setVelocityHigh (parameterArray.readInteger (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_VELOCITY_HIGH : X0D_ZONE_VELOCITY_HIGH)));

        zone.setGain (inputToDb (parameterArray.readFloat (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_GAIN : X0D_ZONE_GAIN))));
        zone.setPanning (parameterArray.readFloat (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_PANNING : X0D_ZONE_PANNING)));
        zone.setTuning (parameterArray.readFloat (zoneOffset + (isOldFormat ? PRE_X0D_ZONE_TUNE : X0D_ZONE_TUNE)));
    }


    /**
     * Reads the SoundInfo data and fills the metadata of the given multi-sample source with it.
     *
     * @param multisampleSource The multi-sample source in which to store the read data
     * @param data The sound info data
     * @param parts The file parts for category detection if no categories are stored in the sound
     *            info
     * @throws IOException Could not read the sound info section
     */
    private static void readSoundinfo (final IMultisampleSource multisampleSource, final byte [] data, final String [] parts) throws IOException
    {
        final IMetadata metadata = multisampleSource.getMetadata ();

        final ByteArrayInputStream soundInfoIn = new ByteArrayInputStream (data);
        StreamUtils.readUnsigned16 (soundInfoIn, false);

        // Could be a version: 02 02
        StreamUtils.readUnsigned16 (soundInfoIn, false);

        // Unknown
        soundInfoIn.skipNBytes (11);

        final String soundName = StreamUtils.readUtf16WithLength (soundInfoIn);
        if (!soundName.isBlank () && !PLUGIN_MASCHINE_SAMPLER.equals (soundName))
            multisampleSource.setName (soundName);
        final String soundAuthor = StreamUtils.readUtf16WithLength (soundInfoIn);
        if (!soundAuthor.isBlank ())
            metadata.setCreator (soundAuthor);
        final String soundCompany = StreamUtils.readUtf16WithLength (soundInfoIn);
        if (!soundCompany.isBlank ())
            metadata.setDescription (soundCompany);

        // 0, 0, 0, 0, 0, 0, 0, 0
        soundInfoIn.skipNBytes (8);
        // -1, -1, -1, -1, -1, -1, -1, -1
        soundInfoIn.skipNBytes (8);
        // 0, 0, 0, 0, 0, 0, 0, 0
        soundInfoIn.skipNBytes (8);
        // 0, 0, 0, 0, 0, 0, 0, 0
        soundInfoIn.skipNBytes (8);
        // 0, 0, 0, 0
        soundInfoIn.skipNBytes (4);
        // 1, 0, 0, 0
        soundInfoIn.skipNBytes (4);

        // Library info
        final int numLibraryInfo = (int) StreamUtils.readUnsigned32 (soundInfoIn, false);
        final List<String> libraryInfo = new ArrayList<> ();
        for (int i = 0; i < numLibraryInfo; i++)
            libraryInfo.add (StreamUtils.readUtf16WithLength (soundInfoIn));

        // Categories
        final int numCategoryPath = (int) StreamUtils.readUnsigned32 (soundInfoIn, false);
        final List<String> categoryPath = new ArrayList<> ();
        String categoryPart = "";
        // We only use the last one which contains the full sub-categories path
        for (int i = 0; i < numCategoryPath; i++)
            categoryPart = StreamUtils.readUtf16WithLength (soundInfoIn);
        final String [] categoryParts = categoryPart.split ("\\\\:");
        for (int i = categoryParts.length - 1; i >= 0; i--)
            if (!categoryParts[i].isBlank ())
                categoryPath.add (categoryParts[i]);
        String detectedCategory = TagDetector.detectCategory (categoryPath);
        if (TagDetector.CATEGORY_UNKNOWN.equals (detectedCategory))
            detectedCategory = TagDetector.detectCategory (Collections.singletonList (parts[0]), Arrays.asList (AudioFileUtils.getFolderNames (parts)), false);
        metadata.setCategory (detectedCategory);
        metadata.setKeywords (TagDetector.detectKeywords (categoryPath));

        // Padding
        StreamUtils.readUnsigned32 (soundInfoIn, false);

        final int numAttributes = (int) StreamUtils.readUnsigned32 (soundInfoIn, false);
        final Map<String, String> attributes = new HashMap<> ();
        for (int i = 0; i < numAttributes; i++)
        {
            final String name = StreamUtils.readUtf16WithLength (soundInfoIn);
            final String value = StreamUtils.readUtf16WithLength (soundInfoIn);
            attributes.put (name, value);
        }
    }


    /**
     * File paths are either absolute or relative to a library root (which is not known). Therefore,
     * the sample files need to be located.
     *
     * @param filePaths The read paths
     * @param sourceFilePath The location of the Maschine file
     * @param notifier For notifications
     * @return The corrected file paths
     */
    private static List<File> lookupFiles (final List<String> filePaths, final String sourceFilePath, final INotifier notifier)
    {
        int missingFiles = 0;

        final List<File> files = new ArrayList<> ();
        for (final String filename: filePaths)
        {
            File sampleFile = new File (filename);
            if (sampleFile.isAbsolute () && !sampleFile.exists ())
                sampleFile = new File (sourceFilePath, sampleFile.getName ());
            else
            {
                sampleFile = new File (sourceFilePath, filename);
                if (!sampleFile.exists ())
                {
                    // This should work for libraries
                    sampleFile = new File (new File (sourceFilePath).getParentFile ().getParentFile (), sampleFile.getName ());
                    if (!sampleFile.exists ())
                        sampleFile = new File (sourceFilePath, sampleFile.getName ());
                }
            }

            if (!sampleFile.exists ())
                missingFiles++;

            files.add (sampleFile);
        }

        // Only search for missing files, if all of them are missing!
        if (missingFiles != files.size ())
            return files;

        final List<File> lookedupFiles = new ArrayList<> ();
        File previousFolder = null;

        for (final File sampleFile: files)
        {
            // Find the sample file starting 2 folders up, which should work for libraries
            final int height = 2;
            final File file = AbstractDetector.findSampleFile (notifier, sampleFile.getParentFile (), previousFolder, sampleFile.getName (), height);
            if (file != null && file.exists ())
            {
                lookedupFiles.add (file);
                previousFolder = file.getParentFile ();
            }
            else
                lookedupFiles.add (sampleFile);
        }
        return lookedupFiles;
    }


    private static void writeFloatValueRow (final int offset, final List<byte []> array, final float value) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.write (0);
        MaschinePresetParameterArray.writeFloats (out, value, value);
        StreamUtils.padBytes (out, 9);
        array.set (offset, out.toByteArray ());
    }


    /**
     * Converts gain in the range of [0..1] to the range [-82.3f..10.0f]. -82.3f is very close to
     * -Inf.
     *
     * @param input The input value in the range of [0..1]
     * @return The gain value in dB
     */
    public static float inputToDb (final float input)
    {
        if (input <= 0.0f)
            return MIN_DB;
        final float db = GAIN * (float) Math.log10 (input / REFERENCE_DB);
        return Math.max (db, MIN_DB);
    }


    /**
     * Converts gain in the range of [-82.3f..10.0f] to the range [0..1]. -82.3f is very close to
     * -Inf.
     *
     * @param db The gain value in dB
     * @return The input value in the range of [0..1]
     */
    public static float dbToInput (final double db)
    {
        if (db <= MIN_DB)
            return 0.0f;
        return (float) (REFERENCE_DB * Math.pow (10.0, db / GAIN));
    }


    /**
     * Converts the given normalized value to milli-seconds. Tweaked for the attack part of the
     * envelope.
     *
     * @param v The normalized value in the range of [0..1]
     * @return The value in milli-seconds
     */
    public static float mapToAttackMillis (final float v)
    {
        final float x = Math.clamp (v, 0, 1f);
        if (x <= 0.5f)
            // from (0,0) to (0.5,171)
            return 171f / 0.5f * x;
        if (x <= 0.85f)
            // from (0.5,171) to (0.85,2100)
            return 171f + (2100f - 171f) / (0.85f - 0.5f) * (x - 0.5f);

        // from (0.85,2100) to (1,7700)
        return 2100f + (7700f - 2100f) / (1f - 0.85f) * (x - 0.85f);
    }


    /**
     * Converts the given milli-seconds to normalized value. Tweaked for the attack part of the
     * envelope.
     *
     * @param millis The value in milli-seconds
     * @return The normalized value in the range of [0..1]
     */
    public static float attackMillisToInput (final float millis)
    {
        final float y = Math.clamp (millis, 0f, 7700f);
        if (y <= 171f)
            return y / 342f;
        if (y <= 2100f)
            return 0.5f + (y - 171f) / 5520f;
        return 0.85f + (y - 2100f) / 37333.333f;
    }


    /**
     * Converts the given normalized value to milli-seconds. Tweaked for the decay and release part
     * of the envelope.
     *
     * @param v The normalized value in the range of [0..1]
     * @return The value in milli-seconds
     */
    public static float mapToDecayAndRelease (final float v)
    {
        final float x = Math.clamp (v, 0, 1f);
        if (x <= 0.29f)
            // 0.00 → 2.9 ; 0.29 → 72.7
            return 2.9f + (72.7f - 2.9f) / 0.29f * x;
        if (x <= 0.50f)
            // 0.29 → 72.7 ; 0.50 → 217.0
            return 72.7f + (217f - 72.7f) / (0.50f - 0.29f) * (x - 0.29f);
        if (x <= 0.77f)
            // 0.50 → 217.0; 0.77 → 1500.0
            return 217f + (1500f - 217f) / (0.77f - 0.50f) * (x - 0.50f);

        // 0.77 → 1500.0; 1.00 → 12300.0
        return 1500f + (12300f - 1500f) / (1.00f - 0.77f) * (x - 0.77f);
    }


    /**
     * Converts the given milli-seconds to normalized value. Tweaked for the decay and release part
     * of the envelope.
     *
     * @param millis The value in milli-seconds
     * @return The normalized value in the range of [0..1]
     */
    public static float decayAndReleaseMillisToInput (final float millis)
    {
        final float y = Math.clamp (millis, 2.9f, 12300f);

        if (y <= 72.7f)
            return (y - 2.9f) / 241.37931f;

        if (y <= 217f)
            return 0.29f + (y - 72.7f) / 687.6190f;

        if (y <= 1500f)
            return 0.50f + (y - 217f) / 4733.3333f;

        return 0.77f + (y - 1500f) / 46956.5217f;
    }


    /**
     * Maps the normalized input value to frequency in Hz using exponential curve, does not match
     * 100% but very close.
     *
     * @param value The normalized value in the range of [0..1]
     * @return The frequency in the range of [43.7..19600] Hertz
     */
    public static double mapToFrequency (final double value)
    {
        return Math.clamp (51.0917 * Math.exp (5.9497 * value), 43.7, 19600);
    }


    /**
     * Maps the frequency in Hz to normalized input value using exponential curve, does not match
     * 100% but very close.
     *
     * @param frequency The frequency in the range of [43.7..19600] Hertz
     * @return The normalized value in the range of [0..1]
     */
    public static float frequencyToNorm (final double frequency)
    {
        final double y = Math.clamp (frequency, 43.7, 19600.0);
        return (float) (Math.log (y / 51.0917) / 5.9497);
    }


    /**
     * Set the LFO parameters of the given LFO modulator.
     *
     * @param modulator The LFO modulator
     * @param amount The modulation amount in the range of [-1..1]
     * @param rangeCents The modulation in cents of an amount of 1
     * @param modWheelAmount The share of the amount which is controlled by the modulation wheel in
     *            the range of [0..1]. The full amount is reached with the modulation wheel fully up
     * @param waveform The waveform
     * @param rate The rate in Hertz
     * @param startPhase The start phase in the range of [0..1]
     */
    public static void applyLfo (final ILfoModulator modulator, final double amount, final double rangeCents, final double modWheelAmount, final LfoWaveform waveform, final double rate, final double startPhase)
    {
        final double depth = amount * rangeCents / IEnvelope.MAX_ENVELOPE_DEPTH;
        modulator.setDepth (Math.clamp (depth * (1.0 - modWheelAmount), -1.0, 1.0));
        modulator.getDepthModWheelModulator ().setDepth (Math.clamp (depth * modWheelAmount, -1.0, 1.0));

        final ILfo lfo = modulator.getSource ();
        lfo.setWaveform (waveform);
        lfo.setRate (rate);
        lfo.setStartPhase (Math.clamp (startPhase, 0.0, 1.0));
        lfo.setKeySync (true);
    }


    /**
     * Get the modulation amount of the given LFO modulator with the modulation wheel fully up.
     *
     * @param modulator The LFO modulator
     * @param rangeCents The modulation in cents of an amount of 1
     * @return The amount in the range of [-1..1]
     */
    public static double getLfoAmount (final ILfoModulator modulator, final double rangeCents)
    {
        final double fullDepth = modulator.getDepth () + modulator.getDepthModWheelModulator ().getDepth ();
        return Math.clamp (fullDepth * IEnvelope.MAX_ENVELOPE_DEPTH / rangeCents, -1.0, 1.0);
    }


    /**
     * Get the share of the depth of the given LFO modulator which is controlled by the modulation
     * wheel.
     *
     * @param modulator The LFO modulator
     * @return The share in the range of [0..1]
     */
    public static double getLfoModWheelAmount (final ILfoModulator modulator)
    {
        final double modWheelDepth = modulator.getDepthModWheelModulator ().getDepth ();
        final double fullDepth = modulator.getDepth () + modWheelDepth;
        if (fullDepth == 0)
            return 0;
        return Math.clamp (modWheelDepth / fullDepth, 0.0, 1.0);
    }


    /**
     * Get the LFO waveform for the given index.
     *
     * @param index The index of the waveform: 0 = Sine, 1 = Triangle, 2 = Rectangle, 3 = Saw, 4 =
     *            Random
     * @return The waveform
     */
    public static LfoWaveform getLfoWaveform (final int index)
    {
        return LFO_WAVEFORMS[Math.clamp (index, 0, LFO_WAVEFORMS.length - 1)];
    }


    /**
     * Get the index of the given LFO waveform.
     *
     * @param waveform The waveform
     * @return The index of the waveform: 0 = Sine, 1 = Triangle, 2 = Rectangle, 3 = Saw, 4 = Random
     */
    public static int getLfoWaveformIndex (final LfoWaveform waveform)
    {
        return switch (waveform)
        {
            case TRIANGLE -> 1;
            case SQUARE -> 2;
            case SAWTOOTH_UP, SAWTOOTH_DOWN -> 3;
            case RANDOM -> 4;
            default -> 0;
        };
    }


    /**
     * Maps the normalized LFO speed to a rate.
     *
     * @param speed The normalized speed in the range of [0..1]
     * @return The rate in Hertz
     */
    public static double mapToLfoRate (final double speed)
    {
        return interpolateLogarithmic (Math.clamp (speed, 0.0, 1.0), LFO_SPEED_VALUES, LFO_SPEED_RATES, false);
    }


    /**
     * Maps a rate to the normalized LFO speed.
     *
     * @param rate The rate in Hertz
     * @return The normalized speed in the range of [0..1]
     */
    public static float lfoRateToInput (final double rate)
    {
        final double clampedRate = Math.clamp (rate, LFO_SPEED_RATES[0], LFO_SPEED_RATES[LFO_SPEED_RATES.length - 1]);
        return (float) interpolateLogarithmic (clampedRate, LFO_SPEED_RATES, LFO_SPEED_VALUES, true);
    }


    /**
     * Interpolate between the points of two tables, the values of the rate table are interpolated
     * logarithmically.
     *
     * @param value The value to look up in the input table, must be in the range of the table
     * @param inputs The input table with ascending values
     * @param outputs The output table
     * @param isInputRate True if the input table contains the rates, false if the output table
     *            contains them
     * @return The interpolated output value
     */
    private static double interpolateLogarithmic (final double value, final double [] inputs, final double [] outputs, final boolean isInputRate)
    {
        for (int i = 1; i < inputs.length; i++)
        {
            if (value > inputs[i])
                continue;
            if (isInputRate)
            {
                final double position = Math.log (value / inputs[i - 1]) / Math.log (inputs[i] / inputs[i - 1]);
                return outputs[i - 1] + position * (outputs[i] - outputs[i - 1]);
            }
            final double position = (value - inputs[i - 1]) / (inputs[i] - inputs[i - 1]);
            return outputs[i - 1] * Math.pow (outputs[i] / outputs[i - 1], position);
        }
        return outputs[outputs.length - 1];
    }


    private class Offsets
    {
        final boolean isOldFormat;

        int           offsetFirstZone;
        int           offsetPluginInfo;
        int           offsetNumberOfSamples;
        int           offsetPluginName;
        int           groupOffset;


        public Offsets (final boolean isOldFormat)
        {
            this.isOldFormat = isOldFormat;

            this.update (0);
        }


        public void update (final int groupOffset)
        {
            this.groupOffset = groupOffset;

            this.offsetFirstZone = this.groupOffset + (this.isOldFormat ? PRE_X0D_FIRST_ZONE : X0D_FIRST_ZONE);
            this.offsetPluginInfo = this.groupOffset + (this.isOldFormat ? PRE_X0D_PLUGIN_INFO : X0D_PLUGIN_INFO);
            this.offsetNumberOfSamples = this.groupOffset + (this.isOldFormat ? PRE_X0D_NUMBER_OF_SAMPLES : X0D_NUMBER_OF_SAMPLES);
            this.offsetPluginName = this.groupOffset + (this.isOldFormat ? PRE_X0D_FIRST_ZONE_PLUGIN_NAME : X0D_FIRST_ZONE_PLUGIN_NAME);
        }
    }
}
