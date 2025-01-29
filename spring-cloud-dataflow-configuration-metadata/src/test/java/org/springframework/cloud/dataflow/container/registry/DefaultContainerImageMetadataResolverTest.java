/*
 * Copyright 2020-2021 the original author or authors.
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

package org.springframework.cloud.dataflow.container.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.cloud.dataflow.configuration.metadata.container.DefaultContainerImageMetadataResolver;
import org.springframework.cloud.dataflow.container.registry.authorization.RegistryAuthorizer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @author Corneil du Plessis
 */
class DefaultContainerImageMetadataResolverTest {

	@Mock
	private RestTemplate mockRestTemplate;

	@Mock
	private ContainerImageRestTemplateFactory containerImageRestTemplateFactory;

	private final Map<String, ContainerRegistryConfiguration> registryConfigurationMap = new HashMap<>();

	private ContainerRegistryService containerRegistryService;

	private AutoCloseable autoCloseable;


	@BeforeEach
	void init() {
		autoCloseable = MockitoAnnotations.openMocks(this);

		when(containerImageRestTemplateFactory.getContainerRestTemplate(anyBoolean(), anyBoolean(), anyMap())).thenReturn(mockRestTemplate);

		// DockerHub registry configuration by default.
		ContainerRegistryConfiguration dockerHubAuthConfig = new ContainerRegistryConfiguration();
		dockerHubAuthConfig.setRegistryHost(ContainerRegistryProperties.DOCKER_HUB_HOST);
		dockerHubAuthConfig.setAuthorizationType(ContainerRegistryConfiguration.AuthorizationType.dockeroauth2);

		ContainerRegistryConfiguration privateRegistryConfig = new ContainerRegistryConfiguration();
		privateRegistryConfig.setRegistryHost("my-private-repository.com:5000");
		privateRegistryConfig.setAuthorizationType(ContainerRegistryConfiguration.AuthorizationType.dockeroauth2);

		registryConfigurationMap.put(dockerHubAuthConfig.getRegistryHost(), dockerHubAuthConfig);
		registryConfigurationMap.put(privateRegistryConfig.getRegistryHost(), privateRegistryConfig);

		RegistryAuthorizer registryAuthorizer = mock(RegistryAuthorizer.class);

		when(registryAuthorizer.getType()).thenReturn(ContainerRegistryConfiguration.AuthorizationType.dockeroauth2);
		when(registryAuthorizer.getAuthorizationHeaders(any(ContainerImage.class), any())).thenReturn(new HttpHeaders());

		this.containerRegistryService = new ContainerRegistryService(containerImageRestTemplateFactory,
				new ContainerImageParser(), registryConfigurationMap, Collections.singletonList(registryAuthorizer));
	}

	@AfterEach
	void tearDown() throws Exception {
		autoCloseable.close();
	}

	@Test
	void getImageLabelsInvalidImageName() {
		assertThatExceptionOfType(ContainerRegistryException.class).isThrownBy(() -> {
			DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(this.containerRegistryService);
			resolver.getImageLabels(null);
		});
	}

	@Test
	void getImageLabels() throws JsonProcessingException {

		DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(this.containerRegistryService);

		Map<String, Object> manifestResponse = Collections.singletonMap("config", Collections.singletonMap("digest", "123"));
		mockManifestRestTemplateCall(manifestResponse, "registry-1.docker.io", null, "test/image", "latest");

		mockBlogRestTemplateCall("{\"config\": { \"Labels\": { \"boza\": \"koza\"} } }",
				"registry-1.docker.io", null, "test/image", "123");

		Map<String, String> labels = resolver.getImageLabels("test/image:latest");
		assertThat(labels)
				.hasSize(1)
				.containsEntry("boza", "koza");
	}

	@Test
	void getImageLabelsFromPrivateRepository() throws JsonProcessingException {

		DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(this.containerRegistryService);

		Map<String, Object> manifestResponse = Collections.singletonMap("config", Collections.singletonMap("digest", "123"));
		mockManifestRestTemplateCall(manifestResponse, "my-private-repository.com", "5000", "test/image", "latest");

		mockBlogRestTemplateCall("{\"config\": { \"Labels\": { \"boza\": \"koza\"} } }",
				"my-private-repository.com", "5000", "test/image", "123");

		Map<String, String> labels = resolver.getImageLabels("my-private-repository.com:5000/test/image:latest");
		assertThat(labels)
				.hasSize(1)
				.containsEntry("boza", "koza");
	}

