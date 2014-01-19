package ar.edu.itba.pedestriansim.factory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

import org.newdawn.slick.geom.Line;
import org.newdawn.slick.geom.Rectangle;

import ar.edu.itba.pedestriansim.PedestrianAppConfig;
import ar.edu.itba.pedestriansim.back.PedestrianArea;
import ar.edu.itba.pedestriansim.gui.Camera;

import com.google.common.base.Optional;

public class PedestrianAreaFactory {

	private PedestrianAppConfig _config;
	
	public PedestrianAreaFactory(PedestrianAppConfig config) {
		_config = config;
	}
	
	public PedestrianArea produce(Camera camera) {
		try {
			PedestrianArea pedestrianArea = _config.getPedestrianArea();
			loadSources(pedestrianArea);
			loadObstacles(pedestrianArea);
			setupView(camera);
			return pedestrianArea;
		} catch (IOException e) {
    		throw new IllegalStateException(e);
    	}
	}
	
	private void loadSources(PedestrianArea pedestrianArea) throws IOException {
		PedestrianSourceFactory	sourceCreator = new PedestrianSourceFactory(pedestrianArea);
		PedestrianFactory pedestrianfactory = new PedestrianFactory(_config);
		for (Properties sourceConfig : _config.getPedestrianSources()) {
			pedestrianArea.addSource(sourceCreator.produce(sourceConfig, pedestrianfactory));
		}
	}
	
	private void loadObstacles(PedestrianArea pedestrianArea) {
		InputStream is = _config.getObjectFile();
		if (is == null) {
			return;
		}
		Scanner scanner = new Scanner(is);
		while (scanner.hasNextLine()) {
			String[] values = scanner.nextLine().split(" ");
			float x, y;
			switch (values[0]) {
			case "rectangle":
				x = Float.parseFloat(values[1]);
				y = Float.parseFloat(values[2]);
				float width = Float.parseFloat(values[3]);
				float height = Float.parseFloat(values[4]);
				pedestrianArea.addObstacle(new Rectangle(x, y, width, height));
				break;
			case "zoom":
				
				break;
			case "location":
				
				break;
			case "line":
				float x1 = Float.parseFloat(values[1]);
				float y1 = Float.parseFloat(values[2]);
				float x2 = Float.parseFloat(values[3]);
				float y2 = Float.parseFloat(values[4]);
				pedestrianArea.addObstacle(new Line(x1, y1, x2, y2));
			default:
				break;
			}
		}
		scanner.close();
	}

	private void setupView(Camera camera) {
		Optional<Integer> zoom = _config.getOptional("zoom", Integer.class);
		if (zoom.isPresent()) {
			camera.setZoom(zoom.get());
		}
		Optional<String[]> location = _config.getLocation();
		if (location.isPresent()) {
			camera.scrollX(Float.parseFloat(location.get()[0]));
			camera.scrollY(Float.parseFloat(location.get()[1]));
		}
	}
}