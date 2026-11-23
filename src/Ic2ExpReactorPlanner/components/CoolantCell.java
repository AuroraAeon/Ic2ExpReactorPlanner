/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner.components;

import java.awt.Image;

/**
 * Represents a coolant cell in a reactor.
 * @author Brian McCloud
 */
public class CoolantCell extends ReactorItem {

    private boolean heatedInElectricReactor;
    
    public CoolantCell(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
    }
    
    public CoolantCell(final CoolantCell other) {
        super(other);
    }
    
    @Override
    public double adjustCurrentHeat(final double heat) {
        currentCellCooling += heat;
        bestCellCooling = Math.max(currentCellCooling, bestCellCooling);
        if (usesGtnhRules() && sourceMod != null && isHeatAcceptor()) {
            if (currentHeat == 0) {
                heatedInElectricReactor = false;
            }
            double refused = super.adjustCurrentHeat(heat);
            if (currentHeat > 0 && !parent.isFluid() && !isBroken()) {
                heatedInElectricReactor = true;
            }
            return refused;
        }
        return super.adjustCurrentHeat(heat);
    }

    @Override
    public boolean isHeatAcceptor() {
        return super.isHeatAcceptor() && (!usesGtnhRules() || !parent.isFluid() || !heatedInElectricReactor);
    }

    @Override
    public void reloadFreshHeat() {
        super.reloadFreshHeat();
        heatedInElectricReactor = false;
    }

    @Override
    public double getExplosionPowerOffset() {
        if (usesGtnhRules() && sourceMod != null && !isBroken()) {
            return 1f + (float) getMaxHeat() / 30_000f;
        }
        return 0;
    }
    
}
