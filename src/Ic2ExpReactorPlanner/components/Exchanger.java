/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner.components;

import java.awt.Image;

/**
 * Represents a heat exchanger of some sort in a reactor.
 * @author Brian McCloud
 */
public class Exchanger extends ReactorItem {
    
    private final int switchSide;
    private final int switchReactor;
    
    public Exchanger(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod, final int switchSide, final int switchReactor) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
        this.switchSide = switchSide;
        this.switchReactor = switchReactor;
    }
    
    public Exchanger(final Exchanger other) {
        super(other);
        this.switchSide = other.switchSide;
        this.switchReactor = other.switchReactor;
    }
    
    @Override
    public void transfer() {
        // Code adapted from decompiled IC2 code, class ItemReactorHeatSwitch, with permission from Thunderdark.
        double myHeat = 0;
        if (switchSide > 0) {
            double myPercent = getCurrentHeat() * 100.0 / getMaxHeat();
            myHeat = exchangeWith(row, col - 1, myPercent, myHeat);
            myHeat = exchangeWith(row, col + 1, myPercent, myHeat);
            myHeat = exchangeWith(row - 1, col, myPercent, myHeat);
            myHeat = exchangeWith(row + 1, col, myPercent, myHeat);
        }
        if (switchReactor > 0) {
            double mymed = getCurrentHeat() * 100.0 / getMaxHeat();
            double Reactormed = parent.getCurrentHeat() * 100.0 / parent.getMaxHeat();

            int add = (int) Math.round(parent.getMaxHeat() / 100.0 * (Reactormed + mymed / 2.0));
            if (add > switchReactor) {
                add = switchReactor;
            }
            if (Reactormed + mymed / 2.0 < 1.0) {
                add = switchSide / 2;
            }
            if (Reactormed + mymed / 2.0 < 0.75) {
                add = switchSide / 4;
            }
            if (Reactormed + mymed / 2.0 < 0.5) {
                add = switchSide / 8;
            }
            if (Reactormed + mymed / 2.0 < 0.25) {
                add = 1;
            }
            if (Math.round(Reactormed * 10.0) / 10.0 > Math.round(mymed * 10.0) / 10.0) {
                add -= 2 * add;
            } else if (Math.round(Reactormed * 10.0) / 10.0 == Math.round(mymed * 10.0) / 10.0) {
                add = 0;
            }
            myHeat -= add;
            parent.adjustCurrentHeat(add);
            if (add > 0) {
                currentHullHeating = add;
            } else {
                currentHullCooling = -add;
            }
        }
        adjustCurrentHeat(myHeat);
    }

    private double exchangeWith(int targetRow, int targetCol, double myPercent, double pendingHeat) {
        ReactorItem target = parent.getComponentAt(targetRow, targetCol);
        if (target == null || !target.isHeatAcceptor()) {
            return pendingHeat;
        }
        double targetCapacity = target.heatCapacityForExchange();
        double targetPercent = target.heatForExchange() * 100.0 / targetCapacity;
        double average = targetPercent + myPercent / 2.0;

        int amount = Math.min(switchSide, (int) (targetCapacity / 100.0 * average));
        if (average < 0.25) {
            amount = 1;
        } else if (average < 0.5) {
            amount = switchSide / 8;
        } else if (average < 0.75) {
            amount = switchSide / 4;
        } else if (average < 1.0) {
            amount = switchSide / 2;
        }
        amount *= Long.compare(Math.round(myPercent * 10.0), Math.round(targetPercent * 10.0));
        if (amount > 0) {
            currentComponentHeating += amount;
        }
        return pendingHeat - amount + target.adjustCurrentHeat(amount);
    }
    
    @Override
    public double getHullCoolingCapacity() {
        return switchReactor;
    }
    
}
