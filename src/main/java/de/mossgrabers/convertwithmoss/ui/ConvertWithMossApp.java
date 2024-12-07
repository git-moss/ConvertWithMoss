// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.ICreator;
import de.mossgrabers.convertwithmoss.core.detector.IDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.file.CSVRenameFile;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonCreator;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonDetector;
import de.mossgrabers.convertwithmoss.format.akai.MPCKeygroupCreator;
import de.mossgrabers.convertwithmoss.format.akai.MPCKeygroupDetector;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleCreator;
import de.mossgrabers.convertwithmoss.format.bitwig.BitwigMultisampleDetector;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerCreator;
import de.mossgrabers.convertwithmoss.format.decentsampler.DecentSamplerDetector;
import de.mossgrabers.convertwithmoss.format.disting.DistingExCreator;
import de.mossgrabers.convertwithmoss.format.disting.DistingExDetector;
import de.mossgrabers.convertwithmoss.format.exs.EXS24Creator;
import de.mossgrabers.convertwithmoss.format.exs.EXS24Detector;
import de.mossgrabers.convertwithmoss.format.kmp.KMPCreator;
import de.mossgrabers.convertwithmoss.format.kmp.KMPDetector;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleCreator;
import de.mossgrabers.convertwithmoss.format.korgmultisample.KorgmultisampleDetector;
import de.mossgrabers.convertwithmoss.format.music1010.Music1010Creator;
import de.mossgrabers.convertwithmoss.format.music1010.Music1010Detector;
import de.mossgrabers.convertwithmoss.format.nki.NkiCreator;
import de.mossgrabers.convertwithmoss.format.nki.NkiDetector;
import de.mossgrabers.convertwithmoss.format.samplefile.SampleFileDetector;
import de.mossgrabers.convertwithmoss.format.sf2.Sf2Creator;
import de.mossgrabers.convertwithmoss.format.sf2.Sf2Detector;
import de.mossgrabers.convertwithmoss.format.sfz.SfzCreator;
import de.mossgrabers.convertwithmoss.format.sfz.SfzDetector;
import de.mossgrabers.convertwithmoss.format.sxt.SxtCreator;
import de.mossgrabers.convertwithmoss.format.sxt.SxtDetector;
import de.mossgrabers.convertwithmoss.format.tal.TALSamplerCreator;
import de.mossgrabers.convertwithmoss.format.tal.TALSamplerDetector;
import de.mossgrabers.convertwithmoss.format.tx16wx.TX16WxCreator;
import de.mossgrabers.convertwithmoss.format.tx16wx.TX16WxDetector;
import de.mossgrabers.convertwithmoss.format.waldorf.qpat.WaldorfQpatCreator;
import de.mossgrabers.convertwithmoss.format.waldorf.qpat.WaldorfQpatDetector;
import de.mossgrabers.convertwithmoss.format.wav.WavCreator;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YamahaYsfcCreator;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YamahaYsfcDetector;
import de.mossgrabers.tools.ui.AbstractFrame;
import de.mossgrabers.tools.ui.DefaultApplication;
import de.mossgrabers.tools.ui.EndApplicationException;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.TraversalManager;
import de.mossgrabers.tools.ui.control.LoggerBoxWeb;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BasePanel;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import de.mossgrabers.tools.ui.panel.ButtonPanel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;


/**
 * The sample converter application.
 *
 * @author Jürgen Moßgraber
 */
public class ConvertWithMossApp extends AbstractFrame implements INotifier, Consumer<IMultisampleSource>
{
    private static final int               NUMBER_OF_DIRECTORIES               = 20;

    private static final String            ENABLE_DARK_MODE                    = "EnableDarkMode";
    private static final String            DESTINATION_CREATE_FOLDER_STRUCTURE = "DestinationCreateFolderStructure";
    private static final String            DESTINATION_ADD_NEW_FILES           = "DestinationAddNewFiles";
    private static final String            DESTINATION_PATH                    = "DestinationPath";
    private static final String            DESTINATION_TYPE                    = "DestinationType";
    private static final String            SOURCE_PATH                         = "SourcePath";
    private static final String            SOURCE_TYPE                         = "SourceType";
    private static final String            RENAMING_CSV_FILE                   = "RenamingCSVFile";
    private static final String            RENAMING_SOURCE_ENABLED             = "EnableRenaming";

