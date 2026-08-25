// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;


/**
 * Settings for the creators which write the floppy disks of the E-mu Emulator and Emulator II: the
 * format of the image to write and the operating system to put onto the system tracks of the disk.
 * The system is not part of this program; it is taken from a system file of the sampler or from an
 * image of one of its disks.
 *
 * @author Jürgen Moßgraber
 */
public class EmuDiskCreatorUI implements ICoreTaskSettings
{
    private static final String OUTPUT_FORMAT    = "OutputFormat";
    private static final String OPERATING_SYSTEM = "OperatingSystem";
    private static final String FORMAT_HFE       = "hfe";
    private static final String FORMAT_RAW       = "raw";

    private final String        prefix;
    private final String        systemFileType;
    private final String        rawFileEnding;

    private ComboBox<String>    outputFormatBox;
    private TextField           operatingSystemField;

    private boolean             writeRawImage    = false;
    private String              operatingSystem  = "";


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     * @param systemFileType The type of the system file of the sampler, e.g. E1O
     * @param rawFileEnding The file ending of a raw disk image of the sampler, e.g. emufd
     */
    public EmuDiskCreatorUI (final String prefix, final String systemFileType, final String rawFileEnding)
    {
        this.prefix = prefix;
        this.systemFileType = systemFileType;
        this.rawFileEnding = rawFileEnding;
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        this.outputFormatBox = panel.createComboBox ("@IDS_EMU_OUTPUT_FORMAT", List.of (Functions.getMessage ("IDS_EMU_OUTPUT_HFE"), Functions.getMessage ("IDS_EMU_OUTPUT_RAW", this.rawFileEnding)));
        this.outputFormatBox.setMaxWidth (Double.MAX_VALUE);

        this.operatingSystemField = panel.createField (Functions.getMessage ("IDS_EMU_OPERATING_SYSTEM", this.systemFileType));
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputFormatBox.getSelectionModel ().select (config.getBoolean (this.prefix + OUTPUT_FORMAT, false) ? 1 : 0);
        this.operatingSystemField.setText (config.getProperty (this.prefix + OPERATING_SYSTEM, ""));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (this.prefix + OUTPUT_FORMAT, this.outputFormatBox.getSelectionModel ().getSelectedIndex () == 1);
        config.setProperty (this.prefix + OPERATING_SYSTEM, this.operatingSystemField.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.writeRawImage = this.outputFormatBox.getSelectionModel ().getSelectedIndex () == 1;
        this.operatingSystem = this.operatingSystemField.getText ().trim ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String format = parameters.remove (this.prefix + OUTPUT_FORMAT);
        this.writeRawImage = false;
        if (format != null)
            switch (format.trim ().toLowerCase (Locale.US))
            {
                case FORMAT_HFE:
                    // this.writeRawImage is already false;
                    break;
                case FORMAT_RAW:
                    this.writeRawImage = true;
                    break;
                default:
                    if (format.trim ().equalsIgnoreCase (this.rawFileEnding))
                    {
                        this.writeRawImage = true;
                        break;
                    }
                    notifier.logError ("IDS_EMU_UNKNOWN_OUTPUT_FORMAT", format);
                    return false;
            }

        final String system = parameters.remove (this.prefix + OPERATING_SYSTEM);
        this.operatingSystem = system == null ? "" : system.trim ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            this.prefix + OUTPUT_FORMAT,
            this.prefix + OPERATING_SYSTEM
        };
    }


    /**
     * Should a raw disk image be written instead of a HxC floppy emulator image?
     *
     * @return True to write a raw image
     */
    public boolean isWriteRawImage ()
    {
        return this.writeRawImage;
    }


    /**
     * Get the path of the system file or the disk image from which the operating system is taken.
     *
     * @return The path, empty if none is set
     */
    public String getOperatingSystemPath ()
    {
        return this.operatingSystem;
    }
}
