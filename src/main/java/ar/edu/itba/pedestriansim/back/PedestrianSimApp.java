package ar.edu.itba.pedestriansim.back;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;

import ar.edu.itba.command.CommandParam;
import ar.edu.itba.command.CommandParser;
import ar.edu.itba.command.ParsedCommand;
import ar.edu.itba.common.rand.RandomGenerator;
import ar.edu.itba.common.rand.UniformRandomGenerator;
import ar.edu.itba.pedestriansim.back.config.ApplicationConfigBuilder;
import ar.edu.itba.pedestriansim.back.config.CrossingConfig;
import ar.edu.itba.pedestriansim.back.config.DefaultPedestrianAppConfig;
import ar.edu.itba.pedestriansim.back.config.HallwayConfig;
import ar.edu.itba.pedestriansim.back.config.PedestrianConfigurationFromFile;
import ar.edu.itba.pedestriansim.back.config.SquareRoomConfig;
import ar.edu.itba.pedestriansim.back.entity.PedestrianAppConfig;
import ar.edu.itba.pedestriansim.back.entity.PedestrianArea;
import ar.edu.itba.pedestriansim.back.entity.PedestrianForces;
import ar.edu.itba.pedestriansim.back.entity.PedestrianSim;
import ar.edu.itba.pedestriansim.back.factory.PedestrianForcesFactory;
import ar.edu.itba.pedestriansim.back.logic.FutureForceUpdaterComponent;
import ar.edu.itba.pedestriansim.back.logic.FuturePositionUpdaterComponent;
import ar.edu.itba.pedestriansim.back.logic.PedestrianAreaStateFileWriter;
import ar.edu.itba.pedestriansim.back.logic.PedestrianAreaStep;
import ar.edu.itba.pedestriansim.back.logic.PedestrianForceUpdaterComponent;
import ar.edu.itba.pedestriansim.back.logic.PedestrianPositionUpdaterComponent;
import ar.edu.itba.pedestriansim.back.logic.ProducePedestrians;
import ar.edu.itba.pedestriansim.back.logic.RemovePedestriansOnTarget;
import ar.edu.itba.pedestriansim.back.logic.SocialForceUpdaterComponent;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.io.Closer;

public class PedestrianSimApp implements Runnable {

	public static final CommandParser parser;
	static {
		parser = new CommandParser()
			.param(new CommandParam("-config").message("Archivo (.properties) de donde se va a leer la configuracion de la aplicacion."))
			.param(new CommandParam("-map").required().constrained("cross", "hallway", "room").message("Mapa en el cual correr la simulacion"))
			.param(new CommandParam("-static").message("Archivo con informacion estatica de los peatones"))
			.param(new CommandParam("-dynamic").message("Archivo con informacion dinamica de los peatones"))
			.param(new CommandParam("-door").message("Tamano de la puera (-map room unicamente!)"))
			.param(new CommandParam("-amount").message("Cantidad total de peatones(-map room unicamente!)"))
			.param(new CommandParam("-time").message("Tiempo de simulacion en segundos"))
			.param(new CommandParam("-outdir").message("Directorio donde guardar los archivos de salida"))
		;
	}
	public static void main(String[] args) throws FileNotFoundException, IOException {
		ParsedCommand cmd = parser.parse(args);
		if (cmd.hasErrors()) {
			System.out.println(cmd.getErrorString());
			return;
		}
		ApplicationConfigBuilder configBuilder;
		if (cmd.hasParam("-config")) {
			Properties properties = new Properties();
			properties.load(new FileInputStream(cmd.param("-config")));
			configBuilder = new PedestrianConfigurationFromFile(properties);
		} else {
			String outdir = cmd.hasParam("-outdir") ? cmd.param("-outdir") : "";
			configBuilder = new DefaultPedestrianAppConfig(outdir);
		}
		String mapName = cmd.param("-map").toLowerCase();
		configBuilder = "cross".equals(mapName) ? new CrossingConfig(configBuilder) 
			: "hallway".equals(mapName) ? new HallwayConfig(configBuilder) 
			: new SquareRoomConfig(configBuilder);
		if (mapName.equals("room") && cmd.hasParam("-door")) {
			((SquareRoomConfig) configBuilder).setDoorWidth(Float.valueOf(cmd.param("-door")));
		}
		if (mapName.equals("room") && cmd.hasParam("-amount")) {
			((SquareRoomConfig) configBuilder).setAmount(Integer.valueOf(cmd.param("-amount")));
		}
		PedestrianAppConfig config = configBuilder.get();
		if (cmd.hasParam("-static")) {
			config.setStaticfile(new File(cmd.param("-static")));
			config.makeNewRun(false);
		}
		if (cmd.hasParam("-dynamic")) {
			config.setDynamicfile(new File(cmd.param("-dynamic")));
			config.makeNewRun(false);
		}
		if (cmd.hasParam("-time")) {
			config.setSimulationTime(Float.valueOf(cmd.param("-time")));
		}
		new PedestrianSimApp(config).run();
	}

