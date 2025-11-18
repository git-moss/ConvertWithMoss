// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;


/**
 * Settings for DecentSampler creator.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerCreatorUI extends WavChunkSettingsUI
{
    private static final String DS_OUTPUT_CREATE_BUNDLE        = "DecentSamplerOutputCreateBundle";
    private static final String DS_OUTPUT_MAKE_MONOPHONIC      = "DecentSamplerOutputMakeMonophonic";
    private static final String DS_TEMPLATE_FOLDER_PATH        = "DecentSamplerTemplateFolderPath";
    private static final String DS_OUTPUT_ADD_FILTER_TO_GROUPS = "DecentSamplerAddFilterToGroups";

    private static final String TEMPLATE_FOLDER                = "de/mossgrabers/convertwithmoss/templates/dspreset/";
    private static final int    NUMBER_OF_DIRECTORIES          = 20;

    private CheckBox            createBundleCheckBox;
    private CheckBox            makeMonophonicCheckBox;
    private CheckBox            addFilterToGroupsCheckBox;
    private ComboBox<String>    templateFolderPathField;
    private Button              templateFolderPathSelectButton;
    private final List<String>  templateFolderPathHistory      = new ArrayList<> ();
    private Button              createTemplatesButton;

    private boolean             createBundle;
    private boolean             makeMonophonic;
    private boolean             addFilterToGroups;
    private File                templateFolderPath;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public DecentSamplerCreatorUI (final String prefix)
    {
        super (prefix, true, false, false, false);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        this.templateFolderPathField = new ComboBox<> ();
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_DS_OUTPUT_FORMAT");
        this.createBundleCheckBox = panel.createCheckBox ("@IDS_DS_CREATE_BUNDLE");

        final TitledSeparator separator = panel.createSeparator ("@IDS_DS_USER_INTERFACE");
        separator.getStyleClass ().add ("titled-separator-pane");

        this.makeMonophonicCheckBox = panel.createCheckBox ("@IDS_DS_MAKE_MONOPHONIC");
        this.addFilterToGroupsCheckBox = panel.createCheckBox ("@IDS_DS_ADD_FILTER_TO_GROUPS");

        final BoxPanel templateFolderPathPanel = new BoxPanel (Orientation.VERTICAL, false);
        final TitledSeparator templateFolderPathTitle = new TitledSeparator (Functions.getText ("@IDS_DS_TEMPLATE_FOLDER"));
        templateFolderPathTitle.getStyleClass ().add ("titled-separator-pane");
        templateFolderPathTitle.setLabelFor (this.templateFolderPathField);
        templateFolderPathPanel.addComponent (templateFolderPathTitle);

        this.templateFolderPathSelectButton = new Button (Functions.getText ("@IDS_DS_SELECT_TEMPLATE_PATH"));
        this.templateFolderPathSelectButton.setTooltip (new Tooltip (Functions.getText ("@IDS_DS_SELECT_TEMPLATE_PATH_TOOLTIP")));
        this.templateFolderPathSelectButton.setOnAction (_ -> this.selectTemplateFolderPath (null));

        this.createTemplatesButton = new Button (Functions.getText ("@IDS_DS_CREATE_TEMPLATES"));
        this.createTemplatesButton.setTooltip (new Tooltip (Functions.getText ("@IDS_DS_CREATE_TEMPLATES_TOOLTIP")));
        this.createTemplatesButton.setOnAction (_ -> this.createTemplates ());

        templateFolderPathPanel.addComponent (new BorderPane (this.templateFolderPathField, null, this.templateFolderPathSelectButton, null, null));
        this.templateFolderPathField.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (templateFolderPathPanel);
        panel.addComponent (this.createTemplatesButton);

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.createBundleCheckBox.setSelected (config.getBoolean (DS_OUTPUT_CREATE_BUNDLE, false));
        this.makeMonophonicCheckBox.setSelected (config.getBoolean (DS_OUTPUT_MAKE_MONOPHONIC, false));
        this.addFilterToGroupsCheckBox.setSelected (config.getBoolean (DS_OUTPUT_ADD_FILTER_TO_GROUPS, true));

        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            final String templateFolderPath = config.getProperty (DS_TEMPLATE_FOLDER_PATH + i);
            if (templateFolderPath == null)
                break;
            if (!this.templateFolderPathHistory.contains (templateFolderPath))
                this.templateFolderPathHistory.add (templateFolderPath);
        }
        this.templateFolderPathField.getItems ().addAll (this.templateFolderPathHistory);
        this.templateFolderPathField.setEditable (true);
        if (!this.templateFolderPathHistory.isEmpty ())
            this.templateFolderPathField.getEditor ().setText (this.templateFolderPathHistory.get (0));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (DS_OUTPUT_CREATE_BUNDLE, this.createBundleCheckBox.isSelected ());
        config.setBoolean (DS_OUTPUT_MAKE_MONOPHONIC, this.makeMonophonicCheckBox.isSelected ());
        config.setBoolean (DS_OUTPUT_ADD_FILTER_TO_GROUPS, this.addFilterToGroupsCheckBox.isSelected ());

        updateHistory (this.templateFolderPathField.getEditor ().getText (), this.templateFolderPathHistory);
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
            config.setProperty (DS_TEMPLATE_FOLDER_PATH + i, this.templateFolderPathHistory.size () > i ? this.templateFolderPathHistory.get (i) : "");

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.createBundle = this.createBundleCheckBox.isSelected ();
        this.makeMonophonic = this.makeMonophonicCheckBox.isSelected ();
        this.addFilterToGroups = this.addFilterToGroupsCheckBox.isSelected ();
        this.templateFolderPath = new File (this.templateFolderPathField.getEditor ().getText ());

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        String value = parameters.remove (DS_OUTPUT_CREATE_BUNDLE);
        this.createBundle = "1".equals (value);

        value = parameters.remove (DS_OUTPUT_MAKE_MONOPHONIC);
        this.makeMonophonic = "1".equals (value);

        value = parameters.remove (DS_TEMPLATE_FOLDER_PATH);
        this.addFilterToGroups = "1".equals (value);

        value = parameters.remove (DS_OUTPUT_ADD_FILTER_TO_GROUPS);
        this.templateFolderPath = new File (value == null ? "" : value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (DS_OUTPUT_CREATE_BUNDLE);
        parameterNames.add (DS_OUTPUT_MAKE_MONOPHONIC);
        parameterNames.add (DS_TEMPLATE_FOLDER_PATH);
        parameterNames.add (DS_OUTPUT_ADD_FILTER_TO_GROUPS);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the content of a template.
     * 
     * @param filename The filename of the template
     * @return The template content
     * @throws IOException If the template cannot be found or not be loaded
     */
    public String getTemplateCode (final String filename) throws IOException
    {
        final File currentTemplateFolderPath = this.getTemplateFolderPath ();
        if (currentTemplateFolderPath.exists () && currentTemplateFolderPath.isDirectory ())
        {
            final File templateFile = new File (currentTemplateFolderPath, filename);
            if (templateFile.exists ())
                return Files.readString (templateFile.toPath (), StandardCharsets.UTF_8);
        }

        return Functions.textFileFor (TEMPLATE_FOLDER + filename);
    }


    /**
     * Get the path to the template folder.
     * 
     * @return The template folder
     */
    public File getTemplateFolderPath ()
    {
        return this.templateFolderPath;
    }


    /**
     * Should a filter be added to groups?
     * 
     * @return True to add a filter
     */
    public boolean addFilterToGroups ()
    {
        return this.addFilterToGroups;
    }


    /**
     * Should a bundle be created?
     * 
     * @return True to create a bundle
     */
    public boolean createBundle ()
    {
        return this.createBundle;
    }


    /**
     * Should it be made mono-phonic?
     * 
     * @return True for mono-phonic mode
     */
    public boolean makeMonophonic ()
    {
        return this.makeMonophonic;
    }


    private void selectTemplateFolderPath (final Window parentWindow)
    {
        final File currentTemplateFolderPath = new File (this.templateFolderPathField.getEditor ().getText ());
        final BasicConfig config = new BasicConfig ("");
        if (currentTemplateFolderPath.exists () && currentTemplateFolderPath.isDirectory ())
            config.setActivePath (currentTemplateFolderPath);
        final Optional<File> file = Functions.getFolderFromUser (parentWindow, config, "@IDS_DS_SELECT_TEMPLATE_FOLDER_HEADER");
        if (file.isPresent ())
            this.templateFolderPathField.getEditor ().setText (file.get ().getAbsolutePath ());
    }


    private void createTemplates ()
    {
        final File templateFolderPath = new File (this.templateFolderPathField.getEditor ().getText ());
        if (!templateFolderPath.exists () && !templateFolderPath.mkdirs ())
        {
            Functions.message ("@IDS_DS_COULD_NOT_CREATE_TEMPLATE_DIR");
            return;
        }

        if (!templateFolderPath.isDirectory ())
        {
            Functions.message ("@IDS_DS_TEMPLATE_DIR_IS_FILE");
            return;
        }

        try
        {
            // Copy the template from the JAR resources to the given template folder
            final String uiTemplate = Functions.textFileFor (TEMPLATE_FOLDER + "ui.xml");
            final File uiFile = new File (templateFolderPath, "ui.xml");
            if (!uiFile.exists ())
                Files.write (uiFile.toPath (), uiTemplate.getBytes ());
        }
        catch (final IOException ex)
        {
            Functions.message ("@IDS_DS_COULD_NOT_CREATE_TEMPLATES", ex.getMessage ());
            return;
        }

        Functions.message ("@IDS_DS_TEMPLATES_CREATED");
    }


    private static void updateHistory (final String newItem, final List<String> history)
    {
        history.remove (newItem);
        history.add (0, newItem);
    }
}
