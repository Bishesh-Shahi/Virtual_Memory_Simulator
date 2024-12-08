import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;

public class VirtualMemory {
    public static final int PAGE_SIZE = 256;
    public static final int FRAME_SIZE = PAGE_SIZE;
    private int numPages;
    private int numFrames;
    private int tlbSize;

    private int[] pageTable;
    private int[] invertedPageTable;
    private TLB_Entry[] tlb;
    private int nextLoadLocation = 0;
    private int nextFreeTlbIndex = 0;
    private int pageFaults = 0;
    private int tlbMisses = 0;
    private int totalPageReferences = 0;
    private int diskAccesses = 0;
    private Map<Integer, ProcessStats> processStats = new ConcurrentHashMap<>();

    public static class ProcessStats {
        private AtomicInteger pageReferences = new AtomicInteger(0);
        private AtomicInteger tlbMisses = new AtomicInteger(0);
        private AtomicInteger pageFaults = new AtomicInteger(0);
        private volatile String status = "READY";
        
        public void incrementPageReferences() { pageReferences.incrementAndGet(); }
        public void incrementTlbMisses() { tlbMisses.incrementAndGet(); }
        public void incrementPageFaults() { pageFaults.incrementAndGet(); }
        
        public int getPageReferences() { return pageReferences.get(); }
        public double getTlbMissRatio() {
            return pageReferences.get() == 0 ? 0 : 
                   (double) tlbMisses.get() / pageReferences.get();
        }
        public double getPageFaultRatio() {
            return pageReferences.get() == 0 ? 0 : 
                   (double) pageFaults.get() / pageReferences.get();
        }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public VirtualMemory(int numPages, int numFrames, int tlbSize) {
        this.numPages = numPages;
        this.numFrames = numFrames;
        this.tlbSize = tlbSize;
        this.pageTable = new int[numPages];
        Arrays.fill(pageTable, -1);
        this.invertedPageTable = new int[numFrames];
        Arrays.fill(invertedPageTable, -1);
        this.tlb = new TLB_Entry[tlbSize];
        for (int i = 0; i < tlbSize; i++) {
            tlb[i] = new TLB_Entry(-1, -1, false);
        }
    }

    private int searchTlb(int pageNumber) {
        for (TLB_Entry entry : tlb) {
            if (entry.isValid() && entry.getPageNumber() == pageNumber) {
                return entry.getFrameNumber();
            }
        }
        tlbMisses++;
        return -1;
    }

    private void updateTlb(int pageNumber, int frameNumber) {
        tlb[nextFreeTlbIndex].setPageNumber(pageNumber);
        tlb[nextFreeTlbIndex].setFrameNumber(frameNumber);
        tlb[nextFreeTlbIndex].setValid(true);
        nextFreeTlbIndex = (nextFreeTlbIndex + 1) % tlbSize;
    }

    public synchronized int searchPageTable(int pageNumber, int processId) {
        // Validate input
        if (pageNumber < 0 || pageNumber >= numPages) {
            return -1;
        }

        ProcessStats stats = processStats.computeIfAbsent(processId, k -> new ProcessStats());
        stats.incrementPageReferences();
        totalPageReferences++;
        
        // First check TLB
        int frameNumber = searchTlb(pageNumber);
        if (frameNumber != -1) {
            return frameNumber;
        }
        
        stats.incrementTlbMisses();
        
        // Check if we need to evict a page (page fault handling)
        if (pageTable[pageNumber] == -1) {
            stats.incrementPageFaults();
            pageFaults++;
            diskAccesses++;
            
            // If the next frame is already occupied, update the page table entry
            if (invertedPageTable[nextLoadLocation] != -1) {
                pageTable[invertedPageTable[nextLoadLocation]] = -1;
            }
            
            frameNumber = nextLoadLocation;
            pageTable[pageNumber] = frameNumber;
            invertedPageTable[frameNumber] = pageNumber;
            nextLoadLocation = (nextLoadLocation + 1) % numFrames;
            updateTlb(pageNumber, frameNumber);
            return frameNumber;
        }
        
        updateTlb(pageNumber, pageTable[pageNumber]);
        return pageTable[pageNumber];
    }

    public String getStateAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------------------------------------------------------------------------------------\n");
        sb.append("The current state of the TLB:\n");
        
        sb.append("Page#:  ");
        for (TLB_Entry entry : tlb) {
            sb.append(String.format("%-4d", entry.getPageNumber()));
        }
        sb.append("\n");
        
        sb.append("Frame#: ");
        for (TLB_Entry entry : tlb) {
            sb.append(String.format("%-4d", entry.getFrameNumber()));
        }
        sb.append("\n\n");

        sb.append("The page table (page#: frame# (-1 if not mapped), valid bit):\n");
        sb.append("Page#:  ");
        for (int i = 0; i < numPages; i++) {
            sb.append(String.format("%-4d", i));
        }
        sb.append("\n");
        
        sb.append("Frame#: ");
        for (int frame : pageTable) {
            sb.append(String.format("%-4d", frame));
        }
        sb.append("\n");
        
        sb.append("Valid:  ");
        for (int frame : pageTable) {
            sb.append(String.format("%-4d", (frame >= 0 ? 1 : 0)));
        }
        sb.append("\n");
        
        return sb.toString();
    }

    public int getTotalPageReferences() { return totalPageReferences; }
    public int getTlbMisses() { return tlbMisses; }
    public int getPageFaults() { return pageFaults; }
    public int getNumPages() { return numPages; }
    
    public double getTlbMissRatio() {
        return totalPageReferences == 0 ? 0 : (double) tlbMisses / totalPageReferences;
    }
    
    public double getPageFaultRatio() {
        return totalPageReferences == 0 ? 0 : (double) pageFaults / totalPageReferences;
    }
    
    public void incrementTotalPageReferences() {
        totalPageReferences++;
    }

    public Map<Integer, ProcessStats> getProcessStats() {
        return processStats;
    }

    public ProcessStats getOrCreateProcessStats(int processId) {
        return processStats.computeIfAbsent(processId, k -> new ProcessStats());
    }
}