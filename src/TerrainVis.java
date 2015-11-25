/**
 * TerrainVis by Anton Ridgway
 * November 5, 2014
 * 
 * Visualizes terrain from GridFloat data, with contours drawn on top, and
 * provides a minimal user interface. The number and range of contours can
 * be adjusted, as well as the colors applied to the terrain, and the
 * resolution of terrain data used (to help with very large datasets).
 * 
 * The terrain can be spun by clicking with the mouse at some distance from
 * the center of the display, and the mouse wheel can be used to control zoom.
 */

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.jogamp.opengl.util.FPSAnimator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.DoubleBuffer;
import java.security.CodeSource;

public class TerrainVis extends Frame implements GLEventListener, MouseListener, MouseMotionListener, MouseWheelListener
{
	private static final long serialVersionUID = -5680020543723325793L;
	
	//Default Constants
	private static final int DEFAULT_WIDTH = 800;
	private static final int DEFAULT_HEIGHT = 600;
	
	//Camera Initial Constants
	private static final double DEFAULT_FOV = 25.0;
	private static final double DEFAULT_AR = (int)(((double)DEFAULT_WIDTH)/DEFAULT_HEIGHT);
	private static final double DEFAULT_ZNEAR = 0.1;
	private static final double DEFAULT_ZFAR = 150;
	private static final double[] DEFAULT_POS = new double[]{0,0,60};
	private static final double[] DEFAULT_TARGET = new double[]{0,0,0};
	private static final double[] DEFAULT_UP = new double[]{0,1,0};
	private static final double ZOOM_INCREMENT = 10.0;
	private static final double Z_MIN = 5.0;
	private static final double Z_MAX = 100.0;
	private static final double ROTATION_SPEED_DEGS = 5.0;
	
	//UI Initial Constants
	private static final int DEFAULT_CONTOURS = 10;
	private static final int DEFAULT_RES_FACTOR = 1;
	private static final float[] BACKGROUND_COLOR = new float[]{0.04f, 0.04f, 0.04f};
	private static final double[] DEFAULT_LOW_COLOR = new double[]{0.0, 0.0, 1.0};
	private static final double[] DEFAULT_HIGH_COLOR = new double[]{1.0, 0.0, 0.0};
	private static final double[] DEFAULT_MARKER_COLOR = new double[]{1.0, 1.0, 1.0};
	private static final double[] CONTOUR_COLOR = new double[]{0.0, 0.0, 0.0};
	private static final double CONTOUR_SPINNER_INCREMENT = 10.;
	private static final int RES_SPINNER_INCREMENT = 1;
	private static final int DEF_MAX_ROWS_COLUMNS = 1000;
	private static final boolean DEFAULT_SHOW_MARKER_VALUE = true;
	private static final boolean DEFAULT_SHOW_CONTOURS_VALUE = true;
	private static final boolean DEFAULT_SHOW_WIREFRAME_VALUE = false;
	private static final double GRID_SCALE = 20.;
	private static final double CONTOUR_DISPLAY_OFFSET = 0.0;
	private static final double MARKER_DISPLAY_OFFSET = 0.0;
	
	//Grid Display Information
	private static String currentDatafile;
	private static int numCtrs, resFactor;
	private static double lowCtrVal, highCtrVal, stepSize;
	private static double[] lowColor, highColor, colorDist, markerColor;
	private static boolean showMarker, showContours, showWireframe;
	private static GridFloatReader gridData;
	private static double gridWidth, gridHeight, gridDepthScale,
						  cellSizeX, cellSizeY, cellSizeRatio;
	private static double yRotation;
	private static double xRotation;

	//Display List IDs 
	private boolean listNumsGenerated = false;
	private boolean contoursGenerated = false;
	private boolean meshGenerated = false;
	private int contourList, meshList;
	
	//OpenGL Display and Interaction Entities
	private GL2 gl2;
	private GLProfile myProfile = null;
	private GLCapabilities myCapabilities = null;
	private GLJPanel myCanvas = null;
	private int canvasCenterX = 0;
	private int canvasCenterY = 0;
	private FPSAnimator myAnimator;
	private boolean mouseDown;
	private double mouseX, mouseY;

	//Camera Properties
	private GLU glu;
	private double camFOV;
	private double camAspectRatio;
	private double camZNear;
	private double camZFar;
	private double[] camPos;
	private double[] targetPos;
	private double[] upVector;
	
