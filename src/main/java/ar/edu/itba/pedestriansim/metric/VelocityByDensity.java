package ar.edu.itba.pedestriansim.metric;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.tuple.Pair;

import ar.edu.itba.command.CommandParam;
import ar.edu.itba.command.CommandParser;
import ar.edu.itba.command.ParsedCommand;
import ar.edu.itba.pedestriansim.back.PedestrianSimApp;
import ar.edu.itba.pedestriansim.back.config.HallwayConfig;
import ar.edu.itba.pedestriansim.back.entity.PedestrianAppConfig;
import ar.edu.itba.pedestriansim.back.entity.PedestrianAreaFileSerializer;
import ar.edu.itba.pedestriansim.back.entity.PedestrianAreaFileSerializer.DymaimcFileStep;
import ar.edu.itba.pedestriansim.back.entity.PedestrianAreaFileSerializer.StaticFileLine;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.io.Closer;

public class VelocityByDensity {

	public static final CommandParser parser;
	static {
		parser = new CommandParser()
			.param(new CommandParam("-newRun").required().constrained("true", "false").message("Crear nueva corrida?"))
			.param(new CommandParam("-metricsDir").defaultValue("metrics").message("Directorio a utilizar para guardar las metricas"))
		;
	}

	private static final int RUNS_COUNT = 3;

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.out.println(parser.getHelp());
			return;
		}
		ParsedCommand cmd = parser.parse(args);
		if (cmd.hasErrors()) {
			System.out.println(cmd.getErrorString());
			return;
		}
		boolean newRun = "true".equals(cmd.param("-newRun"));
		String metricsDir = cmd.param("-metricsDir");
		new VelocityByDensity(newRun, metricsDir).generate();
	}

	private final float[] thresholds = { 0f };
	private final float[] alphas = { 800 };
	@SuppressWarnings("serial")
	private List<Range<Float>> betas = new LinkedList<Range<Float>>() {{
		add(Range.closed(0.65f, 0.85f));
	}};

	private final File _metricsDirectory;
	private final File runsDirectory;
	private boolean _newRun;

	public VelocityByDensity(boolean newRun, String metricsDir) {
		_newRun = newRun;
		_metricsDirectory = new File(metricsDir);
		runsDirectory = new File(_metricsDirectory + File.separator + "runs");
		_metricsDirectory.mkdir();
		runsDirectory.mkdir();
	}

	public void generate() {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		for (final float threshold : thresholds) {
			for (final float alpha : alphas) {
				for (final Range<Float> beta : betas) {
					executor.execute(new Runnable() {
						@Override
						public void run() {
							String id = buildFileId(threshold, alpha, beta);
							List<PedestrianAppConfig> runs = runSimulations(id, alpha, beta, threshold);
							File output = new File(_metricsDirectory + File.separator + ".txt");
							List<Pair<Long, Float>> list = Lists.newArrayList();
							try {
								long start = System.currentTimeMillis();
								FileWriter writer = new FileWriter(output);
								for (PedestrianAppConfig config : runs) {
									Closer closer = Closer.create();
									PedestrianAreaFileSerializer serializer = new PedestrianAreaFileSerializer();
									Supplier<StaticFileLine> staticInfo = serializer.staticFileInfo(closer.register(new Scanner(config.staticfile())));
									Supplier<DymaimcFileStep> steps = serializer.steps(closer.register(new Scanner(config.dynamicfile())));
									float timeStep = config.pedestrianArea().timeStep().floatValue();
									list.addAll(new VBDFromFile(staticInfo, steps).runMetrics(timeStep));
									closer.close();
								}
								for (Pair<Long, Float> p: list) {
									writer.append(p.getLeft() + " " + p.getRight());
								}
								writer.close();
								System.out.println(System.currentTimeMillis() - start);
							} catch (IOException e) {
								throw new IllegalStateException(e);
							}
						}

						private String buildFileId(float threshold, float alpha, Range<Float> beta) {
							return "b=" + beta + "-a=" + alpha + "-t=" + threshold;
						}
					});
				}
			}
		}

		
	}

	private List<PedestrianAppConfig> runSimulations(String id, float alpha, Range<Float> beta, float threshold) {
		List<PedestrianAppConfig> runs = Lists.newArrayList();
		for (int runNumber = 0; runNumber < RUNS_COUNT; runNumber++) {
			String fileId = id + "-c=" + runNumber;
			System.out.println("Started: " + fileId);
			PedestrianAppConfig config = new HallwayConfig().get();
			config.setStaticfile(new File(runsDirectory + File.separator + fileId + "-static.txt"));
			config.setDynamicfile(new File(runsDirectory + File.separator + fileId + "-dynamic.txt"));
			config.pedestrianFactory().setPedestrianAlphaBeta(Pair.of(alpha, beta));
			config.setExternalForceThreshold(threshold);
			if (_newRun) {
				new PedestrianSimApp(config).run();
			}
			runs.add(config);
			System.out.println("Finished: " + fileId);
		}
		return runs;
	}
}


	

