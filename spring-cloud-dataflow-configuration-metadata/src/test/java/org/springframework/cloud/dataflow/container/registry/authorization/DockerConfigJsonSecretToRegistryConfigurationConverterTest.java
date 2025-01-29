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

package org.springframework.cloud.dataflow.container.registry.authorization;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.cloud.dataflow.container.registry.ContainerImageRestTemplateFactory;
import org.springframework.cloud.dataflow.container.registry.ContainerRegistryConfiguration;
import org.springframework.cloud.dataflow.container.registry.ContainerRegistryProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 * @author Corneil du Plessis
 */
class DockerConfigJsonSecretToRegistryConfigurationConverterTest {

	@Mock
	private RestTemplate mockRestTemplate;

	@Mock
	private ContainerImageRestTemplateFactory containerImageRestTemplateFactory;

	private DockerConfigJsonSecretToRegistryConfigurationConverter converter;

	private AutoCloseable autoCloseable;
	
	@BeforeEach
	void init() {
		autoCloseable = MockitoAnnotations.openMocks(this);
		when(containerImageRestTemplateFactory.getContainerRestTemplate(anyBoolean(), anyBoolean(), anyMap())).thenReturn(mockRestTemplate);
		converter = new DockerConfigJsonSecretToRegistryConfigurationConverter(new ContainerRegistryProperties(), containerImageRestTemplateFactory);
	}

	@AfterEach
	void tearDown() throws Exception {
		autoCloseable.close();
	}

	@Test
	void convertAnonymousRegistry() throws URISyntaxException {

		when(mockRestTemplate.exchange(
				eq(new URI("https://demo.repository.io/v2/")), eq(HttpMethod.GET), any(), eq(Map.class)))
				.thenReturn(new ResponseEntity<>(new HashMap<>(), HttpStatus.OK));

		String b = "{\"auths\":{\"demo.repository.io\":{}}}";
		Map<String, ContainerRegistryConfiguration> result = converter.convert(b);

		assertThat(result)
				.hasSize(1)
				.containsKey("demo.repository.io");

		ContainerRegistryConfiguration registryConfiguration = result.get("demo.repository.io");

		assertThat(registryConfiguration.getRegistryHost()).isEqualTo("demo.repository.io");
		assertThat(registryConfiguration.getUser()).isNull();
		assertThat(registryConfiguration.getSecret()).isNull();
		assertThat(registryConfiguration.getAuthorizationType()).isEqualTo(ContainerRegistryConfiguration.AuthorizationType.anonymous);
	}

	@Test
	void convertBasicAuthRegistry() throws URISyntaxException {

		when(mockRestTemplate.exchange(
				eq(new URI("https://demo.repository.io/v2/_catalog")), eq(HttpMethod.GET), any(), eq(Map.class)))
				.thenReturn(new ResponseEntity<>(new HashMap<>(), HttpStatus.OK));

		String b = "{\"auths\":{\"demo.repository.io\":{\"username\":\"testuser\",\"password\":\"testpassword\",\"auth\":\"YWRtaW46SGFyYm9yMTIzNDU=\"}}}";
		Map<String, ContainerRegistryConfiguration> result = converter.convert(b);

		assertThat(result)
				.hasSize(1)
				.containsKey("demo.repository.io");

		ContainerRegistryConfiguration registryConfiguration = result.get("demo.repository.io");

		assertThat(registryConfiguration.getRegistryHost()).isEqualTo("demo.repository.io");
		assertThat(registryConfiguration.getUser()).isEqualTo("testuser");
		assertThat(registryConfiguration.getSecret()).isEqualTo("testpassword");
		assertThat(registryConfiguration.getAuthorizationType()).isEqualTo(ContainerRegistryConfiguration.AuthorizationType.basicauth);
	}

	@Test
	void convertDockerHubRegistry() throws URISyntaxException {

		HttpHeaders authenticateHeader = new HttpHeaders();
		authenticateHeader.add("Www-Authenticate", "Bearer realm=\"https://demo.repository.io/service/token\",service=\"demo-registry\",scope=\"registry:category:pull\"");
		HttpClientErrorException httpClientErrorException =
				HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", authenticateHeader, new byte[0], null);

		when(mockRestTemplate.exchange(eq(new URI("https://demo.repository.io/v2/")),
				eq(HttpMethod.GET), any(), eq(Map.class))).thenThrow(httpClientErrorException);

		String b = "{\"auths\":{\"demo.repository.io\":{\"username\":\"testuser\",\"password\":\"testpassword\",\"auth\":\"YWRtaW46SGFyYm9yMTIzNDU=\"}}}";
		Map<String, ContainerRegistryConfiguration> result = converter.convert(b);

		assertThat(result)
				.hasSize(1)
				.containsKey("demo.repository.io");

		ContainerRegistryConfiguration registryConfiguration = result.get("demo.repository.io");

		assertThat(registryConfiguration.getRegistryHost()).isEqualTo("demo.repository.io");
		assertThat(registryConfiguration.getUser()).isEqualTo("testuser");
		assertThat(registryConfiguration.getSecret()).isEqualTo("testpassword");
		assertThat(registryConfiguration.getAuthorizationType()).isEqualTo(ContainerRegistryConfiguration.AuthorizationType.dockeroauth2);
		assertThat(registryConfiguration.getExtra()).containsEntry("registryAuthUri", "https://demo.repository.io/service/token?service=demo-registry&scope=repository:{repository}:pull");

	}

}
