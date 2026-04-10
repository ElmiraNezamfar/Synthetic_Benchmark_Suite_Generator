/*
 *
 * Copyright (c) 2023, Advanced Micro Devices, Inc.
 * All rights reserved.
 *
 * Author: Chris Lavin, AMD Research and Advanced Development.
 *
 * This file is part of RapidWright.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.xilinx.rapidwright.util;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import java.io.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.tests.CodePerfTracker;

public class SyntheticIncrementalBenchmark {
        
    private String baseEdfPath;
    private String baseDcpPath;
    private EDIFNetlist input;
    private Design input_design;
    // 1. CONSTRUCTOR: Python will call this once to set up the initial files.    
    public SyntheticIncrementalBenchmark(String baseEdfPath, String baseDcpPath) {
        this.baseEdfPath = baseEdfPath;
        this.baseDcpPath = baseDcpPath;
    }
    
    // Python will call this directly, pass the 5 variables, and get the Markdown text back.
    public String processStep(String instanceName, String moduleName, String iteration, String step, String a, String folderpath) {
        String markdownResult = "";
        
        if (instanceName.equals("backToTheSourceDesign")) {
            input = EDIFTools.readEdifFile(this.baseEdfPath);
            input_design = Design.readCheckpoint(this.baseDcpPath, CodePerfTracker.SILENT);
            
            // Calls the updated method that returns a String (no files!)
            markdownResult = EDIFTools.updateCellDeltaCount_calculateModuleByModule(input);
            System.out.println("Reverts the current working design back to the base benchmark");
            
        } else if (instanceName.equals("selectedDesign")) {
            String edfFileName = this.baseEdfPath.substring(0, this.baseEdfPath.lastIndexOf('.')) + "_i-" + iteration + "_s-" + step + "_a-" + a + ".edf";
            input = EDIFTools.readEdifFile(edfFileName);
            
            String dcpFileName = this.baseEdfPath.substring(0, this.baseEdfPath.lastIndexOf('.')) + "_i-" + iteration + "_s-" + step + "_a-" + a + ".dcp";
            input_design = Design.readCheckpoint(dcpFileName, CodePerfTracker.SILENT);
            
            // Calls the updated method that returns a String (no files!)
            markdownResult = EDIFTools.updateCellDeltaCount_calculateModuleByModule(input);
            System.out.println("Updates the active design to the chosen optimal history design");
            
        } else {
            markdownResult = EDIFTools.syntheticIncBench(input, input_design, moduleName, instanceName, iteration, step, a, folderpath);
        }
        
        // Hand the markdown text directly back to Python!
        return markdownResult;
    }
}
