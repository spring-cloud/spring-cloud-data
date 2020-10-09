/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.configuration.metadata.container;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cloud.dataflow.configuration.metadata.AppMetadataResolutionException;
import org.springframework.cloud.dataflow.configuration.metadata.container.authorization.RegistryAuthorizer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Leverages the Docker Registry HTTP V2 API to retrieve the configuration object and the labels
 * form the specified image.
 *
 * @author Christian Tzolov
 */
public class DefaultContainerImageMetadataResolver implements ContainerImageMetadataResolver {

	private static final List<String> SUPPORTED_MANIFEST_MEDIA_TYPES =
			Collections.unmodifiableList(Arrays.asList(ContainerImageMetadataProperties.OCI_IMAGE_MANIFEST_MEDIA_TYPE,
					ContainerImageMetadataProperties.DOCKER_IMAGE_MANIFEST_MEDIA_TYPE));

	private final RestTemplate containerRestTemplate;
	private final RestTemplate noSslVerificationContainerRestTemplate;
	private final ContainerImageParser containerImageParser;
	private final Map<RegistryConfiguration.AuthorizationType, RegistryAuthorizer> registryAuthorizerMap;
	private final Map<String, RegistryConfiguration> registryConfigurationMap;

	public static class RegistryRequest {

		private final ContainerImage containerImage;
		private final RegistryConfiguration registryConf;
		private final HttpHeaders authHttpHeaders;
		private final RestTemplate restTemplate;

		public RegistryRequest(ContainerImage containerImage,
				RegistryConfiguration registryConf, HttpHeaders authHttpHeaders, RestTemplate requestRestTemplate) {
			this.containerImage = containerImage;
			this.registryConf = registryConf;
			this.authHttpHeaders = authHttpHeaders;
			this.restTemplate = requestRestTemplate;
		}

		public ContainerImage getContainerImage() {
			return containerImage;
		}

		public RegistryConfiguration getRegistryConf() {
			return registryConf;
		}

		public HttpHeaders getAuthHttpHeaders() {
			return authHttpHeaders;
		}

		public RestTemplate getRestTemplate() {
			return restTemplate;
		}
	}

	public DefaultContainerImageMetadataResolver(RestTemplate restTemplate, RestTemplate noSslVerificationContainerRestTemplate,
			ContainerImageParser containerImageParser, Map<String, RegistryConfiguration> registryConfigurationMap,
			List<RegistryAuthorizer> registryAuthorizes) {

		this.containerRestTemplate = restTemplate;
		this.noSslVerificationContainerRestTemplate = noSslVerificationContainerRestTemplate;
		this.containerImageParser = containerImageParser;
		this.registryConfigurationMap = registryConfigurationMap;
		this.registryAuthorizerMap = new HashMap<>();
		for (RegistryAuthorizer authorizer : registryAuthorizes) {
			this.registryAuthorizerMap.put(authorizer.getType(), authorizer);
		}
	}

	@Override
	public Map<String, String> getImageLabels(String imageName) {

		if (!StringUtils.hasText(imageName)) {
			throw new AppMetadataResolutionException("Null or empty image name");
		}

		RegistryRequest registryRequest = this.getRegistryRequest(imageName);

		Map manifest = this.getImageManifest(registryRequest, Map.class);

		if (!isNotNullMap(manifest.get("config"))) {
			throw new AppMetadataResolutionException(
					String.format("Image [%s] has incorrect or missing manifest config element: %s",
							imageName, manifest.toString()));
		}
		String configDigest = ((Map<String, String>) manifest.get("config")).get("digest");

		if (!StringUtils.hasText(configDigest)) {
			throw new AppMetadataResolutionException(
					String.format("Missing or invalid Configuration Digest: [%s] for image [%s]", configDigest, imageName));
		}

		String configBlob = this.getImageBlob(registryRequest, configDigest, String.class);

		// Parse the config blob string into JSON instance.
		try {
			Map<String, Object> configBlobMap = new ObjectMapper().readValue(configBlob, Map.class);

			if (!isNotNullMap(configBlobMap.get("config"))) {
				throw new AppMetadataResolutionException(
						String.format("Configuration json for image [%s] with digest [%s] has incorrect Config Blog element",
								imageName, configDigest));
			}

			Map<String, Object> configElement = (Map<String, Object>) configBlobMap.get("config");

			return isNotNullMap(configElement.get("Labels")) ?
					(Map<String, String>) configElement.get("Labels") : Collections.emptyMap();
		}
		catch (JsonProcessingException e) {
			throw new AppMetadataResolutionException("Unable to extract the labels from the Config blob", e);
		}

	}

