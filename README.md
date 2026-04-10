# Synthetic Benchmark Generator 

This repository contains the open-source implementation of the tool described in the paper: **"A Synthetic Benchmark Generator for Evaluating FPGA Incremental Tools"**. 

The framework provides an automated environment for generating synthetic FPGA benchmarks by bridging the high-level productivity of **Python** with the structural precision of the **RapidWright** framework using **JPype**.

## 1. Overview
The tool is designed to facilitate the rapid generation and physical evaluation of FPGA netlists. It consists of:
* **Java Backend**: Custom classes built on RapidWright for netlist manipulation and physical design processing.
* **Python Controller**: An orchestration script that manages the iterative loop, configuration, and result logging.

## 2. Prerequisites
Before running the tool, ensure your system meets the following requirements:
* **Java JDK**: Version 11 or 17.
* **Python**: Version 3.8+ with `jpype1` installed (`pip install jpype1`).
* **RapidWright Dependencies**: Ensure the `jars/` directory is present in the root of this repository.

## 3. Installation & Compilation
To maintain anonymity for peer review, compiled binaries are excluded from this repository. You must compile the source code locally before first use:

1.  Open a terminal in the project root.
2.  Run the Gradle wrapper to build the Java components:
    ```bash
    ./gradlew compileJava
    ```
    *Note: This process transforms the code in `src/` into runnable bytecode without requiring a global Gradle installation.*

## 4. Configuration
Update the absolute paths for your local environment and desire parameters value in the `config.json` at the ./python/src/rapidwright/ folder.

## 5. Usage
To start the benchmark generation loop:
```bash
python ./generateHistory.py --config config.json