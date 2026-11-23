package Ic2ExpReactorPlanner;

import static Ic2ExpReactorPlanner.BundleHelper.formatI18n;
import static Ic2ExpReactorPlanner.BundleHelper.getI18n;
import Ic2ExpReactorPlanner.components.ReactorItem;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/** Coordinates grid selection, keyboard navigation and independent edit transactions. */
final class ReactorGridEditor extends JPanel {
    static final int ROWS = 6;
    static final int COLUMNS = 9;
    private static final Color GRID_BACKGROUND = new Color(0xe8edf3);
    private static final Color SELECTED = new Color(0x1f568b);
    private static final Color INACTIVE = new Color(0xf1f3f5);
    private static final Color FAILURE = new Color(0xfdddda);
    private static final Color RETAINED_HEAT = new Color(0xffedcc);
    private static final DataFlavor DRAG_FLAVOR = dragFlavor();
    private static final String GRID_PREFIX = "erp-grid/1";

    private final Cell[][] cells = new Cell[ROWS][COLUMNS];
    private final JLabel[] columnHeaders = new JLabel[COLUMNS];
    private final Supplier<Reactor> reactor;
    private final IntSupplier activeColumns;
    private final Consumer<Runnable> edit;
    private final Supplier<ReactorItem> brush;
    private final BooleanSupplier hasBrush;
    private final Consumer<ReactorItem> pick;
    private final Runnable selectionChanged;
    private int row;
    private int column;
    private int anchorRow;
    private int anchorColumn;
    private JPanel[][] resultHighlights;

    ReactorGridEditor(Supplier<Reactor> reactor, IntSupplier activeColumns, Consumer<Runnable> edit,
            Supplier<ReactorItem> brush, BooleanSupplier hasBrush, Consumer<ReactorItem> pick,
            Runnable selectionChanged) {
        super(new GridBagLayout());
        this.reactor = reactor;
        this.activeColumns = activeColumns;
        this.edit = edit;
        this.brush = brush;
        this.hasBrush = hasBrush;
        this.pick = pick;
        this.selectionChanged = selectionChanged;
        setOpaque(false);
        for (int col = 0; col < COLUMNS; col++) {
            columnHeaders[col] = new JLabel(Integer.toString(col + 1), JLabel.CENTER);
            add(columnHeaders[col], constraints(col + 1, 0, false, false));
        }
        for (int r = 0; r < ROWS; r++) {
            add(new JLabel(Character.toString((char) ('A' + r)), JLabel.CENTER), constraints(0, r + 1, false, false));
            for (int c = 0; c < COLUMNS; c++) {
                cells[r][c] = new Cell(r, c);
                add(cells[r][c], constraints(c + 1, r + 1, true, true));
            }
        }
    }