	private boolean isNotNullMap(Object object) {
		return object != null && (object instanceof Map);
	}

	private RegistryRequest getRegistryRequest(String imageName) {

		// Convert the image name into a well-formed ContainerImage
		ContainerImage containerImage = this.containerImageParser.parse(imageName);

		// Find a registry configuration that matches the image's registry host
		RegistryConfiguration registryConf = this.registryConfigurationMap.get(containerImage.getRegistryHost());
		if (registryConf == null) {
			throw new AppMetadataResolutionException(
					"Could not find an Registry Configuration for: " + containerImage.getRegistryHost());
		}

		// Retrieve a registry authorizer that supports the configured authorization type.
		RegistryAuthorizer registryAuthorizer = this.registryAuthorizerMap.get(registryConf.getAuthorizationType());
		if (registryAuthorizer == null) {
			throw new AppMetadataResolutionException(
					"Could not find an RegistryAuthorizer of type:" + registryConf.getAuthorizationType());
		}

		// Use the authorizer to obtain authorization headers.
		HttpHeaders authHttpHeaders = registryAuthorizer.getAuthorizationHeaders(containerImage, registryConf);
		if (authHttpHeaders == null) {
			throw new AppMetadataResolutionException(
					"Could not obtain authorized headers for: " + containerImage + ", config:" + registryConf);
		}

		RestTemplate requestRestTemplate = registryConf.isDisableSslVerification() ?
				this.noSslVerificationContainerRestTemplate : this.containerRestTemplate;

		return new RegistryRequest(containerImage, registryConf, authHttpHeaders, requestRestTemplate);
	}

	private <T> T getImageManifest(RegistryRequest registryRequest, Class<T> responseClassType) {

		String imageManifestMediaType = registryRequest.getRegistryConf().getManifestMediaType();
		if (!SUPPORTED_MANIFEST_MEDIA_TYPES.contains(imageManifestMediaType)) {
			throw new AppMetadataResolutionException("Not supported image manifest media type:" + imageManifestMediaType);
		}
		HttpHeaders httpHeaders = new HttpHeaders(registryRequest.getAuthHttpHeaders());
		httpHeaders.set(HttpHeaders.ACCEPT, imageManifestMediaType);

		// Docker Registry HTTP V2 API pull manifest
		ContainerImage containerImage = registryRequest.getContainerImage();
		UriComponents manifestUriComponents = UriComponentsBuilder.newInstance()
				.scheme("https")
				.host(containerImage.getHostname())
				.port(StringUtils.hasText(containerImage.getPort()) ? containerImage.getPort() : null)
				.path("v2/{repository}/manifests/{reference}")
				.build().expand(containerImage.getRepository(), containerImage.getRepositoryReference());

		ResponseEntity<T> manifest = registryRequest.getRestTemplate().exchange(manifestUriComponents.toUri(),
				HttpMethod.GET, new HttpEntity<>(httpHeaders), responseClassType);
		return manifest.getBody();
	}

	private <T> T getImageBlob(RegistryRequest registryRequest, String configDigest, Class<T> responseClassType) {
		ContainerImage containerImage = registryRequest.getContainerImage();
		HttpHeaders httpHeaders = new HttpHeaders(registryRequest.getAuthHttpHeaders());

		// Docker Registry HTTP V2 API pull config blob
		UriComponents blobUriComponents = UriComponentsBuilder.newInstance()
				.scheme("https")
				.host(containerImage.getHostname())
				.port(StringUtils.hasText(containerImage.getPort()) ? containerImage.getPort() : null)
				.path("v2/{repository}/blobs/{digest}")
				.build().expand(containerImage.getRepository(), configDigest);

		ResponseEntity<T> blob = registryRequest.getRestTemplate().exchange(blobUriComponents.toUri(),
				HttpMethod.GET, new HttpEntity<>(httpHeaders), responseClassType);

		return blob.getBody();
	}
}
