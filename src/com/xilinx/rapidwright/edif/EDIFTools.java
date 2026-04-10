/*
 * Copyright (c) 2017-2022, Xilinx, Inc.
 * Copyright (c) 2022, Advanced Micro Devices, Inc.
 * All rights reserved.
 *
 * Author: Chris Lavin, Xilinx Research Labs.
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
/**
 *
 */
package com.xilinx.rapidwright.edif;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


import com.xilinx.rapidwright.design.*;
import com.xilinx.rapidwright.device.Part;
import com.xilinx.rapidwright.device.PartNameTools;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.*;
import org.python.antlr.ast.Str;


/**
 * A collection of utility methods for extracting information from BYU EDIF tool
 * netlists.
 * Created on: Dec 2, 2015
 */
public class EDIFTools {

    public static String EDIF_HIER_SEP = "/";

    public static final String EDIF_LIBRARY_HDI_PRIMITIVES_NAME = "hdi_primitives";

    public static final String MACRO_PRIMITIVES_LIB = "macro_primitives";

    public static final String EDIF_LIBRARY_WORK_NAME = "work";

    public static final String EDIF_PART_PROP = "PART";

    public static final String DONT_TOUCH = "DONT_TOUCH";

    public static final Set<String> edifKeywordSet =
        new HashSet<>(Arrays.asList(
            "abs", "acload", "actual", "after", "and", "annotate", "apply",
            "arc", "array", "arraymacro", "arrayrelatedinfo", "arraysite",
            "assign", "atleast", "atmost", "author", "basearray", "becomes",
            "between", "block", "boolean", "booleandisplay", "booleanmap",
            "borderpattern", "borderwidth", "boundingbox","build", "ceiling",
            "cell", "cellref", "celltype", "change", "circle","collector",
            "color", "comment", "commentgraphics", "compound", "concat",
            "connectlocation", "constant", "constraint", "contents",
            "cornertype","criticality", "currentmap", "curve", "cycle",
            "dataorigin", "dcfaninload", "dcfanoutload", "dcmaxfanin",
            "dcmaxfanout", "delay", "delta", "derivation", "design",
            "designator", "difference", "direction", "display", "divide",
            "dominates", "dot", "duration", "e", "edif", "ediflevel",
            "edifversion", "else", "enclosuredistance", "endtype", "entry",
            "equal", "escape", "event", "exactly", "external", "fabricate",
            "false", "figure", "figurearea", "figuregroup",
            "figuregroupobject", "figuregroupoverride", "figuregroupref",
            "figureperimeter", "figurewidth", "fillpattern", "fix", "floor",
            "follow", "forbiddenevent", "foreach", "formal", "formallist",
            "generate", "globalportref", "greaterthan", "gridmap", "if",
            "ignore", "includefiguregroup", "increasing", "initial",
            "instance", "instancebackannotate", "instancegroup", "instancemap",
            "instanceref", "integer", "integerdisplay", "interface",
            "interfiguregroupspacing", "intersection",
            "intrafiguregroupspacing", "inverse", "isolated", "iterate",
            "joined", "justify", "keywordalias", "keyworddefine",
            "keyworddisplay", "keywordlevel", "keywordmap",
            "keywordparameters", "lessthan", "library", "libraryref",
            "listofnets", "listofports", "literal", "loaddelay", "logicassign",
            "logicinput", "logiclist", "logicmapinput", "logicmapoutput",
            "logiconeof", "logicoutput", "logicport", "logicref", "logicvalue",
            "logicwaveform", "maintain", "match", "max", "member", "min",
            "minomax", "minomaxdisplay", "mnm", "mod", "multiplevalueset",
            "mustjoin", "name", "negate", "net", "netbackannotate",
            "netbundle", "netdelay", "netgroup", "netmap", "netref",
            "nochange", "nonpermutable", "not", "notallowed", "notchspacing",
            "number", "numberdefinition", "numberdisplay", "offpageconnector",
            "offsetevent", "openshape", "optional", "or", "orientation",
            "origin", "overhangdistance", "overlapdistance", "oversize",
            "owner", "page", "pagesize", "parameter", "parameterassign",
            "parameterdisplay", "path", "pathdelay", "pathwidth", "permutable",
            "physicaldesignrule", "plug", "point", "pointdisplay", "pointlist",
            "pointsubtract", "pointsum", "polygon", "port", "portbackannotate",
            "portbundle", "portdelay", "portgroup", "portimplementation",
            "portinstance", "portlist", "portlistalias", "portmap", "portref",
            "product", "program", "property", "propertydisplay",
            "protectionframe", "pt", "rangevector", "rectangle",
            "rectanglesize", "rename", "resolves", "scale", "scalex", "scaley",
            "section", "shape", "simulate", "simulationinfo", "singlevalueset",
            "site", "socket", "socketset", "status", "steady",
            "strictlyincreasing", "string", "stringdisplay", "strong",
            "subtract", "sum", "symbol", "symmetry", "table", "tabledefault",
            "technology", "textheight", "then", "timeinterval", "timestamp",
            "timing", "transform", "transition", "trigger", "true",
            "unconstrained", "undefined", "union", "unit", "unused",
            "userdata", "variable", "version", "view", "viewlist", "viewmap",
            "viewref", "viewtype", "visible", "voltagemap", "wavevalue",
            "weak", "weakjoined", "when", "while", "written", "xcoord", "xor",
            "ycoord"
        ));

    public static final String LOGICAL_VCC_NET_NAME = "<const1>";
    public static final String LOGICAL_GND_NET_NAME = "<const0>";

    public static final String LOAD_TCL_SUFFIX = "_load.tcl";

    public static final AtomicInteger UNIQUE_COUNT = new AtomicInteger();

    /**
     * Flag to set a feature where any .edf file that is attempted to be loaded will check if an
     * existing binary EDIF has already been generated and will load that instead (faster).  This
     * will also enable generation of binary EDIF files after a successful EDIF file loading to be
     * used on the next load.
     */
    public static final boolean RW_ENABLE_EDIF_BINARY_CACHING =
            System.getenv("RW_ENABLE_EDIF_BINARY_CACHING") != null;

    public static String getUniqueSuffix() {
        return "_rw_created" + UNIQUE_COUNT.getAndIncrement();
    }

    /**
     * Helper method to get the part name from an EDIF netlist.
     * @param edif The EDIF netlist from which to extract the part name.
     * @return The part name or null if none was found.
     */
    public static String getPartName(EDIFNetlist edif) {
        EDIFPropertyValue p = edif.getDesign().getPropertiesMap().get(EDIF_PART_PROP);
        if (p == null) {
            p = edif.getDesign().getPropertiesMap().get(EDIF_PART_PROP.toLowerCase());
            if (p == null) return null;
        }
        return p.getValue();
    }

    public static boolean ensureCellInLibraries(EDIFNetlist e, EDIFCell c) {
        EDIFCell libCell = e.getCell(c.getName());
        if (libCell == null) {
            // Just add it
            return matchCellToEDIFLibrary(e, c).addCell(c) != null;
        }
        return true;
    }

    private static EDIFLibrary matchCellToEDIFLibrary(EDIFNetlist n, EDIFCell c) {
        String libName = c.isPrimitive() ? EDIF_LIBRARY_HDI_PRIMITIVES_NAME : EDIF_LIBRARY_WORK_NAME;
        EDIFLibrary lib = n.getLibrary(libName);
        if (lib != null) return lib;
        Collection<EDIFLibrary> libs = n.getLibraries();
        if (libs.size() == 0) {
            // No libraries exist, so create one
            EDIFLibrary libToAdd = new EDIFLibrary(libName);
            n.addLibrary(libToAdd);
            return libs.iterator().next();
        }
        // Not sure which one to use, so we'll just choose the first
        return libs.iterator().next();
    }

    public static void consolidateLibraries(EDIFNetlist edif) {
        HashMap<String,EDIFCell> moveToWork = new HashMap<String,EDIFCell>();
        EDIFLibrary workLib = edif.getLibrary(EDIF_LIBRARY_WORK_NAME);
        if (workLib == null) {
            workLib = new EDIFLibrary(EDIF_LIBRARY_WORK_NAME);
            edif.addLibrary(workLib);
        }

        for (EDIFLibrary lib : edif.getLibraries()) {
            for (EDIFLibrary extLib : lib.getExternallyReferencedLibraries()) {
                if (extLib.getName().equals(workLib.getName())) {
                    for (EDIFCell c : extLib.getCells()) {
                        EDIFCell duplicate = moveToWork.put(c.toString(), c);
                        if (duplicate != null) {
                            throw new RuntimeException("ERROR: duplicate library cell " + c.toString());
                        }
                    }
                }
            }
        }
        for (EDIFCell c : moveToWork.values()) {
            c.moveToLibrary(workLib);
            workLib.addCell(c);
        }
        EDIFLibrary hdiPrims = edif.getLibrary(EDIF_LIBRARY_HDI_PRIMITIVES_NAME);
        if (hdiPrims == null) {
            hdiPrims = new EDIFLibrary(EDIF_LIBRARY_HDI_PRIMITIVES_NAME);
        }
        for (EDIFCell c : workLib.getExternallyReferencedCells()) {
            if (c.getLibrary().getName().equals(hdiPrims.getName())) {
                if (!hdiPrims.containsCell(c.getName())) {
                    c.moveToLibrary(hdiPrims);
                    hdiPrims.addCell(c);
                }
            }
        }
    }

    public static void connectPortBus(EDIFCell topCell, EDIFCellInst src, EDIFCellInst snk,
                                      String srcPrefix, String snkPrefix, int width) {
        for (int i=0; i < width; i++) {
            String suffix = "["+i+"]";
            String outputPortName = srcPrefix + suffix;
            String inputPortName = snkPrefix + suffix;

            EDIFNet net = null;
            net = new EDIFNet("conn_net_" + EDIFTools.makeNameEDIFCompatible(outputPortName), topCell);

            EDIFPortInst outputPortInst = src.getPortInst(outputPortName);
            EDIFPortInst inputPortInst = snk.getPortInst(inputPortName);

            @SuppressWarnings("unused")
            EDIFPortInst outputPort = new EDIFPortInst(outputPortInst.getPort(),net,outputPortInst.getIndex(),src);
            @SuppressWarnings("unused")
            EDIFPortInst inputPort = new EDIFPortInst(inputPortInst.getPort(),net,inputPortInst.getIndex(),snk);
        }
    }

    /**
     * Creates a map for each EDIF cell type to all its instances, starting with the top cell instance.
     * @param topCellInst The top cell instance from which to start the search.
     * @return A map where the key is the cell type and the value is a list of all instances of the type.
     */
    public static HashMap<EDIFCell, ArrayList<EDIFCellInst>> generateCellInstMap(EDIFCellInst topCellInst) {
        HashMap<EDIFCell, ArrayList<EDIFCellInst>> instanceMap = new HashMap<EDIFCell, ArrayList<EDIFCellInst>>();

        Queue<EDIFCellInst> q = new LinkedList<EDIFCellInst>();
        q.add(topCellInst);

        while (!q.isEmpty()) {
            EDIFCellInst eci = q.poll();

            ArrayList<EDIFCellInst> insts = instanceMap.get(eci.getCellType());
            if (insts == null) {
                insts = new ArrayList<EDIFCellInst>();
                instanceMap.put(eci.getCellType(), insts);
            }
            insts.add(eci);

            for (EDIFCellInst child : eci.getCellType().getCellInsts()) {
                q.add(child);
            }
        }

        return instanceMap;
    }

    /**
     * Gets the EDIFNet corresponding to the hierarchical name of a net.
     * @param hierarchicalName The full path name to the het
     * @return The net or null if none was found
     */
    public static EDIFNet getNet(EDIFNetlist e, String hierarchicalName) {
        String[] parts = hierarchicalName.split(EDIF_HIER_SEP);

        EDIFCellInst currInst = e.getTopCellInst();
        for (int i=0; i < parts.length-1; i++) {
            currInst = currInst.getCellType().getCellInst(EDIFTools.makeNameEDIFCompatible(parts[i]));
        }
        String netName = makeNameEDIFCompatible(parts[parts.length-1]);
        return currInst.getCellType().getNet(netName);
    }

    public static EDIFNet addNet(EDIFNetlist e, String hierarchicalName) {
        String[] parts = hierarchicalName.split(EDIF_HIER_SEP);
        EDIFCellInst currInst = e.getTopCellInst();
        for (int i=0; i < parts.length-1; i++) {
            currInst = currInst.getCellType().getCellInst(EDIFTools.makeNameEDIFCompatible(parts[i]));
        }
        String netName = makeNameEDIFCompatible(parts[parts.length-1]);
        EDIFNet newEDIFNet = null;

        newEDIFNet = new EDIFNet(netName,currInst.getCellType());
        currInst.getCellType().addNet(newEDIFNet);

        return newEDIFNet;
    }

    /**
     * Tries to make a string EDIF compatible by replacing invalid characters
     * with an underscore.
     * @param currName Existing name to be made legal
     * @return A new string that has been legalized to EDIF standards.
     */
    public static String makeNameEDIFCompatible(String currName) {
        char[] newName = currName.toCharArray();
        int len = lengthOfNameWithoutBus(newName);
        for (int i=0; i < len; i++) {
            switch (newName[i]) {
            case '[':
            case ']':
            case '<':
            case '>':
            case '.':
            case '/':
            case '\\':
            case '(':
            case ')':
            case '{':
            case '}':
            case '?':
            case ';':
            case '\'':
            case '`':
            case ':':
            case '\"':
            case '!':
            case '|':
            case '~':
            case '*':
            case '^':
            case '=':
            case '-':
            case '+':
            case ',':
            case '%':
            case '#':
            case '@':
            case '$':
            case '&':
            case ' ':
                newName[i] = '_';
            default:
                // Keep the same
            }
            if (newName[i]>127) {
                newName[i] = '_';
            }
        }
        if (newName[0] == '_' || Character.isDigit(newName[0])) return "&" + new String(newName,0,len);
        return new String(newName,0,len);
    }

    /**
     * Strips off bracket index in a bussed name (ex:
     * {@code "data[0]" --> "data["}). Leaves open bracket by default as this is the
     * key for bussed ports in the port map of {@link EDIFCell}
     * 
     * @param name Bracketed bussed name.
     * @return Name of bus with index and close bracket removed
     */
    public static String getRootBusName(String name) {
        return getRootBusName(name, true);
    }

    /**
     * Strips off bracket index in a bussed name (ex: {@code "data[0]" --> "data"}).
     * 
     * @param name               Bracketed bussed name.
     * @param includeOpenBracket If true, the result will include the open square
     *                           bracket ("data[")
     * @return Name of bus with brackets removed
     */
    public static String getRootBusName(String name, boolean includeOpenBracket) {
        int bracket = name.lastIndexOf('[');
        if (bracket == -1) return name;
        return name.substring(0, bracket + (includeOpenBracket ? 1 : 0));
    }

    /**
     * Determines if the char[] ends with the pattern [#:#] where # are positive
     * bus values (e.g., [7:0]) and then returns the length of the string without
     * the bus suffix (if it exists).  If the name does not end with the bus
     * pattern, it returns the original length of the char[].
     * @param name
     * @return The length of the string
     */
    public static int lengthOfNameWithoutBus(char[] name) {
        return lengthOfNameWithoutBus(name, false);
    }

    /**
     * Determines if the char[] ends with the pattern [#:#] where # are positive bus
     * values (e.g., [7:0]) and then returns the length of the string without the
     * bus suffix (if it exists). If the name does not end with the bus pattern, it
     * returns the original length of the char[].
     * 
     * @param name
     * @param keepOpenBracket In the case of a bussed name, this will return the
     *                        index of the string including the open square bracket
     *                        (useful for port the bus name keyed map in EDIFCell).
     * @return The length of the string
     */
    public static int lengthOfNameWithoutBus(char[] name, boolean keepOpenBracket) {
        int len = name.length;
        int i = len-1;
        if (name[i--] != ']') return len;
        while (Character.isDigit(name[i])) {
            i--;
        }
        if (name[i--] != ':') return len;
        while (Character.isDigit(name[i])) {
            i--;
        }
        if (name[i] != '[') return len;
        return i + (keepOpenBracket ? 1 : 0);
    }

    public static int getPortIndexFromName(String name) {
        int lengthRootName = name.lastIndexOf('[');
        String tmp = name.substring(lengthRootName+1, name.length()-1);
        return Integer.parseInt(tmp);
    }

    /**
     *
     * @param name
     * @return
     */
    public static int getWidthOfPortFromName(String name) {
        int lengthRootName = lengthOfNameWithoutBus(name.toCharArray());
        if (lengthRootName == name.length()) return 1;
        int colonIdx = -1;
        int leftBracket = -1;
        for (int i=name.length()-3; i >= 0; i--) {
            char c = name.charAt(i);
            if (c == ':') colonIdx = i;
            else if (c == '[') {
                leftBracket = i;
                break;
            }
        }
        if (colonIdx == -1 || leftBracket == -1) {
            throw new RuntimeException("ERROR: Interpreting port " + name + ", couldn't identify indicies.");
        }

        int left = Integer.parseInt(name.substring(leftBracket+1, colonIdx));
        int right = Integer.parseInt(name.substring(colonIdx+1, name.length()-1));
        return Math.abs(left - right) + 1;
    }

