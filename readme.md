# RDF XML to N-Triples Converter

##### Mohamed IFQIR and Esmaà KADRI

## Description

This Java application converts RDF (Resource Description Framework) XML files to the N-Triples format. It provides a robust solution for transforming RDF XML documents into a standardized, line-based representation of RDF data.

## Features

- Dynamically extracts and handles XML namespaces
- Supports various RDF element types
- Generates blank nodes for resources without explicit identifiers
- Handles literal values with optional datatypes and language tags
- Eliminates duplicate triples
- Escapes special characters in N-Triples format

## Prerequisites

- Java Development Kit (JDK) 8 or higher
- A valid RDF XML file to convert

## Compilation

### On Ubuntu/Linux

1. Ensure you have Java installed:

   ```bash
   sudo apt update
   sudo apt install default-jdk
   ```
2. Compile the Java file:

   ```bash
   javac RdfToNTriples.java
   ```

### On Windows

1. Install Java JDK from the official Oracle website or use OpenJDK
2. Open Command Prompt and navigate to the project directory:

   ```cmd
   javac RdfToNTriples.java
   ```

## Execution

### On Ubuntu/Linux

```bash
java RdfToNTriples input.rdf
```

### On Windows

```cmd
java RdfToNTriples input.rdf
```

## Example

Input an RDF XML file as a command-line argument:

```bash
java RdfToNTriples example.rdf
```

This will output the converted N-Triples to the console.

## Supported RDF XML Features

- Resource identification via `rdf:about`
- Blank node generation via `rdf:nodeID`
- Literal values with optional:
  - Datatypes (`rdf:datatype`)
  - Language tags (`xml:lang`)
- Nested resource descriptions

## Namespace Handling

The converter automatically:

- Recognizes and expands namespace prefixes
- Handles default namespaces
- Includes standard RDF and XML namespaces

## Error Handling

- Displays usage instructions if no input file is provided
- Prints error messages for file parsing issues

## Limitations

- Assumes well-formed RDF XML input
- May require modification for extremely complex RDF structures

## License

MIT

## Contributing

Contributions are welcome! Please submit pull requests or open issues on the project repository.

## Contact

mohamedifqir99@gmail.com
