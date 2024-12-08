import javax.swing.*;
import java.awt.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

public class VirtualMemoryGUI extends JFrame {
    private VirtualMemory vm;
    private JTextArea outputArea;
    private JTextField pagesField, framesField, tlbSizeField;
    private JButton initButton, stepButton, runAllButton, pauseButton;
    private JPanel statsPanel;
    private Random random;
    private int currentStep = 0;
    private static final int TOTAL_STEPS = 20;
    private ExecutorService executor;
    private AtomicBoolean isPaused;
    private AtomicBoolean isRunning;
    private JSpinner processCountSpinner;
    private JPanel processStatusPanel;

    public VirtualMemoryGUI() {
        setTitle("Virtual Memory Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        executor = Executors.newSingleThreadExecutor();
        isPaused = new AtomicBoolean(false);
        isRunning = new AtomicBoolean(false);
        setupUI();
        setSize(1200, 800);
        
        setLocationRelativeTo(null);
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        
        // Input Panel
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        
        inputPanel.add(new JLabel("Number of Pages:"));
        pagesField = new JTextField("");
        inputPanel.add(pagesField);
        
        inputPanel.add(new JLabel("Number of Frames:"));
        framesField = new JTextField("");
        inputPanel.add(framesField);
        
        inputPanel.add(new JLabel("TLB Size:"));
        tlbSizeField = new JTextField("");
        inputPanel.add(tlbSizeField);
        
        initButton = new JButton("Initialize");
        inputPanel.add(initButton);
        
        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        stepButton = new JButton("Step");
        runAllButton = new JButton("Run All");
        pauseButton = new JButton("Pause");
        stepButton.setEnabled(false);
        runAllButton.setEnabled(false);
        pauseButton.setEnabled(false);
        buttonPanel.add(stepButton);
        buttonPanel.add(runAllButton);
        buttonPanel.add(pauseButton);
        
        // Output Area
        outputArea = new JTextArea(20, 50);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setEditable(false);
        
        // scroll pane with minimum size
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setMinimumSize(new Dimension(400, 300));
        scrollPane.setPreferredSize(new Dimension(400, 300));
        
        // Stats Panel - Modified
        statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Statistics"));
        
        // Wrap statsPanel in a ScrollPane with fixed size
        JScrollPane statsScrollPane = new JScrollPane(statsPanel);
        statsScrollPane.setPreferredSize(new Dimension(250, 400));
        statsScrollPane.setMinimumSize(new Dimension(250, 400));
        
        // Process Control Panel
        JPanel processPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        processPanel.setBorder(BorderFactory.createTitledBorder("Process Control"));
        
        processCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
        processPanel.add(new JLabel("Number of Processes:"));
        processPanel.add(processCountSpinner);
        
        // Add Process Status Panel
        processStatusPanel = new JPanel();
        processStatusPanel.setLayout(new BoxLayout(processStatusPanel, BoxLayout.Y_AXIS));
        processStatusPanel.setBorder(BorderFactory.createTitledBorder("Process Status"));
        
        JScrollPane statusScrollPane = new JScrollPane(processStatusPanel);
        statusScrollPane.setPreferredSize(new Dimension(200, 150));
        
        // Add components to frame
        add(inputPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        add(statsScrollPane, BorderLayout.EAST);
        JPanel westPanel = new JPanel(new BorderLayout());
        westPanel.add(processPanel, BorderLayout.NORTH);
        westPanel.add(statusScrollPane, BorderLayout.CENTER);
        add(westPanel, BorderLayout.WEST);
        
        setupListeners();
    }

    private void setupListeners() {
        initButton.addActionListener(e -> initializeSimulation());
        
        stepButton.addActionListener(e -> {
            if (!isRunning.get()) {
                int processCount = (Integer) processCountSpinner.getValue();
                if (processCount > 0) {
                    isRunning.set(true);
                    int processId = currentStep % processCount;
                    performSingleStep(processId, new Random(100 + processId));
                    isRunning.set(false);
                }
            }
        });
        
        runAllButton.addActionListener(e -> {
            if (!isRunning.get()) {
                runAllButton.setEnabled(false);
                stepButton.setEnabled(false);
                pauseButton.setEnabled(true);
                runSimulation();
            }
        });
        
        pauseButton.addActionListener(e -> {
            isPaused.set(!isPaused.get());
            pauseButton.setText(isPaused.get() ? "Resume" : "Pause");
        });
    }

    private void runSimulation() {
        if (vm == null) {
            JOptionPane.showMessageDialog(this, 
                "Please initialize the simulation first", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentStep = 0;
        int processCount = (Integer) processCountSpinner.getValue();
        
        // Reset simulation state
        isRunning.set(true);
        isPaused.set(false);
        
        // Initialize all processes
        for (int i = 0; i < processCount; i++) {
            VirtualMemory.ProcessStats stats = vm.getOrCreateProcessStats(i);
            stats.setStatus("SLEEPING");
        }
        
        // Update UI state
        pauseButton.setEnabled(true);
        stepButton.setEnabled(false);
        runAllButton.setEnabled(false);
        updateProcessStatus();
        
        // Create and submit tasks for each process
        for (int i = 0; i < processCount; i++) {
            final int processId = i;
            executor.execute(() -> {
                Random processRandom = new Random(100 + processId);
                try {
                    VirtualMemory.ProcessStats stats = vm.getOrCreateProcessStats(processId);
                    
                    for (int step = 0; step < TOTAL_STEPS && isRunning.get(); step++) {
                        if (isPaused.get()) {
                            synchronized(vm) {
                                stats.setStatus("PAUSED");
                                SwingUtilities.invokeLater(this::updateProcessStatus);
                            }
                            while (isPaused.get() && isRunning.get()) {
                                Thread.sleep(100);
                            }
                            if (!isRunning.get()) break;
                        }
                        
                        synchronized(vm) {
                            stats.setStatus("RUNNING");
                            SwingUtilities.invokeLater(this::updateProcessStatus);
                            performSingleStep(processId, processRandom);
                            Thread.sleep(200);
                        }
                    }
                    
                    synchronized(vm) {
                        stats.setStatus("FINISHED");
                        SwingUtilities.invokeLater(this::updateProcessStatus);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Monitor completion in separate thread
        executor.execute(this::monitorCompletion);
    }

    // New method to handle completion monitoring
    private void monitorCompletion() {
        try {
            while (isRunning.get()) {
                Thread.sleep(100);
                synchronized(vm) {
                    boolean allFinished = vm.getProcessStats().values().stream()
                        .allMatch(stats -> "FINISHED".equals(stats.getStatus()));
                    
                    if (allFinished) {
                        isRunning.set(false);
                        SwingUtilities.invokeLater(() -> {
                            stepButton.setEnabled(true);
                            runAllButton.setEnabled(true);
                            pauseButton.setEnabled(false);
                            pauseButton.setText("Pause");
                            
                            // Show completion message
                            JOptionPane.showMessageDialog(this,
                                "Simulation completed successfully!",
                                "Simulation Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                        });
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void performSingleStep(int processId, Random processRandom) {
        // Add bounds checking and ensure ProcessStats exists
        if (processId < 0) {
            return;
        }
        
        synchronized(vm) {
            if (vm != null) {
                VirtualMemory.ProcessStats stats = vm.getOrCreateProcessStats(processId);
                
                int pageNumber = processRandom.nextInt(vm.getNumPages());
                int offset = processRandom.nextInt(VirtualMemory.PAGE_SIZE);
                int logicalAddress = pageNumber * VirtualMemory.PAGE_SIZE + offset;
                
                int frameNumber = vm.searchPageTable(pageNumber, processId);
                int physicalAddress = frameNumber * VirtualMemory.PAGE_SIZE + offset;
                
                StringBuilder output = new StringBuilder();
                output.append("\n------ Process ").append(processId)
                      .append(" - Step ").append(currentStep + 1)
                      .append(" ------\n");
                output.append("Page ").append(pageNumber).append(" requested. ");
                output.append("Logical address: ").append(logicalAddress);
                output.append(" => Physical address: ").append(physicalAddress).append("\n\n");
                
                output.append(vm.getStateAsString());
                
                final String outputText = output.toString();
                SwingUtilities.invokeLater(() -> {
                    outputArea.append(outputText);
                    outputArea.setCaretPosition(outputArea.getDocument().getLength());
                    updateStats();
                    currentStep++;
                });
            }
        }
    }

    private void initializeSimulation() {
        try {
            int pages = Integer.parseInt(pagesField.getText());
            int frames = Integer.parseInt(framesField.getText());
            int tlbSize = Integer.parseInt(tlbSizeField.getText());
            
            vm = new VirtualMemory(pages, frames, tlbSize);
            currentStep = 0;
            isRunning.set(false);
            isPaused.set(false);
            
            // Initialize process stats
            int processCount = (Integer) processCountSpinner.getValue();
            synchronized(vm) {
                vm.getProcessStats().clear();
                for (int i = 0; i < processCount; i++) {
                    vm.getProcessStats().put(i, new VirtualMemory.ProcessStats());
                }
            }
            
            outputArea.setText("Simulation initialized\n");
            stepButton.setEnabled(true);
            runAllButton.setEnabled(true);
            pauseButton.setEnabled(false);
            updateStats();
            updateProcessStatus();
            
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, 
                "Please enter valid numbers", 
                "Input Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStats() {
        statsPanel.removeAll();
        
        addStatRow("Total References:", vm.getTotalPageReferences());
        addStatRow("TLB Miss Ratio:", String.format("%.2f", vm.getTlbMissRatio()));
        addStatRow("Page Fault Ratio:", String.format("%.2f", vm.getPageFaultRatio()));
        
        Map<Integer, VirtualMemory.ProcessStats> processStats = vm.getProcessStats();
        for (Map.Entry<Integer, VirtualMemory.ProcessStats> entry : processStats.entrySet()) {
            addStatRow("Process " + entry.getKey() + " References:", 
                      entry.getValue().getPageReferences());
            addStatRow("Process " + entry.getKey() + " TLB Miss Ratio:", 
                      String.format("%.2f", entry.getValue().getTlbMissRatio()));
            addStatRow("Process " + entry.getKey() + " Page Fault Ratio:", 
                      String.format("%.2f", entry.getValue().getPageFaultRatio()));
        }
        
        statsPanel.revalidate();
        statsPanel.repaint();
    }

    private void addStatRow(String label, Object value) {
        // Modified to use horizontal panel for each row
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel(label));
        row.add(new JLabel(value.toString()));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        statsPanel.add(row);
    }

    private void updateProcessStatus() {
        processStatusPanel.removeAll();
        
        Map<Integer, VirtualMemory.ProcessStats> processStats = vm.getProcessStats();
        for (Map.Entry<Integer, VirtualMemory.ProcessStats> entry : processStats.entrySet()) {
            JPanel processRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel statusLabel = new JLabel(String.format("Process %d: %s", 
                entry.getKey(), entry.getValue().getStatus()));
            
            // Add color coding based on status
            switch (entry.getValue().getStatus()) {
                case "RUNNING":
                    statusLabel.setForeground(Color.GREEN);
                    break;
                case "SLEEPING":
                    statusLabel.setForeground(Color.ORANGE);
                    break;
                case "PAUSED":
                    statusLabel.setForeground(Color.RED);
                    break;
                case "FINISHED":
                    statusLabel.setForeground(Color.GRAY);
                    break;
                default:
                    statusLabel.setForeground(Color.BLACK);
            }
            
            processRow.add(statusLabel);
            processStatusPanel.add(processRow);
        }
        
        processStatusPanel.revalidate();
        processStatusPanel.repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new VirtualMemoryGUI().setVisible(true);
        });
    }
    
    @Override
    public void dispose() {
        executor.shutdown();
        super.dispose();
    }
} 