	private static final Logger logger = Logger.getLogger(PedestrianSimApp.class);

	private final boolean FUTURE_FORCE_MODEL = true;
	private final PedestrianAppConfig _config;

	public PedestrianSimApp(PedestrianAppConfig config) {
		_config = Preconditions.checkNotNull(config);
	}

	@Override
	public void run() {
		logger.info("[Simulation] started");
		configure(new PedestrianSim(_config.pedestrianArea())).run();
		logger.info("[Simulation] Finished");
	}

	private PedestrianSim configure(PedestrianSim sim) {
		final Closer fileCloser = Closer.create();
		sim.cutCondition(new Predicate<PedestrianArea>() {
			@Override
			public boolean apply(PedestrianArea input) {
				return input.elapsedTime().floatValue() > _config.simulationTime();
			}
		});
		sim.onStep(new PedestrianAreaStateFileWriter(
			fileCloser.register(newFileWriter(_config.staticfile())), 
			fileCloser.register(newFileWriter(_config.dynamicfile())), 0.02f)
		);
		if (FUTURE_FORCE_MODEL) {
			configureFutureModelComponents(sim);
		} else {
			configureSocialForceModelComponents(sim);
		}
		sim
			.onStep(new PedestrianPositionUpdaterComponent())
			.onStep(new RemovePedestriansOnTarget())
			.onStep(new ProducePedestrians(_config.pedestrianFactory()))
			.onStep(new PedestrianAreaStep() {
				private int _lastP = 0;
				@Override
				public void update(PedestrianArea input) {
					input.addElapsedTime(input.timeStep());
					int p = (int) (input.elapsedTime().floatValue() * 100 / _config.simulationTime());
					if (p != _lastP && p % 10 == 0) {
						System.out.println(p + "%");
						_lastP = p;
					}
				}
			})
			.onEnd(new Function<PedestrianArea, PedestrianArea>() {
				@Override
				public PedestrianArea apply(PedestrianArea input) {
					try {
						fileCloser.close();
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
					return input;
				}
			});
		return sim;
	}

	private FileWriter newFileWriter(File file) {
		try {
			return new FileWriter(file);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void configureFutureModelComponents(PedestrianSim sim) {
		final PedestrianForces forces = new PedestrianForcesFactory().build(_config);
		final float noiseP = 1f;
		sim 
			.onStep(new FutureForceUpdaterComponent(forces))
			.onStep(new FuturePositionUpdaterComponent(
				Optional.<RandomGenerator>absent(),
				Optional.of(new UniformRandomGenerator(-noiseP, noiseP))
			))
			.onStep(new PedestrianForceUpdaterComponent(forces))
//			.onStep(new KillPedestrianStuckOnWall())
		;
	}
	
	private void configureSocialForceModelComponents(PedestrianSim sim) {
		sim.onStep(new SocialForceUpdaterComponent());
	}
}

