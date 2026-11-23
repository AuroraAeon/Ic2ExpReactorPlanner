/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Ic2ExpReactorPlanner;

import static Ic2ExpReactorPlanner.BundleHelper.formatI18n;
import static Ic2ExpReactorPlanner.BundleHelper.getI18n;
import Ic2ExpReactorPlanner.components.CoolantCell;
import Ic2ExpReactorPlanner.components.FuelRod;
import Ic2ExpReactorPlanner.components.ReactorItem;
import Ic2ExpReactorPlanner.components.Vent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;

/** Swing workspace for designing, simulating and comparing IC2 reactors. */
public class ReactorPlannerFrame extends JFrame {
    private static final String VERSION = Objects.toString(ReactorPlannerFrame.class.getPackage().getImplementationVersion(),
            "development");
    private static final String GTNH = "GTNH";
    private static final int HISTORY_LIMIT = 100;
    private static final int MAX_LAYOUT_TEXT_BYTES = 65_536;
    private static final Color SURFACE = new Color(0xf6f8fb);
    private static final Color WARNING = new Color(0x91291e);
    private static final Color STATUS = new Color(0x2c516a);
    private static final File PREFS_FILE = new File("erpprefs.xml");

    private Reactor reactor = new Reactor();
    private Reactor simulatedReactor;
    private Reactor currentDesign;
    private Reactor prevDesign;
    private AutomationSimulator runningSimulator;
    private AutomationSimulator simulator;
    private AutomationSimulator prevSimulator;
    private JPanel[][] highlights;
    private String currentReactorCode;
    private String prevReactorCode;
    private String prevReactorOldCode;
    private String runProfile;
    private VacuumCooling.Config completedVacuum;
    private SwingWorker<SimulationData, Void> preview;
    private final Timer previewTimer = new Timer(280, event -> startPreview());
    private boolean syncing;
    private boolean filteringPalette;
    private int brushId = -1;
    private final ReactorItem[] brushSettings = new ReactorItem[ComponentFactory.getComponentCount()];
    private final Deque<Reactor> undoHistory = new ArrayDeque<>();
    private final Deque<Reactor> redoHistory = new ArrayDeque<>();
    private final Properties prefs = new Properties();

    private final JFileChooser csvChooser = new JFileChooser();
    private final JFileChooser layoutChooser = new JFileChooser();
    private final JFileChooser textureChooser = new JFileChooser();
    private final JComboBox<String> mcVersionCombo = new JComboBox<>(new String[] {"1.7.10", "1.12.2"});
    private final JComboBox<String> gtVersionCombo = new JComboBox<>(new String[] {GTNH, "none", "5.08", "5.09"});
    private final JSpinner chambers = spinner(6, 0, 6);
    private final JSpinner heatSpinner = spinner(0, 0, (int) reactor.getMaxHeat() - 1);
    private final JSpinner maxTime = spinner(5_000_000, 0, 5_000_000);
    private final JSpinner fluidTransfer = spinner(10_000, 0, 10_000);
    private final JSpinner onPulse = spinner(5_000_000, 0, 5_000_000);
    private final JSpinner offPulse = spinner(0, 0, 5_000_000);
    private final JSpinner suspendTemp = spinner(120_000, 0, 120_000);
    private final JSpinner resumeTemp = spinner(120_000, 0, 120_000);
    private final JSpinner componentHeat = spinner(0, 0, Reactor.MAX_COMPONENT_HEAT);
    private final JSpinner placingThreshold = spinner(0, 0, Reactor.MAX_COMPONENT_HEAT);
    private final JSpinner placingPause = spinner(0, 0, 10_000);
    private final JSpinner threshold = spinner(0, 0, Reactor.MAX_COMPONENT_HEAT);
    private final JSpinner pause = spinner(0, 0, 10_000);
    private final JSpinner csvLimit = spinner(10_000, 1, 5_000_000);
    private final JSpinner vacuumThreshold = spinner(90, 1, 99);
    private final JSpinner vacuumStock = spinner(4, 0, 100_000);
    private final JSpinner vacuumMachines = spinner(1, 1, 1024);
    private final JSpinner vacuumOverclocks = spinner(2, 0, 6);
    private final JRadioButton euMode = new JRadioButton(getI18n("Config.EUReactor"));
    private final JRadioButton fluidMode = new JRadioButton(getI18n("Config.FluidReactor"));
    private final JCheckBox automated = new JCheckBox(getI18n("UI.AutomatedReactor"));
    private final JCheckBox pulsed = new JCheckBox(getI18n("UI.PulsedReactor"));
    private final JCheckBox injectors = new JCheckBox(getI18n("Config.ReactorCoolantInjectors"));
    private final JCheckBox csvOutput = new JCheckBox(getI18n("Config.CSVCheckbox"));
    private final JCheckBox vacuumLoop = new JCheckBox(getI18n("Workspace.Vacuum"));
    private final JCheckBox oldCode = new JCheckBox(getI18n("UI.ShowOldStyleReactorCode"));
    private final JCheckBox expandAlloy = new JCheckBox(getI18n("UI.ExpandAdvancedAlloy"));
    private final JCheckBox showDetails = new JCheckBox(getI18n("Workspace.ShowInspector"));
    private final JCheckBox showPlacing = new JCheckBox(getI18n("UI.ShowComponentPreconfigControls"));
    private final JCheckBox lockPrevCodeCheck = new JCheckBox(getI18n("UI.LockInTabCode"));
    private final JCheckBox onlyShowDiffCheck = new JCheckBox(getI18n("UI.OnlyShowDiffData"));
    private final JLabel texturePackLabel = new JLabel(getI18n("UI.TexturePackDefault"));
    private final JLabel csvFileLabel = new JLabel(getI18n("UI.CSVFileDefault"));
    private final JLabel safety = new JLabel();
    private final JLabel telemetry = new JLabel();
    private final JLabel selection = new JLabel();
    private final JLabel runState = new JLabel(getI18n("Workspace.Idle"));
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel comparisonLabel = new JLabel(getI18n("Comparison.Default"));
    private final JTextField comparisonCodeField = textField(24, false);
    private final JTextField codeField = textField(26, false);
    private final JTextField search = textField(12, true);
    private final JComboBox<String> componentFilter = new JComboBox<>(new String[] {
        getI18n("Workspace.AllComponents"), getI18n("Workspace.FuelComponents"),
        getI18n("Workspace.CoolingComponents"), getI18n("Workspace.OtherComponents")
    });
    private final DefaultListModel<Integer> paletteItems = new DefaultListModel<>();
    private final JList<Integer> palette = new JList<>(paletteItems) {
        @Override
        public String getToolTipText(MouseEvent event) {
            int index = locationToIndex(event.getPoint());
            if (index < 0 || !getCellBounds(index, index).contains(event.getPoint())) {
                return null;
            }
            ReactorItem item = ComponentFactory.getDefaultComponent(paletteItems.get(index));
            if (item == null) {
                return getI18n("Workspace.EraseBrush");
            }
            String value = item.name;
            if (item instanceof FuelRod) {
                value += " · " + formatI18n("Workspace.FuelLife", activeFuelUpdates(item));
            } else if (item.getMaxHeat() > 1) {
                value += " · " + formatI18n("Workspace.CellCapacity", 0.0, item.getMaxHeat());
            }
            return value;
        }
    };
    private final JTextArea componentArea = textArea(6);
    private final JTextArea materialsArea = textArea(6);
    private final JTextArea componentListArea = textArea(6);
    private final JScrollPane outputPane = scroll(textArea(8));
    private final JScrollPane comparisonScroll = scroll(comparisonLabel);
    private final JTabbedPane outputTabs = new JTabbedPane();
    private final JPanel pulsePanel = new JPanel();
    private final JPanel preconfiguration = new JPanel();
    private final JPanel inspector = new JPanel(new BorderLayout(0, 4));
    private final ReactorGridEditor grid = new ReactorGridEditor(() -> reactor, () -> reactor.getColumns(),
            this::edit, this::brush, () -> brushId >= 0, this::sample, this::updateInspector);
    private final Action undo = command("Workspace.Undo", this::undo);
    private final Action redo = command("Workspace.Redo", this::redo);
    private final Action fill = command("Workspace.Fill", grid::fill);
    private final Action erase = command("Workspace.Erase", grid::erase);
    private final Action run = command("UI.SimulateButton", this::runSimulation);
    private final Action cancel = command("UI.CancelButton", this::cancelSimulation);
    private final Action copyComparison = command("UI.CopyComparisonData", this::copyComparison);

    public ReactorPlannerFrame() {
        super(getI18n("UI.MainTitle"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        ToolTipManager.sharedInstance().setDismissDelay(20_000);
        setJMenuBar(createMenu());
        setContentPane(createWorkspace());
        wireControls();
        previewTimer.setRepeats(false);
        loadPreferences();
        applyProfile();
        refreshPalette();
        refreshDesign();
        cancel.setEnabled(false);
        copyComparison.setEnabled(false);

        GraphicsConfiguration configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        int width = bounds.width - insets.left - insets.right;
        int height = bounds.height - insets.top - insets.bottom;
        setMinimumSize(new Dimension(Math.min(width, 680), Math.min(height, 490)));
        setSize(Math.min(1180, width), Math.min(855, height));
        setLocationRelativeTo(null);
    }

    private JMenuBar createMenu() {
        JMenuBar menu = new JMenuBar();
        JMenu file = new JMenu(getI18n("Workspace.File"));
        addMenuAction(file, command("Workspace.Open", this::openLayout), KeyEvent.VK_O);
        addMenuAction(file, command("Workspace.Save", this::saveLayout), KeyEvent.VK_S);
        file.addSeparator();
        file.add(command("UI.PasteCodeButton", this::pasteLayout));
        file.add(command("UI.CopyCodeButton", () -> copyText(codeField.getText())));
        menu.add(file);

        JMenu edit = new JMenu(getI18n("Workspace.Edit"));
        addMenuAction(edit, undo, KeyEvent.VK_Z);
        addMenuAction(edit, redo, KeyEvent.VK_Y);
        edit.addSeparator();
        addMenuAction(edit, command("Workspace.CopyCells", grid::copy), KeyEvent.VK_C);
        addMenuAction(edit, command("Workspace.PasteCells", grid::paste), KeyEvent.VK_V);
        edit.add(fill);
        edit.add(erase);
        edit.add(command("UI.ClearGridButton", () -> edit(reactor::clearGrid)));
        menu.add(edit);

        JMenu simulation = new JMenu(getI18n("UI.SimulationTab"));
        JMenuItem simulate = simulation.add(run);
        simulate.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        simulation.add(cancel);
        menu.add(simulation);
        return menu;
    }

    private static void addMenuAction(JMenu menu, Action action, int key) {
        menu.add(action).setAccelerator(KeyStroke.getKeyStroke(key, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
    }

    private JPanel createWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 8));
        workspace.setBackground(SURFACE);
        workspace.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JPanel profile = row(labeled("Workspace.Profile", gtVersionCombo), labeled("Workspace.Chambers", chambers));
        profile.setToolTipText(getI18n("Workspace.ProfileHelp"));
        gtVersionCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                String key = GTNH.equals(value) ? "Workspace.ProfileGtnh" : "Workspace.ProfileIc2";
                String label = "5.08".equals(value) || "5.09".equals(value) ? "GregTech " + value : getI18n(key);
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        });
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(profile, BorderLayout.WEST);
        JPanel simulation = row(new JButton(run), new JButton(cancel));
        header.add(simulation, BorderLayout.EAST);
        workspace.add(header, BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.setMinimumSize(new Dimension(360, 220));
        left.add(grid, BorderLayout.CENTER);
        JPanel tools = new JPanel(new GridLayout(0, 1));
        tools.setOpaque(false);
        tools.add(row(new JButton(undo), new JButton(redo), new JButton(fill), new JButton(erase)));
        tools.add(row(button("Workspace.CopyCells", grid::copy), button("Workspace.PasteCells", grid::paste)));
        left.add(tools, BorderLayout.NORTH);
        JPanel layoutCode = new JPanel(new BorderLayout(5, 0));
        layoutCode.setOpaque(false);
        layoutCode.add(labeled("UI.CodeLabel", codeField), BorderLayout.CENTER);
        layoutCode.add(row(button("UI.CopyCodeButton", () -> copyText(codeField.getText())),
                button("UI.PasteCodeButton", this::pasteLayout)), BorderLayout.EAST);
        left.add(layoutCode, BorderLayout.SOUTH);
        JPanel palettePanel = createPalette();
        JSplitPane designSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, palettePanel);
        designSplit.setResizeWeight(0.73);
        designSplit.setContinuousLayout(true);
        designSplit.setBorder(null);
        designSplit.setMinimumSize(new Dimension(0, 238));