	//UI Components
	private File appFilePath;
	private JButton openFileButton;
	private JButton changeColorsButton;
	private JSpinner numContoursSpinner;
	private JSpinner lowContourSpinner;
	private JSpinner highContourSpinner;
	private JSpinner resFactorSpinner;
	private SpinnerNumberModel numContoursSpinnerModel;
	private SpinnerNumberModel lowContourSpinnerModel;
	private SpinnerNumberModel highContourSpinnerModel;
	private SpinnerNumberModel resFactorSpinnerModel;
	private Dimension spinnerDimension;
	private boolean spinnerShouldRedraw;
	private JCheckBox markerCheckbox;
	private JCheckBox contoursCheckbox;
	private JCheckBox wireframeCheckbox;
	
	public TerrainVis()
	{
		//Initialize the frame and create the canvas.
		super("Anton Ridgway - Terrain Visualization");
        myProfile = GLProfile.getDefault();
        myCapabilities = new GLCapabilities( myProfile );
        myCanvas = new GLJPanel( myCapabilities );
        myCanvas.addGLEventListener(this);
        myCanvas.addMouseListener(this);
        myCanvas.addMouseMotionListener(this);
        myCanvas.addMouseWheelListener(this);
        myAnimator = new FPSAnimator(myCanvas, 60);
        myAnimator.start();
        
        //Add a window listener to close the window when needed.
        addWindowListener(
        	new WindowAdapter()
        	{
	            public void windowClosing( WindowEvent windowevent )
	            {
	                remove(myCanvas);
	                dispose();
	                System.exit(0);
	            }
            }
    	);

        //Fill in default values.
    	camFOV = DEFAULT_FOV;
    	camAspectRatio = DEFAULT_AR;
    	camZNear = DEFAULT_ZNEAR;
    	camZFar = DEFAULT_ZFAR;
    	camPos = DEFAULT_POS;
    	targetPos = DEFAULT_TARGET;
    	upVector = DEFAULT_UP;
        
		currentDatafile = "";
		lowCtrVal = 0;
		highCtrVal = 0;
	    lowColor = DEFAULT_LOW_COLOR;
	    highColor = DEFAULT_HIGH_COLOR;
	    markerColor = DEFAULT_MARKER_COLOR;
	    colorDist = new double[]{highColor[0] - lowColor[0],
	    						 highColor[1] - lowColor[1],
	    						 highColor[2] - lowColor[2]};
    	showMarker = DEFAULT_SHOW_MARKER_VALUE;
    	showContours = DEFAULT_SHOW_CONTOURS_VALUE;
    	showWireframe = DEFAULT_SHOW_WIREFRAME_VALUE;
    	numCtrs = DEFAULT_CONTOURS;
    	resFactor = DEFAULT_RES_FACTOR;
    	stepSize = 0;
	    
	    //Get the distance between each color value.
	    colorDist = new double[]{highColor[0] - lowColor[0],
	    						 highColor[1] - lowColor[1],
	    						 highColor[2] - lowColor[2]};
	    
        buildGUI();
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setVisible( true );
	}
	
	/**
	 * buildGUI handles GUI initialization and adds all components to the main frame.
	 */
	private void buildGUI()
	{
		//Set natural look and feel.
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//Set up main layout.
		this.setLayout(new BorderLayout());
		this.add(myCanvas, BorderLayout.CENTER);
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BorderLayout());
		this.add(bottomPanel, BorderLayout.SOUTH);
		JPanel topRow = new JPanel();
		JPanel bottomRow = new JPanel();
		bottomPanel.add(topRow, BorderLayout.NORTH);
		bottomPanel.add(bottomRow, BorderLayout.SOUTH);

		//Set up individual components with appropriate listeners
		File source = new File(System.getProperty("java.class.path"));
		String binDirectory = source.getAbsoluteFile().getParentFile().toString();
		appFilePath = new File(binDirectory).getParentFile();
		
