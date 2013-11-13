environments-maven-plugin
=========================
![Build Status](https://api.travis-ci.org/mikesena/environments-maven-plugin.png)

A Maven plugin to manage generation of multiple environment configurations and artefacts, using the power of Maven, and without the headaches of hacking the build lifecycle.

### Scenarios
* Build versions for multiple development environments, each with its own configuration
* Different product versions, such as a standard / deluxe.

The plugin was originally created so that separate deployable packages could be created for test environments and production.
This was achieved through this plugin creating multiple environment configurations, during the same build, which were then packaged by the Maven Assembly plugin into individual ZIP releases.

Installation
-------------
The plugin is available through Maven central (hosted by Sonatype).
```xml
<dependency>
    <groupId>com.github.mikesena.maven.plugins</groupId>
    <artifactId>environments-maven-plugin</artifactId>
    <version>0.0.17</version>
</dependency>
```

Goals
-----
Currently, only one goal is available / required for the plugin to work.
```xml
<executions>
    <execution>
        <phase>generate-sources</phase>
        <goals>
            <goal>create-environment-configurations</goal>
        </goals>
    </execution>
</executions>
```

Configuration
-------------
### Required Parameters
| Parameter | Description |
| --------- | ----------- |
| environments | List of environments that will be created. |
| outputDirectory | Directory to create the environments. |
| propertiesDirectory | Input directory, containing all the environment property files. |
| templateDirectory | Directory containing the environment template. |

#### Example
```xml
<configuration>
    <!-- Files here will be read and used for all environments. -->
    <commonPropertiesDirectory>src/main/properties</commonPropertiesDirectory>
    
    <!-- Environments to create.  Correlate to folders in 'preportiesDirectory'. -->
    <environments>
        <environment>env1</environment>
        <environment>env3</environment>
        <environment>env7</environment>
        <environment>prod</environment>
    </environments>
    
    <!-- Location of the environment configurations. -->
    <propertiesDirectory>src/main/properties/envs</propertiesDirectory>
    
    <!-- Template to use as the base of each environment. -->
    <templateDirectory>src/main/environment-template</templateDirectory>
    
    <!-- Where files can be placed on completion. -->
    <outputDirectory>${project.build.directory}/environments</outputDirectory>
</configuration>
````
### Optional Parameters
| Parameter | Description | Default |
| --------- | ----------- | ------- |
| commonPropertiesDirectory | Directory, containing property files with common properties in them. | |
| enforcePropertiesMustExist | Performs a check, so that all environment files can guaranteed to all have the same proprety keys. | true |
| filterOnFilenames | Whether directory & file names are filtered (allows files to be renamed based on a property). | true |
| overrideIfExists | Whether to override files that already exist when creating an environment. | true |


Usage Examples
--------------
