import os
import argparse
import json
import time
import copy
import math
import shutil
import logging
import pandas as pd
import numpy as np
from datetime import datetime
from openpyxl import load_workbook
from openpyxl.styles import Alignment
from anytree import Node
import matplotlib
matplotlib.use('Agg') # This tells matplotlib to run in the background without Tkinter
import matplotlib.pyplot as plt

# ==============================================================================
# 0. DUAL-LOGGING SETUP (Execution Trace)
# ==============================================================================
log_filename = f"execution_trace_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"

# 1. Create the handlers manually
file_handler = logging.FileHandler(log_filename)
file_handler.setLevel(logging.INFO) # File keeps EVERYTHING (Full history)

console_handler = logging.StreamHandler()
console_handler.setLevel(logging.WARNING) # Terminal ONLY shows Warnings/Errors (Fast!)

# 2. Add them to the config
logging.basicConfig(
    level=logging.INFO, # Base level must be INFO so the file handler gets the data
    format='[%(asctime)s] %(levelname)s: %(message)s',
    handlers=[
        file_handler,
        console_handler
    ]
)
logger = logging.getLogger(__name__)

# ==============================================================================
# 1. PARSING & TREE BUILDING
# ==============================================================================

def parse_hierarchical_data_from_file(file_path):
    with open(file_path, 'r') as file:
        lines = file.readlines()

    header_found = False
    hierarchical_data = []

    for line in lines:
        if header_found:
            if '|' in line:
                hierarchical_data.append(line.strip())
        if 'Instance' in line:
            header_found = True
    return hierarchical_data

def parse_hierarchical_data_from_string(raw_md_text):
    """Parses the tree data directly from a multiline string instead of a file."""
    # Split the giant string returned by Java into individual lines
    raw_md_text = str(raw_md_text)
    lines = raw_md_text.splitlines()

    header_found = False
    hierarchical_data = []

    for line in lines:
        if header_found:
            if '|' in line:
                hierarchical_data.append(line.strip())
        if 'Instance' in line:
            header_found = True
    return hierarchical_data

def get_level(instance):    
    return (len(instance) - len(instance.lstrip()))

def build_data(hierarchical_data):
    parents = []
    data = []
    last_level = 1
    last_instance_name = None
    parent_name = None
    parent_idx = 0
    dfs_idx = 0
    parents.insert(parent_idx,parent_name)
    for line in hierarchical_data:
        parts = line.split('|')
        total_Delta_number = parts[4].strip()
        total_Cell_number = parts[3].strip()
        module_name = parts[2].strip()        
        level = get_level(parts[1])
        instance_name = parts[1].strip()
        instance_module_name = instance_name + module_name        
        if level > last_level:            
            parent_idx += 1            
            parent_name = last_instance_name
            parents.append(parent_name)
        elif level < last_level:
            parent_idx -= 1
            parents.pop()
            pop_count = int((last_level-level)/2)
            for i in range(pop_count-1):
                parents.pop()
            parent_name = parents[-1]        
        last_instance_name = instance_module_name          
        data.append((dfs_idx, instance_module_name, parent_name, int(total_Cell_number), int(total_Delta_number), instance_name, module_name))        
        last_level = level
        dfs_idx += 1
    return data

def create_tree_structure(data):
    nodes = {}
    for dfs_idx, name, parent, weight, delta, inst, module in data:
        if parent is None:
            nodes[name] = Node(name, weight=float(weight), delta=float(delta), wd_ratio=0, dfs_id=dfs_idx, instance_name=inst, module_name=module)
        else:
            nodes[name] = Node(name, parent=nodes[parent], weight=float(weight), delta=float(delta), wd_ratio=0, dfs_id=dfs_idx, instance_name=inst, module_name=module)
    return nodes

# ==============================================================================
# 2. GLOBAL MAP & CANDIDATE FINDING
# ==============================================================================

node_by_id = {}

def map_nodes_by_id(node):
    node_by_id[node.dfs_id] = node
    if node.children:
        for child in node.children:
            map_nodes_by_id(child)

def get_available_candidate_ids(root_node):
    candidate_ids = []
    if root_node is None or not root_node.children:
        return candidate_ids
        
    stack = list(root_node.children) 
    while stack:
        current_node = stack.pop()
        
        # Include ALL modules (leaves AND mid-level wrappers)
        candidate_ids.append(current_node.dfs_id)
        
        if current_node.children:
            stack.extend(current_node.children)
            
    return candidate_ids