        createResultTabs();
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, designSplit, outputTabs);
        vertical.setResizeWeight(0.70);
        vertical.setContinuousLayout(true);
        vertical.setBorder(null);
        outputTabs.setMinimumSize(new Dimension(0, 95));
        workspace.add(vertical, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> vertical.setDividerLocation(0.73));

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        JPanel summaries = new JPanel(new GridLayout(0, 1, 0, 2));
        summaries.setOpaque(false);
        telemetry.setForeground(STATUS);
        summaries.add(telemetry);
        summaries.add(safety);
        footer.add(summaries, BorderLayout.CENTER);
        progress.setPreferredSize(new Dimension(100, 12));
        progress.getAccessibleContext().setAccessibleName(getI18n("UI.SimulationTab"));
        footer.add(row(runState, progress), BorderLayout.EAST);
        workspace.add(footer, BorderLayout.SOUTH);
        return workspace;
    }

    private JPanel createPalette() {
        palette.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        palette.setVisibleRowCount(6);
        palette.setFixedCellHeight(28);
        palette.setDragEnabled(true);
        palette.setToolTipText("");
        palette.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                ReactorItem item = value instanceof Integer id ? ComponentFactory.getDefaultComponent(id) : null;
                super.getListCellRendererComponent(list, item == null ? getI18n("Workspace.EraseBrush") : item.name,
                        index, selected, focus);
                setIcon(item == null || item.image == null ? null : new ImageIcon(item.image));
                setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 7));
                return this;
            }
        });
        palette.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent component) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent component) {
                return palette.getSelectedValue() == null ? null : ReactorGridEditor.paletteDrag(brush());
            }
        });
        JPanel controls = form();
        addField(controls, 0, "Workspace.SearchComponents", search);
        addField(controls, 1, "Workspace.ComponentCategory", componentFilter);
        search.setToolTipText(getI18n("Workspace.SearchHelp"));
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(246, 120));
        panel.add(controls, BorderLayout.NORTH);
        JScrollPane paletteScroll = scroll(palette);
        paletteScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(paletteScroll, BorderLayout.CENTER);
        preconfiguration.setLayout(new GridBagLayout());
        preconfiguration.setOpaque(false);
        addField(preconfiguration, 0, "Config.InitialComponentHeat", componentHeat);
        addField(preconfiguration, 1, "Config.PlacingReplacementThreshold", placingThreshold);
        addField(preconfiguration, 2, "Config.PlacingReactorPause", placingPause);
        panel.add(preconfiguration, BorderLayout.SOUTH);
        return panel;
    }

    private void createResultTabs() {
        outputTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        outputTabs.addTab(getI18n("UI.SimulationTab"), outputPane);
        JPanel configuration = form();
        ButtonGroup modes = new ButtonGroup();
        modes.add(euMode);
        modes.add(fluidMode);
        addField(configuration, 0, "Config.SimulationStyle", row(euMode, fluidMode));
        addField(configuration, 1, "UI.InitialReactorHeat", heatSpinner);
        addField(configuration, 2, "Workspace.LimitSeconds", maxTime);
        addFull(configuration, 3, row(automated, pulsed));
        addFull(configuration, 4, injectors);
        addField(configuration, 5, "Workspace.FluidTransfer", fluidTransfer);
        fluidTransfer.setToolTipText(getI18n("Workspace.FluidTransferHelp"));
        outputTabs.addTab(getI18n("Workspace.RunSettings"), scroll(configuration));

        JPanel cooling = form();
        addFull(cooling, 0, vacuumLoop);
        addField(cooling, 1, "Workspace.VacuumThreshold", vacuumThreshold);
        addField(cooling, 2, "Workspace.VacuumReserve", vacuumStock);
        addField(cooling, 3, "Workspace.VacuumMachines", vacuumMachines);
        addField(cooling, 4, "Workspace.VacuumOverclocks", vacuumOverclocks);
        addFull(cooling, 5, new JLabel(getI18n("Workspace.VacuumHelp")));
        outputTabs.addTab(getI18n("Workspace.Vacuum"), scroll(cooling));

        pulsePanel.setLayout(new GridBagLayout());
        addField(pulsePanel, 0, "Config.OnPulse", onPulse);
        addField(pulsePanel, 1, "Config.OffPulse", offPulse);
        addField(pulsePanel, 2, "Config.SuspendTemp", suspendTemp);
        addField(pulsePanel, 3, "Config.ResumeTemp", resumeTemp);
        addFull(pulsePanel, 4, button("UI.ResetPulseConfig", () -> edit(reactor::resetPulseConfig)));
        outputTabs.addTab(getI18n("UI.PulseConfigurationTab"), scroll(pulsePanel));

        JPanel inspectorControls = form();
        addFull(inspectorControls, 0, selection);
        addField(inspectorControls, 1, "Config.ReplacementThreshold", threshold);
        addField(inspectorControls, 2, "Config.ReactorPause", pause);
        inspector.add(inspectorControls, BorderLayout.WEST);
        inspector.add(scroll(componentArea), BorderLayout.CENTER);
        outputTabs.addTab(getI18n("UI.ComponentTab"), inspector);
        outputTabs.addTab(getI18n("UI.ComponentListTab"), scroll(componentListArea));
        outputTabs.addTab(getI18n("UI.MaterialsTab"), scroll(materialsArea));

        JPanel comparison = new JPanel(new BorderLayout(0, 4));
        comparison.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel comparisonControls = new JPanel(new GridLayout(0, 1));
        comparisonControls.add(row(comparisonCodeField,
                button("UI.CopyCodeButton", () -> copyText(comparisonCodeField.getText())), lockPrevCodeCheck));
        comparisonControls.add(row(onlyShowDiffCheck, new JButton(copyComparison)));
        comparison.add(comparisonControls, BorderLayout.NORTH);
        comparisonLabel.setVerticalAlignment(JLabel.TOP);
        comparison.add(comparisonScroll, BorderLayout.CENTER);
        outputTabs.addTab(getI18n("UI.ComparisonTab"), comparison);

        JPanel csv = form();
        addFull(csv, 0, csvOutput);
        addField(csv, 1, "Config.CSVLimit", csvLimit);
        addFull(csv, 2, row(csvFileLabel, button("UI.CSVBrowseButton", this::chooseCsv)));
        addFull(csv, 3, new JLabel(getI18n("UI.CSVHelp")));
        outputTabs.addTab(getI18n("UI.CSVTab"), scroll(csv));

        JPanel advanced = form();
        addField(advanced, 0, "UI.MinecraftVersion", mcVersionCombo);
        addFull(advanced, 1, row(showDetails, showPlacing));
        addFull(advanced, 2, oldCode);
        addFull(advanced, 3, expandAlloy);
        addFull(advanced, 4, row(texturePackLabel, button("UI.TexturePackBrowseButton", this::chooseTexture),
                button("UI.TexturePackClearButton", this::clearTexture)));
        addFull(advanced, 5, new JLabel(getI18n("UI.TexturePackHelp")));
        addFull(advanced, 6, new JLabel(formatI18n("UI.VersionNumber", VERSION)));
        outputTabs.addTab(getI18n("UI.AdvancedTab"), scroll(advanced));
    }

    private void wireControls() {
        gtVersionCombo.addActionListener(event -> {
            if (!syncing) {
                applyProfile();
                undoHistory.clear();
                redoHistory.clear();
                refreshPalette();
                refreshDesign();
                savePreferences();
            }
        });
        mcVersionCombo.addActionListener(event -> {
            if (!syncing) {
                applyProfile();
                refreshPalette();
                refreshDesign();
                savePreferences();
            }
        });
        chambers.addChangeListener(event -> {
            if (!syncing) {
                try {
                    edit(() -> reactor.setChambers(number(chambers)));
                } catch (IllegalArgumentException ex) {
                    refreshDesign();
                    warning("Workspace.ChambersOccupied");
                }
            }
        });
        configure(heatSpinner, value -> reactor.setCurrentHeat(value));
        configure(maxTime, value -> reactor.setMaxSimulationTicks(value));
        configure(fluidTransfer, value -> reactor.setFluidTransferLimit(value));
        configure(onPulse, value -> reactor.setOnPulse(value));
        configure(offPulse, value -> reactor.setOffPulse(value));
        configure(suspendTemp, value -> reactor.setSuspendTemp(value));
        configure(resumeTemp, value -> reactor.setResumeTemp(value));
        euMode.addActionListener(event -> edit(() -> reactor.setFluid(false)));
        fluidMode.addActionListener(event -> edit(() -> reactor.setFluid(true)));
        automated.addActionListener(event -> edit(() -> reactor.setAutomated(automated.isSelected())));
        pulsed.addActionListener(event -> edit(() -> reactor.setPulsed(pulsed.isSelected())));
        injectors.addActionListener(event -> edit(() -> reactor.setUsingReactorCoolantInjectors(injectors.isSelected())));
        configure(threshold, value -> {
            ReactorItem item = selectedItem();
            if (item != null) {
                item.setAutomationThreshold(value);
            }
        });
        configure(pause, value -> {
            ReactorItem item = selectedItem();
            if (item != null) {
                item.setReactorPause(value);
            }
        });
        componentHeat.addChangeListener(event -> changeBrush(item -> item.setInitialHeat(number(componentHeat))));
        placingThreshold.addChangeListener(event -> changeBrush(item -> item.setAutomationThreshold(number(placingThreshold))));
        placingPause.addChangeListener(event -> changeBrush(item -> item.setReactorPause(number(placingPause))));
        oldCode.addActionListener(event -> {
            refreshDesign();
            updateComparison();
            savePreferences();
        });
        showDetails.addActionListener(event -> {
            int tab = outputTabs.indexOfComponent(inspector);
            if (showDetails.isSelected() && tab < 0) {
                outputTabs.insertTab(getI18n("UI.ComponentTab"), null, inspector, null, 4);
            } else if (!showDetails.isSelected() && tab >= 0) {
                outputTabs.remove(inspector);
            }
            savePreferences();
        });
        showPlacing.addActionListener(event -> {
            preconfiguration.setVisible(showPlacing.isSelected());
            savePreferences();
        });
        expandAlloy.addActionListener(event -> {
            applyProfile();
            refreshDesign();
            savePreferences();
        });
        onlyShowDiffCheck.addActionListener(event -> updateComparison());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshPalette();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshPalette();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshPalette();
            }
        });
        componentFilter.addActionListener(event -> refreshPalette());
        vacuumLoop.addActionListener(event -> refreshDesign());
        vacuumThreshold.addChangeListener(event -> refreshDesign());
        vacuumStock.addChangeListener(event -> refreshDesign());
        vacuumMachines.addChangeListener(event -> refreshDesign());
        vacuumOverclocks.addChangeListener(event -> refreshDesign());
        palette.addListSelectionListener(event -> {
            if (!filteringPalette && !event.getValueIsAdjusting() && palette.getSelectedValue() != null) {
                selectBrush(palette.getSelectedValue());
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(
                KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "searchComponents");
        getRootPane().getActionMap().put("searchComponents", command("Workspace.SearchComponents", () -> {
            search.requestFocusInWindow();
            search.selectAll();
        }));
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "clearBrush");
        getRootPane().getActionMap().put("clearBrush", command("Workspace.ClearBrush", () -> {
            palette.clearSelection();
            selectBrush(-1);
        }));
    }

    private void configure(JSpinner input, Consumer<Integer> change) {
        input.addChangeListener(event -> {
            if (!syncing) {
                edit(() -> change.accept(number(input)));
            }
        });
    }

    private void applyProfile() {
        String gt = (String) gtVersionCombo.getSelectedItem();
        String minecraft = (String) mcVersionCombo.getSelectedItem();
        boolean isGtnh = GTNH.equals(gt);
        boolean isIc2 = "none".equals(gt);
        boolean isOldIc2 = "1.7.10".equals(minecraft) || !isIc2;
        reactor.setSimulationProfile(isGtnh ? SimulationProfile.GTNH_2_9_0 : SimulationProfile.LEGACY);
        reactor.setLegacyRules(isOldIc2, "5.09".equals(gt));
        MaterialsList.setGTVersion(gt);
        MaterialsList.setUseUfcForCoolantCells(!isOldIc2);
        MaterialsList.setExpandAdvancedAlloy(isIc2 && expandAlloy.isSelected());
        expandAlloy.setEnabled(isIc2);
        mcVersionCombo.setEnabled(isIc2 && runningSimulator == null);
        injectors.setEnabled(!isOldIc2);
        if (isOldIc2) {
            reactor.setUsingReactorCoolantInjectors(false);
        }
    }

    private boolean available(ReactorItem item) {
        String profile = (String) gtVersionCombo.getSelectedItem();
        if (item.id == 35 && "none".equals(profile) && "1.7.10".equals(mcVersionCombo.getSelectedItem())) {
            return false;
        }
        return reactor.getSimulationProfile().supports(item) && (item.sourceMod == null || switch (item.sourceMod) {
            case "GTNH" -> GTNH.equals(profile);
            case "GT5.08" -> GTNH.equals(profile) || "5.08".equals(profile) || "5.09".equals(profile);
            case "GT5.09" -> "5.09".equals(profile);
            case "Coaxium" -> "none".equals(profile);
            default -> false;
        });
    }

    private void refreshPalette() {
        filteringPalette = true;
        paletteItems.clear();
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        int filter = componentFilter.getSelectedIndex();
        if (filter == 0 && getI18n("Workspace.EraseBrush").toLowerCase(Locale.ROOT).contains(query)) {
            paletteItems.addElement(0);
        }
        for (int id = 1; id < brushSettings.length; id++) {
            ReactorItem item = ComponentFactory.getDefaultComponent(id);
            if (item == null || !available(item)) {
                continue;
            }
            int category = item instanceof FuelRod ? 1
                    : item instanceof CoolantCell || item instanceof Vent || item.getMaxHeat() > 1 ? 2 : 3;
            if ((filter == 0 || filter == category) && (item.name.toLowerCase(Locale.ROOT).contains(query)
                    || item.baseName.toLowerCase(Locale.ROOT).contains(query))) {
                paletteItems.addElement(id);
            }
        }
        if (brushId >= 0 && !paletteItems.contains(brushId)) {
            selectBrush(-1);
        }
        palette.setSelectedValue(brushId, false);
        filteringPalette = false;
    }

    private void selectBrush(int id) {
        brushId = id;
        if (id > 0 && brushSettings[id] == null) {
            brushSettings[id] = ComponentFactory.createComponent(id);
        }
        ReactorItem chosen = id > 0 ? brushSettings[id] : null;
        syncing = true;
        try {
            componentHeat.setValue(chosen == null ? 0 : (int) chosen.getInitialHeat());
            placingThreshold.setValue(chosen == null ? 0 : chosen.getAutomationThreshold());
            placingPause.setValue(chosen == null ? 0 : chosen.getReactorPause());
            componentHeat.setEnabled(chosen != null && chosen.getMaxHeat() > 1);
            placingThreshold.setEnabled(chosen != null && (chosen.getMaxHeat() > 1 || chosen.getMaxDamage() > 1));
            placingPause.setEnabled(placingThreshold.isEnabled());
            fill.setEnabled(id >= 0);
        } finally {
            syncing = false;
        }
    }

    private void changeBrush(Consumer<ReactorItem> change) {
        if (!syncing && brushId > 0) {
            change.accept(brushSettings[brushId]);
        }
    }

    private void sample(ReactorItem item) {
        if (available(item)) {
            if (!paletteItems.contains(item.id)) {
                search.setText("");
                componentFilter.setSelectedIndex(0);
            }
            brushSettings[item.id] = duplicate(item);
            selectBrush(item.id);
            palette.setSelectedValue(item.id, true);
        }
    }

    private ReactorItem brush() {
        return brushId <= 0 ? null : duplicate(brushSettings[brushId]);
    }

    private static ReactorItem duplicate(ReactorItem item) {
        ReactorItem result = ComponentFactory.createComponent(item.id);
        result.setInitialHeat(item.getInitialHeat());
        result.setAutomationThreshold(item.getAutomationThreshold());
        result.setReactorPause(item.getReactorPause());
        return result;
    }

    private ReactorItem selectedItem() {
        return reactor.getComponentAt(grid.selectedRow(), grid.selectedColumn());
    }

    private void edit(Runnable operation) {
        if (syncing) {
            return;
        }
        Reactor before = reactor.copyDesign();
        operation.run();
        if (!sameDesign(before, reactor)) {
            remember(undoHistory, before);
            redoHistory.clear();
        }
        refreshDesign();
    }

    private static void remember(Deque<Reactor> history, Reactor design) {
        if (history.size() == HISTORY_LIMIT) {
            history.removeLast();
        }
        history.push(design);
    }

    private void undo() {
        if (!undoHistory.isEmpty()) {
            remember(redoHistory, reactor.copyDesign());
            reactor = undoHistory.pop();
            syncProfileWithDesign();
            refreshDesign();
        }
    }

    private void redo() {
        if (!redoHistory.isEmpty()) {
            remember(undoHistory, reactor.copyDesign());
            reactor = redoHistory.pop();
            syncProfileWithDesign();
            refreshDesign();
        }
    }

    private void syncProfileWithDesign() {
        syncing = true;
        try {
            if (reactor.getSimulationProfile() == SimulationProfile.GTNH_2_9_0) {
                gtVersionCombo.setSelectedItem(GTNH);
            } else {
                if (reactor.usesLegacyGt509()) {
                    gtVersionCombo.setSelectedItem("5.09");
                } else if (!reactor.usesLegacyEarlyReflectors() || !"5.08".equals(gtVersionCombo.getSelectedItem())) {
                    gtVersionCombo.setSelectedItem("none");
                }
                mcVersionCombo.setSelectedItem(reactor.usesLegacyEarlyReflectors() ? "1.7.10" : "1.12.2");
            }
        } finally {
            syncing = false;
        }
        applyProfile();
        refreshPalette();
    }

    private void refreshDesign() {
        syncing = true;
        try {
            chambers.setValue(reactor.getChambers());
            ((SpinnerNumberModel) heatSpinner.getModel()).setMaximum((int) reactor.getMaxHeat() - 1);
            heatSpinner.setValue((int) reactor.getCurrentHeat());
            maxTime.setValue(reactor.getMaxSimulationTicks());
            fluidTransfer.setValue(reactor.getFluidTransferLimit());
            fluidTransfer.setEnabled(reactor.isFluid()
                    && reactor.getSimulationProfile() == SimulationProfile.GTNH_2_9_0);
            onPulse.setValue(reactor.getOnPulse());
            offPulse.setValue(reactor.getOffPulse());
            suspendTemp.setValue(reactor.getSuspendTemp());
            resumeTemp.setValue(reactor.getResumeTemp());
            euMode.setSelected(!reactor.isFluid());
            fluidMode.setSelected(reactor.isFluid());
            automated.setSelected(reactor.isAutomated());
            pulsed.setSelected(reactor.isPulsed());
            injectors.setSelected(reactor.isUsingReactorCoolantInjectors());
            codeField.setText(oldCode.isSelected() ? reactor.getOldCode() : reactor.getCode());
            codeField.setCaretPosition(0);
            materialsArea.setText(reactor.getMaterials().toString());
            componentListArea.setText(reactor.getComponentList().toString());
        } finally {
            syncing = false;
        }
        undo.setEnabled(!undoHistory.isEmpty());
        redo.setEnabled(!redoHistory.isEmpty());
        fill.setEnabled(brushId >= 0);
        boolean vacuumAvailable = reactor.getSimulationProfile() == SimulationProfile.GTNH_2_9_0 && !reactor.isFluid();
        vacuumLoop.setEnabled(vacuumAvailable);
        vacuumThreshold.setEnabled(vacuumAvailable && vacuumLoop.isSelected());
        vacuumStock.setEnabled(vacuumThreshold.isEnabled());
        vacuumMachines.setEnabled(vacuumThreshold.isEnabled());
        vacuumOverclocks.setEnabled(vacuumThreshold.isEnabled());
        boolean current = currentResults();
        grid.setResultHighlights(current ? highlights : null);
        grid.refresh();
        updateInspector();
        updateSafety(current ? simulator.getData() : null);
        if (preview != null) {
            preview.cancel(true);
            preview = null;
        }
        previewTimer.stop();
        if (!current && runningSimulator == null && getI18n("Workspace.RunToValidate").equals(safety.getText())
                && reactor.getSimulationProfile() == SimulationProfile.GTNH_2_9_0) {
            previewTimer.restart();
        }
    }

    private boolean currentResults() {
        return currentDesign != null && sameDesign(currentDesign, reactor) && profileSelection().equals(runProfile)
                && Objects.equals(vacuumConfig(), completedVacuum);
    }

    private String profileSelection() {
        return gtVersionCombo.getSelectedItem() + ":" + mcVersionCombo.getSelectedItem();
    }

    private static boolean sameDesign(Reactor a, Reactor b) {
        if (a.getSimulationProfile() != b.getSimulationProfile() || a.getChambers() != b.getChambers()
                || a.getCurrentHeat() != b.getCurrentHeat() || a.isFluid() != b.isFluid()
                || a.isPulsed() != b.isPulsed() || a.isAutomated() != b.isAutomated()
                || a.getMaxSimulationTicks() != b.getMaxSimulationTicks()
                || a.getFluidTransferLimit() != b.getFluidTransferLimit()
                || a.getOnPulse() != b.getOnPulse() || a.getOffPulse() != b.getOffPulse()
                || a.getSuspendTemp() != b.getSuspendTemp() || a.getResumeTemp() != b.getResumeTemp()
                || a.isUsingReactorCoolantInjectors() != b.isUsingReactorCoolantInjectors()) {
            return false;
        }
        for (int row = 0; row < ReactorGridEditor.ROWS; row++) {
            for (int col = 0; col < a.getColumns(); col++) {
                ReactorItem left = a.getComponentAt(row, col);
                ReactorItem right = b.getComponentAt(row, col);
                if (left != right && (left == null || right == null || left.id != right.id
                        || left.getInitialHeat() != right.getInitialHeat()
                        || left.getAutomationThreshold() != right.getAutomationThreshold()
                        || left.getReactorPause() != right.getReactorPause())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void updateInspector() {
        ReactorItem design = selectedItem();
        int row = grid.selectedRow();
        int column = grid.selectedColumn();
        selection.setText(formatI18n("Workspace.Slot", (char) ('A' + row), column + 1,
                design == null ? getI18n("Workspace.EmptySlot") : design.name));
        boolean configurable = design != null && (design.getMaxDamage() > 1 || design.getMaxHeat() > 1);
        syncing = true;
        try {
            threshold.setEnabled(configurable);
            pause.setEnabled(configurable);
            threshold.setValue(configurable ? design.getAutomationThreshold() : 0);
            pause.setValue(configurable ? design.getReactorPause() : 0);
        } finally {
            syncing = false;
        }
        StringBuilder text = new StringBuilder();
        if (design != null && design.getMaxHeat() > 1) {
            text.append(formatI18n("Workspace.CellCapacity", design.getInitialHeat(), design.getMaxHeat())).append('\n');
        }
        if (design instanceof FuelRod && design.getMaxDamage() > 1) {
            text.append(formatI18n("Workspace.FuelLife", activeFuelUpdates(design))).append('\n');
        }
        if (simulatedReactor != null && currentResults()) {
            ReactorItem item = simulatedReactor.getComponentAt(row, column);
            if (item != null) {
                if (item.getMaxHeat() > 1) {
                    text.append(formatI18n("Workspace.CellPeak", item.getMaxReachedHeat(), item.getMaxHeat())).append('\n');
                }
                if (item instanceof FuelRod && item.getMaxDamage() > 1) {
                    text.append(formatI18n("Workspace.FuelRemaining",
                            Math.max(0, activeFuelUpdates(item) - item.getCurrentDamage()))).append('\n');
                }
                text.append(item.info);
            } else if (design instanceof FuelRod) {
                text.append(formatI18n("Workspace.FuelRemaining", 0.0)).append('\n');
            }
            String status = highlights[row][column].getToolTipText();
            if (status != null) {
                text.append('\n').append(status);
            }
        }
        componentArea.setText(text.toString().strip());
        componentArea.setCaretPosition(0);
    }

    private double activeFuelUpdates(ReactorItem fuel) {
        SimulationProfile profile = reactor.getSimulationProfile();
        double damageLimit = fuel.getMaxDamage(profile);
        return profile == SimulationProfile.GTNH_2_9_0 ? damageLimit - 1 : damageLimit;
    }

    private int fuelSlots() {
        int count = 0;
        for (int row = 0; row < ReactorGridEditor.ROWS; row++) {
            for (int col = 0; col < reactor.getColumns(); col++) {
                if (reactor.getComponentAt(row, col) instanceof FuelRod) {
                    count++;
                }
            }
        }
        return count;
    }

    private String unavailableComponent() {
        for (int row = 0; row < ReactorGridEditor.ROWS; row++) {
            for (int col = 0; col < reactor.getColumns(); col++) {
                ReactorItem item = reactor.getComponentAt(row, col);
                if (item != null && !available(item)) {
                    return formatI18n("Workspace.UnsupportedSlot", (char) ('A' + row), col + 1, item.name);
                }
            }
        }
        return null;
    }

    private void updateSafety(SimulationData result) {
        int fuel = fuelSlots();
        telemetry.setText(formatI18n("Workspace.DesignSummary", reactor.getCurrentHeat(), reactor.getMaxHeat(), fuel));
        String reason = unavailableComponent();
        if (reason == null && reactor.isFluid() && reactor.getSimulationProfile() == SimulationProfile.GTNH_2_9_0
                && reactor.getChambers() != 6) {
            reason = getI18n("Workspace.FluidChambers");
        } else if (reason == null && reactor.getCurrentHeat() >= reactor.getMaxHeat() * 0.4) {
            reason = getI18n("Workspace.HighStartingHeat");
        }
        if (result != null) {
            double value = reactor.isFluid() ? result.avgHUoutput : result.avgEUoutput;
            telemetry.setText(formatI18n(reactor.isFluid() ? "Workspace.HeatSummary" : "Workspace.PowerSummary",
                    value, result.maxTemp, result.totalReactorTicks));
            String outcome = failure(result);
            if (outcome != null) {
                reason = outcome;
            }
        }
        if (reason == null && fuel == 0) {
            reason = getI18n("Workspace.NoFuel");
        }
        safety.setForeground(reason == null ? STATUS : WARNING);
        safety.setText(reason == null ? getI18n(result == null ? "Workspace.RunToValidate" : "Workspace.NoFailure") : reason);
        safety.setToolTipText(safety.getText());
    }

    private static String failure(SimulationData result) {
        if (result.exploded) {
            return formatI18n("Workspace.Exploded", result.totalReactorTicks);
        }
        if (result.firstComponentBrokenTime != Integer.MAX_VALUE) {
            return formatI18n("Workspace.ComponentFailed", result.firstComponentBrokenDescription,
                    (char) ('A' + result.firstComponentBrokenRow), result.firstComponentBrokenCol + 1,
                    result.firstComponentBrokenTime);
        }
        if (result.vacuumCooling != null && result.vacuumCooling.firstShortageCycle() > 0) {
            return formatI18n("Workspace.VacuumShortage", result.vacuumCooling.firstShortageCycle());
        }
        return null;
    }

    private VacuumCooling.Config vacuumConfig() {
        return vacuumLoop.isSelected() && vacuumLoop.isEnabled()
                ? new VacuumCooling.Config(number(vacuumThreshold), number(vacuumStock),
                        number(vacuumMachines), number(vacuumOverclocks)) : null;
    }

    private void startPreview() {
        Reactor sample = reactor.copyDesign();
        sample.setMaxSimulationTicks(Math.min(20, sample.getMaxSimulationTicks()));
        VacuumCooling.Config vacuum = vacuumConfig();
        preview = new SwingWorker<>() {
            @Override
            protected SimulationData doInBackground() throws Exception {
                return AutomationSimulator.simulate(sample, vacuum);
            }

            @Override
            protected void done() {
                if (preview != this) {
                    return;
                }
                preview = null;
                try {
                    String result = failure(get());
                    if (result != null) {
                        safety.setForeground(WARNING);
                        safety.setText(result);
                        safety.setToolTipText(result);
                    }
                } catch (CancellationException | ExecutionException ex) {
                    // The full-run action reports any engine failure directly.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        preview.execute();
    }

    private void runSimulation() {
        if (runningSimulator != null) {
            return;
        }
        String unavailable = unavailableComponent();
        if (unavailable != null) {
            JOptionPane.showMessageDialog(this, unavailable, getI18n("Warning.Title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (csvOutput.isSelected() && csvChooser.getSelectedFile() == null) {
            warning("Workspace.SelectCsv");
            return;
        }
        Reactor design = reactor.copyDesign();
        Reactor work = design.copyDesign();
        JTextArea output = textArea(8);
        outputPane.setViewportView(output);
        outputTabs.setSelectedComponent(outputPane);
        JPanel[][] runHighlights = new JPanel[ReactorGridEditor.ROWS][ReactorGridEditor.COLUMNS];
        for (int row = 0; row < runHighlights.length; row++) {
            for (int column = 0; column < runHighlights[row].length; column++) {
                runHighlights[row][column] = new JPanel();
            }
        }
        File csv = csvOutput.isSelected() ? csvChooser.getSelectedFile() : null;
        VacuumCooling.Config vacuum = vacuumConfig();
        String mode = profileSelection();
        AutomationSimulator worker;
        try {
            worker = new AutomationSimulator(work, output, runHighlights, csv, number(csvLimit), vacuum);
        } catch (IllegalArgumentException ex) {
            warning(design.isFluid() && design.getChambers() < 6 ? "Workspace.FluidChambers" : "Workspace.InvalidSetup");
            return;
        }
        previewTimer.stop();
        if (preview != null) {
            preview.cancel(true);
            preview = null;
        }
        runningSimulator = worker;
        gtVersionCombo.setEnabled(false);
        mcVersionCombo.setEnabled(false);
        run.setEnabled(false);
        cancel.setEnabled(true);
        progress.setValue(0);
        progress.setIndeterminate(true);
        runState.setText(getI18n("Workspace.Running"));
        worker.addPropertyChangeListener(event -> {
            if (runningSimulator != worker) {
                return;
            }
            if ("progress".equals(event.getPropertyName())) {
                progress.setIndeterminate(false);
                progress.setValue(worker.getProgress());
            } else if ("state".equals(event.getPropertyName()) && event.getNewValue() == SwingWorker.StateValue.DONE) {
                finishSimulation(worker, design, work, runHighlights, output, vacuum, mode);
            }
        });
        worker.execute();
    }

    private void cancelSimulation() {
        if (runningSimulator != null) {
            cancel.setEnabled(false);
            runState.setText(getI18n("Workspace.Cancelling"));
            runningSimulator.requestStop();
        }
    }

    private void finishSimulation(AutomationSimulator worker, Reactor design, Reactor work, JPanel[][] runHighlights,
            JTextArea output, VacuumCooling.Config vacuum, String mode) {
        runningSimulator = null;
        gtVersionCombo.setEnabled(true);
        applyProfile();
        run.setEnabled(true);
        cancel.setEnabled(false);
        progress.setIndeterminate(false);
        try {
            worker.get();
            if (worker.getData() == null) {
                progress.setValue(0);
                runState.setText(getI18n(worker.isStopRequested() ? "Workspace.Cancelled" : "Workspace.Failed"));
                return;
            }
            if (simulator != null && !lockPrevCodeCheck.isSelected()) {
                prevSimulator = simulator;
                prevDesign = currentDesign;
                prevReactorCode = currentReactorCode;
                prevReactorOldCode = prevDesign.getOldCode();
            }
            currentDesign = design;
            currentReactorCode = design.getCode();
            completedVacuum = vacuum;
            runProfile = mode;
            simulatedReactor = work;
            simulator = worker;
            highlights = runHighlights;
            progress.setValue(100);
            runState.setText(getI18n("Workspace.Completed"));
            appendRunDetails(output, worker.getData(), design);
            updateComparison();
            refreshDesign();
        } catch (CancellationException ex) {
            progress.setValue(0);
            runState.setText(getI18n("Workspace.Cancelled"));
        } catch (ExecutionException ex) {
            runState.setText(getI18n("Workspace.Failed"));
            output.append("\n" + formatI18n("Workspace.SimulationError", ex.getCause().getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            runState.setText(getI18n("Workspace.Failed"));
        }
    }

    private void appendRunDetails(JTextArea output, SimulationData data, Reactor design) {
        if (design.isFluid() && design.getSimulationProfile() == SimulationProfile.GTNH_2_9_0) {
            output.append(formatI18n("Workspace.HotCoolant", data.hotCoolantMillibuckets));
        }
        if (data.totalReactorTicks == design.getMaxSimulationTicks() && !data.exploded) {
            output.append(formatI18n("Workspace.ReachedLimit", data.totalReactorTicks));
        }
        GtnhThoriumCycle.Products product = data.thoriumProducts;
        if (product != null && product.spentRods() > 0) {
            output.append(formatI18n("Workspace.ThoriumProducts", product.singleRods(), product.dualRods(),
                    product.quadRods(), product.lutetiumDust(), product.thoriumDust(), product.ironDust()));
        }
        VacuumCooling.Result cooling = data.vacuumCooling;
        if (cooling != null) {
            output.append(formatI18n("Workspace.VacuumSummary", cooling.swaps(), cooling.regenerated(),
                    cooling.waitingOrCooling(), cooling.destroyed(), cooling.heatRemoved(), cooling.energyConsumedEu()));
            if (cooling.firstShortageCycle() > 0) {
                output.append(formatI18n("Workspace.VacuumShortage", cooling.firstShortageCycle()) + "\n");
            }
        }
        output.setCaretPosition(0);
    }

    private void openLayout() {
        if (layoutChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                var path = layoutChooser.getSelectedFile().toPath();
                if (Files.size(path) > MAX_LAYOUT_TEXT_BYTES) {
                    throw new IllegalArgumentException();
                }
                importLayout(Files.readString(path));
            } catch (IOException | IllegalArgumentException ex) {
                warning("Workspace.InvalidLayout");
            }
        }
    }

    private void saveLayout() {
        if (layoutChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = layoutChooser.getSelectedFile();
        if (file.exists() && JOptionPane.showConfirmDialog(this, getI18n("Workspace.ReplaceFile"), getI18n("Warning.Title"),
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Files.writeString(file.toPath(), reactor.getCode() + System.lineSeparator());
        } catch (IOException ex) {
            warning("Workspace.SaveFailed");
        }
    }

    private void pasteLayout() {
        try {
            String code = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            importLayout(code);
        } catch (IllegalStateException | UnsupportedFlavorException | IOException | IllegalArgumentException ex) {
            warning("Workspace.InvalidLayout");
        }
    }

    private void importLayout(String code) {
        if (code.isBlank() || code.length() > MAX_LAYOUT_TEXT_BYTES) {
            throw new IllegalArgumentException();
        }
        Reactor imported = reactor.copyDesign();
        imported.setCode(code.replaceAll("\\s+", ""));
        edit(() -> reactor = imported);
        syncProfileWithDesign();
        refreshDesign();
        if (!imported.getImportWarnings().isEmpty()) {
            JOptionPane.showMessageDialog(this, imported.getImportWarnings(), getI18n("Warning.Title"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void chooseCsv() {
        if (csvChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            csvFileLabel.setText(csvChooser.getSelectedFile().getPath());
            savePreferences();
        }
    }

    private void chooseTexture() {
        if (textureChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File path = textureChooser.getSelectedFile();
            prefs.setProperty("texturePack", path.getAbsolutePath());
            texturePackLabel.setText(formatI18n("UI.TexturePackSpecific", path.getPath()));
            savePreferences();
        }
    }

    private void clearTexture() {
        prefs.remove("texturePack");
        texturePackLabel.setText(getI18n("UI.TexturePackDefault"));
        savePreferences();
    }

    private void loadPreferences() {
        syncing = true;
        try {
            if (PREFS_FILE.isFile()) {
                try (var stream = Files.newInputStream(PREFS_FILE.toPath())) {
                    prefs.loadFromXML(stream);
                } catch (IOException ex) {
                    runState.setText(getI18n("Workspace.PrefsUnavailable"));
                }
            }
            gtVersionCombo.setSelectedItem(switch (prefs.getProperty("gtVersion", GTNH)) {
                case "none" -> "none";
                case "5.08" -> "5.08";
                case "5.09" -> "5.09";
                default -> GTNH;
            });
            mcVersionCombo.setSelectedItem("1.7.10".equals(prefs.getProperty("mcVersion")) ? "1.7.10" : "1.12.2");
            showDetails.setSelected(Boolean.parseBoolean(prefs.getProperty("showComponentDetailButtons", "true")));
            showPlacing.setSelected(Boolean.parseBoolean(prefs.getProperty("showComponentPreconfigControls", "false")));
            oldCode.setSelected(Boolean.parseBoolean(prefs.getProperty("showOldStyleReactorCode", "false")));
            expandAlloy.setSelected(Boolean.parseBoolean(prefs.getProperty("expandAdvancedAlloy", "false")));
            preconfiguration.setVisible(showPlacing.isSelected());
            if (!showDetails.isSelected()) {
                outputTabs.remove(inspector);
            }
            String csv = prefs.getProperty("csvFile");
            if (csv != null) {
                csvChooser.setSelectedFile(new File(csv));
                csvFileLabel.setText(csv);
            }
            String texture = prefs.getProperty("texturePack");
            if (texture != null) {
                texturePackLabel.setText(formatI18n("UI.TexturePackSpecific", texture));
            }
            layoutChooser.setFileFilter(new FileNameExtensionFilter(getI18n("Workspace.LayoutFiles"), "erp", "txt"));
        } finally {
            syncing = false;
        }
    }

    private void savePreferences() {
        prefs.setProperty("gtVersion", (String) gtVersionCombo.getSelectedItem());
        prefs.setProperty("mcVersion", (String) mcVersionCombo.getSelectedItem());
        prefs.setProperty("showComponentDetailButtons", Boolean.toString(showDetails.isSelected()));
        prefs.setProperty("showComponentPreconfigControls", Boolean.toString(showPlacing.isSelected()));
        prefs.setProperty("showOldStyleReactorCode", Boolean.toString(oldCode.isSelected()));
        prefs.setProperty("expandAdvancedAlloy", Boolean.toString(expandAlloy.isSelected()));
        if (csvChooser.getSelectedFile() != null) {
            prefs.setProperty("csvFile", csvChooser.getSelectedFile().getAbsolutePath());
        }
        try (var stream = Files.newOutputStream(PREFS_FILE.toPath())) {
            prefs.storeToXML(stream, null);
        } catch (IOException ex) {
            runState.setText(getI18n("Workspace.PrefsUnavailable"));
        }
    }

    private void copyText(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException ex) {
            warning("Workspace.ClipboardError");
        }
    }

    private void copyComparison() {
        try {
            HtmlSelection data = new HtmlSelection(comparisonLabel.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(data, data);
        } catch (IllegalStateException ex) {
            warning("Workspace.ClipboardError");
        }
    }

    private void warning(String key) {
        JOptionPane.showMessageDialog(this, getI18n(key), getI18n("Warning.Title"), JOptionPane.WARNING_MESSAGE);
    }

    private static Action command(String key, Runnable operation) {
        return new AbstractAction(getI18n(key)) {
            @Override
            public void actionPerformed(ActionEvent event) {
                operation.run();
            }
        };
    }

    private static JButton button(String key, Runnable operation) {
        return new JButton(command(key, operation));
    }

    private static JPanel row(Component... items) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 3));
        panel.setOpaque(false);
        for (Component item : items) {
            panel.add(item);
        }
        return panel;
    }

    private static JPanel labeled(String key, JComponent field) {
        JLabel label = new JLabel(getI18n(key));
        label.setLabelFor(field);
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setOpaque(false);
        panel.add(label, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel form() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    private static void addField(JPanel panel, int row, String key, JComponent field) {
        JLabel label = new JLabel(getI18n(key));
        label.setLabelFor(field);
        GridBagConstraints left = fieldConstraints(row);
        left.gridx = 0;
        panel.add(label, left);
        GridBagConstraints right = fieldConstraints(row);
        right.gridx = 1;
        right.weightx = 1;
        panel.add(field, right);
    }

    private static void addFull(JPanel panel, int row, JComponent component) {
        GridBagConstraints constraints = fieldConstraints(row);
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        panel.add(component, constraints);
    }

    private static GridBagConstraints fieldConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(3, 6, 3, 6);
        constraints.anchor = GridBagConstraints.LINE_START;
        return constraints;
    }

    private static JSpinner spinner(int value, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(7);
        return spinner;
    }

    private static int number(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static JTextField textField(int columns, boolean editable) {
        JTextField field = new JTextField(columns);
        field.setEditable(editable);
        return field;
    }

    private static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 35);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, UIManager.getFont("TextArea.font").getSize()));
        area.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
        return area;
    }

    private static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    public static void main(String[] args) {
        for (String arg : args) {
            if ("--lang=en".equals(arg)) {
                Locale.setDefault(Locale.ENGLISH);
            } else if ("--lang=zh-CN".equalsIgnoreCase(arg)) {
                Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
            }
        }
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionDialogDisplay());
        SwingUtilities.invokeLater(() -> {
            configureAppearance();
            new ReactorPlannerFrame().setVisible(true);
        });
    }

    static void configureAppearance() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ex) {
            // Swing's default look and feel remains active.
        }
        var defaults = UIManager.getLookAndFeelDefaults();
        for (Object key : defaults.keySet()) {
            if (key.toString().endsWith(".font") && defaults.get(key) instanceof Font font) {
                UIManager.put(key, new FontUIResource(Font.SANS_SERIF, font.getStyle(), Math.max(13, font.getSize())));
            }
        }
    }

    private String buildIntComparisonString(String comparisonType, int left, int right, int defaultValue) {
        if (right == defaultValue) {
            return String.format(getI18n("Comparison." + comparisonType + ".LeftOnly"), left);
        } else if (left == defaultValue) {
            return String.format(getI18n("Comparison." + comparisonType + ".RightOnly"), right);
        } else {
            return String.format(getI18n("Comparison." + comparisonType + ".Both"), left - right, left, right);
        }
    }
    
    /**
     * Builds an integer comparison string using the resource bundle, based on whether both left and right values are non-default, or just one.
     * @param comparison the comparison type, for looking up the appropriate keys in the resource bundle, between "Comparison." and ".BothColored", ".LeftOnly", or ".RightOnly"
     * @param left the left-side value of the comparison
     * @param right the right-side value of the comparison
     * @param defaultValue the default value for the relevant entry, and if either left or right is equal to it, that value can be omitted as not applicable.
     * @param threshold the minimum value to consider good or bad - if this is negative, then negative differences with greater magnitude will be shown in green (good), 
     * otherwise positive differences will be green; differences in the other direction will be shown in red (bad).  Differences closer to zero than this (in either direction) 
     * will be shown in orange.
     * If both are equal to this, the method should not even be called.
     * @return a string to show the comparison between the two values.
     */
    private String buildColoredIntComparisonString(String comparisonType, int left, int right, int defaultValue, int threshold) {
        if (right == defaultValue) {
            return String.format(getI18n("Comparison." + comparisonType + ".LeftOnly"), left);
        } else if (left == defaultValue) {
            return String.format(getI18n("Comparison." + comparisonType + ".RightOnly"), right);
        } else {
            String color = "orange";
            if (Math.abs(left - right) > Math.abs(threshold)) {
                if (Math.signum(left - right) == Math.signum(threshold)) {
                    color = "green";
                } else {
                    color = "red";
                }
            }
            return String.format(getI18n("Comparison." + comparisonType + ".BothColored"), color, left - right, left, right);
        }
    }
    
    private final DecimalFormat comparisonFormat = new DecimalFormat(getI18n("Comparison.CompareDecimalFormat"));
    private final DecimalFormat simpleFormat = new DecimalFormat(getI18n("Comparison.SimpleDecimalFormat"));
    
    private String colorDecimal(double value, double threshold) {
        String color = "orange";
        if (Math.abs(value) > Math.abs(threshold)) {
            if (Math.signum(value) == Math.signum(threshold)) {
                color = "green";
            } else {
                color = "red";
            }
        }
        return String.format("<font color=\"%s\">%s</font>", color, comparisonFormat.format(value));
    }
    
    private String simpleDecimal(double value) {
        return simpleFormat.format(value);
    }
    
    private void updateComparison() {
        if (simulator == null || simulator.getData() == null || prevSimulator == null || prevSimulator.getData() == null) {
            return;
        }
        if (oldCode.isSelected()) {
            comparisonCodeField.setText(prevReactorOldCode);
        } else {
            comparisonCodeField.setText(prevReactorCode);
        }
        comparisonScroll.getVerticalScrollBar().setUnitIncrement(16);
        StringBuilder text = new StringBuilder(1000);
        StringBuilder prebreakText = new StringBuilder(500);
        StringBuilder predepleteText = new StringBuilder(500);
        StringBuilder postsimText = new StringBuilder(500);
        boolean alwaysDiff = !onlyShowDiffCheck.isSelected();
        text.append("<html>");
        Reactor tempReactor = currentDesign;
        Reactor prevReactor = prevDesign;
        SimulationData leftData = simulator.getData();
        SimulationData rightData = prevSimulator.getData();
        if ((leftData.timeToBelow50 != Integer.MAX_VALUE || rightData.timeToBelow50 != Integer.MAX_VALUE) && (alwaysDiff || leftData.timeToBelow50 != rightData.timeToBelow50)) {
            text.append(getI18n("Comparison.Prefix.TimeToBelow50"));
            text.append(buildColoredIntComparisonString("Time", leftData.timeToBelow50, rightData.timeToBelow50, Integer.MAX_VALUE, 1));
        }
        if ((leftData.timeToBurn != Integer.MAX_VALUE || rightData.timeToBurn != Integer.MAX_VALUE) && (alwaysDiff || leftData.timeToBurn != rightData.timeToBurn)) {
            text.append(getI18n("Comparison.Prefix.TimeToBurn"));
            text.append(buildColoredIntComparisonString("Time", leftData.timeToBurn, rightData.timeToBurn, Integer.MAX_VALUE, 1));
        }
        if ((leftData.timeToEvaporate != Integer.MAX_VALUE || rightData.timeToEvaporate != Integer.MAX_VALUE) && (alwaysDiff || leftData.timeToEvaporate != rightData.timeToEvaporate)) {
            text.append(getI18n("Comparison.Prefix.TimeToEvaporate"));
            text.append(buildColoredIntComparisonString("Time", leftData.timeToEvaporate, rightData.timeToEvaporate, Integer.MAX_VALUE, 1));
        }
        if ((leftData.timeToHurt != Integer.MAX_VALUE || rightData.timeToHurt != Integer.MAX_VALUE) && (alwaysDiff || leftData.timeToHurt != rightData.timeToHurt)) {
            text.append(getI18n("Comparison.Prefix.TimeToHurt"));
            text.append(buildColoredIntComparisonString("Time", leftData.timeToHurt, rightData.timeToHurt, Integer.MAX_VALUE, 1));
        }
        if ((leftData.timeToLava != Integer.MAX_VALUE || rightData.timeToLava != Integer.MAX_VALUE) && (alwaysDiff || leftData.timeToLava != rightData.timeToLava)) {
            text.append(getI18n("Comparison.Prefix.TimeToLava"));
            text.append(buildColoredIntComparisonString("Time", leftData.timeToLava, rightData.timeToLava, Integer.MAX_VALUE, 1));
        }
        if ((leftData.timeToXplode != Integer.MAX_VALUE || rightData.timeToXplode != Integer.MAX_VALUE) && (alwaysDiff || leftData.timeToXplode != rightData.timeToXplode)) {
            text.append(getI18n("Comparison.Prefix.TimeToXplode"));
            text.append(buildColoredIntComparisonString("Time", leftData.timeToXplode, rightData.timeToXplode, Integer.MAX_VALUE, 1));
        }
        if (leftData.firstComponentBrokenTime != Integer.MAX_VALUE || rightData.firstComponentBrokenTime != Integer.MAX_VALUE) {
            if (alwaysDiff || Math.abs(leftData.firstComponentBrokenTime - rightData.firstComponentBrokenTime) > 10) {
                text.append(buildIntComparisonString("Time", leftData.firstComponentBrokenTime, rightData.firstComponentBrokenTime, Integer.MAX_VALUE));
            }

            if (leftData.firstComponentBrokenTime != Integer.MAX_VALUE && rightData.firstComponentBrokenTime != Integer.MAX_VALUE) {
                prebreakText.append(getI18n("Comparison.Prefix.Prebreak"));
                if (!tempReactor.isFluid()) {
                    if (!prevReactor.isFluid()) {
                        if (leftData.prebreakMinEUoutput <= leftData.prebreakMaxEUoutput && rightData.prebreakMinEUoutput <= rightData.prebreakMaxEUoutput) {
                            if (alwaysDiff || Math.abs(leftData.prebreakTotalEUoutput - rightData.prebreakTotalEUoutput) > 1000
                                    || Math.abs(leftData.prebreakAvgEUoutput - rightData.prebreakAvgEUoutput) > 0.1
                                    || Math.abs(leftData.prebreakMinEUoutput - rightData.prebreakMinEUoutput) > 0.1
                                    || Math.abs(leftData.prebreakMaxEUoutput - rightData.prebreakMaxEUoutput) > 0.1) {
                                prebreakText.append(formatI18n("Comparison.EUEUoutput",
                                        colorDecimal(leftData.prebreakTotalEUoutput - rightData.prebreakTotalEUoutput, 1000),
                                        simpleDecimal(leftData.prebreakTotalEUoutput),
                                        simpleDecimal(rightData.prebreakTotalEUoutput),
                                        colorDecimal(leftData.prebreakAvgEUoutput - rightData.prebreakAvgEUoutput, 0.1),
                                        simpleDecimal(leftData.prebreakAvgEUoutput),
                                        simpleDecimal(rightData.prebreakAvgEUoutput),
                                        colorDecimal(leftData.prebreakMinEUoutput - rightData.prebreakMinEUoutput, 0.1),
                                        simpleDecimal(leftData.prebreakMinEUoutput),
                                        simpleDecimal(rightData.prebreakMinEUoutput),
                                        colorDecimal(leftData.prebreakMaxEUoutput - rightData.prebreakMaxEUoutput, 0.1),
                                        simpleDecimal(leftData.prebreakMaxEUoutput),
                                        simpleDecimal(rightData.prebreakMaxEUoutput)));
                            }
                        }
                    } else {
                        if (leftData.prebreakMinEUoutput <= leftData.prebreakMaxEUoutput && rightData.prebreakMinHUoutput <= rightData.prebreakMaxHUoutput) {
                            prebreakText.append(formatI18n("Comparison.EUHUoutput",
                                    simpleDecimal(leftData.prebreakTotalEUoutput),
                                    simpleDecimal(rightData.prebreakTotalHUoutput),
                                    simpleDecimal(leftData.prebreakAvgEUoutput),
                                    simpleDecimal(rightData.prebreakAvgHUoutput),
                                    simpleDecimal(leftData.prebreakMinEUoutput),
                                    simpleDecimal(rightData.prebreakMinHUoutput),
                                    simpleDecimal(leftData.prebreakMaxEUoutput),
                                    simpleDecimal(rightData.prebreakMaxHUoutput)));
                        }
                    }
                } else {
                    if (!prevReactor.isFluid()) {
                        if (leftData.prebreakMinHUoutput <= leftData.prebreakMaxHUoutput && rightData.prebreakMinEUoutput <= rightData.prebreakMaxEUoutput) {
                            prebreakText.append(formatI18n("Comparison.HUEUoutput",
                                    simpleDecimal(leftData.prebreakTotalHUoutput),
                                    simpleDecimal(rightData.prebreakTotalEUoutput),
                                    simpleDecimal(leftData.prebreakAvgHUoutput),
                                    simpleDecimal(rightData.prebreakAvgEUoutput),
                                    simpleDecimal(leftData.prebreakMinHUoutput),
                                    simpleDecimal(rightData.prebreakMinEUoutput),
                                    simpleDecimal(leftData.prebreakMaxHUoutput),
                                    simpleDecimal(rightData.prebreakMaxEUoutput)));
                        }
                    } else {
                        if (leftData.prebreakMinHUoutput <= leftData.prebreakMaxHUoutput && rightData.prebreakMinHUoutput <= rightData.prebreakMaxHUoutput) {
                            if (alwaysDiff || Math.abs(leftData.prebreakTotalHUoutput - rightData.prebreakTotalHUoutput) > 1000
                                    || Math.abs(leftData.prebreakAvgHUoutput - rightData.prebreakAvgHUoutput) > 0.1
                                    || Math.abs(leftData.prebreakMinHUoutput - rightData.prebreakMinHUoutput) > 0.1
                                    || Math.abs(leftData.prebreakMaxHUoutput - rightData.prebreakMaxHUoutput) > 0.1) {
                                prebreakText.append(formatI18n("Comparison.HUHUoutput",
                                        colorDecimal(leftData.prebreakTotalHUoutput - rightData.prebreakTotalHUoutput, 1000),
                                        simpleDecimal(leftData.prebreakTotalHUoutput),
                                        simpleDecimal(rightData.prebreakTotalHUoutput),
                                        colorDecimal(leftData.prebreakAvgHUoutput - rightData.prebreakAvgHUoutput, 0.1),
                                        simpleDecimal(leftData.prebreakAvgHUoutput),
                                        simpleDecimal(rightData.prebreakAvgHUoutput),
                                        colorDecimal(leftData.prebreakMinHUoutput - rightData.prebreakMinHUoutput, 0.1),
                                        simpleDecimal(leftData.prebreakMinHUoutput),
                                        simpleDecimal(rightData.prebreakMinHUoutput),
                                        colorDecimal(leftData.prebreakMaxHUoutput - rightData.prebreakMaxHUoutput, 0.1),
                                        simpleDecimal(leftData.prebreakMaxHUoutput),
                                        simpleDecimal(rightData.prebreakMaxHUoutput)));
                            }
                        }
                    }
                }
            }
        }
        if (prebreakText.length() > 0) {
            text.append(getI18n("Comparison.Prefix.PrebreakTime"));
            text.append(prebreakText);
            text.append("<br>");
        }
        boolean moxStyleReactor = false;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                ReactorItem component = tempReactor.getComponentAt(row, col);
                if (component != null) {
                    String baseName = component.baseName;
                    if ("fuelRodMox".equals(baseName) || "dualFuelRodMox".equals(baseName) || "quadFuelRodMox".equals(baseName)
                            || "fuelRodNaquadah".equals(baseName) || "dualFuelRodNaquadah".equals(baseName) || "quadFuelRodNaquadah".equals(baseName)) {
                        moxStyleReactor = true;
                    }
                }
            }
        }
        if (leftData.firstRodDepletedTime != Integer.MAX_VALUE || rightData.firstRodDepletedTime != Integer.MAX_VALUE) {
            if (alwaysDiff || Math.abs(leftData.firstRodDepletedTime - rightData.firstRodDepletedTime) > 10) {
                text.append(getI18n("Comparison.Prefix.PredepleteTime"));
                text.append(buildIntComparisonString("Time", leftData.firstRodDepletedTime, rightData.firstRodDepletedTime, Integer.MAX_VALUE));
            }

            if (leftData.totalRodCount > 0 && rightData.totalRodCount > 0) {
                if (!tempReactor.isFluid()) {
                    if (!prevReactor.isFluid()) {
                        if (leftData.predepleteMinEUoutput <= leftData.predepleteMaxEUoutput && rightData.predepleteMinEUoutput <= rightData.predepleteMaxEUoutput) {
                            if (alwaysDiff || Math.abs(leftData.predepleteTotalEUoutput - rightData.predepleteTotalEUoutput) > 1000
                                    || Math.abs(leftData.predepleteAvgEUoutput - rightData.predepleteAvgEUoutput) > 0.1
                                    || Math.abs(leftData.predepleteMinEUoutput - rightData.predepleteMinEUoutput) > 0.1
                                    || Math.abs(leftData.predepleteMaxEUoutput - rightData.predepleteMaxEUoutput) > 0.1) {
                                predepleteText.append(formatI18n("Comparison.EUEUoutput",
                                        colorDecimal(leftData.predepleteTotalEUoutput - rightData.predepleteTotalEUoutput, 1000),
                                        simpleDecimal(leftData.predepleteTotalEUoutput),
                                        simpleDecimal(rightData.predepleteTotalEUoutput),
                                        colorDecimal(leftData.predepleteAvgEUoutput - rightData.predepleteAvgEUoutput, 0.1),
                                        simpleDecimal(leftData.predepleteAvgEUoutput),
                                        simpleDecimal(rightData.predepleteAvgEUoutput),
                                        colorDecimal(leftData.predepleteMinEUoutput - rightData.predepleteMinEUoutput, 0.1),
                                        simpleDecimal(leftData.predepleteMinEUoutput),
                                        simpleDecimal(rightData.predepleteMinEUoutput),
                                        colorDecimal(leftData.predepleteMaxEUoutput - rightData.predepleteMaxEUoutput, 0.1),
                                        simpleDecimal(leftData.predepleteMaxEUoutput),
                                        simpleDecimal(rightData.predepleteMaxEUoutput)));
                            }
                        }
                    } else {
                        if (leftData.predepleteMinEUoutput <= leftData.predepleteMaxEUoutput && rightData.predepleteMinHUoutput <= rightData.predepleteMaxHUoutput) {
                            predepleteText.append(formatI18n("Comparison.EUHUoutput",
                                    simpleDecimal(leftData.predepleteTotalEUoutput),
                                    simpleDecimal(rightData.predepleteTotalHUoutput),
                                    simpleDecimal(leftData.predepleteAvgEUoutput),
                                    simpleDecimal(rightData.predepleteAvgHUoutput),
                                    simpleDecimal(leftData.predepleteMinEUoutput),
                                    simpleDecimal(rightData.predepleteMinHUoutput),
                                    simpleDecimal(leftData.predepleteMaxEUoutput),
                                    simpleDecimal(rightData.predepleteMaxHUoutput)));
                        }
                    }
                } else {
                    if (!prevReactor.isFluid()) {
                        if (leftData.predepleteMinHUoutput <= leftData.predepleteMaxHUoutput && rightData.predepleteMinEUoutput <= rightData.predepleteMaxEUoutput) {
                            predepleteText.append(formatI18n("Comparison.HUEUoutput",
                                    simpleDecimal(leftData.predepleteTotalHUoutput),
                                    simpleDecimal(rightData.predepleteTotalEUoutput),
                                    simpleDecimal(leftData.predepleteAvgHUoutput),
                                    simpleDecimal(rightData.predepleteAvgEUoutput),
                                    simpleDecimal(leftData.predepleteMinHUoutput),
                                    simpleDecimal(rightData.predepleteMinEUoutput),
                                    simpleDecimal(leftData.predepleteMaxHUoutput),
                                    simpleDecimal(rightData.predepleteMaxEUoutput)));
                        }
                    } else {
                        if (leftData.predepleteMinHUoutput <= leftData.predepleteMaxHUoutput && rightData.predepleteMinHUoutput <= rightData.predepleteMaxHUoutput) {
                        if (alwaysDiff || Math.abs(leftData.predepleteTotalHUoutput - rightData.predepleteTotalHUoutput) > 1000
                                || Math.abs(leftData.predepleteAvgHUoutput - rightData.predepleteAvgHUoutput) > 0.1
                                || Math.abs(leftData.predepleteMinHUoutput - rightData.predepleteMinHUoutput) > 0.1
                                || Math.abs(leftData.predepleteMaxHUoutput - rightData.predepleteMaxHUoutput) > 0.1) {
                            predepleteText.append(formatI18n("Comparison.HUHUoutput",
                                    colorDecimal(leftData.predepleteTotalHUoutput - rightData.predepleteTotalHUoutput, 1000),
                                    simpleDecimal(leftData.predepleteTotalHUoutput),
                                    simpleDecimal(rightData.predepleteTotalHUoutput),
                                    colorDecimal(leftData.predepleteAvgHUoutput - rightData.predepleteAvgHUoutput, 0.1),
                                    simpleDecimal(leftData.predepleteAvgHUoutput),
                                    simpleDecimal(rightData.predepleteAvgHUoutput),
                                    colorDecimal(leftData.predepleteMinHUoutput - rightData.predepleteMinHUoutput, 0.1),
                                    simpleDecimal(leftData.predepleteMinHUoutput),
                                    simpleDecimal(rightData.predepleteMinHUoutput),
                                    colorDecimal(leftData.predepleteMaxHUoutput - rightData.predepleteMaxHUoutput, 0.1),
                                    simpleDecimal(leftData.predepleteMaxHUoutput),
                                    simpleDecimal(rightData.predepleteMaxHUoutput)));
                        }
                        }
                    }
                }
                if (leftData.predepleteMinTemp <= leftData.predepleteMaxTemp && rightData.predepleteMinTemp <= rightData.predepleteMaxTemp) {
                    if (alwaysDiff || Math.abs(leftData.predepleteMinTemp - rightData.predepleteMinTemp) > 10) {
                        predepleteText.append(formatI18n("Comparison.PredepleteMinTemp",
                                colorDecimal(leftData.predepleteMinTemp - rightData.predepleteMinTemp, moxStyleReactor ? 10 : -10),
                                simpleDecimal(leftData.predepleteMinTemp),
                                simpleDecimal(rightData.predepleteMinTemp)));
                    }
                    if (alwaysDiff || Math.abs(leftData.predepleteMaxTemp - rightData.predepleteMaxTemp) > 10) {
                        String leftMaxTempDecimal = simpleDecimal(leftData.predepleteMaxTemp);
                        String rightMaxTempDecimal = simpleDecimal(rightData.predepleteMaxTemp);
                        if ((leftData.predepleteMaxTemp >= tempReactor.getMaxHeat() * 0.85) != (rightData.predepleteMaxTemp >= prevReactor.getMaxHeat() * 0.85)) {
                            if (leftData.predepleteMaxTemp >= tempReactor.getMaxHeat() * 0.85) {
                                leftMaxTempDecimal = "<font color=\"red\">" + leftMaxTempDecimal + "</font>";
                                rightMaxTempDecimal = "<font color=\"green\">" + rightMaxTempDecimal + "</font>";
                            } else {
                                leftMaxTempDecimal = "<font color=\"green\">" + leftMaxTempDecimal + "</font>";
                                rightMaxTempDecimal = "<font color=\"red\">" + rightMaxTempDecimal + "</font>";
                            }
                        }
                        predepleteText.append(formatI18n("Comparison.PredepleteMaxTemp",
                                colorDecimal(leftData.predepleteMaxTemp - rightData.predepleteMaxTemp, moxStyleReactor ? 10 : -10), leftMaxTempDecimal, rightMaxTempDecimal));
                    }
                }
            }
        }
        if (predepleteText.length() > 0) {
            text.append(getI18n("Comparison.Prefix.Predeplete"));
            text.append(predepleteText);
            text.append("<br>");
        }

        if (alwaysDiff || Math.abs(leftData.totalReactorTicks - rightData.totalReactorTicks) > 10) {
            text.append(getI18n("Comparison.Prefix.PostSimulationTime"));
            text.append(buildIntComparisonString("Time", leftData.totalReactorTicks, rightData.totalReactorTicks, Integer.MAX_VALUE));
        }
        if (!tempReactor.isFluid()) {
            if (!prevReactor.isFluid()) {
                if (leftData.minEUoutput <= leftData.maxEUoutput && rightData.minEUoutput <= rightData.maxEUoutput) {
                    if (alwaysDiff || Math.abs(leftData.totalEUoutput - rightData.totalEUoutput) > 1000
                            || Math.abs(leftData.avgEUoutput - rightData.avgEUoutput) > 0.1
                            || Math.abs(leftData.minEUoutput - rightData.minEUoutput) > 0.1
                            || Math.abs(leftData.maxEUoutput - rightData.maxEUoutput) > 0.1) {
                        postsimText.append(formatI18n("Comparison.EUEUoutput",
                                colorDecimal(leftData.totalEUoutput - rightData.totalEUoutput, 1000),
                                simpleDecimal(leftData.totalEUoutput),
                                simpleDecimal(rightData.totalEUoutput),
                                colorDecimal(leftData.avgEUoutput - rightData.avgEUoutput, 0.1),
                                simpleDecimal(leftData.avgEUoutput),
                                simpleDecimal(rightData.avgEUoutput),
                                colorDecimal(leftData.minEUoutput - rightData.minEUoutput, 0.1),
                                simpleDecimal(leftData.minEUoutput),
                                simpleDecimal(rightData.minEUoutput),
                                colorDecimal(leftData.maxEUoutput - rightData.maxEUoutput, 0.1),
                                simpleDecimal(leftData.maxEUoutput),
                                simpleDecimal(rightData.maxEUoutput)));
                    }
                }
            } else {
                if (leftData.minEUoutput <= leftData.maxEUoutput && rightData.minHUoutput <= rightData.maxHUoutput) {
                    postsimText.append(formatI18n("Comparison.EUHUoutput",
                            simpleDecimal(leftData.totalEUoutput),
                            simpleDecimal(rightData.totalHUoutput),
                            simpleDecimal(leftData.avgEUoutput),
                            simpleDecimal(rightData.avgHUoutput),
                            simpleDecimal(leftData.minEUoutput),
                            simpleDecimal(rightData.minHUoutput),
                            simpleDecimal(leftData.maxEUoutput),
                            simpleDecimal(rightData.maxHUoutput)));
                }
            }
        } else {
            if (!prevReactor.isFluid()) {
                if (leftData.minHUoutput <= leftData.maxHUoutput && rightData.minEUoutput <= rightData.maxEUoutput) {
                    postsimText.append(formatI18n("Comparison.HUEUoutput",
                            simpleDecimal(leftData.totalHUoutput),
                            simpleDecimal(rightData.totalEUoutput),
                            simpleDecimal(leftData.avgHUoutput),
                            simpleDecimal(rightData.avgEUoutput),
                            simpleDecimal(leftData.minHUoutput),
                            simpleDecimal(rightData.minEUoutput),
                            simpleDecimal(leftData.maxHUoutput),
                            simpleDecimal(rightData.maxEUoutput)));
                }
            } else {
                if (leftData.minHUoutput <= leftData.maxHUoutput && rightData.minHUoutput <= rightData.maxHUoutput) {
                    if (alwaysDiff || Math.abs(leftData.totalHUoutput - rightData.totalHUoutput) > 1000
                            || Math.abs(leftData.avgHUoutput - rightData.avgHUoutput) > 0.1
                            || Math.abs(leftData.minHUoutput - rightData.minHUoutput) > 0.1
                            || Math.abs(leftData.maxHUoutput - rightData.maxHUoutput) > 0.1) {
                        postsimText.append(formatI18n("Comparison.HUHUoutput",
                                colorDecimal(leftData.totalHUoutput - rightData.totalHUoutput, 1000),
                                simpleDecimal(leftData.totalHUoutput),
                                simpleDecimal(rightData.totalHUoutput),
                                colorDecimal(leftData.avgHUoutput - rightData.avgHUoutput, 0.1),
                                simpleDecimal(leftData.avgHUoutput),
                                simpleDecimal(rightData.avgHUoutput),
                                colorDecimal(leftData.minHUoutput - rightData.minHUoutput, 0.1),
                                simpleDecimal(leftData.minHUoutput),
                                simpleDecimal(rightData.minHUoutput),
                                colorDecimal(leftData.maxHUoutput - rightData.maxHUoutput, 0.1),
                                simpleDecimal(leftData.maxHUoutput),
                                simpleDecimal(rightData.maxHUoutput)));
                    }
                }
            }
        }
        if (leftData.minTemp <= leftData.maxTemp && rightData.minTemp <= rightData.maxTemp) {
            if (alwaysDiff || Math.abs(leftData.minTemp - rightData.minTemp) > 10) {
                postsimText.append(formatI18n("Comparison.PostsimMinTemp",
                        colorDecimal(leftData.minTemp - rightData.minTemp, moxStyleReactor ? 10 : -10),
                        simpleDecimal(leftData.minTemp),
                        simpleDecimal(rightData.minTemp)));
            }
            if (alwaysDiff || Math.abs(leftData.maxTemp - rightData.maxTemp) > 10) {
                String leftMaxTempDecimal = simpleDecimal(leftData.maxTemp);
                String rightMaxTempDecimal = simpleDecimal(rightData.maxTemp);
                if ((leftData.maxTemp >= tempReactor.getMaxHeat() * 0.85) != (rightData.maxTemp >= prevReactor.getMaxHeat() * 0.85)) {
                    if (leftData.maxTemp >= tempReactor.getMaxHeat() * 0.85) {
                        leftMaxTempDecimal = "<font color=\"red\">" + leftMaxTempDecimal + "</font>";
                        rightMaxTempDecimal = "<font color=\"green\">" + rightMaxTempDecimal + "</font>";
                    } else {
                        leftMaxTempDecimal = "<font color=\"green\">" + leftMaxTempDecimal + "</font>";
                        rightMaxTempDecimal = "<font color=\"red\">" + rightMaxTempDecimal + "</font>";
                    }
                }
                postsimText.append(formatI18n("Comparison.PostsimMaxTemp",
                        colorDecimal(leftData.maxTemp - rightData.maxTemp, moxStyleReactor ? 10 : -10), leftMaxTempDecimal, rightMaxTempDecimal));
            }
        }
        if (postsimText.length() > 0) {
            text.append(getI18n("Comparison.Prefix.PostSimulation"));
            text.append(postsimText);
            text.append("<br>");
        }
        
        if ((leftData.hullHeating != 0 || rightData.hullHeating != 0) && (alwaysDiff || Math.abs(leftData.hullHeating - rightData.hullHeating) > 1)) {
            text.append(formatI18n("Comparison.HullHeating",
                    colorDecimal(leftData.hullHeating - rightData.hullHeating, -1),
                    simpleDecimal(leftData.hullHeating),
                    simpleDecimal(rightData.hullHeating)));
        }
        if ((leftData.componentHeating != 0 || rightData.componentHeating != 0) && (alwaysDiff || Math.abs(leftData.componentHeating - rightData.componentHeating) > 1)) {
            text.append(formatI18n("Comparison.ComponentHeating",
                    colorDecimal(leftData.componentHeating - rightData.componentHeating, -1),
                    simpleDecimal(leftData.componentHeating),
                    simpleDecimal(rightData.componentHeating)));
        }
        if (leftData.hullCooling != 0 || rightData.hullCooling != 0) {
            if (alwaysDiff || Math.abs(leftData.hullCooling - rightData.hullCooling) > 1) {
                text.append(formatI18n("Comparison.HullCooling",
                        colorDecimal(leftData.hullCooling - rightData.hullCooling, 1),
                        simpleDecimal(leftData.hullCooling),
                        simpleDecimal(rightData.hullCooling)));
            }
            if (alwaysDiff || Math.abs(leftData.hullCoolingCapacity - rightData.hullCoolingCapacity) > 1) {
                text.append(formatI18n("Comparison.HullCoolingPossible",
                        colorDecimal(leftData.hullCoolingCapacity - rightData.hullCoolingCapacity, 1),
                        simpleDecimal(leftData.hullCoolingCapacity),
                        simpleDecimal(rightData.hullCoolingCapacity)));
            }
        }
        if (leftData.ventCooling != 0 || rightData.ventCooling != 0) {
            if (alwaysDiff || Math.abs(leftData.ventCooling - rightData.ventCooling) > 1) {
                text.append(formatI18n("Comparison.VentCooling",
                        colorDecimal(leftData.ventCooling - rightData.ventCooling, 1),
                        simpleDecimal(leftData.ventCooling),
                        simpleDecimal(rightData.ventCooling)));
            }
            if (alwaysDiff || Math.abs(leftData.ventCoolingCapacity - rightData.ventCoolingCapacity) > 1) {
                text.append(formatI18n("Comparison.VentCoolingPossible",
                        colorDecimal(leftData.ventCoolingCapacity - rightData.ventCoolingCapacity, 1),
                        simpleDecimal(leftData.ventCoolingCapacity),
                        simpleDecimal(rightData.ventCoolingCapacity)));
            }
        }
        text.append("<br>");
        
        final String materialDiff = tempReactor.getMaterials().buildComparisonString(prevReactor.getMaterials(), alwaysDiff);
        if (!materialDiff.isEmpty()) {
            text.append(getI18n("Comparison.MaterialsHeading"));
            text.append(materialDiff);
            text.append("<br>");
        }
        
        final String componentDiff = tempReactor.getComponentList().buildComparisonString(prevReactor.getComponentList(), alwaysDiff);
        if (!componentDiff.isEmpty()) {
            text.append(getI18n("Comparison.ComponentsHeading"));
            text.append(componentDiff);
            text.append("<br>");
        }
        
        final String replacedDiff = leftData.replacedItems.buildComparisonString(rightData.replacedItems, alwaysDiff);
        if (!replacedDiff.isEmpty()) {
            text.append(getI18n("Comparison.ComponentsReplacedHeading"));
            text.append(replacedDiff);
        }
        
        if (text.toString().replace("<html>", "").replace("<br>", "").isEmpty()) {
            text.append(getI18n("Comparison.NoDifferences"));
        }
        
        text.append("</html>");
        comparisonLabel.setText(text.toString());
        copyComparison.setEnabled(true);
    }

    /**
     * @param compType Component type to read from the bundle object
     * @return Assembled tooltip string
     */
}
