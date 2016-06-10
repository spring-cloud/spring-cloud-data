/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.configuration.metadata;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataGroup;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.jar.JarFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Used to retrieve metadata about the configuration properties that can alter an application's behavior.
 * @author Eric Bottard
 */
public class ApplicationConfigurationMetadataResolver {

	private static final String CONFIGURATION_METADATA_PATTERN = "classpath*:/META-INF/spring-configuration-metadata.json";

	private static final String DATAFLOW_PROPERTIES = "classpath*:/META-INF/dataflow.properties";

	public static final String CONFIGURATION_CLASSES = "configuration.classes";

	public static final String CONFIGURATION_PROPERTIES = "configuration.properties";

	public static final String PRIMARY_PREFIX = "primary.prefix";

	private final Set<String> globalWhiteListedProperties = new HashSet<>();

	private final Set<String> globalWhiteListedClasses = new HashSet<>();

	public ApplicationConfigurationMetadataResolver() {
		JarFile.registerUrlProtocolHandler();
		try {
			Resource[] globalResources = new PathMatchingResourcePatternResolver(ApplicationConfigurationMetadataResolver.class.getClassLoader()).getResources(DATAFLOW_PROPERTIES);
			loadWhiteLists(globalResources, globalWhiteListedClasses, globalWhiteListedProperties);
		}
		catch (IOException e) {
			throw new RuntimeException("Error reading global white list of configuration properties", e);
		}
	}

	public List<ConfigurationMetadataProperty> listProperties(Resource app) {
		return listProperties(app, false);
	}

	/**
	 * Return metadata about configuration properties that are documented via
	 * <a href="http://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html">
	 * Spring Boot configuration metadata</a> and visible in an app.
	 * @param app a Spring Cloud Stream app; typically a Boot uberjar,
	 *            but directories are supported as well
	 */
	public List<ConfigurationMetadataProperty> listProperties(Resource app, boolean exhaustive) {
		List<ConfigurationMetadataProperty> result = new ArrayList<>();
		ClassLoader moduleClassLoader = null;
		try {
			File moduleFile = app.getFile();
			Archive archive = moduleFile.isDirectory() ? new ExplodedArchive(moduleFile) : new JarFileArchive(moduleFile);
			moduleClassLoader = createClassLoader(archive);
			ResourcePatternResolver moduleResourceLoader = new PathMatchingResourcePatternResolver(moduleClassLoader);


			Collection<String> whiteListedClasses = new HashSet<>(globalWhiteListedClasses);
			Collection<String> whiteListedProperties = new HashSet<>(globalWhiteListedProperties);

			loadWhiteLists(moduleResourceLoader.getResources(DATAFLOW_PROPERTIES), whiteListedClasses, whiteListedProperties);
			String primaryPrefix = primaryPrefix(app);

			ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();
			for (Resource r : moduleResourceLoader.getResources(CONFIGURATION_METADATA_PATTERN)) {
				builder.withJsonResource(r.getInputStream());
			}

			for (ConfigurationMetadataGroup group : builder.build().getAllGroups().values()) {
				if (group.getId().equals(primaryPrefix)) {
					for (ConfigurationMetadataProperty property : group.getProperties().values()) {
						result.add(unprefix(property));
					}
				}
				else if (exhaustive || isWhiteListed(group, whiteListedClasses)) {
					result.addAll(group.getProperties().values());
				} // Props in the root group have an id that looks prefixed itself. Handle here
				else if ("_ROOT_GROUP_".equals(group.getId())) {
					for (ConfigurationMetadataProperty property : group.getProperties().values()) {
						int lastDot = property.getId().lastIndexOf('.');
						String prefix = lastDot > 0 ? property.getId().substring(0, lastDot) : "";
						if (prefix.equals(primaryPrefix)) {
							result.add(unprefix(property));
						}
						else if (isWhiteListed(property, whiteListedProperties)) {
							result.add(property);
						}
					}
				}
				else { // Look for per property WL
					for (ConfigurationMetadataProperty property : group.getProperties().values()) {
						if (isWhiteListed(property, whiteListedProperties)) {
							result.add(property);
						}
					}
				}
			}

		}
		catch (Exception e) {
			throw new RuntimeException("Exception trying to list configuration properties for application " + app, e);
		}
		finally {
			if (moduleClassLoader instanceof Closeable) {
				try {
					((Closeable) moduleClassLoader).close();
				}
				catch (IOException e) {
					// ignore
				}
			}
		}
		return result;
	}

