// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.ExternalModulator;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Group;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.InternalModulator;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Program;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Zone;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.ZoneLoop;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Abstract base class for handling NKI files in a specific format.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractKontaktFormat implements IKontaktFormat
{
    protected final INotifier notifier;
    protected boolean         isBigEndian;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    protected AbstractKontaktFormat (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /**
     * Fill the given multi-sample instance with data from the program.
     *
     * @param multiSample The multi-sample to fill
     * @param program The program from which to get the data
     * @param parts Parts of the name for category detection
     * @param filePaths All file paths
     * @param isMonolith True if all files are stored in a monolith
     * @throws IOException Error finding samples
     */
    @SuppressWarnings("null")
    public void fillInto (final IMultisampleSource multiSample, final Program program, final String [] parts, final List<String> filePaths, boolean isMonolith) throws IOException
    {
        setMetadata (multiSample, program, parts);

        final List<File> files = this.lookupFiles (filePaths, multiSample.getSourceFile ().getParent (), isMonolith);
        final Map<Integer, Pair<IGroup, Group>> indexedGroups = createGroups (program);
        for (final Zone kontaktZone: program.getZones ())
        {
            // Zones without a sample file might be present
            if (kontaktZone.getFilenameId () < 0)
                continue;

            final Integer groupIndex = Integer.valueOf (kontaktZone.getGroupIndex ());
            final Pair<IGroup, Group> groupPair = indexedGroups.get (groupIndex);
            if (groupPair == null)
                throw new IOException (Functions.getMessage ("IDS_NKI5_MISSING_GROUP", groupIndex.toString ()));

            final IGroup group = groupPair.getKey ();
            final Group kontaktGroup = groupPair.getValue ();

            final ISampleZone zone = this.createZone (kontaktZone, files);
            group.addSampleZone (zone);

            zone.setStart (kontaktZone.getSampleStart ());
            zone.setStop (kontaktZone.getNumFrames () - kontaktZone.getSampleEnd ());

            zone.setKeyLow (kontaktZone.getLowKey ());
            zone.setKeyHigh (kontaktZone.getHighKey ());
            final int rootKey = kontaktZone.getRootKey ();
            zone.setKeyRoot (rootKey);

            final float volume = program.getInstrumentVolume () * kontaktGroup.getVolume () * kontaktZone.getZoneVolume ();
            zone.setGain (MathUtils.valueToDb (volume));
            zone.setPanning (Math.clamp (program.getInstrumentPan () + kontaktGroup.getPan () + kontaktZone.getZonePan (), -1, 1));

            zone.setTuning (calculateTune (kontaktZone.getZoneTune (), kontaktGroup.getTune (), program.getInstrumentTune ()));
            zone.setKeyTracking (kontaktGroup.isKeyTracking () ? 1 : 0);

            zone.setVelocityLow (kontaktZone.getLowVelocity ());
            zone.setVelocityHigh (kontaktZone.getHighVelocity ());

            zone.setNoteCrossfadeLow (kontaktZone.getFadeLowKey ());
            zone.setNoteCrossfadeHigh (kontaktZone.getFadeHighKey ());
            zone.setVelocityCrossfadeLow (kontaktZone.getFadeLowKey ());
            zone.setVelocityCrossfadeHigh (kontaktZone.getFadeHighVelocity ());

            // Only on a group level...
            zone.setReversed (kontaktGroup.isReverse ());

            // IMPROVE Fill Filter info, when understood where it is stored
            final IFilter filter = null;

            // Set pitch by pitch-bend and amplitude by velocity modulation
            boolean foundAmpVelocity = false;
            for (final ExternalModulator modulator: kontaktGroup.getExternalModulators ())
            {
                final long sourceType = modulator.getSourceType ();
                final long destType = modulator.getDestType ();
                if (sourceType == ExternalModulator.SOURCE_PITCHBEND && (destType == ExternalModulator.DEST_PITCH || destType == ExternalModulator.DEST_PITCH2))
                {
                    final int cents = Math.round (modulator.getIntensity () * 12) * 100;
                    zone.setBendUp (cents);
                    zone.setBendDown (-cents);
                }
                else if ((sourceType == ExternalModulator.SOURCE_VELOCITY || sourceType == ExternalModulator.SOURCE_VELOCITY2) && (destType == ExternalModulator.DEST_VOLUME || destType == ExternalModulator.DEST_VOLUME2 || destType == ExternalModulator.DEST_VOLUME3))
                {
                    zone.getAmplitudeVelocityModulator ().setDepth (modulator.getIntensity ());
                    foundAmpVelocity = true;
                }
            }
            if (!foundAmpVelocity)
                zone.getAmplitudeVelocityModulator ().setDepth (0);

            // Set amplitude and pitch modulator
            for (final InternalModulator modulator: kontaktGroup.getInternalModulators ())
            {
                final Optional<IEnvelopeModulator> volumeEnvelope = modulator.getVolumeEnvelope ();
                if (volumeEnvelope.isPresent ())
                {
                    final IEnvelopeModulator amplitudeEnvelopeModulator = zone.getAmplitudeEnvelopeModulator ();
                    final IEnvelopeModulator kontaktEnvelopeModulator = volumeEnvelope.get ();
                    amplitudeEnvelopeModulator.setDepth (kontaktEnvelopeModulator.getDepth ());
                    amplitudeEnvelopeModulator.setSource (kontaktEnvelopeModulator.getSource ());
                }
                final Optional<IEnvelopeModulator> pitchEnvelope = modulator.getPitchEnvelope ();
                if (pitchEnvelope.isPresent ())
                {
                    final IEnvelopeModulator pitchModulator = zone.getPitchModulator ();
                    final IEnvelopeModulator kontaktPitchModulator = pitchEnvelope.get ();
                    pitchModulator.setDepth (kontaktPitchModulator.getDepth ());
                    pitchModulator.setSource (kontaktPitchModulator.getSource ());
                }
                if (filter != null)
                {
                    final Optional<IEnvelopeModulator> cutoffEnvelope = modulator.getFilterCutoffEnvelope ();
                    if (cutoffEnvelope.isPresent ())
                    {
                        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                        final IEnvelopeModulator kontaktCutoffModulator = cutoffEnvelope.get ();
                        cutoffModulator.setDepth (kontaktCutoffModulator.getDepth ());
                        cutoffModulator.setSource (kontaktCutoffModulator.getSource ());
                    }
                }
            }

            for (final ZoneLoop zoneLoop: kontaktZone.getLoops ())
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                final int loopMode = zoneLoop.getMode ();
                if (loopMode == ZoneLoop.MODE_UNTIL_END || loopMode == ZoneLoop.MODE_UNTIL_RELEASE)
                {
                    loop.setType (zoneLoop.isAlternating () ? LoopType.ALTERNATING : LoopType.FORWARDS);
                    loop.setStart (zoneLoop.getLoopStart ());
                    loop.setEnd (zoneLoop.getLoopStart () + zoneLoop.getLoopLength ());
                    loop.setCrossfadeInSamples (zoneLoop.getCrossfadeLength ());
                    zone.addLoop (loop);
                }
            }
        }

        final List<IGroup> sampleGroups = new ArrayList<> ();
        for (final Pair<IGroup, Group> pair: indexedGroups.values ())
            sampleGroups.add (pair.getKey ());
        multiSample.setGroups (sampleGroups);
    }


    private static void setMetadata (final IMultisampleSource source, final Program program, final String [] parts)
    {
        source.setName (program.getName ());

        final IMetadata metadata = source.getMetadata ();

        final String instrumentAuthor = program.getInstrumentAuthor ();
        if (instrumentAuthor != null && !instrumentAuthor.isBlank ())
            metadata.setCreator (instrumentAuthor);
        final String instrumentURL = program.getInstrumentURL ();
        if (instrumentURL != null && !instrumentURL.isBlank ())
            metadata.setDescription (instrumentURL);

        final String instrumentIconName = program.getInstrumentIconName ();
        if (instrumentIconName == null || instrumentIconName.isBlank () || "New".equals (instrumentIconName))
            metadata.setCategory (TagDetector.detectCategory (parts));
        else
            metadata.setCategory (TagDetector.detectCategory (instrumentIconName.split (" ")));
    }


    private static Map<Integer, Pair<IGroup, Group>> createGroups (final Program program)
    {
        final Map<Integer, Pair<IGroup, Group>> map = new TreeMap<> ();
        final List<Group> groups = program.getGroups ();
        for (int i = 0; i < groups.size (); i++)
        {
            final Group kontaktGroup = groups.get (i);
            final IGroup group = new DefaultGroup ();
            group.setName (kontaktGroup.getName ());
            if (kontaktGroup.isReleaseTrigger ())
                group.setTrigger (TriggerType.RELEASE);
            map.put (Integer.valueOf (i), new Pair<> (group, kontaktGroup));
        }
        return map;
    }


    private List<File> lookupFiles (final List<String> filePaths, final String sourceFilePath, final boolean isMonolith)
    {
        int missingFiles = 0;

        final List<File> files = new ArrayList<> ();
        for (final String filename: filePaths)
        {
            File sampleFile = new File (filename);
            if ((sampleFile.isAbsolute () || filename.startsWith ("/Volumes")) && !sampleFile.exists ())
                sampleFile = new File (sourceFilePath, sampleFile.getName ());
            else
            {
                sampleFile = new File (sourceFilePath, filename);
                if (!sampleFile.exists ())
                    sampleFile = new File (sourceFilePath, sampleFile.getName ());
            }

            if (!sampleFile.exists ())
                missingFiles++;

            files.add (sampleFile);
        }

        // Only search for missing files, if all of them are missing!
        if (isMonolith || (missingFiles != files.size ()))
            return files;

        final List<File> lookedupFiles = new ArrayList<> ();
        File previousFolder = null;

        for (final File sampleFile: files)
        {
            // Find the sample file starting 2 folders up
            final int height = 2;
            final File file = AbstractDetector.findSampleFile (this.notifier, sampleFile.getParentFile (), previousFolder, sampleFile.getName (), height);
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


    private ISampleZone createZone (final Zone zone, final List<File> files) throws IOException
    {
        final int filenameId = zone.getFilenameId ();
        if (filenameId < 0 || filenameId >= files.size ())
            throw new IOException (Functions.getMessage ("IDS_NKI5_WRONG_FILE_INDEX", Integer.toString (filenameId)));

        // Check if it is an absolute path, try to find the sample file...
        final File sampleFile = files.get (filenameId);
        // Ignore non-existing files since it might be in a monolith
        final ISampleData sampleData = sampleFile.exists () ? AbstractDetector.createSampleData (sampleFile, this.notifier) : null;
        return new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
    }


    private static double calculateTune (final double zoneTune, final double groupTune, final double progTune)
    {
        // All three tune values are stored logarithmically
        final double value = 12.0 * Math.log (zoneTune * groupTune * progTune) / Math.log (2);
        return Math.round (value * 100000) / 100000.0;
    }
}
