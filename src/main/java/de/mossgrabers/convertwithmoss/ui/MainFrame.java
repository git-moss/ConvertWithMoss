// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import de.mossgrabers.convertwithmoss.core.ContentsEntry;
import de.mossgrabers.convertwithmoss.core.ConverterBackend;
import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.ICoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.ICreator;
import de.mossgrabers.convertwithmoss.core.detector.IDetector;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.AbstractFrame;
import de.mossgrabers.tools.ui.EndApplicationException;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.TraversalManager;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.control.loggerbox.LoggerBox;
import de.mossgrabers.tools.ui.control.loggerbox.LoggerBoxLogger;
import de.mossgrabers.tools.ui.panel.BasePanel;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import de.mossgrabers.tools.ui.panel.ButtonPanel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.util.Duration;


/**
 * The sample converter application.
 *
 * @author Jürgen Moßgraber
 */
public class MainFrame extends AbstractFrame implements INotifier
{
    private static final int       NUMBER_OF_DIRECTORIES               = 20;
    private static final int       MAXIMUM_NUMBER_OF_LOG_ENTRIES       = 100000;

    private static final String    ENABLE_DARK_MODE                    = "EnableDarkMode";
    private static final String    DESTINATION_CREATE_FOLDER_STRUCTURE = "DestinationCreateFolderStructure";
    private static final String    DESTINATION_ADD_NEW_FILES           = "DestinationAddNewFiles";
    private static final String    DESTINATION_PATH                    = "DestinationPath";
    private static final String    DESTINATION_FORMAT                  = "DestinationFormat";
    private static final String    DESTINATION_TYPE                    = "DestinationType";
    private static final String    SOURCE_PATH                         = "SourcePath";
    private static final String    SOURCE_FILE                         = "SourceFile";
    private static final String    SOURCE_BATCH_MODE                   = "SourceBatchMode";
    private static final String    SOURCE_TYPE                         = "SourceType";
    /** The prefix of the format which reads all disk images, whatever sampler wrote them. */
    private static final String    GENERIC_IMAGE_FORMAT                = "ISO";
    private static final String    PRESET_LIBRARY_FILENAME             = "PresetLibraryFilename";
    private static final String    PERFORMANCE_LIBRARY_FILENAME        = "PerformanceLibraryFilename";
    private static final String    PROCESSING_ENABLE                   = "ProcessingEnable";
    private static final String    PROCESSING_ENABLE_NORMALIZE         = "ProcessingEnableNormalize";
    private static final String    PROCESSING_MAKE_MONO                = "ProcessingMakeMono";
    private static final String    PROCESSING_ENABLE_TRIM_SAMPLE       = "ProcessingEnableTrimSample";
    private static final String    PROCESSING_MAX_NUMBER_OF_SAMPLES    = "ProcessingMaxNumberOfSamples";
    private static final String    PROCESSING_REDUCE_BIT_DEPTH         = "ProcessingReduceBitDepth";
    private static final String    PROCESSING_REDUCE_FREQUENCY         = "ProcessingReduceFrequency";
    private static final String    PROCESSING_ALWAYS_RESAMPLE          = "ProcessingAlwaysResample";
    private static final String    PROCESSING_LOOP_CROSSFADES          = "ProcessingLoopCrossfades";
    private static final String    PROCESSING_SNAP_LOOPS               = "ProcessingSnapLoops";
    private static final String    PROCESSING_TRANSPOSE                = "ProcessingTranspose";

    private static final int       DEST_TYPE_PRESET                    = 0;
    private static final int       DEST_TYPE_PRESET_LIBRARY            = 1;
    private static final int       DEST_TYPE_PERFORMANCE               = 2;
    private static final int       DEST_TYPE_PERFORMANCE_LIBRARY       = 3;

    private BorderPane             mainPane;
    private BorderPane             executePane;
    private final ComboBox<String> sourcePathField                     = new ComboBox<> ();
    private final ComboBox<String> destinationPathField                = new ComboBox<> ();

    private Button                 convertButton;
    private Button                 analyseButton;
    private Button                 closeButton;
    private Button                 cancelButton;
    private Button                 processingButton;
    private Button                 settingsButton;
    private Button                 sourceFolderSelectButton;
    private ToggleButton           batchModeButton;
    private Button                 contentsButton;
    private Button                 clearSelectionButton;
    private Label                  selectionLabel;
    private HBox                   selectionPane;
    private Button                 destinationFolderSelectButton;

    private final TabPane          destinationTypeTabPane              = new TabPane ();

    private final List<String>     sourcePathHistory                   = new ArrayList<> ();
    private final List<String>     sourceFileHistory                   = new ArrayList<> ();
    private final List<String>     destinationPathHistory              = new ArrayList<> ();

    private final LoggerBoxLogger  logger                              = new LoggerBoxLogger (MAXIMUM_NUMBER_OF_LOG_ENTRIES);
    private final LoggerBox        loggingArea                         = new LoggerBox (this.logger);
    private final TraversalManager traversalManager                    = new TraversalManager ();

    private FileWriter             logWriter;
    private boolean                combineWithPreviousMessage          = false;
    private TextField              presetLibraryFilename;
    private TextField              performanceLibraryFilename;
    private final ConverterBackend backend;

    private SettingsDialog         settingsDialog;
    private ProcessingDialog       processingDialog;
    private ContentsDialog         contentsDialog;
    private boolean                isContentsRun                       = false;
    private int                    numberOfFoundSources                = 0;
    private int                    numberOfSelectedSources             = 0;
    private final DetectSettings   detectSettings                      = new DetectSettings ();

    // The format of the last contents run, which is the one to read from when listening to a preset
    private IDetector<?>           contentsDetector;
    private boolean                contentsDetectPerformances          = false;
    // Keeps the last source which was listened to, so that playing it again is instant. Only one is
    // kept, since the samples of a preset of a disk image can be quite large
    private ContentsEntry          auditionEntry;
    private IMultisampleSource     auditionSource;
    private volatile boolean       isLoggingSuppressed                 = false;

    // Parameters of Settings dialog
    private boolean                addNewFiles;
    private boolean                enableDarkMode;
    private TaskPane               sourceTaskPane;
    private TaskPane               destinationTaskPane;


    /**
     * Constructor.
     *
     * @throws EndApplicationException Startup crash
     */
    public MainFrame () throws EndApplicationException
    {
        super ("de/mossgrabers/convertwithmoss", 1280, 840);

        this.backend = new ConverterBackend (this);
    }


