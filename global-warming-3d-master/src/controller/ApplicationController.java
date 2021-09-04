package controller;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.MeshView;
import javafx.util.Pair;
import model.AnimationModel;
import model.GeoCoord;
import model.GlobeAnomaliesRepresentation;
import model.ResourceManager;
import model.YearModel;
import util.CameraManager;
import util.ErrorManager;
import util.GeometryManager;
import view.AnomalyChart;
import view.ModeSwitcher;
import view.Scale;
import view.YearLabel;

/**
 * ApplicationView FXML Controller class
 *
 * @author Antonin
 */
public class ApplicationController implements Initializable {
    
    private AnimationModel animation;
    private ResourceManager rm;
    
    private ModeSwitcher switcher;
    private GlobeAnomaliesRepresentation displayType;
    
    private YearLabel yearLabel;
    private Scale scale;
    
    private YearModel year;
    
    @FXML
    private BorderPane mainPane;
    
    @FXML
    private Pane pane3D;
    
    Group root3D;
    Group anomalyGroup;
    
    
    @FXML
    private TextField tfYear;
    
    @FXML
    private Slider yearsSlider;
    
    @FXML
    private ImageView playPause;
    
    @FXML
    private ImageView slowDown;
    
    @FXML
    private ImageView speedUp;
    
    @FXML
    private Label speedLabel;
    
    @FXML
    private Label latitudeLabel;
    
    @FXML
    private Label longitudeLabel;
    
    @FXML
    private VBox rightPanel;
    private AnomalyChart anomaliesChart;
    
    @FXML
    private ImageView searchIcon;
    

