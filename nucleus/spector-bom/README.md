# Spector Bill of Materials (BOM)

The `spector-bom` module provides centralized dependency management for all Spector modules and consumer projects.

## Usage

Import the BOM into your Maven `dependencyManagement` section:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.spectrayan</groupId>
            <artifactId>spector-bom</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