# ==============================================================================
# 3. COST CALCULATION & SELECTION (Using Min-Max Scaling)
# ==============================================================================

def normalize_and_select_best(candidate_ids, TCR_Active, green_zone_start, green_zone_end, a, b, step_k, iter_i, folderpath):
    if not candidate_ids:
        return None, 0.0, {}

    raw_candidates = [node_by_id[nid] for nid in candidate_ids]
    
    # 1. SAFE Candidates (Used for Math AND Plotting)
    candidates = [
        node for node in raw_candidates 
        if getattr(node, 'parent', None) is not None 
        and node.weight <= green_zone_end
        and getattr(node, 'delta', 0.0) < 1500  # Prevent picking single modules that cause massive I/O explosions        
    ]
    
    # 2. FORBIDDEN Candidates (Used ONLY for Plotting, ignored by math)
    forbidden_candidates = [
        node for node in raw_candidates 
        if getattr(node, 'parent', None) is not None 
        and node.weight > green_zone_end
    ]
    
    if not candidates:
        return None, 0.0, {}

    # 1. Calculate Error (x) and Raw Delta (y) for SAFE candidates only
    for node in candidates:
        node.x = abs(node.weight - TCR_Active)
        node.y = getattr(node, 'delta', 0.0)

    # 2. Find boundaries for Min-Max Scaling (Safe candidates only)
    x_values = [node.x for node in candidates]
    y_values = [node.y for node in candidates]
    
    min_x, max_x = min(x_values), max(x_values)
    min_y, max_y = min(y_values), max(y_values)
    
    range_x = (max_x - min_x) if (max_x - min_x) > 0 else 1.0
    range_y = (max_y - min_y) if (max_y - min_y) > 0 else 1.0

    best_node = None
    best_score = None 

    # 3. Scale and Calculate Cost
    for node in candidates:
        node.x_normalized = (node.x - min_x) / range_x
        node.y_normalized = (node.y - min_y) / range_y
        node.cost = (a * node.x_normalized) + (b * node.y_normalized)
        
        if a > 0.5:
            current_score = (node.cost, node.y, node.x, getattr(node, 'instance_name', ''))
        else:
            current_score = (node.cost, node.x, node.y, getattr(node, 'instance_name', ''))
            
        if best_score is None or current_score < best_score:
            best_score = current_score
            best_node = node

    min_cost = best_score[0] if best_score else 0.0
    num_safe = len(candidates)
    num_total = len(candidates) + len(forbidden_candidates)

    metrics = {'min_x': min_x, 'max_x': max_x, 'min_y': min_y, 'max_y': max_y}
    return best_node, min_cost, metrics
