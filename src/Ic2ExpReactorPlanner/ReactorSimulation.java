package Ic2ExpReactorPlanner;

import static Ic2ExpReactorPlanner.BundleHelper.formatI18n;
import static Ic2ExpReactorPlanner.BundleHelper.getI18n;
import Ic2ExpReactorPlanner.components.BreederCell;
import Ic2ExpReactorPlanner.components.FuelRod;
import Ic2ExpReactorPlanner.components.Plating;
import Ic2ExpReactorPlanner.components.ReactorItem;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** One deterministic simulation over an isolated, mutable reactor layout. */
final class ReactorSimulation {

    private final Reactor reactor;
    private final ReactorItem[] components;
    private final int[] slots;
    private final ReactorItem[] fuel;
    private final boolean gtnh;
    private final boolean gtnhFluid;
    private final VacuumCooling vacuum;
    private final GtnhThoriumCycle thorium = new GtnhThoriumCycle();

    private final Consumer<String[]> sink;
    private final BooleanSupplier stopRequested;
    private final IntConsumer updateProgress;
    private final boolean reportCells;

    private final boolean[][] alreadyBroken = new boolean[6][9];
    private final int[] replacementCount = new int[Reactor.ROWS * Reactor.MAX_COLUMNS];

    private final int initialHeat;

    private final File csvFile;

    private final int csvLimit;

    private double minEUoutput = Double.MAX_VALUE;
    private double maxEUoutput = 0.0;
    private double minHeatOutput = Double.MAX_VALUE;
    private double maxHeatOutput = 0.0;

    private final int onPulseDuration;
    private final int offPulseDuration;
    private final int clockPeriod;
    private final int suspendTemp;
    private final int resumeTemp;
    private final int maxSimulationTicks;

    private boolean reachedBelow50;
    private boolean startedAtOrAbove50;
    private boolean reachedBurn;
    private boolean reachedEvaporate;
    private boolean reachedHurt;
    private boolean reachedLava;
    private boolean reachedExplode;

    private boolean allFuelRodsDepleted = false;
    private boolean componentsIntact = true;
    private boolean anyRodsDepleted = false;

    private int activeTime = 0;
    private int inactiveTime = 0;
    private int currentActiveTime = 0;
    private int minActiveTime = Integer.MAX_VALUE;
    private int maxActiveTime = 0;
    private int currentInactiveTime = 0;
    private int minInactiveTime = Integer.MAX_VALUE;
    private int maxInactiveTime = 0;

    private double totalHullHeating = 0;
    private double totalComponentHeating = 0;
    private double totalHullCooling = 0;
    private double totalVentCooling = 0;

    private boolean showHeatingCoolingCalled = false;

    private boolean active = true;

    private int pauseTimer = 0;

    private int redstoneUsed = 0;

    private int lapisUsed = 0;

    private final MaterialsList replacedItems = new MaterialsList();

    private final DecimalFormat decimalFormat = new DecimalFormat(getI18n("Simulation.DecimalFormat"));

    private final SimulationData data = new SimulationData();

    ReactorSimulation(Reactor reactor, File csvFile, int csvLimit, VacuumCooling.Config vacuumConfig,
            Consumer<String[]> sink, BooleanSupplier stopRequested, IntConsumer updateProgress,
            boolean reportCells) {
        this.sink = sink;
        this.stopRequested = stopRequested;
        this.updateProgress = updateProgress;
        this.reportCells = reportCells;
        this.reactor = reactor;
        this.gtnh = reactor.getSimulationProfile() == SimulationProfile.GTNH_2_9_0;
        this.gtnhFluid = gtnh && reactor.isFluid();
        if (vacuumConfig != null && (!gtnh || reactor.isFluid())) {
            throw new IllegalArgumentException("External vacuum cooling requires the GTNH 2.9.0 EU reactor profile");
        }
        if (gtnh && reactor.isFluid() && reactor.getChambers() != 6) {
            throw new IllegalArgumentException("An IC2 fluid reactor requires six chambers");
        }
        vacuum = vacuumConfig == null ? null : new VacuumCooling(vacuumConfig);
        List<ReactorItem> items = new ArrayList<>();
        List<ReactorItem> rods = new ArrayList<>();
        int[] occupiedSlots = new int[Reactor.ROWS * Reactor.MAX_COLUMNS];
        for (int row = 0; row < Reactor.ROWS; row++) {
            for (int col = 0; col < reactor.getColumns(); col++) {
                ReactorItem item = reactor.getComponentAt(row, col);
                if (item != null) {
                    if (!reactor.getSimulationProfile().supports(item)) {
                        throw new IllegalArgumentException("Unsupported reactor component for this profile: " + item.name);
                    }
                    occupiedSlots[items.size()] = row * Reactor.MAX_COLUMNS + col;
                    items.add(item);
                    if (item.getRodCount() > 0) {
                        rods.add(item);
                    }
                }
            }
        }
        components = items.toArray(ReactorItem[]::new);
        fuel = rods.toArray(ReactorItem[]::new);
        slots = occupiedSlots;
        this.initialHeat = (int)reactor.getCurrentHeat();
        this.onPulseDuration = reactor.getOnPulse();
        this.offPulseDuration = reactor.getOffPulse();
        this.clockPeriod = onPulseDuration + offPulseDuration;
        this.suspendTemp = reactor.getSuspendTemp();
        this.resumeTemp = reactor.getResumeTemp();
        this.csvFile = csvFile;
        this.csvLimit = csvLimit;
        this.maxSimulationTicks = Math.max(0, reactor.getMaxSimulationTicks());
    }

