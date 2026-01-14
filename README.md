# Ontology Hub Scripts

A collection of **Java** and **Shell** scripts designed to automate essential tasks for the *Ontology Hub* within the **PIONERA** project. These tools support query generation, Linked Open Vocabularies (LOV) backup, and project packaging.

## 🚧 Project Status

This repository is actively under development. APIs and commands may change between minor versions.

---

## Table of Contents

- [Context and Purpose](#context-and-purpose)
- [Key Features](#key-features)
- [Repository Structure](#repository-structure)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [How to Contribute](#how-to-contribute)
- [Roadmap](#roadmap)
- [Acknowledgments and Funding](#acknowledgments-and-funding)
- [Authors and Contact](#authors-and-contact)
- [License](#license)

---

## Context and Purpose

The *PIONERA Ontology Hub* serves as a central platform for storing, validating, and publishing ontologies used across project scenarios. This repository provides scripts that automate critical tasks such as SPARQL query generation, LOV backups, and ontology processing.

---

## Key Features

- Generate SPARQL queries using `createQueries.sh`.
- Backup LOV vocabularies with `lovBackup.sh`.
- Configure endpoints and parameters via example configuration files (`lov.example.config`).
- Build and package Java projects using Maven (`pom.xml`, `assembly.xml`).
- Advanced CLI tools for RDF processing and Elasticsearch indexing.

---

## Repository Structure

```text
Ontology-Hub-Scripts/
├── createQueries.sh           # Generate SPARQL queries
├── lovBackup.sh               # Backup LOV vocabularies
├── lov.example.config         # LOV configuration template
├── pom.xml                    # Maven build file
├── assembly.xml               # JAR packaging
├── src/main/java/org/lov/cli/ # CLI tools (Aggregator, Rdf2mongo, etc.)
├── src/main/resources/queries/ # Over 100 SPARQL query files
└── src/main/resources/mappings/ # JSON mappings for indexing
```

---

## Requirements

- **Java 17 or higher**
- **Maven 3.6+**
- **POSIX-compliant shell** (bash/zsh)
- Access to the Ontology Hub endpoint

---

## Installation

1. Verify your environment:

   ```bash
   java -version
   javadoc --version
   mvn -v
   ```

   Expected output:

   ```text
   openjdk version "17.0.17" 2025-10-21
   javadoc 17.0.17
   Apache Maven 3.9.11
   ```

2. Clone the repository:

   ```bash
   git clone https://github.com/ProyectoPIONERA/Ontology-Hub-Scripts.git
   cd Ontology-Hub-Scripts
   ```

3. Build the project:

   ```bash
   mvn clean package
   ```

   You should see **BUILD SUCCESS**. This will generate executable artifacts (e.g., a JAR file in `target/`).

---

## Usage

### Running Shell Scripts (from the root directory)

Rename `lov.example.config` to `lov.config` and update it according to your configuration.

- Generate SPARQL queries for LOV data:

   ```bash
   ./createQueries.sh
   ```

- Backup LOV vocabularies using a configuration file:

   ```bash
   ./lovBackup.sh --config lov.example.config
   ```

### Running Java CLI Tools (after compilation)

- Execute the **Aggregator** tool for RDF processing:

   ```bash
   java -cp target/classes org.lov.cli.Aggregator
   ```

- Execute the Elasticsearch indexing tool:

   ```bash
   java -cp target/classes org.lov.cli.ElasticsearchIndexLOV
   ```

**Notes:**
- Shell scripts require bash.
- Java tools depend on prior compilation (`mvn clean package`).

---

## How to Contribute

- Open *issues* to report bugs or request new features.
- Fork the repository and create branches following the project guidelines.
- Submit *pull requests* referencing related *issues*.

---

## Roadmap

- Additional scripts for PIONERA integration.
- Enhanced validation reporting.
- Extended documentation and testing.

---

## Acknowledgments and Funding

This work is part of the **PIONERA** project, partially funded by [grant/program].

---

## Authors and Contact

- PIONERA Ontologies Team
- Contact: *[Ontology Engineering Group](https://oeg.fi.upm.es)*, *[Universidad Politécnica de Madrid](https://www.upm.es/internacional)*.

---

## Funding

This work has received funding from the PIONERA project (Enhancing interoperability in data spaces through artificial intelligence), a project funded in the context of the call for Technological Products and Services for Data Spaces of the Ministry for Digital Transformation and Public Administration within the framework of the PRTR funded by the European Union (NextGenerationEU)

<div align="center">
  <img src="funding_label.png" alt="Logos financiación" width="900" />
</div>

## License

Ontology Hub Scripts is available under the **[Apache License 2.0](https://github.com/ProyectoPIONERA/Ontology-Hub-Scripts/blob/main/LICENSE)**.