"""
    # =========================================================================
    # PLOT 1: RAW PHYSICAL SPACE (Shows ALL Nodes: Safe + Forbidden)
    # =========================================================================
    plt.figure(figsize=(9, 6))

    # 1. Background Boxes
    start_box = max(0, green_zone_start) 
    plt.axvspan(start_box, green_zone_end, color='lightgreen', alpha=0.3, label='Green Zone Target')
    
    camera_limit = max(1000, TCR_Active * 3, green_zone_end * 1.1)
    plt.axvspan(green_zone_end, camera_limit * 2, color='lightcoral', alpha=0.2, label='Forbidden (Overshoot)')

    plt.axhline(y=0, color='black', linestyle='-', alpha=0.3, linewidth=1)
    plt.axvline(x=TCR_Active, color='blue', linestyle='--', alpha=0.8, linewidth=2, label=f'Current Target = {TCR_Active:.0f}')

    # 2. Plot SAFE Candidates (Green Dots)
    all_safe_weights = [n.weight for n in candidates]
    all_safe_deltas = [n.y for n in candidates] 
    plt.scatter(all_safe_weights, all_safe_deltas, color='cadetblue', alpha=0.8, edgecolors='white', zorder=4, label=f'Safe Candidates (N={num_safe})')

    # 3. Plot FORBIDDEN Candidates (Gray X's)
    if forbidden_candidates:
        all_forb_weights = [n.weight for n in forbidden_candidates]
        all_forb_deltas = [getattr(n, 'delta', 0.0) for n in forbidden_candidates] 
        plt.scatter(all_forb_weights, all_forb_deltas, color='gray', alpha=0.6, marker='x', zorder=3, label=f'Disqualified Nodes (N={len(forbidden_candidates)})')

    # 4. Plot the Winner
    if best_node:
        plt.scatter(best_node.weight, best_node.y, color='red', marker='*', s=300, edgecolors='black', zorder=5, label=f'Best Node: {best_node.instance_name}')

    plt.title(f"Total Modules: {num_total}\nIteration {iter_i} | Step {step_k} | a={a}, b={b}", fontweight='bold')
    plt.xlabel("Module Weight (Total Cell Count)", fontweight='bold')
    plt.ylabel(r"$\Delta$", fontweight='bold')
    
    plt.xlim(-10, camera_limit)
    plt.grid(True, linestyle='--', alpha=0.5)
    plt.legend(loc='upper center', bbox_to_anchor=(0.5, -0.15), ncol=3, fontsize=9)
    
    parent_folder = os.path.join(folderpath, f"plots_a{a}")
    sub_folder = f"Iteration_{iter_i}"
    output_dir = os.path.join(parent_folder, sub_folder)
    os.makedirs(output_dir, exist_ok=True)

    phys_filename = os.path.join(output_dir, f"PhysicalSpace_i{iter_i}_k{step_k}_a{a}.png")
    plt.savefig(phys_filename, bbox_inches='tight')
    plt.close()

    # =========================================================================
    # PLOT 2: NORMALIZED DECISION SPACE (Only Safe Nodes shown here)
    # =========================================================================
    
    plt.figure(figsize=(8, 6))
    all_x_norm = [n.x_normalized for n in candidates]
    all_y_norm = [n.y_normalized for n in candidates]

    plt.scatter(all_x_norm, all_y_norm, color='skyblue', alpha=0.7, edgecolors='gray', label=f'Evaluated Candidates (N={num_safe})')

    if best_node:
        plt.scatter(best_node.x_normalized, best_node.y_normalized, color='red', marker='*', s=300, edgecolors='black', zorder=5, label=f'Best Node: {best_node.instance_name}')

    plt.title(f"Normalized Decision Space\nIteration {iter_i} | Step {step_k} | a={a}, b={b}", fontweight='bold')
    plt.xlabel(r"Normalized Weight Error ($x_{norm}$)", fontweight='bold')
    plt.ylabel(r"Normalized Performance Delta ($y_{norm}$)", fontweight='bold')
    plt.xlim(-0.05, 1.05)
    plt.ylim(-0.05, 1.05)
    plt.grid(True, linestyle='--', alpha=0.5)
    plt.legend(loc='upper right')
    norm_filename = os.path.join(output_dir, f"NormalizedSpace_i{iter_i}_k{step_k}_a{a}.png")
    plt.savefig(norm_filename, bbox_inches='tight')
    plt.close()
    
    # =========================================================================

    metrics = {'min_x': min_x, 'max_x': max_x, 'min_y': min_y, 'max_y': max_y}
    return best_node, min_cost, metrics
"""
    
# ==============================================================================
# 4. RAPIDWRIGHT I/O AND SYNC (ORIGINAL SIMPLE VERSION)
# ==============================================================================

def write_in_file_for_RapidWright(p1, p2, p3, p4, p5):
    # Give RapidWright 3 seconds to clear its memory and reset its file watchers
    # before we throw the next command at it.
    time.sleep(3.0)
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    filename = f"ModuleName_{timestamp}.txt"
    folder_path = os.path.join(os.getcwd(), "RapidWrightInputData")
    os.makedirs(folder_path, exist_ok=True)
    file_path = os.path.join(folder_path, filename)

    with open(file_path, "w") as f:
        f.write(f"{p1}\n{p2}\n{p3}\n{p4}\n{p5}\n")
    logger.debug(f"Wrote RapidWright command: {p1}, {p2}, {p3}, {p4}, {p5}")

def wait_for_new_md_file(folder, known_files, timeout=600):
    logger.info("Waiting for RapidWright to produce a new .md file...")
    start_time = time.time()
    
    while True:
        if os.path.exists(folder):
            current_files = set(f for f in os.listdir(folder) if f.startswith("InputData_") and f.endswith(".md"))
        else:
            current_files = set()
            
        new_files = current_files - known_files

        if new_files:
            full_paths = [os.path.join(folder, f) for f in new_files]
            newest = max(full_paths, key=os.path.getmtime)
            time.sleep(1.0) 
            logger.info(f"New file detected: {os.path.basename(newest)}")
            return newest, current_files
            
        if (time.time() - start_time) > timeout:
            logger.error("Timeout waiting for RapidWright.")
            raise TimeoutError("RapidWright did not respond in time.")
            
        time.sleep(2)