    /**
     * Create a unique net in the provided parent EDIFCell with the preferred provided
     * name. If such a net already exists in this cell, append a unique suffix.
     * @param parentCell EDIFCell in which new net is to be created.
     * @param netName Preferred net name.
     * @return Newly created EDIFNet.
     */
    public static EDIFNet createUniqueNet(EDIFCell parentCell, String netName) {
        if (parentCell.getNet(netName) != null) {
            netName += getUniqueSuffix();
        }
        return new EDIFNet(netName, parentCell);
    }

    /**
     * Create a unique port in the provided parent EDIFCell with the preferred provided
     * name. If such a port already exists in this cell, append a unique suffix.
     * This method checks for the existence of the given name as well as for its root bus,
     * e.g. for portName 'foo[9]', both this exact name and 'foo' will be checked.
     * @param parentCell EDIFCell in which new port is to be created.
     * @param portName Preferred port name.
     * @param dir EDIFDirection.
     * @param width Bit width.
     * @return Newly created EDIFPort.
     */
    public static EDIFPort createUniquePort(EDIFCell parentCell, String portName, EDIFDirection dir, int width) {
        String rootBusName = getRootBusName(portName);
        if (parentCell.getPort(rootBusName) != null ||
                (rootBusName != portName && parentCell.getPort(portName) != null)) {
            portName += getUniqueSuffix();
        }
        return parentCell.createPort(portName, dir, width);
    }

    /**
     * Connects two existing logical port insts together by creating new ports and nets on all cells
     * instantiated between their levels of hierarchy.  It assumes the netlist cells involved only
     * have one instance (does not differentiate cells when creating the ports).  This assumption
     * can be enforced by calling {@link #uniqueifyNetlist(Design)}. If the src or snk
     * port insts do net have nets connected, it will create them and connect them in their parent
     * cell definition.
     * @param src The logical port inst driver or source
     * @param snk The logical port inst sink
     * @param newName A unique name to be used in creating the ports and nets
     */
    public static void connectPortInstsThruHier(EDIFHierPortInst src, EDIFHierPortInst snk,
            String newName) {
        EDIFHierCellInst commonAncestor =
                src.getHierarchicalInst().getCommonAncestor(snk.getHierarchicalInst());
        EDIFHierPortInst finalSrc = src;
        EDIFHierPortInst finalSnk = snk;
        boolean createdSrcNet = false;
        // Trace existing connections or make new connections between src and snk to the common
        // ancestor.
        for (EDIFHierPortInst hierPortInst : new EDIFHierPortInst[] {src, snk}) {
            EDIFHierCellInst hierParentInst = hierPortInst.getHierarchicalInst();
            EDIFNet currNet = hierPortInst.getNet();
            if (currNet == null && !(hierParentInst.equals(commonAncestor) && hierPortInst == snk)) {
                // When operating on the snk pin, we've found that it is not connected to a net.  Instead of trying to
                // connect through the hierarchy starting at the snk, start from the common ancestor and leverage
                // as much existing connectivity as possible.
                List<EDIFCellInst> sinkHier = snk.getFullHierarchicalInst().getFullHierarchy();
                boolean foundPath = false;
                do {
                    foundPath = false;
                    EDIFCellInst nextTargetInst = sinkHier.get(commonAncestor.getDepth());
                    EDIFNet evalNet = finalSrc.getPortInst().getNet();
                    if (evalNet != null) {
                        for (EDIFPortInst pi : evalNet.getEDIFPortInstList()) {
                            // Does this net connect to the next instance in hierarchy?
                            if (pi.getCellInst() == nextTargetInst) {
                                EDIFNet internalNet = pi.getInternalNet();
                                // Is there a net inside the cell we can follow?
                                if (internalNet != null) {
                                    EDIFHierCellInst nextParent = finalSrc.getHierarchicalInst()
                                            .getChild(pi.getCellInst());
                                    EDIFPortInst internalPortInst = internalNet.getPortInst(null, pi.getName());
                                    finalSrc = new EDIFHierPortInst(nextParent, internalPortInst);
                                    commonAncestor = commonAncestor.getChild(pi.getCellInst());
                                    foundPath = true;
                                    break;
                                }
                            }
                        }
                    }
                } while (foundPath && snk.getHierarchicalInst().getInst() != finalSrc.getHierarchicalInst().getInst());
                if (!foundPath) {
                    // We still need to create some ports to get us to the end
                    if (hierPortInst == src)
                        createdSrcNet = true;
                    currNet = createUniqueNet(hierParentInst.getCellType(), newName);
                    currNet.addPortInst(hierPortInst.getPortInst());
                }
            }

            while (hierParentInst.getInst() != commonAncestor.getInst()) {
                EDIFPortInst exitPath = currNet.getTopLevelPortInst();
                EDIFPortInst outerPortInst = null;
                if (exitPath != null) {
                    // Follow existing connection to parent instance
                    outerPortInst = hierParentInst.getInst().getPortInst(exitPath.getName());
                    if (outerPortInst == null) {
                        outerPortInst = new EDIFPortInst(exitPath.getPort(), null,
                                exitPath.getIndex(), hierParentInst.getInst());
                    }
                    hierParentInst = hierParentInst.getParent();
                    currNet = outerPortInst.getNet();
                    if (currNet == null) {
                        currNet = createUniqueNet(hierParentInst.getCellType(), newName);
                        currNet.addPortInst(outerPortInst);
                    }
                } else {
                    // The sink already has a net and port instance on the target instance, we just
                    // need to connect to it inside the cell.
                    if (hierPortInst == src && snk.getFullHierarchicalInst().equals(hierParentInst)) {
                        currNet.createPortInst(snk.getPortInst().getPort());
                        return;
                    }
                    // no port to the parent cell above exists, create one
                    EDIFCell cellType = hierParentInst.getCellType();
                    EDIFPort port = createUniquePort(cellType, newName,
                            hierPortInst == src ? EDIFDirection.OUTPUT : EDIFDirection.INPUT, 1);

                    currNet.createPortInst(port);
                    EDIFCellInst prevInst = hierParentInst.getInst();
                    hierParentInst = hierParentInst.getParent();
                    if (hierParentInst.equals(commonAncestor) && hierPortInst == snk) {
                        // We don't need to create another net, just connect to the src's net
                        currNet = finalSrc.getNet();
                    } else {
                        currNet = createUniqueNet(hierParentInst.getCellType(), port.getName());
                    }
                    outerPortInst = currNet.createPortInst(port, prevInst);
                }
                EDIFHierPortInst currPortInst = new EDIFHierPortInst(hierParentInst, outerPortInst);
                if (hierPortInst == src) {
                    finalSrc = currPortInst;
                } else {
                    finalSnk = currPortInst;
                }
            }
        }
        // Disconnect sink from existing net if connected
        EDIFNet snkNet = finalSnk.getNet();
        if (snkNet != null) {
            if (createdSrcNet && snkNet.getParentCell() == finalSrc.getNet().getParentCell()) {
                // Let's delete the net we created and use the existing snkNet instead
                EDIFNet net = finalSrc.getNet();
                if (snkNet != net) {
                    net.getParentCell().removeNet(net);
                    snkNet.addPortInst(finalSrc.getPortInst());
                }
                return;
            } else {
                snkNet.removePortInst(finalSnk.getPortInst());
            }
        }
        // Make final connection in the common ancestor instance
        finalSrc.getNet().addPortInst(finalSnk.getPortInst());
    }

    /**
     * Connects an existing logical net and logical port inst together by creating new ports and
     * nets on all cells instantiated between their levels of hierarchy.  This is a helper method
     * on top of {@link #connectPortInstsThruHier(EDIFHierPortInst,EDIFHierPortInst,String)}
     * for identifying the correct source and sink pins to be provided to this, according to
     * whether the provided pin argument is an input or output pin (and finding the appropriate
     * pin from the provided net).
     * @param net The logical net
     * @param pin The logical port inst source or sink
     * @param newName A unique name to be used in creating the ports and nets
     */
    public static void connectPortInstsThruHier(EDIFHierNet net, EDIFHierPortInst pin, String newName) {
        boolean pinIsOutput = pin.isOutput();
        EDIFHierPortInst src = pinIsOutput ? pin : null;
        EDIFHierPortInst snk = pinIsOutput ? null : pin;
        EDIFHierCellInst netInst = net.getHierarchicalInst();
        EDIFCell cellType = netInst.getCellType();
        
        // Search for existing pins on the provided net, use those preferentially
        if (pinIsOutput) {
            for (EDIFHierPortInst pi : net.getPortInsts()) {
                if (pi.isInput() || (pi.getPortInst().isTopLevelPort() && pi.isOutput())) {
                    snk = pi;
                    break;
                }
            }            
        } else {
            List<EDIFHierPortInst> sources = net.getSourcePortInsts(true);
            if (!sources.isEmpty()) {
                src = sources.get(0);
            }
        }

        // If the net has no usable pins for reference, determine the hierarchical
        // direction the connection will need to be made and create a pin pre-emptively
        outer: if (snk == null || src == null) {
            EDIFHierCellInst pinInst = pin.getFullHierarchicalInst();
            EDIFHierCellInst commonAncestor = pinInst.getCommonAncestor(netInst);
            
            if (netInst.getDepth() > commonAncestor.getDepth()) {
                // Check if we can move towards the commonAncestor
                EDIFHierNet currNet = net;
                // follow net connectivity until we either reach the commonAncestor or can't go any higher
                while (!currNet.getHierarchicalInst().equals(commonAncestor)) {
                    EDIFPortInst topPortInst = currNet.getNet().getTopLevelPortInst();
                    if (topPortInst == null) {
                        // No existing connectivity exists, we should create a new port and PortInst on the appropriate
                        // instance connected to the requested net
                        EDIFHierCellInst targetInst = currNet.getHierarchicalInst();
                        EDIFPort port = createUniquePort(targetInst.getCellType(), newName, pinIsOutput ? EDIFDirection.INPUT : EDIFDirection.OUTPUT, 1);
                        EDIFHierPortInst otherPin = new EDIFHierPortInst(targetInst, currNet.getNet().createPortInst(port));
                        if (pinIsOutput) {
                            snk = otherPin;
                        } else {
                            src = otherPin;
                        }
                        break outer;
                    }
                    EDIFPortInst outerPortInst = currNet.getParentInst().getPortInst(topPortInst.getName());
                    currNet = new EDIFHierNet(currNet.getHierarchicalInst().getParent(), outerPortInst.getNet());
                }
            } 
            if (commonAncestor.getDepth() <= pinInst.getDepth()) {
                // Need to go up in hierarchy ...
                EDIFHierNet currNet;
                if (pinIsOutput) {
                    // ... from the target pin (which is an input)
                    currNet = pin.getHierarchicalNet();
                } else {
                    // ... from the target net (which is to be treated as the output)
                    currNet = net;
                }
                // follow net connectivity until we either reach the commonAncestor or can't go any higher
                do {
                    EDIFPortInst topPortInst = currNet == null ? null : currNet.getNet().getTopLevelPortInst();
                    if (topPortInst == null) {
                        // No existing connectivity exists, we should create a new port and PortInst on the appropriate
                        // instance connected to the requested net
                        EDIFHierCellInst targetInst = commonAncestor.getChild(pinInst.getFullHierarchy().get(commonAncestor.getDepth()));
                        EDIFPort port = createUniquePort(targetInst.getCellType(), newName, pinIsOutput ? EDIFDirection.OUTPUT : EDIFDirection.INPUT, 1);
                        EDIFHierPortInst otherPin = new EDIFHierPortInst(commonAncestor, net.getNet().createPortInst(port, targetInst.getInst()));
                        if (pinIsOutput) { 
                            snk = otherPin;
                        } else {
                            src = new EDIFHierPortInst(targetInst, new EDIFPortInst(port, null));
                        }
                        break;
                    }
                    EDIFPortInst outerPortInst = currNet.getParentInst().getPortInst(topPortInst.getName());
                    if (outerPortInst.getNet() == null) {
                        break;
                    }
                    currNet = new EDIFHierNet(currNet.getHierarchicalInst().getParent(), outerPortInst.getNet());
                    EDIFHierPortInst otherPin = new EDIFHierPortInst(currNet.getHierarchicalInst(), outerPortInst);
                    if (pinIsOutput) {
                        snk = otherPin;
                    } else {
                        src = otherPin;
                    }
                } while (!currNet.getHierarchicalInst().equals(commonAncestor));
            } else {
                // Create one if one doesn't exist
                EDIFPort port = createUniquePort(cellType, newName, pinIsOutput ? EDIFDirection.INPUT : EDIFDirection.OUTPUT, 1);
                net.getNet().createPortInst(port);

                // EDIFTools.connectPortInstsThruHier() does not support top-level portInsts;
                // need to create a port inst in the parent cell too
                EDIFNet upperNet = createUniqueNet(netInst.getParent().getCellType(), port.getName());
                EDIFHierPortInst otherPin = new EDIFHierPortInst(netInst.getParent(), upperNet.createPortInst(port, netInst.getInst()));
                if (pinIsOutput) { 
                    snk = otherPin;
                } else {
                    src = otherPin;
                }
            }
        }
        connectPortInstsThruHier(src, snk, newName);
    }

    /**
     * Specialized function to connect a debug port within an EDIF netlist.
     * @param topPortNet The top-level net that connects to the debug core's input port.
     * @param routedNetName The name of the routed net who's source is the net we need to connect to
     * @param newPortName The name of the port to be added at each level of hierarchy
     * @param parentInst The instance where topPortNet resides
     * @param netlist The current netlist
     * @param instMap The map of the design created by {@link EDIFTools#generateCellInstMap(EDIFCellInst)}
     */
    public static void connectDebugProbe(EDIFNet topPortNet, String routedNetName, String newPortName,
            EDIFHierCellInst parentInst, EDIFNetlist netlist, HashMap<EDIFCell, ArrayList<EDIFCellInst>> instMap) {
        EDIFNet currNet = topPortNet;
        String currParentName = parentInst.getParent().getFullHierarchicalInstName();
        EDIFCellInst currInst = parentInst.getInst();
        // Need to check if we need to move up levels of hierarchy before we move down
        while (!routedNetName.startsWith(currParentName)) {
            EDIFPort port = currInst.getCellType().createPort(newPortName, EDIFDirection.INPUT, 1);
            currNet.createPortInst(port);
            EDIFCellInst prevInst = currInst;
            currParentName = currParentName.substring(0, currParentName.lastIndexOf(EDIFTools.EDIF_HIER_SEP));
            currInst = netlist.getCellInstFromHierName(currParentName);
            currNet = new EDIFNet(newPortName, currInst.getCellType());
            currNet.createPortInst(newPortName, prevInst);
        }

        String[] parts = routedNetName.split(EDIFTools.EDIF_HIER_SEP);
        int idx = 0;
        if (!netlist.getTopCell().equals(currInst.getCellType())) {
            while ( idx < parts.length) {
                if (parts[idx++].equals(currInst.getName())) {
                    break;
                }
            }
            if (idx == parts.length) {
                throw new RuntimeException("ERROR: Couldn't find instance " +
                    currInst.getName() + " from routed net name " + routedNetName);
            }
        }

        for (int i=idx; i <= parts.length-2; i++) {
            currInst = currInst.getCellType().getCellInst(parts[i]);
            EDIFCell type = currInst.getCellType();
            if (instMap != null && instMap.get(type).size() > 1) {
                // TODO Replicate cell type and create new
            }
            EDIFPort newPort = currInst.getCellType().createPort(newPortName, EDIFDirection.OUTPUT, 1);
            EDIFPortInst portInst = new EDIFPortInst(newPort, currNet, currInst);
            currNet.addPortInst(portInst);
            if (i == parts.length-2) {
                EDIFNet targetNet = currInst.getCellType().getNet(parts[parts.length-1]);
                targetNet.createPortInst(newPort);
            } else {
                EDIFNet childNet = new EDIFNet(topPortNet.getName(), currInst.getCellType());
                childNet.createPortInst(newPort);
                currNet = childNet;
            }
        }
    }

    /**
     * Specialized function to add enable a debug probe connection.  Adds the external port on the debug core and
     * a new net at the top level that can be used to as a sink to probe a user signal.  Used in conjunction with
     * EDIFTools.connectDebugProbe(...)
     * @param newDebugNetName Name of the debug net to add at the top level
     * @param topCell Name of the cell where the net and port should exists
     * @param currPort The existing port on the debug core
     * @param debugCore The instance of the debug core already within the EDIF design
     * @return The newly created net with the debug port attached.
     */
    public static EDIFNet addDebugPortAndNet(String newDebugNetName, EDIFCell topCell, EDIFPortInst currPort, EDIFCellInst debugCore) {
        // Create a new net for the port connection
        EDIFNet net = topCell.createNet(newDebugNetName);
        addDebugPort(net,topCell,currPort,debugCore);
        return net;
    }

