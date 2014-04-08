package ar.edu.itba.pedestriansim.back.component;

import org.newdawn.slick.geom.Line;
import org.newdawn.slick.geom.Vector2f;

import ar.edu.itba.pedestriansim.back.entity.Pedestrian;
import ar.edu.itba.pedestriansim.back.entity.PedestrianArea;
import ar.edu.itba.pedestriansim.back.entity.PedestrianForces;
import ar.edu.itba.pedestriansim.back.physics.Vectors;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class FutureForceUpdaterComponent extends Component {

	private final Vector2f externalForcesOnFuture = new Vector2f();

	private final PedestrianArea scene;
	private final PedestrianForces _pedestrianForces;
	
	private Vector2f cache = new Vector2f();

	public FutureForceUpdaterComponent(PedestrianArea scene, PedestrianForces pedestrianForces) {
		this.scene = scene;
		_pedestrianForces = pedestrianForces;
	}

	public void update(float elapsedTimeInSeconds) {
		for (Pedestrian subject : scene.getPedestrians()) {
			updatePedestrianFuture(subject);
		}
	}

	private void updatePedestrianFuture(Pedestrian me) {
		Vector2f futureLocation = me.getFuture().getBody().getCenter();
		externalForcesOnFuture.set(Vectors.nullVector());
		float radiusThreshold = _pedestrianForces.getExternalForceRadiusThreshold();
		boolean externalForcesUnderThreshold = false;
		if (futureIsFurtherThan(radiusThreshold, me)) {
			Iterable<Pedestrian> pedestriansToAvoid = removeOnBack(me, scene.getPedestriansAndSkip(me));
			for (Pedestrian other : pedestriansToAvoid) {
				// TODO: the repulsion force is calculated once for (A,B) and then for (B,A) event though
				// the result is the same, use a cache objet here!
				Vector2f interactionLocation = _pedestrianForces.getInteractionLocation().apply(other);
				Vector2f repulsionForce = _pedestrianForces.getRepulsionForceModel().apply(futureLocation, interactionLocation);
				externalForcesOnFuture.add(repulsionForce);
			}
			float threshold = _pedestrianForces.getExternalForceThreshold();
			if (externalForcesOnFuture.lengthSquared() < threshold * threshold) {
				externalForcesOnFuture.set(Vectors.nullVector());
				setFutureInDesiredPath(me);
				externalForcesUnderThreshold = true;
			}
		}
		if (!externalForcesUnderThreshold) {
			// XXX: no need to apply force on future if no external forces exists
			// because it is positioned on the desired path
			externalForcesOnFuture.add(_pedestrianForces.getForceOnFuture().apply(me));
		}
		me.getFuture().getBody().setAppliedForce(externalForcesOnFuture);
	}
	
	private boolean futureIsFurtherThan(float distance, Pedestrian me) {
		Vector2f futureLocation = me.getFuture().getBody().getCenter();
		float radiusSum = me.getBody().getRadius() + me.getFuture().getBody().getRadius();
		return (me.getBody().getCenter().distance(futureLocation) - radiusSum) > distance;
	}
	
	private Iterable<Pedestrian> removeOnBack(Pedestrian me, Iterable<Pedestrian> others) {
		Vector2f center = me.getBody().getCenter();
		final Line meToTargetLine = new Line(center, me.getTargetSelection().getTarget().getClosesPoint(center));
		float[] normal = meToTargetLine.getNormal(0);
		cache.set(meToTargetLine.getStart());
		cache.x += normal[0];
		cache.y += normal[1];
		// TODO: esto crea demasiada basura, podria hacerse mas eficiente
		return Iterables.filter(others, new Predicate<Pedestrian>() {
			@Override
			public boolean apply(Pedestrian input) {
				Vector2f inputLocation = input.getBody().getCenter();
				return Vectors.isLeft(meToTargetLine.getStart(), cache, inputLocation);	
			}
		});
	}
	
	private void setFutureInDesiredPath(Pedestrian me) {
		Vector2f targetCenter = me.getTargetSelection().getTarget().getClosesPoint(me.getBody().getCenter());
		float distance = me.getReactionDistance();
		Vectors.pointBetween(me.getBody().getCenter(), targetCenter, distance, cache);
		me.getFuture().getBody().setLocation(cache);
		me.getFuture().getBody().setAppliedForce(Vectors.nullVector());
		me.getFuture().getBody().setVelocity(Vectors.nullVector());
	}
}