def load_tree_from_file(file_path):
    hierarchical_data = parse_hierarchical_data_from_file(file_path)
    data = build_data(hierarchical_data)
    nodes_source = create_tree_structure(data)
    root_name = data[0][1] 
    new_root = nodes_source[root_name]
    node_by_id.clear()
    map_nodes_by_id(new_root)
    root_module_name = data[0][6]
    return new_root, root_module_name

def load_tree_from_data(raw_md_text):
    """Builds the tree directly from the Java output string."""
    hierarchical_data = parse_hierarchical_data_from_string(raw_md_text)
    data = build_data(hierarchical_data)
    nodes_source = create_tree_structure(data)
    root_name = data[0][1] 
    new_root = nodes_source[root_name]
    node_by_id.clear()
    map_nodes_by_id(new_root)
    root_module_name = data[0][6]
    return new_root, root_module_name

def extract_hierarchy_metrics(raw_md_text):
    """Parses the raw markdown table to calculate topology metrics."""
    raw_md_text = str(raw_md_text)
    lines = raw_md_text.strip().split('\n')
    data_lines = []
    
    for line in lines:
        if line.startswith('|') and not line.startswith('| Instance'):
            parts = line.split('|')
            if len(parts) > 2:
                instance_col = parts[1]
                name_stripped = instance_col.lstrip()
                
                if name_stripped: 
                    leading_spaces = len(instance_col) - len(name_stripped)
                    level = (leading_spaces - 1) // 2 
                    data_lines.append((level, name_stripped))
    
    total_nodes = len(data_lines)
    if total_nodes == 0:
        return "No hierarchy data found."
        
    max_depth = 0
    leaf_nodes = 0
    
    for i in range(total_nodes):
        current_level = data_lines[i][0]
        if current_level > max_depth:
            max_depth = current_level
            
        is_leaf = False
        if i == total_nodes - 1:
            is_leaf = True 
        else:
            next_level = data_lines[i+1][0]
            if next_level <= current_level:
                is_leaf = True
                
        if is_leaf:
            leaf_nodes += 1
            
    metrics = (
        f"=================================================\n"
        f" DESIGN HIERARCHY TOPOLOGY\n"
        f"=================================================\n"
        f" Total Nodes (Modules/Instances) : {total_nodes}\n"
        f" Number of Leaf Nodes            : {leaf_nodes}\n"
        f" Maximum Hierarchy Depth         : {max_depth}\n"
        f"=================================================\n"
    )
    return metrics

# ==============================================================================
# 5. MAIN EXECUTION LOOP
# ==============================================================================