    /**
     * Adds the debug port to the debug core in the EDIF netlist
     * @param net The net to which the port should connect
     * @param topCell The top cell which has the debug core as a subcell
     * @param currPort The current port to add
     * @param debugCore The debug core
     * @return The newly created {@link EDIFPortInst}
     */
    public static EDIFPortInst addDebugPort(EDIFNet net, EDIFCell topCell, EDIFPortInst currPort, EDIFCellInst debugCore) {
        // Add the actual external port on the debug core
        EDIFPortInst probeInput = new EDIFPortInst(currPort.getPort(), net, debugCore);
        net.addPortInst(probeInput);


        if (topCell.getNet(net) == null) {
            topCell.addNet(net);
        }
        return probeInput;
    }

    /**
     * Creates and/or gets the static net (GND/VCC) in the specified cell.
     * @param type The type of net to get or create
     * @param cellInst The hier cell inst that should have a static net
     * @param netlist The netlist of interest
     * @return An existing or newly created static hier net for the cell provided.
     */
    public static EDIFHierNet getStaticNet(NetType type, EDIFHierCellInst cellInst, EDIFNetlist netlist) {
        EDIFNet n = getStaticNet(type, cellInst.getCellType(), netlist);
        return new EDIFHierNet(cellInst, n);
    }

    /**
     * Creates and/or gets the static net (GND/VCC) in the specified cell.
     * @param type The type of net to get or create
     * @param cell The cell that should have a static net
     * @param netlist The netlist of interest
     * @return An existing or newly created static net for the cell provided.
     */
    public static EDIFNet getStaticNet(NetType type, EDIFCell cell, EDIFNetlist netlist) {
        return getStaticNet(type, cell, netlist, type == NetType.GND ? LOGICAL_GND_NET_NAME : LOGICAL_VCC_NET_NAME);
    }

    /**
     * Creates and/or gets the static net (GND/VCC) in the specified cell.
     * @param type The type of net to get or create
     * @param cell The cell that should have a static net
     * @param netlist The netlist of interest
     * @param netName If the net is to be created, what is the desired name
     * @return An existing or newly created static net for the cell provided.
     */
    public static EDIFNet getStaticNet(NetType type, EDIFCell cell, EDIFNetlist netlist, String netName) {
        String staticTypeName = type.toString();
        EDIFCellInst staticInst = cell.getCellInst(staticTypeName);
        String portName = staticTypeName.equals("GND") ? "G" : "P";
        if (staticInst == null) {
            EDIFCell staticSrc = netlist.getLibrary(EDIF_LIBRARY_HDI_PRIMITIVES_NAME).getCell(staticTypeName);
            if (staticSrc == null) {
                staticSrc = new EDIFCell(netlist.getLibrary(EDIF_LIBRARY_HDI_PRIMITIVES_NAME), staticTypeName);
                staticSrc.addPort(new EDIFPort(portName, EDIFDirection.OUTPUT,1));
            }
            staticInst = new EDIFCellInst(staticTypeName, staticSrc, cell);
            cell.addCellInst(staticInst);
        }
        EDIFPortInst outputPortInst = staticInst.getPortInst(portName);
        if (outputPortInst == null) {
            outputPortInst = new EDIFPortInst(staticInst.getPort(portName),null,staticInst);
        }
        EDIFNet staticNet = outputPortInst.getNet();

        if (staticNet == null) {
            String debugNetName = netName;
            staticNet = cell.getNet(debugNetName);
            if (staticNet == null) {
                staticNet = cell.createNet(debugNetName);
            }
            staticNet.addPortInst(outputPortInst);
        }
        return staticNet;
    }

    public static void main(String[] args) {
        CodePerfTracker t = new CodePerfTracker("EDIF Read", true);
        String edifFileName = args[0];
        EDIFNetlist edif;

        t.start("Read EDIF from ASCII EDIF file");
        edif = EDIFTools.loadEDIFFile(edifFileName);
        t.stop();
        t.start("Write EDIF file");
        EDIFTools.writeEDIFFile(edifFileName.replace(".edf", "_out.edf"), edif, "partname");
        t.stop().printSummary();
    }

    protected static Path getTempEDIFFile() {
        String tempEDIFFileName = ".temp_edif_" + FileTools.getUniqueProcessAndHostID() + ".edf";
        return FileSystems.getDefault().getPath(tempEDIFFileName);
    }

    public static EDIFNetlist loadEDIFStream(InputStream is, long size) throws IOException {
        try (EDIFParser p = new EDIFParser(is)) {
            return p.parseEDIFNetlist();
        }
    }

    public static EDIFNetlist loadEDIFFile(Path fileName) {
        return loadEDIFFile(fileName, Integer.MAX_VALUE);
    }

