package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.components.ReactorItem;

/** Rules selected independently for each reactor design. */
public enum SimulationProfile {
    LEGACY,
    GTNH_2_9_0;

    public static final int GAME_TICKS_PER_REACTOR_UPDATE = 20;
    public static final float IC2_PULSE_ENERGY_FACTOR = 5.0f;
    public static final float GTNH_NUCLEAR_CONFIG_FACTOR = 5.0f;
    public static final float GTNH_COOLANT_CONVERSION_FACTOR = 2.0f;

    // The installed GregTech item registrations are in LoaderGTBlockFluid.
    // Shared 1/2/4 variants differ only in the number of internal cells.
    public enum GtnhFuel {
        URANIUM(20_000, 2f, 4f, 0f),
        MOX(10_000, 2f, 4f, 1.5f),
        THORIUM(50_000, 0.4f, 1f, 0f),
        NAQUADAH(100_000, 4f, 4f, 0f),
        QUAD_NAQUADAH(100_000, 4f, 1f, 0f),
        NAQUADRIA(100_000, 4f, 4f, 1.5f),
        TIBERIUM(50_000, 2f, 2f, 0f),
        THE_CORE(100_000, 8f, 4f, 0f),
        HIGH_DENSITY_URANIUM(70_000, 4f, 4f, 0f),
        HIGH_DENSITY_PLUTONIUM(70_000, 2f, 4f, 6f),
        EXCITED_URANIUM(6_000, 48f, 64f, 0f),
        EXCITED_PLUTONIUM(10_000, 64f, 64f, 2f);

        public final int maxDamage;
        public final float pulseEnergy;
        public final float heat;
        public final float moxBonus;

        GtnhFuel(int maxDamage, float pulseEnergy, float heat, float moxBonus) {
            this.maxDamage = maxDamage;
            this.pulseEnergy = pulseEnergy;
            this.heat = heat;
            this.moxBonus = moxBonus;
        }

        public static GtnhFuel forComponent(int id) {
            return switch (id) {
                case 1, 2, 3 -> URANIUM;
                case 4, 5, 6 -> MOX;
                case 26, 27, 28 -> THORIUM;
                case 45, 46 -> NAQUADAH;
                case 47 -> QUAD_NAQUADAH;
                case 48, 49, 50 -> NAQUADRIA;
                case 51, 52, 53 -> TIBERIUM;
                case 54 -> THE_CORE;
                case 60, 61, 62 -> HIGH_DENSITY_URANIUM;
                case 63, 64, 65 -> HIGH_DENSITY_PLUTONIUM;
                case 66, 67, 68 -> EXCITED_URANIUM;
                case 69, 70, 71 -> EXCITED_PLUTONIUM;
                default -> null;
            };
        }
    }

    public boolean supports(ReactorItem item) {
        if (item == null) {
            return true;
        }
        if (this == GTNH_2_9_0) {
            return !"GT5.09".equals(item.sourceMod) && !"Coaxium".equals(item.sourceMod);
        }
        return item.id <= 58;
    }
}