    private final IDetector []             detectors;
    private final ICreator []              creators;

    private BorderPane                     mainPane;
    private BorderPane                     executePane;
    private final ComboBox<String>         sourcePathField                     = new ComboBox<> ();
    private final ComboBox<String>         destinationPathField                = new ComboBox<> ();
    private File                           sourceFolder;
    private File                           outputFolder;
    private Button                         convertButton;
    private Button                         analyseButton;
    private Button                         sourceFolderSelectButton;
    private Button                         destinationFolderSelectButton;
    private CheckBox                       createFolderStructure;
    private CheckBox                       addNewFiles;
    private CheckBox                       enableDarkMode;

    private final TabPane                  sourceTabPane                       = new TabPane ();
    private final TabPane                  destinationTabPane                  = new TabPane ();

    private final List<String>             sourcePathHistory                   = new ArrayList<> ();
    private final List<String>             destinationPathHistory              = new ArrayList<> ();

    private boolean                        onlyAnalyse                         = true;
    private Button                         closeButton;
    private Button                         cancelButton;

    private CheckBox                       renameCheckbox;
    private final TextField                renameFilePathField                 = new TextField ();
    private Button                         renameFilePathSelectButton;

    private final CSVRenameFile            csvRenameFile                       = new CSVRenameFile ();
    private final LoggerBoxWeb             loggingArea                         = new LoggerBoxWeb ();
    private final TraversalManager         traversalManager                    = new TraversalManager ();
    private final List<IMultisampleSource> collectedSources                    = new ArrayList<> ();


    /**
     * Main-method.
     *
     * @param arguments The startup arguments
     */
    public static void main (final String [] arguments)
    {
        Application.launch (DefaultApplication.class, ConvertWithMossApp.class.getName ());
    }


    /**
     * Constructor.
     *
     * @throws EndApplicationException Startup crash
     */
    public ConvertWithMossApp () throws EndApplicationException
    {
        super ("de/mossgrabers/convertwithmoss", 1280, 840);

        this.detectors = new IDetector []
        {
            new Music1010Detector (this),
            new AbletonDetector (this),
            new MPCKeygroupDetector (this),
            new BitwigMultisampleDetector (this),
            new TX16WxDetector (this),
            new DecentSamplerDetector (this),
            new DistingExDetector (this),
            new NkiDetector (this),
            new KMPDetector (this),
            new KorgmultisampleDetector (this),
            new EXS24Detector (this),
            new SxtDetector (this),
            new SampleFileDetector (this),
            new SfzDetector (this),
            new Sf2Detector (this),
            new TALSamplerDetector (this),
            new WaldorfQpatDetector (this),
            new YamahaYsfcDetector (this)
        };

        this.creators = new ICreator []
        {
            new Music1010Creator (this),
            new AbletonCreator (this),
            new MPCKeygroupCreator (this),
            new BitwigMultisampleCreator (this),
            new TX16WxCreator (this),
            new DecentSamplerCreator (this),
            new DistingExCreator (this),
            new NkiCreator (this),
            new KMPCreator (this),
            new KorgmultisampleCreator (this),
            new EXS24Creator (this),
            new SxtCreator (this),
            new SfzCreator (this),
            new Sf2Creator (this),
            new TALSamplerCreator (this),
            new WaldorfQpatCreator (this),
            new WavCreator (this),
            new YamahaYsfcCreator (this)
        };
    }