    private void report(String... messages) {
        if (sink != null) {
            sink.accept(messages);
        }
    }

    private boolean cancelled() {
        return stopRequested.getAsBoolean() || Thread.currentThread().isInterrupted();
    }

    SimulationData run() throws IOException {
        long startTime = System.nanoTime();
        int reactorTicks = 0;
        int cooldownTicks = 0;
        int totalRodCount = 0;
        try (PrintWriter csvOut = csvFile == null ? null : new PrintWriter(csvFile, StandardCharsets.UTF_8)) {
            if (csvOut != null) {
                csvOut.print(getI18n("CSVData.HeaderReactorTick"));
                csvOut.print(getI18n("CSVData.HeaderCoreHeat"));
                if (reactor.isFluid()) {
                    csvOut.print(getI18n("CSVData.HeaderHUOutput"));
                } else {
                    csvOut.print(getI18n("CSVData.HeaderEUOutput"));
                }
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorItem component = reactor.getComponentAt(row, col);
                        if (component != null && (component.getMaxHeat() > 1 || component.getMaxDamage() > 1)) {
                            csvOut.printf(Locale.ROOT, getI18n("CSVData.HeaderComponentName"), component.name, row, col);
                        }
                        if (component != null && component.producesOutput()) {
                            csvOut.printf(Locale.ROOT, getI18n("CSVData.HeaderComponentOutput"), component.name, row, col);
                        }
                    }
                }
                csvOut.println();
            }
            report(""); //NOI18N
            report(getI18n("Simulation.Started"));
            reactor.setCurrentHeat(initialHeat);
            reactor.clearVentedHeat();
            double minReactorHeat = initialHeat;
            double maxReactorHeat = initialHeat;
            reachedBelow50 = false;
            startedAtOrAbove50 = initialHeat >= 0.5 * reactor.getMaxHeat();
            reachedBurn = !gtnh && initialHeat >= 0.4 * reactor.getMaxHeat();
            reachedEvaporate = !gtnh && startedAtOrAbove50;
            reachedHurt = !gtnh && initialHeat >= 0.7 * reactor.getMaxHeat();
            reachedLava = !gtnh && initialHeat >= 0.85 * reactor.getMaxHeat();
            reachedExplode = false;
            for (ReactorItem component : components) {
                component.clearCurrentHeat();
                component.clearDamage();
                totalRodCount += component.getRodCount();
            }
            if (reportCells) {
                for (int row = 0; row < Reactor.ROWS; row++) {
                    for (int col = 0; col < Reactor.MAX_COLUMNS; col++) {
                        report("R" + row + "C" + col + ":0xC0C0C0");
                    }
                }
            }
            if (vacuum != null) {
                vacuum.initialize(reactor);
            }
            if (gtnh && reactor.isPulsed()) {
                active = onPulseDuration > 0 && initialHeat < suspendTemp;
            }
            data.totalRodCount = totalRodCount;
            double lastEUoutput = 0.0;
            double totalEUoutput = 0.0;
            double lastHeatOutput = 0.0;
            double totalHeatOutput = 0.0;
            double maxGeneratedHeat = 0.0;
            allFuelRodsDepleted = false;
            componentsIntact = true;
            anyRodsDepleted = false;
            while (reactorTicks < maxSimulationTicks && !cancelled()) {
                reactorTicks++;
                if (reactorTicks % 256 == 0) {
                    updateProgress.accept((int) (reactorTicks * 95L / maxSimulationTicks));
                }
                double generatedHeat = simulateUpdate();
                lastHeatOutput = effectiveVentedHeat();
                maxReactorHeat = Math.max(reactor.getCurrentHeat(), maxReactorHeat);
                minReactorHeat = Math.min(reactor.getCurrentHeat(), minReactorHeat);
                checkReactorTemperature(reactorTicks);
                maxGeneratedHeat = Math.max(generatedHeat, maxGeneratedHeat);
                lastEUoutput = gtnhFluid ? 0 : reactor.getCurrentEUoutput();
                totalEUoutput += lastEUoutput;
                totalHeatOutput += lastHeatOutput;
                if (reactor.getCurrentHeat() <= reactor.getMaxHeat() || gtnh) {
                    if (reactor.isPulsed() || reactor.isAutomated()) {
                        if (active) {
                            activeTime++;
                            currentActiveTime++;
                            if (reactor.isPulsed() && (reactor.getCurrentHeat() >= suspendTemp || !isPulseOn(reactorTicks))) {
                                active = false;
                                minActiveTime = Math.min(currentActiveTime, minActiveTime);
                                maxActiveTime = Math.max(currentActiveTime, maxActiveTime);
                                currentActiveTime = 0;
                            }
                        } else {
                            inactiveTime++;
                            currentInactiveTime++;
                            if (pauseTimer > 0) {
                                pauseTimer--;
                            }
                            if (pauseTimer == 0 && (!reactor.isPulsed()
                                    || (reactor.getCurrentHeat() <= resumeTemp && isPulseOn(reactorTicks)))) {
                                active = true;
                                minInactiveTime = Math.min(currentInactiveTime, minInactiveTime);
                                maxInactiveTime = Math.max(currentInactiveTime, maxInactiveTime);
                                currentInactiveTime = 0;
                            }
                        }
                    }
                    minEUoutput = Math.min(lastEUoutput, minEUoutput);
                    maxEUoutput = Math.max(lastEUoutput, maxEUoutput);
                    minHeatOutput = Math.min(lastHeatOutput, minHeatOutput);
                    maxHeatOutput = Math.max(lastHeatOutput, maxHeatOutput);
                }
                calculateHeatingCooling(reactorTicks);
                if (gtnh) {
                    handleBrokenComponents(reactorTicks, totalHeatOutput, totalRodCount, totalEUoutput, minReactorHeat, maxReactorHeat);
                }
                if (reactor.getCurrentHeat() < reactor.getMaxHeat()) {
                    if (vacuum != null) {
                        vacuum.service(reactor, reactorTicks);
                    }
                    if (reactor.isAutomated() || reactor.isUsingReactorCoolantInjectors()) {
                        handleAutomation(reactorTicks);
                    }
                }
                if (csvOut != null && reactorTicks <= csvLimit) {
                    csvOut.printf(Locale.ROOT, getI18n("CSVData.EntryReactorTick"), reactorTicks);
                    csvOut.printf(Locale.ROOT, getI18n("CSVData.EntryCoreHeat"), reactor.getCurrentHeat());
                    if (reactor.isFluid()) {
                        csvOut.printf(Locale.ROOT, getI18n("CSVData.EntryHUOutput"), lastHeatOutput * 40);
                    } else {
                        csvOut.printf(Locale.ROOT, getI18n("CSVData.EntryEUOutput"), reactor.getCurrentEUoutput());
                    }
                    for (int row = 0; row < 6; row++) {
                        for (int col = 0; col < 9; col++) {
                            ReactorItem component = reactor.getComponentAt(row, col);
                            if (component != null && (component.getMaxHeat() > 1 || component.getMaxDamage() > 1)) {
                                double componentValue = component.getCurrentDamage();
                                if (component.getMaxHeat() > 1) {
                                    componentValue = component.getCurrentHeat();
                                }
                                csvOut.printf(Locale.ROOT, getI18n("CSVData.EntryComponentValue"), componentValue);
                            }
                            if (component != null && component.producesOutput()) {
                                csvOut.printf(Locale.ROOT, getI18n("CSVData.EntryComponentOutput"), component.getCurrentOutput());
                            }
                        }
                    }
                    csvOut.println();
                }
                if (!gtnh) {
                    handleBrokenComponents(reactorTicks, totalHeatOutput, totalRodCount, totalEUoutput, minReactorHeat, maxReactorHeat);
                }
                if (reactor.getCurrentHeat() >= reactor.getMaxHeat()
                        || (gtnh ? !hasUsableFuel() : allFuelRodsDepleted && lastEUoutput == 0 && lastHeatOutput == 0)) {
                    break;
                }
            }
            if (cancelled()) {
                report(formatI18n("Simulation.CancelledAtTick", reactorTicks));
                return null;
            }
            data.minTemp = minReactorHeat;
            data.maxTemp = maxReactorHeat;
            data.totalReactorTicks = reactorTicks;
            data.exploded = reactor.getCurrentHeat() >= reactor.getMaxHeat();
            data.fuelExhausted = fuel.length > 0 && !hasUsableFuel();
            data.hotCoolantMillibuckets = gtnh && reactor.isFluid() ? (long) (2 * totalHeatOutput) : 0;
            data.thoriumProducts = thorium.result();
            if (vacuum != null) {
                data.vacuumCooling = vacuum.result();
            }
            report(formatI18n("Simulation.ReactorMinTemp", minReactorHeat));
            report(formatI18n("Simulation.ReactorMaxTemp", maxReactorHeat));
            if (reactor.getCurrentHeat() < reactor.getMaxHeat()) {
                report(formatI18n("Simulation.TimeWithoutExploding", reactorTicks));
                if (reactor.isPulsed()) {
                    String rangeString = "";
                    if (maxActiveTime > minActiveTime) {
                        rangeString = formatI18n("Simulation.ActiveTimeRange", minActiveTime, maxActiveTime);
                    } else if (minActiveTime < activeTime) {
                        rangeString = formatI18n("Simulation.ActiveTimeSingle", minActiveTime);
                    }
                    report(formatI18n("Simulation.ActiveTime", activeTime, rangeString));
                    rangeString = "";
                    if (maxInactiveTime > minInactiveTime) {
                        rangeString = formatI18n("Simulation.InactiveTimeRange", minInactiveTime, maxInactiveTime);
                    } else if (minInactiveTime < inactiveTime) {
                        rangeString = formatI18n("Simulation.InactiveTimeSingle", minInactiveTime);
                    }
                    report(formatI18n("Simulation.InactiveTime", inactiveTime, rangeString));
                }
                final String replacedItemsString = replacedItems.toString();
                if (!replacedItemsString.isEmpty()) {
                    data.replacedItems = new MaterialsList(replacedItems);
                    report(formatI18n("Simulation.ComponentsReplaced", replacedItemsString));
                }

                if (reactorTicks > 0) {
                    data.totalReactorTicks = reactorTicks;
                    if (reactor.isFluid()) {
                        data.totalHUoutput = 40 * totalHeatOutput;
                        data.avgHUoutput = 2 * totalHeatOutput / reactorTicks;
                        data.minHUoutput = 2 * minHeatOutput;
                        data.maxHUoutput = 2 * maxHeatOutput;
                        if (totalHeatOutput > 0) {
                            report(formatI18n("Simulation.HeatOutputs",
                                    decimalFormat.format(40 * totalHeatOutput),
                                    decimalFormat.format(2 * totalHeatOutput / reactorTicks),
                                    decimalFormat.format(2 * minHeatOutput),
                                    decimalFormat.format(2 * maxHeatOutput)));
                            if (totalRodCount > 0) {
                                report(formatI18n("Simulation.Efficiency", totalHeatOutput / reactorTicks / 4 / totalRodCount, minHeatOutput / 4 / totalRodCount, maxHeatOutput / 4 / totalRodCount));
                            }
                        }
                    } else {
                        data.totalEUoutput = totalEUoutput;
                        data.avgEUoutput = totalEUoutput / (reactorTicks * 20);
                        data.minEUoutput = minEUoutput / 20.0;
                        data.maxEUoutput = maxEUoutput / 20.0;
                        if (totalEUoutput > 0) {
                            report(formatI18n("Simulation.EUOutputs",
                                    decimalFormat.format(totalEUoutput),
                                    decimalFormat.format(totalEUoutput / (reactorTicks * 20)),
                                    decimalFormat.format(minEUoutput / 20.0),
                                    decimalFormat.format(maxEUoutput / 20.0)));
                            if (totalRodCount > 0) {
                                report(formatI18n("Simulation.Efficiency", totalEUoutput / reactorTicks / 100 / totalRodCount, minEUoutput / 100 / totalRodCount, maxEUoutput / 100 / totalRodCount));
                            }
                        }
                    }
                }

                if (reactor.getCurrentHeat() > 0.0) {
                    report(formatI18n("Simulation.ReactorRemainingHeat", reactor.getCurrentHeat()));
                }
                double residualComponentHeat = 0.0;
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorItem component = reactor.getComponentAt(row, col);
                        if (component != null && !component.isBroken()) {
                            if (component.getCurrentHeat() > 0.0) {
                                residualComponentHeat += component.getCurrentHeat();
                                report(String.format("R%dC%d:0xFFA500", row, col)); // NOI18N
                                component.info.append(formatI18n("ComponentInfo.RemainingHeat", component.getCurrentHeat()));
                            }
                        }
                    }
                }
                if (reactor.getCurrentHeat() == 0.0 && residualComponentHeat == 0.0) {
                    report(getI18n("Simulation.NoCooldown"));
                } else if (reactorTicks > 0) {
                    cooldownTicks = simulateCooldown();
                }
            } else {
                if (reactorTicks > 0) {
                    if (reactor.isFluid()) {
                        data.totalHUoutput = 40 * totalHeatOutput;
                        data.avgHUoutput = 2 * totalHeatOutput / reactorTicks;
                        data.minHUoutput = 2 * minHeatOutput;
                        data.maxHUoutput = 2 * maxHeatOutput;
                    } else {
                        data.totalEUoutput = totalEUoutput;
                        data.avgEUoutput = totalEUoutput / (reactorTicks * 20L);
                        data.minEUoutput = minEUoutput / 20.0;
                        data.maxEUoutput = maxEUoutput / 20.0;
                    }
                }
                report(formatI18n("Simulation.ReactorOverheatedTime", reactorTicks));
                data.explosionPower = explosionPower();
                report(formatI18n("Simulation.ExplosionPower", data.explosionPower));
            }
            double totalEffectiveVentCooling = 0.0;
            double totalVentCoolingCapacity = 0.0;
            double totalCellCooling = 0.0;
            double totalCondensatorCooling = 0.0;

            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) {
                    ReactorItem component = reactor.getComponentAt(row, col);
                    if (component != null) {
                        if (component.getVentCoolingCapacity() > 0) {
                            component.info.append(formatI18n("ComponentInfo.UsedCooling", component.getBestVentCooling(), component.getVentCoolingCapacity()));
                            totalEffectiveVentCooling += component.getBestVentCooling();
                            totalVentCoolingCapacity += component.getVentCoolingCapacity();
                        } else if (component.getBestCellCooling() > 0) {
                            component.info.append(formatI18n("ComponentInfo.ReceivedHeat", component.getBestCellCooling()));
                            totalCellCooling += component.getBestCellCooling();
                        } else if (component.getBestCondensatorCooling() > 0) {
                            component.info.append(formatI18n("ComponentInfo.ReceivedHeat", component.getBestCondensatorCooling()));
                            totalCondensatorCooling += component.getBestCondensatorCooling();
                        } else if (component.getMaxHeatGenerated() > 0) {
                            if (!reactor.isFluid() && component.getMaxEUGenerated() > 0) {
                                component.info.append(formatI18n("ComponentInfo.GeneratedEU", component.getMinEUGenerated(), component.getMaxEUGenerated()));
                            }
                            component.info.append(formatI18n("ComponentInfo.GeneratedHeat", component.getMinHeatGenerated(), component.getMaxHeatGenerated()));
                        }
                        if (component.getMaxReachedHeat() > 0) {
                            component.info.append(formatI18n("ComponentInfo.ReachedHeat", component.getMaxReachedHeat(), component.getMaxHeat()));
                        }
                    }
                }
            }

