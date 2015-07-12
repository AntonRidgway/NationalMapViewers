# NationalMapViewers
Java software to visualize terrain data in GridFloat format, as provided by the USGS.
It provides a 3D contour plot visualization (TerrainVis.java), as well as a to-scale scene walkthrough (SceneWalkthrough.java).

Note that this project is dependent on JOGL for its rendering. I made use of the following guide to quickly set up the dependency: https://jogamp.org/wiki/index.php/Setting_up_a_JogAmp_project_in_your_favorite_IDE

Compatible terrain data for this project can be acquired from the [USGS National Map viewer](http://viewer.nationalmap.gov/).

1. Use the map to find an area you'd like to download data for.
2. Click the "Download by Bounding Box" button in the toolbar above the map, and draw a bounding box around the area.
3. A pop-up window should appear with data options for the selected area. Choose "Elevation DEM Products" and press "Next".
4. Find a GridFloat-format dataset to your liking and check the box. Highlighting different options will highlight the portion of your bounding box that the data pertains to.
5. Press "Next" to exit the pop-up window. The data you've selected will appear in the cart on the left-hand side of the menu.
6. Press "Checkout" and enter your email address to have the data sent to you.

Since this data is in the public domain, I've taken the liberty of including some data of the area surrounding Mt. Rainier in Washington. The data is compressed to conserve space.

Map services and data available from U.S. Geological Survey, National Geospatial Program.
The "flare" and "grass" images included with the project are public domain, courtesy of Wikimedia Commons, while the flag is my own.

* Grass: https://commons.wikimedia.org/wiki/File:Rasen.jpg
* Flare: https://commons.wikimedia.org/wiki/File:FlameV1.png
