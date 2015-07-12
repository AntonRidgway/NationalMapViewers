import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

/**
 * GridFloatReader is a simple class that reads in the GridFloat format
 * used by the National Map viewer hosted at viewer.nationalmap.gov.
 *  
 * @author Anton Ridgway
 */
public class GridFloatReader
{
	//Output diagnostic info?
	private final boolean CONSOLE_DEBUG = true;
	
	//Project file data (not currently used)
	private String projection, datum, zUnits, units, spheroid, xShift, yShift, parameters;
	
	//Header file data
	public int numColumns, numRows, noData, numEmptyCells, numCells;
	public boolean bigEndian;
	public double xLowerLeftCorner, yLowerLeftCorner, yUpperLeftCorner, xUpperLeftCorner, 
		cellSize, maxHeight, minHeight, avgHeight, minLat, minLong, maxLat, maxLong,
		cellSizeX, cellSizeY, cellSizeRatio;
	
	//Data file data
	public float[][] height;
	public int maxHeightXIdx, maxHeightYIdx, minHeightXidx, minHeightYIdx;
	private final double WORLD_RADIUS = 6371000.;
	
	/**
	 * The GridFloatReader constructor reads in the files associated with the input
	 * prefix: prefix.prj, prefix.hdr, and prefix.flt.
	 * 
	 * @param prefix - The filename (minus extension) for the GridFloat data to be read in.
	 */
	public GridFloatReader(String prefix)
	{
		try
		{
			//---Read Project File---
			Scanner scanner = new Scanner(new File(prefix+".prj"));
			Map<String,String> properties = new HashMap<String,String>();
			while(scanner.hasNextLine()) {
				String[] line = scanner.nextLine().split(" +");
				if(line.length == 2)
					properties.put(line[0], line[1]);
				else if (line.length == 2)
					properties.put(line[0], null);
			}
			projection = properties.get("Projection");
			datum = properties.get("Datum");
			zUnits = properties.get("Zunits");
			units = properties.get("Units");
			spheroid = properties.get("Spheroid");
			xShift = properties.get("Xshift");
			yShift = properties.get("Yshift");
			parameters = properties.get("Parameters");
			scanner.close();
			
			if(CONSOLE_DEBUG)
			{
				System.out.println(prefix+".prj read successfully."
									+"\nProjection: "+projection
									+"\nDatum: "+datum
									+"\nZunits: "+zUnits
									+"\nUnits: "+units
									+"\nSpheroid: "+spheroid
									+"\nXshift: "+xShift
									+"\nyShift: "+yShift
									+"\nParameters: "+parameters
									+"\n");
			}
		}
		catch(IOException e)
		{
			System.err.println(prefix+".prj could not be read in the root of the project directory.");
			e.printStackTrace();
		}
		try
		{
			//---Read Header File---
			BufferedReader headerFile = new BufferedReader(new FileReader(prefix+".hdr"));
			StringTokenizer parser = new StringTokenizer(headerFile.readLine());
			parser.nextToken(); //Skip the item name first
			String parseStr = parser.nextToken();
			numColumns = Integer.parseInt(parseStr);
			
			parser = new StringTokenizer(headerFile.readLine());
			parser.nextToken();
			parseStr = parser.nextToken();
			numRows = Integer.parseInt(parseStr);
			
			parser = new StringTokenizer(headerFile.readLine());
			parser.nextToken();
			parseStr = parser.nextToken();
			xLowerLeftCorner = Double.parseDouble(parseStr);
			
			parser = new StringTokenizer(headerFile.readLine());
			parser.nextToken();
			parseStr = parser.nextToken();
			yLowerLeftCorner = Double.parseDouble(parseStr);
			
			parser = new StringTokenizer(headerFile.readLine());
			parser.nextToken();
			parseStr = parser.nextToken();
			cellSize = Double.parseDouble(parseStr);
			
			parser = new StringTokenizer(headerFile.readLine());
			parser.nextToken();
			parseStr = parser.nextToken();
			noData = Integer.parseInt(parseStr);
			
			parser = new StringTokenizer(headerFile.readLine());
			parser.nextToken();
			parseStr = parser.nextToken();
			if(parseStr.equals("MSBFIRST")) //Java is most-significant bit first.
				bigEndian = true;
			else
				bigEndian = false;
			
			yUpperLeftCorner = yLowerLeftCorner + cellSize*numRows;
			xUpperLeftCorner = xLowerLeftCorner + cellSize*numColumns;
			minLong = xLowerLeftCorner;
			minLat = yLowerLeftCorner;
			maxLong = minLong + cellSize*numColumns;
			maxLat = minLat + cellSize*numRows;
			cellSizeX = haversine(minLat,minLong,minLat,maxLong)/numColumns;
			cellSizeY = haversine(minLat,minLong,maxLat,maxLong)/numRows;
			cellSizeRatio = cellSizeY/cellSizeX;
			headerFile.close();
			
			if(CONSOLE_DEBUG)
			{
				System.out.println(prefix+".hdr read successfully."
						+ "\n"+numColumns+" columns X "+numRows+" rows."
						+ "\nCell size: "+cellSizeX+" X "+cellSizeY+"."
						+ "\n"+minLat+"\u00b0 to "+maxLat+"\u00b0 latitude."
						+ "\n"+minLong+"\u00b0 to "+maxLong+"\u00b0 longitude."
						+ "\n");
			}
		}
		catch(IOException e)
		{
			System.err.println(prefix+".hdr could not be read in the root of the project directory.");
			e.printStackTrace();
		}
		try
		{
			//---Read Data File---
			DataInputStream dataFile = new DataInputStream(new FileInputStream(prefix+".flt"));
			maxHeight = Double.NEGATIVE_INFINITY;
			minHeight = Double.POSITIVE_INFINITY;
			height = new float[numRows][numColumns];
			for (int i = 0; i < numRows; i++)
				for (int j = 0; j < numColumns; j++)
				{
					height[i][j] = Float.intBitsToFloat(bigEndian ? dataFile.readInt() : Integer.reverseBytes(dataFile.readInt()));
					
					if (height[i][j] == noData)
						numEmptyCells++;
					else
					{
						avgHeight += height[i][j];
						numCells++;
					}
					if (height[i][j] > maxHeight)
					{
						maxHeight = height[i][j];
						maxHeightYIdx = i;
						maxHeightXIdx = j;
					}
					if (height[i][j] < minHeight)
					{
						minHeight = height[i][j];
						minHeightYIdx = i;
						minHeightXidx = j;
					}
				}
			avgHeight /= numCells;
			dataFile.close();
			
			if(CONSOLE_DEBUG)
			{
				System.out.println(prefix+".flt read successfully."
						+ "\nMin height: "+minHeight
						+ "\nMax height: "+maxHeight
						+ "\nAvg height: "+avgHeight
						+ "\n");
			}
		}
		catch(IOException e)
		{
			System.err.println(prefix+".flt could not be read in the root of the project directory.");
			e.printStackTrace();
		}
	}

	/**
	 * An implementation of the well-known Haversine formula to calculate the distance between two points
	 * in latitude-longitude coordinates. 
	 * @param lat1 - Latitude of the first point.
	 * @param lon1 - Longitude of the first point.
	 * @param lat2 - Latitude of the second point.
	 * @param lon2 - Longitude of the second point.
	 * @return The great-circle distance between the two points.
	 */
	public double haversine(double lat1, double lon1, double lat2, double lon2)
	{
		double dLat = Math.toRadians(lat2-lat1);
		double dLon = Math.toRadians(lon2-lon1);
		double a = Math.sin(dLat/2.) * Math.sin(dLat/2.)
				 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				 * Math.sin(dLon/2.) * Math.sin(dLon/2.);
		double c = 2. * Math.atan2(Math.sqrt(a),Math.sqrt(1.-a));
		return WORLD_RADIUS*c;
	}
}