//            if (totalVentCoolingCapacity > 0) {
//                report(formatI18n("Simulation.TotalVentCooling", totalEffectiveVentCooling, totalVentCoolingCapacity));
//            }
            showHeatingCooling(reactorTicks);  // Call to show this info in case it hasn't already been shown, such as for an automated reactor.
            if (totalCellCooling > 0) {
                report(formatI18n("Simulation.TotalCellCooling", totalCellCooling));
            }
            if (totalCondensatorCooling > 0) {
                report(formatI18n("Simulation.TotalCondensatorCooling", totalCondensatorCooling));
            }
            if (maxGeneratedHeat > 0) {
                report(formatI18n("Simulation.MaxHeatGenerated", maxGeneratedHeat));
            }
            if (redstoneUsed > 0) {
                report(formatI18n("Simulation.RedstoneUsed", redstoneUsed));
            }
            if (lapisUsed > 0) {
                report(formatI18n("Simulation.LapisUsed", lapisUsed));
            }
//            double totalCooling = totalEffectiveVentCooling + totalCellCooling + totalCondensatorCooling;
//            if (totalCooling >= maxGeneratedHeat) {
//                report(formatI18n("Simulation.ExcessCooling", totalCooling - maxGeneratedHeat));
//            } else {
//                report(formatI18n("Simulation.ExcessHeating", maxGeneratedHeat - totalCooling));
//            }
            //return null;
            if (csvOut != null && csvOut.checkError()) {
                throw new IOException("Could not write CSV file: " + csvFile);
            }
        } catch (IOException | RuntimeException e) {
            if (cooldownTicks == 0) {
                report(formatI18n("Simulation.ErrorReactor", reactorTicks));
            } else {
                report(formatI18n("Simulation.ErrorCooldown", cooldownTicks));
            }
            report(e.toString());
            throw e;
        }
        if (cancelled()) {
            return null;
        }
        data.cooldownReactorTicks = cooldownTicks;
        long endTime = System.nanoTime();
        report(formatI18n("Simulation.ElapsedTime", (endTime - startTime) / 1e9));
        updateProgress.accept(100);
        return data;
    }

    private int simulateCooldown() {
        // Cooldown is supplementary and does not contribute to operating-cycle output.
        final int limit = 50_000;
        double[] priorHeat = new double[components.length];
        int cooldownTicks = 0;
        int hullColdAt = -1;
        while (cooldownTicks < limit && !cancelled()) {
            double hullBefore = reactor.getCurrentHeat();
            if (hullColdAt < 0 && hullBefore == 0) {
                hullColdAt = cooldownTicks;
            }
            for (int i = 0; i < components.length; i++) {
                priorHeat[i] = components[i].getCurrentHeat();
            }
            reactor.clearVentedHeat();
            if (gtnh) {
                reactor.resetHeatCapacity();
            }
            for (ReactorItem component : components) {
                if (!component.isBroken()) {
                    if (gtnh && component instanceof Plating plating) {
                        reactor.adjustMaxHeat(plating.getHeatAdjustment());
                    }
                    component.dissipate();
                    component.transfer();
                }
            }
            double vented = effectiveVentedHeat();
            cooldownTicks++;
            boolean heatMoved = hullBefore != reactor.getCurrentHeat();
            for (int i = 0; i < components.length; i++) {
                ReactorItem component = components[i];
                if (component.getCurrentHeat() != priorHeat[i]) {
                    heatMoved = true;
                    if (component.getCurrentHeat() == 0) {
                        component.info.append(formatI18n("ComponentInfo.CooldownTime", cooldownTicks));
                    }
                }
            }
            if ((!gtnh && vented == 0.0) || !heatMoved
                    || reactor.getCurrentHeat() >= reactor.getMaxHeat()) {
                break;
            }
        }
        if (reactor.getCurrentHeat() == 0) {
            report(formatI18n("Simulation.ReactorCooldownTime", hullColdAt < 0 ? cooldownTicks : hullColdAt));
        } else {
            report(formatI18n("Simulation.ReactorResidualHeat", reactor.getCurrentHeat(), cooldownTicks));
        }
        report(formatI18n("Simulation.TotalCooldownTime", cooldownTicks));
        return cooldownTicks;
    }

    private double simulateUpdate() {
        reactor.clearEUOutput();
        reactor.clearVentedHeat();
        if (gtnh) {
            reactor.resetHeatCapacity();
        }
        for (ReactorItem component : components) {
            component.preReactorTick();
        }
        if (active) {
            allFuelRodsDepleted = true; // assume rods depleted until one is found that isn't.
        }
        double generatedHeat = 0.0;
        for (ReactorItem component : components) {
            if (!component.isBroken()) {
                if (allFuelRodsDepleted && component.getRodCount() > 0) {
                    allFuelRodsDepleted = false;
                }
                if (gtnh && component instanceof Plating plating) {
                    reactor.adjustMaxHeat(plating.getHeatAdjustment());
                }
                if (active) {
                    generatedHeat += component.generateHeat();
                }
                component.dissipate();
                component.transfer();
            }
        }
        if (active) {
            for (ReactorItem rod : fuel) {
                if (!rod.isBroken()) {
                    rod.generateEnergy();
                }
            }
            if (gtnh) {
                for (ReactorItem rod : fuel) {
                    if (rod instanceof FuelRod fuelRod) {
                        fuelRod.finishEnergyPass();
                    }
                }
            }
        }
        return generatedHeat;
    }

    private boolean isPulseOn(int elapsedTicks) {
        return clockPeriod > 0 && elapsedTicks % clockPeriod < onPulseDuration;
    }

    private double effectiveVentedHeat() {
        if (!gtnhFluid) {
            return reactor.getVentedHeat();
        }
        int requestedMb = (int) (SimulationProfile.GTNH_COOLANT_CONVERSION_FACTOR * (float) reactor.getVentedHeat());
        int convertedMb = Math.min(reactor.getFluidTransferLimit(), requestedMb);
        // The game returns unconverted coolant's heat to the hull using integer division.
        reactor.adjustCurrentHeat((requestedMb - convertedMb) / 2);
        return convertedMb / 2.0;
    }

    private double explosionPower() {
        if (gtnh) {
            float power = 10;
            float modifier = 1;
            for (ReactorItem component : components) {
                power += (float) component.getExplosionPowerOffset();
                if (component instanceof Plating plating) {
                    modifier *= plating.getHeatEffectModifier();
                }
            }
            return Math.min(100F, power * (modifier * modifier));
        }
        double power = 10;
        double modifier = 1;
        for (ReactorItem component : components) {
            power += component.getExplosionPowerOffset();
            modifier *= component.getExplosionPowerMultiplier();
        }
        return power * modifier;
    }

    private boolean hasUsableFuel() {
        for (ReactorItem rod : fuel) {
            if (!rod.isBroken()) {
                return true;
            }
        }
        return false;
    }

    private void handleBrokenComponents(final int reactorTicks, final double totalHeatOutput, final int totalRodCount, final double totalEUoutput, final double minReactorHeat, final double maxReactorHeat) {
        for (int index = 0; index < components.length; index++) {
            int row = slots[index] / Reactor.MAX_COLUMNS;
            int col = slots[index] % Reactor.MAX_COLUMNS;
            ReactorItem component = components[index];
            if (component.isBroken() && !alreadyBroken[row][col]) {
                alreadyBroken[row][col] = true;
                if (component instanceof BreederCell) {
                    component.info.append(formatI18n("ComponentInfo.BreederCompleted", reactorTicks));
                    continue;
                }
                if (gtnh && component.getRodCount() > 0) {
                    thorium.recordDepletion(component);
                }
                if (component.getRodCount() == 0) {
                    report(String.format("R%dC%d:0xFF0000", row, col)); //NOI18N
                    component.info.append(formatI18n("ComponentInfo.BrokeTime", reactorTicks));
                    if (componentsIntact) {
                        componentsIntact = false;
                        data.firstComponentBrokenTime = reactorTicks;
                        data.firstComponentBrokenRow = row;
                        data.firstComponentBrokenCol = col;
                        data.firstComponentBrokenDescription = component.toString();
                        report(formatI18n("Simulation.FirstComponentBrokenDetails", component.toString(), row, col, reactorTicks));
                        if (reactor.isFluid()) {
                            data.prebreakTotalHUoutput = 40 * totalHeatOutput;
                            data.prebreakAvgHUoutput = 2 * totalHeatOutput / reactorTicks;
                            data.prebreakMinHUoutput = 2 * minHeatOutput;
                            data.prebreakMaxHUoutput = 2 * maxHeatOutput;
                            report(formatI18n("Simulation.HeatOutputsBeforeBreak",
                                    decimalFormat.format(40 * totalHeatOutput),
                                    decimalFormat.format(2 * totalHeatOutput / reactorTicks),
                                    decimalFormat.format(2 * minHeatOutput),
                                    decimalFormat.format(2 * maxHeatOutput)));
                            if (totalRodCount > 0) {
                                report(formatI18n("Simulation.Efficiency", totalHeatOutput / reactorTicks / 4 / totalRodCount, minHeatOutput / 4 / totalRodCount, maxHeatOutput / 4 / totalRodCount));
                            }
                        } else {
                            data.prebreakTotalEUoutput = totalEUoutput;
                            data.prebreakAvgEUoutput = totalEUoutput / (reactorTicks * 20);
                            data.prebreakMinEUoutput = minEUoutput / 20.0;
                            data.prebreakMaxEUoutput = maxEUoutput / 20.0;
                            report(formatI18n("Simulation.EUOutputsBeforeBreak",
                                    decimalFormat.format(totalEUoutput),
                                    decimalFormat.format(totalEUoutput / (reactorTicks * 20)),
                                    decimalFormat.format(minEUoutput / 20.0),
                                    decimalFormat.format(maxEUoutput / 20.0)));
                            if (totalRodCount > 0) {
                                report(formatI18n("Simulation.Efficiency", totalEUoutput / reactorTicks / 100 / totalRodCount, minEUoutput / 100 / totalRodCount, maxEUoutput / 100 / totalRodCount));
                            }
                        }
                    }
                } else if (!anyRodsDepleted) {
                    anyRodsDepleted = true;
                    data.firstRodDepletedTime = reactorTicks;
                    data.firstRodDepletedRow = row;
                    data.firstRodDepletedCol = col;
                    data.firstRodDepletedDescription = component.toString();
                    report(formatI18n("Simulation.FirstRodDepletedDetails", component.toString(), row, col, reactorTicks));
                    if (reactor.isFluid()) {
                        data.predepleteTotalHUoutput = 40 * totalHeatOutput;
                        data.predepleteAvgHUoutput = 2 * totalHeatOutput / reactorTicks;
                        data.predepleteMinHUoutput = 2 * minHeatOutput;
                        data.predepleteMaxHUoutput = 2 * maxHeatOutput;
                        report(formatI18n("Simulation.HeatOutputsBeforeDepleted",
                                decimalFormat.format(40 * totalHeatOutput),
                                decimalFormat.format(2 * totalHeatOutput / reactorTicks),
                                decimalFormat.format(2 * minHeatOutput),
                                decimalFormat.format(2 * maxHeatOutput)));
                        if (totalRodCount > 0) {
                            report(formatI18n("Simulation.Efficiency", totalHeatOutput / reactorTicks / 4 / totalRodCount, minHeatOutput / 4 / totalRodCount, maxHeatOutput / 4 / totalRodCount));
                        }
                    } else {
                        data.predepleteTotalEUoutput = totalEUoutput;
                        data.predepleteAvgEUoutput = totalEUoutput / (reactorTicks * 20);
                        data.predepleteMinEUoutput = minEUoutput / 20.0;
                        data.predepleteMaxEUoutput = maxEUoutput / 20.0;
                        report(formatI18n("Simulation.EUOutputsBeforeDepleted",
                                decimalFormat.format(totalEUoutput),
                                decimalFormat.format(totalEUoutput / (reactorTicks * 20)),
                                decimalFormat.format(minEUoutput / 20.0),
                                decimalFormat.format(maxEUoutput / 20.0)));
                        if (totalRodCount > 0) {
                            report(formatI18n("Simulation.Efficiency", totalEUoutput / reactorTicks / 100 / totalRodCount, minEUoutput / 100 / totalRodCount, maxEUoutput / 100 / totalRodCount));
                        }
                    }
                    data.predepleteMinTemp = minReactorHeat;
                    data.predepleteMaxTemp = maxReactorHeat;
                    report(formatI18n("Simulation.ReactorMinTempBeforeDepleted", minReactorHeat));
                    report(formatI18n("Simulation.ReactorMaxTempBeforeDepleted", maxReactorHeat));
                }
                showHeatingCooling(reactorTicks);
            }
        }
    }


    private void handleAutomation(final int reactorTicks) {
        for (int index = 0; index < components.length; index++) {
            ReactorItem component = components[index];
            if (reactor.isAutomated() && (vacuum == null || !VacuumCooling.supports(component))
                    && needsReplacement(component)) {
                if (gtnh) {
                    component.reloadInitialHeat();
                } else {
                    component.clearCurrentHeat();
                }
                component.clearDamage();
                int slot = slots[index];
                alreadyBroken[slot / Reactor.MAX_COLUMNS][slot % Reactor.MAX_COLUMNS] = false;
                replacedItems.add(component.name);
                if (replacementCount[index]++ < 8) {
                    component.info.append(formatI18n("ComponentInfo.ReplacedTime", reactorTicks));
                }
                if (component.getReactorPause() > 0) {
                    active = false;
                    pauseTimer = Math.max(pauseTimer, component.getReactorPause());
                    minActiveTime = Math.min(currentActiveTime, minActiveTime);
                    maxActiveTime = Math.max(currentActiveTime, maxActiveTime);
                    currentActiveTime = 0;
                }
            }
            if (reactor.isUsingReactorCoolantInjectors() && (!gtnh || !component.isBroken()) && component.needsCoolantInjected()) {
                component.injectCoolant();
                if ("rshCondensator".equals(component.baseName)) {
                    redstoneUsed++;
                } else if ("lzhCondensator".equals(component.baseName)) {
                    lapisUsed++;
                }
            }
        }
    }

    private boolean needsReplacement(ReactorItem component) {
        int threshold = component.getAutomationThreshold();
        if (component.getMaxHeat() > 1) {
            double initial = component.getInitialHeat();
            double current = component.getCurrentHeat();
            return (!gtnh || !component.isBroken()) && ((threshold > initial && current >= threshold)
                    || (threshold < initial && current <= threshold));
        }
        return component.isBroken() || (component.getMaxDamage() > 1 && component.getCurrentDamage() >= threshold);
    }

    private void checkReactorTemperature(final int reactorTicks) {
        double fraction = gtnh
                ? (float) reactor.getCurrentHeat() / (float) reactor.getMaxHeat()
                : reactor.getCurrentHeat() / reactor.getMaxHeat();
        if (fraction < 0.5 && !reachedBelow50 && (reachedEvaporate || startedAtOrAbove50)) {
            report(formatI18n("Simulation.TimeToBelow50", reactorTicks));
            reachedBelow50 = true;
            data.timeToBelow50 = reactorTicks;
        }
        if (fraction >= (gtnh ? 0.4f : 0.4) && !reachedBurn) {
            report(formatI18n("Simulation.TimeToBurn", reactorTicks));
            reachedBurn = true;
            data.timeToBurn = reactorTicks;
        }
        if (fraction >= 0.5 && !reachedEvaporate) {
            report(formatI18n("Simulation.TimeToEvaporate", reactorTicks));
            reachedEvaporate = true;
            data.timeToEvaporate = reactorTicks;
        }
        if (fraction >= (gtnh ? 0.7f : 0.7) && !reachedHurt) {
            report(formatI18n("Simulation.TimeToHurt", reactorTicks));
            reachedHurt = true;
            data.timeToHurt = reactorTicks;
        }
        if (fraction >= (gtnh ? 0.85f : 0.85) && !reachedLava) {
            report(formatI18n("Simulation.TimeToLava", reactorTicks));
            reachedLava = true;
            data.timeToLava = reactorTicks;
        }
        if (fraction >= 1 && !reachedExplode) {
            report(formatI18n("Simulation.TimeToXplode", reactorTicks));
            reachedExplode = true;
            data.timeToXplode = reactorTicks;
        }
    }

    private void calculateHeatingCooling(final int reactorTicks) {
        if (reactorTicks > 20 && !showHeatingCoolingCalled) {
            for (ReactorItem component : components) {
                totalHullHeating += component.getCurrentHullHeating();
                totalComponentHeating += component.getCurrentComponentHeating();
                totalHullCooling += component.getCurrentHullCooling();
                totalVentCooling += component.getCurrentVentCooling();
            }
        }
    }

    private void showHeatingCooling(final int reactorTicks) {
        if (!showHeatingCoolingCalled) {
            showHeatingCoolingCalled = true;
            if (reactorTicks >= 40) {
                double totalHullCoolingCapacity = 0;
                double totalVentCoolingCapacity = 0;
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorItem component = reactor.getComponentAt(row, col);
                        if (component != null) {
                            totalHullCoolingCapacity += component.getHullCoolingCapacity();
                            totalVentCoolingCapacity += component.getVentCoolingCapacity();
                        }
                    }
                }
                data.hullHeating = totalHullHeating / (reactorTicks - 20);
                data.componentHeating = totalComponentHeating / (reactorTicks - 20);
                data.hullCooling = totalHullCooling / (reactorTicks - 20);
                data.hullCoolingCapacity = totalHullCoolingCapacity;
                data.ventCooling = totalVentCooling / (reactorTicks - 20);
                data.ventCoolingCapacity = totalVentCoolingCapacity;
                if (totalHullHeating > 0) {
                    report(formatI18n("Simulation.HullHeating", totalHullHeating / (reactorTicks - 20)));
                }
                if (totalComponentHeating > 0) {
                    report(formatI18n("Simulation.ComponentHeating", totalComponentHeating / (reactorTicks - 20)));
                }
                if (totalHullCoolingCapacity > 0) {
                    report(formatI18n("Simulation.HullCooling", totalHullCooling / (reactorTicks - 20), totalHullCoolingCapacity));
                }
                if (totalVentCoolingCapacity > 0) {
                    report(formatI18n("Simulation.VentCooling", totalVentCooling / (reactorTicks - 20), totalVentCoolingCapacity));
                }
            }
        }
    }
}
