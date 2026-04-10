package com.xilinx.rapidwright.util;

public class ModuleData {
    private String instance;
    private String module;
    private String parent;
    private int totalCells;
    private int delta;

    public ModuleData(String instance, String module, String parent, int totalCells, int delta) {
        this.instance = instance;
        this.module = module;
        this.parent = parent;
        this.totalCells = totalCells;
        this.delta = delta;
    }

    public String getInstance() { return instance; }
    public String getModule() { return module; }
    public String getParent() { return parent; }
    public int getTotalCells() { return totalCells; }
    public int getDelta() { return delta; }
    public void setTotalCells(int totalCells) { this.totalCells = totalCells; }

    public String toString() {
        return String.format("%s [%s]: cells=%d, delta=%d", instance, module, totalCells, delta);
    }
}