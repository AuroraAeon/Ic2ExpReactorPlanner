package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.components.ReactorItem;

/**
 * Counts actual spent thorium rod items and their deterministic thermal centrifuge yields.
 * Recipe source: GT5-Unofficial 5.09.54.265, FissionFuelLoader.
 */
public final class GtnhThoriumCycle {
    public static final int CENTRIFUGE_GAME_TICKS_PER_ROD = 500;
    public static final int CENTRIFUGE_EU_PER_GAME_TICK = 48;

    private long singleRods;
    private long dualRods;
    private long quadRods;

    public record Products(long singleRods, long dualRods, long quadRods) {
        public long spentRods() {
            return singleRods + dualRods + quadRods;
        }

        public double lutetiumDust() {
            return singleRods / 2.0 + dualRods + 2.0 * quadRods;
        }

        public long thoriumDust() {
            return singleRods + 2 * dualRods + 4 * quadRods;
        }

        public long ironDust() {
            return singleRods + 3 * dualRods + 6 * quadRods;
        }

        public long centrifugeGameTicks() {
            return spentRods() * CENTRIFUGE_GAME_TICKS_PER_ROD;
        }

        public long centrifugeEnergyEu() {
            return centrifugeGameTicks() * CENTRIFUGE_EU_PER_GAME_TICK;
        }
    }

    /** Report each transition to a spent item once, before planner automation can replace it. */
    public void recordDepletion(ReactorItem item) {
        switch (item.id) {
            case 26 -> singleRods++;
            case 27 -> dualRods++;
            case 28 -> quadRods++;
            default -> { }
        }
    }

    public Products result() {
        return new Products(singleRods, dualRods, quadRods);
    }
}
