/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner.components;

import java.awt.Image;

/**
 * Represents a condensator in a reactor, either RSH or LZH.
 * @author Brian McCloud
 */
public class Condensator extends ReactorItem {
    
    public Condensator(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
    }
    
    public Condensator(final Condensator other) {
        super(other);
    }

    @Override
    public boolean isHeatAcceptor() {
        return super.isHeatAcceptor() && (!usesGtnhRules() || currentHeat < getMaxHeat());
    }
    
    @Override
    public double adjustCurrentHeat(final double heat) {
        if (heat < 0.0) {
            return heat;
        }
        double acceptedHeat = Math.min(heat, getMaxHeat() - (usesGtnhRules() ? currentHeat : heat));
        currentCondensatorCooling += usesGtnhRules() ? acceptedHeat : heat;
        bestCondensatorCooling = Math.max(currentCondensatorCooling, bestCondensatorCooling);
        double result = heat - acceptedHeat;
        currentHeat += acceptedHeat;
        maxReachedHeat = Math.max(maxReachedHeat, currentHeat);
        return result;
    }

    @Override
    protected double heatForExchange() {
        return usesGtnhRules() ? 0 : currentHeat;
    }

    @Override
    protected double heatCapacityForExchange() {
        return getMaxHeat() + (usesGtnhRules() ? 1 : 0);
    }

    @Override
    public boolean needsCoolantInjected() {
        return currentHeat > 0.85 * getMaxHeat();
    }
    
    @Override
    public void injectCoolant() {
        currentHeat = 0;
    }

}
