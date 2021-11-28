// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.creator.ICreator;
import de.mossgrabers.sampleconverter.core.detector.IDetector;
import de.mossgrabers.sampleconverter.format.akai.MPCKeygroupCreator;
import de.mossgrabers.sampleconverter.format.akai.MPCKeygroupDetector;
import de.mossgrabers.sampleconverter.format.bitwig.BitwigMultisampleCreator;
import de.mossgrabers.sampleconverter.format.bitwig.BitwigMultisampleDetector;
import de.mossgrabers.sampleconverter.format.decentsampler.DecentSamplerCreator;
import de.mossgrabers.sampleconverter.format.decentsampler.DecentSamplerDetector;
import de.mossgrabers.sampleconverter.format.korgmultisample.KorgmultisampleCreator;
import de.mossgrabers.sampleconverter.format.korgmultisample.KorgmultisampleDetector;
import de.mossgrabers.sampleconverter.format.sf2.Sf2Detector;
import de.mossgrabers.sampleconverter.format.sfz.SfzCreator;
import de.mossgrabers.sampleconverter.format.sfz.SfzDetector;
import de.mossgrabers.sampleconverter.format.wav.WavDetector;
import de.mossgrabers.sampleconverter.ui.tools.AbstractFrame;
import de.mossgrabers.sampleconverter.ui.tools.DefaultApplication;
import de.mossgrabers.sampleconverter.ui.tools.EndApplicationException;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.ui.tools.control.LoggerBox;
import de.mossgrabers.sampleconverter.ui.tools.control.TitledSeparator;
import de.mossgrabers.sampleconverter.ui.tools.panel.BoxPanel;
import de.mossgrabers.sampleconverter.ui.tools.panel.ButtonPanel;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * The sample converter application.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SampleConverterApp extends AbstractFrame implements INotifier, Consumer<IMultisampleSource>
{
    private static final String ENABLE_DARK_MODE                    = "EnableDarkMode";
    private static final String DESTINATION_CREATE_FOLDER_STRUCTURE = "DestinationCreateFolderStructure";
    private static final String DESTINATION_ADD_NEW_FILES           = "DestinationAddNewFiles";
    private static final String DESTINATION_PATH                    = "DestinationPath";
    private static final String DESTINATION_TYPE                    = "DestinationType";
    private static final String SOURCE_PATH                         = "SourcePath";
    private static final String SOURCE_TYPE                         = "SourceType";

    private final IDetector []  detectors;
    private final ICreator []   creators;

    private BorderPane          mainPane;
    private BorderPane          executePane;
    private TextField           sourcePathField;
    private TextField           destinationPathField;
    private File                sourceFolder;
    private File                outputFolder;
    private CheckBox            createFolderStructure;
    private CheckBox            addNewFiles;
    private CheckBox            enableDarkMode;

    private TabPane             sourceTabPane;
    private TabPane             destinationTabPane;

    private boolean             onlyAnalyse                         = true;
    private Button              closeButton;
    private Button              cancelButton;
    private final LoggerBox     loggingArea                         = new LoggerBox ();


    /**
     * Main-method.
     *
     * @param args The startup arguments
     */
    public static void main (final String [] args)
    {
        Application.launch (DefaultApplication.class, SampleConverterApp.class.getName ());
    }


    /**
     * Constructor.
     *
     * @throws EndApplicationException Startup crash
     */
    public SampleConverterApp () throws EndApplicationException
    {
        super ("de/mossgrabers/sampleconverter", 1000, 800);

        this.detectors = new IDetector []
        {
            new WavDetector (this),
            new BitwigMultisampleDetector (this),
            new SfzDetector (this),
            new Sf2Detector (this),
            new DecentSamplerDetector (this),
            new MPCKeygroupDetector (this),
            new KorgmultisampleDetector (this)
        };

        this.creators = new ICreator []
        {
            new BitwigMultisampleCreator (this),
            new SfzCreator (this),
            new DecentSamplerCreator (this),
            new MPCKeygroupCreator (this),
            new KorgmultisampleCreator (this)
        };
    }


    /** {@inheritDoc} */
    @Override
    public void initialise (final Stage stage, final Optional<String> baseTitleOptional) throws EndApplicationException
    {
        super.initialise (stage, baseTitleOptional, true, true, true);

        // The main button panel
        final ButtonPanel buttonPanel = new ButtonPanel (Orientation.VERTICAL);
        buttonPanel.createButton ("@IDS_MAIN_CONVERT").setOnAction (event -> this.execute (false));
        buttonPanel.createButton ("@IDS_MAIN_ANALYSE").setOnAction (event -> this.execute (true));

        // Source pane
        final BorderPane sourcePane = new BorderPane ();

        this.sourcePathField = new TextField ();
        final BorderPane sourceFolderPanel = new BorderPane (this.sourcePathField);

        final Button sourceFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_SOURCE"));
        sourceFolderSelectButton.setOnAction (event -> {

            final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, Functions.getText ("@IDS_MAIN_SELECT_SOURCE_HEADER"));
            if (file.isPresent ())
                this.sourcePathField.setText (file.get ().getAbsolutePath ());

        });
        sourceFolderPanel.setRight (sourceFolderSelectButton);

        final BoxPanel sourceUpperPart = new BoxPanel (Orientation.VERTICAL);
        sourceUpperPart.addComponent (new TitledSeparator (Functions.getText ("@IDS_MAIN_SOURCE_HEADER")));
        sourceUpperPart.addComponent (sourceFolderPanel);

        sourcePane.setTop (sourceUpperPart.getPane ());
        this.sourceTabPane = new TabPane ();
        this.sourceTabPane.getStyleClass ().add ("paddingLeftBottomRight");
        sourcePane.setCenter (this.sourceTabPane);

        final ObservableList<Tab> tabs = this.sourceTabPane.getTabs ();
        for (final IDetector detector: this.detectors)
        {
            final Tab tab = new Tab (detector.getName (), detector.getEditPane ());
            tab.setClosable (false);
            tabs.add (tab);
        }

        // Destination pane
        final BorderPane destinationPane = new BorderPane ();

        this.destinationPathField = new TextField ();
        final BorderPane destinationFolderPanel = new BorderPane (this.destinationPathField);

        final Button destinationFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION"));
        destinationFolderSelectButton.setOnAction (event -> {

            final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, Functions.getText ("@IDS_MAIN_SELECT_DESTINATION_HEADER"));
            if (file.isPresent ())
                this.destinationPathField.setText (file.get ().getAbsolutePath ());

        });
        destinationFolderPanel.setRight (destinationFolderSelectButton);

        final BoxPanel destinationUpperPart = new BoxPanel (Orientation.VERTICAL);
        destinationUpperPart.addComponent (new TitledSeparator ("@IDS_MAIN_DESTINATION_HEADER"));
        destinationUpperPart.addComponent (destinationFolderPanel);

        destinationPane.setTop (destinationUpperPart.getPane ());
        this.destinationTabPane = new TabPane ();
        this.destinationTabPane.getStyleClass ().add ("paddingLeftBottomRight");
        destinationPane.setCenter (this.destinationTabPane);

        final BoxPanel bottomLeft = new BoxPanel (Orientation.HORIZONTAL);
        this.createFolderStructure = bottomLeft.createCheckBox ("@IDS_MAIN_CREATE_FOLDERS", "@IDS_MAIN_CREATE_FOLDERS_TOOLTIP");
        this.createFolderStructure.setSelected (true);
        this.addNewFiles = bottomLeft.createCheckBox ("@IDS_MAIN_ADD_NEW", "@IDS_MAIN_ADD_NEW_TOOLTIP");

        final BoxPanel bottomRight = new BoxPanel (Orientation.HORIZONTAL);
        this.enableDarkMode = bottomRight.createCheckBox ("@IDS_MAIN_ENABLE_DARK_MODE", "@IDS_MAIN_ENABLE_DARK_MODE_TOOLTIP");
        this.enableDarkMode.selectedProperty ().addListener ( (obs, wasSelected, isSelected) -> {
            final ObservableList<String> stylesheets = this.scene.getStylesheets ();
            final String stylesheet = this.startPath + "/css/Darkmode.css";
            this.loggingArea.setDarkmode (isSelected.booleanValue ());
            if (isSelected.booleanValue ())
                stylesheets.add (stylesheet);
            else
                stylesheets.remove (stylesheet);
        });

        destinationPane.setBottom (new BorderPane (null, null, bottomRight.getPane (), null, bottomLeft.getPane ()));

        final ObservableList<Tab> destinationTabs = this.destinationTabPane.getTabs ();
        for (final ICreator creator: this.creators)
        {
            final Tab tab = new Tab (creator.getName (), creator.getEditPane ());
            tab.setClosable (false);
            destinationTabs.add (tab);
        }

        // Tie it all together ...
        final HBox grid = new HBox ();
        grid.setFillHeight (true);
        grid.getChildren ().addAll (sourcePane, destinationPane);
        HBox.setHgrow (sourcePane, Priority.ALWAYS);
        HBox.setHgrow (destinationPane, Priority.ALWAYS);

        this.mainPane = new BorderPane ();
        this.mainPane.setCenter (grid);
        this.mainPane.setRight (buttonPanel.getPane ());

        // Execution pane
        this.executePane = new BorderPane ();

        // The execution button panel
        final ButtonPanel exButtonPanel = new ButtonPanel (Orientation.VERTICAL);
        this.cancelButton = exButtonPanel.createButton ("@IDS_EXEC_CANCEL");
        this.cancelButton.setOnAction (event -> this.cancelExecution ());
        this.closeButton = exButtonPanel.createButton ("@IDS_EXEC_CLOSE");
        this.closeButton.setOnAction (event -> this.closeExecution ());

        this.executePane.setCenter (this.loggingArea.getWebView ());
        this.executePane.setRight (exButtonPanel.getPane ());
        this.executePane.setVisible (false);

        final StackPane stackPane = new StackPane (this.mainPane, this.executePane);
        this.setCenterNode (stackPane);

        this.loadConfig ();

        this.updateTitle (null);
    }


    /**
     * Load configuration settings.
     */
    private void loadConfig ()
    {
        final String sourcePath = this.config.getProperty (SOURCE_PATH);
        if (sourcePath != null)
            this.sourcePathField.setText (sourcePath);

        final String destinationPath = this.config.getProperty (DESTINATION_PATH);
        if (destinationPath != null)
            this.destinationPathField.setText (destinationPath);

        this.createFolderStructure.setSelected (this.config.getBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, true));
        this.addNewFiles.setSelected (this.config.getBoolean (DESTINATION_ADD_NEW_FILES, false));
        this.enableDarkMode.setSelected (this.config.getBoolean (ENABLE_DARK_MODE, false));

        for (final IDetector detector: this.detectors)
            detector.loadSettings (this.config);
        for (final ICreator creator: this.creators)
            creator.loadSettings (this.config);

        final int sourceType = this.config.getInteger (SOURCE_TYPE, 0);
        this.sourceTabPane.getSelectionModel ().select (sourceType);
        final int destinationType = this.config.getInteger (DESTINATION_TYPE, 0);
        this.destinationTabPane.getSelectionModel ().select (destinationType);
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        for (final IDetector detector: this.detectors)
            detector.shutdown ();

        this.config.setProperty (SOURCE_PATH, this.sourcePathField.getText ());
        this.config.setProperty (DESTINATION_PATH, this.destinationPathField.getText ());
        this.config.setBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, this.createFolderStructure.isSelected ());
        this.config.setBoolean (DESTINATION_ADD_NEW_FILES, this.addNewFiles.isSelected ());
        this.config.setBoolean (ENABLE_DARK_MODE, this.enableDarkMode.isSelected ());

        for (final IDetector detector: this.detectors)
            detector.saveSettings (this.config);
        for (final ICreator creator: this.creators)
            creator.saveSettings (this.config);

        final int sourceSelectedIndex = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (SOURCE_TYPE, sourceSelectedIndex);
        final int destinationSelectedIndex = this.destinationTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (DESTINATION_TYPE, destinationSelectedIndex);

        // Store configuration
        super.exit ();

        Platform.exit ();
    }


    /**
     * Execute the conversion.
     *
     * @param onlyAnalyse Do not create output files if true
     */
    private void execute (final boolean onlyAnalyse)
    {
        this.onlyAnalyse = onlyAnalyse;

        if (!this.verifyFolders ())
            return;

        final int selectedDetector = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedDetector < 0)
            return;

        this.loggingArea.clear ();

        this.mainPane.setVisible (false);
        this.executePane.setVisible (true);

        Platform.runLater ( () -> {
            this.log ("IDS_NOTIFY_DETECTING");
            this.detectors[selectedDetector].detect (this.sourceFolder, this);
        });
    }


    /**
     * Cancel button was pressed.
     */
    private void cancelExecution ()
    {
        final int selectedDetector = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedDetector >= 0)
            this.detectors[selectedDetector].cancel ();
    }


    /**
     * Close button was pressed.
     */
    private void closeExecution ()
    {
        this.mainPane.setVisible (true);
        this.executePane.setVisible (false);
    }


    /**
     * Set and check folder for existence.
     *
     * @return True if OK
     */
    private boolean verifyFolders ()
    {
        // Check source folder
        this.sourceFolder = new File (this.sourcePathField.getText ());
        if (!this.sourceFolder.exists () || !this.sourceFolder.isDirectory ())
        {
            Functions.message ("The source folder does not exist: %1", this.sourceFolder.getAbsolutePath ());
            return false;
        }

        if (this.onlyAnalyse)
            return true;

        // Check output folder
        this.outputFolder = new File (this.destinationPathField.getText ());
        if (!this.outputFolder.exists () && !this.outputFolder.mkdirs ())
        {
            Functions.message ("Could not create output folder: %1", this.outputFolder.getAbsolutePath ());
            return false;
        }
        if (!this.outputFolder.isDirectory ())
        {
            Functions.message ("The selected output folder is not a folder: %1", this.outputFolder.getAbsolutePath ());
            return false;
        }

        // Output folder must be empty or add new must be active
        if (!this.addNewFiles.isSelected ())
        {
            final String [] content = this.outputFolder.list ();
            if (content == null || content.length > 0)
            {
                Functions.message ("The output folder is not empty. Please select an empty folder.");
                return false;
            }
        }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void accept (final IMultisampleSource multisampleSource)
    {
        final int selectedCreator = this.destinationTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedCreator < 0)
            return;

        this.log ("IDS_NOTIFY_MAPPING", multisampleSource.getMappingName ());

        try
        {
            if (this.onlyAnalyse)
                return;

            final boolean createStructure = this.createFolderStructure.isSelected ();
            final File multisampleOutputFolder = calcOutputFolder (this.outputFolder, multisampleSource.getSubPath (), createStructure);
            this.creators[selectedCreator].create (multisampleOutputFolder, multisampleSource);
        }
        catch (final IOException | RuntimeException ex)
        {
            this.logError ("IDS_NOTIFY_SAVE_FAILED", ex.getMessage ());
        }
    }


    /** {@inheritDoc} */
    @Override
    public void log (final String messageID, final String... replaceStrings)
    {
        this.loggingArea.notify (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final String... replaceStrings)
    {
        this.loggingArea.notifyError (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final Throwable throwable)
    {
        this.loggingArea.notifyError (Functions.getMessage (messageID, throwable));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final Throwable throwable)
    {
        this.loggingArea.notifyError (throwable.getMessage (), throwable);
    }


    /**
     * Creates the file object for the output folder.
     *
     * @param out The top output folder
     * @param parts The sub-folder parts
     * @param createSubFolders If true the sub-folders are created inside of the top output folder
     * @return The destination folder
     * @throws IOException Could not create sub-folders
     */
    private static File calcOutputFolder (final File out, final String [] parts, final boolean createSubFolders) throws IOException
    {
        if (!createSubFolders)
            return out;

        File result = out;
        for (int i = parts.length - 2; i >= 1; i--)
        {
            result = new File (result, parts[i]);
            if (!result.exists () && !result.mkdirs ())
                throw new IOException ("Could not create folder: " + result.getAbsolutePath ());
        }
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public void updateButtonStates (final boolean canClose)
    {
        Platform.runLater ( () -> {
            this.cancelButton.setDisable (canClose);
            this.closeButton.setDisable (!canClose);
        });
    }
}
