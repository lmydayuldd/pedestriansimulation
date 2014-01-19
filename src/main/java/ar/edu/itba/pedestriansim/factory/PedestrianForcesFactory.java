package ar.edu.itba.pedestriansim.factory;

import org.newdawn.slick.geom.Vector2f;

import ar.edu.itba.pedestriansim.PedestrianAppConfig;
import ar.edu.itba.pedestriansim.back.Pedestrian;
import ar.edu.itba.pedestriansim.back.PedestrianForces;
import ar.edu.itba.pedestriansim.back.Pedestrians;
import ar.edu.itba.pedestriansim.back.force.ConstantValueForce;
import ar.edu.itba.pedestriansim.back.force.PedestrianForce;
import ar.edu.itba.pedestriansim.back.force.ReactionDistanceDesireForce;
import ar.edu.itba.pedestriansim.back.replusionforce.RepulsionForce;
import ar.edu.itba.pedestriansim.back.replusionforce.RepulsionForceModel1;
import ar.edu.itba.pedestriansim.physics.SpringForceModel;

import com.google.common.base.Function;

public class PedestrianForcesFactory {

	private static enum RepulsionForceType {MODEL_1};
	private static enum PedestrianInteractionType {BODY_LOCATION, FUTURE_LOCATION};
	private static enum DesireForceType {REACTION_DISTANCE};
	private static enum ForceOnFutureType {CONSTANT};

	private PedestrianAppConfig _config;

	public PedestrianForcesFactory(PedestrianAppConfig config) {
		_config = config;
	}

	public PedestrianForces produce() {
		PedestrianForces forces = new PedestrianForces();
		float springConstant = _config.get("springConstant", Float.class);
		forces.setCollisitionModel(new SpringForceModel(springConstant));
		float externalForceThreshold  = _config.get("externalForceThreshold", Float.class);
		forces.setExternalForceThreshold(externalForceThreshold);
		forces.setInteractionLocation(buildInteractionLocation());
		forces.setRepulsionForceModel(buildRepulsionForce());
		forces.setDesireForce(buildDesireForce());
		forces.setForceOnFuture(buildForceOnFuture());
		return forces;
	}
	
	private Function<Pedestrian, Vector2f> buildInteractionLocation() {
		Function<Pedestrian, Vector2f> interactionLocation;
		PedestrianInteractionType interactionType = _config.getEnum(PedestrianInteractionType.class);
		switch (interactionType) {
			case BODY_LOCATION:
				interactionLocation = Pedestrians.getLocation();
				break;
			case FUTURE_LOCATION:
				interactionLocation = Pedestrians.getFutureLocation();
				break;
			default:
				throw new IllegalStateException("Unknown type for PedestrianInteractionType: " + interactionType);
		}
		return interactionLocation;
	}

	private RepulsionForce buildRepulsionForce() {
		RepulsionForce repulsionForce;
		RepulsionForceType repulsionForceType = _config.getEnum(RepulsionForceType.class);
		switch (repulsionForceType) {
			case MODEL_1:
				repulsionForce = new RepulsionForceModel1(
					_config.getEnumParam(repulsionForceType, "alpha", Float.class),
					_config.getEnumParam(repulsionForceType, "beta", Float.class));
				break;
			default:
				throw new IllegalStateException("Unknown type for RepulsionForceModel: " + repulsionForceType);
		}
		return repulsionForce;
	}
	
	private PedestrianForce buildDesireForce() {
		PedestrianForce desireForce;
		DesireForceType desireForceType = _config.getEnum(DesireForceType.class);
		switch (desireForceType) {
			case REACTION_DISTANCE:
				desireForce = new ReactionDistanceDesireForce();
				break;
			default:
				throw new IllegalStateException("Unknown type for DesireForceType: " + desireForceType);
		}
		return desireForce;
	}
	
	private PedestrianForce buildForceOnFuture() {
		PedestrianForce forceOnFuture;
		ForceOnFutureType desireForceType = _config.getEnum(ForceOnFutureType.class);
		switch (desireForceType) {
			case CONSTANT:
				forceOnFuture = new ConstantValueForce();
				break;
			default:
				throw new IllegalStateException("Unknown type for DesireForceType: " + desireForceType);
		}
		return forceOnFuture;
	}
}