def main(folder_src, folder_dest, folder_base, base_edf, base_dcp, TARGET_MODULE_MULTIPLIER, MAX_REDUCTION_PCT):
    logger.info("Starting Optimization Process")
    
    os.makedirs(folder_src, exist_ok=True) # Ensure source directory exists
    os.makedirs(folder_dest, exist_ok=True) # Ensure destination directory exists
    
    print("Initializing Synthetic Incremental Benchmark Generator...")
    
    # Copy the original base files into the generatedBench folder to trick Java 
    # into using it as the primary working directory for reading AND writing.
    working_edf = os.path.join(folder_src, os.path.basename(base_edf))
    working_dcp = os.path.join(folder_src, os.path.basename(base_dcp))
    shutil.copy2(base_edf, working_edf)
    shutil.copy2(base_dcp, working_dcp)
    
    # Initialize RapidWright Java object pointing to the copies in generatedBench
    syntheticIncBench = SyntheticIncrementalBenchmark(working_edf, working_dcp)
    
    a_values = np.linspace(0.1, 0.9, 9)

    hierarchy_saved = False

    stability_data = {}
    
    for a in a_values:
        a = round(a, 2)
        b = round(1 - a, 2)
        a_str = str(round(a, 2)) # Java expects strings!
        b_str = str(round(1 - a, 2))
        
        logger.info(f"==================================================")
        logger.info(f" SWEEP START: a = {a} | b = {b}")
        logger.info(f"==================================================")
        
        excel_data = []
        
        logger.info("Requesting reset to Source Design...")
        
        # Call to Java: Reset to source instantly in-memory
        raw_md_text = syntheticIncBench.processStep("backToTheSourceDesign", "NONE", "0", "0", "0", folder_src)
        current_root, root_module_name = load_tree_from_data(raw_md_text)

        # ==============================================================
        # SAVE HIERARCHY ONCE
        # ==============================================================
        if not hierarchy_saved:
            logger.info("Extracting and saving base design hierarchy...")
            metrics_text = extract_hierarchy_metrics(raw_md_text)
            
            hierarchy_filename = f"{root_module_name}_hierarchical_structure.txt"
            hierarchy_filepath = os.path.join(folder_src, hierarchy_filename)
            
            with open(hierarchy_filepath, "w") as f:
                f.write(metrics_text + "\n")
                f.write("=== Original Raw Hierarchy Text ===\n")
                f.write(str(raw_md_text))
                
            logger.info(f"SUCCESS: Saved Hierarchy Info to {hierarchy_filename}")
            
            # Flip the flag so this block never runs again for this design
            hierarchy_saved = True
        # ==============================================================

        i = 1 # Iteration Counter
        
        while True:
            logger.info(f"\n--- Iteration i = {i} ---")
                        
            if i==1:
                W_source_i = current_root.weight
                W_current_k_math = W_source_i
            else:
                W_source_i = W_current_k_math
                W_current_k = W_current_k_math
            
            # ==========================================================
            # HIERARCHY-AWARE TARGET CALCULATION
            # ==========================================================
            # 1. Get all candidates legally allowed to be removed
            candidate_ids = get_available_candidate_ids(current_root)
            safe_candidates = [
                node_by_id[nid] for nid in candidate_ids 
                if getattr(node_by_id[nid], 'parent', None) is not None
            ]
            
            if not safe_candidates:
                logger.warning("No safe candidates left to remove. Ending generation.")
                break
                
            # 2. Extract their weights (cell counts)
            candidate_weights = [node.weight for node in safe_candidates]
            
            # 3. Find the 75th percentile module size.
            representative_module_size = np.percentile(candidate_weights, 75)
            
            # 4. Set the Target Reduction to equal roughly 'n' representative modules
            raw_target_reduction = representative_module_size * TARGET_MODULE_MULTIPLIER
            
            # 5. SAFETY NET: Clip the reduction to be reasonable.
            # Minimum: Prevents stalling out on tiny leaves.
            # Maximum: Prevents gutting the design if modules are huge.
            max_allowed_cut = MAX_REDUCTION_PCT * W_source_i
            min_allowed_cut = np.percentile(candidate_weights, 25)
            TCR_Boundary = np.clip(raw_target_reduction, min_allowed_cut, max_allowed_cut)
            # ==========================================================
            
            TC = W_source_i - TCR_Boundary 
            
            # Give the algorithm 10% wiggle room based on the reduction target, minimum 15 cells
            Margin = max(0.10 * TCR_Boundary, 5.0) 
            
            UpperBound_Design = TC + Margin 
            LowerBound_Design = TC - Margin 
            
            logger.info(f"Targeting removal of ~{TCR_Boundary:.1f} cells (75th pct module size: {representative_module_size:.1f}, raw_target_reduction:{raw_target_reduction},min_allowed_cut:{min_allowed_cut},max_allowed_cut:{max_allowed_cut})")
            logger.info(f"Source Weight: {W_source_i} | Target Final Size (TC): {TC:.1f} | Green Zone: [{LowerBound_Design:.1f}, {UpperBound_Design:.1f}]")
            
            valid_designs = []
            k = 1 
            W_current_k = W_source_i  
            
            # --- EARLY STOPPING VARIABLES ---
            PATIENCE_LIMIT = 5        
            MAX_VALID_SEARCHES = 15   
            best_valid_delta = float('inf')
            steps_without_improvement = 0
            # --------------------------------
            
            while True:                
                if len(node_by_id) <= 1:
                    logger.warning("Only 1 node remaining in the tree. Breaking step loop.")
                    break
                    
                TCR_Active = W_current_k - TC
                safe_target = TCR_Active
                if TCR_Active < 0:
                    safe_target = Margin + TCR_Active
                
                gz_start_weight = W_current_k - UpperBound_Design
                gz_end_weight = W_current_k - LowerBound_Design

                logger.info(f"Step k={k} | Math Tracked Weight: {W_current_k} | Active TCR: {TCR_Active:.1f} | safe_target: {safe_target}")
                                
                candidate_ids = get_available_candidate_ids(current_root)
                if not candidate_ids:
                    logger.warning("No valid candidates found. Breaking step loop.")
                    break
                    
                selected_node, min_cost, metrics = normalize_and_select_best(candidate_ids, safe_target, gz_start_weight, gz_end_weight, a, b, k, i, folder_src)
                
                if selected_node is None:
                    logger.warning("Could not select node. Breaking step loop.")
                    break
                
                W_current_k -= selected_node.weight                
                
                raw_md_text = syntheticIncBench.processStep(selected_node.instance_name, selected_node.module_name, str(i), str(k), a_str, folder_src)
                current_root, root_module_name = load_tree_from_data(raw_md_text)
                
                Root_Delta = current_root.delta                             
                
                if LowerBound_Design <= W_current_k <= UpperBound_Design:
                    logger.info("Design is INSIDE Green Zone. Saving to valid list.")
                    valid_designs.append({
                        'step_k': k,
                        'root_delta': Root_Delta,
                        'weight': W_current_k
                    })

                   # --- EARLY STOPPING LOGIC ---
                    if Root_Delta < best_valid_delta:
                        best_valid_delta = Root_Delta
                        steps_without_improvement = 0 # Reset patience
                    else:
                        steps_without_improvement += 1 # I/O got worse
                        
                    if steps_without_improvement >= PATIENCE_LIMIT:
                        logger.info(f"EARLY STOPPING: I/O Delta hasn't improved in {PATIENCE_LIMIT} steps. Saving time!")
                        break
                        
                    if len(valid_designs) >= MAX_VALID_SEARCHES:
                        logger.info(f"EARLY STOPPING: Reached max limit of {MAX_VALID_SEARCHES} searches in the Green Zone.")
                        break
                    # ----------------------------

                    k += 1 
                elif W_current_k < LowerBound_Design:
                    logger.info("Design OVERSHOT Lower Bound. Halting removal steps.")
                    break 
                else:
                    logger.info("Design still ABOVE Green Zone. Continuing removal steps.")
                    k += 1                                    
            
             # --- EVALUATION PHASE ---
            if not valid_designs:
                logger.warning("Iteration failed to find any designs inside the Green Zone. Ending iteration loop for this 'a'.")
                break 
                
            best_valid = min(valid_designs, key=lambda x: x['root_delta'])
            k = best_valid['step_k']
            best_delta = best_valid['root_delta']

            if a not in stability_data:
                # Added 'weights' list to track cell count
                stability_data[a] = {'iterations': [], 'root_deltas': [], 'weights': []}
                
            stability_data[a]['iterations'].append(i)
            stability_data[a]['root_deltas'].append(best_delta)
            
            # Save the actual cell count (weight) of the winning design for this iteration
            stability_data[a]['weights'].append(best_valid['weight'])
            W_current_k_math = best_valid['weight']

            
            logger.info(f"Evaluating Valid Designs: Found best step k={k} with Root Delta={best_delta}")
            
            if best_delta > 2072:
                logger.warning(f"Best Delta ({best_delta}) > 2072 limit. Ending iteration loop for this 'a'.")
                break 
            else:
                edf_filename = f"{root_module_name}_i-{i}_s-{k}_a-{a}.edf" 
                dcp_filename = f"{root_module_name}_i-{i}_s-{k}_a-{a}.dcp" 
                
                src_edf = os.path.join(folder_src, edf_filename)
                dest_edf = os.path.join(folder_dest, edf_filename)
                
                if os.path.exists(src_edf):
                    shutil.copy2(src_edf, dest_edf)
                    logger.info(f"SUCCESS: Copied optimal design {edf_filename} to {folder_dest}")
                else:
                    logger.error(f"WARNING: Expected EDF file NOT FOUND at {src_edf}")

                src_dcp = os.path.join(folder_src, dcp_filename)
                dest_dcp = os.path.join(folder_dest, dcp_filename)
                
                if os.path.exists(src_dcp):
                    shutil.copy2(src_dcp, dest_dcp)
                    logger.info(f"SUCCESS: Copied optimal design {dcp_filename} to {folder_dest}")
                else:
                    logger.error(f"WARNING: Expected DCP file NOT FOUND at {src_dcp}")

                logger.info(f"Advancing to Iteration {i+1}. Informing RapidWright of selected design k={k}.")
                
                raw_md_text = syntheticIncBench.processStep("selectedDesign", "NONE", str(i), str(k), a_str, folder_src)
                current_root, root_module_name = load_tree_from_data(raw_md_text)
                
                i += 1                       
    
    # ==============================================================================
    # PLOT: INTERFACE STABILITY (Root Delta vs Iteration)
    # ==============================================================================
    logger.info(f"Generating Interface Stability Plot for {root_module_name}...")
    
    plt.figure(figsize=(10, 6))
    
    # Loop through the data we collected and plot a line for each 'a' value
    colors = plt.cm.viridis(np.linspace(0, 0.9, len(stability_data)))
    for (a_val, data), color in zip(stability_data.items(), colors):
        if data['iterations']: # Only plot if we actually recorded data
            plt.plot(data['iterations'], data['root_deltas'], marker='o', 
                     linewidth=2, color=color, label=f'a = {a_val}')

    plt.title(f"Interface Stability: Root Delta vs Iteration\nBenchmark: {root_module_name}", fontweight='bold', fontsize=14)
    plt.xlabel("Iteration", fontweight='bold', fontsize=12)
    plt.ylabel(r"Root $\Delta$ (Interface Stability)", fontweight='bold', fontsize=12)
    
    # Force the X-axis to only show whole numbers (since iterations are integers)
    ax = plt.gca()
    ax.xaxis.set_major_locator(matplotlib.ticker.MaxNLocator(integer=True))
    
    plt.grid(True, linestyle='--', alpha=0.6)
    plt.legend(title="Cost Weights", bbox_to_anchor=(1.05, 1), loc='upper left')
    
    # Save the plot in the source folder
    stability_plot_filename = os.path.join(folder_src, f"Interface_Stability_{root_module_name}.png")
    plt.savefig(stability_plot_filename, bbox_inches='tight')
    plt.close()
    
    logger.info(f"SUCCESS: Saved Interface Stability plot to {stability_plot_filename}")

    # ==============================================================================
    # PLOT 2: SIZE REDUCTION (Cell Count vs Iteration)
    # ==============================================================================
    logger.info(f"Generating Size Reduction Plot for {root_module_name}...")
    
    plt.figure(figsize=(10, 6))
    
    # Loop through the data and plot the cell count (weight) for each 'a' value
    for (a_val, data), color in zip(stability_data.items(), colors):
        if data['iterations']: 
            plt.plot(data['iterations'], data['weights'], marker='s', # Using 's' (square) markers to visually distinguish from the circular I/O plot
                     linewidth=2, color=color, label=f'a = {a_val}')

    plt.title(f"Design Size Reduction: Total Cell Count vs Iteration\nBenchmark: {root_module_name}", fontweight='bold', fontsize=14)
    plt.xlabel("Iteration", fontweight='bold', fontsize=12)
    plt.ylabel("Total Cell Count (Weight)", fontweight='bold', fontsize=12)
    
    # Force the X-axis to only show whole numbers
    ax = plt.gca()
    ax.xaxis.set_major_locator(matplotlib.ticker.MaxNLocator(integer=True))
    
    plt.grid(True, linestyle='--', alpha=0.6)
    plt.legend(title="Cost Weights", bbox_to_anchor=(1.05, 1), loc='upper left')
    
    # Save the plot in the source folder
    size_plot_filename = os.path.join(folder_src, f"Size_Reduction_{root_module_name}.png")
    plt.savefig(size_plot_filename, bbox_inches='tight')
    plt.close()
    
    logger.info(f"SUCCESS: Saved Size Reduction plot to {size_plot_filename}")

    logger.info("Optimization completely finished.")

