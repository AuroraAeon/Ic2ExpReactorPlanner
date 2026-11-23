package Ic2ExpReactorPlanner;

import static org.junit.Assert.*;

import Ic2ExpReactorPlanner.components.FuelRod;
import Ic2ExpReactorPlanner.components.ReactorItem;
import org.junit.Test;

public class GtnhComponentTest {
    private final Reactor reactor = new Reactor();

    public GtnhComponentTest() {
        reactor.setSimulationProfile(SimulationProfile.GTNH_2_9_0);
    }

    private ReactorItem place(int row, int col, String name) {
        ReactorItem item = ComponentFactory.createComponent(name);
        reactor.setComponentAt(row, col, item);
        return item;
    }

    @Test
    public void adjacentDifferentFuelsCreditEachPulseToItsReceiver() {
        var uranium = (FuelRod) place(0, 0, "fuelRodUranium");
        var thorium = (FuelRod) place(0, 1, "quadFuelRodThorium");
        assertEquals(12, uranium.generateHeat(), 0);
        assertEquals(40, thorium.generateHeat(), 0);
        uranium.generateEnergy();
        thorium.generateEnergy();
        uranium.finishEnergyPass();
        thorium.finishEnergyPass();

        assertEquals(250, uranium.getCurrentEUGenerated() / 20, 0);
        assertEquals(130, thorium.getCurrentEUGenerated() / 20, 0.001);
        float raw = 2f + 0.4f;
        for (int cell = 0; cell < 4; cell++) {
            for (int pulse = 0; pulse < 3; pulse++) {
                raw += 0.4f;
            }
            raw += 2f;
        }
        assertEquals((double) (raw * 5f * 5f) * 20, reactor.getCurrentEUoutput(), 0);
    }

    @Test
    public void lastThoriumEnergyPassDepletesBeforeNextRodCanPulseIt() {
        var thorium = place(0, 0, "quadFuelRodThorium");
        var neighbor = place(0, 1, "fuelRodUranium");
        thorium.applyDamage(49_998);
        assertEquals(40, thorium.generateHeat(), 0);
        assertFalse(thorium.isBroken());
        thorium.generateEnergy();
        assertTrue(thorium.isBroken());
        var beforeNeighbor = thorium.getCurrentEUGenerated();
        neighbor.generateEnergy();
        assertEquals(beforeNeighbor, thorium.getCurrentEUGenerated(), 0);
        assertEquals(5 * 2 * 500, neighbor.getCurrentEUGenerated(), 0);
    }

    @Test
    public void reflectorBreaksOnTheCurrentInternalFuelPulse() {
        var thorium = place(0, 0, "quadFuelRodThorium");
        var reflector = place(0, 1, "neutronReflector");
        reflector.applyDamage(9_999);
        assertEquals(28, thorium.generateHeat(), 0);
        assertTrue(reflector.isBroken());
        thorium.generateEnergy();
        assertEquals(120, thorium.getCurrentEUGenerated() / 20, 0.001);
    }

    @Test
    public void fluidMoxRechecksTemperatureForEachInternalCell() {
        reactor.setFluid(true);
        reactor.setCurrentHeat(4_997);
        var mox = place(0, 0, "quadFuelRodMox");
        assertEquals(168, mox.generateHeat(), 0);
        assertEquals(5_165, reactor.getCurrentHeat(), 0);
    }

    @Test
    public void fullCoolantSurvivesAndOverflowChangesHeatShare() {
        var left = place(0, 0, "coolantCell10k");
        var uranium = place(0, 1, "fuelRodUranium");
        var right = place(0, 2, "coolantCell10k");
        left.adjustCurrentHeat(10_000);
        assertFalse(left.isBroken());
        assertEquals(4, uranium.generateHeat(), 0);
        assertTrue(left.isBroken());
        assertEquals(1, right.getCurrentHeat(), 0);
        left.reloadFreshHeat();
        assertFalse(left.isBroken());
        assertEquals(0, left.getCurrentHeat(), 0);
        assertEquals(10_000, left.getMaxReachedHeat(), 0);
    }

    @Test
    public void saturatedCondensatorRejectsHeatWithoutMelting() {
        var condensator = place(0, 0, "rshCondensator");
        condensator.adjustCurrentHeat(19_998);
        assertEquals(2, condensator.adjustCurrentHeat(4), 0);
        assertFalse(condensator.isBroken());
        assertFalse(condensator.isHeatAcceptor());
        assertEquals(-4, condensator.adjustCurrentHeat(-4), 0);
    }

    @Test
    public void gtCoolantInheritsElectricUseTagUntilItsHeatIsCleared() {
        var cell = place(0, 0, "coolantCellHelium60k");
        cell.adjustCurrentHeat(95);
        reactor.setFluid(true);
        assertFalse(cell.isHeatAcceptor());
        cell.reloadFreshHeat();
        assertTrue(cell.isHeatAcceptor());
    }

    @Test
    public void breederCompletionAbsorbsPulsesWithoutBoostingFuelHeat() {
        reactor.setCurrentHeat(6_000);
        var fuel = place(0, 0, "quadFuelRodThorium");
        var breeder = place(0, 1, "breederRodLithium");
        breeder.applyDamage(9_990);
        assertEquals(24, fuel.generateHeat(), 0);
        assertEquals(10_000, breeder.getCurrentDamage(), 0);
        assertTrue(breeder.isBroken());
        fuel.generateEnergy();
        assertEquals(120, fuel.getCurrentEUGenerated() / 20, 0.001);
    }

    @Test
    public void coreUsesSeventeenBasePulsesPerInternalCell() {
        var core = place(0, 0, "fuelRodTheCore");
        assertEquals(19_584, core.generateHeat(), 0);
        core.generateEnergy();
        assertEquals(108_800, reactor.getCurrentEUoutput() / 20, 0);
    }

    @Test
    public void coreExchangerTransfersSeventyTwoHullHeatInOnePass() {
        reactor.setCurrentHeat(5_000);
        var exchanger = place(0, 0, "coreHeatExchanger");
        exchanger.transfer();
        assertEquals(4_928, reactor.getCurrentHeat(), 0);
        assertEquals(72, exchanger.getCurrentHeat(), 0);
    }

    @Test
    public void newComponentsHaveProfileSupportMaterialsAndRoundTrip() {
        reactor.setComponentAt(0, 0, ComponentFactory.createComponent(59));
        reactor.setComponentAt(0, 1, ComponentFactory.createComponent(71));
        reactor.setComponentAt(0, 2, ComponentFactory.createComponent(72));
        assertNotNull(MaterialsList.getMaterialsForComponent(reactor.getComponentAt(0, 0)));
        assertFalse(SimulationProfile.LEGACY.supports(reactor.getComponentAt(0, 1)));
        assertTrue(SimulationProfile.GTNH_2_9_0.supports(reactor.getComponentAt(0, 1)));
        var restored = new Reactor();
        restored.setCode(reactor.getCode());
        assertEquals(reactor.getCode(), restored.getCode());
        assertEquals(1_000_000_000, restored.getComponentAt(0, 0).getMaxHeat(), 0);
        assertEquals(4, restored.getComponentAt(0, 1).getRodCount());
        assertEquals("breederRodLithium", restored.getComponentAt(0, 2).baseName);
    }
}
