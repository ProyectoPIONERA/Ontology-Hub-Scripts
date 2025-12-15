# Ontology Hub Scripts

Colección de scripts en **Java** y **Shell** para automatizar tareas del *Ontology Hub* en el proyecto **PIONERA**. Incluye herramientas para generación de consultas, respaldo de Linked Open Vocabularies (LOV) y empaquetado del proyecto.

## 🚧 Estado del Proyecto

Activamente en desarrollo. La API y los comandos pueden cambiar entre versiones menores.

---

## Tabla de Contenidos

- [Contexto y Propósito](#contexto-y-propósito)
- [Características Principales](#características-principales)
- [Estructura del Repositorio](#estructura-del-repositorio)
- [Requisitos](#requisitos)
- [Instalación](#instalación)
- [Uso](#uso)
- [Cómo Contribuir](#cómo-contribuir)
- [Hoja de Ruta](#hoja-de-ruta)
- [Agradecimientos y Financiación](#agradecimientos-y-financiación)
- [Autores y Contacto](#autores-y-contacto)
- [Licencia](#licencia)

---

## Contexto y Propósito

El *PIONERA Ontology Hub* actúa como punto central para almacenar, validar y publicar ontologías en los casos de uso del proyecto. Este repositorio contiene scripts que automatizan tareas clave como generación de consultas, respaldo de LOV y procesamiento de ontologías.

---

## Características Principales

- Generación de consultas mediante `createQueries.sh`.
- Respaldo de vocabularios LOV con `lovBackup.sh`.
- Configuración de endpoints y parámetros mediante archivos de ejemplo (`lov.example.config`).
- Construcción y empaquetado del proyecto Java con Maven (`pom.xml`, `assembly.xml`).
- Herramientas avanzadas en CLI para procesamiento RDF e indexación en Elasticsearch.

---

## Estructura del Repositorio

```text
Ontology-Hub-Scripts/
├── createQueries.sh           # Generar consultas SPARQL
├── lovBackup.sh               # Respaldo de vocabularios LOV
├── lov.example.config         # Plantilla de configuración LOV
├── pom.xml                    # Build con Maven
├── assembly.xml               # Empaquetado JAR
├── src/main/java/org/lov/cli/ # Herramientas CLI (Aggregator, Rdf2mongo, etc.)
├── src/main/resources/queries/ # +100 archivos SPARQL
└── src/main/resources/mappings/ # Mapeos JSON para indexación
```

---

## Requisitos

- **Java 17+**
- **Maven 3.6+**
- **Shell POSIX** (bash/zsh)
- Acceso al endpoint del Ontology Hub

---

## Instalación

1. Validar entorno:

   ```bash
   java -version
   javadoc --version
   mvn -v
   ```

   Salida esperada:

   ```text
   openjdk version "17.0.17" 2025-10-21
   javadoc 17.0.17
   Apache Maven 3.9.11
   ```

2. Clonar repositorio:

   ```bash
   git clone https://github.com/ProyectoPIONERA/Ontology-Hub-Scripts.git
   cd Ontology-Hub-Scripts
   ```

3. Construir proyecto:

   ```bash
   mvn clean package
   ```

   Debes ver el mensaje **BUILD SUCCESS**. Esto generará artefactos ejecutables (por ejemplo, un JAR en `target/`).

---

## Uso

### Ejecutar Scripts Shell (desde el directorio raíz)

Renombra `lov.example.config` a `lov.config` y edítalo según tu configuración.

- Generar consultas SPARQL para datos LOV:

   ```bash
   ./createQueries.sh
   ```

- Respaldar vocabularios LOV usando archivo de configuración:

   ```bash
   ./lovBackup.sh --config lov.example.config
   ```

### Ejecutar Herramientas Java CLI (después de compilar)

- Ejecutar herramienta **Aggregator** para procesamiento RDF:

   ```bash
   java -cp target/classes org.lov.cli.Aggregator
   ```

- Ejecutar herramienta de indexación en Elasticsearch:

   ```bash
   java -cp target/classes org.lov.cli.ElasticsearchIndexLOV
   ```

**Notas:**
- Los scripts Shell están basados en bash.
- Las herramientas Java dependen de la compilación previa (`mvn clean package`).

---

## Cómo Contribuir

- Abre *issues* para reportar errores o solicitar funcionalidades.
- Haz *fork* y crea ramas siguiendo el estilo del proyecto.
- Envía *pull requests* referenciando los *issues*.

---

## Hoja de Ruta

- Scripts adicionales para integración con PIONERA.
- Mejora en reportes de validación.
- Documentación y pruebas ampliadas.

---

## Agradecimientos y Financiación

Parte del proyecto **PIONERA**, financiado parcialmente por [grant/program].

---

## Autores y Contacto

- Equipo de Ontologías PIONERA  
- Contacto: *[Ontology Engineering Group](https://oeg.fi.upm.es)*, *[Universidad Politécnica de Madrid](https://www.upm.es/internacional)*.

---

## Licencia 🔓
Ontology Hub Scripts está disponible bajo la Licencia Apache 2.0.