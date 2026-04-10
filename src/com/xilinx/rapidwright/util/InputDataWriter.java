package com.xilinx.rapidwright.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.xilinx.rapidwright.edif.*;

import com.xilinx.rapidwright.util.ModuleData;


public class InputDataWriter {

    private static String repeatChar(char ch, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    public static void writeHierarchicalTable(String outputPath, List<ModuleData> dataList) {
        // Determine max widths
        int maxInstanceLen = "Instance".length();
        int maxModuleLen = "Module".length();
        int maxTotalLen = "Total Cells".length();
        int maxDeltaLen = "Delta".length();

        for (ModuleData data : dataList) {
            maxInstanceLen = Math.max(maxInstanceLen, data.getInstance().length());
            maxModuleLen = Math.max(maxModuleLen, data.getModule().length());
            maxTotalLen = Math.max(maxTotalLen, String.valueOf(data.getTotalCells()).length());
            maxDeltaLen = Math.max(maxDeltaLen, String.valueOf(data.getDelta()).length());
        }

        // Header and separator
        String separator = "+" +
                "-".repeat(maxInstanceLen + 2) + "+" +
                "-".repeat(maxModuleLen + 2) + "+" +
                "-".repeat(maxTotalLen + 2) + "+" +
                "-".repeat(maxDeltaLen + 2) + "+";

        String header = String.format("| %-" + maxInstanceLen + "s | %-" + maxModuleLen + "s | %" + maxTotalLen + "s | %" + maxDeltaLen + "s |",
                "Instance", "Module", "Total Cells", "Delta");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(separator);
            writer.newLine();
            writer.write(header);
            writer.newLine();
            writer.write(separator);
            writer.newLine();

            for (ModuleData data : dataList) {
                String row = String.format("| %-" + maxInstanceLen + "s | %-" + maxModuleLen + "s | %" + maxTotalLen + "d | %" + maxDeltaLen + "d |",
                        data.getInstance(), data.getModule(), data.getTotalCells(), data.getDelta());
                writer.write(row);
                writer.newLine();
            }

            writer.write(separator);
            writer.newLine();
            System.out.println("File written to: " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeHierarchicalByParent(String outputPath,
                                                 List<ModuleData> moduleList, EDIFNetlist logicalNetlist) {

        Map<String, ModuleData> instanceMap = new HashMap<>();
        Map<String, List<ModuleData>> childrenMap = new HashMap<>();

        // Step 1: Map all instances
        for (ModuleData data : moduleList) {
            instanceMap.put(data.getInstance().trim(), data);
        }

        // Step 2: Organize by parent
        for (ModuleData data : moduleList) {
            String childInst = data.getInstance().trim();
            EDIFCellInst cellInst = logicalNetlist.getCellInstFromHierName(childInst);
            if (cellInst == null) continue;

            EDIFCell parentInst = cellInst.getParentCell();

            childrenMap.computeIfAbsent(parentInst.getName(), k -> new ArrayList<>()).add(data);
        }

        // Step 3: Recursive write
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            EDIFCell topCell = logicalNetlist.getTopCell();
            String topName = topCell.getName();

            writer.write(generateRow(instanceMap.get(topName), 0));
            writer.newLine();

            writeChildrenRecursive(writer, childrenMap, topName, instanceMap, 1);

            System.out.println("Hierarchical InputData written to: " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeChildrenRecursive(BufferedWriter writer,
                                               Map<String, List<ModuleData>> childrenMap,
                                               String parentInstanceName, Map<String, ModuleData> instanceMap, int level) throws IOException {

        List<ModuleData> children = childrenMap.get(parentInstanceName);
        if (children == null) return;
     
        children.sort(Comparator.comparing(ModuleData::getInstance));

        for (ModuleData child : children) {
            writer.write(generateRow(child, level));
            writer.newLine();
            writeChildrenRecursive(writer, childrenMap, child.getInstance().trim(), instanceMap, level + 1);
        }
    }

    private static String generateRow(ModuleData data, int indentLevel) {
        String indent = "  ".repeat(indentLevel);
        return String.format("%s%s, %s, %d, %d",
                indent, data.getInstance().trim(), data.getModule(), data.getTotalCells(), data.getDelta());
    }
    
    public static String writeInputDataMarkdown(List<ModuleData> moduleList, EDIFNetlist logicalNetlist) {
        Map<String, ModuleData> instanceMap = new HashMap<>();
        Map<String, List<ModuleData>> childrenMap = new HashMap<>();
        
        for (ModuleData data : moduleList) {
            instanceMap.put(data.getModule().trim(), data);
            childrenMap.computeIfAbsent(data.getParent(), k -> new ArrayList<>()).add(data);
        }
        
        EDIFCell topCell = logicalNetlist.getTopCell();
        String topName = topCell.getName();
        ModuleData topData = instanceMap.getOrDefault(topName, new ModuleData(topName, "(top)","", 0, 0));
        
        updateCellNum(childrenMap, topName, instanceMap);
        
        int instanceW = Math.max("Instance".length(), topData.getInstance().length());
        int moduleW = Math.max("Module".length(), topData.getModule().length());
        int totalW = "Total Cells".length();
        int deltaW = "Delta".length();
        
        for (ModuleData d : moduleList) {
            instanceW = Math.max(instanceW, d.getInstance().length() + 30);
            moduleW = Math.max(moduleW, d.getModule().length() + 30);
            totalW = Math.max(totalW, String.valueOf(d.getTotalCells()).length());
            deltaW = Math.max(deltaW, String.valueOf(d.getDelta()).length());
        }
        
        String separator = "+" +
                "-".repeat(instanceW + 2) + "+" +
                "-".repeat(moduleW + 2) + "+" +
                "-".repeat(totalW + 2) + "+" +
                "-".repeat(deltaW + 2) + "+";
        
        String header = String.format("| %-" + instanceW + "s | %-" + moduleW + "s | %" + totalW + "s | %" + deltaW + "s |",
                "Instance", "Module", "Total Cells", "Delta");
        
        // 3. Replaced BufferedWriter with StringBuilder
        StringBuilder sb = new StringBuilder();
        
        sb.append(separator).append("\n");
        sb.append(header).append("\n");
        sb.append(separator).append("\n");
        sb.append(formatMarkdownRow(topData, 0, instanceW, moduleW, totalW, deltaW)).append("\n");
        
        writeMarkdownChildren(sb, childrenMap, topName, 1, instanceMap, instanceW, moduleW, totalW, deltaW);
        
        sb.append(separator).append("\n");
        
        // 4. Return the massive string
        return sb.toString();
    }
    
    public static void writeInputDataMarkdown_WriteInFile(String outputPath, List<ModuleData> moduleList, EDIFNetlist logicalNetlist) {
        Map<String, ModuleData> instanceMap = new HashMap<>();
        Map<String, List<ModuleData>> childrenMap = new HashMap<>();
        Map<String, List<ModuleData>> childrenMap_parentChild = new HashMap<>();

        // 1. Build instance lookup map
        for (ModuleData data : moduleList) {
            instanceMap.put(data.getModule().trim(), data);
        }

        // 2. Build parent children map using EDIF hierarchy
        for (ModuleData data : moduleList) {
            childrenMap.computeIfAbsent(data.getParent(), k -> new ArrayList<>()).add(data);
        }
        
        // 2. Build parent children map using EDIF hierarchy
        for (ModuleData data : moduleList) {
            childrenMap_parentChild.computeIfAbsent(data.getParent(), k -> new ArrayList<>()).add(data);
        }
        
        EDIFCell topCell = logicalNetlist.getTopCell();
        String topName = topCell.getName();
        String topNameInst = logicalNetlist.getTopCellInst().getName();
        
        //ModuleData rootData = instanceMap.get(topName);
        
        // 3. Find top module (root of hierarchy)
        ModuleData topData = instanceMap.getOrDefault(topName, new ModuleData(topName, "(top)","", 0, 0));
        /**
        for (String childName : childrenMap.keySet()) {
            List<ModuleData> children = childrenMap.get(childName);
            updateCellNum(children,childName,topData);
        }
        */
        //updateCellNum(children,childName,topData);
        updateCellNum(childrenMap, topName, instanceMap);
        // 4. Calculate column widths
        int instanceW = Math.max("Instance".length(), topData.getInstance().length());
        int moduleW = Math.max("Module".length(), topData.getModule().length());
        int totalW = "Total Cells".length();
        int deltaW = "Delta".length();

        for (ModuleData d : moduleList) {
            instanceW = Math.max(instanceW, d.getInstance().length() + 30); // +4 for indent space
            moduleW = Math.max(moduleW, d.getModule().length() + 30);
            totalW = Math.max(totalW, String.valueOf(d.getTotalCells()).length());
            deltaW = Math.max(deltaW, String.valueOf(d.getDelta()).length());
        }

        // 5. Build header + separators
        String separator = "+" +
                "-".repeat(instanceW + 2) + "+" +
                "-".repeat(moduleW + 2) + "+" +
                "-".repeat(totalW + 2) + "+" +
                "-".repeat(deltaW + 2) + "+";

        String header = String.format("| %-" + instanceW + "s | %-" + moduleW + "s | %" + totalW + "s | %" + deltaW + "s |",
                "Instance", "Module", "Total Cells", "Delta");

        // 6. Write the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(separator); writer.newLine();
            writer.write(header); writer.newLine();
            writer.write(separator); writer.newLine();

            // Write root/top
            writer.write(formatMarkdownRow(topData, 0, instanceW, moduleW, totalW, deltaW)); writer.newLine();

            // Write children recursively
            writeMarkdownChildren_WriteInFile(writer, childrenMap, topName, 1, instanceMap, instanceW, moduleW, totalW, deltaW);

            writer.write(separator); writer.newLine();
            System.out.println("InputData.md written to: " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static int updateCellNum_back(List<ModuleData> children_next, String parentInstance, ModuleData children_data) {
        
        if ((children_next == null) || (children_next.size() == 1)) {
            children_data.setTotalCells(children_data.getTotalCells());
            return children_data.getTotalCells();
        }
        int cell_cumulative = 0;
        
        children_next.sort(Comparator.comparing(ModuleData::getInstance));
        
        for (ModuleData child : children_next) {
            //cell_cumulative = child.getTotalCells();
            cell_cumulative += updateCellNum_back(children_next,child.getModule().trim(),child);
        }
        children_data.setTotalCells(children_data.getTotalCells() + cell_cumulative);
        
        return children_data.getTotalCells();
    }
    
    private static void updateCellNum(Map<String, List<ModuleData>> childrenMap,
                                              String parentInstance, Map<String, ModuleData> instanceMap) {
        List<ModuleData> children = childrenMap.get(parentInstance);
        if (children == null) return;
        
        //children.sort(Comparator.comparing(ModuleData::getInstance));
        int totalCellNumber = instanceMap.get(parentInstance).getTotalCells();
        for (ModuleData child : children) {
            updateCellNum(childrenMap, child.getModule(),instanceMap);
            totalCellNumber += child.getTotalCells();
        }
        instanceMap.get(parentInstance).setTotalCells(totalCellNumber);
    }
    
    private static void writeMarkdownChildren_WriteInFile(BufferedWriter writer,
                                              Map<String, List<ModuleData>> childrenMap,
                                              String parentInstance,
                                              int level,
                                              Map<String, ModuleData> instanceMap,
                                              int instanceW, int moduleW, int totalW, int deltaW) throws IOException {
        List<ModuleData> children = childrenMap.get(parentInstance);
        if (children == null) return;
        
        children.sort(Comparator.comparing(ModuleData::getInstance));
        
        for (ModuleData child : children) {
            writer.write(formatMarkdownRow(child, level, instanceW, moduleW, totalW, deltaW)); writer.newLine();
            writeMarkdownChildren_WriteInFile(writer, childrenMap, child.getModule().trim(), level + 1,
                    instanceMap, instanceW, moduleW, totalW, deltaW);
        }
    }
    
    // 1. Changed BufferedWriter to StringBuilder and removed 'throws IOException'
    private static void writeMarkdownChildren(StringBuilder sb,
                                              Map<String, List<ModuleData>> childrenMap,
                                              String parentInstance,
                                              int level,
                                              Map<String, ModuleData> instanceMap,
                                              int instanceW, int moduleW, int totalW, int deltaW) {
        List<ModuleData> children = childrenMap.get(parentInstance);
        if (children == null) return;
        
        children.sort(Comparator.comparing(ModuleData::getInstance));
        
        for (ModuleData child : children) {
            // 2. Swapped writer.write() and writer.newLine() for sb.append()
            sb.append(formatMarkdownRow(child, level, instanceW, moduleW, totalW, deltaW)).append("\n");
            
            // 3. Pass the StringBuilder into the recursive call
            writeMarkdownChildren(sb, childrenMap, child.getModule().trim(), level + 1,
                    instanceMap, instanceW, moduleW, totalW, deltaW);
        }
    }
    
    private static String formatMarkdownRow(ModuleData data, int level,
                                            int instanceW, int moduleW, int totalW, int deltaW) {
        String indent = "  ".repeat(level);  // 2 spaces per level
        String indentedInstance = indent + data.getInstance().trim();
        return String.format("| %-" + instanceW + "s | %-" + moduleW + "s | %" + totalW + "d | %" + deltaW + "d |",
                indentedInstance, data.getModule(), data.getTotalCells(), data.getDelta());
    }
    public static String generateTimestampedFilename(String prefix, String extension) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        return prefix + "_" + timestamp + extension;
    }

}
