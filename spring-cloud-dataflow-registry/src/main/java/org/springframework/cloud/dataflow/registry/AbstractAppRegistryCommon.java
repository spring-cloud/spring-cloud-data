package org.springframework.cloud.dataflow.registry;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.domain.AppRegistration;
import org.springframework.cloud.dataflow.registry.support.ResourceUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link AppRegistryCommon} implementation common for the Classic and the Skipper modes.
 * 
 * @author Christian Tzolov
 */
public abstract class AbstractAppRegistryCommon implements AppRegistryCommon {

	private static final Logger logger = LoggerFactory.getLogger(AbstractAppRegistryCommon.class);

	public static final String METADATA_KEY_SUFFIX = "metadata";

	private ResourceLoader resourceLoader;

	public AbstractAppRegistryCommon(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	@Override
	public Resource getAppResource(AppRegistration appRegistration) {
		return this.resourceLoader.getResource(appRegistration.getUri().toString());
	}

	@Override
	public Resource getAppMetadataResource(AppRegistration appRegistration) {
		return appRegistration.getMetadataUri() != null ? this.resourceLoader.getResource(
				appRegistration.getMetadataUri().toString()) : null;
	}

	protected Properties loadProperties(Resource resource) {
		try {
			return PropertiesLoaderUtils.loadProperties(resource);
		}
		catch (IOException e) {
			throw new RuntimeException("Error reading from " + resource.getDescription(), e);
		}
	}

	protected static final Function<Map.Entry<Object, Object>, AbstractMap.SimpleImmutableEntry<String, URI>> toStringAndUriFunc = kv -> {
		try {
			return new AbstractMap.SimpleImmutableEntry<>((String) kv.getKey(), new URI((String) kv.getValue()));
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	};

	@Override
	public List<AppRegistration> importAll(boolean overwrite, Resource... resources) {
		return Stream.of(resources)
				.map(this::loadProperties)
				.flatMap(prop -> prop.entrySet().stream()
						.map(toStringAndUriFunc)
						.flatMap(kv -> toValidAppRegistration(kv, metadataUriFromProperties(kv.getKey(), prop)))
						.filter(a -> isOverwrite(a, overwrite))
						.map(ar -> save(ar)))
				.collect(Collectors.toList());
	}

	protected abstract boolean isOverwrite(AppRegistration app, boolean overwrite);

	/**
	 * Builds a {@link Stream} from key/value mapping.
	 * @return
	 * <ul>
	 * <li>valid AppRegistration as single element Stream</li>
	 * <li>silently ignores well malformed metadata entries (0 element Stream) or</li>
	 * <li>fails otherwise.</li>
	 * </ul>
	 *
	 * @param kv key/value representing app key (key) and app URI (value)
	 * @param metadataURI metadataUri computed from a given app key
	 */
	protected Stream<AppRegistration> toValidAppRegistration(Map.Entry<String, URI> kv, URI metadataURI) {
		String key = kv.getKey();
		String[] tokens = key.split("\\.");
		if (tokens.length == 2) {
			String name = tokens[1];
			ApplicationType type = ApplicationType.valueOf(tokens[0]);
			URI appURI = warnOnMalformedURI(key, kv.getValue());

			String version = getVersionOrBroken(appURI.toString());

			return Stream.of(new AppRegistration(name, type, version, appURI, metadataURI));
		}
		else {
			Assert.isTrue(tokens.length == 3 && METADATA_KEY_SUFFIX.equals(tokens[2]),
					"Invalid format for app key '" + key + "'in file. Must be <type>.<name> or <type>.<name>"
							+ ".metadata");
			return Stream.empty();
		}
	}

	private String getVersionOrBroken(String uri) {
		try {
			Resource appResource = resourceLoader.getResource(uri);

			return ResourceUtils.getResourceVersion(appResource);
		}
		catch (IllegalStateException ise) {
			logger.warn("", ise);
			return "broken";
		}
	}

	protected URI metadataUriFromProperties(String key, Properties properties) {
		String metadataValue = properties.getProperty(key + "." + METADATA_KEY_SUFFIX);
		try {
			return metadataValue != null ? warnOnMalformedURI(key, new URI(metadataValue)) : null;
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	protected URI warnOnMalformedURI(String key, URI uri) {
		if (StringUtils.isEmpty(uri)) {
			logger.warn(String.format("Error when registering '%s': URI is required", key));
		}
		else if (!StringUtils.hasText(uri.getScheme())) {
			logger.warn(
					String.format("Error when registering '%s' with URI %s: URI scheme must be specified", key, uri));
		}
		else if (!StringUtils.hasText(uri.getSchemeSpecificPart())) {
			logger.warn(String.format("Error when registering '%s' with URI %s: URI scheme-specific part must be " +
					"specified", key, uri));
		}
		return uri;
	}

}
