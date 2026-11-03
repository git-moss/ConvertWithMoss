// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkmPart;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkmZ48Part;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpKeygroupLocation;


/**
 * Creator for Akai S5000/S6000/Z4/Z8 AKP files. Each program is stored in its own folder together
 * with its samples since the samples are referenced by their name only and need to be in the same
 * folder as the program. Performances are stored as AKM files (multis), together with all their
 * programs and samples in one folder.
 *
 * @author Jürgen Moßgraber
 */
public class AkpCreator extends AbstractWavCreator<AkpCreatorUI>
{
    /** The maximum length of the program and sample names. */
    private static final int                    MAX_NAME_LENGTH          = 20;
    private static final DestinationAudioFormat DESTINATION_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, 48000, false);


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkpCreator (final INotifier notifier)
    {
        super ("Akai AKP", "S5000", notifier, new AkpCreatorUI ("S5000"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String programName = createName (multisampleSource.getName (), "Program");

        // Store the program and all samples in one folder
        final File programFolder = this.createUniqueFilename (destinationFolder, programName, "");
        safeCreateDirectory (programFolder);

        if (this.writeProgram (programFolder, programName, multisampleSource, new HashSet<> ()))
            this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    public void createPerformance (final File destinationFolder, final IPerformanceSource performanceSource) throws IOException
    {
        List<IInstrumentSource> instruments = performanceSource.getInstruments ();
        if (instruments.isEmpty ())
            return;

        final String multiName = createName (performanceSource.getName (), "Multi");
        if (instruments.size () > AkmFile.NUMBER_OF_PARTS)
        {
            this.notifier.logError ("IDS_AKM_TOO_MANY_PARTS", multiName, Integer.toString (instruments.size ()), Integer.toString (AkmFile.NUMBER_OF_PARTS));
            instruments = instruments.subList (0, AkmFile.NUMBER_OF_PARTS);
        }

        // Store the multi, all programs and all samples in one folder since they are referenced
        // by their names only
        final File multiFolder = this.createUniqueFilename (destinationFolder, multiName, "");
        safeCreateDirectory (multiFolder);

        // Instruments which use the same multi-sample share the program
        final Map<IMultisampleSource, String> programNames = new IdentityHashMap<> ();
        final Set<String> usedProgramNames = new HashSet<> ();
        final Set<String> usedSampleNames = new HashSet<> ();
        final List<IInstrumentSource> partInstruments = new ArrayList<> ();
        final AkmFile akmFile = new AkmFile ();
        final List<AkmPart> parts = akmFile.getParts ();
        for (final IInstrumentSource instrumentSource: instruments)
        {
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();
            String programName = programNames.get (multisampleSource);
            if (programName == null)
            {
                programName = createUniqueName (createName (multisampleSource.getName (), "Program"), usedProgramNames);
                if (!this.writeProgram (multiFolder, programName, multisampleSource, usedSampleNames))
                    continue;
                programNames.put (multisampleSource, programName);
            }

            setPart (parts.get (partInstruments.size ()), programName, instrumentSource);
            partInstruments.add (instrumentSource);
        }
        if (partInstruments.isEmpty ())
            return;

        // The Z4/Z8 parts store the level with a higher resolution
        final List<AkmZ48Part> z48Parts = akmFile.createZ48Chunk ().getParts ();
        for (int i = 0; i < partInstruments.size (); i++)
            z48Parts.get (i).setLevel (Math.round (Math.clamp (partInstruments.get (i).getGain (), -60.0, 6.0) * 10.0) / 10.0);

        final File multiFile = new File (multiFolder, multiName + ".AKM");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
        akmFile.write (multiFile, this.settingsConfiguration.getGeneration ());

        this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    protected void additionalProcessing (final IMultisampleSource multisampleSource, final ISampleZone zone, final WaveFile wavFile)
    {
        super.additionalProcessing (multisampleSource, zone, wavFile);

        // The program contains no root key and loops, they are always read from the sample chunk.
        // The tuning is stored in the program, therefore the pitch fraction is not set
        updateSampleChunk (zone, wavFile);
        final int keyRoot = zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot ();
        wavFile.getSampleChunk ().setPitch (Math.clamp (keyRoot, 0, 127), 0);
    }


    /**
     * Write an AKP file and its samples.
     *
     * @param programFolder The folder in which to store the program and the samples
     * @param programName The name of the program, which is also the file name
     * @param multisampleSource The multi-sample to store
     * @param usedSampleNames The upper case names of the samples which are already stored in the
     *            folder, the names of the samples of the program are added
     * @return True if the program was written, false if the multi-sample contains no samples
     * @throws IOException Could not store the program or the samples
     */
    private boolean writeProgram (final File programFolder, final String programName, final IMultisampleSource multisampleSource, final Set<String> usedSampleNames) throws IOException
    {
        final List<ISampleZone> sampleZones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            sampleZones.addAll (group.getSampleZones ());
        if (sampleZones.isEmpty ())
            return false;
        createSampleNames (sampleZones, usedSampleNames);

        for (final ISampleZone zone: sampleZones)
            if (zone.getKeyLow () < AkpKeygroupLocation.LOWEST_NOTE)
            {
                this.notifier.logError ("IDS_AKP_LOW_KEY_LIMITED", programName, Integer.toString (AkpKeygroupLocation.LOWEST_NOTE));
                break;
            }

        List<List<ISampleZone>> keygroupZones = AkpFile.createKeygroupZones (sampleZones);
        if (keygroupZones.size () > AkpFile.MAX_KEYGROUPS)
        {
            this.notifier.logError ("IDS_AKP_TOO_MANY_KEYGROUPS", programName, Integer.toString (keygroupZones.size ()), Integer.toString (AkpFile.MAX_KEYGROUPS));
            keygroupZones = keygroupZones.subList (0, AkpFile.MAX_KEYGROUPS);
        }
        final List<ISampleZone> writtenZones = new ArrayList<> ();
        for (final List<ISampleZone> zones: keygroupZones)
            writtenZones.addAll (zones);

        final AkpFile akpFile = new AkpFile ();
        akpFile.setSampleZones (keygroupZones);
        final File programFile = new File (programFolder, programName + ".AKP");
        this.notifier.log ("IDS_NOTIFY_STORING", programFile.getAbsolutePath ());
        akpFile.write (programFile, this.settingsConfiguration.getGeneration ());

        // The samples are trimmed to the start and end of the zones since the program cannot
        // store them
        this.writeSamples (programFolder, multisampleSource, writtenZones, ".WAV", DESTINATION_AUDIO_FORMAT, true);
        return true;
    }


    /**
     * Set the parameters of a multi part from an instrument.
     *
     * @param part The part to fill
     * @param programName The name of the program to play
     * @param instrumentSource The instrument
     */
    private static void setPart (final AkmPart part, final String programName, final IInstrumentSource instrumentSource)
    {
        part.setPresetName (programName);

        // There is no OMNI mode, use the first channel instead
        final int midiChannel = instrumentSource.getMidiChannel ();
        part.setMidiChannel (midiChannel >= 0 && midiChannel < 16 ? midiChannel : 0);

        part.setLowKey (Math.clamp (instrumentSource.getClipKeyLow (), 0, 127));
        part.setHighKey (Math.clamp (instrumentSource.getClipKeyHigh (), 0, 127));
        part.setTranspose (Math.clamp (instrumentSource.getTranspose (), -36, 36));
        part.setFineTune (Math.clamp (instrumentSource.getTuning (), -50, 50));
        part.setPanning ((int) Math.round (Math.clamp (instrumentSource.getPanning (), -1.0, 1.0) * 50.0));
        // The level is roughly a linear gain factor with 100 = 0dB
        part.setVolume ((int) Math.round (Math.clamp (100.0 * Math.pow (10.0, instrumentSource.getGain () / 20.0), 0.0, 100.0)));
    }


    /**
     * Set unique sample names with a maximum length of 20 characters to all zones. Zones with the
     * same name keep sharing the same sample.
     *
     * @param sampleZones The sample zones
     * @param usedNames The upper case names which are already in use, the new names are added
     */
    private static void createSampleNames (final List<ISampleZone> sampleZones, final Set<String> usedNames)
    {
        final Map<String, String> names = new HashMap<> ();
        for (final ISampleZone zone: sampleZones)
        {
            final String originalName = zone.getName () == null ? "" : zone.getName ();
            String name = names.get (originalName);
            if (name == null)
            {
                name = createUniqueName (createName (originalName, "Sample"), usedNames);
                names.put (originalName, name);
            }
            zone.setName (name);
        }
    }


    /**
     * Create a name which is not yet in use by adding a number to the given name. The file systems
     * are case-insensitive, therefore the names are compared in upper case.
     *
     * @param baseName The name, maximum 20 characters
     * @param usedNames The upper case names which are already in use, the new name is added
     * @return The unique name, maximum 20 characters
     */
    private static String createUniqueName (final String baseName, final Set<String> usedNames)
    {
        String name = baseName;
        for (int counter = 2; usedNames.contains (name.toUpperCase (Locale.US)); counter++)
        {
            final String suffix = "_" + counter;
            name = baseName.substring (0, Math.min (baseName.length (), MAX_NAME_LENGTH - suffix.length ())).trim () + suffix;
        }
        usedNames.add (name.toUpperCase (Locale.US));
        return name;
    }


    /**
     * Create a name which can be used as a file name and in the AKP file: only ASCII characters and
     * limited to 20 characters.
     *
     * @param name The name
     * @param defaultName The name to use if the given name is empty
     * @return The name
     */
    private static String createName (final String name, final String defaultName)
    {
        final String asciiName = SafeFileNames.create (name).replaceAll ("[^\\x20-\\x7E]", "_");
        final String result = asciiName.substring (0, Math.min (asciiName.length (), MAX_NAME_LENGTH)).trim ();
        return result.isEmpty () ? defaultName : result;
    }
}
