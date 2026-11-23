package Ic2ExpReactorPlanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Compares independent GTNH layouts using identical jobs and different worker counts. */
public final class IndependentLayoutsBenchmark {
    private static final int REPETITIONS = 8;
    private static final int SAMPLES = 8;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Locale.setDefault(Locale.US);
        Reactor passive = read("examples/gtnh-2.9.0-thorium-11-passive.txt");
        Reactor vacuum = read("examples/gtnh-2.9.0-thorium-11-vacuum.txt");
        var config = new VacuumCooling.Config(90, 15, 1, 2);
        List<Callable<Fingerprint>> jobs = new ArrayList<>();
        for (int i = 0; i < REPETITIONS; i++) {
            jobs.add(() -> Fingerprint.of(AutomationSimulator.simulate(passive)));
            jobs.add(() -> Fingerprint.of(AutomationSimulator.simulate(vacuum, config)));
        }
        List<Fingerprint> reference = null;
        System.out.println("workers,layouts,median_batch_ms,layouts_per_second");
        for (int workers : new int[]{1, 2, 4}) {
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            try {
                for (int warmup = 0; warmup < 4; warmup++) {
                    run(executor, jobs);
                }
                var times = new long[SAMPLES];
                for (int sample = 0; sample < SAMPLES; sample++) {
                    long start = System.nanoTime();
                    List<Fingerprint> actual = run(executor, jobs);
                    times[sample] = System.nanoTime() - start;
                    if (reference == null) {
                        reference = actual;
                    } else if (!reference.equals(actual)) {
                        throw new AssertionError("Result changed with worker count " + workers);
                    }
                }
                Arrays.sort(times);
                double medianNs = (times[SAMPLES / 2 - 1] + times[SAMPLES / 2]) / 2.0;
                System.out.printf("%d,%d,%.3f,%.2f%n", workers, jobs.size(), medianNs / 1e6, jobs.size() * 1e9 / medianNs);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static Reactor read(String file) throws Exception {
        Reactor reactor = new Reactor();
        reactor.setCode(Files.readString(Path.of(file)).strip());
        return reactor;
    }

    private static List<Fingerprint> run(ExecutorService executor, List<Callable<Fingerprint>> jobs) throws Exception {
        List<Fingerprint> results = new ArrayList<>(jobs.size());
        for (var future : executor.invokeAll(jobs)) {
            results.add(future.get());
        }
        return results;
    }

    private record Fingerprint(int steps, double eu, double maxHeat, boolean exploded, int firstBroken,
            GtnhThoriumCycle.Products products, VacuumCooling.Result cooler) {
        private static Fingerprint of(SimulationData data) {
            return new Fingerprint(data.totalReactorTicks, data.totalEUoutput, data.maxTemp, data.exploded,
                    data.firstComponentBrokenTime, data.thoriumProducts, data.vacuumCooling);
        }
    }
}