	public String primaryPrefix(Resource app) {
		ClassLoader moduleClassLoader = null;
		try {
			File moduleFile = app.getFile();
			Archive archive = moduleFile.isDirectory() ? new ExplodedArchive(moduleFile) : new JarFileArchive(moduleFile);
			moduleClassLoader = createClassLoader(archive);
			ResourcePatternResolver moduleResourceLoader = new PathMatchingResourcePatternResolver(moduleClassLoader);
			String primaryPrefix = null;
			for (Resource resource : moduleResourceLoader.getResources(DATAFLOW_PROPERTIES)) {
				Properties properties = new Properties();
				properties.load(resource.getInputStream());
				String prefix = properties.getProperty(PRIMARY_PREFIX);
				if (prefix != null) {
					Assert.isNull(primaryPrefix, String.format("Multiple primary prefixes have been declared: '%s' and '%s'", prefix, primaryPrefix));
					primaryPrefix = prefix;
				}
			}
			return primaryPrefix;
		}
		catch (Exception e) {
			throw new RuntimeException("An error occurred while reading app metadata", e);
		}
		finally {
			if (moduleClassLoader instanceof Closeable) {
				try {
					((Closeable) moduleClassLoader).close();
				}
				catch (IOException e) {
					// ignore
				}
			}
		}

	}

	/**
	 * For properties that are deemed to belong to the "main" group, return a copy that uses no qualifying prefix.
	 */
	private ConfigurationMetadataProperty unprefix(ConfigurationMetadataProperty original) {
		ConfigurationMetadataProperty result = new ConfigurationMetadataProperty();
		result.setDefaultValue(original.getDefaultValue());
		result.setDeprecation(original.getDeprecation());
		result.setDescription(original.getDescription());
		result.setName(original.getName());
		int lastDot = original.getId().lastIndexOf('.');
		result.setId(original.getId().substring(lastDot + 1)); // removes prefix, will be -1+1=0 if no dot
		result.setShortDescription(original.getShortDescription());
		result.setType(original.getType());
		return result;
	}

	/**
	 * Loads white lists of properties and group classes and add them to the given collections.
	 */
	private void loadWhiteLists(Resource[] resources, Collection<String> classes, Collection<String> props) throws IOException {
		for (Resource resource : resources) {
			Properties properties = new Properties();
			properties.load(resource.getInputStream());
			classes.addAll(Arrays.asList(StringUtils.delimitedListToStringArray(properties.getProperty(CONFIGURATION_CLASSES), ",", " ")));
			props.addAll(Arrays.asList(StringUtils.delimitedListToStringArray(properties.getProperty(CONFIGURATION_PROPERTIES), ",", " ")));
		}
	}

	/**
	 * Return whether a single property has been white listed as being a "main" configuration property.
	 */
	private boolean isWhiteListed(ConfigurationMetadataProperty property, Collection<String> properties) {
		return properties.contains(property.getId());
	}

	/**
	 * Return whether a configuration property group (class) has been white listed as being a "main" group.
	 */
	private boolean isWhiteListed(ConfigurationMetadataGroup group, Collection<String> classes) {
		Set<String> sourceTypes = group.getSources().keySet();
		return !sourceTypes.isEmpty() && classes.containsAll(sourceTypes);
	}

	/**
	 * Return a {@link ClassLoader} for accessing resources in the provided
	 * {@link Archive}. The caller is responsible for disposing of the
	 * class loader.
	 * @param archive the archive for which to return a class loader
	 * @return class loader for the given archive
	 * @throws Exception if the class loader cannot be created
	 */
	protected ClassLoader createClassLoader(Archive archive) throws Exception {
		return new ClassLoaderExposingJarLauncher(archive).createClassLoader();
	}


	/**
	 * Extension of {@link AppJarLauncher} used for exposing a {@link ClassLoader}
	 * for the provided {@link Archive}.
	 */
	private static class ClassLoaderExposingJarLauncher extends AppJarLauncher {

		public ClassLoaderExposingJarLauncher(Archive archive) {
			super(archive);
		}

		protected ClassLoader createClassLoader() throws Exception {
			List<Archive> classPathArchives = getClassPathArchives();
			return createClassLoader(classPathArchives);
		}
	}

}
