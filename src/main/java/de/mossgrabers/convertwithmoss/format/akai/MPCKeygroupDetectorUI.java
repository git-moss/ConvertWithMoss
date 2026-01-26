// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;


/**
 * Settings of the MPC Key-group detector.
 *
 * @author Jürgen Moßgraber
 */
public class MPCKeygroupDetectorUI extends MetadataSettingsUI
{
    private static final String MPC_IGNORE_LOOPS = "MPCIgnoreLoops";

    private CheckBox            ignoreLoopsCheckBox;
    private boolean             ignoreLoops;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public MPCKeygroupDetectorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_MPC_OPTIONS");

        this.ignoreLoopsCheckBox = panel.createCheckBox ("@IDS_MPC_IGNORE_LOOPS", "@IDS_MPC_IGNORE_LOOPS_TOOLTIP");

        ////////////////////////////////////////////////////////////
        // Metadata

        this.addTo (panel);
        this.getSeparator ().getStyleClass ().add ("titled-separator-pane");

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        super.saveSettings (config);

        config.setBoolean (MPC_IGNORE_LOOPS, this.ignoreLoopsCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        this.ignoreLoopsCheckBox.setSelected (config.getBoolean (MPC_IGNORE_LOOPS, false));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.ignoreLoops = this.ignoreLoopsCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        String value = parameters.remove (MPC_IGNORE_LOOPS);
        this.ignoreLoops = "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (MPC_IGNORE_LOOPS);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should unsupported attributes be logged?
     *
     * @return True if they should be logged
     */
    public boolean ignoreLoops ()
    {
        return this.ignoreLoops;
    }
}
