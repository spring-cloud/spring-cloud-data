/*
 * Copyright 2016-2023 the original author or authors.
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

package org.springframework.cloud.dataflow.configuration.metadata;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.cloud.dataflow.configuration.metadata.container.ContainerImageMetadataResolver;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApplicationConfigurationMetadataResolver}.
 *
 * @author Eric Bottard
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @author Corneil du Plessis
 */
class BootApplicationConfigurationMetadataResolverTests {

	@Mock
	private ContainerImageMetadataResolver containerImageMetadataResolver;

	private ApplicationConfigurationMetadataResolver resolver;

	private AutoCloseable autoCloseable;

	@BeforeEach
	void init() {
		autoCloseable = MockitoAnnotations.openMocks(this);
		resolver = new BootApplicationConfigurationMetadataResolver(containerImageMetadataResolver);
	}

	@AfterEach
	void tearDown() throws Exception {
		autoCloseable.close();
	}

	@Test
	void appDockerResourceEmptyLabels() {
		when(containerImageMetadataResolver.getImageLabels("test/test:latest")).thenReturn(new HashMap<>());
		List<ConfigurationMetadataProperty> properties = resolver
				.listProperties(new DockerResource("test/test:latest"));
		assertThat(properties).isEmpty();
	}

	@Test
	void appDockerResource() throws IOException {
		byte[] bytes = StreamUtils.copyToByteArray(new ClassPathResource(
				"apps/no-visible-properties/META-INF/spring-configuration-metadata.json", getClass())
						.getInputStream());
		when(containerImageMetadataResolver.getImageLabels("test/test:latest"))
				.thenReturn(Collections.singletonMap(
						"org.springframework.cloud.dataflow.spring-configuration-metadata.json",
						new String(bytes)));
		List<ConfigurationMetadataProperty> properties = resolver
				.listProperties(new DockerResource("test/test:latest"));
		assertThat(properties).hasSize(3);
	}

	@Test
	void appDockerResourceBrokenFormat() {
		byte[] bytes = "Invalid metadata json content1".getBytes();
		Map<String, String> result = Collections.singletonMap(
				"org.springframework.cloud.dataflow.spring-configuration-metadata.json",
				new String(bytes));
		when(containerImageMetadataResolver.getImageLabels("test/test:latest")).thenReturn(result);
		List<ConfigurationMetadataProperty> properties = resolver
				.listProperties(new DockerResource("test/test:latest"));
		assertThat(properties).isEmpty();
	}

	@Test
	void appSpecificVisiblePropsShouldBeVisible() {
		List<ConfigurationMetadataProperty> properties = resolver
				.listProperties(new ClassPathResource("apps/filter-processor", getClass()));
		assertThat(properties)
				.haveAtLeast(1, configPropertyIdentifiedAs("filter.expression"))
				.haveAtLeast(1, configPropertyIdentifiedAs("some.other.property.included.prefix.expresso2"));
	}

	@Test
	void otherPropertiesShouldOnlyBeVisibleInExtensiveCall() {
		List<ConfigurationMetadataProperty> properties = resolver
				.listProperties(new ClassPathResource("apps/filter-processor", getClass()));
		assertThat(properties).doNotHave(configPropertyIdentifiedAs("some.prefix.hidden.by.default.secret"));
		properties = resolver.listProperties(new ClassPathResource("apps/filter-processor", getClass()), true);
		assertThat(properties).haveAtLeast(1, configPropertyIdentifiedAs("some.prefix.hidden.by.default.secret"));
	}

	@Test
	void shouldReturnEverythingWhenNoDescriptors() {
		List<ConfigurationMetadataProperty> properties = resolver
				.listProperties(new ClassPathResource("apps/no-visible-properties", getClass()));
		List<ConfigurationMetadataProperty> full = resolver
				.listProperties(new ClassPathResource("apps/no-visible-properties", getClass()), true);
		assertThat(properties).isEmpty();
		assertThat(full).hasSize(3);
	}

	@Test
	void deprecatedErrorPropertiesShouldNotBeVisible() {
		List<ConfigurationMetadataProperty> properties = resolver
				.listProperties(new ClassPathResource("apps/deprecated-error", getClass()));
		List<ConfigurationMetadataProperty> full = resolver
				.listProperties(new ClassPathResource("apps/deprecated-error", getClass()), true);
		assertThat(properties).isEmpty();
		assertThat(full).hasSize(2);
	}

	@Test
	void shouldReturnPortMappingProperties() {
		Map<String, Set<String>> portNames = resolver.listPortNames(new ClassPathResource("apps/filter-processor", getClass()));
		assertThat(portNames).hasSize(2);
		assertThat(portNames.get("inbound")).hasSize(3);
		assertThat(portNames.get("inbound")).contains("in1", "in2", "in3");
		assertThat(portNames.get("outbound")).hasSize(2);
		assertThat(portNames.get("outbound")).contains("out1", "out2");
	}

	@Test
	void shouldReturnOptionGroupsProperties() {
		Map<String, Set<String>> optionGroups = resolver.listOptionGroups(new ClassPathResource("apps/filter-processor", getClass()));
		assertThat(optionGroups).hasSize(4);
		assertThat(optionGroups.get("g1")).hasSize(3);
		assertThat(optionGroups.get("g1")).contains("foo1.bar1", "foo1.bar2", "foo1.bar3");
		assertThat(optionGroups.get("g2")).isEmpty();
		assertThat(optionGroups.get("g1.sb1")).hasSize(1);
		assertThat(optionGroups.get("g1.sb1")).contains("foo2.bar1");
		assertThat(optionGroups.get("g1.sb2")).hasSize(2);
		assertThat(optionGroups.get("g1.sb2")).contains("foo3.bar1", "foo3.bar2");
	}

	@Test
	void appDockerResourceWithInboundOutboundPortMapping() {
		Map<String, String> result = new HashMap<>();
		result.put("configuration-properties.inbound-ports", "input1,input2, input3");
		result.put("configuration-properties.outbound-ports", "output1, output2");
		when(this.containerImageMetadataResolver.getImageLabels("test/test:latest")).thenReturn(result);
		Map<String, Set<String>> portNames = this.resolver.listPortNames(new DockerResource("test/test:latest"));
		assertThat(portNames).hasSize(2);
		assertThat(portNames.get("inbound")).hasSize(3);
		assertThat(portNames.get("inbound")).contains("input1", "input2", "input3");
		assertThat(portNames.get("outbound")).hasSize(2);
		assertThat(portNames.get("outbound")).contains("output1", "output2");
	}

	private Condition<ConfigurationMetadataProperty> configPropertyIdentifiedAs(String name) {
		return new Condition<>(c -> name.equals(c.getId()), "id:" + name);
	}

}