    public static EDIFNetlist loadEDIFFile(Path fileName, int maxThreads) {
        try {
            final long size = Files.size(fileName);
            if (ParallelEDIFParser.calcThreads(size, maxThreads, fileName.toString().endsWith(".gz")) > 1) {
                try (ParallelEDIFParser p = new ParallelEDIFParser(fileName)) {
                    return p.parseEDIFNetlist();
                }
            } else {
                try (EDIFParser p = new EDIFParser(fileName)) {
                    return p.parseEDIFNetlist();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("ERROR: Couldn't read file : " + fileName, e);
        }
    }

    public static EDIFNetlist loadEDIFFile(String fileName) {
        return loadEDIFFile(Paths.get(fileName), Integer.MAX_VALUE);
    }

    public static void ensureCorrectPartInEDIF(EDIFNetlist edif, String partName) {
        EDIFDesign design = edif.getDesign();
        if (design == null) {
            design = new EDIFDesign(edif.getName());
            edif.setDesign(design);
        }
        Map<String, EDIFPropertyValue> propMap = design.getPropertiesMap();
        if (propMap == null || propMap.size() == 0) {
            edif.getDesign().addProperty(EDIF_PART_PROP, partName);
            return;
        }
        boolean modified = false;
        for (Entry<String,EDIFPropertyValue> p : propMap.entrySet()) {
            String val = p.getValue().toString();
            if (val.contains("intex") || val.contains("irtex")) {
                EDIFPropertyValue v = new EDIFPropertyValue();
                v.setType(EDIFValueType.STRING);
                v.setValue(partName);
                p.setValue(v);
                modified = true;
                break;
            }
        }
        if (!modified) {
            edif.getDesign().removeProperty(EDIF_PART_PROP.toLowerCase());
            edif.getDesign().addProperty(EDIF_PART_PROP, partName);
        }
    }

    public static EDIFNetlist readEdifFile(Path edifFileName) {
        return readEdifFile(edifFileName, Integer.MAX_VALUE);
    }

    public static List<String> getEDNFiles(Path parent) {
        String edifDirectoryName = parent.toAbsolutePath().toString();
        File origDir = new File(edifDirectoryName);
        String[] ednFiles = origDir.list(FileTools.getEDNFilenameFilter());
        if (ednFiles != null && ednFiles.length > 0) {
            edifDirectoryName = edifDirectoryName + File.separator;
            for (int i = 0; i < ednFiles.length; i++) {
                ednFiles[i] = edifDirectoryName + ednFiles[i];
            }
            return new ArrayList<>(Arrays.asList(ednFiles));
        }
        return Collections.emptyList();
    }

    public static Path getEDIFParentDir(Path edifFileName) {
        Path parent = edifFileName == null ? null : edifFileName.getParent();
        return parent == null ? Paths.get(System.getProperty("user.dir")) : parent;
    }

    public static EDIFNetlist readEdifFile(Path edifFileName, int maxThreads) {
        Path parent = getEDIFParentDir(edifFileName);
        if (RW_ENABLE_EDIF_BINARY_CACHING) {
            Path bedif = parent.resolve(
                            edifFileName.getFileName().toString().replace(".edf", ".bedf"));
            if (Files.exists(bedif) && FileTools.isFileNewer(bedif, edifFileName)) {
                EDIFNetlist netlist = null;
                try {
                    netlist = BinaryEDIFReader.readBinaryEDIF(bedif);
                    return netlist;
                } catch (Exception e) {
                    System.out.println("WARNING: Unable to read Binary EDIF: " + bedif.toString()
                            + ", falling back to reading EDIF: " + edifFileName.toString());
                }
            }
        }
        EDIFNetlist edif = loadEDIFFile(edifFileName, maxThreads);
        edif.setOrigDirectory(parent.toAbsolutePath().toString());
        edif.setEncryptedCells(getEDNFiles(parent));
        if (RW_ENABLE_EDIF_BINARY_CACHING) {
            Path bedif = parent.resolve(
                    edifFileName.getFileName().toString().replace(".edf", ".bedf"));
            try {
                BinaryEDIFWriter.writeBinaryEDIF(bedif, edif);
            }
            catch (Exception e) {
                System.out.println("INFO: Unable to write Binary EDIF file: " + bedif.toString());
            }
        }
        return edif;
    }

    public static EDIFNetlist readEdifFile(String edifFileName) {
        return readEdifFile(Paths.get(edifFileName), Integer.MAX_VALUE);
    }

    public static void writeEDIFFile(Path fileName, EDIFNetlist edif, String partName) {
        ensureCorrectPartInEDIF(edif, partName);
        edif.exportEDIF(fileName);
    }

    public static void writeEDIFFile(String fileName, EDIFNetlist edif, String partName) {
        writeEDIFFile(Paths.get(fileName), edif, partName);
    }

    public static void writeEDIFFile(OutputStream out, EDIFNetlist edif, String partName) {
        writeEDIFFile(out, (Path) null, edif, partName);
    }


    /**
     * Write out EDIF to a stream.  Also checks if netlist has potential encrypted cells and
     * creates a Tcl script to help re-import design into Vivado intact.
     * @param out The output stream
     * @param dcpFileName The name of the DCP file associated with this netlist
     * @param edif The netlist of the design
     * @param partName The target part for this design
     */
    public static void writeEDIFFile(OutputStream out, Path dcpFileName, EDIFNetlist edif,
                                        String partName) {
        try {
            ensureCorrectPartInEDIF(edif, partName);
            edif.exportEDIF(out);
            if (dcpFileName != null && edif.getEncryptedCells() != null) {
                if (edif.getEncryptedCells().size() > 0) {
                    writeTclLoadScriptForPartialEncryptedDesigns(edif, dcpFileName, partName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write out EDIF to a stream.  Also checks if netlist has potential encrypted cells and
     * creates a Tcl script to help re-import design into Vivado intact.
     * @param out The output stream
     * @param dcpFileName The name of the DCP file associated with this netlist
     * @param edif The netlist of the design
     * @param partName The target part for this design
     */
    public static void writeEDIFFile(OutputStream out, String dcpFileName, EDIFNetlist edif,
                                     String partName) {
        if (dcpFileName == null) {
            writeEDIFFile(out, (Path) null, edif, partName);
        } else {
            writeEDIFFile(out, Paths.get(dcpFileName), edif, partName);
        }
    }

    public static void writeTclLoadScriptForPartialEncryptedDesigns(EDIFNetlist edif,
                                                            Path dcpFileName, String partName) {
        ArrayList<String> lines = new ArrayList<String>();
        for (String cellName : edif.getEncryptedCells()) {
            if (cellName.endsWith(".edn") || cellName.endsWith(".edf")) {
                lines.add(EDIFNetlist.READ_EDIF_CMD + " {" + cellName + "}");
            } else if (cellName.endsWith(".dcp")) {
                lines.add("read_checkpoint {" + cellName + "}");
            } else if (cellName.endsWith(".v")) {
                lines.add("read_verilog {" + cellName + "}");
            } else {
                System.err.println("ERROR: Unrecognized or missing extension for encrypted cell file: " + cellName);
            }
        }
        Path pathDCPFileName = dcpFileName.toAbsolutePath();

        lines.add("read_checkpoint {" + pathDCPFileName + "}");
        lines.add("set_property top "+edif.getName()+" [current_fileset]");
        lines.add("link_design -part " + partName);
        Path tclFileName = FileTools.replaceExtension(pathDCPFileName, LOAD_TCL_SUFFIX);
        try {
            Files.write(tclFileName, lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("INFO: Design Checkpoint \'"+ pathDCPFileName + "\'"
                + "\n      may contain encrypted cells. To correctly load the design into Vivado, "
                + "\n      please source this Tcl script to open the checkpoint: "
                + "\n\n      source " + tclFileName + "\n");
    }

    public static EDIFNetlist readEdifFromZipFile(String zipFileName) throws IOException {
        Pair<InputStream,Long> p = FileTools.getInputStreamFromZipFile(zipFileName, ".edf");
        InputStream is = p.getFirst();
        long size = p.getSecond();
        return EDIFTools.loadEDIFStream(is, size);
    }

    public static EDIFNetlist createNewNetlist(String topName) {
        return createNewNetlist(topName, true);
    }

    public static EDIFNetlist createNewNetlist(String topName, boolean addINV) {
        EDIFNetlist n = new EDIFNetlist(topName);
        n.generateBuildComments();
        EDIFLibrary primLib = n.addLibrary(new EDIFLibrary(EDIFTools.EDIF_LIBRARY_HDI_PRIMITIVES_NAME));
        if (addINV) primLib.addCell(Design.getUnisimCell(Unisim.INV));
        EDIFLibrary workLib = n.addLibrary(new EDIFLibrary(EDIFTools.EDIF_LIBRARY_WORK_NAME));
        EDIFCell top = new EDIFCell(workLib, topName);
        EDIFDesign eDesign = new EDIFDesign(topName);
        n.setDesign(eDesign);
        eDesign.setTopCell(top);
        return n;
    }

    /**
     * Creates a new netlist from an existing EDIFCellInst in a netlist.  This operation does not
     * modify the source netlist.
     * @param cellInst The new top cell/top cell inst in the netlist.
     * @return The newly created netlist from the provided cell inst.
     */
    public static EDIFNetlist createNewNetlist(EDIFCellInst cellInst) {
        EDIFNetlist n = new EDIFNetlist(cellInst.getName());
        n.generateBuildComments();
        EDIFDesign eDesign = new EDIFDesign(cellInst.getName());
        n.setDesign(eDesign);
        EDIFCell topCell = cellInst.getCellType();
        n.copyCellAndSubCells(topCell);
        eDesign.setTopCell(n.getLibrary(topCell.getLibrary().getName()).getCell(topCell.getName()));
        // If we have more than the primitives and work library, lets order the libraries in the same way
        // from the source netlist
        if (n.getLibraries().size() > 2) {
            // Put libraries in the same order as source netlist
            Map<String, EDIFLibrary> libs = new HashMap<>();
            for (EDIFLibrary lib : n.getLibraries()) {
                libs.put(lib.getName(), lib);
            }
            n.getLibrariesMap().clear();
            for (String libName : cellInst.getCellType().getNetlist().getLibrariesMap().keySet()) {
                EDIFLibrary lib = libs.get(libName);
                if (lib != null) {
                    n.getLibrariesMap().put(libName, lib);
                }
            }
        }
        return n;
    }

    public static int[] bitBlastBus(String busSuffix) {
        int colon = busSuffix.indexOf(':');
        int left = Integer.parseInt(busSuffix.substring(1,colon));
        int right = Integer.parseInt(busSuffix.substring(colon+1,busSuffix.length()-1));
        int inc = left > right ? -1 : 1;
        int[] idxs = new int[Math.abs(left-right) + 1];
        int ii = left;
        for (int i=0; i < idxs.length; i++ ) {
            idxs[i] = ii;
            ii += inc;
        }
        return idxs;
    }

    /**
     * Bit blasts the shorthand bus name (ex: {@code "data[0:2]" --> ["data0", "data1", "data2"]})
     * @param bussedSignal The bussed name with bracketed indices
     * @return A fully expanded array of strings names or the original name if no brackets are present
     */
    public static String[] bitBlast(String bussedSignal) {
        int lastLeftBracket = bussedSignal.lastIndexOf('[');
        int colon = bussedSignal.lastIndexOf(':');
        if (lastLeftBracket == -1 || colon == -1) return new String[]{bussedSignal};
        int[] indices = EDIFTools.bitBlastBus(bussedSignal.substring(lastLeftBracket));
        String[] signals = new String[indices.length];
        String base = bussedSignal.substring(0, lastLeftBracket);
        for (int i=0; i < indices.length; i++) {
            signals[i] = base +"[" + indices[i] + "]";
        }
        return signals;
    }

    /**
     * Creates a top level port (if it doesn't already exist) and an array of corresponding {@link EDIFPortInst}s
     * to be used to connect to an array of EDIFNets
     * @param parent Top level cell to which the port should be created
     * @param name Root name of the port to create and corresponding {@link EDIFPortInst}s (no brackets)
     * @param dir Direction of the port.
     * @param width Width of the port
     * @return An array [0:width-1] of port refs.
     */
    public static EDIFPortInst[] createPortInsts(EDIFCell parent, String name, EDIFDirection dir, int width) {
        String portName = name + (width==1 ? "" : "[" + (width-1) +":0]");
        EDIFPort port = parent.createPort(portName, dir, width);
        EDIFPortInst[] portInsts = new EDIFPortInst[width];
        for (int i=0; i < width; i++) {
            portInsts[i] = new EDIFPortInst(port,null,i);
        }
        return portInsts;
    }

    /**
     * Creates an array of {@link EDIFPortInst}s from an existing port on an {@link EDIFCellInst}.
     * @param name Root name of the port (no brackets)
     * @param dir Direction of the port
     * @param width Width of the port
     * @param eci The existing cell instance with the port to create the {@link EDIFPortInst}s.
     * @return The newly created array of {@link EDIFPortInst}s corresponding the port and {@link EDIFCellInst}.
     */
    public static EDIFPortInst[] createPortInsts(String name, EDIFDirection dir, int width, EDIFCellInst eci) {
        EDIFPort resultPort = eci.getPort(name);
        EDIFPortInst[] resultPortInsts = new EDIFPortInst[width];
        for (int i=0; i < width; i++) {
            resultPortInsts[i] = new EDIFPortInst(resultPort,null,i,eci);
        }
        return resultPortInsts;
    }


    /**
     * Traverse all connected EDIFNets to find all leaf sink portrefs as part of the physical net.
     * @param startingInput Search begins at this portref and searches below in inner levels of hierarchy.  Doesn't search backwards.
     * @return The list of all leaf cell pin (portrefs) found below the provided starting input pin. Or null if the portref provided is not an input.
     */
    public static ArrayList<EDIFHierPortInst> findSinks(EDIFHierPortInst startingInput) {
        if (!startingInput.isInput()) return null;
        Queue<EDIFHierPortInst> q = new LinkedList<>();
        q.add(startingInput);
        ArrayList<EDIFHierPortInst> sinks = new ArrayList<>();
        while (!q.isEmpty()) {
            EDIFHierPortInst curr = q.poll();
            final EDIFHierCellInst cellInst = curr.getHierarchicalInst().getChild(curr.getPortInst().getCellInst());

            EDIFHierNet internalNet = curr.getInternalNet();
            for (EDIFPortInst p : internalNet.getNet().getPortInsts()) {
                if (!p.isInput()) continue;
                if (p.getCellInst() == null) continue;
                EDIFHierPortInst newPortInst = new EDIFHierPortInst(cellInst, p);
                if (p.getCellInst().getCellType().isPrimitive()) {
                    sinks.add(newPortInst);
                } else {
                    q.add(newPortInst);
                }
            }
        }

        return sinks;
    }

    /**
     * Creates (or gets if it already exists) a top level port instance.
     * @param d Current design with the netlist
     * @param name Name of the top level port
     * @param dir The desired port directionality
     * @return The top level port instance (created or retrieved) from the top cell.
     */
    public static EDIFPortInst createTopLevelPortInst(Design d, String name, PinType dir) {
        EDIFNetlist n = d.getNetlist();
        EDIFPort port = n.getTopCell().getPort(name);
        if (port == null && name.contains("[") && name.contains("]")) {
            // check if this is a part of a bus
            port = n.getTopCell().getPort(getRootBusName(name, true));
        }
        if (port == null) {
            port = d.getTopEDIFCell().createPort(name, EDIFDirection.getDir(dir), 1);
        }
        EDIFPortInst pr = n.getTopCellInst().getPortInst(name);
        if (pr == null) {
            int idx = -1;
            if (port.isBus()) {
                idx = port.getPortIndexFromNameIndex(getPortIndexFromName(name));
            }
            pr = new EDIFPortInst(port, null, idx);
        }
        return pr;
    }

    /**
     * Creates (or gets) a top level port by the specified name.  It will
     * also ensure that the port is connected to the specified {@link EDIFCellInst}
     * which should be part of an IBUF or OBUF.
     * @param d Current design with netlist
     * @param name Name of the port (and net if none exists)
     * @param i IBUF (or INBUF in UltraScale devices) or OBUF instances
     * @param dir Direction of the pin on the IBUF/INBUF/OBUF instance to connect.
     */
    public static void addTopLevelPort(Design d, String name, EDIFCellInst i, PinType dir) {
        EDIFPortInst pr = createTopLevelPortInst(d, name, dir);
        EDIFNet portNet = pr.getNet();
        if (portNet == null) {
            portNet = d.getTopEDIFCell().getNet(name);
        }
        if (portNet == null) {
            portNet = d.getTopEDIFCell().createNet(name);
        }
        String portName = dir == PinType.IN ? "I" : "O";
        EDIFPortInst bufPortInst = portNet.getPortInst(i, portName);
        if (bufPortInst == null)
            portNet.createPortInst(portName, i);
        if (pr.getNet() == null)
            portNet.addPortInst(pr);
    }

    public static List<String> getMacroLeafCellNames(EDIFCell cell) {
        Queue<EDIFHierCellInst> q = new LinkedList<>();
        for (EDIFCellInst inst : cell.getCellInsts()) {
            q.add(EDIFHierCellInst.createRelative(inst));
        }
        ArrayList<String> leafCells = new ArrayList<String>();
        while (!q.isEmpty()) {
            EDIFHierCellInst inst = q.remove();
            if (inst.getCellType().isPrimitive()) {
                leafCells.add(inst.getFullHierarchicalInstName());
            } else {
                for (EDIFCellInst i : inst.getCellType().getCellInsts()) {
                    q.add(inst.getChild(i));
                }
            }
        }
        return leafCells;
    }

    /**
     * Creates a map of all cells in the netlist and a mapping to a list of all the hierarchical
     * instances of that cell.  Helpful in finding multiple instances of the same cell.
     * @param netlist The netlist to build the map from.
     * @return The populated map of cells to list of hierarchical instances.
     */
    public static Map<EDIFLibrary, Map<EDIFCell, List<EDIFHierCellInst>>>
                            createCellInstanceMap(EDIFNetlist netlist) {
        Map<EDIFLibrary, Map<EDIFCell, List<EDIFHierCellInst>>> cellInstMap =
                new HashMap<EDIFLibrary, Map<EDIFCell, List<EDIFHierCellInst>>>();

        Queue<EDIFHierCellInst> toProcess = new LinkedList<EDIFHierCellInst>();
        netlist.getTopHierCellInst().addChildren(toProcess);

        while (!toProcess.isEmpty()) {
            EDIFHierCellInst curr = toProcess.poll();

            EDIFLibrary lib = curr.getCellType().getLibrary();
            Map<EDIFCell, List<EDIFHierCellInst>> cellMap = cellInstMap.computeIfAbsent(lib, k -> new HashMap<>());

            EDIFCell cell = curr.getCellType();
            List<EDIFHierCellInst> insts = cellMap.computeIfAbsent(cell, k -> new ArrayList<>());
            insts.add(curr);

            if (curr.getInst().getCellType().getCellInsts() == null) {
                continue;
            }
            for (EDIFCellInst i : curr.getInst().getCellType().getCellInsts()) {
                toProcess.add(curr.getChild(i));
            }
        }

        return cellInstMap;
    }

    /**
     * Duplicates EDIFCells such that each EDIFCellInst only instantiates an EDIFCell once
     * (except primitives and macros).  It also updates references throughout the logical and
     * physical netlist so all references are self-consistent.  This transformation is useful when
     * performing netlist manipulations such as adding/removing cells, ports or nets within a design.
     * @param design The design containing the netlist to uniqueify.
     * @return True if uniqueification necessary and netlist was modified, False if not necessary and netlist was not modified. Null if indeterminable.
     */
    public static Boolean uniqueifyNetlist(Design design) {
        if (design.getModuleInsts().size() > 0) {
            System.err.println("ERROR: Cannot uniqueify netlist, design contains ModuleInstances. "
                    + "Please call Design.flattenDesign() first.");
            return null;
        }
        EDIFNetlist netlist = design.getNetlist();
        EDIFLibrary macros = Design.getMacroPrimitives(design.getDevice().getSeries());
        Map<EDIFLibrary, Map<EDIFCell, List<EDIFHierCellInst>>> instMap = createCellInstanceMap(netlist);
        // Don't uniqueify primitive cell instances
        instMap.remove(netlist.getHDIPrimitivesLibrary());

        Map<EDIFCell, List<String>> toUniqueify = new HashMap<EDIFCell, List<String>>();

        for (Entry<EDIFLibrary, Map<EDIFCell, List<EDIFHierCellInst>>> libEntry : instMap.entrySet()) {
            for (Entry<EDIFCell,List<EDIFHierCellInst>> e : libEntry.getValue().entrySet()) {
                // Also skip macros
                if (macros.containsCell(e.getKey())) continue;
                // Identify multiple instantiated cells
                if (e.getValue().size() > 1) {
                    List<String> hierCellNames = new ArrayList<>(e.getValue().size());
                    for (EDIFHierCellInst ehci : e.getValue()) {
                        hierCellNames.add(ehci.getFullHierarchicalInstName());
                    }
                    toUniqueify.put(e.getKey(), hierCellNames);
                }
            }
        }

        if (toUniqueify.isEmpty())
            return false;

        for (EDIFCell curr : new ArrayList<>(toUniqueify.keySet())) {
            duplicateMultiInstCell(design, curr, toUniqueify);
        }

        netlist.resetParentNetMap();

        return true;
    }


    private static int unique = 1;

    private static void duplicateMultiInstCell(Design design, EDIFCell cell,
                                    Map<EDIFCell, List<String>> toUniqueify) {
        EDIFNetlist netlist = design.getNetlist();
        // Check that all higher level cells don't have multiple shared cell definitions, before
        // duplicating this one
        List<String> insts = toUniqueify.get(cell);
        if (insts == null) {
            // Already processed, or no duplicates
            return;
        }
        for (String instName : insts) {
            EDIFHierCellInst inst = netlist.getHierCellInstFromName(instName);
            String[] instParents = inst.getFullHierarchicalInstName().split(EDIF_HIER_SEP);
            StringBuilder sb = new StringBuilder(instParents[0]);
            for (int i=1; i < instParents.length; i++) {
                EDIFCellInst parent = netlist.getCellInstFromHierName(sb.toString());
                if (parent != null) {
                    List<String> parentDuplicates = toUniqueify.get(parent.getCellType());
                    if (parentDuplicates != null) {
                        duplicateMultiInstCell(design, parent.getCellType(), toUniqueify);
                    }
                }
                sb.append(EDIF_HIER_SEP + instParents[i]);
            }
        }

        // Duplicate cell definitions for all but first instance
        boolean first = true;
        for (String instName : insts) {
            EDIFHierCellInst cellInst = netlist.getHierCellInstFromName(instName);
            if (first) {
                first = false;
                continue;
            }
            // Perform cell duplication
            EDIFCell origCell = cellInst.getCellType();
            EDIFCell newCell = new EDIFCell(origCell.getLibrary(), origCell, origCell.getName()
                    + "_RW" + unique++);
            cellInst.getInst().setCellType(newCell);

            // Update any physical cell references
            for (EDIFCellInst inst : newCell.getCellInsts()) {
                String potentialLeafCell = cellInst.getFullHierarchicalInstName()
                        + EDIF_HIER_SEP + inst.getName();
                Cell physCell = design.getCell(potentialLeafCell);
                if (physCell != null) {
                    physCell.setEDIFHierCellInst(cellInst.getChild(inst));
                }
            }

            // Update any physical net references
            for (EDIFNet net : newCell.getNets()) {
                String potentialLeafCell = cellInst.getFullHierarchicalInstName()
                        + EDIF_HIER_SEP + net.getName();
                Net physNet = design.getNet(potentialLeafCell);
                if (physNet != null) {
                    physNet.setLogicalHierNet(cellInst.getNet(net.getName()));
                }
            }
        }
        toUniqueify.remove(cell);
    }

    public static void printHierSepNames(EDIFNetlist netlist) {
        for (EDIFLibrary lib : netlist.getLibraries()) {
            for (EDIFCell cell : lib.getCells()) {
                if (cell.getName().contains(EDIF_HIER_SEP)) {
                    System.out.println("CELL: " + lib.getName() + "," + cell.getName());
                }

                for (EDIFPort port : cell.getPorts()) {
                    if (port.getName().contains(EDIF_HIER_SEP)) {
                        System.out.println("PORT: " + lib.getName() + "," + cell.getName()
                        +"," + port.getName());
                    }
                }

                for (EDIFNet net : cell.getNets()) {
                    if (net.getName().contains(EDIF_HIER_SEP)) {
                        System.out.println("NET: " + lib.getName() + "," + cell.getName()
                        +"," + net.getName());
                    }
                }

                for (EDIFCellInst inst : cell.getCellInsts()) {
                    if (inst.getName().contains(EDIF_HIER_SEP)) {
                        System.out.println("INST: " + lib.getName() + "," + cell.getName()
                        + "," + inst.getName());
                    }
                }
            }
        }
    }

    public static void printLibraries(EDIFNetlist netlist) {
        for (EDIFLibrary lib : netlist.getLibraries()) {
            System.out.println("LIBRARY: " + lib.getName());
            for (Entry<String,EDIFCell> entry : lib.getCellMap().entrySet()) {
                System.out.println("  CELL: " + entry.getValue().getName() + " /// " + entry.getKey());
                for (EDIFCellInst inst : entry.getValue().getCellInsts()) {
                    System.out.println("    INST: " + inst.getCellType().getName() + "("+inst.getName() +")");
                }
            }
        }
    }

    public static <T>
    Iterable<T> sortIfStable(Collection<T> collection, Comparator<T> comparator, boolean stable) {
        if (!stable) {
            return collection;
        }
        return collection.stream().sorted(comparator)::iterator;
    }

    public static <T extends EDIFName>
    Iterable<T> sortIfStable(Collection<T> collection, boolean stable) {
        if (!stable) {
            return collection;
        }
        return collection.stream().sorted(Comparator.comparing(EDIFName::getName))::iterator;
    }

    public static <T extends Comparable<T>, U>
    Iterable<Entry<T,U>> sortIfStable(Map<T,U> collection, boolean stable) {
        if (!stable) {
            return collection.entrySet();
        }
        return collection.entrySet().stream().sorted(Entry.comparingByKey())::iterator;
    }

    /**
     * Locks the netlist by applying the DONT_TOUCH property to instances and nets
     * so that Vivado won't make changes to it during opt_design, place_design,
     * phys_opt_design, or route_design. Note: RapidWright doesn't respect the
     * DONT_TOUCH attribute.
     * 
     * @param netlist The netlist to lock
     */
    public static void lockNetlist(EDIFNetlist netlist) {
        EDIFPropertyValue value = new EDIFPropertyValue("true", EDIFValueType.BOOLEAN);
        lockNetlist(netlist, i -> i.addProperty(DONT_TOUCH, value));
    }

    /**
     * Unlocks the netlist by removing the DONT_TOUCH property on instances and nets
     * so that Vivado won't make changes to it during opt_design, place_design,
     * phys_opt_design, or route_design.
     * 
     * @param netlist The netlist to unlock
     */
    public static void unlockNetlist(EDIFNetlist netlist) {
        lockNetlist(netlist, i -> i.removeProperty(DONT_TOUCH));
    }

    /**
     * Traversal helper method to {@link #lockNetlist(EDIFNetlist)} and
     * {@link #unlockNetlist(EDIFNetlist)}.
     * 
     * @param netlist    The netlist on which to apply the action.
     * @param lockAction The method of action (lock or unlock) to apply to
     *                   {@link EDIFPropertyObject} objects.
     */
    private static void lockNetlist(EDIFNetlist netlist, Consumer<EDIFPropertyObject> lockAction) {
        EDIFCell top = netlist.getTopCell();
        for (EDIFLibrary lib : netlist.getLibraries()) {
            if (lib.isHDIPrimitivesLibrary()) continue;
            for (EDIFCell cell : lib.getCells()) {
                for (EDIFCellInst inst : cell.getCellInsts()) {
                    EDIFCell cellType = inst.getCellType();
                    if (cellType.isPrimitive() && !cellType.hasContents()) {
                        String type = cellType.getName();
                        if (!type.equals(Unisim.GND.name()) && !type.equals(Unisim.VCC.name())) {
                            lockAction.accept(inst);
                        }
                    }
                }
    
                for (EDIFNet net : cell.getNets()) {
                    if (net != null && !net.isGND() && !net.isVCC()) {
                        if (cell == top || net.isInternalToParent()) {
                            lockAction.accept(net);
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a flattened version of the provided netlist.
     * 
     * @param netlist  The netlist to flatten.
     * @param partName The target part of the netlist.
     * @return A new flattened version of the provided netlist.
     */
    public static EDIFNetlist createFlatNetlist(EDIFNetlist netlist, String partName) {
        Part part = PartNameTools.getPart(partName);
        netlist.collapseMacroUnisims(part.getSeries());

        EDIFNetlist flatNetlist = EDIFTools.createNewNetlist(netlist.getTopCell().getName());
        ensureCorrectPartInEDIF(flatNetlist, part.getName());
        EDIFCell flatTop = flatNetlist.getTopCell();
        EDIFLibrary prims = flatNetlist.getHDIPrimitivesLibrary();
        EDIFLibrary work = flatNetlist.getWorkLibrary();

        // Copy all leaf cell instances except VCC/GND
        boolean includeBlackBoxes = true;
        for (EDIFHierCellInst inst : netlist.getAllLeafHierCellInstances(includeBlackBoxes)) {
            EDIFCell type = inst.getCellType();
            if (type.isStaticSource()) continue;
            String name = inst.getFullHierarchicalInstName();
            EDIFCellInst flatInst = flatTop.createChildCellInst(name, type);
            flatInst.setPropertiesMap(inst.getInst().createDuplicatePropertiesMap());
            EDIFLibrary lib = !type.isPrimitive() ? work : prims;
            if (!lib.containsCell(type)) {
                lib.addCell(type);
            }
        }

        // Copy all physical parent nets and connect them to flattened cell instances
        for (Entry<EDIFHierNet, List<EDIFHierPortInst>> e : netlist.getPhysicalNetPinMap().entrySet()) {
            if (e.getKey().getNet().isGND() || e.getKey().getNet().isVCC()) {
                continue;
            }
            String name = e.getKey().getHierarchicalNetName();
            EDIFNet flatNet = flatTop.createNet(name);
            for (EDIFHierPortInst p : e.getValue()) {
                if (p.getCellType().isStaticSource()) continue;
                EDIFCellInst flatInst = flatTop.getCellInst(p.getFullHierarchicalInstName());
                flatNet.createPortInst(p.getPortInst().getName(), flatInst);
            }
        }

        // Create a single VCC/GND net and connect up ports
        for (NetType staticType : new NetType[] { NetType.GND, NetType.VCC }) {
            EDIFNet staticNet = EDIFTools.getStaticNet(staticType, flatTop, flatNetlist);
            List<EDIFHierPortInst> physPins = staticType == NetType.GND 
                    ? netlist.getPhysicalGndPins()
                    : netlist.getPhysicalVccPins();
            for (EDIFHierPortInst p : physPins) {
                if (p.getCellType().isStaticSource()) continue;
                EDIFCellInst flatInst = flatTop.getCellInst(p.getFullHierarchicalInstName());
                staticNet.createPortInst(p.getPortInst().getName(), flatInst);
            }
        }

        // Copy ports and connect accordingly
        for (EDIFPort topPort : netlist.getTopCell().getPorts()) {
            EDIFPort flatPort = flatTop.createPort(topPort);
            if (flatPort.isBus()) {
                int[] indicies = flatPort.getBitBlastedIndicies();
                int i = 0;
                for (EDIFNet net : topPort.getInternalNets()) {
                    if (net == null) continue;
                    EDIFNet flatNet = flatTop.getNet(net.getName());
                    if (flatNet == null) {
                        flatNet = flatTop.createNet(net.getName());
                    }
                    flatNet.createPortInst(flatPort, indicies[i++]);
                }
            } else {
                EDIFNet net = topPort.getInternalNet();
                if (net == null) continue;
                EDIFNet flatNet = flatTop.getNet(net.getName());
                if (flatNet == null) {
                    flatNet = flatTop.createNet(net.getName());
                }
                flatNet.createPortInst(flatPort);
            }
        }

        // Restore netlists to expanded state
        netlist.expandMacroUnisims(part.getSeries());
        flatNetlist.expandMacroUnisims(part.getSeries());

        return flatNetlist;
    }


    /**
     * Removes the edif cell named EDIFCellName from a logical net list.
     * It makes incremental benchmarks by removing selected module(s).
     * @param logicalNetlist The current logical netlist
     * @param EDIFCellName The name of the selected cell to remove.
     */
    
    public static String syntheticIncBench(EDIFNetlist logicalNetlist, Design design, String EDIFCellName, String instanceName, String iteration, String step, String a, String folderpath) {
        final EDIFCell module = logicalNetlist.getCell(EDIFCellName);
        if (module == null) {
            throw new IllegalStateException("Did not find cell to remove: " + EDIFCellName);
        }
        String newNetlistFile = syntheticIncBench(logicalNetlist, design, module, instanceName, iteration, step, a, folderpath);
        
        EDIFNetlist input = EDIFTools.readEdifFile(newNetlistFile);
        String markdownData;
        markdownData = EDIFTools.updateCellDeltaCount_calculateModuleByModule(input);
        return markdownData;
    }

    /**
     * Turns the cell named EDIFCellName into a selected module to remove.
     *
     * @param logicalNetlist        The current logical netlist
     * @param module                The logical cell to remove.
     */
    public static String syntheticIncBench(EDIFNetlist logicalNetlist, Design design, EDIFCell module, String instanceName, String iteration, String step, String a, String folderpath) {
        CodePerfTracker t = CodePerfTracker.SILENT;
        t.start("Init");

        String selectedCellToRemove = instanceName;
        EDIFCell topModule = logicalNetlist.getTopCell();
        EDIFCellInst topModuleInst = logicalNetlist.getTopCellInst();
        if (topModule == null) {
            throw new IllegalStateException("Top Module is null");
        }
        
        EDIFNet portInstNet = new EDIFNet();
        EDIFNet portInstInternalNet = new EDIFNet(); // for Bus net
        String portInstNetName = " ";
        int count_portInst = 0;
        int count_newPIPO = 0;
        int count_free_PIPO = 0;
        EDIFDirection direction = EDIFDirection.INOUT;
        PioDir dir = PioDir.INPUT;
        String newPIOName = "";
        IOTraceResult portToPIPO = new IOTraceResult();
        List<driverPortInfo> driverPort = new ArrayList<>();
        parentAndCellInfo parentCellInfo = new parentAndCellInfo();
        parentCellInfo = findParentAndCellInfo(logicalNetlist,module);
        EDIFCellInst moduleInst = logicalNetlist.getCellInstFromHierName(parentCellInfo.hierarchicalCellName);
        
        List<String> topToSelectedModule = new ArrayList<>();
        // Make a list for all cell in hierarichal path to the selected module
        topToSelectedModule.clear();
        if (parentCellInfo.hierarchicalCellName != null && !parentCellInfo.hierarchicalCellName.isEmpty()) {
            topToSelectedModule.addAll(Arrays.asList(parentCellInfo.hierarchicalCellName.split("/")));
        }
        if (topToSelectedModule.isEmpty()) {
            throw new IllegalStateException("Empty hierarchical path to target instance");
        }
        
        /**
         * Algoritm
         * -> for-loop on all In/Out ports of selected module => p
         * -> find net that connected to the port => n
         * -> for net "n" find all ports connected to this net
         * -> find that port that is connected to the parent => if it's connected to the parent its CellInst is null => if (portInstOnNet.getCellInst() == null) {...
         * -> Then go up to its parent and find parent info => upLevelParent, upLevelParentCellInfo
         * -- now we are in the parent of selected module
         * -> having a while-loop to check all parents up to top module (if parent is null then we are in top module => (upLevelParent != null))
         *                                             up to internal connection (means that it is not connected to its parent in one level upper => (isInOutPortOfParent = false))
         *                                             up to where there is another branch for the net, it means that it is not dirctly connected to the selected module
         *                                             TODO it needs to check for all possible situation
         * -> if it is an INPUT for selected module, just in one case need to add new PO: it is (1) direct connection and (2) not from PI
         *    (in Vivado and FPGA PI is not directly connect to module it firs connect to FDRE, LUTs, or chain of them then connect to other cells or modules)
         *    So if we are in top module and it is connected to PI eventually we don't need add PO and also we will be free of one PI.
         *
         * -> if it is an OUTPUT for selected module, needs connect every points that drive from this OUTPUT to PI
         *    if it is connected to a OBUF in top module, needs to remove this connection to output because it is not meaningful to have a connection from new PI to PO.
         */
        
        System.out.println("The specific hierarchical module '" + instanceName + "' chosen to be pruned from the current design state. " );
        
        for (EDIFPortInst portInst : moduleInst.getPortInsts()) {
            
            if (portInst.getPort().isBus()) {
                int busSize = portInst.getPort().getInternalNets().size();
                int idx = portInst.getIndex();
                portInstInternalNet = portInst.getPort().getInternalNet(idx);
                portInstNet = portInst.getNet();
            } else {
                portInstNet = portInst.getNet();
            }
            if ((portInst.getName().contains("clk")) || (portInst.getName().contains("clock")) || (portInst.getName().equals("clock_IBUF_BUFG")))
            {
                portInstNet.removePortInst(portInst);
                continue;
            }
            
            if (portInstNet != null) {
                EDIFPortInstList portInstList;
                portInstList = portInstNet.getEDIFPortInstList();
                EDIFCell upLevelParent = null;
                boolean isInOutPortOfParent = false;
                boolean isDirectConnection;
                if (portInstList.size() <= 2) {
                    isDirectConnection = true;
                } else {
                    isDirectConnection = false;
                }
                int driveNum = 0;
                boolean PIPO_getFree = false;
                EDIFPort PIPO_inTopModule = null;
                boolean isAllPIPOBusConnectedWithModuleInst = true;
                boolean checkPIPOBusForFirstTime = true;
                int countToRemovePIPOPort = 0;
                for (EDIFPortInst ports : portInstList) {
                    if (ports.getDirection() == EDIFDirection.INPUT) {
                        driveNum++;
                    }                  
                        if (ports.getCellInst() != null) {
                            if ((ports.getCellInst().getCellType().getName().equals("OBUF")) || (ports.getCellInst().getCellType().getName().equals("IBUF"))) {
                                int modulePortBusSize = portInst.getPort().getWidth();
                                countToRemovePIPOPort = modulePortBusSize;
                                int PIPOBusSize = 0;
                                
                                EDIFCellInst IOBUFInst_toRemove = ports.getCellInst();
                                boolean shouldRemoveBUF = false;
                                if ((ports.getCellInst().getCellType().getName().equals("OBUF")) || ((ports.getCellInst().getCellType().getName().equals("IBUF")) && (IOBUFInst_toRemove.getPortInst("O").getNet().getPortInsts().size() == 2))) {
                                    shouldRemoveBUF = true;
                                }
                                if (shouldRemoveBUF) {
                                    EDIFNet IOBUFNet_toRemove;
                                    Collection<EDIFPortInst> BUF_portInsts = List.copyOf(IOBUFInst_toRemove.getPortInsts());
                                    for (EDIFPortInst IOBUF_portInst : BUF_portInsts) {
                                        IOBUFNet_toRemove = IOBUF_portInst.getNet();
                                        for (EDIFPortInst IOBUF_portInst_toRemove : IOBUFNet_toRemove.getPortInsts()) {
                                            if (((IOBUF_portInst.getName().equals("I")) && (ports.getCellInst().getCellType().getName().equals("IBUF"))) || ((ports.getCellInst().getCellType().getName().equals("OBUF")) && (IOBUF_portInst.getName().equals("O")))) {
                                                if (IOBUF_portInst_toRemove.getCellInst() == null) {
                                                    PIPOBusSize = IOBUF_portInst_toRemove.getPort().getWidth();
                                                    countToRemovePIPOPort -= PIPOBusSize;
                                                    PIPO_inTopModule = IOBUF_portInst_toRemove.getPort();
                                                    if (PIPOBusSize == modulePortBusSize) {
                                                        isAllPIPOBusConnectedWithModuleInst = true;
                                                    } else {
                                                        isAllPIPOBusConnectedWithModuleInst = false;
                                                    }
                                                }
                                            }
                                            if (IOBUF_portInst_toRemove.getParentCell() == topModule) {
                                                topModuleInst.removePortInst(IOBUF_portInst_toRemove);
                                                if (isAllPIPOBusConnectedWithModuleInst) {
                                                    topModule.removePort(IOBUF_portInst_toRemove.getPort());
                                                } else {
                                                    boolean isBUFConnectedToConst = false;
                                                    
                                                    Collection<EDIFNet> tmpTopModuleNets = List.copyOf(topModule.getNets());
                                                    for (EDIFNet tmpNet : tmpTopModuleNets) {
                                                        if (tmpNet.getName().startsWith(PIPO_inTopModule.getBusName() + "[")) {
                                                            if ((ports.getCellInst().getCellType().getName().equals("OBUF"))) {
                                                                for (EDIFPortInst tmpPortInst : tmpNet.getPortInsts()) {
                                                                    if (tmpPortInst.getName().equals("O")) {
                                                                        EDIFNet constNet = tmpPortInst.getCellInst().getPortInst("I").getNet();
                                                                        if ((constNet.getName().equals("<const0>")) || (constNet.getName().equals("<const1>"))) {
                                                                            isBUFConnectedToConst = true;
                                                                            countToRemovePIPOPort--;
                                                                            for (EDIFPortInst tmpPortInstConst : tmpPortInst.getCellInst().getPortInsts()) {
                                                                                topModuleInst.removePortInst(tmpPortInstConst);
                                                                            }
                                                                            topModule.removeNet(constNet);
                                                                        }
                                                                    }
                                                                    if (isBUFConnectedToConst) {
                                                                        topModule.removeCellInst(tmpPortInst.getCellInst());
                                                                    }
                                                                }
                                                            }
                                                            if (isBUFConnectedToConst) {
                                                                topModule.removeNet(tmpNet);
                                                                isBUFConnectedToConst = false;
                                                            }
                                                        }
                                                        if (countToRemovePIPOPort == 0) {
                                                            break;
                                                        }
                                                    }
                                                    topModule.removePort(IOBUF_portInst_toRemove.getPort());
                                                    isAllPIPOBusConnectedWithModuleInst = true;
                                                }
                                            }
                                        }
                                        topModule.removeNet(IOBUFNet_toRemove);
                                    }
                                    topModule.removeCellInst(IOBUFInst_toRemove);
                                    PIPO_getFree = true;
                                }
                            }
                        }
                }
                if (PIPO_getFree) {
                    continue;
                }
                parentAndCellInfo upLevelParentCellInfo = new parentAndCellInfo();
                driverPort.clear();
                EDIFPortInst portInstToRemove = null;
                boolean isInternalOnly = true;
                for (EDIFPortInst portInstOnNet : portInstList) {
                    if (portInstOnNet.getCellInst() == null) {
                        isInternalOnly = false;
                        break;
                    }
                }
                
                if ((isInternalOnly) || (portInst.getDirection() == EDIFDirection.OUTPUT)) {
                    upLevelParent = portInstNet.getParentCell();
                    portInstToRemove = portInst;
                    portInstNet.removePortInst(portInst);
                    if (portInst.getDirection() == EDIFDirection.INPUT) {
                        
                        if (isDirectConnection) {
                            /**
                             * if it is direct connection
                             * and we cannot go up any more
                             * and it is not in top module
                             * (1) the net in this level of hierarchy needs to connect to new PO
                             * (2) remove those direct connection from this hierarchy level to the selected module => did it, before this if
                             */
                            
                            parentAndCellInfo upTolatestCell = findParentAndCellInfo(logicalNetlist, moduleInst.getCellType());
                            int y = upTolatestCell.hierarchicalCellName.split("/").length;
                            
                            // Create new PO port and net in top module
                            String newOBUFName = portInstNet.getName() + module.getName() + "_OBUF_new";
                            EDIFPort newOBUFPort = topModule.createPort(newOBUFName, EDIFDirection.OUTPUT, 1);
                            // Create new net in top module to connect with new PO
                            EDIFNet newOBUFNet = topModule.createNet(newOBUFName + "__O");
                            newOBUFNet.createPortInst(newOBUFPort);
                            
                            // Create new OBUF instance in top module to connect to the new PO
                            EDIFCell OBUF_cell = logicalNetlist.getHDIPrimitive(Unisim.OBUF);
                            
                            EDIFCellInst newOBUFInst = topModule.createChildCellInst((newOBUFName + "_OBUF_inst"), OBUF_cell);
                            EDIFPort OBUFI = OBUF_cell.getPort("I");
                            EDIFPort OBUFO = OBUF_cell.getPort("O");
                            newOBUFNet.createPortInst(OBUFO, newOBUFInst);
                            EDIFNet newPortToI = null;
                            if (y > 1) {
                                newPortToI = topModule.createNet(newOBUFName + "__I");
                                newPortToI.createPortInst(OBUFI, newOBUFInst);
                            }
                            
                            int idx = upTolatestCell.hierarchicalCellName.lastIndexOf("/");
                            String lastHierCellName = (idx >= 0) ? upTolatestCell.hierarchicalCellName.substring(0, idx) : "";
                            String cellInstName = "";
                            EDIFCell cellToPO = null;
                            EDIFCell lastCellToPO = topModule;
                            EDIFCellInst cellInstToPO = null;
                            EDIFNet newNetToOBUF = newPortToI;
                            EDIFPort newPortToPO = null;
                            EDIFNet newNetToPO = null;
                            
                            for (int i = 0; i < y - 1; i++) {
                                cellInstName = topToSelectedModule.get(i);
                                cellToPO = lastCellToPO.getCellInst(cellInstName).getCellType();
                                cellInstToPO = lastCellToPO.getCellInst(cellInstName);
                                newPortToPO = cellToPO.createPort(cellInstToPO.getName() + newOBUFName + "__ToPO", EDIFDirection.OUTPUT, 1);
                                newNetToPO = cellToPO.createNet(cellInstToPO.getName() + newOBUFName + "_net_ToPO");
                                newNetToPO.createPortInst(newPortToPO);
                                newNetToOBUF.createPortInst(newPortToPO, cellInstToPO);
                                newNetToOBUF = newNetToPO;
                                lastCellToPO = cellToPO;
                            }
                            
                            if (newPortToPO != null) {
                                if (newNetToPO != null) {
                                    // it is the point that net needs new driver
                                    EDIFCell driver_cell = logicalNetlist.getHDIPrimitive(Unisim.LUT1);
                                    EDIFCellInst asDriver = cellToPO.createChildCellInst((cellInstToPO.getName() + newOBUFName + "_asDriver"), driver_cell);
                                    asDriver.addProperty("INIT", "2'h1");
                                    EDIFPort LUT1_I0 = driver_cell.getPort("I0");
                                    EDIFPort LUT1_O = driver_cell.getPort("O");
                                    newNetToPO.createPortInst(LUT1_O, asDriver);
                                    portInstNet.createPortInst(LUT1_I0, asDriver);
                                }
                            } else if (y==1) {
                                /** selected module to remove is instance in top module and just need to
                                 * connect an available net of another instance (if it is a direct connection between two
                                 * instances of top module).                                 
                                  */
                                portInstNet.createPortInst(OBUFI, newOBUFInst);
                            }
                        }
                    } else if (portInst.getDirection() == EDIFDirection.OUTPUT) {
                        /**
                         * for both direct connection and non-direct connection needs connect it to new PI from
                         * (1) if it is direct connection, need connect latest net to a new PI
                         * (2) if it is direct connection, and if we are in top module and this net eventually connected to the PO, don't need add new PI. Just need to remove all nets and ports from selected module up to here
                         * (3) if it is non-direct connection, and we are not in top module, need to connect it from first branch to new PI
                         * (4) if it is non-direct connection, and we are in top module, for those connection that connected to PO do noting and remove nets, for others connect to new PI
                         */
                        
                        parentAndCellInfo upTolatestCell = findParentAndCellInfo(logicalNetlist, moduleInst.getCellType());
                        int y = upTolatestCell.hierarchicalCellName.split("/").length;
                        
                        // Create new PI port and net in top module
                        String newIBUFName = portInstNet.getName() + module.getName() + "_IBUF_new";
                        EDIFPort newIBUFPort = topModule.createPort(newIBUFName, EDIFDirection.INPUT, 1);
                        // Create new net in top module to connect with new PI
                        EDIFNet newIBUFNet = topModule.createNet(newIBUFName + "__I");
                        newIBUFNet.createPortInst(newIBUFPort);
                        
                        // Create new OBUF instance in top module to connect to the new PO
                        EDIFCell IBUF_cell = logicalNetlist.getHDIPrimitive(Unisim.IBUF);
                        
                        EDIFCellInst newIBUFInst = topModule.createChildCellInst((newIBUFName + "_IBUF_inst"), IBUF_cell);
                        EDIFPort IBUFI = IBUF_cell.getPort("I");
                        EDIFPort IBUFO = IBUF_cell.getPort("O");
                        newIBUFNet.createPortInst(IBUFI, newIBUFInst);
                        EDIFNet newNetFromO = null;
                        if ( y > 1) {
                            newNetFromO = topModule.createNet(newIBUFName + "__O");
                            newNetFromO.createPortInst(IBUFO, newIBUFInst);
                        }
                        
                        int idx = upTolatestCell.hierarchicalCellName.lastIndexOf("/");
                        String lastHierCellName = (idx >= 0) ? upTolatestCell.hierarchicalCellName.substring(0, idx) : "";
                        String cellInstName = "";
                        EDIFCell cellFromPI = null;
                        EDIFCell lastCellFromPI = topModule;
                        EDIFCellInst cellInstFromPI = null;
                        EDIFNet newNetFromIBUF = newNetFromO;
                        EDIFPort newPortFromPI = null;
                        EDIFNet newNetFromPI = null;
                        
                        for (int i = 0; i < y - 1; i++) {
                            cellInstName = topToSelectedModule.get(i);
                            cellFromPI = lastCellFromPI.getCellInst(cellInstName).getCellType();
                            cellInstFromPI = lastCellFromPI.getCellInst(cellInstName);
                            newPortFromPI = cellFromPI.createPort(cellInstFromPI.getName() + newIBUFName + "__FromPI", EDIFDirection.INPUT, 1);
                            newNetFromPI = cellFromPI.createNet(cellInstFromPI.getName() + newIBUFName + "_net_FromPI");
                            newNetFromPI.createPortInst(newPortFromPI);
                            newNetFromIBUF.createPortInst(newPortFromPI, cellInstFromPI);
                            newNetFromIBUF = newNetFromPI;
                            lastCellFromPI = cellFromPI;
                        }
                        if (newPortFromPI != null) {
                            if (newNetFromPI != null) {
                                // it is the point that net needs new driver
                                EDIFCell driver_cell = logicalNetlist.getHDIPrimitive(Unisim.LUT1);
                                EDIFCellInst asDriver = cellFromPI.createChildCellInst((cellInstFromPI.getName() + newIBUFName + "_asDriver"), driver_cell);
                                asDriver.addProperty("INIT", "2'h1");
                                EDIFPort LUT1_I0 = driver_cell.getPort("I0");
                                EDIFPort LUT1_O = driver_cell.getPort("O");
                                newNetFromPI.createPortInst(LUT1_I0, asDriver);
                                portInstNet.createPortInst(LUT1_O, asDriver);
                            }
                        } else if (y==1) {
                            /** selected module to remove is instance in top module and just need to
                             * connect an available net of another instance (if it is a direct connection between two
                             * instances of top module).
                             * TODO if it's connected to PI/PO needs to delete it
                             */
                            portInstNet.createPortInst(IBUFO, newIBUFInst);
                        }                       
                    }
                    
                } else if ((portInst.getDirection() == EDIFDirection.INPUT) && (isDirectConnection)) {
                    boolean breakCheck = false;
                    AbstractMap<EDIFPort,EDIFCellInst> IOBufInfo = new HashMap<EDIFPort,EDIFCellInst>();                    
                    for (EDIFPortInst portInstOnNet : portInstList) {
                        // if the port instance of this net is In Out port of the module (cellInst = null)
                        if (portInstOnNet.getCellInst() == null) {
                            isInOutPortOfParent = true;
                            portInstToRemove = portInstOnNet;
                            upLevelParent = portInstOnNet.getParentCell();
                            upLevelParentCellInfo = findParentAndCellInfo(logicalNetlist, upLevelParent);
                            EDIFCellInst upLevelModuleInst = logicalNetlist.getCellInstFromHierName(upLevelParentCellInfo.hierarchicalCellName);
                            EDIFNet upLevelPortInstNet = null;
                            EDIFNet upLevelPortInstInternalNet = null;
                            // if upLevelParent is null means that we are at top module
                            while ((upLevelParent != topModule) && (isInOutPortOfParent) && (isDirectConnection)) {
                                EDIFPortInst upLevelPortInst = upLevelModuleInst.getPortInst(portInstOnNet.getName()); 
                                if (upLevelPortInst != null) {
                                    if (upLevelPortInst.getPort().isBus()) {
                                        int busSize = upLevelPortInst.getPort().getInternalNets().size();
                                        int idx = upLevelPortInst.getIndex();
                                        upLevelPortInstInternalNet = upLevelPortInst.getPort().getInternalNet(idx);
                                        upLevelPortInstNet = upLevelPortInst.getNet();
                                    } else {
                                        upLevelPortInstNet = upLevelPortInst.getNet();
                                    }
                                    driverPort.add(new driverPortInfo(upLevelPortInstNet, upLevelPortInst, upLevelParent));
                                    EDIFPortInstList upLevelPortInstList;
                                    upLevelPortInstList = upLevelPortInstNet.getEDIFPortInstList();
                                    if (upLevelPortInstList.size() > 2) {
                                        isDirectConnection = false;
                                    } else {
                                        isDirectConnection = true;
                                    }
                                    
                                    for (EDIFPortInst next_net_portInst : upLevelPortInstList) {
                                        isInOutPortOfParent = false;
                                        if (next_net_portInst.getCellInst() == null) {
                                            upLevelParent = next_net_portInst.getParentCell();
                                            upLevelParentCellInfo = findParentAndCellInfo(logicalNetlist, upLevelParent);
                                            upLevelModuleInst = logicalNetlist.getCellInstFromHierName(upLevelParentCellInfo.hierarchicalCellName);
                                            isInOutPortOfParent = true;
                                            portInstOnNet = next_net_portInst;
                                            break;
                                        }
                                    }
                                } else {
                                    isInOutPortOfParent = false;
                                }
                            }
                            
                            /**
                             * for either INPUT or OUTPUT portInst in selected module, need to remove nets and ports from selected module up to module before latest one
                             * for latest module in drivePort, just need to remove portInst related to the connection and keep the net for further analysis
                             */
                            if (driverPort.size() > 0) {
                                for (int i = 0; i < driverPort.size() - 1; i++) {
                                    driverPortInfo netAndPortToRemove = driverPort.get(i);
                                    netAndPortToRemove.net.removePortInst(netAndPortToRemove.portInst);
                                    
                                    netAndPortToRemove.cell.removeNet(netAndPortToRemove.net);                                    
                                }
                                driverPortInfo netAndPortToRemove = driverPort.get(driverPort.size() - 1);
                                netAndPortToRemove.net.removePortInst(netAndPortToRemove.portInst);
                                if (netAndPortToRemove.net.getPortInsts().size() == 1) {
                                    for (EDIFPortInst ports : netAndPortToRemove.net.getPortInsts()) {
                                        if (ports.getCellInst() == null) {
                                            continue;
                                        } else if ((ports.getCellInst().getCellType().getName().equals("OBUF")) || (ports.getCellInst().getCellType().getName().equals("IBUF"))) {
                                            EDIFCellInst IBUFInst_toRemove = ports.getCellInst();
                                            EDIFNet IOBUFNet_toRemove;
                                            Collection<EDIFPortInst> BUF_portInsts = List.copyOf(IBUFInst_toRemove.getPortInsts());
                                            for (EDIFPortInst IOBUF_portInst : BUF_portInsts) {
                                                IOBUFNet_toRemove = IOBUF_portInst.getNet();
                                                for (EDIFPortInst IOBUF_portInst_toRemove : IOBUFNet_toRemove.getPortInsts()) {
                                                    if (IOBUF_portInst_toRemove.getParentCell() == topModule) {
                                                        topModule.removePort(IOBUF_portInst_toRemove.getPort());
                                                        topModule.trackChange(EDIFChangeType.PORT_REMOVE,IOBUF_portInst_toRemove.getPort().getName());
                                                        topModuleInst.removePortInst(IOBUF_portInst_toRemove);
                                                        topModule.trackChange(EDIFChangeType.PORT_INST_REMOVE,IOBUF_portInst_toRemove.getName());
                                                    }
                                                }
                                                topModule.removeNet(IOBUFNet_toRemove);
                                            }
                                            topModule.removeCellInst(IBUFInst_toRemove);
                                        }
                                    }
                                }
                                if (portInst.getDirection() == EDIFDirection.INPUT) {
                                    
                                    /**
                                     * whenever we reach to a net with multiple FanOut,
                                     * just need to remove those direct connections from selected module until this module => did it before this if
                                     * Don't need add new PO
                                     */
                                    
                                    if ((upLevelParent != topModule) && (isDirectConnection) && (!isInOutPortOfParent)) {
                                        /**
                                         * if it is direct connection
                                         * and we cannot go up any more
                                         * and it is not in top module
                                         * (1) the net in this level of hierarchy needs to connect to new PO
                                         * (2) remove those direct connection from this hierarchy level to the selected module => did it before this if
                                         */
                                        
                                        parentAndCellInfo upTolatestCell = findParentAndCellInfo(logicalNetlist, netAndPortToRemove.cell);
                                        int y = upTolatestCell.hierarchicalCellName.split("/").length;
                                        
                                        // Create new PO port and net in top module
                                        String newOBUFName = netAndPortToRemove.net.getName() + module.getName() + "_OBUF_new";
                                        EDIFPort newOBUFPort = topModule.createPort(newOBUFName, EDIFDirection.OUTPUT, 1);
                                        // Create new net in top module to connect with new PO
                                        EDIFNet newOBUFNet = topModule.createNet(newOBUFName + "__O");
                                        newOBUFNet.createPortInst(newOBUFPort);
                                        
                                        // Create new OBUF instance in top module to connect to the new PO
                                        EDIFCell OBUF_cell = logicalNetlist.getHDIPrimitive(Unisim.OBUF);
                                        
                                        EDIFCellInst newOBUFInst = topModule.createChildCellInst((newOBUFName + "_OBUF_inst"), OBUF_cell);
                                        EDIFPort OBUFI = OBUF_cell.getPort("I");
                                        EDIFPort OBUFO = OBUF_cell.getPort("O");
                                        newOBUFNet.createPortInst(OBUFO, newOBUFInst);
                                        EDIFNet newPortToI = null;
                                        if (y > 1) {
                                            newPortToI = topModule.createNet(newOBUFName + "__I");
                                            newPortToI.createPortInst(OBUFI, newOBUFInst);
                                        }
                                        
                                        int idx = upTolatestCell.hierarchicalCellName.lastIndexOf("/");
                                        String lastHierCellName = (idx >= 0) ? upTolatestCell.hierarchicalCellName.substring(0, idx) : "";
                                        String cellInstName = "";
                                        EDIFCell cellToPO = null;
                                        EDIFCell lastCellToPO = topModule;
                                        EDIFCellInst cellInstToPO = null;
                                        EDIFNet newNetToOBUF = newPortToI;
                                        EDIFPort newPortToPO = null;
                                        EDIFNet newNetToPO = null;
                                        
                                        for (int i = 0; i < y - 1; i++) {
                                            cellInstName = topToSelectedModule.get(i);
                                            cellToPO = lastCellToPO.getCellInst(cellInstName).getCellType();
                                            cellInstToPO = lastCellToPO.getCellInst(cellInstName);
                                            newPortToPO = cellToPO.createPort(cellInstToPO.getName() + newOBUFName + "__ToPO", EDIFDirection.OUTPUT, 1);
                                            newNetToPO = cellToPO.createNet(cellInstToPO.getName() + newOBUFName + "_net_ToPO");
                                            newNetToPO.createPortInst(newPortToPO);
                                            newNetToOBUF.createPortInst(newPortToPO, cellInstToPO);
                                            newNetToOBUF = newNetToPO;
                                            lastCellToPO = cellToPO;
                                        }
                                        if (newPortToPO != null) {
                                            if (newNetToPO != null) {
                                                // it is the point of net that needs new driver
                                                EDIFCell driver_cell = logicalNetlist.getHDIPrimitive(Unisim.LUT1);
                                                EDIFCellInst asDriver = cellToPO.createChildCellInst((cellInstToPO.getName() + newOBUFName + "_asDriver"), driver_cell);
                                                asDriver.addProperty("INIT", "2'h1");
                                                EDIFPort LUT1_I0 = driver_cell.getPort("I0");
                                                EDIFPort LUT1_O = driver_cell.getPort("O");
                                                newNetToPO.createPortInst(LUT1_O, asDriver);
                                                upLevelPortInstNet.createPortInst(LUT1_I0, asDriver);
                                            }
                                        } else if (y==1) {
                                            upLevelPortInstNet.createPortInst(OBUFI, newOBUFInst);
                                        }
                                    } else if ((upLevelParent == topModule) && (isDirectConnection)) {
                                        /**
                                         * it is direct connection
                                         * and we are in top level module
                                         * if it is INPUT port of top module so we don't need add new PO
                                         * (1) remove those directed connection from selected module to this PI => did it before this if
                                         * (2) the INPUT port in top module connected fist to IBUF,FDRE,LUTs,etc from its output to others
                                         */
                                        System.out.println("This Net is in Top and direct connection");
                                        
                                    }
                                    
                                } else if (portInst.getDirection() == EDIFDirection.OUTPUT) {
                                    /**
                                     * for both direct connection and non-direct connection needs connect it to new PI from
                                     * (1) if it is direct connection, need connect latest net to a new PI
                                     * (2) if it is direct connection, and if we are in top module and this net eventually connected to the PO, don't need add new PI. Just need to remove all nets and ports from selected module up to here
                                     * (3) if it is non-direct connection, and we are not in top module, need to connect it from first branch to new PI
                                     * (4) if it is non-direct connection, and we are in top module, for those connection that connected to PO do noting and remove nets, for others connect to new PI
                                     */
                                    
                                    parentAndCellInfo upTolatestCell = findParentAndCellInfo(logicalNetlist, netAndPortToRemove.cell);
                                    int y = upTolatestCell.hierarchicalCellName.split("/").length;
                                    
                                    // Create new PI port and net in top module
                                    String newIBUFName = netAndPortToRemove.net.getName() + module.getName() + "_IBUF_new";
                                    EDIFPort newIBUFPort = topModule.createPort(newIBUFName, EDIFDirection.INPUT, 1);
                                    // Create new net in top module to connect with new PI
                                    EDIFNet newIBUFNet = topModule.createNet(newIBUFName + "__I");
                                    newIBUFNet.createPortInst(newIBUFPort);
                                    
                                    // Create new OBUF instance in top module to connect to the new PO
                                    EDIFCell IBUF_cell = logicalNetlist.getHDIPrimitive(Unisim.IBUF);
                                    
                                    EDIFCellInst newIBUFInst = topModule.createChildCellInst((newIBUFName + "_IBUF_inst"), IBUF_cell);
                                    EDIFPort IBUFI = IBUF_cell.getPort("I");
                                    EDIFPort IBUFO = IBUF_cell.getPort("O");
                                    newIBUFNet.createPortInst(IBUFI, newIBUFInst);
                                    
                                    EDIFNet newNetFromO = null;
                                    if (y > 1) {
                                        newNetFromO = topModule.createNet(newIBUFName + "__O");
                                        newNetFromO.createPortInst(IBUFO, newIBUFInst);
                                    }
                                    
                                    int idx = upTolatestCell.hierarchicalCellName.lastIndexOf("/");
                                    String lastHierCellName = (idx >= 0) ? upTolatestCell.hierarchicalCellName.substring(0, idx) : "";
                                    String cellInstName = "";
                                    EDIFCell cellFromPI = null;
                                    EDIFCell lastCellFromPI = topModule;
                                    EDIFCellInst cellInstFromPI = null;
                                    EDIFNet newNetFromIBUF = newNetFromO;
                                    EDIFPort newPortFromPI = null;
                                    EDIFNet newNetFromPI = null;
                                    
                                    for (int i = 0; i < y - 1; i++) {
                                        cellInstName = topToSelectedModule.get(i);
                                        cellFromPI = lastCellFromPI.getCellInst(cellInstName).getCellType();
                                        cellInstFromPI = lastCellFromPI.getCellInst(cellInstName);
                                        newPortFromPI = cellFromPI.createPort(cellInstFromPI.getName() + newIBUFName + "__FromPI", EDIFDirection.INPUT, 1);
                                        newNetFromPI = cellFromPI.createNet(cellInstFromPI.getName() + newIBUFName + "_net_FromPI");
                                        newNetFromPI.createPortInst(newPortFromPI);
                                        newNetFromIBUF.createPortInst(newPortFromPI, cellInstFromPI);
                                        newNetFromIBUF = newNetFromPI;
                                        lastCellFromPI = cellFromPI;
                                    }
                                    if (newPortFromPI != null) {
                                        if (newNetFromPI != null) {
                                            // it is the point of net that needs new driver
                                            EDIFCell driver_cell = logicalNetlist.getHDIPrimitive(Unisim.LUT1);
                                            EDIFCellInst asDriver = cellFromPI.createChildCellInst((cellInstFromPI.getName() + newIBUFName + "_asDriver"), driver_cell);
                                            asDriver.addProperty("INIT", "2'h1");
                                            EDIFPort LUT1_I0 = driver_cell.getPort("I0");
                                            EDIFPort LUT1_O = driver_cell.getPort("O");
                                            newNetFromPI.createPortInst(LUT1_I0, asDriver);
                                            upLevelPortInstNet.createPortInst(LUT1_O, asDriver);
                                        }
                                    } else if (y==1) {
                                        upLevelPortInstNet.createPortInst(IBUFO, newIBUFInst);
                                    }
                                }
                            }
                            
                            if (upLevelParent == topModule) {
                                System.out.println("Reach to the top...");
                                portToPIPO.reachesTop = true;
                            } else {
                                portToPIPO.reachesTop = false;
                                
                            }
                        }
                    }
                    if (portInstToRemove != null) {
                        portInstNet.removePortInst(portInstToRemove);
                        portInstNet.removePortInst(portInstNet.getPortInst(moduleInst, portInst.getName()));
                    }
                } else if ((portInst.getDirection() == EDIFDirection.INPUT) && (!isDirectConnection)) {
                    portInstNet.removePortInst(portInstNet.getPortInst(moduleInst, portInst.getName()));
                }
            }
        }
        
        // Remove Logical Cell

        EDIFHierCellInst hierarchicalCell = logicalNetlist.getHierCellInstFromName(parentCellInfo.hierarchicalCellName);
        List<EDIFHierCellInst> allLeafs = logicalNetlist.getAllLeafDescendants(hierarchicalCell);
        Set<Cell> cells = new HashSet<>();
        for (EDIFHierCellInst i : allLeafs) {
            // Get the cell
            Cell c = design.getCell(i.getFullHierarchicalInstName());
            if (c == null) {
                continue;
            }
            cells.add(c);
        }
        
        for (Cell c : cells) {
            // Remove Logical Cell
            for (EDIFPortInst portInst : c.getEDIFCellInst().getPortInsts()) {
                EDIFNet en = portInst.getNet();
                if (en != null) {
                    en.removePortInst(portInst);
                }
            }
            c.getParentCell().removeCellInst(c.getEDIFCellInst());
        }

        parentCellInfo.currParentCell.removeCellInst(selectedCellToRemove);
        moduleInst = logicalNetlist.getCellInstFromHierName(parentCellInfo.hierarchicalCellName);
        logicalNetlist.removeUnusedCellsFromAllWorkLibraries();
        List<EDIFPort> portToRemove = new ArrayList<>();
        for (EDIFPort topPort : topModule.getPorts()) {
            if (topPort.getInternalNet() == null) {
                portToRemove.add(topPort);
            }
        }
        
        System.out.println("------------------ Target Module Successfully Pruned ------------------");
        String filename_edf = topModule.getName()+"_i-"+iteration+"_s-"+step+"_a-"+a+".edf";
        String fullpath_edf = Paths.get(folderpath, filename_edf).toString();
        String filename_dcp = topModule.getName()+"_i-"+iteration+"_s-"+step+"_a-"+a+".dcp";
        String fullpath_dcp = Paths.get(folderpath, filename_dcp).toString();        
        logicalNetlist.exportEDIF(fullpath_edf);
        design.writeCheckpoint(fullpath_dcp, CodePerfTracker.SILENT);
        t.stop().printSummary();
        return  fullpath_edf;
    }
    
    public static String updateCellDeltaCount_calculateModuleByModule(EDIFNetlist logicalNetlist) {
        int cell_num = 0;
        int delta = 0;
        List<ModuleData> moduleList = new ArrayList<>();
        EDIFCell topCell = logicalNetlist.getTopCell();
        EDIFCell topModule = logicalNetlist.getTopCell();
        
        for (EDIFCell module : logicalNetlist.getWorkLibrary().getCells()) {
            
            String moduleName = module.getName();
            String instModlueName = "";
            String parentInstModuleName = "";
            EDIFCell parent = logicalNetlist.getTopCell();
            
            if (topCell.getName().equals(moduleName)) {
                instModlueName = topCell.getName();
                cell_num = topCell.getCellInsts().size();                
                delta = topCell.getInternalNetMap().size();                
                moduleList.add(new ModuleData("(top)",instModlueName, "", cell_num, delta));
            }
            else {
                parentAndCellInfo parentCellInfo = new parentAndCellInfo();
                parentCellInfo = findParentAndCellInfo(logicalNetlist,module);
                EDIFCellInst moduleInst = logicalNetlist.getCellInstFromHierName(parentCellInfo.hierarchicalCellName);
                instModlueName = moduleInst.getName();
                parent = moduleInst.getParentCell();
                int count_newPIPO = 0;
                int count_free_PIPO = 0;
                EDIFNet portInstNet = new EDIFNet();
                List<driverPortInfo> driverPort = new ArrayList<>();
                
                for (EDIFPortInst portInst : moduleInst.getPortInsts()) {
                    if (portInst.getPort().isBus()) {
                        portInstNet = portInst.getNet();
                    } else {
                        portInstNet = portInst.getNet();
                    }
                    
                    if (portInstNet != null) {
                        EDIFPortInstList portInstList;
                        portInstList = portInstNet.getEDIFPortInstList(); 
                        EDIFCell upLevelParent = null;
                        boolean isInOutPortOfParent = false;
                        boolean isDirectConnection;
                        if (portInstList.size() <= 2) {
                            isDirectConnection = true;                            
                        } else {
                            isDirectConnection = false;                            
                        }
                        int driveNum = 0;
                        boolean PIPO_getFree = false;
                        for (EDIFPortInst ports : portInstList) {
                            if (ports.getDirection() == EDIFDirection.INPUT) {
                                driveNum++;
                            }
                            if (isDirectConnection) {
                                if (ports.getCellInst() != null) {
                                    if ((ports.getCellInst().getCellType().getName().equals("OBUF")) || (ports.getCellInst().getCellType().getName().equals("IBUF"))) {
                                        count_free_PIPO++;
                                        PIPO_getFree = true;
                                    }
                                }
                            }
                        }
                        if (PIPO_getFree) {
                            continue;
                        }
                        parentAndCellInfo upLevelParentCellInfo = new parentAndCellInfo();
                        driverPort.clear();
                        EDIFPortInst portInstToRemove = null;
                        boolean isInternalOnly = true;
                        for (EDIFPortInst portInstOnNet : portInstList) {
                            if (portInstOnNet.getCellInst() == null) {
                                isInternalOnly = false;
                                break;
                            }
                        }
                        
                        if ((isInternalOnly) || (portInst.getDirection() == EDIFDirection.OUTPUT)) {
                            upLevelParent = portInstNet.getParentCell();
                            portInstToRemove = portInst;
                            if (portInst.getDirection() == EDIFDirection.INPUT) {
                                if (isDirectConnection) {
                                    // Create new PO port and net in top module
                                    count_newPIPO++;
                                }
                            } else if (portInst.getDirection() == EDIFDirection.OUTPUT) {
                                // Create new PI port and net in top module
                                count_newPIPO++;
                            }
                        } else if ((portInst.getDirection() == EDIFDirection.INPUT) && (isDirectConnection)) {
                            boolean breakCheck = false;
                            
                            for (EDIFPortInst portInstOnNet : portInstList) {
                                // if the port instance of this net is In Out port of the module (cellInst = null)
                                if (portInstOnNet.getCellInst() == null) {
                                    isInOutPortOfParent = true;
                                    portInstToRemove = portInstOnNet;                                    
                                    upLevelParent = portInstOnNet.getParentCell(); //=d;
                                    upLevelParentCellInfo = findParentAndCellInfo(logicalNetlist, upLevelParent);
                                    EDIFCellInst upLevelModuleInst = logicalNetlist.getCellInstFromHierName(upLevelParentCellInfo.hierarchicalCellName);
                                    EDIFNet upLevelPortInstNet = null;
                                    EDIFNet upLevelPortInstInternalNet = null;
                                    while ((upLevelParent != topModule) && (isInOutPortOfParent) && (isDirectConnection)) {
                                        EDIFPortInst upLevelPortInst = upLevelModuleInst.getPortInst(portInstOnNet.getName());
                                        if (upLevelPortInst != null) {
                                            if (upLevelPortInst.getPort().isBus()) {
                                                upLevelPortInstNet = upLevelPortInst.getNet();
                                            } else {
                                                upLevelPortInstNet = upLevelPortInst.getNet();
                                            }
                                            driverPort.add(new driverPortInfo(upLevelPortInstNet, upLevelPortInst, upLevelParent));
                                            EDIFPortInstList upLevelPortInstList;
                                            upLevelPortInstList = upLevelPortInstNet.getEDIFPortInstList();
                                            if (upLevelPortInstList.size() > 2) {
                                                isDirectConnection = false;
                                            } else {
                                                isDirectConnection = true;
                                            }
                                            
                                            for (EDIFPortInst next_net_portInst : upLevelPortInstList) {
                                                isInOutPortOfParent = false;
                                                if (next_net_portInst.getCellInst() == null) {
                                                    upLevelParent = next_net_portInst.getParentCell();
                                                    upLevelParentCellInfo = findParentAndCellInfo(logicalNetlist, upLevelParent);
                                                    upLevelModuleInst = logicalNetlist.getCellInstFromHierName(upLevelParentCellInfo.hierarchicalCellName);
                                                    isInOutPortOfParent = true;
                                                    portInstOnNet = next_net_portInst;
                                                    break;
                                                }
                                            }
                                        } else {
                                            isInOutPortOfParent = false;
                                        }
                                    }
                                    
                                    if (driverPort.size() > 0) {
                                        driverPortInfo netAndPortToRemove = driverPort.get(driverPort.size() - 1);
                                        if (netAndPortToRemove.net.getPortInsts().size() == 1) {
                                            for (EDIFPortInst ports : netAndPortToRemove.net.getPortInsts()) {
                                                if (ports.getCellInst() == null) {
                                                    continue;
                                                } else if ((ports.getCellInst().getCellType().getName().equals("OBUF")) || (ports.getCellInst().getCellType().getName().equals("IBUF"))) {
                                                    count_free_PIPO++;
                                                }
                                            }
                                        }
                                        if (portInst.getDirection() == EDIFDirection.INPUT) {
                                            if ((upLevelParent != topModule) && (isDirectConnection) && (!isInOutPortOfParent)) {
                                                // Create new PO port and net in top module
                                                count_newPIPO++;
                                            }
                                            
                                        } else if (portInst.getDirection() == EDIFDirection.OUTPUT) {
                                            // Create new PI port and net in top module
                                            count_newPIPO++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                delta = count_newPIPO - count_free_PIPO;
                cell_num = module.getCellInsts().size();
                moduleList.add(new ModuleData(instModlueName, moduleName, parent.getName(), cell_num, delta));
            }
        }
        
        // Pass data to the writer method and return the result.
        return InputDataWriter.writeInputDataMarkdown(moduleList, logicalNetlist);
    }    
    
    public static final class parentAndCellInfo {
        public String hierarchicalCellName = "";
        public String parentHierCellName = "";
        public String parentInstModuleName = "";
        public String currParent = "";
        public EDIFCellInst parentCellInst = new EDIFCellInst();
        public EDIFCell currParentCell = new EDIFCell();
        public EDIFCellInst currParentCellInst = new EDIFCellInst();
        public EDIFPortInst currParentPortInst = new EDIFPortInst();
        EDIFHierCellInst parentHierCellInst = null;
    }
    
    public static final class driverPortInfo{
        public final EDIFNet net;
        public final EDIFPortInst portInst;
        public final EDIFCell cell;
        public driverPortInfo(EDIFNet n, EDIFPortInst p, EDIFCell c) { this.net = n; this.portInst = p; this.cell = c; }
    }
    
    public static parentAndCellInfo findParentAndCellInfo(EDIFNetlist logicalNetlist, EDIFCell module) {
        parentAndCellInfo parentAndCellInfo = new parentAndCellInfo();
        List<String> topToSelectedModule = new ArrayList<>();
        EDIFCell parent = logicalNetlist.getTopCell();

        Queue<EDIFHierCellInst> queue = new LinkedList<EDIFHierCellInst>();
        queue.clear();
        queue.add(logicalNetlist.getTopHierCellInst());

        while (!queue.isEmpty()) {
            EDIFHierCellInst p = queue.poll();
            EDIFCellInst i = p.getInst();
            String curr = p.getFullHierarchicalInstName();
            if (p.getParent() != null) {
                parentAndCellInfo.currParent = p.getParent().getFullHierarchicalInstName();
            }
            for (EDIFCellInst i2 : i.getCellType().getCellInsts()) {
                queue.add(p.getChild(i2));
            }
            if (i.getCellType().getName().equals(module.getName())) {
                parentAndCellInfo.parentInstModuleName = i.getParentCell().getName();
                parentAndCellInfo.parentHierCellInst = p.getParent();
                parentAndCellInfo.parentCellInst = p.getParent().getInst();
                parentAndCellInfo.parentHierCellName = parentAndCellInfo.currParent;
                parentAndCellInfo.currParentCell = i.getParentCell();
                parentAndCellInfo.hierarchicalCellName = curr;
                break;
            }
        }
        return(parentAndCellInfo);
    }

    public enum PioDir {
        INPUT,
        OUTPUT
    }

    public static final class IOTraceResult {
        public String  startHierPort;            
        public boolean reachesTop;
        public String  topPortName;
        public EDIFDirection topPortDir;
        public String  ioKind;                   

        public java.util.List<String> chain = new java.util.ArrayList<String>();

        // best partial (even if !reachesTop):
        public String   furthestCellFullPath;    // TOP-relative path to highest cell reached
        public String   furthestPort;            // that cell's own interface port name (base)
        public Integer  furthestBitIndex;        // bit index on that port (null for scalar)
        public int      furthestLevelsUp;        // levels above leaf-parent
        public String   note;

        @Override
        public String toString() {
            return "start=" + startHierPort +
                    " reachesTop=" + reachesTop +
                    (reachesTop ? (" topPort=" + topPortName + " dir=" + topPortDir) : "") +
                    " ioKind=" + ioKind +
                    " furthest=" + (furthestCellFullPath==null? "-" : furthestCellFullPath + "." + furthestPort +
                    (furthestBitIndex==null? "" : "["+furthestBitIndex+"]")) +
                    " levelsUp=" + furthestLevelsUp +
                    " chain=" + chain +
                    (note==null? "" : " note=" + note);
        }
    }

    /* internal BFS state */
    private static final class TraceState {
        final EDIFCell cell;
        final EDIFNet net;
        final java.util.ArrayDeque<EDIFCellInst> up;   // instances to climb to TOP
        final java.util.List<String> chain;
        TraceState(EDIFCell c, EDIFNet n, java.util.ArrayDeque<EDIFCellInst> u, java.util.List<String> ch){
            this.cell = c;
            this.net = n;
            this.up = new java.util.ArrayDeque<EDIFCellInst>(u);
            this.chain = ch;
        }
    }

    /**
     * Trace from a leaf port instance toward TOP.
     * No string parsing: we use the EDIFPortInst directly and RapidWright's bus APIs.
     *
     * @param netlist            EDIF Netlist
     * @param hierLeafInstPath   TOP-relative instance path to the leaf instance (e.g. "u2/u3/u4")
     * @param leafPortInst       the actual EDIFPortInst at that leaf instance
     */
    public static IOTraceResult tracePortPathToTop(EDIFNetlist netlist, String hierLeafInstPath, EDIFPortInst leafPortInst, String leafPortStr) {
        IOTraceResult R = new IOTraceResult();
        EDIFCell top = netlist.getTopCell();

        R.startHierPort = leafPortStr;
        return R;
    }

    /**
     * Create/reuse a TOP PI/PO (with IBUF/OBUF) and thread the signal from the boundary
     * cell port **bit** up to TOP through pass-through ports.
     *
     * @param nl                   RapidWright EDIFNetlist
     * @param targetCellPath       TOP-relative path to the boundary cell 
     * @param targetCellPortBase   boundary cell's own port *base* name (no [bit])
     * @param targetBitIndex       null for scalar, or exact bit index for bus
     * @param requestedTopPortName desired TOP port name (auto-suffixed if taken)
     * @param dir                  PioDir.INPUT for module inputs, PioDir.OUTPUT for outputs
     * @return actual TOP port name used (may be suffixed)
     */
    public static String ensureTopPIOAndThreadFromCellPort(EDIFNetlist nl,
                                                           String targetCellPath,
                                                           String targetCellPortBase,
                                                           Integer targetBitIndex,
                                                           String requestedTopPortName,
                                                           PioDir dir) {
        if (targetCellPath == null) targetCellPath = "";
        targetCellPath = targetCellPath.replaceFirst("^top/?", "");

        EDIFCell top = nl.getTopCell();

        String[] toks = targetCellPath.isEmpty() ? new String[0] : targetCellPath.split("/");
        java.util.ArrayList<EDIFCellInst> chain = new java.util.ArrayList<EDIFCellInst>(toks.length);
        EDIFCell cursor = top;
        for (int i = 0; i < toks.length; i++) {
            String in = toks[i].trim();
            if (in.isEmpty()) continue;
            EDIFCellInst ci = cursor.getCellInst(in);
            if (ci == null) throw new IllegalStateException("Instance '" + in + "' not under '" + cursor.getName() + "'");
            chain.add(ci);
            cursor = ci.getCellType();
        }
        EDIFCellInst boundaryInst = chain.isEmpty() ? null : chain.get(chain.size() - 1);
        EDIFCell boundaryParent = (boundaryInst == null) ? top : boundaryInst.getParentCell();

        boolean isInput = (dir == PioDir.INPUT);
        
        EDIFPort topPort = getOrCreateTopPort(top, requestedTopPortName, isInput ? EDIFDirection.INPUT : EDIFDirection.OUTPUT);
        String actualTop = topPort.getName();
        EDIFNet padNet  = getOrCreateNet(top, makePadNetName(actualTop));
        EDIFNet coreNet = getOrCreateNet(top, makeCoreNetName(actualTop, isInput));

        if (isInput) {
            EDIFCell ibuf = ensurePrimitive(nl, "hdi_primitives", "IBUF");
            EDIFCellInst ibufInst = getOrCreateChild(top, sanitized(actualTop) + "_IBUF", ibuf);
            ensureOwnPortOnNet(padNet, topPort);
            connectPinToNet(padNet, ibufInst, "I");
            connectPinToNet(coreNet, ibufInst, "O");
        } else {
            EDIFCell obuf = ensurePrimitive(nl, "hdi_primitives", "OBUF");
            EDIFCellInst obufInst = getOrCreateChild(top, sanitized(actualTop) + "_OBUF", obuf);
            connectPinToNet(coreNet, obufInst, "I");
            connectPinToNet(padNet, obufInst, "O");
            ensureOwnPortOnNet(padNet, topPort);
        }
        if (targetCellPortBase.contains(":")) {
            int tmp = targetCellPortBase.indexOf(":");
            int idx = targetCellPortBase.lastIndexOf("[",tmp);
            targetCellPortBase = targetCellPortBase.substring(0, idx);
        }
        // Determine the boundary net in the parent of the boundary cell.
        // Prefer a direct lookup via the boundary cell type port; if that port doesn't exist, fall back to scanning nets.
        EDIFNet boundaryNet = null;
        EDIFPort boundaryPort = null;

        if (boundaryInst == null) {
            // No boundary (already at TOP)
            boundaryNet = coreNet;
            boundaryParent = top;
        } else {

            // Try to find a port on the boundary cell type by bus base or scalar name.
            boundaryPort = boundaryInst.getCellType().getPort(targetCellPortBase);

            if (boundaryPort != null) {
                // Use exact bit if bus
                boundaryNet = findParentNetForPortBit(boundaryParent, boundaryInst, boundaryPort, targetBitIndex);
            }

            if (boundaryNet == null) {
                // Fallback: scan parent nets to find the net that touches (boundaryInst, base[/bit])
                for (EDIFNet n : boundaryParent.getNets()) {
                    for (EDIFPortInst pi : n.getPortInsts()) {
                        if (!boundaryInst.equals(pi.getCellInst())) continue;
                        EDIFPort p = pi.getPort();
                        if (p == null) continue;
                        String haveBase = p.isBus() ? p.getBusName() : p.getName();
                        if (!haveBase.equals(targetCellPortBase)) continue;
                        if (p.isBus()) {
                            if (targetBitIndex != null && pi.getIndex() != targetBitIndex.intValue()) continue;
                            boundaryNet = p.getInternalNet(pi.getIndex());
                            boundaryPort = p; // inferred
                            targetBitIndex = Integer.valueOf(pi.getIndex());
                        } else {
                            // scalar
                            if (targetBitIndex != null && targetBitIndex.intValue() != 0) continue;
                            boundaryNet = pi.getNet();
                            boundaryPort = p;
                            targetBitIndex = null;
                        }
                        break;
                    }
                    if (boundaryNet != null) break;
                }
            }

            if (boundaryNet == null) {
                // Still nothing: create a new net and connect the boundary instance pin to it (if port exists).
                boundaryNet = boundaryParent.createNet(uniqueNetName(boundaryParent, targetCellPortBase + "__net"));
                if (boundaryPort == null) {
                    // Last resort: try to resolve the port by base again; if not present, we cannot attach a child pin.
                    boundaryPort = boundaryInst.getCellType().getPort(targetCellPortBase);
                }
                if (boundaryPort != null) {
                    if (boundaryPort.isBus()) {
                        if (targetBitIndex == null) throw new IllegalStateException("Bit index required for bus '" + targetCellPortBase + "'");
                        connectPinBitToNet(boundaryNet, boundaryInst, boundaryPort, targetBitIndex.intValue());
                    } else {
                        connectPinToNet(boundaryNet, boundaryInst, boundaryPort.getName());
                    }
                }
                // If boundaryPort is still null here, that means the child cell type truly has no such port.
                // In that pathological case there is no pin to connect; caller should re-check inputs.
            }
        }

        if (boundaryParent == top) {
            return actualTop; // nothing to stitch, TOP already has coreNet
        }

        // Add pass-through on boundaryParent and connect its own-port to boundaryNet
        String passBase = "pio_pass_" + sanitized(actualTop);
        EDIFPort pass = getOrCreatePort(boundaryParent, ensureUniquePortName(boundaryParent, passBase, EDIFDirection.INOUT), EDIFDirection.INOUT);
        ensureOwnPortOnNet(boundaryNet, pass);

        // Climb ancestors, reuse coreNet name at each level
        EDIFCell cur = boundaryParent;
        EDIFNet  curNet = boundaryNet;

        for (int i = chain.size() - 2; i >= -1; i--) {
            EDIFCellInst parentInst = (i >= 0) ? chain.get(i) : null;
            EDIFCell parent = (parentInst == null) ? top : parentInst.getParentCell();

            EDIFNet upNet = getOrCreateNet(parent, coreNet.getName());
            if (parentInst != null) connectPinToNet(upNet, parentInst, pass.getName());

            if (parent != top) {
                pass = getOrCreatePort(parent, ensureUniquePortName(parent, passBase, EDIFDirection.INOUT), EDIFDirection.INOUT);
                ensureOwnPortOnNet(upNet, pass);
            }

            cur = parent;
            curNet = upNet;
        }
        return actualTop;
    }

    /* Which primitives we traverse through in fabric. Extend if needed. */
    private static boolean isAllowedPrim(String prim){
        if (prim == null) return false;
        if (prim.startsWith("LUT")) return true;
        if (prim.startsWith("FD"))  return true;     // FDRE/FDSE/FDCE/FDPE...
        if (prim.startsWith("IBUF") || prim.startsWith("OBUF") || prim.startsWith("IOBUF")) return true;
        if (prim.startsWith("MUXF")) return true;    // MUXF7/MUXF8
        if ("CARRY4".equals(prim))   return true;
        return false;
    }

    /* Find which bit of 'port' corresponds to 'net' inside this cell. */
    private static Integer findBitIndexOfInternalNet(EDIFPort port, EDIFNet net){
        if (port == null || net == null || !port.isBus()) return null;
        java.util.List<EDIFNet> bits = port.getInternalNets();
        for (int i = 0; i < bits.size(); i++) {
            if (bits.get(i) == net) return Integer.valueOf(i);
        }
        return null;
    }

    /* Parent-level net touching (inst.port[bit]) — no strings, no parsing. */
    private static EDIFNet findParentNetForPortBit(EDIFCell parentCell, EDIFCellInst inst, EDIFPort port, Integer bitIdx){
        for (EDIFPortInst pi : inst.getPortInsts()) {
            if (pi.getPort() != port) continue;
            if (port.isBus()) {
                if (bitIdx == null) continue;
                if (pi.getIndex() == bitIdx.intValue()) return port.getInternalNet(bitIdx.intValue());
            } else {
                return pi.getNet();
            }
        }
        return null;
    }

    /* For a primitive instance in 'parentCell', collect nets on all its pins (bus-aware). */
    private static java.util.List<EDIFNet> getAllNetsOfChildInst(EDIFCell parentCell, EDIFCellInst child, EDIFNet exclude){
        java.util.ArrayList<EDIFNet> out = new java.util.ArrayList<EDIFNet>();
        for (EDIFPortInst pi : child.getPortInsts()) {
            EDIFPort p = pi.getPort();
            if (p == null) continue;
            EDIFNet nn = p.isBus() ? p.getInternalNet(pi.getIndex()) : pi.getNet();
            if (nn != null && nn != exclude) out.add(nn);
        }
        return out;
    }

    /* Idempotent own-port presence. */
    private static void ensureOwnPortOnNet(EDIFNet net, EDIFPort cellPort){
        for (EDIFPortInst pi : net.getPortInsts()) {
            if (pi.getCellInst() == null && pi.getPort() == cellPort) return;
        }
        net.createPortInst(cellPort);
    }

    /* Scalar connect (by name). */
    private static void connectPinToNet(EDIFNet net, EDIFCellInst inst, String portName){
        EDIFPort p = inst.getCellType().getPort(portName);
        if (p == null) throw new IllegalArgumentException("Port '"+portName+"' not on cell '"+inst.getCellType().getName()+"'");
        for (EDIFPortInst pi : net.getPortInsts()) {
            if (inst.equals(pi.getCellInst()) && pi.getPort() == p) return;
        }
        net.createPortInst(p, inst);
    }

    /* Bus bit connect (explicit bit index). */
    private static void connectPinBitToNet(EDIFNet net, EDIFCellInst inst, EDIFPort port, int bitIdx){
        // avoid duplicates
        for (EDIFPortInst pi : net.getPortInsts()) {
            if (inst.equals(pi.getCellInst()) && pi.getPort() == port && pi.getIndex() == bitIdx) return;
        }
        EDIFPortInst pi = net.createPortInst(port, bitIdx, inst);

    }
    
    private static String joinPath(String[] arr, int from, int toExclusive){
        if (toExclusive <= from) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < toExclusive; i++) {
            if (sb.length() > 0) sb.append('/');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /* Return the cell's own interface port that sits on this net (null if none). */
    private static EDIFPort ownPortOnNet(EDIFCell cell, EDIFNet net) {
        for (EDIFPortInst pi : net.getPortInsts()) {
            if (pi.getCellInst() == null) return pi.getPort();
        }
        return null;
    }

    /**
     * Remove B's pins from A's nets.
     *
     * @param parentA    parent cell (A)
     * @param childB     child instance under A (B)
     * @param portFilter null = all B pins; scalar "ready" = that scalar only;
     *                   bus base "denom" = all bits; exact bit "denom[3]" = that bit only
     * @return number of nets deleted
     */
    public static int removeChildPinsAndPruneNets(EDIFCell parentA, EDIFCellInst childB, String portFilter) {
        int deleted = 0;

        java.util.ArrayList<EDIFNet> nets = new java.util.ArrayList<EDIFNet>(parentA.getNets());
        for (int ni = 0; ni < nets.size(); ni++) {
            EDIFNet n = nets.get(ni);

            // First pass: decide if there are child pins from instances other than B
            boolean hasOtherChildPins = false;
            for (EDIFPortInst pi : n.getPortInsts()) {
                EDIFCellInst owner = pi.getCellInst();
                if (owner != null && !owner.equals(childB)) { hasOtherChildPins = true; break; }
            }

            // Collect B pins to remove (respecting portFilter)
            java.util.ArrayList<EDIFPortInst> toRemove = new java.util.ArrayList<EDIFPortInst>();
            for (EDIFPortInst pi : n.getPortInsts()) {
                if (!childB.equals(pi.getCellInst())) continue;
                if (portFilter == null || matchesPortFilter(pi, portFilter)) {
                    toRemove.add(pi);
                }
            }
            if (toRemove.isEmpty()) continue;

            // Remove the selected B pins
            for (int i = 0; i < toRemove.size(); i++) {
                n.removePortInst(toRemove.get(i));
            }

            // If no other child pins existed, this net was A↔B-only → delete it
            if (!hasOtherChildPins) {
                parentA.removeNet(n);
                deleted++;
            }
        }
        return deleted;
    }

    private static boolean matchesPortFilter(EDIFPortInst pi, String portFilter) {
        if (portFilter == null) return true;

        EDIFPort p = pi.getPort();
        if (p == null) return false;

        if (p.isBus()) {
            String base = p.getBusName();
            if (portFilter.equals(base)) return true; // match all bits of this bus
            String exact = base + "[" + pi.getIndex() + "]";
            return portFilter.equals(exact);
        } else {
            return portFilter.equals(p.getName());
        }
    }

    private static String sanitized(String s){ return s.replaceAll("[^A-Za-z0-9_]", "_"); }
    private static String ensureUniquePortName(EDIFCell cell, String base, EDIFDirection dir){
        if (cell.getPort(base) == null) return base;
        int i = 1;
        while (cell.getPort(base + "_" + i) != null) i++;
        return base + "_" + i;
    }
    private static EDIFPort getOrCreateTopPort(EDIFCell top, String requestedName, EDIFDirection dir){
        EDIFPort p = top.getPort(requestedName);
        if (p != null) return p;
        String unique = requestedName;
        int i = 0;
        while (top.getPort(unique) != null) unique = requestedName + "_" + (++i);
        return top.createPort(unique, dir, 1);
    }
    private static EDIFNet getOrCreateNet(EDIFCell cell, String name){
        EDIFNet n = cell.getNet(name);
        return (n != null) ? n : cell.createNet(name);
    }
    private static String uniqueNetName(EDIFCell cell, String base){
        String n = base;
        int i = 0;
        while (cell.getNet(n) != null) n = base + "_" + (++i);
        return n;
    }
    private static EDIFPort getOrCreatePort(EDIFCell cell, String name, EDIFDirection dir){
        EDIFPort p = cell.getPort(name);
        return (p != null) ? p : cell.createPort(name, dir, 1);
    }
    private static EDIFCellInst getOrCreateChild(EDIFCell parent, String instName, EDIFCell ref){
        EDIFCellInst ci = parent.getCellInst(instName);
        if (ci != null) return ci;
        String n = instName;
        int i = 0;
        while (parent.getCellInst(n) != null) n = instName + "_" + (++i);
        return parent.createChildCellInst(n, ref);
    }
    private static EDIFCell ensurePrimitive(EDIFNetlist nl, String libName, String primName){
        EDIFLibrary lib = nl.getLibrary(libName);
        if (lib == null) { lib = new EDIFLibrary(libName); nl.addLibrary(lib); }
        EDIFCell prim = lib.getCell(primName);
        return prim;
    }
    private static java.util.Map<String,EDIFDirection> map1(String k, EDIFDirection v){
        java.util.HashMap<String,EDIFDirection> m = new java.util.HashMap<String,EDIFDirection>();
        m.put(k, v);
        return m;
    }
    private static String makePadNetName(String actualTop){ return actualTop + "__PAD"; }
    private static String makeCoreNetName(String actualTop, boolean isInput){ return actualTop + (isInput ? "__I" : "__O"); }

}


