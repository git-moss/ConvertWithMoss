// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.nki.type.IKontaktType;
import de.mossgrabers.convertwithmoss.format.nki.type.KontaktTypes;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;

import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;


/**
 * Creator for Native Instruments Kontakt NKI files.
 *
 * @author Jürgen Moßgraber
 */
public class NkiCreator extends AbstractCreator
{
    private static final String NKI_OUTPUT_FORMAT = "NkiOutputFormat";
    private static final String FOLDER_POSTFIX    = " Samples";

    private final KontaktTypes  kontaktTypes;
    private ToggleGroup         outputFormatGroup;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public NkiCreator (final INotifier notifier)
    {
        super ("Kontakt NKI", notifier);

        this.kontaktTypes = new KontaktTypes (notifier, null);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_NKI_OUTPUT_FORMAT");

        this.outputFormatGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_NKI_KONTAKT_1");
        order1.setToggleGroup (this.outputFormatGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_NKI_KONTAKT_2");
        order2.setToggleGroup (this.outputFormatGroup);
        order2.setDisable (true);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        final int formatIndex = config.getInteger (NKI_OUTPUT_FORMAT, 0);
        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        this.outputFormatGroup.selectToggle (toggles.get (formatIndex < toggles.size () ? formatIndex : 0));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        for (int i = 0; i < toggles.size (); i++)
        {
            if (toggles.get (i).isSelected ())
            {
                config.setInteger (NKI_OUTPUT_FORMAT, i);
                break;
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final Integer kontaktID = this.outputFormatGroup.getToggles ().get (0).isSelected () ? KontaktTypes.ID_KONTAKT1 : KontaktTypes.ID_KONTAKT2_BIG_ENDIAN;
        final IKontaktType kontaktType = this.kontaktTypes.getType (kontaktID);

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = new File (destinationFolder, sampleName + ".nki");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        // First, store all samples
        final String safeSampleFolderName = sampleName + FOLDER_POSTFIX;
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        final List<File> writeSamples = this.writeSamples (sampleFolder, multisampleSource);

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            kontaktType.writeNKI (out, safeSampleFolderName, multisampleSource, calculateSampleSize (writeSamples));
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Calculates the size of all samples.
     *
     * @param sampleFiles The sample files (must be all WAV files).
     * @return The summed size
     * @throws IOException Could not read a file
     */
    private static int calculateSampleSize (final List<File> sampleFiles) throws IOException
    {
        int size = 0;
        try
        {
            for (final File file: sampleFiles)
                size += new WaveFile (file, true).getDataChunk ().getData ().length;
        }
        catch (final ParseException ex)
        {
            throw new IOException (ex);
        }
        return size;
    }
}