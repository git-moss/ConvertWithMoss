// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;


/**
 * Creator for Yamaha YSFC files.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcCreator extends AbstractCreator
{
    private static final String                 YSFC_OUTPUT_FORMAT_LIBRARY    = "YsfcOutputFormatPreset";
    private static final String                 YSFC_COMBINE_INTO_ONE_LIBRARY = "YsfcCombineIntoOneLibrary";
    private static final String                 YSFC_COMBINE_FILENAME         = "YsfcCombineFilename";

    private static final DestinationAudioFormat DESTINATION_AUDIO_FORMAT      = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);


    private enum OutputFormat
    {
        MONTAGE_USER,
        MONTAGE_LIBRARY,
        MODX_USER,
        MODX_LIBRARY
    }


    private static final Map<OutputFormat, String> ENDING_MAP  = new EnumMap<> (OutputFormat.class);
    private static final Map<OutputFormat, String> VERSION_MAP = new EnumMap<> (OutputFormat.class);

    static
    {
        ENDING_MAP.put (OutputFormat.MONTAGE_USER, ".X7U");
        ENDING_MAP.put (OutputFormat.MONTAGE_LIBRARY, ".X7L");
        ENDING_MAP.put (OutputFormat.MODX_USER, ".X8U");
        ENDING_MAP.put (OutputFormat.MODX_LIBRARY, ".X8L");

        VERSION_MAP.put (OutputFormat.MONTAGE_USER, "4.0.5");
        VERSION_MAP.put (OutputFormat.MONTAGE_LIBRARY, "4.0.5");
        VERSION_MAP.put (OutputFormat.MODX_USER, "5.0.1");
        VERSION_MAP.put (OutputFormat.MODX_LIBRARY, "5.0.1");
    }

    private ToggleGroup outputFormatGroup;
    private CheckBox    combineIntoOneLibrary;
    private TextField   combinationFilename;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public YamahaYsfcCreator (final INotifier notifier)
    {
        super ("Yamaha YSFC", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_YSFC_LIBRARY_FORMAT");
        this.outputFormatGroup = new ToggleGroup ();
        for (int i = 0; i < 4; i++)
        {
            final RadioButton order = panel.createRadioButton ("@IDS_YSFC_OUTPUT_FORMAT_OPTION" + i);
            order.setAccessibleHelp (Functions.getMessage ("IDS_YSFC_LIBRARY_FORMAT"));
            order.setToggleGroup (this.outputFormatGroup);
        }

        panel.createSeparator ("@IDS_YSFC_SOURCE_COMBINATION");
        this.combineIntoOneLibrary = panel.createCheckBox ("@IDS_YSFC_MULTI_SAMPLE_COMBINATION");
        this.combinationFilename = panel.createField ("@IDS_YSFC_COMBINATION_FILENAME");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputFormatGroup.selectToggle (this.outputFormatGroup.getToggles ().get (config.getInteger (YSFC_OUTPUT_FORMAT_LIBRARY, 1)));
        this.combineIntoOneLibrary.setSelected (config.getBoolean (YSFC_COMBINE_INTO_ONE_LIBRARY, false));
        this.combinationFilename.setText (config.getProperty (YSFC_COMBINE_FILENAME, ""));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (YSFC_OUTPUT_FORMAT_LIBRARY, this.getSelectedOutputFormat ().ordinal ());
        config.setBoolean (YSFC_COMBINE_INTO_ONE_LIBRARY, this.combineIntoOneLibrary.isSelected ());
        config.setProperty (YSFC_COMBINE_FILENAME, this.combinationFilename.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean wantsMultipleFiles ()
    {
        return this.combineIntoOneLibrary.isSelected ();
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final List<IMultisampleSource> multisampleSources) throws IOException
    {
        if (multisampleSources.isEmpty ())
            return;

        final String combinationName = this.combinationFilename.getText ();
        final String name = multisampleSources.size () > 1 && combinationName.length () > 0 ? combinationName : multisampleSources.get (0).getName ();
        final String multiSampleName = createSafeFilename (name);

        final OutputFormat selectedOutputFormat = this.getSelectedOutputFormat ();
        final File multiFile = this.createUniqueFilename (destinationFolder, multiSampleName, ENDING_MAP.get (selectedOutputFormat));
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        storeMultisamples (multisampleSources, multiFile, selectedOutputFormat);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.create (destinationFolder, Collections.singletonList (multisampleSource));
    }


    /**
     * Create all YSFC chunks for the given multi-samples and store the file.
     *
     * @param multisampleSources The multi-sample sources to store
     * @param multiFile The file in which to store
     * @param outputFormat The output format
     * @throws IOException Could not store the file
     */
    private static void storeMultisamples (final List<IMultisampleSource> multisampleSources, final File multiFile, final OutputFormat outputFormat) throws IOException
    {
        final YsfcFile ysfcFile = new YsfcFile ();
        ysfcFile.setVersionStr (VERSION_MAP.get (outputFormat));

        final int libraryID = outputFormat == OutputFormat.MODX_USER || outputFormat == OutputFormat.MONTAGE_USER ? 0x10000 : 0x20000;

        // Numbering covers all(!) samples
        int sampleNumber = 1;

        for (int i = 0; i < multisampleSources.size (); i++)
        {
            final IMultisampleSource multisampleSource = multisampleSources.get (i);

            final List<YamahaYsfcKeybank> keybankList = new ArrayList<> ();
            final List<YamahaYsfcWaveData> waveDataList = new ArrayList<> ();

            final String multisampleName = multisampleSource.getName ();

            for (final IGroup group: multisampleSource.getGroups ())
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    // Ensure that the WAV is 16 bit
                    final WaveFile waveFile = AudioFileUtils.convertToWav (zone.getSampleData (), DESTINATION_AUDIO_FORMAT);
                    final FormatChunk formatChunk = waveFile.getFormatChunk ();
                    final int numberOfChannels = formatChunk.getNumberOfChannels ();
                    final String sampleName = zone.getName ();
                    if (numberOfChannels > 2)
                        throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), sampleName));

                    final DataChunk dataChunk = waveFile.getDataChunk ();
                    int numSamples;
                    try
                    {
                        numSamples = dataChunk.calculateLength (formatChunk);
                    }
                    catch (final CompressionNotSupportedException ex)
                    {
                        throw new IOException (ex);
                    }

                    final byte [] data = dataChunk.getData ();
                    final boolean isStereo = numberOfChannels == 2;

                    for (int channel = 0; channel < numberOfChannels; channel++)
                    {
                        final YamahaYsfcKeybank keybank = new YamahaYsfcKeybank ();
                        keybank.setNumber (sampleNumber);

                        keybank.setChannels (numberOfChannels);
                        keybank.setSampleFrequency (formatChunk.getSampleRate ());
                        keybank.setSampleLength (numSamples);
                        keybank.setRootNote (zone.getKeyRoot ());

                        keybank.setKeyRangeLower (zone.getKeyLow ());
                        keybank.setKeyRangeUpper (zone.getKeyHigh ());
                        keybank.setVelocityRangeLower (zone.getVelocityLow ());
                        keybank.setVelocityRangeUpper (zone.getVelocityHigh ());

                        final double tune = zone.getTune ();
                        final int semitones = (int) Math.floor (tune);
                        keybank.setCoarseTune (semitones);
                        keybank.setFineTune ((int) Math.floor ((tune - semitones) * 100.0));

                        // The 0-255 range seems to be somehow logarithmically scaled and there is
                        // not much happening below 200
                        keybank.setLevel (207 + 2 * (int) (Math.round (Math.clamp (zone.getGain (), -12.0, 12.0)) + 12));

                        final double panorama = zone.getPanorama ();
                        keybank.setPanorama ((int) (panorama < 0 ? panorama * 64 : panorama * 63));

                        keybank.setPlayStart (zone.getStart ());
                        keybank.setPlayEnd (zone.getStop ());

                        final List<ISampleLoop> loops = zone.getLoops ();
                        if (loops.isEmpty ())
                            keybank.setLoopMode (1);
                        else
                        {
                            final ISampleLoop loop = loops.get (0);
                            keybank.setLoopMode (loop.getType () == LoopType.BACKWARDS ? 2 : 0);
                            keybank.setLoopPoint (loop.getStart ());
                            if (loop.getEnd () < zone.getStop ())
                                keybank.setPlayEnd (loop.getEnd ());
                        }

                        keybankList.add (keybank);

                        final YamahaYsfcWaveData waveData = new YamahaYsfcWaveData ();
                        waveDataList.add (waveData);
                        waveData.setData (isStereo ? getChannelData (channel, data) : data);

                        sampleNumber++;
                    }
                }

            // Note: if several multi-samples will be combined this number needs to be increased!
            final int sampleIndex = libraryID + 1 + i;

            final YamahaYsfcEntry keyBankEntry = new YamahaYsfcEntry ();
            keyBankEntry.setSpecificValue (sampleIndex);

            // Set the category
            String n = multisampleName;
            final String category = multisampleSource.getMetadata ().getCategory ();
            if (category != null)
            {
                final Integer categoryID = YamahaYsfcCategories.getWaveformCategoryIndex (category);
                if (categoryID != null)
                    n = categoryID.toString () + ":" + n;
            }
            keyBankEntry.setItemName (n);

            final YamahaYsfcEntry waveDataEntry = new YamahaYsfcEntry ();
            waveDataEntry.setItemName (multisampleName);
            waveDataEntry.setSpecificValue (sampleIndex);

            ysfcFile.fillWaveChunks (keyBankEntry, keybankList, waveDataEntry, waveDataList);
        }

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            ysfcFile.write (out);
        }
    }


    private static byte [] getChannelData (final int channel, final byte [] data)
    {
        final byte [] channelData = new byte [data.length / 2];
        int pos = 0;
        final int offset = channel == 0 ? 0 : 2;
        for (int i = 0; i < data.length; i += 4)
        {
            channelData[pos] = data[i + offset];
            channelData[pos + 1] = data[i + 1 + offset];
            pos += 2;
        }
        return channelData;
    }


    private OutputFormat getSelectedOutputFormat ()
    {
        int selected = 1;
        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        for (int i = 0; i < toggles.size (); i++)
            if (toggles.get (i).isSelected ())
            {
                selected = i;
                break;
            }
        return OutputFormat.values ()[selected];
    }
}