/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner.components;

import Ic2ExpReactorPlanner.SimulationProfile;
import java.awt.Image;

/**
 * Represents a neutron reflector in a reactor.
 * @author Brian McCloud
 */
public class Reflector extends ReactorItem {
    
    public Reflector(final int id, final String baseName, final String name, final Image image, final double maxDamage, final double maxHeat, final String sourceMod) {
        super(id, baseName, name, image, maxDamage, maxHeat, sourceMod);
    }
    
    public Reflector(final Reflector other) {
        super(other);
    }
    
    @Override
    public boolean isNeutronReflector() {
        return !isBroken();
    }

    @Override
    public double generateHeat() {
        if (usesGtnhRules()) {
            return 0;
        }
        ReactorItem component = parent.getComponentAt(row - 1, col);
        if (component != null) {
            applyDamage(component.getRodCount());
        }
        component = parent.getComponentAt(row, col + 1);
        if (component != null) {
            applyDamage(component.getRodCount());
        }
        component = parent.getComponentAt(row + 1, col);
        if (component != null) {
            applyDamage(component.getRodCount());
        }
        component = parent.getComponentAt(row, col - 1);
        if (component != null) {
            applyDamage(component.getRodCount());
        }
        return 0;
    }
    
    @Override
    public double getMaxDamage() {
        return getMaxDamage(usesGtnhRules() ? SimulationProfile.GTNH_2_9_0 : SimulationProfile.LEGACY);
    }

    @Override
    public double getMaxDamage(SimulationProfile profile) {
        if (maxDamage > 1 && (profile == SimulationProfile.GTNH_2_9_0
                || (parent != null && parent.usesLegacyEarlyReflectors()))) {
            return maxDamage / 3;
        }
        return maxDamage;
    }

    @Override
    boolean receiveNeutronPulse(FuelRod source, boolean heatPass) {
        if (isBroken()) {
            return false;
        }
        if (heatPass) {
            applyDamage(1);
        } else {
            source.receiveNeutronPulse(source, false);
        }
        return true;
    }
}
