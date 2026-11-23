package Ic2ExpReactorPlanner;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Runs against upstream as well as the current implementation. */
public final class SimulatorBenchmark {
    private static final int WARMUPS = 8;
    private static final int SAMPLES = 12;
    private static final int BATCH = 3;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Locale.setDefault(Locale.US);
        if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("A JDK exposing per-thread allocation counters is required");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        long threadId = Thread.currentThread().getId();
        System.out.println("scenario,median_ms_per_simulation,reactor_steps_per_second,median_bytes_per_simulation,ticks,total_EU,total_HU,min_hull,max_hull,first_depleted,first_broken");
        for (String scenario : List.of("sparse-uranium", "dense-eu", "fluid-exchangers", "automated-thorium")) {
            for (int i = 0; i < WARMUPS; i++) {
                simulate(scenario);
            }
            double[] nanos = new double[SAMPLES];
            double[] bytes = new double[SAMPLES];
            SimulationData result = null;
            for (int i = 0; i < SAMPLES; i++) {
                long allocation = bean.getThreadAllocatedBytes(threadId);
                long start = System.nanoTime();
                for (int b = 0; b < BATCH; b++) {
                    result = simulate(scenario);
                }
                nanos[i] = (System.nanoTime() - start) / (double) BATCH;
                bytes[i] = (bean.getThreadAllocatedBytes(threadId) - allocation) / (double) BATCH;
            }
            double duration = median(nanos);
            if (result == null || result.totalReactorTicks == 0) {
                throw new IllegalStateException("Benchmark did not finish: " + scenario);
            }
            System.out.printf("%s,%.3f,%.0f,%.0f,%d,%.2f,%.2f,%.2f,%.2f,%d,%d%n",
                    scenario, duration / 1e6, result.totalReactorTicks * 1e9 / duration, median(bytes),
                    result.totalReactorTicks, result.totalEUoutput, result.totalHUoutput,
                    result.minTemp, result.maxTemp, result.firstRodDepletedTime, result.firstComponentBrokenTime);
        }
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        return (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
    }

    private static SimulationData simulate(String scenario) throws Exception {
        Reactor reactor = new Reactor();
        reactor.setMaxSimulationTicks(30_000);
        switch (scenario) {
            case "sparse-uranium" -> {
                place(reactor, 0, 0, "fuelRodUranium");
                place(reactor, 0, 1, "heatVent");
            }
            case "dense-eu" -> {
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        place(reactor, row, col, row % 2 == 0 && col % 2 == 0
                                ? "fuelRodUranium" : "advancedHeatVent");
                    }
                }
            }
            case "fluid-exchangers" -> {
                reactor.setFluid(true);
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        String component = switch ((row + col) % 4) {
                            case 0 -> "fuelRodMox";
                            case 1 -> "advancedHeatVent";
                            case 2 -> "heatExchanger";
                            default -> "componentHeatVent";
                        };
                        place(reactor, row, col, component);
                    }
                }
            }
            case "automated-thorium" -> {
                reactor.setAutomated(true);
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 5; col++) {
                        boolean fuel = (row + col) % 2 == 0;
                        place(reactor, row, col, fuel ? "quadFuelRodThorium" : "coolantCellHelium360k");
                        if (!fuel) {
                            reactor.getComponentAt(row, col).setAutomationThreshold(300_000);
                        }
                    }
                }
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        AutomationSimulator simulator = new AutomationSimulator(reactor, null, null, null, 0) {
            @Override
            protected void process(List<String> chunks) {
                // Keep the same benchmark independent of Swing painting and EDT throughput.
            }
        };
        simulator.doInBackground();
        return simulator.getData();
    }

    private static void place(Reactor reactor, int row, int col, String name) {
        var item = ComponentFactory.createComponent(name);
        if (item == null) {
            throw new IllegalArgumentException(name);
        }
        reactor.setComponentAt(row, col, item);
    }
}
