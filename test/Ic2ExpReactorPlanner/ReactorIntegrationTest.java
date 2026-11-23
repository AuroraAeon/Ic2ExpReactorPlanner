package Ic2ExpReactorPlanner;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReactorIntegrationTest {
    // Posted by upstream author in the official New Reactors design thread, post 594.
    private static final String MAUVECLOUD_CHECKERBOARD =
            "010901090109010901090109010901090109010901090109010901090109010901090109010901090109010901090109010901090109";
    // v2.4.2 code from the High output fluid reactors thread, post 21.
    private static final String FLUID_REV3 =
            "erp=IO3DJJu16N47AYzFwO6s/w7SnKn4CNY4rks076a2YPthdAxjUyrOBa2nTVydQjLkg8m7TeNBqeurF5dEluUD";

    @Test
    public void importsAndRunsPublishedLegacyDesign() throws Exception {
        Reactor reactor = new Reactor();
        reactor.setCode(MAUVECLOUD_CHECKERBOARD);
        assertEquals(MAUVECLOUD_CHECKERBOARD, reactor.getOldCode().split("\\|")[0]);
        SimulationData result = AutomationSimulator.simulate(reactor);
        assertEquals(27, result.totalRodCount);
        assertEquals(54_000_000, result.totalEUoutput, 0);
        assertEquals(0, result.maxTemp, 0);
        assertFalse(result.exploded);
        assertEquals(20_000, result.firstRodDepletedTime);
    }

    @Test
    public void readsAndReexportsPublishedRevisionThreeWithoutChangingIt() {
        Reactor r = new Reactor();
        r.setCode(FLUID_REV3);
        assertTrue(r.isFluid());
        assertTrue(r.isPulsed());
        assertEquals(FLUID_REV3, r.getCode());
    }

    @Test
    public void preservesProfileWidthAndInactiveAutomationOptions() {
        Reactor original = new Reactor();
        original.setSimulationProfile(SimulationProfile.GTNH_2_9_0);
        original.setChambers(2);
        original.setFluidTransferLimit(9);
        var cell = ComponentFactory.createComponent("coolantCell10k");
        cell.setAutomationThreshold(7_999);
        cell.setReactorPause(3);
        original.setComponentAt(5, 4, cell);
        Reactor restored = new Reactor();
        restored.setCode(original.getCode());
        assertEquals(original.getCode(), restored.getCode());
        assertEquals(SimulationProfile.GTNH_2_9_0, restored.getSimulationProfile());
        assertEquals(2, restored.getChambers());
        assertEquals(9, restored.getFluidTransferLimit());
        assertEquals(7_999, restored.getComponentAt(5, 4).getAutomationThreshold());
        assertEquals(3, restored.getComponentAt(5, 4).getReactorPause());
        assertThrows(IllegalArgumentException.class, () -> restored.setChambers(1));
        assertEquals(2, restored.getChambers());
        assertNotSame(cell, original.copyDesign().getComponentAt(5, 4));
    }

    @Test
    public void failedImportsCannotPartiallyReplaceCurrentDesign() {
        Reactor reactor = new Reactor();
        reactor.setCode(MAUVECLOUD_CHECKERBOARD);
        String prior = reactor.getCode();
        assertThrows(IllegalArgumentException.class, () -> reactor.setCode("erp=/w=="));
        assertThrows(IllegalArgumentException.class, () -> reactor.setCode("FF" + "00".repeat(53)));
        assertThrows(IllegalArgumentException.class, () -> reactor.setCode("00".repeat(54) + "|e"));
        assertEquals(prior, reactor.getCode());
    }

    @Test
    public void legacyRulesRemainIsolatedAndSurviveVersionedExport() throws Exception {
        Reactor design = new Reactor();
        design.setLegacyRules(true, true);
        design.setMaxSimulationTicks(1);
        design.setComponentAt(0, 0, ComponentFactory.createComponent("fuelRodUranium"));
        Reactor restored = new Reactor();
        restored.setCode(design.getCode());
        assertTrue(restored.usesLegacyEarlyReflectors());
        assertTrue(restored.usesLegacyGt509());
        assertEquals(200, AutomationSimulator.simulate(restored).totalEUoutput, 0);
        design.setLegacyRules(false, false);
        assertEquals(100, AutomationSimulator.simulate(design).totalEUoutput, 0);
        assertEquals(200, AutomationSimulator.simulate(restored).totalEUoutput, 0);
    }

    @Test
    public void fluidConversionReturnsUnusedHeatToHull() throws Exception {
        Reactor reactor = new Reactor();
        reactor.setSimulationProfile(SimulationProfile.GTNH_2_9_0);
        reactor.setFluid(true);
        reactor.setMaxSimulationTicks(1);
        reactor.setComponentAt(0, 0, ComponentFactory.createComponent("fuelRodUranium"));
        reactor.setComponentAt(0, 1, ComponentFactory.createComponent("heatVent"));

        SimulationData fullTransfer = AutomationSimulator.simulate(reactor);
        assertEquals(8, fullTransfer.hotCoolantMillibuckets);
        assertEquals(0, fullTransfer.maxTemp, 0);
        assertEquals(0, fullTransfer.totalEUoutput, 0);

        reactor.setFluidTransferLimit(3);
        SimulationData restrictedTransfer = AutomationSimulator.simulate(reactor);
        assertEquals(3, restrictedTransfer.hotCoolantMillibuckets);
        assertEquals(2, restrictedTransfer.maxTemp, 0);

        reactor.setChambers(2);
        assertThrows(IllegalArgumentException.class, () -> AutomationSimulator.simulate(reactor));
    }

    @Test
    public void hullOverheatProducesValidSummaryAndUnchangedDesign() throws Exception {
        Reactor reactor = new Reactor();
        reactor.setSimulationProfile(SimulationProfile.GTNH_2_9_0);
        reactor.setCurrentHeat(9997);
        reactor.setComponentAt(0, 0, ComponentFactory.createComponent("fuelRodUranium"));
        String original = reactor.getCode();
        SimulationData result = AutomationSimulator.simulate(reactor);
        assertTrue(result.exploded);
        assertEquals(1, result.totalReactorTicks);
        assertEquals(1, result.timeToXplode);
        assertEquals(1, result.timeToHurt);
        assertEquals(12, result.explosionPower, 0);
        assertEquals(original, reactor.getCode());
    }

    @Test
    public void zeroOnPulseStartsInactiveForGtnh() throws Exception {
        Reactor reactor = new Reactor();
        reactor.setSimulationProfile(SimulationProfile.GTNH_2_9_0);
        reactor.setPulsed(true);
        reactor.setOnPulse(0);
        reactor.setOffPulse(0);
        reactor.setMaxSimulationTicks(2);
        reactor.setComponentAt(0, 0, ComponentFactory.createComponent("fuelRodUranium"));
        SimulationData result = AutomationSimulator.simulate(reactor);
        assertEquals(2, result.totalReactorTicks);
        assertEquals(0, result.totalEUoutput, 0);
        assertEquals(0, result.maxTemp, 0);
        assertFalse(result.fuelExhausted);
    }
}
