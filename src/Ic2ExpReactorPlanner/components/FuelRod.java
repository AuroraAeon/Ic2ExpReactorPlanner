/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner.components;

import Ic2ExpReactorPlanner.SimulationProfile;
import Ic2ExpReactorPlanner.SimulationProfile.GtnhFuel;
import java.awt.Image;
import java.util.Arrays;

/**
 * Represents some form of fuel rod (may be single, dual, or quad).
 * @author Brian McCloud
 */
public class FuelRod extends ReactorItem {
    
    private final int energyMult;
    private final double heatMult;
    private final int rodCount;
    private final boolean moxStyle;
    private final GtnhFuel gtnhFuel;
    private final ReactorItem[] heatableNeighbors = new ReactorItem[4];
    
    public FuelRod(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod, 
            final int energyMult, final double heatMult, final int rodCount, final boolean moxStyle) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
        this.energyMult = energyMult;
        this.heatMult = heatMult;
        this.rodCount = rodCount;
        this.moxStyle = moxStyle;
        this.gtnhFuel = GtnhFuel.forComponent(id);
    }
    
    public FuelRod(final FuelRod other) {
        super(other);
        this.energyMult = other.energyMult;
        this.heatMult = other.heatMult;
        this.rodCount = other.rodCount;
        this.moxStyle = other.moxStyle;
        this.gtnhFuel = other.gtnhFuel;
    }
    
    @Override
    public boolean isNeutronReflector() {
        return !isBroken();
    }

    private int countNeutronNeighbors() {
        int neutronNeighbors = 0;
        ReactorItem component = parent.getComponentAt(row + 1, col);
        if (component != null && component.isNeutronReflector()) {
            neutronNeighbors++;
        }
        component = parent.getComponentAt(row - 1, col);
        if (component != null && component.isNeutronReflector()) {
            neutronNeighbors++;
        }
        component = parent.getComponentAt(row, col - 1);
        if (component != null && component.isNeutronReflector()) {
            neutronNeighbors++;
        }
        component = parent.getComponentAt(row, col + 1);
        if (component != null && component.isNeutronReflector()) {
            neutronNeighbors++;
        }
        return neutronNeighbors;
    }
    
    protected void handleHeat(final int heat) {
        int count = collectHeatAcceptor(0, row + 1, col);
        count = collectHeatAcceptor(count, row - 1, col);
        count = collectHeatAcceptor(count, row, col - 1);
        count = collectHeatAcceptor(count, row, col + 1);
        if (count == 0) {
            parent.adjustCurrentHeat(heat);
            currentHullHeating = heat;
        } else {
            currentComponentHeating = heat;
            int share = heat / count;
            for (int i = 0; i < count; i++) {
                heatableNeighbors[i].adjustCurrentHeat(share);
            }
            heatableNeighbors[0].adjustCurrentHeat(heat % count);
            Arrays.fill(heatableNeighbors, 0, count, null);
        }
    }

    private int collectHeatAcceptor(int count, int targetRow, int targetCol) {
        ReactorItem target = parent.getComponentAt(targetRow, targetCol);
        if (target != null && target.isHeatAcceptor()) {
            heatableNeighbors[count++] = target;
        }
        return count;
    }
    
    @Override
    public double generateHeat() {
        if (usesGtnhRules()) {
            return generateGtnhHeat();
        }
        int pulses = countNeutronNeighbors() + (rodCount == 1 ? 1 : (rodCount == 2) ? 2 : 3);
        int heat = (int)(heatMult * pulses * (pulses + 1));
        if (moxStyle && parent.isFluid() && (parent.getCurrentHeat() / parent.getMaxHeat()) > 0.5) {
            heat *= 2;
        }
        currentHeatGenerated = heat;
        minHeatGenerated = Math.min(minHeatGenerated, heat);
        maxHeatGenerated = Math.max(maxHeatGenerated, heat);
        handleHeat(heat);
        return currentHeatGenerated;
    }

    @Override
    public double generateEnergy() {
        if (usesGtnhRules()) {
            for (int cell = 0; cell < rodCount; cell++) {
                for (int pulse = 0; pulse < 1 + rodCount / 2; pulse++) {
                    receiveNeutronPulse(this, false);
                }
                pulseNeighbors(false);
            }
            finishPass(false);
            return currentEUGenerated;
        }
        int pulses = countNeutronNeighbors() + (rodCount == 1 ? 1 : (rodCount == 2) ? 2 : 3);
        double energy = energyMult * pulses;
        if (parent.usesLegacyGt509() || "GT5.09".equals(sourceMod)) {
            energy *= 2;//EUx2 if from GT5.09 or in GT5.09 mode
            if (moxStyle) {
                energy *= (1 + 1.5 * parent.getCurrentHeat() / parent.getMaxHeat());
            }
        } else if ("GTNH".equals(sourceMod)) {
            energy *= 10;//EUx10 if from GTNH or in GTNH mode
            if (moxStyle) {
                energy *= (1 + 1.5 * parent.getCurrentHeat() / parent.getMaxHeat());
            }
        } else if (moxStyle) {
            energy *= (1 + 4.0 * parent.getCurrentHeat() / parent.getMaxHeat());
        }
        minEUGenerated = Math.min(minEUGenerated, energy);
        maxEUGenerated = Math.max(maxEUGenerated, energy);
        currentEUGenerated = energy;
        parent.addEUOutput(energy);
        applyDamage(1.0);
        return energy;
    }

    @Override
    public double getMaxDamage(SimulationProfile profile) {
        return profile == SimulationProfile.GTNH_2_9_0 && gtnhFuel != null ? gtnhFuel.maxDamage : maxDamage;
    }

    @Override
    public double getMaxDamage() {
        return getMaxDamage(usesGtnhRules() ? SimulationProfile.GTNH_2_9_0 : SimulationProfile.LEGACY);
    }

    @Override
    boolean receiveNeutronPulse(FuelRod source, boolean heatPass) {
        if (isBroken()) {
            return false;
        }
        if (!heatPass) {
            float output = gtnhFuel.pulseEnergy;
            if (gtnhFuel.moxBonus > 0) {
                output *= 1f + gtnhFuel.moxBonus * hullFraction();
            }
            parent.addGtnhPulseOutput(output);
            currentEUGenerated += (double) (output * SimulationProfile.IC2_PULSE_ENERGY_FACTOR
                    * SimulationProfile.GTNH_NUCLEAR_CONFIG_FACTOR) * SimulationProfile.GAME_TICKS_PER_REACTOR_UPDATE;
        }
        return true;
    }

    public void finishEnergyPass() {
        if (usesGtnhRules() && currentEUGenerated > 0) {
            minEUGenerated = Math.min(minEUGenerated, currentEUGenerated);
            maxEUGenerated = Math.max(maxEUGenerated, currentEUGenerated);
        }
    }

    private double generateGtnhHeat() {
        for (int cell = 0; cell < rodCount; cell++) {
            int pulses = 1 + rodCount / 2 + pulseNeighbors(true);
            int triangular = pulses * (pulses + 1) / 2;
            if (gtnhFuel.moxBonus > 0 && parent.isFluid() && hullFraction() > 0.5f) {
                triangular *= 2;
            }
            int produced = Math.round(triangular * gtnhFuel.heat);
            currentHeatGenerated += produced;
            int count = collectHeatAcceptor(0, row, col - 1);
            count = collectHeatAcceptor(count, row, col + 1);
            count = collectHeatAcceptor(count, row - 1, col);
            count = collectHeatAcceptor(count, row + 1, col);
            int remaining = produced;
            for (int i = 0; i < count && remaining > 0; i++) {
                int share = remaining / (count - i);
                remaining -= share;
                remaining += (int) heatableNeighbors[i].adjustCurrentHeat(share);
            }
            Arrays.fill(heatableNeighbors, 0, count, null);
            if (remaining > 0) {
                parent.adjustCurrentHeat(remaining);
                currentHullHeating += remaining;
            }
            currentComponentHeating += produced - Math.max(remaining, 0);
        }
        minHeatGenerated = Math.min(minHeatGenerated, currentHeatGenerated);
        maxHeatGenerated = Math.max(maxHeatGenerated, currentHeatGenerated);
        finishPass(true);
        return currentHeatGenerated;
    }

    private int pulseNeighbors(boolean heatPass) {
        return pulse(row, col - 1, heatPass) + pulse(row, col + 1, heatPass)
                + pulse(row - 1, col, heatPass) + pulse(row + 1, col, heatPass);
    }

    private int pulse(int targetRow, int targetCol, boolean heatPass) {
        ReactorItem target = parent.getComponentAt(targetRow, targetCol);
        return target != null && target.receiveNeutronPulse(this, heatPass) ? 1 : 0;
    }

    private float hullFraction() {
        return (float) parent.getCurrentHeat() / (float) parent.getMaxHeat();
    }

    private void finishPass(boolean heatPass) {
        if (currentDamage >= gtnhFuel.maxDamage - 1) {
            // Unavailable to later callbacks, with the slot's state retained for reporting.
            currentDamage = gtnhFuel.maxDamage;
        } else if (heatPass) {
            applyDamage(1);
        }
    }
    
    @Override
    public int getRodCount() {
        return rodCount;
    }
    
    @Override
    public double getCurrentOutput() {
        if (parent != null) {
            if (parent.isFluid()) {
                return currentHeatGenerated;
            } else {
                return currentEUGenerated;
            }
        }
        return 0;
    }
    
}
