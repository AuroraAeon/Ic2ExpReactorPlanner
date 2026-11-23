package Ic2ExpReactorPlanner;

import Ic2ExpReactorPlanner.components.ReactorItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GtnhThoriumVacuumTest {
    private static final String[] TWO_CHAMBER_LAYOUT = {
        "TOTOT",
        "O.O.O",
        "TOTAT",
        "OTO.O",
        "TOTOT",
        "A.OTO"
    };

    @Test
    public void passiveLayoutFinishesTheWholeThoriumCycleCold() throws Exception {
        Reactor reactor = loadExample("passive", false);
        SimulationData result = AutomationSimulator.simulate(reactor, null);
        assertThoriumCycle(result);
        assertEquals(0, result.maxTemp, 0);
        assertEquals(264, result.componentHeating, 0.1);
        assertNull(result.vacuumCooling);
    }

    @Test
    public void vacuumLayoutReusesColdStockForTheWholeThoriumCycle() throws Exception {
        Reactor reactor = loadExample("vacuum", true);
        VacuumCooling.Config cooling = new VacuumCooling.Config(90, 15, 1, 2);
        SimulationData result = AutomationSimulator.simulate(reactor, cooling);
        assertThoriumCycle(result);
        assertEquals(0, result.maxTemp, 0);
        VacuumCooling.Result vacuum = result.vacuumCooling;
        assertEquals(236, vacuum.swaps());
        assertEquals(236, vacuum.regenerated());
        assertEquals(0, vacuum.waitingOrCooling());
        assertEquals(0, vacuum.destroyed());
        assertEquals(0, vacuum.firstShortageCycle());
        assertEquals(12_744_000, vacuum.heatRemoved(), 0);
        assertEquals(6_796_800, vacuum.energyConsumedEu());
    }

    @Test
    public void freezerQueuesHotCellsAndReleasesStockAtItsNextPoll() {
        Reactor reactor = new Reactor();
        reactor.setSimulationProfile(SimulationProfile.GTNH_2_9_0);
        ReactorItem cell = ComponentFactory.createComponent(29);
        reactor.setComponentAt(0, 0, cell);
        VacuumCooling cooling = new VacuumCooling(new VacuumCooling.Config(90, 1, 1, 0));
        cooling.initialize(reactor);
        cell.adjustCurrentHeat(54_000);
        cooling.service(reactor, 1);
        assertEquals(0, cell.getCurrentHeat(), 0);
        assertEquals(1, cooling.result().waitingOrCooling());

        cell.adjustCurrentHeat(54_000);
        cooling.service(reactor, 2);
        assertEquals(54_000, cell.getCurrentHeat(), 0);
        assertEquals(2, cooling.result().firstShortageCycle());
        assertEquals(2_400, cooling.result().energyConsumedEu());
        cooling.service(reactor, 3);
        assertEquals(0, cooling.result().regenerated());
        cooling.service(reactor, 4);
        assertEquals(1, cooling.result().regenerated());
        assertEquals(2, cooling.result().swaps());
        assertEquals(7_200, cooling.result().energyConsumedEu());
        assertEquals(0, cell.getCurrentHeat(), 0);

        cell.adjustCurrentHeat(60_001);
        cooling.service(reactor, 5);
        assertEquals(1, cooling.result().destroyed());
        assertEquals(2, cooling.result().swaps());
    }

    @Test
    public void oneFreezerProcessesDifferentCellTypesInSlotOrder() {
        Reactor reactor = new Reactor();
        reactor.setSimulationProfile(SimulationProfile.GTNH_2_9_0);
        ReactorItem helium = ComponentFactory.createComponent(29);
        ReactorItem water = ComponentFactory.createComponent(14);
        reactor.setComponentAt(0, 0, helium);
        reactor.setComponentAt(0, 1, water);
        VacuumCooling cooling = new VacuumCooling(new VacuumCooling.Config(90, 1, 1, 0));
        cooling.initialize(reactor);
        helium.adjustCurrentHeat(54_000);
        water.adjustCurrentHeat(9_000);
        cooling.service(reactor, 1);
        assertEquals(0, helium.getCurrentHeat(), 0);
        assertEquals(0, water.getCurrentHeat(), 0);
        assertEquals(2, cooling.result().waitingOrCooling());

        cooling.service(reactor, 4);
        assertEquals(1, cooling.result().regenerated());
        assertEquals(7_200, cooling.result().energyConsumedEu());
        cooling.service(reactor, 5);
        assertEquals(2, cooling.result().regenerated());
        assertEquals(8_400, cooling.result().energyConsumedEu());
    }

    @Test
    public void thermalCentrifugeCountsItemsRatherThanInternalFuelCells() {
        GtnhThoriumCycle cycle = new GtnhThoriumCycle();
        cycle.recordDepletion(ComponentFactory.createComponent(26));
        cycle.recordDepletion(ComponentFactory.createComponent(27));
        cycle.recordDepletion(ComponentFactory.createComponent(28));
        cycle.recordDepletion(ComponentFactory.createComponent(1));
        GtnhThoriumCycle.Products products = cycle.result();
        assertEquals(3, products.spentRods());
        assertEquals(3.5, products.lutetiumDust(), 0);
        assertEquals(7, products.thoriumDust());
        assertEquals(10, products.ironDust());
        assertEquals(1_500, products.centrifugeGameTicks());
        assertEquals(72_000, products.centrifugeEnergyEu());
    }

    private static void assertThoriumCycle(SimulationData result) {
        assertTrue(result.fuelExhausted);
        assertFalse(result.exploded);
        assertEquals(Integer.MAX_VALUE, result.firstComponentBrokenTime);
        assertEquals(49_999, result.firstRodDepletedTime);
        assertEquals(1320.0013427734375, result.maxEUoutput, 0);
        assertEquals(1_319_974_942.746582, result.totalEUoutput, 0);
        GtnhThoriumCycle.Products products = result.thoriumProducts;
        assertEquals(11, products.quadRods());
        assertEquals(22, products.lutetiumDust(), 0);
        assertEquals(44, products.thoriumDust());
        assertEquals(66, products.ironDust());
        assertEquals(5_500, products.centrifugeGameTicks());
        assertEquals(264_000, products.centrifugeEnergyEu());
    }

    private static Reactor loadExample(String name, boolean vacuum) throws IOException {
        String code = Files.readString(Path.of("examples", "gtnh-2.9.0-thorium-11-" + name + ".txt")).strip();
        Reactor reactor = new Reactor();
        reactor.setCode(code);
        assertEquals(2, reactor.getChambers());
        assertEquals(SimulationProfile.GTNH_2_9_0, reactor.getSimulationProfile());
        assertEquals(twoChamberLayout(vacuum).getCode(), reactor.getCode());
        assertEquals(code, reactor.getCode());
        return reactor;
    }

    static Reactor twoChamberLayout(boolean vacuum) {
        Reactor reactor = new Reactor();
        reactor.setChambers(2);
        reactor.setSimulationProfile(SimulationProfile.GTNH_2_9_0);
        for (int row = 0; row < TWO_CHAMBER_LAYOUT.length; row++) {
            for (int col = 0; col < TWO_CHAMBER_LAYOUT[row].length(); col++) {
                char symbol = TWO_CHAMBER_LAYOUT[row].charAt(col);
                int id = switch (symbol) {
                    case 'T' -> 28;
                    case 'O' -> vacuum ? 29 : 13;
                    case 'A' -> vacuum ? 29 : 10;
                    default -> 0;
                };
                reactor.setComponentAt(row, col, ComponentFactory.createComponent(id));
            }
        }
        return reactor;
    }
}
