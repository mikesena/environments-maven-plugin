package com.github.io.maven.plugins.environments;

// CHECKSTYLE SUPPRESS AvoidStaticImport FOR 6 LINES
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

/**
 * The create-environment-configurations goal takes a directory as a template and creates a version related to a
 * particular environment, based on the contents of a properties file.
 * 
 * @author Michael Sena
 * @threadSafe
 */
@Execute(goal = "create-environment-configurations", phase = LifecyclePhase.GENERATE_RESOURCES)
@Mojo(name = "create-environment-configurations", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class CreateEnvironmentConfigurationsMojo extends AbstractMojo {

    private static final String PROPERTIES_FILE_SUFFIX = ".properties";

    @Parameter
    private File commonPropertiesDirectory;

    @Parameter(required = true)
    private String[] environments;

    @Parameter(required = true)
    private File outputDirectory;

    @Parameter(required = false, defaultValue = "true")
    private boolean overrideIfExists;

    @Component(role = BuildPluginManager.class)
    private BuildPluginManager pluginManager;

    @Component(role = MavenProject.class)
    private MavenProject project;

    @Parameter(required = true)
    private File propertiesDirectory;

    @Component(role = MavenSession.class)
    private MavenSession session;

    @Parameter(required = true)
    private File templateDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Creating configurations for " + environments.length + " environment(s).");
        loadCommonProperties();
        for (final String environment : environments) {
            createEnvironment(environment);
        }
    }

    private void copyResources(final File environmentOutputDirectory) throws MojoExecutionException {
        getLog().info("Copying across environment files.");
        executeMojo(plugin("org.apache.maven.plugins", "maven-resources-plugin", "2.6"), goal("copy-resources"),
                        configuration(element("outputDirectory", environmentOutputDirectory.getPath()), element(
                                        "resources", element("resource", element("directory", templateDirectory
                                                        .getPath()), element("filtering", "true")))),
                        executionEnvironment(project, session, pluginManager));
    }

    private void createEnvironment(final String environment) throws MojoExecutionException {
        getLog().info("Creating environment: " + environment);
        final Properties environmentProperties = getEnvironmentProperties(environment);
        final Properties originalProperties = (Properties) project.getProperties().clone();
        final File environmentOutputDirectory = new File(outputDirectory, environment);
        if (environmentOutputDirectory.exists()) {
            if (overrideIfExists) {
                try {
                    FileUtils.deleteDirectory(environmentOutputDirectory);
                } catch (final IOException e) {
                    throw new MojoExecutionException("Unable to delete existing environment output directory: "
                                    + environmentOutputDirectory.getPath(), e);
                }
            } else {
                getLog().warn("Environment Exists; skipping: " + environment);
                return;
            }
        }
        project.getProperties().putAll(environmentProperties);
        copyResources(environmentOutputDirectory);
        resetProperties(originalProperties);
    }

    private Properties getEnvironmentProperties(final String environment) throws MojoExecutionException {
        getLog().debug("Reading properties file for environment: " + environment);
        final File propertiesFile = new File(propertiesDirectory, environment + PROPERTIES_FILE_SUFFIX);
        final Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propertiesFile));
        } catch (final FileNotFoundException e) {
            throw new MojoExecutionException("Missing required file: " + propertiesFile.getPath(), e);
        } catch (final IOException e) {
            throw new MojoExecutionException("Unable to read properties file: " + propertiesFile.getPath(), e);
        }
        return properties;
    }

    private void loadCommonProperties() throws MojoExecutionException {
        final Properties properties = new Properties();
        if ((commonPropertiesDirectory != null) && commonPropertiesDirectory.exists()) {
            try {
                for (final File file : commonPropertiesDirectory.listFiles()) {
                    if (!file.isDirectory() && file.getName().endsWith(PROPERTIES_FILE_SUFFIX)) {
                        properties.load(new FileInputStream(file));
                    }
                }
            } catch (final IOException e) {
                throw new MojoExecutionException("Unable to load common properties files.", e);
            }
        }
        project.getProperties().putAll(properties);
    }

    private void resetProperties(final Properties originalProperties) {
        getLog().debug("Resetting properties back to their original.");
        project.getProperties().clear();
        project.getProperties().putAll(originalProperties);
    }
}
