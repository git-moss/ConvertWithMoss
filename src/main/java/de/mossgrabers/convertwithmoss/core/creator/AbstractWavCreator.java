// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.creator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;
import de.mossgrabers.convertwithmoss.file.wav.BroadcastAudioExtensionChunk;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.InstrumentChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk.SampleChunkLoop;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.XMLUtils;


/**
 * A creator which uses WAV files to store the samples.
 *
 * @param <T> The type of the settings
 * 
 * @author Jürgen Moßgraber
 */
public abstract class AbstractWavCreator<T extends WavChunkSettingsUI> extends AbstractCreator<T>
{
    /**
     * Constructor.
     *
     * @param name The name of the creator.
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     * @param settingsConfiguration The configuration of the settings
     */
    protected AbstractWavCreator (final String name, final String prefix, final INotifier notifier, final T settingsConfiguration)
    {
        super (name, prefix, notifier, settingsConfiguration);
    }


    /**
     * Adds a sample file to the uncompressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zipOutputStream The ZIP output stream
     * @param metadata The multi-sample metadata
     * @param zone The zone to add
     * @param path Optional path (may be null), must not end with a slash
     * @throws IOException Could not read the file
     */
    protected void storeSamplefile (final Set<String> alreadyStored, final ZipOutputStream zipOutputStream, final IMetadata metadata, final ISampleZone zone, final String path) throws IOException
    {
        final String name = checkSampleName (alreadyStored, zone, path);
        if (name == null)
            return;

        final ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), name);
            this.notifier.logText ("\n");
            return;
        }

        final CRC32 crc = new CRC32 ();
        try (final ByteArrayOutputStream bout = new ByteArrayOutputStream (); final OutputStream checkedOut = new CheckedOutputStream (bout, crc))
        {
            if (this.settingsConfiguration.requiresRewrite (DESTINATION_FORMAT))
                this.rewriteFile (metadata, zone, checkedOut, DESTINATION_FORMAT, false);
            else
                sampleData.writeSample (checkedOut);
            putUncompressedEntry (zipOutputStream, name, bout.toByteArray (), crc, metadata.getCreationDateTime ());
        }
    }


    /**
     * Add all samples from all groups in the given uncompressed ZIP output stream.
     *
     * @param zipOutputStream The ZIP output stream to which to add the samples
     * @param relativeFolderName The relative folder under which to store the file in the ZIP
     * @param multisampleSource The multi-sample
     * @throws IOException Could not store the samples
     */
    protected void storeSampleFiles (final ZipOutputStream zipOutputStream, final String relativeFolderName, final IMultisampleSource multisampleSource) throws IOException
    {
        int outputCount = 0;
        final Set<String> alreadyStored = new HashSet<> ();
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                this.notifyProgress ();
                outputCount++;
                this.notifyNewline (outputCount);

                this.storeSamplefile (alreadyStored, zipOutputStream, multisampleSource.getMetadata (), zone, relativeFolderName);
            }
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        return this.writeSamples (sampleFolder, multisampleSource, DESTINATION_FORMAT);
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @param destinationFormat The destination audio format
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final DestinationAudioFormat destinationFormat) throws IOException
    {
        return this.writeSamples (sampleFolder, multisampleSource, ".wav", destinationFormat, false);
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @param destinationFormat The destination audio format
     * @param trim Trim the sample from zone start to end if enabled
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        return this.writeSamples (sampleFolder, multisampleSource, ".wav", destinationFormat, trim);
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @param fileEnding The suffix to use for the file
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final String fileEnding) throws IOException
    {
        return this.writeSamples (sampleFolder, multisampleSource, fileEnding, DESTINATION_FORMAT, false);
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @param fileEnding The suffix to use for the file
     * @param destinationFormat The destination audio format
     * @param trim Trim the sample from zone start to end if enabled
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final String fileEnding, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        final List<File> writtenFiles = new ArrayList<> ();

        int outputCount = 0;
        for (final IGroup group: multisampleSource.getGroups ())
        {
            final List<ISampleZone> sampleZones = group.getSampleZones ();
            for (int zoneIndex = 0; zoneIndex < sampleZones.size (); zoneIndex++)
            {
                if (this.isCancelled ())
                    return writtenFiles;

                final ISampleZone zone = sampleZones.get (zoneIndex);

                final File file = new File (sampleFolder, this.createSampleFilename (zone, zoneIndex, fileEnding));
                try (final FileOutputStream fos = new FileOutputStream (file))
                {
                    this.notifyProgress ();
                    outputCount++;
                    this.notifyNewline (outputCount);

                    if (this.settingsConfiguration.requiresRewrite (destinationFormat) || trim)
                        this.rewriteFile (multisampleSource.getMetadata (), zone, fos, destinationFormat, trim);
                    else
                    {
                        final ISampleData sampleData = zone.getSampleData ();
                        if (sampleData == null)
                        {
                            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), file.getName ());
                            this.notifier.logText ("\n");
                        }
                        else
                            sampleData.writeSample (fos);
                    }

                    writtenFiles.add (file);
                }
                catch (final NoSuchFileException | FileNotFoundException ex)
                {
                    this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
                }
            }
        }
        this.notifyNewline ();

        return writtenFiles;
    }


    /**
     * Writes the sample of the given zone and updates/adds their instrument and sample chunks.
     *
     * @param metadata The metadata to store in a BEXT chunk
     * @param zone The zone from which to take the data to store into the chunks
     * @param outputStream Where to write the result
     * @param destinationFormat The destination audio format
     * @param trim Trim the sample from zone start to end if enabled
     * @throws IOException Could not store the samples
     */
    private void rewriteFile (final IMetadata metadata, final ISampleZone zone, final OutputStream outputStream, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        final ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
            return;

        // Convert resolution
        final WaveFile wavFile = AudioFileUtils.convertToWav (sampleData, destinationFormat);

        // Trim sample from zone start to end
        if (trim)
            trimStartToEnd (wavFile, zone);

        // Update information chunks
        if (this.settingsConfiguration.isUpdateBroadcastAudioChunk ())
            updateBroadcastAudioChunk (metadata, wavFile);
        final int unityNote = Math.clamp (zone.getKeyRoot (), 0, 127);
        if (this.settingsConfiguration.isUpdateInstrumentChunk ())
            updateInstrumentChunk (zone, wavFile, unityNote);
        if (this.settingsConfiguration.isUpdateSampleChunk ())
            updateSampleChunk (zone, wavFile, unityNote);
        if (this.settingsConfiguration.isRemoveJunkChunks ())
            wavFile.removeChunks (RiffID.JUNK_ID, RiffID.JUNK2_ID, RiffID.FILLER_ID, RiffID.MD5_ID);

        wavFile.write (outputStream);
    }


    /**
     * Trims the data of the wave file to the part from the zone start and zone end. The zone is
     * updated accordingly.
     *
     * @param wavFile The WAV file to trim
     * @param zone The zone
     */
    private static void trimStartToEnd (final WaveFile wavFile, final ISampleZone zone)
    {
        final FormatChunk formatChunk = wavFile.getFormatChunk ();

        // Create the truncated data array
        final DataChunk dataChunk = wavFile.getDataChunk ();
        final byte [] data = dataChunk.getData ();
        final int start = zone.getStart ();
        final int stop = zone.getStop ();
        final int lengthInSamples = stop - start;
        final int numBytesPerSample = formatChunk.calculateBytesPerSample ();
        final int startByte = start * numBytesPerSample;
        final int newLength = lengthInSamples * numBytesPerSample;
        final byte [] truncatedData = new byte [newLength];
        System.arraycopy (data, startByte, truncatedData, 0, Math.min (newLength, data.length - startByte));

        // Replace the previous data chunk
        final DataChunk truncatedDataChunk = new DataChunk (formatChunk, lengthInSamples);
        truncatedDataChunk.setData (truncatedData);
        wavFile.setDataChunk (truncatedDataChunk);

        // Update the zone values - necessary for follow-up instrument/sample chunks!
        zone.setStart (0);
        zone.setStop (lengthInSamples);
        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop loop = loops.get (0);
            loop.setStart (Math.max (loop.getStart () - start, 0));
            loop.setEnd (Math.min (loop.getEnd () - start, lengthInSamples));
        }
    }


    private static void updateSampleChunk (final ISampleZone zone, final WaveFile wavFile, final int unityNote)
    {
        final List<ISampleLoop> loops = zone.getLoops ();
        final SampleChunk sampleChunk = new SampleChunk (loops.size ());
        sampleChunk.setSamplePeriod ((int) (1000000000.0 / wavFile.getFormatChunk ().getSampleRate ()));
        sampleChunk.setPitch (unityNote, (int) Math.round (zone.getTune () * 100.0));

        final List<SampleChunkLoop> chunkLoops = sampleChunk.getLoops ();
        for (int i = 0; i < loops.size (); i++)
        {
            final ISampleLoop sampleLoop = loops.get (i);
            final SampleChunkLoop sampleChunkLoop = chunkLoops.get (i);
            switch (sampleLoop.getType ())
            {
                default:
                case FORWARDS:
                    sampleChunkLoop.setType (SampleChunk.LOOP_FORWARD);
                    break;
                case ALTERNATING:
                    sampleChunkLoop.setType (SampleChunk.LOOP_ALTERNATING);
                    break;
                case BACKWARDS:
                    sampleChunkLoop.setType (SampleChunk.LOOP_BACKWARDS);
                    break;
            }
            sampleChunkLoop.setStart (sampleLoop.getStart ());
            sampleChunkLoop.setEnd (sampleLoop.getEnd ());
        }

        wavFile.setSampleChunk (sampleChunk);
    }


    private static void updateInstrumentChunk (final ISampleZone zone, final WaveFile wavFile, final int unityNote)
    {
        InstrumentChunk instrumentChunk = wavFile.getInstrumentChunk ();
        if (instrumentChunk == null)
        {
            instrumentChunk = new InstrumentChunk ();
            wavFile.setInstrumentChunk (instrumentChunk);
        }

        instrumentChunk.setUnshiftedNote (unityNote);
        instrumentChunk.setFineTune (Math.clamp ((int) (zone.getTune () * 100), -50, 50));
        instrumentChunk.setGain (Math.clamp ((int) zone.getGain (), -127, 127));
        instrumentChunk.setLowNote (Math.clamp (zone.getKeyLow (), 0, 127));
        instrumentChunk.setHighNote (Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127));
        instrumentChunk.setLowVelocity (Math.clamp (zone.getVelocityLow (), 0, 127));
        instrumentChunk.setHighVelocity (Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 0, 127));
    }


    private static void updateBroadcastAudioChunk (final IMetadata metadata, final WaveFile wavFile)
    {
        BroadcastAudioExtensionChunk broadcastAudioChunk = wavFile.getBroadcastAudioExtensionChunk ();
        if (broadcastAudioChunk == null)
        {
            broadcastAudioChunk = new BroadcastAudioExtensionChunk ();
            wavFile.setBroadcastAudioExtensionChunk (broadcastAudioChunk);
        }

        broadcastAudioChunk.setDescription (metadata.getDescription ());
        broadcastAudioChunk.setOriginator (metadata.getCreator ());
        Date creationDateTime = metadata.getCreationDateTime ();
        if (creationDateTime == null)
            creationDateTime = new Date ();
        broadcastAudioChunk.setOriginationDateTime (creationDateTime);
    }


    /**
     * Creates an optional string from the XML document.
     *
     * @param document The XML document
     * @return The optional string; empty if an error occurs
     */
    protected Optional<String> createXMLString (final Document document)
    {
        try
        {
            return Optional.of (XMLUtils.toString (document));
        }
        catch (final TransformerException ex)
        {
            this.notifier.logError (ex);
            return Optional.empty ();
        }
    }


    /**
     * Creates full path from the sample name and relative path and adding the prefix path.
     *
     * @param alreadyStored All paths already added to the ZIP file
     * @param zone The sample zone to check
     * @param path The prefix path
     * @return The full path or null if already added to the ZIP
     */
    private static String checkSampleName (final Set<String> alreadyStored, final ISampleZone zone, final String path)
    {
        String filename = zone.getName () + ".wav";
        if (path != null)
            filename = path + FORWARD_SLASH + filename;
        if (alreadyStored.contains (filename))
            return null;
        alreadyStored.add (filename);
        return filename;
    }


    /**
     * Add all samples from all groups in the given compressed ZIP output stream.
     *
     * @param zipOutputStream The ZIP output stream to which to add the samples
     * @param relativeFolderName The relative folder under which to store the file in the ZIP
     * @param multisampleSource The multi-sample
     * @throws IOException Could not store the samples
     */
    protected void zipSampleFiles (final ZipOutputStream zipOutputStream, final String relativeFolderName, final IMultisampleSource multisampleSource) throws IOException
    {
        int outputCount = 0;
        final Set<String> alreadyStored = new HashSet<> ();
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                this.notifyProgress ();
                outputCount++;
                this.notifyNewline (outputCount);

                this.zipSamplefile (alreadyStored, zipOutputStream, zone, multisampleSource.getMetadata ().getCreationDateTime (), relativeFolderName);
            }
        this.notifyNewline ();
    }


    /**
     * Adds a sample file to the compressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zipOutputStream The ZIP output stream
     * @param zone The zone to add
     * @param dateTime The date and time to set as the creation date of the file entry
     * @throws IOException Could not read the file
     */
    protected void zipSamplefile (final Set<String> alreadyStored, final ZipOutputStream zipOutputStream, final ISampleZone zone, final Date dateTime) throws IOException
    {
        this.zipSamplefile (alreadyStored, zipOutputStream, zone, dateTime, null);
    }


    /**
     * Adds a sample file to the compressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zipOutputStream The ZIP output stream
     * @param zone The zone to add
     * @param dateTime The date and time to set as the creation date of the file entry
     * @param path Optional path (may be null), must not end with a slash
     * @throws IOException Could not read the file
     */
    protected void zipSamplefile (final Set<String> alreadyStored, final ZipOutputStream zipOutputStream, final ISampleZone zone, final Date dateTime, final String path) throws IOException
    {
        final String name = checkSampleName (alreadyStored, zone, path);
        if (name == null)
            return;

        final ZipEntry entry = new ZipEntry (name);
        if (dateTime != null)
        {
            final long millis = dateTime.getTime ();
            entry.setCreationTime (FileTime.fromMillis (millis));
            entry.setTime (millis);
        }

        zipOutputStream.putNextEntry (entry);
        final ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), name);
            this.notifier.logText ("\n");
        }
        else
            sampleData.writeSample (zipOutputStream);
        zipOutputStream.closeEntry ();
    }
}
