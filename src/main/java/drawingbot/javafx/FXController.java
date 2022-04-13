package drawingbot.javafx;

import drawingbot.DrawingBotV3;
import drawingbot.FXApplication;
import drawingbot.api.Hooks;
import drawingbot.files.*;
import drawingbot.javafx.controllers.*;
import drawingbot.image.blend.EnumBlendMode;
import drawingbot.integrations.vpype.FXVPypeController;
import drawingbot.integrations.vpype.VpypeHelper;
import drawingbot.javafx.controls.*;
import drawingbot.javafx.observables.ObservableDrawingPen;
import drawingbot.plotting.PlottedDrawing;
import drawingbot.registry.MasterRegistry;
import drawingbot.registry.Register;
import drawingbot.render.IDisplayMode;
import drawingbot.utils.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.control.*;

import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.RangeSlider;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.awt.image.BufferedImageOp;
import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class FXController {

    public FXDrawingArea drawingAreaController;
    public FXImageFilters imageFiltersController;
    public FXPFMControls pfmSettingsController;
    public FXDrawingSets drawingSetsController;
    public FXVersionControl versionControlController;

    public TitledPane titledPaneBatchProcessing = null;
    public FXBatchProcessing batchProcessingController;

    /**
     * starts the FXController, called internally by JavaFX
     */
    @FXML
    public void initialize(){
        DrawingBotV3.logger.entering("FX Controller", "initialize");

        drawingAreaController.drawingArea.set(DrawingBotV3.INSTANCE.drawingArea);

        imageFiltersController.settings.set(DrawingBotV3.INSTANCE.imgFilterSettings);

        pfmSettingsController.pfmSettings.set(DrawingBotV3.INSTANCE.pfmSettings);

        drawingSetsController.drawingSets.set(DrawingBotV3.INSTANCE.drawingSets);

        versionControlController.projectVersions.set(DrawingBotV3.INSTANCE.projectVersions);

        try{
            Hooks.runHook(Hooks.FX_CONTROLLER_PRE_INIT, this);
            initToolbar();
            initViewport();
            initPlottingControls();
            initProgressBar();
            //initPFMControls();
            Hooks.runHook(Hooks.FX_CONTROLLER_POST_INIT, this);

            viewportScrollPane.setHvalue(0.5);
            viewportScrollPane.setVvalue(0.5);

            viewportScrollPane.setOnMouseMoved(DrawingBotV3.INSTANCE::onMouseMovedViewport);
            viewportScrollPane.setOnMousePressed(DrawingBotV3.INSTANCE::onMousePressedViewport);
            viewportScrollPane.setOnKeyPressed(DrawingBotV3.INSTANCE::onKeyPressedViewport);

            initSeparateStages();

        }catch (Exception e){
            DrawingBotV3.logger.log(Level.SEVERE, "Failed to initialize JAVA FX", e);
        }

        DrawingBotV3.logger.exiting("FX Controller", "initialize");
    }

    public Stage exportSettingsStage;
    public FXExportController exportController;

    public Stage vpypeSettingsStage;
    public FXVPypeController vpypeController;

    public Stage mosaicSettingsStage;
    public FXStylesController mosaicController;

    public Stage taskMonitorStage;
    public FXTaskMonitorController taskMonitorController;

    public Stage projectManagerStage;
    public FXProjectManagerController projectManagerController;


    public void initSeparateStages() {
        FXHelper.initSeparateStage("/fxml/exportsettings.fxml", exportSettingsStage = new Stage(), exportController = new FXExportController(), "Export Settings", Modality.APPLICATION_MODAL);
        FXHelper.initSeparateStage("/fxml/vpypesettings.fxml", vpypeSettingsStage = new Stage(), vpypeController = new FXVPypeController(), "vpype Settings", Modality.APPLICATION_MODAL);
        FXHelper.initSeparateStage("/fxml/mosaicsettings.fxml", mosaicSettingsStage = new Stage(), mosaicController = new FXStylesController(), "Mosaic Settings", Modality.APPLICATION_MODAL);
        FXHelper.initSeparateStage("/fxml/serialportsettings.fxml", (Stage) Hooks.runHook(Hooks.SERIAL_CONNECTION_STAGE, new Stage())[0], Hooks.runHook(Hooks.SERIAL_CONNECTION_CONTROLLER, new DummyController())[0], "Plotter / Serial Port Connection", Modality.NONE);
        FXHelper.initSeparateStage("/fxml/taskmonitor.fxml", taskMonitorStage = new Stage(), taskMonitorController = new FXTaskMonitorController(), "Task Monitor", Modality.NONE);
        FXHelper.initSeparateStage("/fxml/projectmanager.fxml", projectManagerStage = new Stage(), projectManagerController = new FXProjectManagerController(), "Project Manager", Modality.NONE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////GLOBAL CONTAINERS
    public VBox vBoxMain = null;

    public ScrollPane scrollPaneSettings = null;
    public VBox vBoxSettings = null;

    public DialogPresetRename presetEditorDialog = new DialogPresetRename();

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //// TOOL BAR

    public Menu menuFile = null;
    public Menu menuView = null;
    public Menu menuFilters = null;
    public Menu menuHelp = null;

    public Map<TitledPane, Stage> settingsStages = new LinkedHashMap<>();
    public Map<TitledPane, Node> settingsContent = new LinkedHashMap<>();

    public void initToolbar(){
        //file
        MenuItem menuOpen = new MenuItem("Open Project");
        menuOpen.setOnAction(e -> FXHelper.importPreset(Register.PRESET_TYPE_PROJECT, true, false));
        menuFile.getItems().add(menuOpen);

        MenuItem menuSave = new MenuItem("Save Project");
        menuSave.disableProperty().bind(DrawingBotV3.INSTANCE.currentDrawing.isNull());
        menuSave.setOnAction(e -> {
            File folder = FileUtils.getExportDirectory();
            String projectName = "New Project";

            PlottedDrawing renderedDrawing = DrawingBotV3.INSTANCE.getCurrentDrawing();
            if(renderedDrawing != null){
                File originalFile = renderedDrawing.getOriginalFile();
                if(originalFile != null){
                    folder = originalFile.getParentFile();
                    projectName = FileUtils.removeExtension(originalFile.getName());
                }
            }
            FXHelper.exportProject(folder, projectName);
        });
        menuFile.getItems().add(menuSave);

        menuFile.getItems().add(new SeparatorMenuItem());
        /*
        MenuItem projectManager = new MenuItem("Open Project Manager");
        projectManager.setOnAction(e -> projectManagerStage.show());
        menuFile.getItems().add(projectManager);

        menuFile.getItems().add(new SeparatorMenuItem());

         */


        MenuItem menuImport = new MenuItem("Import Image");
        menuImport.setOnAction(e -> FXHelper.importImageFile());
        menuFile.getItems().add(menuImport);

        MenuItem menuVideo = new MenuItem("Import Video");
        menuVideo.setOnAction(e -> FXHelper.importVideoFile());
        menuFile.getItems().add(menuVideo);

        menuFile.getItems().add(new SeparatorMenuItem());

        for(ExportTask.Mode exportMode : ExportTask.Mode.values()){
            Menu menuExport = new Menu(exportMode.getDisplayName());
            for(DrawingExportHandler.Category category : DrawingExportHandler.Category.values()){
                for(DrawingExportHandler format : MasterRegistry.INSTANCE.drawingExportHandlers){
                    if(format.category != category){
                        continue;
                    }
                    if(format.isPremium && !FXApplication.isPremiumEnabled){
                        MenuItem item = new MenuItem(format.displayName + " (Premium)");
                        item.setOnAction(e -> showPremiumFeatureDialog());
                        menuExport.getItems().add(item);
                    }else{
                        MenuItem item = new MenuItem(format.displayName);
                        item.setOnAction(e -> FXHelper.exportFile(format, exportMode));
                        menuExport.getItems().add(item);
                    }
                }
                if(category != DrawingExportHandler.Category.values()[DrawingExportHandler.Category.values().length-1]){
                    menuExport.getItems().add(new SeparatorMenuItem());
                }
            }
            menuExport.disableProperty().bind(DrawingBotV3.INSTANCE.currentDrawing.isNull());
            menuFile.getItems().add(menuExport);
        }

        menuFile.getItems().add(new SeparatorMenuItem());

        Hooks.runHook(Hooks.FILE_MENU, menuFile);

        MenuItem menuExportToVPype = new MenuItem("Export to " + VpypeHelper.VPYPE_NAME);
        menuExportToVPype.setOnAction(e -> {
            if(DrawingBotV3.INSTANCE.getCurrentDrawing() != null){
                vpypeSettingsStage.show();
            }
        });
        menuExportToVPype.disableProperty().bind(DrawingBotV3.INSTANCE.currentDrawing.isNull());

        menuFile.getItems().add(menuExportToVPype);

        menuFile.getItems().add(new SeparatorMenuItem());

        MenuItem menuExportSettings = new MenuItem("Export Settings");
        menuExportSettings.setOnAction(e -> exportSettingsStage.show());
        menuFile.getItems().add(menuExportSettings);

        menuFile.getItems().add(new SeparatorMenuItem());

        MenuItem taskMonitor = new MenuItem("Open Task Monitor");
        taskMonitor.setOnAction(e -> taskMonitorStage.show());
        menuFile.getItems().add(taskMonitor);

        menuFile.getItems().add(new SeparatorMenuItem());

        MenuItem menuQuit = new MenuItem("Quit");
        menuQuit.setOnAction(e -> Platform.exit());
        menuFile.getItems().add(menuQuit);

        //view
        ArrayList<TitledPane> allPanes = new ArrayList<>();
        for(Node node : vBoxSettings.getChildren()){
            if(node instanceof TitledPane){
                allPanes.add((TitledPane) node);
            }
        }
        for(TitledPane pane : allPanes){
            MenuItem viewButton = new MenuItem(pane.getText());
            viewButton.setOnAction(e -> {
                Platform.runLater(() -> allPanes.forEach(p -> p.expandedProperty().setValue(p == pane)));
            });
            menuView.getItems().add(viewButton);
            Button undock = new Button("", new Glyph("FontAwesome", FontAwesome.Glyph.LINK));

            undock.setOnAction(e -> {
                Stage currentStage = settingsStages.get(pane);
                if(currentStage == null){

                    //create the stage
                    Stage settingsStage = new Stage(StageStyle.DECORATED);
                    settingsStage.initModality(Modality.NONE);
                    settingsStage.initOwner(FXApplication.primaryStage);
                    settingsStage.setTitle(pane.getText());
                    settingsStage.setResizable(false);

                    //create the root node
                    ScrollPane scrollPane = new ScrollPane();
                    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
                    scrollPane.setPrefWidth(420);

                    //transfer the content
                    Node content = pane.getContent();
                    pane.setAnimated(false);
                    pane.setExpanded(true);
                    pane.layout();

                    pane.setContent(new AnchorPane());
                    scrollPane.setContent(content);

                    pane.setExpanded(false);
                    pane.setAnimated(true);

                    //save the reference for later
                    settingsStages.put(pane, settingsStage);
                    settingsContent.put(pane, content);


                    //show the scene
                    Scene scene = new Scene(scrollPane);
                    settingsStage.setScene(scene);
                    settingsStage.setOnCloseRequest(event -> redockSettingsPane(pane));
                    FXApplication.applyDBStyle(settingsStage);
                    settingsStage.show();
                }else{
                    redockSettingsPane(pane);
                    currentStage.close();
                }
            });

            pane.setContentDisplay(ContentDisplay.RIGHT);
            pane.setGraphic(undock);
            undock.translateXProperty().bind(Bindings.createDoubleBinding(
                    () -> pane.getWidth() - undock.getLayoutX() - undock.getWidth() - 30,
                    pane.widthProperty())
            );
        }



        //filters
        for(Map.Entry<EnumFilterTypes, ObservableList<GenericFactory<BufferedImageOp>>> entry : MasterRegistry.INSTANCE.imgFilterFactories.entrySet()){
            Menu type = new Menu(entry.getKey().toString());

            for(GenericFactory<BufferedImageOp> factory : entry.getValue()){
                MenuItem item = new MenuItem(factory.getName());
                item.setOnAction(e -> FXHelper.addImageFilter(factory, DrawingBotV3.INSTANCE.imgFilterSettings));
                type.getItems().add(item);
            }

            menuFilters.getItems().add(type);
        }

        //help
        if(!FXApplication.isPremiumEnabled){
            MenuItem upgrade = new MenuItem("Upgrade");
            upgrade.setOnAction(e -> FXHelper.openURL(DBConstants.URL_UPGRADE));
            menuHelp.getItems().add(upgrade);
            menuHelp.getItems().add(new SeparatorMenuItem());
        }

        MenuItem documentation = new MenuItem("View Documentation");
        documentation.setOnAction(e -> FXHelper.openURL(DBConstants.URL_READ_THE_DOCS_HOME));
        menuHelp.getItems().add(documentation);

        MenuItem sourceCode = new MenuItem("View Source Code");
        sourceCode.setOnAction(e -> FXHelper.openURL(DBConstants.URL_GITHUB_REPO));
        menuHelp.getItems().add(sourceCode);

        MenuItem configFolder = new MenuItem("Open Configs Folder");
        configFolder.setOnAction(e -> FXHelper.openFolder(new File(FileUtils.getUserDataDirectory())));
        menuHelp.getItems().add(configFolder);
    }

    public void redockSettingsPane(TitledPane pane){
        Node content = settingsContent.get(pane);
        pane.setContent(content);

        settingsStages.put(pane, null);
        settingsContent.put(pane, null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //// VIEWPORT PANE

    ////VIEWPORT WINDOW
    public VBox vBoxViewportContainer = null;
    public ZoomableScrollPane viewportScrollPane = null;

    ////VIEWPORT SETTINGS
    public RangeSlider rangeSliderDisplayedLines = null;
    public TextField textFieldDisplayedShapesMin = null;
    public TextField textFieldDisplayedShapesMax = null;

    public CheckBox checkBoxApplyToExport = null;

    public ChoiceBox<IDisplayMode> choiceBoxDisplayMode = null;
    public ComboBox<EnumBlendMode> comboBoxBlendMode = null;
    public Button buttonResetView = null;

    ////PLOT DETAILS
    public Label labelElapsedTime = null;
    public Label labelPlottedShapes = null;
    public Label labelPlottedVertices = null;
    public Label labelImageResolution = null;
    public Label labelPlottingResolution = null;
    public Label labelCurrentPosition = null;

    public Rectangle colourPickerRectangle;

    //public CheckBox checkBoxDarkTheme = null;

    public void initViewport(){

        ////VIEWPORT SETTINGS
        rangeSliderDisplayedLines.highValueProperty().addListener((observable, oldValue, newValue) -> {
            PlottedDrawing drawing = DrawingBotV3.INSTANCE.getCurrentDrawing();
            if(drawing != null){
                int lines = (int)Utils.mapDouble(newValue.doubleValue(), 0, 1, 0, drawing.getGeometryCount());
                drawing.displayedShapeMax = lines;
                textFieldDisplayedShapesMax.setText(String.valueOf(lines));
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        rangeSliderDisplayedLines.lowValueProperty().addListener((observable, oldValue, newValue) -> {
            PlottedDrawing drawing = DrawingBotV3.INSTANCE.getCurrentDrawing();
            if(drawing != null){
                int lines = (int)Utils.mapDouble(newValue.doubleValue(), 0, 1, 0, drawing.getGeometryCount());
                drawing.displayedShapeMin = lines;
                textFieldDisplayedShapesMin.setText(String.valueOf(lines));
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        textFieldDisplayedShapesMax.setOnAction(e -> {
            PlottedDrawing drawing = DrawingBotV3.INSTANCE.getCurrentDrawing();
            if(drawing != null){
                int lines = (int)Math.max(0, Math.min(drawing.getGeometryCount(), Double.parseDouble(textFieldDisplayedShapesMax.getText())));
                drawing.displayedShapeMax = lines;
                textFieldDisplayedShapesMax.setText(String.valueOf(lines));
                rangeSliderDisplayedLines.setHighValue((double)lines / drawing.getGeometryCount());
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        textFieldDisplayedShapesMin.setOnAction(e -> {
            PlottedDrawing drawing = DrawingBotV3.INSTANCE.getCurrentDrawing();
            if(drawing != null){
                int lines = (int)Math.max(0, Math.min(drawing.getGeometryCount(), Double.parseDouble(textFieldDisplayedShapesMin.getText())));
                drawing.displayedShapeMin = lines;
                textFieldDisplayedShapesMin.setText(String.valueOf(lines));
                rangeSliderDisplayedLines.setLowValue((double)lines / drawing.getGeometryCount());
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        DrawingBotV3.INSTANCE.currentDrawing.addListener((observable, oldValue, newValue) -> {
            if(newValue != null){
                Platform.runLater(() -> {
                    rangeSliderDisplayedLines.setLowValue(0.0F);
                    rangeSliderDisplayedLines.setHighValue(1.0F);
                    textFieldDisplayedShapesMin.setText(String.valueOf(0));
                    textFieldDisplayedShapesMax.setText(String.valueOf(newValue.getGeometryCount()));
                    labelPlottedShapes.setText(Utils.defaultNF.format(newValue.getGeometryCount()));
                    labelPlottedVertices.setText(Utils.defaultNF.format(newValue.getVertexCount()));
                });
            }else{
                rangeSliderDisplayedLines.setLowValue(0.0F);
                rangeSliderDisplayedLines.setHighValue(1.0F);
                textFieldDisplayedShapesMin.setText(String.valueOf(0));
                textFieldDisplayedShapesMax.setText(String.valueOf(0));
                labelPlottedShapes.setText(Utils.defaultNF.format(0));
                labelPlottedVertices.setText(Utils.defaultNF.format(0));
            }


        });

        checkBoxApplyToExport.selectedProperty().bindBidirectional(DrawingBotV3.INSTANCE.exportRange);

        choiceBoxDisplayMode.getItems().addAll(MasterRegistry.INSTANCE.displayModes);
        choiceBoxDisplayMode.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.displayMode);

        comboBoxBlendMode.setItems(FXCollections.observableArrayList(EnumBlendMode.values()));
        comboBoxBlendMode.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.blendMode);

        buttonResetView.setOnAction(e -> DrawingBotV3.INSTANCE.resetView());

        /*
        checkBoxDarkTheme.setSelected(ConfigFileHandler.getApplicationSettings().darkTheme);
        checkBoxDarkTheme.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            ConfigFileHandler.getApplicationSettings().darkTheme = isSelected;
            ConfigFileHandler.getApplicationSettings().markDirty();
            FXApplication.applyCurrentTheme();
        });

         */


        //DrawingBotV3.INSTANCE.displayGrid.bind(checkBoxShowGrid.selectedProperty());
        //DrawingBotV3.INSTANCE.displayGrid.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.reRender());

        viewportScrollPane = new ZoomableScrollPane();

        viewportScrollPane.setFitToWidth(true);
        viewportScrollPane.setFitToHeight(true);
        viewportScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        viewportScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        viewportScrollPane.setVvalue(0.5);
        viewportScrollPane.setHvalue(0.5);
        viewportScrollPane.setMaxWidth(Double.MAX_VALUE);
        viewportScrollPane.setMaxHeight(Double.MAX_VALUE);
        viewportScrollPane.setPannable(true);
        VBox.setVgrow(viewportScrollPane, Priority.ALWAYS);
        vBoxViewportContainer.getChildren().add(viewportScrollPane);

        viewportScrollPane.setOnDragOver(event -> {

            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.LINK);
            }
            event.consume();
        });

        viewportScrollPane.setOnDragDropped(event -> {

            Dragboard db = event.getDragboard();
            boolean success = false;
            if(db.hasFiles()){
                List<File> files = db.getFiles();
                DrawingBotV3.INSTANCE.openFile(files.get(0), false);
                success = true;
            }
            event.setDropCompleted(success);

            event.consume();
        });

        labelElapsedTime.setText("0 s");
        labelPlottedShapes.setText("0");
        labelPlottedVertices.setText("0");
        labelImageResolution.setText("0 x 0");
        labelPlottingResolution.setText("0 x 0");
        labelCurrentPosition.setText("0 x 0 y");
    }

    private final WritableImage snapshotImage = new WritableImage(1, 1);
    private ObservableDrawingPen penForColourPicker;
    private boolean colourPickerActive;

    public void startColourPick(ObservableDrawingPen pen){
        penForColourPicker = pen;
        colourPickerActive = true;
        viewportScrollPane.setCursor(Cursor.CROSSHAIR);
        colourPickerRectangle.setVisible(true);
        colourPickerRectangle.setFill(pen.javaFXColour.get());
    }

    public void doColourPick(MouseEvent event, boolean update){
        Point2D localPoint = viewportScrollPane.getParent().sceneToLocal(event.getSceneX(), event.getSceneY());

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setViewport(new Rectangle2D(localPoint.getX(), localPoint.getY(), 1, 1));

        viewportScrollPane.snapshot((result) -> setPickResult(result, update), parameters, snapshotImage);
    }

    public Void setPickResult(SnapshotResult result, boolean update){
        Color color = result.getImage().getPixelReader().getColor(0, 0);
        if(update){
            colourPickerRectangle.setFill(color);
        }else{
            penForColourPicker.javaFXColour.set(color);
            endColourPick();
        }
        return null;
    }

    public void endColourPick(){
        viewportScrollPane.setCursor(Cursor.DEFAULT);
        penForColourPicker = null;
        colourPickerActive = false;
        colourPickerRectangle.setVisible(false);
    }

    public void onMouseMovedColourPicker(MouseEvent event){
        if(colourPickerActive){
            doColourPick(event, true);
        }
    }

    public void onMousePressedColourPicker(MouseEvent event){
        if(colourPickerActive && event.isPrimaryButtonDown()){
            doColourPick(event, false);
            event.consume();
        }
    }

    public void onKeyPressedColourPicker(KeyEvent event){
        if(event.getCode() == KeyCode.ESCAPE){
            endColourPick();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //// PLOTTING CONTROLS

    public Button buttonStartPlotting = null;
    public Button buttonStopPlotting = null;
    public Button buttonResetPlotting = null;
    public Button buttonSaveVersion = null;

    public void initPlottingControls(){
        buttonStartPlotting.setOnAction(param -> DrawingBotV3.INSTANCE.startPlotting());
        buttonStartPlotting.disableProperty().bind(DrawingBotV3.INSTANCE.taskMonitor.isPlotting);
        buttonStopPlotting.setOnAction(param -> DrawingBotV3.INSTANCE.stopPlotting());
        buttonStopPlotting.disableProperty().bind(DrawingBotV3.INSTANCE.taskMonitor.isPlotting.not());
        buttonResetPlotting.setOnAction(param -> DrawingBotV3.INSTANCE.resetPlotting());

        buttonSaveVersion.setOnAction(param -> versionControlController.saveVersion());
        buttonSaveVersion.disableProperty().bind(Bindings.createBooleanBinding(() -> DrawingBotV3.INSTANCE.taskMonitor.isPlotting.get() || DrawingBotV3.INSTANCE.currentDrawing.get() == null, DrawingBotV3.INSTANCE.taskMonitor.isPlotting, DrawingBotV3.INSTANCE.currentDrawing));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    ////PROGRESS BAR PANE

    public Pane paneProgressBar = null;
    public ProgressBar progressBarGeneral = null;
    public Label progressBarLabel = null;
    public Label labelCancelExport = null;
    public Label labelOpenDestinationFolder = null;

    public void initProgressBar(){
        progressBarGeneral.prefWidthProperty().bind(paneProgressBar.widthProperty());
        progressBarLabel.setText("");

        progressBarGeneral.progressProperty().bind(DrawingBotV3.INSTANCE.taskMonitor.progressProperty);
        progressBarLabel.textProperty().bind(Bindings.createStringBinding(() -> DrawingBotV3.INSTANCE.taskMonitor.getCurrentTaskStatus(), DrawingBotV3.INSTANCE.taskMonitor.messageProperty, DrawingBotV3.INSTANCE.taskMonitor.titleProperty, DrawingBotV3.INSTANCE.taskMonitor.exceptionProperty));

        labelCancelExport.setOnMouseEntered(event -> labelCancelExport.textFillProperty().setValue(Color.BLANCHEDALMOND));
        labelCancelExport.setOnMouseExited(event -> labelCancelExport.textFillProperty().setValue(Color.BLACK));
        labelCancelExport.setOnMouseClicked(event -> {
            Task<?> task = DrawingBotV3.INSTANCE.taskMonitor.currentTask;
            if(task instanceof ExportTask){
                DrawingBotV3.INSTANCE.resetTaskService();
            }
        });
        labelCancelExport.visibleProperty().bind(DrawingBotV3.INSTANCE.taskMonitor.isExporting);

        labelOpenDestinationFolder.setOnMouseEntered(event -> labelOpenDestinationFolder.textFillProperty().setValue(Color.BLANCHEDALMOND));
        labelOpenDestinationFolder.setOnMouseExited(event -> labelOpenDestinationFolder.textFillProperty().setValue(Color.BLACK));
        labelOpenDestinationFolder.setOnMouseClicked(event -> {
            ExportTask task = DrawingBotV3.INSTANCE.taskMonitor.getDisplayedExportTask();
            if(task != null){
                FXHelper.openFolder(task.saveLocation.getParentFile());
            }
        });
        labelOpenDestinationFolder.visibleProperty().bind(Bindings.createBooleanBinding(() -> DrawingBotV3.INSTANCE.taskMonitor.wasExporting.get() || DrawingBotV3.INSTANCE.taskMonitor.isExporting.get(), DrawingBotV3.INSTANCE.taskMonitor.isExporting, DrawingBotV3.INSTANCE.taskMonitor.wasExporting));
    }



    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////PRE PROCESSING PANE




    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////PEN SETTINGS


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////VERSION CONTROL

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////BATCH PROCESSING


    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void showPremiumFeatureDialog(){
        DialogPremiumFeature premiumFeature = new DialogPremiumFeature();

        Optional<Boolean> upgrade = premiumFeature.showAndWait();
        if(upgrade.isPresent() && upgrade.get()){
            FXHelper.openURL(DBConstants.URL_UPGRADE);
        }
    }

    //// PRESET MENU BUTTON \\\\

    public static class DummyController {

        public void initialize(){
            ///NOP
        }

    }


}
