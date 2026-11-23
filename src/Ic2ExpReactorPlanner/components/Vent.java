/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner.components;

import java.awt.Image;

/**
 * Represents some kind of vent in a reactor.
 * @author Brian McCloud
 */
public class Vent extends ReactorItem {
    
    private final int selfVent;
    private final int hullDraw;
    private final int sideVent;
    
    public Vent(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod,
            final int selfVent, final int hullDraw, final int sideVent) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
        this.selfVent = selfVent;
        this.hullDraw = hullDraw;
        this.sideVent = sideVent;
    }
    
    public Vent(final Vent other) {
        super(other);
        this.selfVent = other.selfVent;
        this.hullDraw = other.hullDraw;
        this.sideVent = other.sideVent;
    }
    
    @Override
    public double dissipate() {
        double deltaHeat = Math.min(hullDraw, parent.getCurrentHeat());
        double heatBeforeDraw = getCurrentHeat();
        currentHullCooling = deltaHeat;
        parent.adjustCurrentHeat(-deltaHeat);
        this.adjustCurrentHeat(deltaHeat);
        // A melted IC2 vent still processes self-cooling on the original stack this pass.
        final double currentDissipation = Math.min(selfVent,
                usesGtnhRules() && isBroken() ? heatBeforeDraw : getCurrentHeat());
        currentVentCooling = currentDissipation;
        parent.ventHeat(currentDissipation);
        adjustCurrentHeat(-currentDissipation);
        if (sideVent > 0) {
            if (usesGtnhRules()) {
                coolNeighbor(row, col - 1);
                coolNeighbor(row, col + 1);
                coolNeighbor(row - 1, col);
                coolNeighbor(row + 1, col);
            } else {
                coolNeighbor(row - 1, col);
                coolNeighbor(row, col + 1);
                coolNeighbor(row + 1, col);
                coolNeighbor(row, col - 1);
            }
        }
        bestVentCooling = Math.max(bestVentCooling, currentVentCooling);
        return currentDissipation;
    }

    private void coolNeighbor(int targetRow, int targetCol) {
        ReactorItem target = parent.getComponentAt(targetRow, targetCol);
        if (target != null && (usesGtnhRules() ? target.isHeatAcceptor() : target.isCoolable())) {
            double cooled = sideVent + target.adjustCurrentHeat(-sideVent);
            parent.ventHeat(cooled);
            currentVentCooling += cooled;
        }
    }
    
    @Override
    public double getVentCoolingCapacity() {
        double result = selfVent;
        if (sideVent > 0) {
            ReactorItem component = parent.getComponentAt(row - 1, col);
            if (component != null && component.isCoolable()) {
                result += sideVent;
            }
            component = parent.getComponentAt(row, col + 1);
            if (component != null && component.isCoolable()) {
                result += sideVent;
            }
            component = parent.getComponentAt(row + 1, col);
            if (component != null && component.isCoolable()) {
                result += sideVent;
            }
            component = parent.getComponentAt(row, col - 1);
            if (component != null && component.isCoolable()) {
                result += sideVent;
            }
        }
        return result;
    }
    
    @Override
    public double getHullCoolingCapacity() {
        return hullDraw;
    }
    
    @Override
    public double getCurrentOutput() {
        return currentVentCooling;
    }
 }