    private static GridBagConstraints constraints(int x, int y, boolean weightX, boolean weightY) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = weightX ? 1 : 0;
        constraints.weighty = weightY ? 1 : 0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(2, 2, 2, 2);
        return constraints;
    }

    int selectedRow() {
        return row;
    }

    int selectedColumn() {
        return column;
    }

    void setResultHighlights(JPanel[][] highlights) {
        resultHighlights = highlights;
    }

    void refresh() {
        int columns = activeColumns.getAsInt();
        row = Math.min(row, ROWS - 1);
        column = Math.min(column, columns - 1);
        anchorColumn = Math.min(anchorColumn, columns - 1);
        for (int c = 0; c < COLUMNS; c++) {
            columnHeaders[c].setEnabled(c < columns);
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                cells[r][c].refresh(c < columns, reactor.get().getComponentAt(r, c));
            }
        }
    }

    private void select(int r, int c, boolean extend) {
        if (r < 0 || r >= ROWS || c < 0 || c >= activeColumns.getAsInt()) {
            return;
        }
        row = r;
        column = c;
        if (!extend) {
            anchorRow = r;
            anchorColumn = c;
        }
        cells[r][c].requestFocusInWindow();
        refresh();
        selectionChanged.run();
    }

    private boolean selected(int r, int c) {
        return r >= Math.min(row, anchorRow) && r <= Math.max(row, anchorRow)
                && c >= Math.min(column, anchorColumn) && c <= Math.max(column, anchorColumn);
    }

    void fill() {
        if (hasBrush.getAsBoolean()) {
            fillSelection(brush);
        }
    }

    void erase() {
        fillSelection(() -> null);
    }

    private void fillSelection(Supplier<ReactorItem> item) {
        edit.accept(() -> {
            for (int r = Math.min(row, anchorRow); r <= Math.max(row, anchorRow); r++) {
                for (int c = Math.min(column, anchorColumn); c <= Math.max(column, anchorColumn); c++) {
                    reactor.get().setComponentAt(r, c, item.get());
                }
            }
        });
    }

    private static String token(ReactorItem item) {
        if (item == null) {
            return "0";
        }
        return item.id + ":" + (int) item.getInitialHeat() + ":" + item.getAutomationThreshold() + ":" + item.getReactorPause();
    }

    private static ReactorItem decode(String token) {
        if ("0".equals(token)) {
            return null;
        }
        String[] parts = token.split(":", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException();
        }
        ReactorItem item = ComponentFactory.createComponent(Integer.parseInt(parts[0]));
        if (item == null) {
            throw new IllegalArgumentException();
        }
        int heat = Integer.parseInt(parts[1]);
        int threshold = Integer.parseInt(parts[2]);
        int pause = Integer.parseInt(parts[3]);
        if (heat < 0 || heat > Reactor.MAX_COMPONENT_HEAT || threshold < 0 || threshold > Reactor.MAX_COMPONENT_HEAT
                || pause < 0 || pause > 10_000) {
            throw new IllegalArgumentException();
        }
        item.setInitialHeat(heat);
        item.setAutomationThreshold(threshold);
        item.setReactorPause(pause);
        return item;
    }

    void copy() {
        StringBuilder text = new StringBuilder(GRID_PREFIX);
        int left = Math.min(column, anchorColumn);
        for (int r = Math.min(row, anchorRow); r <= Math.max(row, anchorRow); r++) {
            text.append('\n');
            for (int c = left; c <= Math.max(column, anchorColumn); c++) {
                if (c > left) {
                    text.append(',');
                }
                text.append(token(reactor.get().getComponentAt(r, c)));
            }
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
        } catch (IllegalStateException ex) {
            clipboardError();
        }
    }

    void paste() {
        try {
            String text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (text.length() > 4096) {
                throw new IllegalArgumentException();
            }
            String[] lines = text.strip().split("\\R");
            if (lines.length < 2 || !GRID_PREFIX.equals(lines[0]) || lines.length - 1 > ROWS - row) {
                throw new IllegalArgumentException();
            }
            int width = lines[1].split(",", -1).length;
            if (width > activeColumns.getAsInt() - column) {
                throw new IllegalArgumentException();
            }
            ReactorItem[][] block = new ReactorItem[lines.length - 1][width];
            for (int r = 0; r < block.length; r++) {
                String[] parts = lines[r + 1].split(",", -1);
                if (parts.length != width) {
                    throw new IllegalArgumentException();
                }
                for (int c = 0; c < width; c++) {
                    block[r][c] = decode(parts[c]);
                }
            }
            edit.accept(() -> {
                for (int r = 0; r < block.length; r++) {
                    for (int c = 0; c < width; c++) {
                        reactor.get().setComponentAt(row + r, column + c, block[r][c]);
                    }
                }
            });
        } catch (IllegalStateException | IllegalArgumentException | UnsupportedFlavorException | IOException ex) {
            clipboardError();
        }
    }

    private void clipboardError() {
        JOptionPane.showMessageDialog(this, getI18n("Workspace.ClipboardError"), getI18n("Warning.Title"),
                JOptionPane.WARNING_MESSAGE);
    }

    static Transferable paletteDrag(ReactorItem item) {
        return new Drag(-1, -1, token(item));
    }

    private static DataFlavor dragFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Drag.class.getName(),
                    "Reactor grid item", Drag.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private record Drag(int row, int column, String token) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DRAG_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DRAG_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return this;
        }
    }

    private final class Cell extends JButton {
        private final int cellRow;
        private final int cellColumn;
        private ReactorItem item;
        private boolean canEdit;

        Cell(int cellRow, int cellColumn) {
            this.cellRow = cellRow;
            this.cellColumn = cellColumn;
            setOpaque(true);
            setContentAreaFilled(false);
            setMargin(new Insets(0, 0, 0, 0));
            setPreferredSize(new Dimension(52, 44));
            setMinimumSize(new Dimension(16, 16));
            setTransferHandler(new TransferHandler() {
                @Override
                public int getSourceActions(JComponent component) {
                    return COPY_OR_MOVE;
                }

                @Override
                protected Transferable createTransferable(JComponent component) {
                    return item == null ? null : new Drag(cellRow, cellColumn, token(item));
                }

                @Override
                public boolean canImport(TransferSupport support) {
                    return canEdit && support.isDrop() && support.isDataFlavorSupported(DRAG_FLAVOR);
                }

                @Override
                public boolean importData(TransferSupport support) {
                    if (!canImport(support)) {
                        return false;
                    }
                    try {
                        Drag drag = (Drag) support.getTransferable().getTransferData(DRAG_FLAVOR);
                        if (drag.row() == cellRow && drag.column() == cellColumn) {
                            return false;
                        }
                        ReactorItem pasted = decode(drag.token());
                        select(cellRow, cellColumn, false);
                        edit.accept(() -> {
                            if (drag.row() >= 0 && support.getDropAction() == MOVE) {
                                reactor.get().setComponentAt(drag.row(), drag.column(), null);
                            }
                            reactor.get().setComponentAt(cellRow, cellColumn, pasted);
                        });
                        return true;
                    } catch (UnsupportedFlavorException | IOException ex) {
                        return false;
                    }
                }
            });
            addActionListener(event -> {
                int modifiers = event.getModifiers();
                select(cellRow, cellColumn, (modifiers & ActionEvent.SHIFT_MASK) != 0);
                if ((modifiers & ActionEvent.ALT_MASK) != 0) {
                    if (item != null) {
                        pick.accept(item);
                    }
                } else if ((modifiers & (ActionEvent.CTRL_MASK | ActionEvent.META_MASK | ActionEvent.SHIFT_MASK)) == 0) {
                    fill();
                }
            });
            MouseAdapter mouse = new MouseAdapter() {
                private MouseEvent press;

                @Override
                public void mousePressed(MouseEvent event) {
                    press = SwingUtilities.isLeftMouseButton(event) ? event : null;
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (press != null && press.getPoint().distance(event.getPoint()) >= 6 && item != null) {
                        getModel().setArmed(false);
                        getModel().setPressed(false);
                        getTransferHandler().exportAsDrag(Cell.this, event,
                                event.isControlDown() ? TransferHandler.COPY : TransferHandler.MOVE);
                        press = null;
                    }
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    if (!canEdit) {
                        return;
                    }
                    if (SwingUtilities.isRightMouseButton(event)) {
                        select(cellRow, cellColumn, false);
                        erase();
                    } else if (SwingUtilities.isMiddleMouseButton(event) && item != null) {
                        select(cellRow, cellColumn, false);
                        pick.accept(item);
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            bind("LEFT", () -> select(cellRow, cellColumn - 1, false));
            bind("RIGHT", () -> select(cellRow, cellColumn + 1, false));
            bind("UP", () -> select(cellRow - 1, cellColumn, false));
            bind("DOWN", () -> select(cellRow + 1, cellColumn, false));
            bind("shift LEFT", () -> select(cellRow, cellColumn - 1, true));
            bind("shift RIGHT", () -> select(cellRow, cellColumn + 1, true));
            bind("shift UP", () -> select(cellRow - 1, cellColumn, true));
            bind("shift DOWN", () -> select(cellRow + 1, cellColumn, true));
            bind("DELETE", ReactorGridEditor.this::erase);
        }

        private void bind(String keystroke, Runnable operation) {
            getInputMap().put(KeyStroke.getKeyStroke(keystroke), keystroke);
            getActionMap().put(keystroke, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    operation.run();
                }
            });
        }

        void refresh(boolean active, ReactorItem value) {
            item = value;
            canEdit = active;
            setEnabled(active);
            JPanel result = resultHighlights == null ? null : resultHighlights[cellRow][cellColumn];
            String description = item == null ? getI18n("Workspace.EmptySlot") : item.toString();
            String status = result == null ? null : result.getToolTipText();
            String label = formatI18n("Workspace.Slot", (char) ('A' + cellRow), cellColumn + 1, description);
            setToolTipText(active ? (status == null ? label : label + " · " + status) : getI18n("Workspace.InactiveSlot"));
            getAccessibleContext().setAccessibleName(label);
            Color fill = GRID_BACKGROUND;
            if (result != null && Color.RED.equals(result.getBackground())) {
                fill = FAILURE;
            } else if (result != null && new Color(0xffa500).equals(result.getBackground())) {
                fill = RETAINED_HEAT;
            }
            setBackground(active ? fill : INACTIVE);
            setBorder(BorderFactory.createLineBorder(selected(cellRow, cellColumn) ? SELECTED : fill,
                    row == cellRow && column == cellColumn ? 2 : 1));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            graphics.setColor(getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
            if (item != null && item.image != null) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int size = Math.min(getWidth(), getHeight()) * 3 / 4;
                g.drawImage(item.image, (getWidth() - size) / 2, (getHeight() - size) / 2, size, size, this);
                g.dispose();
            } else if (item != null) {
                graphics.setColor(getForeground());
                graphics.drawString(Integer.toString(item.id), 5, getFontMetrics(getFont()).getAscent() + 4);
            }
        }
    }
}