    /** {@inheritDoc} */
    @Override
    public void initialise (final Stage stage, final Optional<String> baseTitleOptional) throws EndApplicationException
    {
        super.initialise (stage, baseTitleOptional, true, true, true);

        final Stage theStage = this.getStage ();
        this.settingsDialog = new SettingsDialog (theStage);
        this.processingDialog = new ProcessingDialog (theStage);
        this.contentsDialog = new ContentsDialog (theStage);

        // The main button panel
        final ButtonPanel upperButtonPanel = new ButtonPanel (Orientation.VERTICAL);
        this.convertButton = setupButton (upperButtonPanel, "Convert", "@IDS_MAIN_CONVERT", "@IDS_MAIN_CONVERT_TOOLTIP");
        this.convertButton.setDefaultButton (true);
        this.convertButton.setOnAction (_ -> this.execute (false));
        this.analyseButton = setupButton (upperButtonPanel, "Analyse", "@IDS_MAIN_ANALYSE", "@IDS_MAIN_ANALYSE_TOOLTIP");
        this.analyseButton.setOnAction (_ -> this.execute (true));

        final ButtonPanel lowerButtonPanel = new ButtonPanel (Orientation.VERTICAL);
        this.processingButton = setupButton (lowerButtonPanel, "Process", "@IDS_MAIN_PROCESSING", "@IDS_MAIN_PROCESSING_TOOLTIP");
        this.processingButton.setOnAction (_ -> this.openProcessing ());
        this.settingsButton = setupButton (lowerButtonPanel, "Settings", "@IDS_MAIN_SETTINGS", "@IDS_MAIN_SETTINGS_TOOLTIP");
        this.settingsButton.setOnAction (_ -> this.openSettings ());

        // Source pane
        //

        this.sourceFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_SOURCE"));
        this.sourceFolderSelectButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_SELECT_SOURCE_TOOLTIP")));
        this.sourceFolderSelectButton.setOnAction (_ -> this.selectSource ());

        // Batch converts all files of a folder, which is the default; switching it off converts one
        // single picked file and keeps a history of its own
        this.batchModeButton = new ToggleButton (Functions.getText ("@IDS_MAIN_SOURCE_BATCH"));
        this.batchModeButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_SOURCE_BATCH_TOOLTIP")));
        this.batchModeButton.setSelected (true);
        this.batchModeButton.setOnAction (_ -> this.toggleBatchMode ());

        this.contentsButton = new Button (Functions.getText ("@IDS_MAIN_CONTENTS"));
        this.contentsButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_CONTENTS_TOOLTIP")));
        this.contentsButton.setOnAction (_ -> this.openContents ());
        // Collapse the layout when the contents button is hidden
        this.contentsButton.managedProperty ().bind (this.contentsButton.visibleProperty ());
        this.contentsButton.focusTraversableProperty ().bind (this.contentsButton.visibleProperty ());

        final HBox sourceSelectButtons = new HBox (this.sourceFolderSelectButton, this.batchModeButton, this.contentsButton);
        sourceSelectButtons.getStyleClass ().add ("sourceSelectButtons");

        // Shows how many of the found presets are converted, as long as a selection is active
        this.selectionLabel = new Label ();
        this.clearSelectionButton = new Button (Functions.getText ("@IDS_MAIN_SELECTION_CLEAR"));
        this.clearSelectionButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_SELECTION_CLEAR_TOOLTIP")));
        this.clearSelectionButton.setOnAction (_ -> this.clearSelection ());
        this.selectionPane = new HBox (this.selectionLabel, this.clearSelectionButton);
        this.selectionPane.getStyleClass ().add ("sourceSelectButtons");
        // Centred below the source path, where it reads as a statement about that path
        this.selectionPane.setAlignment (Pos.CENTER);
        this.selectionPane.setMaxWidth (Double.MAX_VALUE);
        this.selectionPane.managedProperty ().bind (this.selectionPane.visibleProperty ());
        this.selectionLabel.setLabelFor (this.clearSelectionButton);
        this.clearSelectionButton.focusTraversableProperty ().bind (this.selectionPane.visibleProperty ());

        // Any change of the source discards the selection, which addresses the presets by their
        // position in the detection run of exactly that source
        this.sourcePathField.getEditor ().textProperty ().addListener ((_, _, _) -> this.clearSelection ());

        final BoxPanel sourceUpperPane = new BoxPanel (Orientation.VERTICAL);
        final TitledSeparator sourceTitle = new TitledSeparator (Functions.getText ("@IDS_MAIN_SOURCE_HEADER"));
        sourceTitle.setLabelFor (this.sourcePathField);
        sourceUpperPane.addComponent (sourceTitle);
        sourceUpperPane.addComponent (new BorderPane (this.sourcePathField, null, sourceSelectButtons, null, null));
        sourceUpperPane.addComponent (this.selectionPane);
        this.sourcePathField.setMaxWidth (Double.MAX_VALUE);
        this.updateSelectionPane ();

        this.sourceTaskPane = new TaskPane (this.backend.getDetectors (), true);
        // A selection addresses the sources by their position in the detection run, which changes
        // completely if another source format or another destination type is detected
        this.sourceTaskPane.formatList.getSelectionModel ().selectedItemProperty ().addListener ((_, _, _) -> {
            this.clearSelection ();
            this.updateContentsButton ();
        });
        this.destinationTypeTabPane.getSelectionModel ().selectedIndexProperty ().addListener ((_, _, _) -> this.clearSelection ());
        this.updateContentsButton ();
        final BorderPane sourcePane = new BorderPane ();
        sourcePane.setTop (sourceUpperPane.getPane ());
        sourcePane.setCenter (this.sourceTaskPane.formatPane);

        // Destination pane
        //

        final BorderPane destinationFolderPanel = new BorderPane (this.destinationPathField);

        this.destinationFolderSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION"));
        this.destinationFolderSelectButton.setTooltip (new Tooltip (Functions.getText ("@IDS_MAIN_SELECT_DESTINATION_TOOLTIP")));
        this.destinationFolderSelectButton.setOnAction (_ -> this.selectDestinationFolder ());
        destinationFolderPanel.setRight (this.destinationFolderSelectButton);

        final BoxPanel destinationUpperPart = new BoxPanel (Orientation.VERTICAL);
        final TitledSeparator destinationHeader = new TitledSeparator ("@IDS_MAIN_DESTINATION_HEADER");
        destinationHeader.setLabelFor (this.destinationPathField);
        destinationUpperPart.addComponent (destinationHeader);
        destinationUpperPart.addComponent (destinationFolderPanel);
        this.destinationPathField.setMaxWidth (Double.MAX_VALUE);

        this.destinationTaskPane = new TaskPane (this.backend.getCreators (), false);
        this.configureDestinationTypePane ();
        final BorderPane destinationPane = new BorderPane ();
        destinationPane.setTop (destinationUpperPart.getPane ());
        destinationPane.setCenter (this.destinationTaskPane.formatPane);
        destinationPane.setBottom (this.destinationTypeTabPane);
        this.destinationTypeTabPane.getStyleClass ().add ("paddingLeftBottomRight");

        // Tie it all together ...
        final HBox grid = new HBox ();
        grid.setFillHeight (true);
        grid.getChildren ().addAll (sourcePane, destinationPane);
        HBox.setHgrow (sourcePane, Priority.ALWAYS);
        HBox.setHgrow (destinationPane, Priority.ALWAYS);

        final BorderPane buttonColumn = new BorderPane ();
        final Pane upperButtonPane = upperButtonPanel.getPane ();
        final Pane lowerButtonPane = lowerButtonPanel.getPane ();
        upperButtonPane.setMinHeight (300);
        lowerButtonPane.setMinHeight (110);
        buttonColumn.setTop (upperButtonPane);
        buttonColumn.setBottom (lowerButtonPane);

        this.mainPane = new BorderPane ();
        this.mainPane.setCenter (grid);
        this.mainPane.setRight (buttonColumn);

        // Execution pane
        this.executePane = new BorderPane ();

        // The execution button panel
        final ButtonPanel exButtonPanel = new ButtonPanel (Orientation.VERTICAL);

        this.cancelButton = setupButton (exButtonPanel, "Cancel", "@IDS_EXEC_CANCEL", "@IDS_EXEC_CANCEL_TOOLTIP");
        this.cancelButton.setOnAction (_ -> this.cancelExecution ());
        this.closeButton = setupButton (exButtonPanel, "Close", "@IDS_EXEC_CLOSE", "@IDS_EXEC_CLOSE_TOOLTIP");
        this.closeButton.setOnAction (_ -> this.closeExecution ());

        this.executePane.setCenter (this.loggingArea);
        this.executePane.setRight (exButtonPanel.getPane ());
        this.executePane.setVisible (false);

        final StackPane stackPane = new StackPane (this.mainPane, this.executePane);
        this.setCenterNode (stackPane);

        this.loadConfiguration ();

        this.updateTitle (null);
        this.sourceTaskPane.search.requestFocus ();

        this.configureTraversalManager ();

        this.show ();
    }