		openFileButton = new JButton("Open File");
		openFileButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				loadNewFile();
			}
		});
		bottomRow.add(openFileButton);
		
		changeColorsButton = new JButton("Change Colors");
		changeColorsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				chooseNewColors();
			}
		});
		changeColorsButton.setEnabled(false);
		bottomRow.add(changeColorsButton);

		spinnerDimension = new Dimension(150,20);
		spinnerShouldRedraw = true;
		
		JLabel numContoursLabel = new JLabel("# Contours:");
		numContoursSpinnerModel = new SpinnerNumberModel(10, 0, 100, 1);
		numContoursSpinner = new JSpinner(numContoursSpinnerModel);
		numContoursSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent arg0) {
				numCtrs = (int)numContoursSpinner.getValue();
				handleContourSpinnerChange();
			}
		});
		numContoursSpinner.setEnabled(false);
		topRow.add(numContoursLabel);
		topRow.add(numContoursSpinner);
		
		JLabel lowContourLabel = new JLabel("Low:");
		lowContourSpinnerModel = new SpinnerNumberModel(0.0, 0.0, CONTOUR_SPINNER_INCREMENT, CONTOUR_SPINNER_INCREMENT);
		lowContourSpinner = new JSpinner(lowContourSpinnerModel);
		lowContourSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent arg0) {
				lowCtrVal = (Double)lowContourSpinnerModel.getValue();
				handleContourSpinnerChange();
			}
		});
		lowContourSpinner.setPreferredSize(spinnerDimension);
		lowContourSpinner.setEnabled(false);
		topRow.add(lowContourLabel);
		topRow.add(lowContourSpinner);
	
		JLabel highContourLabel = new JLabel("High:");
		highContourSpinnerModel = new SpinnerNumberModel(0.0, 0.0, CONTOUR_SPINNER_INCREMENT, CONTOUR_SPINNER_INCREMENT);
		highContourSpinner = new JSpinner(highContourSpinnerModel);
		highContourSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent arg0) {
				highCtrVal = (Double)highContourSpinnerModel.getValue();
				handleContourSpinnerChange();
			}
		});
		highContourSpinner.setPreferredSize(spinnerDimension);
		highContourSpinner.setEnabled(false);
		topRow.add(highContourLabel);
		topRow.add(highContourSpinner);
		
		JLabel resFactorLabel = new JLabel("Reduce Resolution By:");
		resFactorSpinnerModel = new SpinnerNumberModel(1, 1, RES_SPINNER_INCREMENT, RES_SPINNER_INCREMENT);
		resFactorSpinner = new JSpinner(resFactorSpinnerModel);
		resFactorSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent arg0) {
				resFactor = (Integer)resFactorSpinnerModel.getValue();
				handleResolutionSpinnerChange();
			}
		});
		resFactorSpinner.setPreferredSize(spinnerDimension);
		resFactorSpinner.setEnabled(false);
		topRow.add(resFactorLabel);
		topRow.add(resFactorSpinner);
		
		markerCheckbox = new JCheckBox("Show Peak Marker", DEFAULT_SHOW_MARKER_VALUE);
		markerCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showMarker = !showMarker;
			}
		});
		markerCheckbox.setEnabled(false);
		bottomRow.add(markerCheckbox);
		
		contoursCheckbox = new JCheckBox("Show Contours", DEFAULT_SHOW_CONTOURS_VALUE);
		contoursCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showContours = !showContours;
			}
		});
		contoursCheckbox.setEnabled(false);
		bottomRow.add(contoursCheckbox);
		
		wireframeCheckbox = new JCheckBox("Show Wireframe", DEFAULT_SHOW_WIREFRAME_VALUE);
		wireframeCheckbox.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				showWireframe = !showWireframe;
			}
		});
		wireframeCheckbox.setEnabled(false);
		bottomRow.add(wireframeCheckbox);
	}
	
	/**
	 * loadNewFile prompts the user to choose a file, uses SparseGridFloatReader to read in the
	 * file information, and processes it.
	 */
	private void loadNewFile()
	{
		JFileChooser openFileDialog = new JFileChooser(appFilePath);
		int returnVal = openFileDialog.showOpenDialog(this);
		if(returnVal == JFileChooser.APPROVE_OPTION)
		{
			File newFile = openFileDialog.getSelectedFile();
			String newDataFile = newFile.getPath();
			int dotPosition = newDataFile.lastIndexOf('.');
			if(dotPosition >= -1) newDataFile = newDataFile.substring(0,dotPosition);
			
			//If the same file is chosen, do nothing.
			if(!newDataFile.equals(currentDatafile))
			{
				currentDatafile = newDataFile;
				gridData = new GridFloatReader(currentDatafile);
				stepSize = getStepSize(numCtrs, gridData.minHeight, gridData.maxHeight);
				
			    //Determine how to display the gridFloat data.
			    cellSizeRatio = gridData.cellSizeRatio;
			    
			    //Given non-square cells, determine the constraining dimension
			    if(gridData.numRows*cellSizeRatio > gridData.numColumns)
			    {
			    	cellSizeY = GRID_SCALE/gridData.numRows;
			    	cellSizeX = cellSizeY/cellSizeRatio;
			    }
			    else
			    {
			    	cellSizeX = GRID_SCALE/gridData.numColumns;
			    	cellSizeY = cellSizeX*cellSizeRatio; 
			    }
			    gridWidth = gridData.numColumns*cellSizeX;
			    gridHeight = gridData.numRows*cellSizeY;
			    gridDepthScale = cellSizeX/gridData.cellSizeX;
		    	
			    //Set up UI spinners
		    	spinnerShouldRedraw = false; //Don't redraw during setup.
		        lowContourSpinnerModel.setMinimum(gridData.minHeight);
		        lowContourSpinnerModel.setMaximum(gridData.maxHeight);
		        lowContourSpinnerModel.setValue(Math.floor(gridData.minHeight)+CONTOUR_SPINNER_INCREMENT);
		        highContourSpinnerModel.setMinimum(gridData.minHeight);
		        highContourSpinnerModel.setMaximum(gridData.maxHeight);
		        highContourSpinnerModel.setValue(Math.ceil(gridData.maxHeight)-CONTOUR_SPINNER_INCREMENT);
		        resFactorSpinnerModel.setMaximum(Math.min(gridData.numColumns-1,gridData.numRows-1));
		        resFactorSpinnerModel.setValue(getResFactor(gridData.numColumns,gridData.numRows));
				lowCtrVal = (Double)lowContourSpinnerModel.getValue();
				highCtrVal = (Double)highContourSpinnerModel.getValue();
		        resFactor = (Integer)resFactorSpinnerModel.getValue(); 
		        spinnerShouldRedraw = true;
		        
		        //Enable UI buttons
				changeColorsButton.setEnabled(true);
				numContoursSpinner.setEnabled(true);
				lowContourSpinner.setEnabled(true);
				highContourSpinner.setEnabled(true);
				resFactorSpinner.setEnabled(true);
				markerCheckbox.setEnabled(true);
				contoursCheckbox.setEnabled(true);
				wireframeCheckbox.setEnabled(true);
				
				//Notify the GL context to generate the display lists.
				contoursGenerated = false;
				meshGenerated = false;
			}
   		}
	}
	
	/**
	 * Prompts the user to choose the visualization colors via three consecutive prompts.
	 * Notifies the GL context that the mesh display list must be regenerated.
	 */
	private void chooseNewColors()
	{
		Color lowColorSelection = JColorChooser.showDialog(this,
				"Choose low elevation color.", new Color((float)lowColor[0],(float)lowColor[1],(float)lowColor[2]));
		if(lowColorSelection != null)
		{
			Color highColorSelection = JColorChooser.showDialog(this,
					"Choose high elevation color.", new Color((float)highColor[0],(float)highColor[1],(float)highColor[2]));
			if(highColorSelection != null)
			{
				Color markerColorSelection = JColorChooser.showDialog(this,
						"Choose marker color.", new Color((float)markerColor[0],(float)markerColor[1],(float)markerColor[2]));
				if(markerColorSelection != null)
				{
					lowColor[0] = ((double)lowColorSelection.getRed())/255.;
					lowColor[1] = ((double)lowColorSelection.getGreen())/255.;
					lowColor[2] = ((double)lowColorSelection.getBlue())/255.;
					highColor[0] = ((double)highColorSelection.getRed())/255.;
					highColor[1] = ((double)highColorSelection.getGreen())/255.;
					highColor[2] = ((double)highColorSelection.getBlue())/255.;
					markerColor[0] = ((double)markerColorSelection.getRed())/255.;
					markerColor[1] = ((double)markerColorSelection.getGreen())/255.;
					markerColor[2] = ((double)markerColorSelection.getBlue())/255.;
				    colorDist = new double[]{highColor[0] - lowColor[0],
							 highColor[1] - lowColor[1],
							 highColor[2] - lowColor[2]};
				    
				    //Regenerate the display list.
				    meshGenerated = false;
				}
			}
		}
	}
	
	/**
	 * Generate the size of step required to have numContours
	 * contours between high and low.
	 * @param numContours The number of contours to generate.
	 * @param lowValue The lowest contour's elevation.
	 * @param highValue The highest contour's elevation.
	 */
	private double getStepSize(int numContours, double lowValue, double highValue)
	{
		return (highValue-lowValue)/numContours;
	}
	
	/**
	 * Determine the resolution factor at which to draw the loaded data.
	 * @param numColumns - The number of columns in the data.
	 * @param numRows - The number of rows in the data.
	 * @return The appropriate resolution factor.
	 */
	private int getResFactor(int numColumns, int numRows) {
		return (int) Math.max(1., Math.ceil(Math.max(numColumns, numRows)/DEF_MAX_ROWS_COLUMNS));
	}
	
	/**
	 * Handles changes for a contour spinner change.  Recalculate step size and
	 * tell the GL context to redraw the contours.
	 */
	private void handleContourSpinnerChange()
	{
		stepSize = getStepSize(numCtrs, lowCtrVal, highCtrVal);
		if(spinnerShouldRedraw) contoursGenerated = false;
	}
	
	/**
	 * Handles changes for a resolution spinner change. Tell the GL context to
	 * redraw the mesh and contours to respect the new values.
	 */
	private void handleResolutionSpinnerChange()
	{
		meshGenerated = false;
		contoursGenerated = false;
	}

	/**
	 * A wrapper for drawMesh which stores the commands in a display list instead
	 * of executing them immediately. meshGenerated must not be true. A polygon offset
	 * allows the contours and marker to be drawn in front of the mesh by rendering 
	 * mesh fragments at a greater depth.
	 */
	private void generateMesh()
	{
		assert(!meshGenerated);
        gl2.glNewList(meshList, GL2.GL_COMPILE);
        gl2.glEnable(GL2.GL_POLYGON_OFFSET_FILL);
        gl2.glPolygonOffset(1.0f, 1.0f);
        drawMesh();
        gl2.glDisable(GL2.GL_POLYGON_OFFSET_FILL);
        gl2.glEndList();
	}

	/**
	 * A wrapper for drawContours which stores the commands in a display list instead
	 * of executing them immediately.  contoursGenerated must not be true.
	 */
	private void generateContours()
	{
		assert(!contoursGenerated);
        gl2.glNewList(contourList, GL2.GL_COMPILE);
        drawContours();
        gl2.glEndList();
	}
	
	/**
	 * drawContours loops across each of the cells for which we have data, and draws each of the contours
	 * that passes through it.
	 * 
	 * Cell Corner Numbering Scheme:
	 *					[0]------------[1]
	 *					 '              '
	 *					 '              '
	 *					 '              '
	 *					 '              '
	 *					[2]------------[3]
	 */
	private void drawContours()
	{
		if(gridData == null) return;
		
		double gridX = -gridWidth/2;
		double gridY = -gridHeight/2;

		//Iterate through cells left-to-right, bottom-to-top
		//Get the lower-left-hand corner of each as cellX, cellY
		for(int x = 0; x < gridData.numColumns-resFactor; x += resFactor)
		{
			double cellX = gridX + x*cellSizeX;
			for(int y = 0; y < gridData.numRows-resFactor; y += resFactor)
			{
				double cellY = gridY + (gridData.numRows-y)*cellSizeY;
				for(int i = 0; i < numCtrs; i++)
				{
					double thisContour = lowCtrVal + i*stepSize;
					double contourZVal = gridDepthScale * (thisContour-gridData.avgHeight);
					double[] ctrColor = CONTOUR_COLOR;
					
					boolean[] isHigher = new boolean[]{gridData.height[y][x] > thisContour,
													   gridData.height[y][x+resFactor] > thisContour,
													   gridData.height[y+resFactor][x] > thisContour,
													   gridData.height[y+resFactor][x+resFactor] > thisContour};
					
					double distPercent01 = calcDistancePercent(thisContour,x,y,x+resFactor,y);
					double distPercent02 = calcDistancePercent(thisContour,x,y,x,y+resFactor);
					double distPercent13 = calcDistancePercent(thisContour,x+resFactor,y,x+resFactor,y+resFactor);
					double distPercent23 = calcDistancePercent(thisContour,x,y+resFactor,x+resFactor,y+resFactor);
					
					double[] p01 = new double[]{cellX + cellSizeX*resFactor*distPercent01, cellY, contourZVal+CONTOUR_DISPLAY_OFFSET};
					double[] p02 = new double[]{cellX, cellY - cellSizeY*resFactor*distPercent02, contourZVal+CONTOUR_DISPLAY_OFFSET};
					double[] p13 = new double[]{cellX + cellSizeX*resFactor, cellY - cellSizeY*resFactor*distPercent13, contourZVal+CONTOUR_DISPLAY_OFFSET};
					double[] p23 = new double[]{cellX + cellSizeX*resFactor*distPercent23, cellY - cellSizeY*resFactor, contourZVal+CONTOUR_DISPLAY_OFFSET};
					
					//0 higher points: do nothing [1 case], or 4 higher points: do nothing [1 case]
					//1 higher point: draw 1 line [4 cases], or 3 higher points: draw 1 line. [4 cases]
					if((isHigher[0] && !isHigher[1] && !isHigher[2] && !isHigher[3])||
					   (!isHigher[0] && isHigher[1] && isHigher[2] && isHigher[3]))
						drawLine(p02, p01, ctrColor);
					else if ((!isHigher[0] && isHigher[1] && !isHigher[2] && !isHigher[3])||
							 (isHigher[0] && !isHigher[1] && isHigher[2] && isHigher[3]))
						drawLine(p01, p13, ctrColor);
					else if ((!isHigher[0] && !isHigher[1] && isHigher[2] && !isHigher[3])||
						     (isHigher[0] && isHigher[1] && !isHigher[2] && isHigher[3]))
						drawLine(p02, p23, ctrColor);
					else if ((!isHigher[0] && !isHigher[1] && !isHigher[2] && isHigher[3])||
							 (isHigher[0] && isHigher[1] && isHigher[2] && !isHigher[3]))
						drawLine(p23, p13, ctrColor);
					
					//2 adjacent higher points: draw 1 line. [4 cases]
					else if((isHigher[0] && isHigher[1] && !isHigher[2] && !isHigher[3])||
							(!isHigher[0] && !isHigher[1] && isHigher[2] && isHigher[3]))
						drawLine(p02, p13, ctrColor);
					else if((isHigher[0] && !isHigher[1] && isHigher[2] && !isHigher[3])||
							(!isHigher[0] && isHigher[1] && !isHigher[2] && isHigher[3]))
						drawLine(p01, p23, ctrColor);
					
					//2 opposite higher points: draw 2 lines. [2 cases]
					//This is the ambiguous case. I chose a configuration arbitrarily.
					else if((isHigher[0] && !isHigher[1] && !isHigher[2] && isHigher[3])||
							(!isHigher[0] && isHigher[1] && isHigher[2] && !isHigher[3]))
					{
						drawLine(p01, p13, ctrColor);
						drawLine(p02, p23, ctrColor);
					}
				}
			}
		}
	}

	/**
	 * drawMesh loops across each of the cells for which we have data, and draws a
	 * triangle strip for each column.
	 */
	private void drawMesh()
	{
		if(gridData == null) return;
		
		//Offsets for the grid, to center it.
		double gridX = -gridWidth/2;
		double gridY = -gridHeight/2;
		
		//Iterate through cells bottom-to-top, left-to-right
		//Get the lower-left-hand corner of each as cellX, cellY
		for(int x = 0; x < gridData.numColumns-resFactor; x += resFactor)
		{
			double cellX = gridX + x*cellSizeX;
			gl2.glBegin(GL.GL_TRIANGLE_STRIP);
			for(int y = 0; y < gridData.numRows; y += resFactor)
			{
				double cellY = gridY+(gridData.numRows-y)*cellSizeY;
				
				gl2.glColor3dv(DoubleBuffer.wrap(elevationToColor(gridData.height[y][x])));
				gl2.glVertex3dv(DoubleBuffer.wrap(new double[]{cellX, cellY, gridDepthScale*(gridData.height[y][x]-gridData.avgHeight)}));
				
				gl2.glColor3dv(DoubleBuffer.wrap(elevationToColor(gridData.height[y][x+resFactor])));
				gl2.glVertex3dv(DoubleBuffer.wrap(new double[]{cellX+cellSizeX*resFactor, cellY, gridDepthScale*(gridData.height[y][x+resFactor]-gridData.avgHeight)}));
			}
			gl2.glEnd();
		}
	}
	
	/**
	 * drawLine draws a single line between two three-component points.
	 * 
	 * @param v1 The first point.
	 * @param v2 The second point.
	 * @param c The color of the line.
	 */
	private void drawLine(double[] v1, double[] v2, double[] c)
	{
		gl2.glColor3d(c[0],c[1],c[2]);
		gl2.glBegin(GL.GL_LINES);
		gl2.glVertex3dv(DoubleBuffer.wrap(v1));
		gl2.glVertex3dv(DoubleBuffer.wrap(v2));
		gl2.glEnd();
	}
	
	/**
	 * drawPoint draws a single 3D point of the specified color.
	 * 
	 * @param pt The point.
	 * @param c The color of the point.
	 */
	private void drawPoint(double[] pt, double[] c)
	{
		gl2.glColor3d(c[0],c[1],c[2]);
		gl2.glBegin(GL.GL_POINTS);
		gl2.glVertex3dv(DoubleBuffer.wrap(pt));
		gl2.glEnd();
	}
	
	/**
	 * Returns the fraction of the distance between two points at which a
	 * provided elevation will fall, according to linear interpolation.
	 * @param cVal the contour value
	 * @param x0 the first point's x
	 * @param y0 the first point's y
	 * @param x1 the second point's x
	 * @param y1 the second point's y
	 * @return the fraction, a double
	 */
	private double calcDistancePercent(double cVal, int x0, int y0, int x1, int y1)
	{
		if(gridData == null) return -1;
		return (gridData.height[y0][x0]-cVal)/(gridData.height[y0][x0]-gridData.height[y1][x1]);
	}

	/**
	 * Interpolate between the low and high color to determine an appropriate color
	 * for an elevation between the low and high elevation.
	 * @param elevation the elevation to find a color for.
	 * @return the color, in a double array of length 3 (RGB)
	 */
	private double[] elevationToColor(double elevation)
	{
		double[] colorVal;
		if(elevation <= gridData.minHeight) colorVal = lowColor;
		else if (elevation >= gridData.maxHeight) colorVal = highColor;
		else if (gridData.minHeight == gridData.maxHeight) colorVal = lowColor;
		else
		{
			double percentElev = (elevation-gridData.minHeight)/(gridData.maxHeight-gridData.minHeight);
			colorVal = new double[]{lowColor[0] + colorDist[0]*percentElev,
									lowColor[1] + colorDist[1]*percentElev,
									lowColor[2] + colorDist[2]*percentElev};
		}
		return colorVal;
	}
	
	//------------------------------------------------------------------------------
    // GLEventListener Implementation
    
	@Override
	public void init(GLAutoDrawable glautodrawable) {
		gl2 = glautodrawable.getGL().getGL2();
		glu = new GLU();
		
		gl2.glClearColor(BACKGROUND_COLOR[0], BACKGROUND_COLOR[1],
				BACKGROUND_COLOR[2], 0f); //set background to dark gray
		gl2.glPointSize(10); //set up marker size/shape
        gl2.glEnable(GL2.GL_POINT_SMOOTH);
        gl2.glEnable(GL2.GL_DEPTH_TEST);
        
		//Generate display list numbers, and notify other parts of the program
		//that they've been generated.
		if(!listNumsGenerated)
		{
	        meshList = gl2.glGenLists(2);
	        contourList = meshList+1;
	        listNumsGenerated = true;
	        assert(meshList == 0); //glGenLists only returns 0 because of an error.
		}
	}

	/**
	 * dispose is called when the context is closed.
	 */
	@Override
	public void dispose(GLAutoDrawable glautodrawable) {
	}

	/**
	 * display is called when the context is redrawn (at 60FPS here,
	 * because of the FPSAnimator).
	 */
	@Override
	public void display(GLAutoDrawable glautodrawable) {
		//Only redraw when there's a loaded file.
		if(!currentDatafile.equals(""))
		{
			//Get our context
			gl2 = glautodrawable.getGL().getGL2();
			gl2.glMatrixMode(GL2.GL_MODELVIEW);
			gl2.glLoadIdentity();
			
			//Camera Setup
			glu.gluLookAt(camPos[0], camPos[1], camPos[2],
						  targetPos[0], targetPos[1], targetPos[2],
						  upVector[0], upVector[1], upVector[2]);
			gl2.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
			
			//Generate display lists if need be.
			if(!contoursGenerated)
			{
		        generateContours();
		        contoursGenerated = true;
			}
			if(!meshGenerated)
			{
		        generateMesh();
		        meshGenerated = true;
			}
			
			//Handle mouse input to apply rotation.
			if(mouseDown)
			{
				yRotation = yRotation+(ROTATION_SPEED_DEGS*(mouseX-canvasCenterX)/canvasCenterX);
				xRotation = xRotation+(ROTATION_SPEED_DEGS*(mouseY-canvasCenterY)/canvasCenterY);
				if(yRotation > 180) yRotation -= 360.;
				else if(yRotation < -180) yRotation += 360.;
				if(xRotation > 180) xRotation -= 360.;
				else if(xRotation < -180) xRotation += 360.;
			}
			gl2.glRotated(yRotation, 0, 1, 0);
			gl2.glRotated(xRotation, 1, 0, 0);
			
			//Determine if the mesh should be drawn as wireframe.
			if(showWireframe)
				gl2.glPolygonMode( GL2.GL_FRONT_AND_BACK, GL2.GL_LINE );
			
			//Call the display lists.
			gl2.glCallList(meshList);
			if(showContours)
				gl2.glCallList(contourList);
			
			//Return the renderer to normal.
			if(showWireframe)
				gl2.glPolygonMode( GL2.GL_FRONT_AND_BACK, GL2.GL_FILL );
			
			if(showMarker) //Draw the highest-point marker if enabled.
			{
				double[] highPt = new double[3];
				highPt[0] = cellSizeX * gridData.maxHeightXIdx - gridWidth/2;
				highPt[1] = cellSizeY * (gridData.numRows-1-gridData.maxHeightYIdx) - gridHeight/2;
				highPt[2] = gridDepthScale * (gridData.maxHeight-gridData.avgHeight) + MARKER_DISPLAY_OFFSET;
				drawPoint(highPt, markerColor);
			}
			gl2.glFlush(); //Ensure that everything is performed.	
		}
	}

	/**
	 * Handle window reshape events.
	 */
	@Override
	public void reshape(GLAutoDrawable glautodrawable, int x, int y, int width,
			int height) {
    	//Get the context.
    	gl2 = glautodrawable.getGL().getGL2();
    	
    	//Set up projection for the new window.
    	canvasCenterX = width/2;
    	canvasCenterY = height/2;
    	camAspectRatio = ((double)width)/height;
		gl2.glMatrixMode(GL2.GL_PROJECTION);
		gl2.glLoadIdentity();
		glu.gluPerspective(camFOV, camAspectRatio, camZNear, camZFar);
		gl2.glViewport(0,0,width,height);
	}

	
	//------------------------------------------------------------------------------
    // MouseListener, MouseWheelListener Implementation
	
	@Override
	public void mouseClicked(MouseEvent arg0) {}
	@Override
	public void mouseEntered(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}
	@Override
	public void mousePressed(MouseEvent e)
	{
		mouseDown = true;
	}
	@Override
	public void mouseReleased(MouseEvent e)
	{
		mouseDown = false;
	}

	@Override
	public void mouseMoved(MouseEvent e)
	{
		mouseX = e.getX();
		mouseY = e.getY();
	}
	@Override
	public void mouseDragged(MouseEvent e)
	{
		mouseX = e.getX();
		mouseY = e.getY();
	}
	
	@Override
	public void mouseWheelMoved(MouseWheelEvent event)
	{
		int wheelDir = event.getWheelRotation();
		camPos[2] += wheelDir*ZOOM_INCREMENT;
		if(camPos[2] < Z_MIN) camPos[2] = Z_MIN;
		else if (camPos[2] > Z_MAX) camPos[2] = Z_MAX;
	}
	
	
	//------------------------------------------------------------------------------
    // Main Call
	
	/**
	 * The main method simply calls the constructor.
	 * @param args The String list of command line arguments. (None are used.)
	 */
	public static void main(String[] args)
	{
		new TerrainVis();
	}
}
