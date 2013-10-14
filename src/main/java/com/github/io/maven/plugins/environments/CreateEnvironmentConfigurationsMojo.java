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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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
public final class CreateEnvironmentConfigurationsMojo extends AbstractMojo {

    /** Suffix used for the properties files. */
    private static final String PROPERTIES_FILE_SUFFIX = ".properties";

    /** Directory, containing property files with common properties in them. */
    @Parameter
    private File commonPropertiesDirectory;

    /** Performs a check, so that all environment files can guaranteed to all have the same proprety keys. */
    @Parameter(required = false, defaultValue = "true")
    private boolean enforcePropertiesMustExist;

    /** List of environments that will be created. */
    @Parameter(required = true)
    private String[] environments;

    /** Used when verifying consistency & completeness between environment files. */
    private Properties initialEnvironmentProperties;

    /** Directory to create the environments. */
    @Parameter(required = true)
    private File outputDirectory;

    /** Whether to override files that already exist when creating an environment. */
    @Parameter(required = false, defaultValue = "true")
    private boolean overrideIfExists;

    /** Maven component that manages plugins for this session. */
    @Component(role = BuildPluginManager.class)
    private BuildPluginManager pluginManager;

    /** Maven component, representing the project being built. */
    @Component(role = MavenProject.class)
    private MavenProject project;

    /** Input directory, containing all the environment property files. */
    @Parameter(required = true)
    private File propertiesDirectory;

    /** Maven component, that controls the session. */
    @Component(role = MavenSession.class)
    private MavenSession session;

    /** Directory containing the environment template. */
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

    private void compareProperties(final Properties newProps) throws MojoExecutionException {
        if (initialEnvironmentProperties == null) {
            initialEnvironmentProperties = newProps;
            return;
        }
        final Set<Object> newKeys = newProps.keySet();
        final List<Object> missingKeys = new ArrayList<>();
        for (final Object key : initialEnvironmentProperties.keySet()) {
            if (newKeys.contains(key)) {
                newKeys.remove(key);
            } else {
                missingKeys.add(" > " + key);
            }
        }
        for (final Object newKey : newKeys) {
            missingKeys.add(" < " + newKey);
        }

        for (final Object key : missingKeys) {
            final String message = "Missing key: " + key;
            if (enforcePropertiesMustExist) {
                getLog().error(message);
            } else {
                getLog().warn(message);
            }
        }

        if ((missingKeys.size() > 0) && enforcePropertiesMustExist) {
            throw new MojoExecutionException("Environment files must be matching in which properties they include.");
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
        compareProperties(environmentProperties);
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