    private static String formatFileEndings (final Set<String> fileEndings)
    {
        if (fileEndings.isEmpty ())
            return "";

        final StringBuilder sb = new StringBuilder (" (");
        boolean first = true;
        for (final String ending: fileEndings)
        {
            if (first)
                first = false;
            else
                sb.append (", ");
            sb.append ("*").append (ending);
        }
        return sb.append (')').toString ();
    }


    private void configureDestinationTypePane ()
    {
        final ObservableList<Tab> destinationTypeTabs = this.destinationTypeTabPane.getTabs ();

        // Add the preset destination type
        Tab tab = new Tab (Functions.getMessage ("IDS_DEST_TYPE_PRESET"), new BorderPane ());
        tab.setTooltip (new Tooltip (Functions.getMessage ("IDS_DEST_TYPE_PRESET_INFO")));
        tab.setClosable (false);
        destinationTypeTabs.add (tab);

        // Add the preset library destination type
        BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.presetLibraryFilename = panel.createField ("@IDS_COMBINE_LIBRARY_FILENAME");
        tab = new Tab (Functions.getMessage ("IDS_DEST_TYPE_LIBRARY"), panel.getPane ());
        tab.setTooltip (new Tooltip (Functions.getMessage ("IDS_DEST_TYPE_LIBRARY_INFO")));
        tab.setClosable (false);
        destinationTypeTabs.add (tab);

        // Add the performance destination type
        tab = new Tab (Functions.getMessage ("IDS_DEST_TYPE_PERFORMANCE"), new BorderPane ());
        tab.setTooltip (new Tooltip (Functions.getMessage ("IDS_DEST_TYPE_PERFORMANCE_INFO")));
        tab.setClosable (false);
        destinationTypeTabs.add (tab);

        // Add the performance library destination type
        panel = new BoxPanel (Orientation.VERTICAL);
        this.performanceLibraryFilename = panel.createField ("@IDS_COMBINE_LIBRARY_FILENAME");
        tab = new Tab (Functions.getMessage ("IDS_DEST_TYPE_PERFORMANCE_LIBRARY"), panel.getPane ());
        tab.setTooltip (new Tooltip (Functions.getMessage ("IDS_DEST_TYPE_PERFORMANCE_LIBRARY_INFO")));
        tab.setClosable (false);
        destinationTypeTabs.add (tab);
    }