    /**
     * Enables/disables the renaming controls depending on the selection status of the renaming
     * checkbox.
     */
    private void updateRenamingControls ()
    {
        this.renameFilePathField.setDisable (!this.renameCheckbox.isSelected ());
        this.renameFilePathSelectButton.setDisable (!this.renameCheckbox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void initialise (final Stage stage, final Optional<String> baseTitleOptional) throws EndApplicationException
    {
        super.initialise (stage, baseTitleOptional, true, true, true);

        // The main button panel
        final ButtonPanel buttonPanel = new ButtonPanel (Orientation.VERTICAL);
        this.convertButton = setupButton (buttonPanel, "Convert", "@IDS_MAIN_CONVERT", "@IDS_MAIN_CONVERT_TOOLTIP");
        this.convertButton.setDefaultButton (true);
        this.convertButton.setOnAction (event -> this.execute (false));
        this.analyseButton = setupButton (buttonPanel, "Analyse", "@IDS_MAIN_ANALYSE", "@IDS_MAIN_ANALYSE_TOOLTIP");
        this.analyseButton.setOnAction (event -> this.execute (true));

        /////////////////////////////////////////////////////////////////////////////
        // Source pane

        this.sourceFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_SOURCE"));
        this.sourceFolderSelectButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_SELECT_SOURCE_TOOLTIP")));
        this.sourceFolderSelectButton.setOnAction (event -> this.selectSourcePath ());
        final BoxPanel sourceUpperPart = new BoxPanel (Orientation.VERTICAL);
        final TitledSeparator sourceTitle = new TitledSeparator (Functions.getText ("@IDS_MAIN_SOURCE_HEADER"));
        sourceTitle.setLabelFor (this.sourcePathField);
        sourceUpperPart.addComponent (sourceTitle);
        sourceUpperPart.addComponent (new BorderPane (this.sourcePathField, null, this.sourceFolderSelectButton, null, null));
        this.sourcePathField.setMaxWidth (Double.MAX_VALUE);

        this.sourceTabPane.getStyleClass ().add ("paddingLeftBottomRight");
        final ObservableList<Tab> tabs = this.sourceTabPane.getTabs ();
        for (final IDetector detector: this.detectors)
        {
            final Tab tab = new Tab (detector.getName (), detector.getEditPane ());
            tab.setClosable (false);
            tabs.add (tab);
        }
        setTabPaneLeftTabsHorizontal (this.sourceTabPane);

        // Rename CSV file section
        final BoxPanel srcRenamingCheckboxPanel = new BoxPanel (Orientation.HORIZONTAL, false);
        this.renameCheckbox = srcRenamingCheckboxPanel.createCheckBox ("@IDS_MAIN_RENAMING", "@IDS_MAIN_RENAMING_TOOLTIP");
        this.renameCheckbox.getStyleClass ().add ("paddingRight");
        this.renameCheckbox.setOnAction (event -> this.updateRenamingControls ());
        this.renameFilePathSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE"));
        this.renameFilePathSelectButton.setOnAction (event -> {

            final Optional<File> file = Functions.getFileFromUser (this.getStage (), true, Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_HEADER"), this.config, new FileChooser.ExtensionFilter (Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_DESCRIPTION"), Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_FILTER")));
            if (file.isPresent ())
                this.renameFilePathField.setText (file.get ().getAbsolutePath ());

        });
        final BorderPane sourceBottomPane = new BorderPane (this.renameFilePathField, null, this.renameFilePathSelectButton, null, srcRenamingCheckboxPanel.getPane ());
        sourceBottomPane.getStyleClass ().add ("paddingRenameBar");

        final BorderPane sourcePane = new BorderPane (this.sourceTabPane);
        sourcePane.setTop (sourceUpperPart.getPane ());
        sourcePane.setBottom (sourceBottomPane);

        /////////////////////////////////////////////////////////////////////////////
        // Destination pane

        final BorderPane destinationFolderPanel = new BorderPane (this.destinationPathField);

        this.destinationFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION"));
        this.destinationFolderSelectButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION_TOOLTIP")));
        this.destinationFolderSelectButton.setOnAction (event -> this.selectDestinationFolder ());
        destinationFolderPanel.setRight (this.destinationFolderSelectButton);

        final BoxPanel destinationUpperPart = new BoxPanel (Orientation.VERTICAL);
        final TitledSeparator destinationHeader = new TitledSeparator ("@IDS_MAIN_DESTINATION_HEADER");
        destinationHeader.setLabelFor (this.destinationPathField);
        destinationUpperPart.addComponent (destinationHeader);
        destinationUpperPart.addComponent (destinationFolderPanel);
        this.destinationPathField.setMaxWidth (Double.MAX_VALUE);

        this.destinationTabPane.getStyleClass ().add ("paddingLeftBottomRight");
        final ObservableList<Tab> destinationTabs = this.destinationTabPane.getTabs ();
        for (final ICreator creator: this.creators)
        {
            final Tab tab = new Tab (creator.getName (), creator.getEditPane ());
            tab.setClosable (false);
            destinationTabs.add (tab);
        }
        setTabPaneLeftTabsHorizontal (this.destinationTabPane);

        final BoxPanel bottomLeft = new BoxPanel (Orientation.HORIZONTAL);
        this.createFolderStructure = bottomLeft.createCheckBox ("@IDS_MAIN_CREATE_FOLDERS", "@IDS_MAIN_CREATE_FOLDERS_TOOLTIP");
        this.createFolderStructure.setSelected (true);
        this.addNewFiles = bottomLeft.createCheckBox ("@IDS_MAIN_ADD_NEW", "@IDS_MAIN_ADD_NEW_TOOLTIP");

        final BoxPanel bottomRight = new BoxPanel (Orientation.HORIZONTAL);
        this.enableDarkMode = bottomRight.createCheckBox ("@IDS_MAIN_ENABLE_DARK_MODE", "@IDS_MAIN_ENABLE_DARK_MODE_TOOLTIP");
        this.enableDarkMode.selectedProperty ().addListener ( (obs, wasSelected, isSelected) -> this.setDarkMode (isSelected.booleanValue ()));

        final BorderPane destinationPane = new BorderPane (this.destinationTabPane);
        destinationPane.setTop (destinationUpperPart.getPane ());
        destinationPane.setBottom (new BorderPane (null, null, bottomRight.getPane (), null, bottomLeft.getPane ()));

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

        this.cancelButton = setupButton (exButtonPanel, "Cancel", "@IDS_EXEC_CANCEL", "@IDS_EXEC_CANCEL_TOOLTIP");
        this.cancelButton.setOnAction (event -> this.cancelExecution ());
        this.closeButton = setupButton (exButtonPanel, "Close", "@IDS_EXEC_CLOSE", "@IDS_EXEC_CLOSE_TOOLTIP");
        this.closeButton.setOnAction (event -> this.closeExecution ());

        final Parent loggerComponent = this.loggingArea.getComponent ();
        this.executePane.setCenter (loggerComponent);
        this.executePane.setRight (exButtonPanel.getPane ());
        this.executePane.setVisible (false);

        final StackPane stackPane = new StackPane (this.mainPane, this.executePane);
        this.setCenterNode (stackPane);

        this.loadConfiguration ();

        this.updateTitle (null);
        this.sourcePathField.requestFocus ();

        this.configureTraversalManager ();
    }


    private void configureTraversalManager ()
    {
        this.traversalManager.add (this.sourcePathField);
        this.traversalManager.add (this.sourceFolderSelectButton);
        this.traversalManager.add (this.sourceTabPane);
        for (final Tab tab: this.sourceTabPane.getTabs ())
            if (tab.getContent () instanceof final Parent content)
                this.traversalManager.addChildren (content);

        this.traversalManager.add (this.destinationPathField);
        this.traversalManager.add (this.destinationFolderSelectButton);
        this.traversalManager.add (this.destinationTabPane);
        for (final Tab tab: this.destinationTabPane.getTabs ())
            if (tab.getContent () instanceof final Parent content)
                this.traversalManager.addChildren (content);

        this.traversalManager.add (this.convertButton);
        this.traversalManager.add (this.analyseButton);

        this.traversalManager.add (this.renameCheckbox);
        this.traversalManager.add (this.renameFilePathField);
        this.traversalManager.add (this.renameFilePathSelectButton);
        this.traversalManager.add (this.createFolderStructure);
        this.traversalManager.add (this.addNewFiles);
        this.traversalManager.add (this.enableDarkMode);

        this.traversalManager.add (this.cancelButton);
        this.traversalManager.add (this.closeButton);
        this.traversalManager.add (this.loggingArea.getComponent ());

        this.traversalManager.register (this.getStage ());
    }


    private void setDarkMode (final boolean isSelected)
    {
        final ObservableList<String> stylesheets = this.scene.getStylesheets ();
        final String stylesheet = this.startPath + "/css/Darkmode.css";
        if (isSelected)
        {
            if (!stylesheets.contains (stylesheet))
            {
                stylesheets.add (stylesheet);
                this.loggingArea.getComponent ().setBlendMode (BlendMode.OVERLAY);
            }
        }
        else
        {
            stylesheets.remove (stylesheet);
            this.loggingArea.getComponent ().setBlendMode (BlendMode.DARKEN);
        }
    }


    /**
     * Load configuration settings.
     */
    private void loadConfiguration ()
    {
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            final String sourcePath = this.config.getProperty (SOURCE_PATH + i);
            if (sourcePath == null || sourcePath.isBlank ())
                break;
            if (!this.sourcePathHistory.contains (sourcePath))
                this.sourcePathHistory.add (sourcePath);
        }
        this.sourcePathField.getItems ().addAll (this.sourcePathHistory);
        this.sourcePathField.setEditable (true);
        if (!this.sourcePathHistory.isEmpty ())
            this.sourcePathField.getEditor ().setText (this.sourcePathHistory.get (0));

        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            final String destinationPath = this.config.getProperty (DESTINATION_PATH + i);
            if (destinationPath == null || destinationPath.isBlank ())
                break;
            if (!this.destinationPathHistory.contains (destinationPath))
                this.destinationPathHistory.add (destinationPath);
        }
        this.destinationPathField.getItems ().addAll (this.destinationPathHistory);
        this.destinationPathField.setEditable (true);
        if (!this.destinationPathHistory.isEmpty ())
            this.destinationPathField.getEditor ().setText (this.destinationPathHistory.get (0));

        final String renamingFilePath = this.config.getProperty (RENAMING_CSV_FILE);
        if (renamingFilePath != null)
            this.renameFilePathField.setText (renamingFilePath);

        this.createFolderStructure.setSelected (this.config.getBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, true));
        this.addNewFiles.setSelected (this.config.getBoolean (DESTINATION_ADD_NEW_FILES, false));
        final boolean isDarkmode = this.config.getBoolean (ENABLE_DARK_MODE, false);
        this.enableDarkMode.setSelected (isDarkmode);
        this.setDarkMode (isDarkmode);
        this.renameCheckbox.setSelected (this.config.getBoolean (RENAMING_SOURCE_ENABLED, false));

