// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpKeygroupLocation;


/**
 * Creator for Akai S5000/S6000/Z4/Z8 AKP files. Each program is stored in its own folder together
 * with its samples since the samples are referenced by their name only and need to be in the same
 * folder as the program.
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
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String programName = createName (multisampleSource.getName (), "Program");

        // Store the program and all samples in one folder
        final File programFolder = this.createUniqueFilename (destinationFolder, programName, "");
        safeCreateDirectory (programFolder);

        final List<ISampleZone> sampleZones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            sampleZones.addAll (group.getSampleZones ());
        if (sampleZones.isEmpty ())
            return;
        createSampleNames (sampleZones);

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
     * Set unique sample names with a maximum length of 20 characters to all zones. Zones with the
     * same name keep sharing the same sample.
     *
     * @param sampleZones The sample zones
     */
    private static void createSampleNames (final List<ISampleZone> sampleZones)
    {
        final Map<String, String> names = new HashMap<> ();
        final Set<String> usedNames = new HashSet<> ();
        for (final ISampleZone zone: sampleZones)
        {
            final String originalName = zone.getName () == null ? "" : zone.getName ();
            String name = names.get (originalName);
            if (name == null)
            {
                final String baseName = createName (originalName, "Sample");
                name = baseName;
                // The file systems are case-insensitive
                for (int counter = 2; usedNames.contains (name.toUpperCase (Locale.US)); counter++)
                {
                    final String suffix = "_" + counter;
                    name = baseName.substring (0, Math.min (baseName.length (), MAX_NAME_LENGTH - suffix.length ())).trim () + suffix;
                }
                names.put (originalName, name);
                usedNames.add (name.toUpperCase (Locale.US));
            }
            zone.setName (name);
        }
    }


    /**
     * Create a name which can be used as a file name and in the AKP file: only ASCII characters
     * and limited to 20 characters.
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