if __name__ == "__main__":
    # 1. Initialize the argument parser
    parser = argparse.ArgumentParser(description="Run the Synthetic Incremental Benchmark Generator.")
    parser.add_argument('--config', default='config.json', help='Path to the JSON configuration file.')
    args = parser.parse_args()
    
    # 2. Read the JSON file
    try:
        with open(args.config, 'r') as file:
            config = json.load(file)
    except FileNotFoundError:
        print(f"Error: The configuration file '{args.config}' was not found.")
        exit(1)
    except json.JSONDecodeError:
        print(f"Error: The file '{args.config}' contains invalid JSON.")
        exit(1)
        
    # 3. SET UP RAPIDWRIGHT PATHS FROM CONFIG
    rw_path = config.get('rapidwright_path')
    if not rw_path or not os.path.exists(rw_path):
        print(f"Error: The RapidWright path '{rw_path}' does not exist or is missing from the config.")
        exit(1)

    os.environ["RAPIDWRIGHT_PATH"] = rw_path
    cp_jars = os.path.join(rw_path, "jars", "*")
    # Check both bin and gradle build folders for compiled classes
    cp_bin = os.path.join(rw_path, "bin")
    cp_gradle = os.path.join(rw_path, "build", "classes", "java", "main")
    os.environ["CLASSPATH"] = cp_bin + os.pathsep + cp_gradle + os.pathsep + cp_jars

    # 4. IMPORT JAVA/RAPIDWRIGHT LIBRARIES NOW THAT PATHS ARE SET
    import rapidwright
    import jpype
    from com.xilinx.rapidwright.util import SyntheticIncrementalBenchmark
        
    # 5. Extract the master folder paths
    master_folder_src = config.get('folder_src')
    master_folder_dest = config.get('folder_dest')
    folder_base = config.get('folder_base')
    TARGET_MODULE_MULTIPLIER = float(config.get("target_module_multiplier", 2.0))
    MAX_REDUCTION_PCT        = float(config.get("max_reduction_pct", 0.15))
    
    # Ensure the base folder exists
    if not folder_base or not os.path.exists(folder_base):
        print(f"Error: The base folder '{folder_base}' does not exist or is missing from the config.")
        exit(1)

    # 6. Scan the base folder for matching .edf and .dcp files
    print(f"Scanning '{folder_base}' for design pairs...")
    found_pairs = 0
    
    for filename in os.listdir(folder_base):
        if filename.endswith(".edf"):
            # Get the base name (e.g., 'sv_chip0' from 'sv_chip0.edf')
            base_name = filename[:-4] 
            
            # Construct the absolute paths for the input files
            edf_path = os.path.join(folder_base, filename)
            dcp_path = os.path.join(folder_base, base_name + ".dcp")
            
            # Check if the matching .dcp file exists
            if os.path.exists(dcp_path):
                found_pairs += 1
                print(f"\n" + "="*60)
                print(f" PROCESSING PAIR #{found_pairs}: {base_name}")
                print(f" ="*60)
                            
                design_folder_src = os.path.join(master_folder_src, base_name)
                design_folder_dest = os.path.join(master_folder_dest, base_name)
                # ==========================================
                # START TIMER
                # ==========================================
                start_time = time.time()
                                
                # Run the actual benchmark optimization
                main(design_folder_src, design_folder_dest, folder_base, edf_path, dcp_path, TARGET_MODULE_MULTIPLIER, MAX_REDUCTION_PCT)
                
                # ==========================================
                # STOP TIMER & SAVE
                # ==========================================
                end_time = time.time()
                elapsed_seconds = end_time - start_time
                
                # Convert to hours, minutes, and seconds for easy reading
                hours, rem = divmod(elapsed_seconds, 3600)
                minutes, seconds = divmod(rem, 60)
                formatted_time = f"{int(hours)}h {int(minutes)}m {seconds:.2f}s"
                
                print(f" Benchmark {base_name} completed in {formatted_time}")
                
                # Save the runtime data as a JSON file in the benchmark's source folder
                runtime_data = {
                    "benchmark_name": base_name,
                    "total_runtime_seconds": round(elapsed_seconds, 2),
                    "formatted_runtime": formatted_time
                }
                
                runtime_filepath = os.path.join(design_folder_src, f"{base_name}_runtime.json")
                with open(runtime_filepath, "w") as f:
                    json.dump(runtime_data, f, indent=4)
                    
            else:
                print(f"Warning: Found '{filename}' but missing matching .dcp file. Skipping.")

    if found_pairs == 0:
        print("No matching .edf and .dcp pairs were found in the base folder.")
    else:
        print(f"\nFinished processing all {found_pairs} design pairs!")