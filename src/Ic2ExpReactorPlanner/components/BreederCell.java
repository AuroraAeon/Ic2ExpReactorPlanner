package Ic2ExpReactorPlanner.components;

import java.awt.Image;

/** GTNH lithium and glowstone rods absorb neutrons without reflecting them. */
public final class BreederCell extends ReactorItem {
    public BreederCell(int id, String baseName, String name, Image image) {
        super(id, baseName, name, image, 10_000, 1, "GTNH");
    }

    public BreederCell(BreederCell other) {
        super(other);
    }

    @Override
    boolean receiveNeutronPulse(FuelRod source, boolean heatPass) {
        if (heatPass && !isBroken()) {
            applyDamage(Math.min(maxDamage - currentDamage, 1 + (int) parent.getCurrentHeat() / 3_000));
        }
        return false;
    }
}
