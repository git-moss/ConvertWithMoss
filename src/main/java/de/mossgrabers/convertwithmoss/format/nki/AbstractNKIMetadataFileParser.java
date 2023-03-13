// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.Utils;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultVelocityLayer;
import de.mossgrabers.convertwithmoss.exception.ValueNotAvailableException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.nki.tag.AbstractTagsAndAttributes;
import de.mossgrabers.convertwithmoss.format.wav.WavSampleMetadata;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Base class for parsing the NKI XML structure.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 * @author Philip Stolz
 */
public abstract class AbstractNKIMetadataFileParser
{
    private static final String               NULL_ENTRY = "(null)";

    protected final AbstractTagsAndAttributes tags;

    private final INotifier                   notifier;


    /**
     * Constructor.
     *
     * @param tags the format specific tags
     * @param notifier Where to report errors
     */
    protected AbstractNKIMetadataFileParser (final AbstractTagsAndAttributes tags, final INotifier notifier)
    {
        this.tags = tags;
        this.notifier = notifier;
    }


    /**
     * Parses the metadata description file.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param content The XML content to parse
     * @param metadata Default metadata
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return The parsed multisample source
     * @throws IOException An error occurred parsing the XML document
     */
    public List<IMultisampleSource> parse (final File sourceFolder, final File sourceFile, final String content, final IMetadataConfig metadata, final Map<String, WavSampleMetadata> monolithSamples) throws IOException
    {
        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            final Element top = document.getDocumentElement ();

            if (!this.isValidTopLevelElement (top))
                return Collections.emptyList ();

            final Element [] programElements = this.findProgramElements (top);
            if (programElements.length == 0)
                return Collections.emptyList ();

            final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
            for (final Element programElement: programElements)
            {
                final String n = metadata.isPreferFolderName () ? sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
                final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFolder, n);
                final MultisampleSource multisampleSource = new MultisampleSource (sourceFile, parts, null, AudioFileUtils.subtractPaths (sourceFolder, sourceFile));
                if (this.parseProgram (programElement, multisampleSource, monolithSamples))
                {
                    // Use some guessing on the filename if no metadata is available...

                    final String creator = multisampleSource.getCreator ();
                    if (creator == null || creator.isBlank ())
                        multisampleSource.setCreator (TagDetector.detect (parts, metadata.getCreatorTags (), metadata.getCreatorName ()));

                    final String category = multisampleSource.getCategory ();
                    if (category == null || category.isBlank ())
                        multisampleSource.setCategory (TagDetector.detectCategory (parts));

                    final String [] keywords = multisampleSource.getKeywords ();
                    if (keywords == null || keywords.length == 0)
                        multisampleSource.setKeywords (TagDetector.detectKeywords (parts));

                    multisampleSources.add (multisampleSource);
                }
            }
            return multisampleSources;
        }
        catch (final SAXException ex)
        {
            throw new IOException (ex);
        }
    }


    /**
     * Checks whether a given element is a valid top-level element.
     *
     * @param top The top-level element.
     * @return True if top is a valid top-level element, false else
     */
    protected abstract boolean isValidTopLevelElement (final Element top);


    /**
     * Returns the program elements in the metadata file.
     *
     * @param top The top element of the document
     * @return An array of program elements, an empty array if nothing was found
     */
    protected abstract Element [] findProgramElements (final Element top);


    /**
     * Parses a program element and retrieves a IMultisampleSource object representing the program.
     *
     * @param programElement The program element to be parsed
     * @param multisampleSource Where to store the parsed data
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return True if successful
     * @throws IOException Could not create path for samples
     */
    private boolean parseProgram (final Element programElement, final MultisampleSource multisampleSource, final Map<String, WavSampleMetadata> monolithSamples) throws IOException
    {
        final String programName = this.tags.programName ();
        if (!programElement.hasAttribute (programName))
            return false;

        final Map<String, String> programParameters = this.readParameters (programElement);

        final String name = programElement.getAttribute (programName);
        multisampleSource.setName (name);
        final String author = programParameters.get ("instrumentAuthor");
        if (author != null)
            multisampleSource.setCreator (author);
        final String description = programParameters.get ("instrumentCredits");
        if (description != null && !NULL_ENTRY.equals (description))
            multisampleSource.setDescription (description);

        final Element [] groupElements = this.getGroupElements (programElement);
        final Element [] zoneElements = this.getZoneElements (programElement);

        final String sourcePath = multisampleSource.getSourceFile ().getParentFile ().getCanonicalPath ();
        final List<IVelocityLayer> velocityLayers = this.getVelocityLayers (programParameters, groupElements, zoneElements, sourcePath, monolithSamples);
        if (velocityLayers.isEmpty ())
        {
            this.notifier.logError ("IDS_NKI_NO_VEL_LAYER_DETECTED");
            return false;
        }

        multisampleSource.setVelocityLayers (velocityLayers);
        return true;
    }


    /**
     * Reads the parameters of a program under an XML element.
     *
     * @param element The XML element
     * @return A map with the parameters and their values, an empty map if no parameters are found
     */
    private Map<String, String> readParameters (final Element element)
    {
        final Element parametersElement = XMLUtils.getChildElementByName (element, this.tags.parameters ());
        return this.readValueMap (parametersElement);
    }


    /**
     * Reads a value map from a given XML element.
     *
     * @param element The XML element
     * @return The value map. If nothing can be read, an empty map is returned.
     */
    protected Map<String, String> readValueMap (final Element element)
    {
        if (element == null)
            return Collections.emptyMap ();

        final Element [] valueElements = XMLUtils.getChildElementsByName (element, this.tags.value (), false);
        if (valueElements == null)
            return Collections.emptyMap ();

        final HashMap<String, String> result = new HashMap<> ();
        for (final Element valueElement: valueElements)
        {
            final String valueName = valueElement.getAttribute (this.tags.valueNameAttribute ());
            final String valueValue = valueElement.getAttribute (this.tags.valueValueAttribute ());
            if (valueName != null && valueValue != null)
                result.put (valueName, valueValue);
        }
        return result;
    }


    /**
     * Retrieves a program's group elements as an array.
     *
     * @param programElement The XML program element
     * @return The array of group elements
     */
    private Element [] getGroupElements (final Element programElement)
    {
        final Element groupsElement = XMLUtils.getChildElementByName (programElement, this.tags.groups ());
        return groupsElement != null ? XMLUtils.getChildElementsByName (groupsElement, this.tags.group (), false) : null;
    }


    /**
     * Retrieves a program's zone elements as an array.
     *
     * @param programElement The XML program element
     * @return The array of zone elements
     */
    private Element [] getZoneElements (final Element programElement)
    {
        final Element zoneElement = XMLUtils.getChildElementByName (programElement, this.tags.zones ());
        return zoneElement != null ? XMLUtils.getChildElementsByName (zoneElement, this.tags.zone (), false) : null;
    }


    /**
     * Creates velocity layers from a program's parameters and its group and zone elements.
     *
     * @param programParameters The program parameters
     * @param groupElements The group elements
     * @param zoneElements The zone elements
     * @param sourcePath The canonical path which contains the NKI file
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return the velocity layers created (empty list is returned if nothing was created)
     */
    private List<IVelocityLayer> getVelocityLayers (final Map<String, String> programParameters, final Element [] groupElements, final Element [] zoneElements, final String sourcePath, final Map<String, WavSampleMetadata> monolithSamples)
    {
        if (groupElements == null || zoneElements == null)
            return Collections.emptyList ();

        final LinkedList<IVelocityLayer> velocityLayers = new LinkedList<> ();
        for (final Element groupElement: groupElements)
        {
            final IVelocityLayer velocityLayer = this.getVelocityLayer (programParameters, groupElement, zoneElements, sourcePath, monolithSamples);
            if (velocityLayer != null)
                velocityLayers.add (velocityLayer);
        }
        return velocityLayers;
    }


    /**
     * Creates one velocity layer from a program's parameters, a given group element and the
     * program's zone elements.
     *
     * @param programParameters The program parameters
     * @param groupElement The group element from which the velocity layer is created
     * @param zoneElements The program's zone elements
     * @param sourcePath The canonical path which contains the NKI file
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return The velocity layer created from the zone element (null, if there is no group element)
     */
    private IVelocityLayer getVelocityLayer (final Map<String, String> programParameters, final Element groupElement, final Element [] zoneElements, final String sourcePath, final Map<String, WavSampleMetadata> monolithSamples)
    {
        if (groupElement == null)
            return null;

        final DefaultVelocityLayer velocityLayer = new DefaultVelocityLayer ();
        velocityLayer.setName (this.getGroupName (groupElement));
        final Map<String, String> groupParameters = this.readParameters (groupElement);
        final IEnvelope groupAmpEnv = this.readGroupAmpEnv (groupElement);
        final int pitchBend = this.readGroupPitchBend (groupElement);
        velocityLayer.setTrigger (this.getTriggerTypeFromGroupElement (groupParameters));
        final Element [] groupZones = this.findGroupZones (groupElement, zoneElements);
        velocityLayer.setSampleMetadata (this.getSampleMetadataFromZones (programParameters, groupParameters, groupAmpEnv, groupElement, groupZones, pitchBend, sourcePath, monolithSamples));
        return velocityLayer;
    }


    /**
     * Creates sample metadata from zones.
     *
     * @param programParameters The program's parameters
     * @param groupParameters The group's parameters
     * @param groupAmpEnv The group's amp envelope
     * @param groupElement The group element from which the metadata is created
     * @param groupZones The zone elements belonging to the group
     * @param pitchBend The pitchbend range (half tone steps)
     * @param sourcePath The canonical path which contains the NKI file
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return A list of sample metadata object. If nothing can be created, an empty list is
     *         returned.
     */
    private List<ISampleMetadata> getSampleMetadataFromZones (final Map<String, String> programParameters, final Map<String, String> groupParameters, final IEnvelope groupAmpEnv, final Element groupElement, final Element [] groupZones, final int pitchBend, final String sourcePath, final Map<String, WavSampleMetadata> monolithSamples)
    {
        if (groupElement == null || groupZones == null)
            return Collections.emptyList ();

        final LinkedList<ISampleMetadata> sampleMetadataList = new LinkedList<> ();

        for (final Element zoneElement: groupZones)
        {
            final File sampleFile = this.getZoneSampleFile (zoneElement, sourcePath, monolithSamples != null);
            if (sampleFile != null)
            {
                DefaultSampleMetadata sampleMetadata = null;
                if (monolithSamples != null)
                    sampleMetadata = monolithSamples.get (sampleFile.getName ());
                else
                {
                    // Check the type of the source sample for compatibility and handle them
                    // accordingly
                    try
                    {
                        final AudioFileFormat.Type type = AudioSystem.getAudioFileFormat (sampleFile).getType ();
                        if (AudioFileFormat.Type.WAVE.equals (type))
                        {
                            if (AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                                sampleMetadata = new DefaultSampleMetadata (sampleFile);
                        }
                        else if (AudioFileFormat.Type.AIFF.equals (type))
                        {
                            this.notifier.log ("IDS_NOTIFY_CONVERT_TO_WAV", sampleFile.getName ());
                            sampleMetadata = new AiffSampleMetadata (sampleFile);
                        }
                        else
                            this.notifier.logError (Functions.getMessage ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", type.toString ()));
                    }
                    catch (final UnsupportedAudioFileException | IOException ex)
                    {
                        this.notifier.logError (Functions.getMessage ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", ex));
                    }
                }
                if (sampleMetadata != null)
                    this.readMetadata (programParameters, groupParameters, groupAmpEnv, pitchBend, sampleMetadataList, zoneElement, sampleMetadata);
            }
        }

        return sampleMetadataList;
    }


    private void readMetadata (final Map<String, String> programParameters, final Map<String, String> groupParameters, final IEnvelope groupAmpEnv, final int pitchBend, final LinkedList<ISampleMetadata> sampleMetadataList, final Element zoneElement, final DefaultSampleMetadata sampleMetadata)
    {
        try
        {
            sampleMetadata.addMissingInfoFromWaveFile (true, true);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex);
            return;
        }

        final Map<String, String> zoneParameters = this.readParameters (zoneElement);
        try
        {
            sampleMetadata.setKeyRoot (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.rootKeyParam ()));
            sampleMetadata.setKeyLow (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.lowKeyParam ()));
            sampleMetadata.setKeyHigh (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.highKeyParam ()));
            sampleMetadata.setVelocityLow (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.lowVelocityParam ()));
            sampleMetadata.setVelocityHigh (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.highVelocityParam ()));
            sampleMetadata.setNoteCrossfadeLow (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.fadeLowParam ()));
            sampleMetadata.setNoteCrossfadeHigh (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.fadeHighParam ()));
            sampleMetadata.setVelocityCrossfadeLow (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.fadeLowVelParam ()));
            sampleMetadata.setVelocityCrossfadeLow (AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.fadeHighVelParam ()));

            final int sampleStart = AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.sampleStartParam ());
            final int sampleEnd = AbstractNKIMetadataFileParser.getInt (zoneParameters, this.tags.sampleEndParam ());

            if (sampleEnd > sampleStart)
            {
                sampleMetadata.setStart (sampleStart);
                sampleMetadata.setStop (sampleEnd);
            }

            final String keyTracking = AbstractNKIMetadataFileParser.getString (groupParameters, this.tags.keyTrackingParam ());
            final double keyTrackingValue = keyTracking.equals (this.tags.yes ()) ? 1.0d : 0.0d;
            sampleMetadata.setKeyTracking (keyTrackingValue);

            final double zoneVol = AbstractNKIMetadataFileParser.getDouble (zoneParameters, this.tags.zoneVolParam ());
            final double groupVol = AbstractNKIMetadataFileParser.getDouble (groupParameters, this.tags.groupVolParam ());
            final double progVol = AbstractNKIMetadataFileParser.getDouble (programParameters, this.tags.progVolParam ());
            sampleMetadata.setGain (20.0d * Math.log10 (zoneVol * groupVol * progVol));

            final double zoneTune = AbstractNKIMetadataFileParser.getDouble (zoneParameters, this.tags.zoneTuneParam ());
            final double groupTune = AbstractNKIMetadataFileParser.getDouble (groupParameters, this.tags.groupTuneParam ());
            final double progTune = AbstractNKIMetadataFileParser.getDouble (programParameters, this.tags.progTuneParam ());
            sampleMetadata.setTune (this.tags.calculateTune (zoneTune, groupTune, progTune));

            final double zonePan = AbstractNKIMetadataFileParser.getDouble (zoneParameters, this.tags.zonePanParam ());
            final double groupPan = AbstractNKIMetadataFileParser.getDouble (groupParameters, this.tags.groupPanParam ());
            final double progPan = AbstractNKIMetadataFileParser.getDouble (programParameters, this.tags.progPanParam ());
            final double totalPan = this.normalizePanning (zonePan) + this.normalizePanning (groupPan) + this.normalizePanning (progPan);
            sampleMetadata.setPanorama (Utils.clamp (totalPan, -1.0d, 1.0d));
        }
        catch (final ValueNotAvailableException e)
        {
            this.notifier.logError ("IDS_NKI_ERROR_MISSING_VALUE", e);
            return;
        }

        if (groupAmpEnv != null)
        {
            final IEnvelope amplitudeEnvelope = sampleMetadata.getAmplitudeEnvelope ();
            amplitudeEnvelope.setAttack (groupAmpEnv.getAttack ());
            amplitudeEnvelope.setHold (groupAmpEnv.getHold ());
            amplitudeEnvelope.setDecay (groupAmpEnv.getDecay ());
            amplitudeEnvelope.setSustain (groupAmpEnv.getSustain ());
            amplitudeEnvelope.setRelease (groupAmpEnv.getRelease ());
        }

        if (groupParameters.containsKey (this.tags.reverseParam ()))
        {
            final String reversed = groupParameters.get (this.tags.reverseParam ());
            sampleMetadata.setReversed (reversed.equals (this.tags.yes ()));
        }

        this.readLoopInformation (zoneElement, sampleMetadata);

        sampleMetadataList.add (sampleMetadata);

        if (pitchBend >= 0)
        {
            sampleMetadata.setBendUp (pitchBend);
            sampleMetadata.setBendDown (pitchBend);
        }
    }


    /**
     * Normalizes a panning value to a range from -1 to 1 where 0 is center and -1 is left.
     *
     * @param panningValue The panning value to normalize
     * @return The normalized panning value
     */
    protected abstract double normalizePanning (final double panningValue);


    /**
     * Reads the loop information from a zone element and writes it to a ISampleMetadata object.
     *
     * @param zoneElement The zone element
     * @param sampleMetadata The ISampleMetadata object
     */
    private void readLoopInformation (final Element zoneElement, final DefaultSampleMetadata sampleMetadata)
    {
        final Element loopsElement = XMLUtils.getChildElementByName (zoneElement, this.tags.loopsElement ());
        if (loopsElement == null)
            return;

        final Element [] loopElements = XMLUtils.getChildElementsByName (loopsElement, this.tags.loopElement (), false);
        if (loopElements == null)
            return;

        for (final Element loopElement: loopElements)
        {
            final Map<String, String> loopParams = this.readValueMap (loopElement);

            int loopStart;
            int loopLength;
            String loopMode;
            int xFadeLength;
            String alternatingLoop;
            try
            {
                loopStart = AbstractNKIMetadataFileParser.getInt (loopParams, this.tags.loopStartParam ());
                loopLength = AbstractNKIMetadataFileParser.getInt (loopParams, this.tags.loopLengthParam ());
                loopMode = AbstractNKIMetadataFileParser.getString (loopParams, this.tags.loopModeParam ());
                xFadeLength = AbstractNKIMetadataFileParser.getInt (loopParams, this.tags.xfadeLengthParam ());
                alternatingLoop = AbstractNKIMetadataFileParser.getString (loopParams, this.tags.alternatingLoopParam ());
            }
            catch (final ValueNotAvailableException e)
            {
                return;
            }

            // If it a one shot then there is no loop
            if (loopMode.equals (this.tags.oneshotValue ()))
                continue;

            LoopType loopType = LoopType.FORWARD;
            if ((loopMode.equals (this.tags.untilEndValue ()) || loopMode.equals (this.tags.untilReleaseValue ())) && alternatingLoop.equals (this.tags.yes ()))
                loopType = LoopType.ALTERNATING;

            final DefaultSampleLoop sampleLoop = new DefaultSampleLoop ();
            sampleLoop.setStart (loopStart);
            sampleLoop.setEnd (loopLength + loopStart);
            if (xFadeLength > 0)
            {
                final double xFadeFactor = (double) loopLength / (double) xFadeLength;
                sampleLoop.setCrossfade (xFadeFactor > 1.0d ? 1.0d : xFadeFactor);
            }
            sampleLoop.setType (loopType);
            sampleMetadata.addLoop (sampleLoop);
        }
    }


    /**
     * Retrieves a sample file from a zone element.
     *
     * @param zoneElement The zone element
     * @param sourcePath The canonical path which contains the NKI file
     * @param isMonolith True if samples are contained in the NKI as well
     * @return The sample file. Null is returned if file cannot be retrieved successfully.
     */
    private File getZoneSampleFile (final Element zoneElement, final String sourcePath, final boolean isMonolith)
    {
        final Element sampleElement = XMLUtils.getChildElementByName (zoneElement, this.tags.zoneSample ());
        if (sampleElement == null)
            return null;

        final Map<String, String> sampleParameters = this.readValueMap (sampleElement);
        final String encodedSampleFileName = sampleParameters.get (this.tags.sampleFileAttribute ());
        if (encodedSampleFileName == null)
        {
            this.notifier.logError ("IDS_NKI_SAMPLE_FILE_ATTRIBUTE_MISSING");
            return null;
        }

        return this.getFileFromEncodedSampleFileName (encodedSampleFileName, sourcePath, isMonolith);
    }


    /**
     * Decodes an encoded sample file name and returns the respective File if it can be found.
     *
     * @param encodedSampleFileName The encoded sample file name
     * @param sourcePath The canonical path which contains the NKI file
     * @param isMonolith True if samples are contained in the NKI as well
     * @return The File if it can be found, null else
     */
    protected File getFileFromEncodedSampleFileName (final String encodedSampleFileName, final String sourcePath, final boolean isMonolith)
    {
        final StringBuilder path = new StringBuilder ();
        try
        {
            final String relativePath = this.decodeEncodedSampleFileName (encodedSampleFileName);
            if (isMonolith)
                return new File (relativePath);
            path.append (sourcePath).append ('/').append (relativePath);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (sourcePath, ex);
            return null;
        }

        final File sampleFile = new File (path.toString ());
        if (sampleFile.exists () && sampleFile.canRead ())
            return sampleFile;

        this.notifier.logError ("IDS_NKI_SAMPLE_FILE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
        return null;
    }


    /**
     * Decodes an encoded sample file name as used in the NKI's respective sample file attribute.
     *
     * @param encodedSampleFileName The encoded sample file name
     * @return The decoded path
     * @throws IOException Could not decode the file name/path
     */
    protected abstract String decodeEncodedSampleFileName (final String encodedSampleFileName) throws IOException;


    /**
     * Retrieves a TriggerType from a group's parameters.
     *
     * @param groupParameters The group element to retrieve the TriggerType from
     * @return The TriggerType that could be retrieved. In doubt, TriggerType.ATTACK is returned.
     */
    protected abstract TriggerType getTriggerTypeFromGroupElement (final Map<String, String> groupParameters);


    /**
     * Retrieves a group element's name.
     *
     * @param groupElement The group element
     * @return The name as a String. If no name was found, "" is returned.
     */
    private String getGroupName (final Element groupElement)
    {
        final String groupName = groupElement.getAttribute (this.tags.groupNameAttribute ());
        return groupName == null ? "" : groupName;
    }


    /**
     * Reads an amplitude envelope from a group element.
     *
     * @param groupElement The group element
     * @return The amp envelope, if one could be found, otherwise null
     */
    private IEnvelope readGroupAmpEnv (final Element groupElement)
    {
        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.intModulatorsElement ());
        if (modulatorsElement == null)
            return null;

        final Element [] modulators = XMLUtils.getChildElementsByName (modulatorsElement, this.tags.intModulatorElement (), false);
        if (modulators == null)
            return null;

        for (final Element modulator: modulators)
        {
            final IEnvelope env = this.getAmpEnvFromModulator (modulator);
            if (env != null)
                return env;
        }
        return null;
    }


    /**
     * Reads an amplitude envelope from a modulator Element.
     *
     * @param modulator The modulator Element
     * @return The amp envelop. If none could be found, null is returned.
     */
    private IEnvelope getAmpEnvFromModulator (final Element modulator)
    {
        final Element envElement = XMLUtils.getChildElementByName (modulator, this.tags.envelopeElement ());
        if (envElement == null || this.hasNameValuePairs (modulator, this.tags.bypassParam (), this.tags.yes ()))
            return null;

        if (this.hasTarget (modulator, this.tags.targetVolValue ()))
            return this.readEnvelopeFromEnvelopeElement (envElement);
        return null;
    }


    /**
     * Reads an IEnvelope from an envelope XML element.
     *
     * @param envElement The envelope XML element.
     * @return The IEnvelope, if envelope could be read successfully, null else.
     */
    private IEnvelope readEnvelopeFromEnvelopeElement (final Element envElement)
    {
        final String envType = envElement.getAttribute (this.tags.envTypeAttribute ());
        if (envType == null)
            return null;

        try
        {
            final DefaultEnvelope env = new DefaultEnvelope ();

            final Map<String, String> envParams = this.readValueMap (envElement);
            final double attackTimeMs = getDouble (envParams, this.tags.attackParam ());
            env.setAttack (attackTimeMs / 1000.0d);

            final double holdTimeMs = AbstractNKIMetadataFileParser.getDouble (envParams, this.tags.holdParam ());
            env.setHold (holdTimeMs / 1000.0d);

            final double decayTimeMs = AbstractNKIMetadataFileParser.getDouble (envParams, this.tags.decayParam ());
            env.setDecay (decayTimeMs / 1000.0d);

            if (!envType.equals (this.tags.ahdEnvTypeValue ()))
            {
                final double sustainLinear = AbstractNKIMetadataFileParser.getDouble (envParams, this.tags.sustainParam ());
                env.setSustain (sustainLinear);

                final double releaseTimeMs = AbstractNKIMetadataFileParser.getDouble (envParams, this.tags.releaseParam ());
                env.setRelease (releaseTimeMs / 1000.0d);
            }
            return env;
        }
        catch (final ValueNotAvailableException e)
        {
            return null;
        }
    }


    /**
     * Returns a String value from a value map.
     *
     * @param valueMap The value
     * @param valueName The value's name
     * @return The String value
     * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
     */
    private static String getString (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException (valueName);
        return valueStr;
    }


    /**
     * Returns an integer value from a value map.
     *
     * @param valueMap The value
     * @param valueName The value's name
     * @return The integer value
     * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
     */
    private static int getInt (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException (valueName);
        return Integer.parseInt (valueStr);
    }


    /**
     * Returns a double value from a value map.
     *
     * @param valueMap The value
     * @param valueName The value's name
     * @return The double value
     * @throws ValueNotAvailableException Indicates that the valueName is not in the valueMap
     */
    private static double getDouble (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException (valueName);
        return Double.parseDouble (valueStr);
    }


    /**
     * Retrieves whether a given modulator element has a specific target.
     *
     * @param modulator The modulator
     * @param expectedTargetValue The target value that is expected
     * @return True if the modulator has the expected target value, false else
     */
    protected abstract boolean hasTarget (Element modulator, String expectedTargetValue);


    /**
     * Checks if an element has a value map containing a required set of (name, value) pairs.
     *
     * @param element The element
     * @param nameValuePairs A number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return True if the element has all name value pairs in its value map, false else.
     */
    protected boolean hasNameValuePairs (final Element element, final String... nameValuePairs)
    {
        if (nameValuePairs == null)
            return true;

        final Map<String, String> parameters = this.readValueMap (element);
        for (int idx = 0; idx < nameValuePairs.length; idx += 2)
        {
            final String name = nameValuePairs[idx];
            if (parameters.containsKey (name))
            {
                final int valueIdx = idx + 1;
                if (valueIdx < nameValuePairs.length)
                {
                    final String value = nameValuePairs[valueIdx];
                    if (!parameters.get (name).equals (value))
                        return false;
                }
            }
            else
                return false;
        }
        return true;
    }


    /**
     * Finds a child element of a given parent element that has a value map containing a required
     * set of (name, value) pairs.
     *
     * @param parentElement The parent element
     * @param elementNameToBeFound The name of the element to be found
     * @param nameValuePairs A number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return The element with the value name pairs. If none could be found, null is returned.
     */
    protected Element findElementWithParameters (final Element parentElement, final String elementNameToBeFound, final String... nameValuePairs)
    {
        final Element [] elementsOfInterest = XMLUtils.getChildElementsByName (parentElement, elementNameToBeFound, false);
        if (elementsOfInterest == null)
            return null;

        for (final Element elementOfInterest: elementsOfInterest)
        {
            final boolean doesMatch = this.hasNameValuePairs (elementOfInterest, nameValuePairs);
            if (doesMatch)
                return elementOfInterest;
        }
        return null;
    }


    /**
     * Reads the pitch bend configuration from a group element.
     *
     * @param groupElement The group element
     * @return The number of semitones (up=down)
     */
    private int readGroupPitchBend (final Element groupElement)
    {
        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.extModulatorsElement ());
        if (modulatorsElement == null)
            return -1;

        final Element [] modulators = XMLUtils.getChildElementsByName (modulatorsElement, this.tags.extModulatorElement (), false);
        if (modulators == null)
            return -1;

        for (final Element modulator: modulators)
        {
            final int pitchBend = this.getPitchBendFromModulator (modulator);
            if (pitchBend >= 0)
                return pitchBend;
        }
        return -1;
    }


    /**
     * Reads a pitch bend value from a modulator Element.
     *
     * @param modulator The modulator Element
     * @return The pitch bend value. If none could be found, a -1 is returned.
     */
    private int getPitchBendFromModulator (final Element modulator)
    {
        int pitchBend = -1;

        final Map<String, String> modulatorParams = this.readValueMap (modulator);
        if (modulatorParams == null || !modulatorParams.containsKey (this.tags.sourceParam ()))
            return pitchBend;

        final String source = modulatorParams.get (this.tags.sourceParam ());

        if (!source.equals (this.tags.pitchBendValue ()))
            return pitchBend;

        final String intensity = this.readPitchBendIntensity (modulator);

        if (intensity == null)
            return pitchBend;

        final double pitchOctaves = Double.parseDouble (intensity);
        final double pitchCents = pitchOctaves * 1200;
        pitchBend = (int) Math.round (pitchCents);
        if (pitchBend < 0)
            pitchBend = -pitchBend;

        if (pitchBend > 9600)
            pitchBend = 9600;

        return pitchBend;
    }


    /**
     * Finds a group's zone elements.
     *
     * @param groupElement The group element
     * @param zoneElements The zone elements from which to find the zones belonging to the group
     * @return An array of zone elements belonging to the group element
     */
    private Element [] findGroupZones (final Element groupElement, final Element [] zoneElements)
    {
        final int index = XMLUtils.getIntegerAttribute (groupElement, this.tags.indexAttribute (), -1);
        if (index == -1 || zoneElements == null)
            return new Element [0];

        final List<Element> matchingZoneElements = new ArrayList<> ();
        for (final Element zoneElement: zoneElements)
        {
            final int groupIndex = XMLUtils.getIntegerAttribute (zoneElement, this.tags.groupIndexAttribute (), -1);
            if (groupIndex == index)
                matchingZoneElements.add (zoneElement);
        }
        return matchingZoneElements.toArray (new Element [matchingZoneElements.size ()]);
    }


    /**
     * Reads the pitch bend intensity value from a modulator element.
     *
     * @param modulator The modulator element
     * @return The pitch bend intensity value or null, if intensity couldn't be read.
     */
    protected abstract String readPitchBendIntensity (final Element modulator);
}
