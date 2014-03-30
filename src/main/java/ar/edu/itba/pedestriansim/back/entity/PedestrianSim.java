package ar.edu.itba.pedestriansim.back.entity;

import java.util.List;

import ar.edu.itba.pedestriansim.back.PedestrianAppConfig;
import ar.edu.itba.pedestriansim.back.component.Component;
import ar.edu.itba.pedestriansim.back.event.EventDispatcher;
import ar.edu.itba.pedestriansim.back.factory.ComponentFactory;
import ar.edu.itba.pedestriansim.back.factory.PedestrianAreaFactory;
import ar.edu.itba.pedestriansim.front.Camera;

public class PedestrianSim implements Updateable {

	private static final EventDispatcher dispatcher = EventDispatcher.instance();

	private PedestrianArea _pedestrianArea;
	private List<Component> _components;

	public PedestrianSim(PedestrianAppConfig config, Camera camera) {
		_pedestrianArea = new PedestrianAreaFactory(config).produce(camera);
		_components = new ComponentFactory(config).produce(_pedestrianArea);
		for (Component component : _components) {
			component.onStart();
		}
	}

	public void update(float elapsedTimeInSeconds) {
		dispatcher.update(elapsedTimeInSeconds);
		for (Updateable component : _components) {
			component.update(elapsedTimeInSeconds);
		}
	}

	public void end() {
		for (Component component : _components) {
			component.onEnd();
		}
	}

	public PedestrianArea getPedestrianArea() {
		return _pedestrianArea;
	}

}