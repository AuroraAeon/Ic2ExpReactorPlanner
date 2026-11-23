package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.components.CoolantCell;
import Ic2ExpReactorPlanner.components.ReactorItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Optional GTNH 2.9.0 external cooler. Each reactor update ends with one inventory poll.
 * Swaps consume cold stock of the same type and enqueue the outgoing cell for regeneration.
 */
public final class VacuumCooling {
    private static final int BASE_EU_PER_GAME_TICK = 120;
    private static final int MAX_COOLED_COMPONENT_ID = 59;

    public record Config(int thresholdPercent, int spareCellsPerType, int freezerCount, int overclocks) {
        public Config {
            if (thresholdPercent < 1 || thresholdPercent > 99 || spareCellsPerType < 0
                    || freezerCount < 1 || freezerCount > 1024 || overclocks < 0 || overclocks > 6) {
                throw new IllegalArgumentException("Invalid vacuum cooling settings");
            }
        }
    }

    public record Result(long swaps, long regenerated, int waitingOrCooling, int destroyed,
                         int firstShortageCycle, double heatRemoved, long energyConsumedEu) {}

    private record Slot(int row, int col, int componentId) {}

    private record Job(int componentId, long startTick, long finishTick) {}

    private final Config config;
    private final int euPerGameTick;
    private final long[] freezerAvailableAt;
    private final int[] coldStock = new int[MAX_COOLED_COMPONENT_ID + 1];
    private final PriorityQueue<Job> jobs = new PriorityQueue<>(Comparator.comparingLong(Job::finishTick));
    private final List<Slot> slots = new ArrayList<>();
    private boolean initialized;
    private long gameTick;
    private long swaps;
    private long regenerated;
    private int destroyed;
    private int firstShortageCycle;
    private double heatRemoved;
    private long finishedEnergyEu;

    public VacuumCooling(Config config) {
        this.config = Objects.requireNonNull(config);
        freezerAvailableAt = new long[config.freezerCount()];
        euPerGameTick = BASE_EU_PER_GAME_TICK << (2 * config.overclocks());
    }

    public static boolean supports(ReactorItem item) {
        return item instanceof CoolantCell && baseDurationGameTicks(item.id) > 0;
    }

    /** Nominal 5.09.54.265 VacuumFreezerRecipes, keyed by stable planner component ID. */
    static int baseDurationGameTicks(int componentId) {
        return switch (componentId) {
            case 14 -> 10;
            case 15 -> 30;
            case 16, 29, 32 -> 60;
            case 30, 33, 55 -> 180;
            case 31, 34, 56 -> 360;
            case 57 -> 540;
            case 58 -> 1080;
            case 59 -> 1_000_000;
            default -> 0;
        };
    }

    public void initialize(Reactor reactor) {
        if (initialized) {
            throw new IllegalStateException("Vacuum cooling state belongs to one simulation");
        }
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < reactor.getColumns(); col++) {
                ReactorItem item = reactor.getComponentAt(row, col);
                if (supports(item)) {
                    slots.add(new Slot(row, col, item.id));
                    coldStock[item.id] = config.spareCellsPerType();
                }
            }
        }
        initialized = true;
    }

    /** Run after chamber processing and before the next reactor update. */
    public void service(Reactor reactor, int reactorCycle) {
        if (!initialized) {
            throw new IllegalStateException("Initialize vacuum cooling before servicing it");
        }
        gameTick = (long) reactorCycle * SimulationProfile.GAME_TICKS_PER_REACTOR_UPDATE;
        while (!jobs.isEmpty() && jobs.peek().finishTick() <= gameTick) {
            Job finished = jobs.remove();
            coldStock[finished.componentId()]++;
            regenerated++;
            finishedEnergyEu += (finished.finishTick() - finished.startTick()) * euPerGameTick;
        }
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            ReactorItem item = reactor.getComponentAt(slot.row(), slot.col());
            if (item == null || item.id != slot.componentId() || item.isBroken()) {
                destroyed++;
                slots.remove(index--);
                continue;
            }
            double heat = item.getCurrentHeat();
            if (heat * 100 < item.getMaxHeat() * config.thresholdPercent()) {
                continue;
            }
            int id = item.id;
            if (coldStock[id] == 0) {
                if (firstShortageCycle == 0) {
                    firstShortageCycle = reactorCycle;
                }
                continue;
            }
            coldStock[id]--;
            item.reloadFreshHeat();
            heatRemoved += heat;
            swaps++;
            enqueue(id);
        }
    }

    private void enqueue(int id) {
        int nextFreezer = 0;
        for (int index = 1; index < freezerAvailableAt.length; index++) {
            if (freezerAvailableAt[index] < freezerAvailableAt[nextFreezer]) {
                nextFreezer = index;
            }
        }
        long startTick = Math.max(gameTick, freezerAvailableAt[nextFreezer]);
        int duration = Math.max(1, baseDurationGameTicks(id) >> config.overclocks());
        long finishTick = startTick + duration;
        freezerAvailableAt[nextFreezer] = finishTick;
        jobs.add(new Job(id, startTick, finishTick));
    }

    public Result result() {
        long energyConsumedEu = finishedEnergyEu;
        for (Job job : jobs) {
            energyConsumedEu += Math.max(0, gameTick - job.startTick()) * euPerGameTick;
        }
        return new Result(swaps, regenerated, jobs.size(), destroyed, firstShortageCycle, heatRemoved,
                energyConsumedEu);
    }
}
