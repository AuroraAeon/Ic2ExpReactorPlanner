package Ic2ExpReactorPlanner;

import static Ic2ExpReactorPlanner.BundleHelper.getI18n;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/** Invokes the reactor model in a worker and applies display updates on the EDT. */
public class AutomationSimulator extends SwingWorker<Void, String> {
    private final JTextArea output;
    private final JPanel[][] reactorButtonPanels;
    private final ReactorSimulation simulation;
    private volatile SimulationData data;
    private volatile boolean stopRequested;

    public AutomationSimulator(Reactor reactor, JTextArea output, JPanel[][] reactorButtonPanels,
            File csvFile, int csvLimit) {
        this(reactor, output, reactorButtonPanels, csvFile, csvLimit, null);
    }

    public AutomationSimulator(Reactor reactor, JTextArea output, JPanel[][] reactorButtonPanels,
            File csvFile, int csvLimit, VacuumCooling.Config vacuumConfig) {
        this.output = output;
        this.reactorButtonPanels = reactorButtonPanels;
        simulation = new ReactorSimulation(reactor, csvFile, csvLimit, vacuumConfig,
                output == null && reactorButtonPanels == null ? null : this::publish,
                () -> stopRequested || isCancelled(), this::setProgress, reactorButtonPanels != null);
    }

    /** Runs without a worker or display, with a private copy of the design. */
    public static SimulationData simulate(Reactor design) throws IOException {
        return simulate(design, null);
    }

    public static SimulationData simulate(Reactor design, VacuumCooling.Config vacuumConfig) throws IOException {
        return new ReactorSimulation(design.copyDesign(), null, 0, vacuumConfig,
                null, () -> false, progress -> { }, false).run();
    }

    public SimulationData getData() {
        return data;
    }

    /** Lets queued and running simulations both reach the normal SwingWorker completion callback. */
    public void requestStop() {
        stopRequested = true;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    @Override
    protected Void doInBackground() throws IOException {
        data = simulation.run();
        if (data != null) {
            firePropertyChange("completed", null, true);
        }
        return null;
    }

    @Override
    protected void process(List<String> chunks) {
        for (String chunk : chunks) {
            if (chunk.isEmpty()) {
                if (output != null) {
                    output.setText("");
                }
            } else {
                if (chunk.matches("R\\dC\\d:.*")) { //NO18N
                    String temp = chunk.substring(5);
                    int row = chunk.charAt(1) - '0';
                    int col = chunk.charAt(3) - '0';
                    if (reactorButtonPanels != null && reactorButtonPanels[row][col] != null && temp.startsWith("0x")) {
                        reactorButtonPanels[row][col].setBackground(Color.decode(temp));
                        if ("0xC0C0C0".equals(temp)) {
                            reactorButtonPanels[row][col].setToolTipText(null);
                        } else if ("0xFF0000".equals(temp)) {
                            reactorButtonPanels[row][col].setToolTipText(getI18n("ComponentTooltip.Broken"));
                        } else if ("0xFFA500".equals(temp)) {
                            reactorButtonPanels[row][col].setToolTipText(getI18n("ComponentTooltip.ResidualHeat"));
                        }
                    }
                } else if (output != null) {
                    output.append(chunk);
                }
            }
        }
    }
    
}