    /**
     * Initializes the controller class.
     * 
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        //Create a graph scene root for the 3D content
        root3D = new Group();
        
        anomalyGroup = new Group();

        root3D.getChildren().add(GeometryManager.load("/resources/earth/earth.obj"));
        
        
        rm = new ResourceManager();
        rm.readTemperatureFile("/resources/tempanomaly_4x4grid.csv");
        System.out.println(rm.toString());
        
        
        year = new YearModel(rm.getMinYear());
        
        // Define display mode and display anomalies
        displayType = GlobeAnomaliesRepresentation.BY_COLOR;
        GeometryManager.drawAnomalies(anomalyGroup, rm, year.getCurrentYear(), displayType);
        

        // Add ambient light
        AmbientLight ambientLight = new AmbientLight(Color.WHITE);
        ambientLight.getScope().add(root3D);
        
        root3D.getChildren().addAll(ambientLight, anomalyGroup);
        
        
        // Add a camera group
        PerspectiveCamera camera = new PerspectiveCamera(true);
        
        // Create scene
        SubScene subScene = new SubScene(root3D, 500, 500, true, SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.setFill(Color.GREY);

        pane3D.getChildren().add(subScene);
        
        
        // need to be done after subScene adding to be displayed above
        pane3D.getChildren().add(initAbove3D());
        
        animation = new AnimationModel(1);
        
        init2D();
        initListeners();
        
        // Build camera manager
        new CameraManager(camera, pane3D, root3D);
    }

    
    private Group initAbove3D() {
        // Controls above 3D scene
        switcher = new ModeSwitcher(70.0);
        
        yearLabel = new YearLabel(rm.getMinYear());
        
        // initialize a scale adapted to the Color display mode :
        scale = new Scale(20, 250, Color.RED, Color.ORANGE, Color.YELLOW,
                                Color.YELLOW.invert(), Color.ORANGE.invert(), Color.RED.invert());
        
        yearLabel.layoutXProperty().bind(pane3D.widthProperty().subtract(yearLabel.widthProperty()).divide(2));
        yearLabel.layoutYProperty().bind(pane3D.heightProperty().subtract(yearLabel.heightProperty()));
        
        switcher.layoutXProperty().bind(pane3D.widthProperty().multiply(0.03f));
        switcher.layoutYProperty().bind(pane3D.heightProperty().multiply(0.03f));
        
        scale.layoutXProperty().bind(pane3D.widthProperty().subtract(scale.widthProperty().add(5)));
        scale.layoutYProperty().bind(pane3D.heightProperty().divide(2).subtract(scale.heightProperty().divide(2)));
        
        return new Group(yearLabel, switcher, scale);
    }
    
    private void init2D() {
        // Add tooltips on the 3D area and the magnifying glass
        Tooltip tooltip3D = new Tooltip("Ctrl + Clic pour obtenir des informations sur une zone.");
        Tooltip.install(pane3D, tooltip3D);
        
        Tooltip tooltipSearch = new Tooltip("Cliquez sur la loupe ou tapez la touche"
                + "\n'Enter' pour lancer la recherche.");
        Tooltip.install(searchIcon, tooltipSearch);
        
        
        // Restrict input length "client side" :
        Pattern pattern = Pattern.compile(".{0,4}");
        TextFormatter formatter = new TextFormatter((UnaryOperator<TextFormatter.Change>) change -> {
            return pattern.matcher(change.getControlNewText()).matches() ? change : null;
        });
        tfYear.setTextFormatter(formatter);
        
        
        // Change "mode" when a new radio button is selected
        switcher.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
            @Override
            public void changed(ObservableValue<? extends Toggle> observable, Toggle oldToggle, Toggle newToggle) {
                if (switcher.hasSelectedToggle()) {
                    if (switcher.isColorModeSelected()) {
                        displayType = GlobeAnomaliesRepresentation.BY_COLOR;
                        scale.setGradient(Color.RED, Color.ORANGE, Color.YELLOW,
                                Color.YELLOW.invert(), Color.ORANGE.invert(), Color.RED.invert());
                    }
                    else if (switcher.isBarModeSelected()) {
                        displayType = GlobeAnomaliesRepresentation.BY_HISTOGRAM;
                        scale.setGradient(Color.RED, Color.BLUE);
                    }
                    
                    switcher.toggleDisabledButton();
                    
                    GeometryManager.drawAnomalies(anomalyGroup, rm, year.getCurrentYear(), displayType);
                }
            }
        });
        
        anomaliesChart = new AnomalyChart(rm);
        rightPanel.getChildren().add(anomaliesChart);
        
        hideRightPanel();
    }

    private void initListeners() {
        /*
        * Listener of the year field : updates slider and label when the
        * "enter" keyboard button is hit (in the field).
        */
        EventHandler textFieldListener = new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent e) {
                if (e.getCode().equals(KeyCode.ENTER)) {
                    try {
                        Double newValue = Double.parseDouble(tfYear.getText());

                        yearsSlider.adjustValue(newValue);
                    } catch (Exception ex) {
                        System.err.println("Impossible to parse the input given.");
                        ErrorManager.displayWrongInput(tfYear.getText());
                    } finally {
                        tfYear.setText("");
                    }
                }
            }
        };
        tfYear.setOnKeyPressed(textFieldListener);
        
        
        // Associate click on the magnifying glass icon to a textfield Entrer-pressed event
        searchIcon.setOnMouseClicked(event -> {
            tfYear.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "",
                    "", KeyCode.ENTER, false, false, false, false));
        });

        
        // Bind year label w/ slider
        yearLabel.textProperty().bind(
            Bindings.format(
                "%.0f",
                yearsSlider.valueProperty()
            ));
        
        // Bind slider w/ year model and redraw 3D scene
        yearsSlider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                year.setCurrentYear(newValue.intValue());
                GeometryManager.drawAnomalies(anomalyGroup, rm, year.getCurrentYear(), displayType);
            }
        });
        
        // Bind speed label w/ animation model
        speedLabel.textProperty().bind(
            Bindings.format(
                "x%-1d",
                animation.speedProperty()
            ));
        
        /*
            TODO : tempo :
                - later in a dedicated class ?
                - add "maintained click" ?
        */
        speedUp.setOnMouseClicked(event -> {
            animation.speedUp();
        });
        
        slowDown.setOnMouseClicked(event -> {
            animation.slowDown();
        });
        
        
        playPause.setOnMouseClicked(event -> {
            playPause.setImage(new Image("/resources/pause.png", 25, 25, true, true));
            
            // Invert isPlaying value
            animation.togglePlaying();
            
            final long startTime = System.nanoTime();
            // Handles animation components (slider and buttons)
            new AnimationTimer() {
                private long nextIteration = startTime/100000 + (1000 * (6 - animation.getSpeed()));
                
                @Override
                public void handle(long now) {
                    if (yearsSlider.getValue() == yearsSlider.getMax()
                        || !animation.isPlaying()) {
                        stop();
                        playPause.setImage(new Image("/resources/play.png", 25, 25, true, true));
                        return;
                    }
                    
                    // Move forward of one "frame" and compute the following timestamp
                    if (nextIteration < now/100000) {
                        yearsSlider.increment();
                        nextIteration = now/100000 + (1000 * (6- animation.getSpeed()));
                    }
                }
            }.start();
            
        });
        
        // Handle 'Ctrl+Click' on globe to fill the right panel
        pane3D.setOnMouseReleased(event -> {
            
            if (event.isControlDown()) {
                
                // Check if the click has encounter a MeshView shape (Earth model is made of it)
                if (event.getPickResult().getIntersectedNode() instanceof MeshView) {
                    // Display right panel if it is hidden
                    if (mainPane.getRight() == null) {
                        mainPane.setRight(rightPanel);
                    }
                    
                    GeometryManager.displayPoint(root3D, event.getPickResult().getIntersectedPoint());
                
                    // Compute click's geographical position and update right panel
                    Pair<Integer, Integer> latLon = GeoCoord.coord3dToGeoCoord(event.getPickResult().getIntersectedPoint());
                    
                    latitudeLabel.setText(GeoCoord.latToString(latLon.getKey()));
                    longitudeLabel.setText(GeoCoord.lonToString(latLon.getValue()));
                    
                    // TO FIX : if lat value is negativized, the graph seems more accurate !
                    float[] dataEvolution = rm.getAllYearsFromCoord(latLon.getKey(), latLon.getValue());
                    
                    // Hydrates the graphic
                    anomaliesChart.updateData(dataEvolution);
                }
            } else {
                hideRightPanel();
//                pane3D.setPrefWidth(mainPane.getWidth());     // this resize only pane3d not root3d...
            }
        });
    }

    private void hideRightPanel() {
        mainPane.setRight(null);    // hide right panel
    }

}