    private void configureTraversalManager ()
    {
        this.traversalManager.add (this.sourcePathField);
        this.traversalManager.add (this.sourceFolderSelectButton);
        this.traversalManager.add (this.batchModeButton);
        this.traversalManager.add (this.contentsButton);
        this.traversalManager.add (this.clearSelectionButton);

        this.traversalManager.add (this.sourceTaskPane.search);
        this.traversalManager.add (this.sourceTaskPane.formatList);
        for (final Node content: this.sourceTaskPane.mappedPanes.values ())
        {
            content.focusTraversableProperty ().bind (content.visibleProperty ());
            if (content instanceof final Parent parent)
                this.traversalManager.addChildren (parent);
        }

        this.traversalManager.add (this.destinationPathField);
        this.traversalManager.add (this.destinationFolderSelectButton);

        this.traversalManager.add (this.destinationTaskPane.search);
        this.traversalManager.add (this.destinationTaskPane.formatList);
        for (final Node content: this.destinationTaskPane.mappedPanes.values ())
        {
            content.focusTraversableProperty ().bind (content.visibleProperty ());
            if (content instanceof final Parent parent)
                this.traversalManager.addChildren (parent);
        }

        this.traversalManager.add (this.destinationTypeTabPane);
        for (final Tab tab: this.destinationTypeTabPane.getTabs ())
            if (tab.getContent () instanceof final Parent content)
                this.traversalManager.addChildren (content);

        this.traversalManager.add (this.convertButton);
        this.traversalManager.add (this.analyseButton);
        this.traversalManager.add (this.processingButton);
        this.traversalManager.add (this.settingsButton);
        this.traversalManager.add (this.cancelButton);
        this.traversalManager.add (this.closeButton);
        this.traversalManager.add (this.loggingArea);

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
                this.loggingArea.setBlendMode (BlendMode.OVERLAY);
            }
        }
        else
        {
            stylesheets.remove (stylesheet);
            this.loggingArea.setBlendMode (BlendMode.DARKEN);
        }
    }


    /**
     * Load configuration settings.
     */
    private void loadConfiguration ()
    {
        // Source configuration
        //

        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            final String sourcePath = this.config.getProperty (SOURCE_PATH + i);
            if (sourcePath == null || sourcePath.isBlank ())
                break;
            if (!this.sourcePathHistory.contains (sourcePath))
                this.sourcePathHistory.add (sourcePath);
        }
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            final String sourceFile = this.config.getProperty (SOURCE_FILE + i);
            if (sourceFile == null || sourceFile.isBlank ())
                break;
            if (!this.sourceFileHistory.contains (sourceFile))
                this.sourceFileHistory.add (sourceFile);
        }
        this.batchModeButton.setSelected (this.config.getBoolean (SOURCE_BATCH_MODE, true));
        final List<String> activeHistory = this.activeSourceHistory ();
        this.sourcePathField.getItems ().addAll (activeHistory);
        this.sourcePathField.setEditable (true);
        if (!activeHistory.isEmpty ())
            this.sourcePathField.getEditor ().setText (activeHistory.get (0));

        for (final IDetector<?> detector: this.backend.getDetectors ())
            detector.getSettings ().loadSettings (this.config);
        final int sourceFormat = this.config.getInteger (SOURCE_TYPE, 0);
        this.sourceTaskPane.setSelectedFormat (sourceFormat);

        // Destination Configuration
        //

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

        for (final ICreator<?> creator: this.backend.getCreators ())
            creator.getSettings ().loadSettings (this.config);
        final int destinationFormat = this.config.getInteger (DESTINATION_FORMAT, 0);
        this.destinationTaskPane.setSelectedFormat (destinationFormat);

        final int destinationType = this.config.getInteger (DESTINATION_TYPE, DEST_TYPE_PRESET);
        this.destinationTypeTabPane.getSelectionModel ().select (destinationType);

        this.presetLibraryFilename.setText (this.config.getProperty (PRESET_LIBRARY_FILENAME, ""));
        this.performanceLibraryFilename.setText (this.config.getProperty (PERFORMANCE_LIBRARY_FILENAME, ""));

        // Processing
        //

        this.detectSettings.enableProcessing = this.config.getBoolean (PROCESSING_ENABLE, false);
        this.detectSettings.enableNormalize = this.config.getBoolean (PROCESSING_ENABLE_NORMALIZE, false);
        this.detectSettings.enableMakeMono = this.config.getBoolean (PROCESSING_MAKE_MONO, false);
        this.detectSettings.enableTrimSample = this.config.getBoolean (PROCESSING_ENABLE_TRIM_SAMPLE, false);
        this.detectSettings.maxNumberOfSamples = this.config.getInteger (PROCESSING_MAX_NUMBER_OF_SAMPLES, -1);
        this.detectSettings.reduceBitDepth = this.config.getInteger (PROCESSING_REDUCE_BIT_DEPTH, 0);
        this.detectSettings.reduceFrequency = this.config.getInteger (PROCESSING_REDUCE_FREQUENCY, 0);
        this.detectSettings.alwaysResample = this.config.getBoolean (PROCESSING_ALWAYS_RESAMPLE, false);
        this.detectSettings.loopCrossfades = this.config.getInteger (PROCESSING_LOOP_CROSSFADES, 0);
        this.detectSettings.snapLoopsToZero = this.config.getBoolean (PROCESSING_SNAP_LOOPS, false);
        this.detectSettings.transposeSemitones = this.config.getInteger (PROCESSING_TRANSPOSE, 0);

        // Options
        //

        this.detectSettings.createFolderStructure = this.config.getBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, true);
        this.addNewFiles = this.config.getBoolean (DESTINATION_ADD_NEW_FILES, false);
        this.enableDarkMode = this.config.getBoolean (ENABLE_DARK_MODE, false);

        this.setDarkMode (this.enableDarkMode);
    }


    /**
     * Save the configuration.
     */
    private void saveConfiguration ()
    {
        updateHistory (this.sourcePathField.getEditor ().getText (), this.activeSourceHistory ());
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
            this.config.setProperty (SOURCE_PATH + i, this.sourcePathHistory.size () > i ? this.sourcePathHistory.get (i) : "");
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
            this.config.setProperty (SOURCE_FILE + i, this.sourceFileHistory.size () > i ? this.sourceFileHistory.get (i) : "");
        this.config.setBoolean (SOURCE_BATCH_MODE, this.batchModeButton.isSelected ());

        updateHistory (this.destinationPathField.getEditor ().getText (), this.destinationPathHistory);
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
            this.config.setProperty (DESTINATION_PATH + i, this.destinationPathHistory.size () > i ? this.destinationPathHistory.get (i) : "");

        for (final IDetector<?> detector: this.backend.getDetectors ())
            detector.getSettings ().saveSettings (this.config);
        for (final ICreator<?> creator: this.backend.getCreators ())
            creator.getSettings ().saveSettings (this.config);

        final int sourceSelectedIndex = this.sourceTaskPane.getSelectedFormat ();
        this.config.setInteger (SOURCE_TYPE, sourceSelectedIndex);
        final int destinationSelectedIndex = this.destinationTaskPane.getSelectedFormat ();
        this.config.setInteger (DESTINATION_FORMAT, destinationSelectedIndex);

        final int destinationTypeSelectedIndex = this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex ();
        this.config.setInteger (DESTINATION_TYPE, destinationTypeSelectedIndex);

        this.config.setProperty (PRESET_LIBRARY_FILENAME, this.presetLibraryFilename.getText ());
        this.config.setProperty (PERFORMANCE_LIBRARY_FILENAME, this.performanceLibraryFilename.getText ());

        //
        // Processing

        this.config.setBoolean (PROCESSING_ENABLE, this.detectSettings.enableProcessing);
        this.config.setBoolean (PROCESSING_ENABLE_NORMALIZE, this.detectSettings.enableNormalize);
        this.config.setBoolean (PROCESSING_MAKE_MONO, this.detectSettings.enableMakeMono);
        this.config.setBoolean (PROCESSING_ENABLE_TRIM_SAMPLE, this.detectSettings.enableTrimSample);
        this.config.setInteger (PROCESSING_MAX_NUMBER_OF_SAMPLES, this.detectSettings.maxNumberOfSamples);
        this.config.setInteger (PROCESSING_REDUCE_BIT_DEPTH, this.detectSettings.reduceBitDepth);
        this.config.setInteger (PROCESSING_REDUCE_FREQUENCY, this.detectSettings.reduceFrequency);
        this.config.setBoolean (PROCESSING_ALWAYS_RESAMPLE, this.detectSettings.alwaysResample);
        this.config.setInteger (PROCESSING_LOOP_CROSSFADES, this.detectSettings.loopCrossfades);
        this.config.setBoolean (PROCESSING_SNAP_LOOPS, this.detectSettings.snapLoopsToZero);
        this.config.setInteger (PROCESSING_TRANSPOSE, this.detectSettings.transposeSemitones);

        //
        // Options

        this.config.setBoolean (DESTINATION_CREATE_FOLDER_STRUCTURE, this.detectSettings.createFolderStructure);
        this.config.setBoolean (DESTINATION_ADD_NEW_FILES, this.addNewFiles);
        this.config.setBoolean (ENABLE_DARK_MODE, this.enableDarkMode);
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        try
        {
            for (final IDetector<?> detector: this.backend.getDetectors ())
                detector.shutdown ();

            this.saveConfiguration ();
            super.exit ();
        }
        catch (final RuntimeException ex)
        {
            Functions.error ("@IDS_ERR_COULD_NOT_STORE_CONFIG", ex);
        }

        Platform.exit ();
    }


    /**
     * Open the settings dialog.
     */
    private void openSettings ()
    {
        this.settingsDialog.createFolderStructureCheckbox.setSelected (this.detectSettings.createFolderStructure);
        this.settingsDialog.addNewFilesCheckbox.setSelected (this.addNewFiles);
        this.settingsDialog.enableDarkModeCheckbox.setSelected (this.enableDarkMode);

        this.settingsDialog.display ().thenAccept (result -> {
            if (result.booleanValue ())
            {
                this.detectSettings.createFolderStructure = this.settingsDialog.createFolderStructureCheckbox.isSelected ();
                this.addNewFiles = this.settingsDialog.addNewFilesCheckbox.isSelected ();
                this.enableDarkMode = this.settingsDialog.enableDarkModeCheckbox.isSelected ();

                this.setDarkMode (this.enableDarkMode);
            }
        });
    }


    /**
     * Open the processing dialog.
     */
    private void openProcessing ()
    {
        this.processingDialog.enableProcessingCheckbox.setSelected (this.detectSettings.enableProcessing);
        this.processingDialog.normalizeCheckbox.setSelected (this.detectSettings.enableNormalize);
        this.processingDialog.makeMonoCheckbox.setSelected (this.detectSettings.enableMakeMono);
        this.processingDialog.trimSample.setSelected (this.detectSettings.enableTrimSample);
        this.processingDialog.maxSamplesField.setText (this.detectSettings.maxNumberOfSamples < 0 ? "" : Integer.toString (this.detectSettings.maxNumberOfSamples));
        this.processingDialog.selectBitDepth (this.detectSettings.reduceBitDepth);
        this.processingDialog.selectFrequency (this.detectSettings.reduceFrequency);
        this.processingDialog.alwaysResampleCheckbox.setSelected (this.detectSettings.alwaysResample);
        this.processingDialog.selectLoopCrossfades (this.detectSettings.loopCrossfades);
        this.processingDialog.snapLoopsCheckbox.setSelected (this.detectSettings.snapLoopsToZero);
        this.processingDialog.selectTranspose (this.detectSettings.transposeSemitones);

        this.processingDialog.display ().thenAccept (result -> {
            if (result.booleanValue ())
            {
                this.detectSettings.enableProcessing = this.processingDialog.enableProcessingCheckbox.isSelected ();
                this.detectSettings.enableNormalize = this.processingDialog.normalizeCheckbox.isSelected ();
                this.detectSettings.enableMakeMono = this.processingDialog.makeMonoCheckbox.isSelected ();
                this.detectSettings.enableTrimSample = this.processingDialog.trimSample.isSelected ();
                final String maxNumberText = this.processingDialog.maxSamplesField.getText ();
                this.detectSettings.maxNumberOfSamples = maxNumberText.isEmpty () || maxNumberText.isBlank () ? -1 : Integer.parseInt (maxNumberText);
                this.detectSettings.reduceBitDepth = this.processingDialog.getBitDepth ();
                this.detectSettings.reduceFrequency = this.processingDialog.getFrequency ();
                this.detectSettings.alwaysResample = this.processingDialog.alwaysResampleCheckbox.isSelected ();
                this.detectSettings.loopCrossfades = this.processingDialog.getLoopCrossfades ();
                this.detectSettings.snapLoopsToZero = this.processingDialog.snapLoopsCheckbox.isSelected ();
                this.detectSettings.transposeSemitones = this.processingDialog.getTranspose ();
            }
        });
    }


    /**
     * Execute the conversion.
     *
     * @param onlyAnalyse Do not create output files if true
     */
    private void execute (final boolean onlyAnalyse)
    {
        if (!this.verifyFolders ())
            return;

        final int selectedDetector = this.sourceTaskPane.getSelectedFormat ();
        final int selectedCreator = this.destinationTaskPane.getSelectedFormat ();
        if (selectedDetector < 0)
        {
            Functions.message ("@IDS_NOTIFY_SELECT_SOURCE_FORMAT");
            return;
        }
        if (selectedCreator < 0)
        {
            Functions.message ("@IDS_NOTIFY_SELECT_DESTINATION_FORMAT");
            return;
        }
        final IDetector<?> detector = this.backend.getDetectors ().get (selectedDetector);
        final ICreator<?> creator = this.backend.getCreators ().get (selectedCreator);
        if (!detector.getSettings ().checkSettingsUI (this) || !creator.getSettings ().checkSettingsUI (this) || this.detectSettings.enableProcessing && !creator.checkProcessingCompatibility (this.detectSettings))
            return;

        this.clearLog ();

        this.mainPane.setVisible (false);
        this.executePane.setVisible (true);

        final int selectedType = this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex ();
        final boolean detectPerformances = selectedType == DEST_TYPE_PERFORMANCE || selectedType == DEST_TYPE_PERFORMANCE_LIBRARY;

        this.detectSettings.libraryName = (detectPerformances ? this.performanceLibraryFilename : this.presetLibraryFilename).getText ().trim ();
        this.detectSettings.wantsMultipleFiles = detectPerformances ? this.wantsMultiplePerformanceFiles () : this.wantsMultiplePresetFiles ();
        Platform.runLater (() -> this.backend.detect (detector, creator, this.detectSettings, detectPerformances, onlyAnalyse));
    }


    /**
     * Detect all sources of the source and show them for selection, so that only some of them can
     * be converted. No files are written.
     */
    private void openContents ()
    {
        // The source is required but the output folder is not, since nothing is written. The
        // source is read exactly as the conversion reads it: a folder in batch mode and the one
        // picked file otherwise
        if (!this.applySourcePath ())
            return;

        final int selectedDetector = this.sourceTaskPane.getSelectedFormat ();
        if (selectedDetector < 0)
        {
            Functions.message ("@IDS_NOTIFY_SELECT_SOURCE_FORMAT");
            return;
        }
        final IDetector<?> detector = this.backend.getDetectors ().get (selectedDetector);
        if (!detector.getSettings ().checkSettingsUI (this))
            return;

        this.loggingArea.clear ();
        this.loggingArea.autoScrollToTailProperty ().set (true);
        this.mainPane.setVisible (false);
        this.executePane.setVisible (true);
        this.isContentsRun = true;

        final int selectedType = this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex ();
        final boolean detectPerformances = selectedType == DEST_TYPE_PERFORMANCE || selectedType == DEST_TYPE_PERFORMANCE_LIBRARY;

        // Remember what is read, so that a preset can be read again to listen to it
        this.contentsDetector = detector;
        this.contentsDetectPerformances = detectPerformances;
        this.clearAuditionCache ();

        Platform.runLater (() -> this.backend.detectContents (detector, this.detectSettings, detectPerformances));
    }


    /**
     * Show the sources which were found by the contents run for selection.
     */
    private void showContentsDialog ()
    {
        this.closeExecution ();

        final List<ContentsEntry> entries = this.backend.getContentsEntries ();
        if (entries.isEmpty ())
        {
            Functions.message ("@IDS_NOTIFY_CONTENTS_NOTHING_FOUND");
            return;
        }

        this.contentsDialog.setEntries (entries, this::readAuditionSource);
        this.contentsDialog.display ().thenAccept (result -> {
            this.contentsDialog.stopAudition ();
            this.clearAuditionCache ();
            if (!result.booleanValue ())
                return;
            this.detectSettings.selectedSources.clear ();
            // Selecting everything is the same as having no selection at all
            if (!this.contentsDialog.areAllSelected ())
                for (final ContentsEntry entry: this.contentsDialog.getSelectedEntries ())
                    this.detectSettings.selectedSources.computeIfAbsent (entry.getSourceFile (), _ -> new HashSet<> ()).add (Integer.valueOf (entry.getIndexInFile ()));
            this.numberOfSelectedSources = this.contentsDialog.getSelectedEntries ().size ();
            this.numberOfFoundSources = entries.size ();
            this.updateSelectionPane ();
        });
    }


    /**
     * Read the source of one entry of the contents dialog again, so that it can be listened to. The
     * entries only hold the information to display, since keeping all sources of e.g. a disk image
     * would need far too much memory. Called from a background thread of the dialog.
     *
     * @param entry The entry to read
     * @return The source, null if it could not be read
     */
    private synchronized IMultisampleSource readAuditionSource (final ContentsEntry entry)
    {
        if (entry.equals (this.auditionEntry))
            return this.auditionSource;
        if (this.contentsDetector == null)
            return null;

        // The detector reports everything it reads, which would end up in a message dialog since
        // the log is not shown while the contents dialog is
        this.isLoggingSuppressed = true;
        final Optional<IMultisampleSource> source;
        try
        {
            source = this.contentsDetector.readSource (this.detectSettings.sourceFolder, entry.getSourceFile (), entry.getIndexInFile (), this.contentsDetectPerformances);
        }
        finally
        {
            this.isLoggingSuppressed = false;
        }

        this.auditionEntry = entry;
        this.auditionSource = source.orElse (null);
        return this.auditionSource;
    }


    /**
     * Forget the source which was listened to last, so that its samples can be freed.
     */
    private synchronized void clearAuditionCache ()
    {
        this.auditionEntry = null;
        this.auditionSource = null;
    }


    /**
     * Only show the contents button for a source format where one file can contain more than one
     * preset. For all other formats one file simply is one preset, so there is nothing to select.
     */
    private void updateContentsButton ()
    {
        final int selectedDetector = this.sourceTaskPane.getSelectedFormat ();
        this.contentsButton.setVisible (selectedDetector >= 0 && ConverterBackend.containsMultiplePresets (this.backend.getDetectors ().get (selectedDetector)));
    }


    /**
     * Discard the selection of presets and convert everything of the source again.
     */
    private void clearSelection ()
    {
        if (this.detectSettings.selectedSources.isEmpty ())
            return;
        this.detectSettings.selectedSources.clear ();
        this.numberOfSelectedSources = 0;
        this.updateSelectionPane ();
    }


    /**
     * Show how many of the found presets the conversion is narrowed down to, if it is.
     */
    private void updateSelectionPane ()
    {
        this.selectionPane.setVisible (!this.detectSettings.selectedSources.isEmpty ());
        if (this.detectSettings.selectedSources.isEmpty ())
            return;
        final String text = Functions.getMessage ("IDS_MAIN_SELECTION_PRESETS", Integer.toString (this.numberOfSelectedSources), Integer.toString (this.numberOfFoundSources));
        this.selectionLabel.setText (text);
        this.selectionPane.setAccessibleText (text);
    }


    /**
     * Cancel button was pressed.
     */
    private void cancelExecution ()
    {
        this.backend.cancelExecution ();
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
        if (!this.applySourcePath ())
            return false;
        this.activeSourceHistory ().add (0, this.sourcePathField.getEditor ().getText ());

        // Check output folder
        this.detectSettings.outputFolder = new File (this.destinationPathField.getEditor ().getText ());
        if (!this.detectSettings.outputFolder.exists () && !this.detectSettings.outputFolder.mkdirs ())
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", this.detectSettings.outputFolder.getAbsolutePath ());
            this.destinationPathField.requestFocus ();
            return false;
        }
        if (!this.detectSettings.outputFolder.isDirectory ())
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_DESTINATION_NOT_A_FOLDER", this.detectSettings.outputFolder.getAbsolutePath ());
            this.destinationPathField.requestFocus ();
            return false;
        }
        this.destinationPathHistory.add (0, this.detectSettings.outputFolder.getAbsolutePath ());

        // Output folder must be empty or add new must be active
        return this.addNewFiles || this.isEmptyFolder (this.detectSettings.outputFolder.getPath ());
    }


    /**
     * Take the entered source over into the detection settings. A file can be entered as well -
     * which is what switching Batch off does - and is then the only file to convert.
     *
     * @return True if the source exists
     */
    private boolean applySourcePath ()
    {
        final File sourcePath = new File (this.sourcePathField.getEditor ().getText ());
        this.detectSettings.sourceFiles.clear ();
        if (sourcePath.isFile ())
        {
            this.detectSettings.sourceFolder = sourcePath.getAbsoluteFile ().getParentFile ();
            this.detectSettings.sourceFiles.add (sourcePath.getAbsoluteFile ());
            return true;
        }
        if (!sourcePath.isDirectory ())
        {
            Functions.message ("@IDS_NOTIFY_SOURCE_DOES_NOT_EXIST", sourcePath.getAbsolutePath ());
            this.sourcePathField.requestFocus ();
            return false;
        }
        this.detectSettings.sourceFolder = sourcePath;
        return true;
    }


    /**
     * Get the path history of the mode which is currently active.
     *
     * @return The folder history in batch mode and the single file history otherwise
     */
    private List<String> activeSourceHistory ()
    {
        return this.batchModeButton.isSelected () ? this.sourcePathHistory : this.sourceFileHistory;
    }


    private boolean wantsMultiplePresetFiles ()
    {
        return this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex () == DEST_TYPE_PRESET_LIBRARY;
    }


    private boolean wantsMultiplePerformanceFiles ()
    {
        return this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex () == DEST_TYPE_PERFORMANCE_LIBRARY;
    }


    private void clearLog ()
    {
        this.loggingArea.clear ();
        this.loggingArea.autoScrollToTailProperty ().set (true);

        try
        {
            this.logWriter = new FileWriter (new File (this.detectSettings.outputFolder, "ConvertWithMoss.log"));
        }
        catch (final IOException ex)
        {
            this.logger.error (Functions.getMessage ("@IDS_NOTIFY_ERR_NO_LOG_FILE", ex.getLocalizedMessage ()));
            this.logWriter = null;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void log (final String messageID, final String... replaceStrings)
    {
        this.logText (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logText (final String text)
    {
        if (this.isLoggingSuppressed)
        {
            this.logToFile (text);
            return;
        }

        final boolean combine = this.combineWithPreviousMessage;
        this.combineWithPreviousMessage = !text.endsWith ("\n");
        if (this.executePane.isVisible ())
            this.logger.info (text, combine);
        else
            Functions.message (text);
        this.logToFile (text);
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final String... replaceStrings)
    {
        this.logErrorText (Functions.getMessage (messageID, replaceStrings));
    }


    /** {@inheritDoc} */
    @Override
    public void logError (final String messageID, final Throwable throwable)
    {
        throwable.printStackTrace ();
        this.logErrorText (Functions.getMessage (messageID, throwable));
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
        {
            final StringBuilder sb = new StringBuilder (message).append ('\n');
            final StringWriter sw = new StringWriter ();
            final PrintWriter pw = new PrintWriter (sw);
            throwable.printStackTrace (pw);
            sb.append (sw.toString ()).append ('\n');
            message = sb.toString ();
        }
        this.logErrorText (message);
    }


    private void logErrorText (final String message)
    {
        if (this.isLoggingSuppressed)
        {
            this.logToFile (message);
            return;
        }

        if (this.executePane.isVisible ())
            this.logger.error (message);
        else
            Functions.error (message, null);
        this.logToFile (message);
    }


    private void logToFile (final String message)
    {
        if (this.logWriter != null)
            try
            {
                this.logWriter.append (message);
            }
            catch (final IOException _)
            {
                // Ignore
            }
    }


    /** {@inheritDoc} */
    @Override
    public void updateButtonStates (final boolean canClose)
    {
        Platform.runLater (() -> {

            this.cancelButton.setDisable (canClose);
            this.closeButton.setDisable (!canClose);
            if (!this.cancelButton.isDisabled ())
            {
                this.cancelButton.setDefaultButton (true);
                this.cancelButton.requestFocus ();
                this.loggingArea.setAccessibleText (Functions.getMessage ("IDS_NOTIFY_PROCESSING"));
            }
            else
            {
                this.closeButton.setDefaultButton (true);
                this.closeButton.requestFocus ();
                this.loggingArea.setAccessibleText (Functions.getMessage ("IDS_NOTIFY_FINISHED"));
            }

            if (canClose && this.logWriter != null)
            {
                try
                {
                    this.logWriter.close ();
                }
                catch (final IOException _)
                {
                    // Ignore
                }
                this.logWriter = null;
            }

        });
    }


    /**
     * Select the source, which is a folder in batch mode and a single file otherwise.
     */
    private void selectSource ()
    {
        if (this.batchModeButton.isSelected ())
            this.selectSourceFolder ();
        else
            this.selectSourceFile ();
    }


    private void selectSourceFolder ()
    {
        this.setActiveSourcePath ();
        final Optional<File> file = Functions.getFolderFromUser (this.getStage (), this.config, "@IDS_MAIN_SELECT_SOURCE_HEADER");
        if (file.isEmpty ())
            return;
        this.sourcePathField.getEditor ().setText (file.get ().getAbsolutePath ());
    }


    /**
     * Select one single source file to convert. The dialog offers the file endings of every source
     * format so that the format can be switched right in it; the format list follows both that
     * choice and the ending of the picked file.
     */
    private void selectSourceFile ()
    {
        this.setActiveSourcePath ();

        final FileChooser chooser = new FileChooser ();
        chooser.setTitle (Functions.getText ("@IDS_MAIN_SELECT_SOURCE_FILE_HEADER"));
        final String activePath = this.config.getActivePath ();
        if (activePath != null)
        {
            final File activeFolder = new File (activePath);
            if (activeFolder.isDirectory ())
                chooser.setInitialDirectory (activeFolder);
        }

        final Map<ExtensionFilter, Integer> formatOfFilter = new HashMap<> ();
        final ExtensionFilter preSelected = this.fillSourceFileFilters (chooser, formatOfFilter);
        if (preSelected != null)
            chooser.setSelectedExtensionFilter (preSelected);

        final File file = chooser.showOpenDialog (this.getStage ());
        if (file == null)
            return;
        this.config.setActivePath (file.getParentFile ());
        this.sourcePathField.getEditor ().setText (file.getAbsolutePath ());

        // A format which was picked in the dialog wins, otherwise the file ending decides
        final ExtensionFilter selectedFilter = chooser.getSelectedExtensionFilter ();
        final Integer pickedFormat = selectedFilter == preSelected ? null : formatOfFilter.get (selectedFilter);
        final int format = pickedFormat == null ? findSourceFormat (this.backend.getDetectors (), file) : pickedFormat.intValue ();
        if (format >= 0)
            this.sourceTaskPane.setSelectedFormat (format);
    }


    /**
     * Add one file filter per source format to the given file chooser plus a filter for all files.
     *
     * @param chooser The file chooser to fill
     * @param formatOfFilter Where to collect the source format index of each added filter
     * @return The filter of the currently selected source format or null if it has none
     */
    private ExtensionFilter fillSourceFileFilters (final FileChooser chooser, final Map<ExtensionFilter, Integer> formatOfFilter)
    {
        final List<IDetector<?>> detectors = this.backend.getDetectors ();
        final int selectedFormat = this.sourceTaskPane.getSelectedFormat ();
        ExtensionFilter preSelected = null;
        for (int index = 0; index < detectors.size (); index++)
        {
            final IDetector<?> detector = detectors.get (index);
            final List<String> extensions = collectExtensions (detector);
            if (extensions.isEmpty ())
                continue;
            final ExtensionFilter filter = new ExtensionFilter (detector.getName (), extensions);
            chooser.getExtensionFilters ().add (filter);
            formatOfFilter.put (filter, Integer.valueOf (index));
            if (index == selectedFormat)
                preSelected = filter;
        }
        chooser.getExtensionFilters ().add (new ExtensionFilter (Functions.getText ("@IDS_MAIN_SELECT_SOURCE_FILES_ALL"), "*.*"));
        return preSelected;
    }


    /**
     * Get the file endings of a detector as file dialog extensions. A file ending might be a full
     * file name (e.g. 'PADCONF.BIN'), which needs to be reduced to its extension.
     *
     * @param detector The detector
     * @return The extensions, empty if the detector accepts any file
     */
    private static List<String> collectExtensions (final IDetector<?> detector)
    {
        final Set<String> extensions = new TreeSet<> ();
        for (final String fileEnding: detector.getFileEndings ())
        {
            final int dotPosition = fileEnding.lastIndexOf ('.');
            extensions.add (dotPosition < 0 ? "*." + fileEnding : "*" + fileEnding.substring (dotPosition));
        }
        return new ArrayList<> (extensions);
    }


    /**
     * Find the source format which reads the given file, judged by its file ending. Disk images are
     * claimed by several detectors - the E-mu ones read their own images - so the generic ISO/IMG
     * format wins there, since it hands the image to whichever of them can read it.
     *
     * @param detectors The available source formats
     * @param file The file to find the format for
     * @return The index of the format or -1 if no format claims the ending
     */
    private static int findSourceFormat (final List<IDetector<?>> detectors, final File file)
    {
        final String name = file.getName ().toLowerCase (Locale.US);
        int match = -1;
        for (int index = 0; index < detectors.size (); index++)
        {
            final IDetector<?> detector = detectors.get (index);
            final Set<String> fileEndings = detector.getFileEndings ();
            if (fileEndings.isEmpty ())
                continue;
            for (final String fileEnding: fileEndings)
                if (name.endsWith (fileEnding.toLowerCase (Locale.US)))
                {
                    if (GENERIC_IMAGE_FORMAT.equals (detector.getPrefix ()))
                        return index;
                    if (match < 0)
                        match = index;
                    break;
                }
        }
        return match;
    }


    /**
     * Switch between converting all files of a folder and converting one single file. Each mode has
     * a path history of its own, which is exchanged here.
     */
    private void toggleBatchMode ()
    {
        final boolean isBatchMode = this.batchModeButton.isSelected ();
        // The entered path belongs to the mode which is being left
        updateHistory (this.sourcePathField.getEditor ().getText (), isBatchMode ? this.sourceFileHistory : this.sourcePathHistory);

        final List<String> history = isBatchMode ? this.sourcePathHistory : this.sourceFileHistory;
        this.sourcePathField.getItems ().setAll (history);
        this.sourcePathField.getEditor ().setText (history.isEmpty () ? "" : history.get (0));
        this.clearSelection ();
    }


    /**
     * Set the folder of the currently entered source path as the folder to open the selection
     * dialogs in. The parent folder is used if a file is currently entered.
     */
    private void setActiveSourcePath ()
    {
        File currentSourcePath = new File (this.sourcePathField.getEditor ().getText ());
        if (currentSourcePath.isFile ())
            currentSourcePath = currentSourcePath.getAbsoluteFile ().getParentFile ();
        if (currentSourcePath != null && currentSourcePath.isDirectory ())
            this.config.setActivePath (currentSourcePath);
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
        if (this.isContentsRun)
        {
            this.isContentsRun = false;
            this.updateButtonStates (true);
            if (!cancelled)
                Platform.runLater (this::showContentsDialog);
            return;
        }

        // Creates libraries if requested
        this.backend.finish (cancelled);

        // Workaround to always scroll fully to the end of the log
        this.loggingArea.autoScrollToTailProperty ().set (false);
        final PauseTransition delay = new PauseTransition (Duration.millis (1000));
        delay.setOnFinished (_ -> this.loggingArea.scrollTo (this.loggingArea.getItems ().size () - 1));
        delay.play ();

        this.updateButtonStates (true);
    }


    private static Button setupButton (final BasePanel panel, final String iconName, final String labelName, final String mnemonic) throws EndApplicationException
    {
        try
        {
            final Image icon = Functions.iconFor ("de/mossgrabers/convertwithmoss/images/" + iconName + ".png");
            final Button button = panel.createButton (icon, labelName, mnemonic);
            button.alignmentProperty ().set (Pos.CENTER_LEFT);
            button.graphicTextGapProperty ().set (12);

            final ImageView image = (ImageView) button.getGraphic ();
            image.setFitWidth (24);
            image.setPreserveRatio (true);

            return button;
        }
        catch (final IOException ex)
        {
            throw new EndApplicationException (ex);
        }
    }


    private static void updateHistory (final String newItem, final List<String> history)
    {
        history.remove (newItem);
        history.add (0, newItem);
    }


    /**
     * Checks if folder is empty. Ignores hidden files/folders (e.g. .DS_Store on macOS and the
     * .Spotlight-V100, .Trashes and .fseventsd folders created on the root of a removable volume)
     * as well as the known Windows system folders, so that the root of a USB stick or external
     * drive is not wrongly reported as non-empty.
     *
     * @param directoryPath Path of folder to check
     * @return True if directory is empty
     */
    private boolean isEmptyFolder (final String directoryPath)
    {
        boolean result = true;
        try (final Stream<Path> paths = Files.list (Path.of (directoryPath)))
        {
            result = paths.filter (path -> {
                // Ignore hidden entries (e.g. macOS .DS_Store and the volume-root
                // .Spotlight-V100, .Trashes, .fseventsd folders) and the known Windows system
                // folders, none of which the user wants to preserve.
                final String name = path.getFileName ().toString ();
                return !name.startsWith (".") && !"Thumbs.db".equals (name) && !"ConvertWithMoss.log".equals (name) && !"System Volume Information".equals (name) && !"$RECYCLE.BIN".equals (name);
            }).count () == 0;
        }
        catch (final IOException _)
        {
            result = false;
        }

        if (!result)
        {
            Functions.message ("@IDS_NOTIFY_FOLDER_MUST_BE_EMPTY");
            this.destinationPathField.requestFocus ();
        }

        return result;
    }


    private class TaskPane
    {
        private final BorderPane                formatPane    = new BorderPane ();
        private final TextField                 search        = new TextField ();
        private final StackPane                 contentArea   = new StackPane ();
        private final ListView<String>          formatList;
        private final Map<String, ICoreTask<?>> mappedTasks   = new HashMap<> ();
        private final Map<String, Integer>      mappedIndices = new HashMap<> ();
        private final List<String>              indices       = new ArrayList<> ();
        private final Map<String, Node>         mappedPanes   = new HashMap<> ();

        private String                          lastSelected  = null;


        private TaskPane (final List<? extends ICoreTask<?>> tasks, final boolean isSource)
        {
            final List<String> taskNames = new ArrayList<> ();
            for (int i = 0; i < tasks.size (); i++)
            {
                final ICoreTask<?> task = tasks.get (i);
                String name = task.getName ();
                final String fileEndings = formatFileEndings (task.getFileEndings ());
                if (!fileEndings.isEmpty ())
                    name += fileEndings;
                taskNames.add (name);

                final ICoreTaskSettings userInterface = task.getSettings ();
                final ScrollPane scrollPane = new ScrollPane (userInterface.getEditPane ());
                scrollPane.fitToWidthProperty ().set (true);
                scrollPane.fitToHeightProperty ().set (true);

                this.contentArea.getChildren ().add (scrollPane);
                this.mappedPanes.put (name, scrollPane);
                this.mappedTasks.put (name, task);
                this.mappedIndices.put (name, Integer.valueOf (i));
                this.indices.add (name);
            }

            this.search.setPromptText (Functions.getMessage ("IDS_MAIN_SEARCH_FORMAT"));
            this.search.getStyleClass ().add ("text-field-with-clear");

            final ObservableList<String> observableList = FXCollections.observableList (taskNames);
            final FilteredList<String> filtered = new FilteredList<> (observableList, _ -> true);
            this.formatList = new ListView<> (filtered);
            this.setTooltips ();
            final MultipleSelectionModel<String> selectionModel = this.formatList.getSelectionModel ();

            filtered.predicateProperty ().bind (Bindings.createObjectBinding (() -> f -> this.isVisibleInFilter (this.search.getText (), f, isSource), this.search.textProperty (), MainFrame.this.destinationTypeTabPane.getSelectionModel ().selectedIndexProperty ()));

            // Ensure that there is always a selected element (select by value)
            selectionModel.selectedItemProperty ().addListener ((_, _, newVal) -> {
                if (newVal != null)
                    this.lastSelected = newVal;
            });
            filtered.addListener ((ListChangeListener<String>) _ -> {
                if (filtered.isEmpty ())
                    return;
                if (this.lastSelected != null && filtered.contains (this.lastSelected))
                    selectionModel.select (this.lastSelected);
                else
                    selectionModel.selectFirst ();
            });

            selectionModel.selectedItemProperty ().addListener ((_, _, selected) -> {
                if (selected != null)
                    this.showPane (selected);
            });

            final BorderPane sidebar = new BorderPane ();
            sidebar.setTop (addClearButton (this.search));
            sidebar.setCenter (this.formatList);
            this.formatPane.setLeft (sidebar);
            this.formatPane.setCenter (this.contentArea);
            this.formatPane.getStyleClass ().add ("paddingLeftBottomRight");
        }


        private void setTooltips ()
        {
            this.formatList.setCellFactory (_ -> new ListCell<String> ()
            {
                // Install once, reuse
                private final Tooltip tip = new Tooltip ();
                {
                    this.setTooltip (this.tip);
                }


                /** {@inheritDoc} */
                @Override
                protected void updateItem (final String item, final boolean empty)
                {
                    super.updateItem (item, empty);
                    this.setText (empty ? null : item);
                    this.tip.setText (empty ? null : item);
                }
            });
        }


        private void showPane (final String selected)
        {
            for (final Entry<String, Node> layer: this.mappedPanes.entrySet ())
                layer.getValue ().setVisible (layer.getKey ().equals (selected));
        }


        /**
         * Set the key of the selected detector/creator.
         *
         * @param sourceFormat The index of the format to select
         */
        public void setSelectedFormat (final int sourceFormat)
        {
            final String name = this.indices.get (Math.clamp (sourceFormat, 0, this.indices.size () - 1));
            final MultipleSelectionModel<String> selectionModel = this.formatList.getSelectionModel ();
            selectionModel.select (name);
            if (selectionModel.getSelectedItem () == null)
                selectionModel.select (0);

            final PauseTransition delay = new PauseTransition (Duration.seconds (1));
            delay.setOnFinished (_ -> this.formatList.scrollTo (selectionModel.getSelectedIndex ()));
            delay.play ();
        }


        /**
         * Get the key of the selected detector/creator. Takes care of different selection indices
         * due to filtered entries.
         *
         * @return The index if one is selected
         */
        public int getSelectedFormat ()
        {
            final String selectedItem = this.formatList.getSelectionModel ().getSelectedItem ();
            if (selectedItem == null)
                return -1;
            final Integer key = this.mappedIndices.get (selectedItem);
            return key == null ? -1 : key.intValue ();
        }


        private boolean isVisibleInFilter (final String filterText, final String itemText, final boolean isSource)
        {
            if (filterText == null || filterText.isBlank () || itemText.toLowerCase ().contains (filterText.toLowerCase ()))
            {
                if (MainFrame.this.sourceTaskPane == null || MainFrame.this.destinationTaskPane == null)
                    return true;

                final int selectedType = MainFrame.this.destinationTypeTabPane.getSelectionModel ().getSelectedIndex ();
                if (isSource)
                {
                    final IDetector<?> detector = (IDetector<?>) MainFrame.this.sourceTaskPane.mappedTasks.get (itemText);
                    return selectedType != DEST_TYPE_PERFORMANCE && selectedType != DEST_TYPE_PERFORMANCE_LIBRARY || detector.supportsPerformances ();
                }

                final ICreator<?> creator = (ICreator<?>) MainFrame.this.destinationTaskPane.mappedTasks.get (itemText);
                return selectedType == DEST_TYPE_PRESET || selectedType == DEST_TYPE_PRESET_LIBRARY && creator.supportsPresetLibraries () || selectedType == DEST_TYPE_PERFORMANCE && creator.supportsPerformances () || selectedType == DEST_TYPE_PERFORMANCE_LIBRARY && creator.supportsPerformanceLibraries ();
            }
            return false;
        }


        private static StackPane addClearButton (final TextField textField)
        {
            final Button clearButton = new Button ("✕");
            clearButton.getStyleClass ().add ("text-field-clear-button");
            clearButton.visibleProperty ().bind (textField.textProperty ().isNotEmpty ());
            clearButton.setOnAction (_ -> textField.clear ());

            StackPane.setAlignment (clearButton, Pos.CENTER_RIGHT);
            StackPane.setMargin (clearButton, new Insets (0, 5, 0, 0));

            // Prevent the button from stealing focus from the text field
            clearButton.setFocusTraversable (false);

            return new StackPane (textField, clearButton);
        }
    }
}