        this.updateRenamingControls ();

        for (final IDetector detector: this.detectors)
            detector.loadSettings (this.config);
        for (final ICreator creator: this.creators)
            creator.loadSettings (this.config);

        final int sourceType = this.config.getInteger (SOURCE_TYPE, 0);
        this.sourceTabPane.getSelectionModel ().select (sourceType);
        final int destinationType = this.config.getInteger (DESTINATION_TYPE, 0);
        this.destinationTabPane.getSelectionModel ().select (destinationType);
    }


    /**
     * Save the configuration.
     */
    private void saveConfiguration ()
    {
        updateHistory (this.sourcePathField.getEditor ().getText (), this.sourcePathHistory);
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
            this.config.setProperty (SOURCE_PATH + i, this.sourcePathHistory.size () > i ? this.sourcePathHistory.get (i) : "");

        updateHistory (this.destinationPathField.getEditor ().getText (), this.destinationPathHistory);
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
            this.config.setProperty (DESTINATION_PATH + i, this.destinationPathHistory.size () > i ? this.destinationPathHistory.get (i) : "");

        this.config.setProperty (RENAMING_CSV_FILE, this.renameFilePathField.getText ());
        this.config.setBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, this.createFolderStructure.isSelected ());
        this.config.setBoolean (DESTINATION_ADD_NEW_FILES, this.addNewFiles.isSelected ());
        this.config.setBoolean (ENABLE_DARK_MODE, this.enableDarkMode.isSelected ());
        this.config.setBoolean (RENAMING_SOURCE_ENABLED, this.renameCheckbox.isSelected ());

        for (final IDetector detector: this.detectors)
            detector.saveSettings (this.config);
        for (final ICreator creator: this.creators)
            creator.saveSettings (this.config);

        final int sourceSelectedIndex = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (SOURCE_TYPE, sourceSelectedIndex);
        final int destinationSelectedIndex = this.destinationTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (DESTINATION_TYPE, destinationSelectedIndex);
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        for (final IDetector detector: this.detectors)
            detector.shutdown ();

        this.saveConfiguration ();
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
        this.collectedSources.clear ();

        if (!this.verifyFolders () || !this.verifyRenameFile ())
            return;

        final int selectedDetector = this.sourceTabPane.getSelectionModel ().getSelectedIndex ();
        if (selectedDetector < 0 || !this.detectors[selectedDetector].checkSettings () || !this.detectors[selectedDetector].validateParameters ())
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
        this.sourceFolder = new File (this.sourcePathField.getEditor ().getText ());
        if (!this.sourceFolder.exists () || !this.sourceFolder.isDirectory ())
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_DOES_NOT_EXIST", this.sourceFolder.getAbsolutePath ());
            this.sourcePathField.requestFocus ();
            return false;
        }
        this.sourcePathHistory.add (0, this.sourceFolder.getAbsolutePath ());

        if (this.onlyAnalyse)
            return true;

        // Check output folder
        this.outputFolder = new File (this.destinationPathField.getEditor ().getText ());
        if (!this.outputFolder.exists () && !this.outputFolder.mkdirs ())
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", this.outputFolder.getAbsolutePath ());
            this.destinationPathField.requestFocus ();
            return false;
        }
        if (!this.outputFolder.isDirectory ())
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_DESTINATION_NOT_A_FOLDER", this.outputFolder.getAbsolutePath ());
            this.destinationPathField.requestFocus ();
            return false;
        }
        this.destinationPathHistory.add (0, this.outputFolder.getAbsolutePath ());

        // Output folder must be empty or add new must be active
        if (!this.addNewFiles.isSelected ())
        {
            if (!isEmptyFolder(this.outputFolder.getPath()))
            {
                Functions.message ("@IDS_NOTIFY_FOLDER_MUST_BE_EMPTY");
                this.destinationPathField.requestFocus ();
                return false;
            }
        }

        return true;
    }


    /**
     * Set and check folder for existence.
     *
     * @return True if OK
     */
    private boolean verifyRenameFile ()
    {
        this.csvRenameFile.clear ();

        if (!this.renameCheckbox.isSelected ())
            return true;

        final String renamingCSVFile = this.renameFilePathField.getText ();
        if (renamingCSVFile.isBlank ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NO_FILE_SPECIFIED");
            this.renameFilePathField.requestFocus ();
            return false;
        }

        final File renamingCSV = new File (renamingCSVFile);
        if (!renamingCSV.exists ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_DOES_NOT_EXIST", renamingCSVFile);
            this.renameFilePathField.requestFocus ();
            return false;
        }

        if (!renamingCSV.canRead ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NOT_READABLE", renamingCSVFile);
            this.renameFilePathField.requestFocus ();
            return false;
        }

        if (!this.csvRenameFile.setRenameFile (renamingCSV))
        {
            this.renameFilePathField.requestFocus ();
            return false;
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

        this.applyRenaming (multisampleSource);
        this.applyDefaultEnvelope (multisampleSource);

        final ICreator creator = this.creators[selectedCreator];
        if (creator.wantsMultipleFiles ())
        {
            if (!this.onlyAnalyse)
                this.collectedSources.add (multisampleSource);
            this.log ("IDS_NOTIFY_COLLECTING", multisampleSource.getMappingName ());
            return;
        }

        this.log ("IDS_NOTIFY_MAPPING", multisampleSource.getMappingName ());

        if (this.onlyAnalyse)
            return;

        try
        {
            final boolean createStructure = this.createFolderStructure.isSelected ();
            final File multisampleOutputFolder = calcOutputFolder (this.outputFolder, multisampleSource.getSubPath (), createStructure);
            creator.create (multisampleOutputFolder, multisampleSource);
        }
        catch (final IOException | RuntimeException ex)
        {
            this.logError ("IDS_NOTIFY_SAVE_FAILED", ex);
        }
    }


    /**
     * Apply a volume envelopes if none are set based on the category of the multi-sample source.
     *
     * @param multisampleSource The multi-sample source
     */
    private void applyDefaultEnvelope (final IMultisampleSource multisampleSource)
    {
        final String category = multisampleSource.getMetadata ().getCategory ();
        boolean wasSet = false;
        final IEnvelope defaultEnvelope = DefaultEnvelope.getDefaultEnvelope (category);
        for (final IGroup layer: multisampleSource.getGroups ())
            for (final ISampleZone zone: layer.getSampleZones ())
            {
                final IEnvelope volumeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
                if (!volumeEnvelope.isSet ())
                {
                    volumeEnvelope.set (defaultEnvelope);
                    wasSet = true;
                }
            }
        if (wasSet)
            this.log ("IDS_NOTIFY_APPLY_DEFAULT_ENVELOPE", category);
    }


    /**
     * Applies the renaming of a IMultisampleSource according to the renaming table.
     *
     * @param multisampleSource the multi-sample source to be renamed.
     */
    private void applyRenaming (final IMultisampleSource multisampleSource)
    {
        if (this.csvRenameFile.isEmpty ())
            return;

        final String sourceName = multisampleSource.getName ();
        final String targetName = this.csvRenameFile.getMapping (sourceName);
        if (targetName != null)
        {
            this.log ("IDS_NOTIFY_RENAMING_SOURCE_TO", sourceName, targetName);
            multisampleSource.setName (targetName);
        }
        else
            this.log ("IDS_NOTIFY_RENAMING_NOT_DEFINED", sourceName);
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
        this.logError (throwable, true);
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final Throwable throwable, final boolean logExceptionStack)
    {
        String message = throwable.getMessage ();
        if (message == null)
            message = throwable.getClass ().getName ();
        if (logExceptionStack)
            this.loggingArea.notifyError (message, throwable);
        else
            this.loggingArea.notifyError (message);
    }


    /** {@inheritDoc} */
    @Override
    public void logText (final String text)
    {
        this.loggingArea.notify (text);
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
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", result.getAbsolutePath ()));
        }
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public void updateButtonStates (final boolean canClose)
    {
        Platform.runLater ( () -> {

            final Parent loggerComponent = this.loggingArea.getComponent ();
            this.cancelButton.setDisable (canClose);
            this.closeButton.setDisable (!canClose);
            if (!this.cancelButton.isDisabled ())
            {
                this.cancelButton.setDefaultButton (true);
                this.cancelButton.requestFocus ();
                loggerComponent.setAccessibleText (Functions.getMessage ("IDS_NOTIFY_PROCESSING"));
            }
            else
            {
                this.closeButton.setDefaultButton (true);
                this.closeButton.requestFocus ();
                loggerComponent.setAccessibleText (Functions.getMessage ("IDS_NOTIFY_FINISHED"));
            }

        });
    }


    private void selectSourcePath ()
    {
        final File currentSourcePath = new File (this.sourcePathField.getEditor ().getText ());
        if (currentSourcePath.exists () && currentSourcePath.isDirectory ())
            this.config.setActivePath (currentSourcePath);
        final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, "@IDS_MAIN_SELECT_SOURCE_HEADER");
        if (file.isPresent ())
            this.sourcePathField.getEditor ().setText (file.get ().getAbsolutePath ());
    }


    private void selectDestinationFolder ()
    {
        final File currentDestinationPath = new File (this.destinationPathField.getEditor ().getText ());
        if (currentDestinationPath.exists () && currentDestinationPath.isDirectory ())
            this.config.setActivePath (currentDestinationPath);
        final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, "@IDS_MAIN_SELECT_DESTINATION_HEADER");
        if (file.isPresent ())
            this.destinationPathField.getEditor ().setText (file.get ().getAbsolutePath ());
    }


    /** {@inheritDoc} */
    @Override
    public void finished (final boolean cancelled)
    {
        if (!this.collectedSources.isEmpty ())
        {
            final int selectedCreator = this.destinationTabPane.getSelectionModel ().getSelectedIndex ();
            if (selectedCreator < 0 || this.onlyAnalyse)
                return;

            try
            {
                this.creators[selectedCreator].create (this.outputFolder, this.collectedSources);
            }
            catch (final IOException | RuntimeException ex)
            {
                this.logError ("IDS_NOTIFY_SAVE_FAILED", ex);
            }

            return;
        }

        this.log (cancelled ? "IDS_NOTIFY_CANCELLED" : "IDS_NOTIFY_FINISHED");
    }


    private static Button setupButton (final BasePanel panel, final String iconName, final String labelName, final String mnemonic) throws EndApplicationException
    {
        Image icon;
        try
        {
            icon = Functions.iconFor ("de/mossgrabers/convertwithmoss/images/" + iconName + ".png");
        }
        catch (final IOException ex)
        {
            throw new EndApplicationException (ex);
        }
        final Button button = panel.createButton (icon, labelName, mnemonic);
        button.alignmentProperty ().set (Pos.CENTER_LEFT);
        button.graphicTextGapProperty ().set (12);
        return button;
    }


    private static void setTabPaneLeftTabsHorizontal (final TabPane tabPane)
    {
        tabPane.setSide (Side.LEFT);
        tabPane.setRotateGraphic (true);
        tabPane.setTabMinHeight (160); // Determines tab width. I know, its odd.
        tabPane.setTabMaxHeight (200);
        tabPane.getStyleClass ().add ("horizontal-tab-pane");

        for (final Tab tab: tabPane.getTabs ())
        {
            final Label l = new Label ("    ");
            l.setVisible (false);
            l.setMaxHeight (0);
            l.setPrefHeight (0);
            tab.setGraphic (l);

            Platform.runLater ( () -> rotateTabLabels (tab));
        }
    }


    private static void rotateTabLabels (final Tab tab)
    {
        // Get the "tab-container" node. This is what we want to rotate/shift for easy
        // left-alignment.
        final Parent parent = tab.getGraphic ().getParent ();
        if (parent == null)
        {
            Platform.runLater ( () -> rotateTabLabels (tab));
            return;
        }
        final Parent tabContainer = parent.getParent ();
        tabContainer.setRotate (90);
        // By default the display will originate from the center.
        // Applying a negative Y transformation will move it left.
        tabContainer.setTranslateY (-80);
    }


    private static void updateHistory (final String newItem, final List<String> history)
    {
        history.remove (newItem);
        history.add (0, newItem);
    }

    /**
     * Checks if folder is empty, ignores OS folder attributes or thumbnail files (eg. MAC: .DS_Store, WIN: Thumbs.db)
     * 
     * @param directoryPath Path of folder to check
     * @return true - directory is empty
     */
    private static boolean isEmptyFolder(String directoryPath) {
        try (Stream<Path> paths = Files.list(Path.of(directoryPath)))
        {
            long fileCount =
            paths
                .filter(path -> !Pattern.matches("^(\\.(DS_Store|desktop)|Thumbs.db)$", path.getFileName().toString()))
                // Ignore hidden files if desired
                //  .filter(path -> {try {return !Files.isHidden(path);} catch (Exception e1) {return false;} })
                .count();
            return fileCount == 0 ? true : false;
        }
        catch (Exception e)
        {
            // TODO Need static logging member function, singleton, service locator pattern or convert EndApplicationException
            //      to a RuntimeException and throw
            return false;
        }
    }

}