	@Test
	void getImageLabelsMissingRegistryConfiguration() {
		assertThatExceptionOfType(ContainerRegistryException.class).isThrownBy(() -> {
			DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(this.containerRegistryService);
			resolver.getImageLabels("somehost:8083/test/image:latest");
		});
	}

	@Test
	void getImageLabelsMissingRegistryAuthorizer() {
		assertThatExceptionOfType(ContainerRegistryException.class).isThrownBy(() -> {
			DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(
				new ContainerRegistryService(containerImageRestTemplateFactory,
					new ContainerImageParser(), registryConfigurationMap, Collections.emptyList()));

			resolver.getImageLabels("test/image:latest");
		});
	}

	@Test
	void getImageLabelsMissingAuthorizationHeader() {
		assertThatExceptionOfType(ContainerRegistryException.class).isThrownBy(() -> {
			RegistryAuthorizer registryAuthorizer = mock(RegistryAuthorizer.class);

			when(registryAuthorizer.getType()).thenReturn(ContainerRegistryConfiguration.AuthorizationType.dockeroauth2);
			when(registryAuthorizer.getAuthorizationHeaders(any(ContainerImage.class), any())).thenReturn(null);

			DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(
				new ContainerRegistryService(containerImageRestTemplateFactory, new ContainerImageParser(), registryConfigurationMap, Arrays.asList(registryAuthorizer)));

			resolver.getImageLabels("test/image:latest");
		});
	}

	@Test
	void getImageLabelsInvalidManifestResponse() {
		assertThatExceptionOfType(ContainerRegistryException.class).isThrownBy(() -> {
			DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(this.containerRegistryService);

			Map<String, Object> manifestResponseWithoutConfig = Collections.emptyMap();
			mockManifestRestTemplateCall(manifestResponseWithoutConfig, "registry-1.docker.io",
				null, "test/image", "latest");

			resolver.getImageLabels("test/image:latest");
		});
	}

	@Test
	void getImageLabelsInvalidDigest() {
		assertThatExceptionOfType(ContainerRegistryException.class).isThrownBy(() -> {
			DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(this.containerRegistryService);

			String emptyDigest = "";
			Map<String, Object> manifestResponse = Collections.singletonMap("config", Collections.singletonMap("digest", emptyDigest));
			mockManifestRestTemplateCall(manifestResponse, "registry-1.docker.io", null,
				"test/image", "latest");

			resolver.getImageLabels("test/image:latest");
		});
	}

	@Test
	void getImageLabelsWithInvalidLabels() throws JsonProcessingException {

		DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(this.containerRegistryService);

		Map<String, Object> manifestResponse = Collections.singletonMap("config", Collections.singletonMap("digest", "123"));
		mockManifestRestTemplateCall(manifestResponse, "registry-1.docker.io", null,
				"test/image", "latest");

		mockBlogRestTemplateCall("{\"config\": { } }",
				"registry-1.docker.io", null, "test/image", "123");

		Map<String, String> labels = resolver.getImageLabels("test/image:latest");
		assertThat(labels).isEmpty();
	}

	@Test
	void getImageLabelsWithMixedOCIResponses() throws JsonProcessingException {
		DefaultContainerImageMetadataResolver resolver = new MockedDefaultContainerImageMetadataResolver(
				this.containerRegistryService);
		String ociInCompatible = "{\"schemaVersion\": 1,\"name\": \"test/image\"}";
		String ociCompatible = "{\"schemaVersion\": 2,\"mediaType\": \"application/vnd.oci.image.manifest.v1+json\",\"config\":{\"mediaType\": \"application/vnd.oci.image.config.v1+json\",\"digest\": \"sha256:efc06d6096cc88697e477abb0b3479557e1bec688c36813383f1a8581f87d9f8\",\"size\": 34268}}";
		mockManifestRestTemplateCallAccepts(ociInCompatible, "my-private-repository.com", "5000", "test/image",
				"latest", ContainerRegistryProperties.DOCKER_IMAGE_MANIFEST_MEDIA_TYPE);
		mockManifestRestTemplateCallAccepts(ociCompatible, "my-private-repository.com", "5000", "test/image", "latest",
				ContainerRegistryProperties.OCI_IMAGE_MANIFEST_MEDIA_TYPE);
		String blobResponse = "{\"config\": {\"Labels\": {\"boza\": \"koza\"}}}";
		mockBlogRestTemplateCall(blobResponse, "my-private-repository.com", "5000", "test/image",
				"sha256:efc06d6096cc88697e477abb0b3479557e1bec688c36813383f1a8581f87d9f8");

		Map<String, String> labels = resolver.getImageLabels("my-private-repository.com:5000/test/image:latest");
		assertThat(labels)
				.isNotEmpty()
				.containsEntry("boza", "koza");
	}

	private void mockManifestRestTemplateCall(Map<String, Object> mapToReturn, String registryHost,
			String registryPort, String repository, String tagOrDigest) {

		UriComponents manifestUriComponents = UriComponentsBuilder.newInstance()
				.scheme("https")
				.host(registryHost)
				.port(StringUtils.hasText(registryPort) ? registryPort : null)
				.path("v2/{repository}/manifests/{reference}")
				.build().expand(repository, tagOrDigest);


		when(mockRestTemplate.exchange(
				eq(manifestUriComponents.toUri()),
				eq(HttpMethod.GET),
				any(HttpEntity.class),
				eq(Map.class))).thenReturn(new ResponseEntity<>(mapToReturn, HttpStatus.OK));
	}

	private void mockBlogRestTemplateCall(String jsonResponse, String registryHost, String registryPort,
			String repository, String digest) throws JsonProcessingException {

		UriComponents blobUriComponents = UriComponentsBuilder.newInstance()
				.scheme("https")
				.host(registryHost)
				.port(StringUtils.hasText(registryPort) ? registryPort : null)
				.path("v2/{repository}/blobs/{digest}")
				.build().expand(repository, digest);

		when(mockRestTemplate.exchange(
				eq(blobUriComponents.toUri()),
				eq(HttpMethod.GET),
				any(HttpEntity.class),
				eq(Map.class)))
				.thenReturn(new ResponseEntity<>(new ObjectMapper().readValue(jsonResponse, Map.class), HttpStatus.OK));
	}

	private void mockManifestRestTemplateCallAccepts(String jsonResponse, String registryHost, String registryPort,
			String repository, String tagOrDigest, String accepts) throws JsonProcessingException {

		UriComponents blobUriComponents = UriComponentsBuilder.newInstance()
			.scheme("https")
			.host(registryHost)
			.port(StringUtils.hasText(registryPort) ? registryPort : null)
			.path("v2/{repository}/manifests/{reference}")
			.build()
			.expand(repository, tagOrDigest);

		MediaType mediaType = new MediaType(org.apache.commons.lang3.StringUtils.substringBefore(accepts, "/"),
				org.apache.commons.lang3.StringUtils.substringAfter(accepts, "/"));
		when(mockRestTemplate.exchange(eq(blobUriComponents.toUri()), eq(HttpMethod.GET),
				argThat(new HeaderAccepts(mediaType)), eq(Map.class)))
			.thenReturn(new ResponseEntity<>(new ObjectMapper().readValue(jsonResponse, Map.class), HttpStatus.OK));
	}

	static class HeaderAccepts implements ArgumentMatcher<HttpEntity<?>> {

		private final MediaType accepts;

		public HeaderAccepts(MediaType accepts) {
			this.accepts = accepts;
		}

		@Override
		public boolean matches(HttpEntity<?> argument) {
			return argument.getHeaders().getAccept().contains(accepts);
		}

	}

	private class MockedDefaultContainerImageMetadataResolver extends DefaultContainerImageMetadataResolver {
		public MockedDefaultContainerImageMetadataResolver(ContainerRegistryService containerRegistryService) {
			super(containerRegistryService);
		}
	}
}
