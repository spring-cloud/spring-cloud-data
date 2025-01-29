/*
 * Copyright 2016-2021 the original author or authors.
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
package org.springframework.cloud.dataflow.rest.client.dsl;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import org.springframework.cloud.dataflow.core.StreamRuntimePropertyKeys;
import org.springframework.cloud.dataflow.rest.SkipperStream;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.RuntimeOperations;
import org.springframework.cloud.dataflow.rest.client.StreamOperations;
import org.springframework.cloud.dataflow.rest.resource.AppInstanceStatusResource;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;
import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.StreamDeploymentResource;
import org.springframework.cloud.dataflow.rest.resource.StreamStatusResource;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.PagedModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Vinicius Carvalho
 * @author Christian Tzolov
 * @author Corneil du Plessis
 */
@SuppressWarnings("unchecked")
class StreamDslTests {

	@Mock
	private DataFlowOperations client;

	@Mock
	private StreamOperations streamOperations;

	@Mock
	private RuntimeOperations runtimeOperations;

	private final StreamApplication timeApplication = new StreamApplication("time");

	private final StreamApplication filterApplication = new StreamApplication("filter");

	private final StreamApplication logApplication = new StreamApplication("log");

	private AutoCloseable autoCloseable;

	@BeforeEach
	void init() {
		autoCloseable = MockitoAnnotations.openMocks(this);
		when(client.streamOperations()).thenReturn(this.streamOperations);
		when(client.runtimeOperations()).thenReturn(this.runtimeOperations);
	}

	@AfterEach
	void tearDown() throws Exception {
		autoCloseable.close();
	}

	@Test
	void simpleDefinition() {
		StreamApplication time = new StreamApplication("time");
		StreamApplication log = new StreamApplication("log");
		Stream stream = Stream.builder(client).name("foo").source(time).sink(log).create()
				.deploy();
		assertThat("time | log").isEqualTo(stream.getDefinition());
	}

	@Test
	void definitionWithLabel() {
		StreamApplication time = new StreamApplication("time").label("tick");
		StreamApplication log = new StreamApplication("log");

		Stream stream = Stream.builder(client).name("foo").source(time).sink(log).create()
				.deploy();
		assertThat("tick: time | log").isEqualTo(stream.getDefinition());
	}

	@Test
	void definitionWithProcessor() {
		StreamApplication time = new StreamApplication("time").label("tick");
		StreamApplication filter = new StreamApplication("filter");
		StreamApplication log = new StreamApplication("log");
		Stream stream = Stream.builder(client).name("foo").source(time).processor(filter)
				.sink(log).create().deploy();
		assertThat("tick: time | filter | log").isEqualTo(stream.getDefinition());
	}

	@Test
	void definitionWithProperties() {
		StreamApplication time = new StreamApplication("time").label("tick")
				.addProperty("fixed-delay", 5000);
		StreamApplication log = new StreamApplication("log");
		Stream stream = Stream.builder(client).name("foo").source(time).sink(log).create()
				.deploy();
		assertThat("tick: time --fixed-delay=5000 | log")
				.isEqualTo(stream.getDefinition());
	}

	@Test
	void definitionWithDeploymentProperties() {
		StreamApplication time = new StreamApplication("time").label("tick")
				.addProperty("fixed-delay", "5000").addDeploymentProperty("count", 2);

		Map<String, Object> deploymentProperties = time.getDeploymentProperties();
		assertThat(deploymentProperties).containsEntry("deployer.tick.count", 2);
	}

	@Test
	void definitionWithDeploymentPropertiesBuilder() {
		StreamDefinitionResource resource = new StreamDefinitionResource("ticktock",
				"tick: time | log", "time | log", "demo stream");
		resource.setStatus("deploying");
		when(streamOperations.createStream(anyString(),
				anyString(), anyString(), anyBoolean())).thenReturn(resource);
		SkipperDeploymentPropertiesBuilder propertiesBuilder = new SkipperDeploymentPropertiesBuilder();
		Map<String, String> props = propertiesBuilder.count("tick", 2)
				.memory("tick", 2048)
				.packageVersion("1.0.0.RELEASE")
				.repoName("foo")
				.platformName("pcf").build();
		Stream.builder(client).name("ticktock").definition("tick: time | log").create()
				.deploy(props);
		ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
		verify(streamOperations, times(1)).deploy(eq("ticktock"),
				mapArgumentCaptor.capture());
		assertThat(mapArgumentCaptor.getValue()).containsKeys("deployer.tick.count",
				"deployer.tick.memory", SkipperStream.SKIPPER_PLATFORM_NAME,
				SkipperStream.SKIPPER_PACKAGE_VERSION, SkipperStream.SKIPPER_REPO_NAME);
	}

	@Test
	void deployWithCreate() {
		StreamDefinitionResource resource = new StreamDefinitionResource("ticktock",
				"time | log", "time | log", "demo stream");
		resource.setStatus("deploying");
		when(streamOperations.createStream(anyString(),
				anyString(), anyString(), anyBoolean())).thenReturn(resource);
		StreamApplication time = new StreamApplication("time");
		StreamApplication log = new StreamApplication("log");
		Stream.builder(client).name("ticktock").description("demo stream").source(time).sink(log).create().deploy();
		verify(streamOperations, times(1)).createStream(
				eq("ticktock"), eq("time | log"), eq("demo stream"), eq(false));
		verify(streamOperations, times(1)).deploy(eq("ticktock"),
				anyMap());
	}

	@Test
	void deployWithDefinition() {
		StreamDefinitionResource resource = new StreamDefinitionResource("ticktock",
				"time | log", "time | log", "demo stream");
		resource.setStatus("deploying");
		when(streamOperations.createStream(anyString(),
				anyString(), anyString(), anyBoolean())).thenReturn(resource);

		Stream.builder(client).name("ticktock").description("demo stream").definition("time | log")
				.create().deploy(Collections.singletonMap("deployer.log.count", "2"));
		verify(streamOperations, times(1)).createStream(
				eq("ticktock"), eq("time | log"), eq("demo stream"), eq(false));
		verify(streamOperations, times(1)).deploy(eq("ticktock"),
				eq(Collections.singletonMap("deployer.log.count", "2")));

	}

	@Test
	void getStatus() {
		StreamDefinitionResource resource = new StreamDefinitionResource("ticktock",
				"time | log", "time | log", "demo stream");
		resource.setStatus("unknown");
		when(streamOperations.getStreamDefinition(eq("ticktock")))
				.thenReturn(resource);
		when(streamOperations.createStream(anyString(),
				anyString(), anyString(), anyBoolean())).thenReturn(resource);
		doAnswer((Answer<Void>) invocationOnMock -> {
			resource.setStatus("deploying");
			return null;
		}).when(streamOperations).deploy(eq("ticktock"), anyMap());

		StreamDefinition streamDefinition = Stream.builder(client).name("ticktock").description("demo stream")
				.definition("time | log").create();
		verify(streamOperations, times(1)).createStream(
				eq("ticktock"), eq("time | log"), eq("demo stream"), eq(false));
		Stream stream = streamDefinition.deploy();
		assertThat("deploying").isEqualTo(stream.getStatus());
	}

	@Test
	void createStream() {
		StreamDefinitionResource resource = new StreamDefinitionResource("ticktock",
				"time | log", "time | log", "demo stream");
		resource.setStatus("deploying");
		when(streamOperations.createStream(anyString(),
				anyString(), anyString(), anyBoolean())).thenReturn(resource);
		StreamApplication time = new StreamApplication("time");
		StreamApplication log = new StreamApplication("log");
		Stream.builder(client).name("ticktock").description("demo stream").source(time).sink(log).create();
		verify(streamOperations, times(1)).createStream(
				eq("ticktock"), eq("time | log"), eq("demo stream"), eq(false));
	}

	@Test
	void duplicateNameWithLabel() {
		StreamApplication filter2 = new StreamApplication("filter").label("filter2");
		Stream.builder(client).name("test").source(timeApplication)
				.processor(filterApplication).processor(filter2).sink(logApplication)
				.create();
		verify(streamOperations, times(1)).createStream(
				eq("test"), eq("time | filter | filter2: filter | log"), eq(""),
				eq(false));
	}

	@Test
	void duplicateNameNoLabel() {
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
			Stream.builder(client).name("test").source(timeApplication)
				.processor(filterApplication).processor(filterApplication)
				.sink(logApplication).create();
		});
	}

	@Test
	void update() {
		StreamDefinitionResource ticktockDefinition = new StreamDefinitionResource("ticktock", "time | log",
				"time | log", "demo stream");
		ticktockDefinition.setStatus("deploying");
		when(streamOperations.createStream(anyString(), anyString(), anyString(), anyBoolean()))
				.thenReturn(ticktockDefinition);

		Stream stream = Stream.builder(client).name("ticktock").description("demo stream")
				.definition(ticktockDefinition.getDslText()).create()
				.deploy();

		when(streamOperations.info(eq("ticktock"))).thenReturn(new StreamDeploymentResource("ticktock", "time | log"));

		stream.update("app.log.log.expression='TIMESTAMP: '.concat(payload)");

		verify(streamOperations, times(1)).updateStream(eq("ticktock"),
				eq("ticktock"), isA(PackageIdentifier.class), isA(Map.class), eq(false), isNull());

		verify(streamOperations, times(1)).info(eq("ticktock"));
	}

	@Test
	void logs() {
		String streamLog = "Test stream log";
		String appLog = "Test app log";
		StreamDefinitionResource ticktockDefinition = new StreamDefinitionResource("ticktock", "time | log",
				"time | log", "demo stream");
		ticktockDefinition.setStatus("deploying");
		when(streamOperations.createStream(anyString(), anyString(), anyString(), anyBoolean()))
				.thenReturn(ticktockDefinition);

		StreamStatusResource streamStatusResource = new StreamStatusResource();
		streamStatusResource.setName("streamName");


		AppStatusResource appStatusResource = new AppStatusResource("deploymentId", "deployed");
		appStatusResource.setInstances(CollectionModel.of(Collections.singletonList(new AppInstanceStatusResource("instanceId", "deployed",
			Collections.singletonMap(StreamRuntimePropertyKeys.ATTRIBUTE_SKIPPER_APPLICATION_NAME, "log")))));
		streamStatusResource.setApplications(CollectionModel.of(Collections.singletonList(appStatusResource)));

		when(runtimeOperations.streamStatus(ticktockDefinition.getName()))
				.thenReturn(PagedModel.of(Collections.singletonList(streamStatusResource), (PagedModel.PageMetadata) null));

		Stream stream = Stream.builder(client).name(ticktockDefinition.getName()).description("demo stream")
				.definition(ticktockDefinition.getDslText()).create()
				.deploy();

		when(streamOperations.streamExecutionLog(ticktockDefinition.getName())).thenReturn(streamLog);
		when(streamOperations.streamExecutionLog(ticktockDefinition.getName(), "deploymentId")).thenReturn(appLog);

		assertThat(stream.logs()).isEqualTo(streamLog);
		assertThat(stream.logs(new StreamApplication(logApplication.getName()))).isEqualTo(appLog);

		verify(streamOperations, times(1)).streamExecutionLog(stream.getName());
		verify(streamOperations, times(1)).streamExecutionLog(stream.getName(), "deploymentId");
		verify(runtimeOperations, times(1)).streamStatus(ticktockDefinition.getName());
	}

	@Test
	void rollback() {
		StreamDefinitionResource ticktockDefinition = new StreamDefinitionResource("ticktock", "time | log",
				"time | log", "demo stream");
		ticktockDefinition.setStatus("deploying");
		when(streamOperations.createStream(anyString(), anyString(), anyString(), anyBoolean()))
				.thenReturn(ticktockDefinition);

		Stream stream = Stream.builder(client).name("ticktock").description("demo stream")
				.definition(ticktockDefinition.getDslText()).create()
				.deploy();

		when(streamOperations.info(eq("ticktock"))).thenReturn(new StreamDeploymentResource("ticktock", "time | log"));

		stream.rollback(666);

		verify(streamOperations, times(1)).rollbackStream(eq("ticktock"), eq(666));
		verify(streamOperations, times(1)).info(eq("ticktock"));
	}

	@Test
	void manifest() {
		StreamDefinitionResource ticktockDefinition = new StreamDefinitionResource("ticktock", "time | log",
				"time | log", "demo stream");
		ticktockDefinition.setStatus("deploying");
		when(streamOperations.createStream(anyString(), anyString(), anyString(), anyBoolean()))
				.thenReturn(ticktockDefinition);

		Stream stream = Stream.builder(client).name("ticktock").description("demo stream")
				.definition(ticktockDefinition.getDslText()).create()
				.deploy();

		stream.manifest(666);

		verify(streamOperations, times(1)).getManifest(eq("ticktock"), eq(666));
	}

	@Test
	void history() {
		StreamDefinitionResource ticktockDefinition = new StreamDefinitionResource("ticktock", "time | log",
				"time | log", "demo stream");
		ticktockDefinition.setStatus("deploying");
		when(streamOperations.createStream(anyString(), anyString(), anyString(), anyBoolean()))
				.thenReturn(ticktockDefinition);

		Stream stream = Stream.builder(client).name("ticktock").description("demo stream")
				.definition(ticktockDefinition.getDslText()).create()
				.deploy();

		stream.history();

		verify(streamOperations, times(1)).history(eq("ticktock"));
	}

	@Test
	void undeploy() {
		StreamDefinitionResource resource = new StreamDefinitionResource("ticktock", "time | log",
				"time | log", "demo stream");
		resource.setStatus("deploying");
		when(streamOperations.createStream(anyString(),
				anyString(), anyString(), anyBoolean())).thenReturn(resource);
		StreamApplication time = new StreamApplication("time");
		StreamApplication log = new StreamApplication("log");

		Stream stream = Stream.builder(client).name("ticktock").description("demo stream")
				.source(time).sink(log)
				.create().deploy();
		verify(streamOperations, times(1)).createStream(
				eq("ticktock"), eq("time | log"), eq("demo stream"), eq(false));
		verify(streamOperations, times(1)).deploy(eq("ticktock"),
				anyMap());
		stream.undeploy();
		verify(streamOperations, times(1))
				.undeploy(eq("ticktock"));
	}

	@Test
	void destroy() {
		StreamDefinitionResource resource = new StreamDefinitionResource("ticktock",
				"time | log", "time | log", "demo stream");
		resource.setStatus("deploying");
		when(streamOperations.createStream(anyString(),
				anyString(), anyString(), anyBoolean())).thenReturn(resource);
		StreamApplication time = new StreamApplication("time");
		StreamApplication log = new StreamApplication("log");
		Stream stream = Stream.builder(client).name("ticktock").description("demo stream")
				.source(time).sink(log)
				.create().deploy();
		verify(streamOperations, times(1)).createStream(
				eq("ticktock"), eq("time | log"), eq("demo stream"), eq(false));
		verify(streamOperations, times(1)).deploy(eq("ticktock"),
				anyMap());
		stream.destroy();
		verify(streamOperations, times(1))
				.destroy(eq("ticktock"));
	}

	@Test
	void scaleApplicationInstances() {
		StreamDefinitionResource resource = new StreamDefinitionResource("ticktock",
				"time | log", "time | log", "demo stream");
		resource.setStatus("deploying");
		when(streamOperations.createStream(anyString(),
				anyString(), anyString(), anyBoolean())).thenReturn(resource);
		StreamApplication time = new StreamApplication("time");
		StreamApplication log = new StreamApplication("log");
		Stream stream = Stream.builder(client).name("ticktock").description("demo stream")
				.source(time).sink(log)
				.create().deploy();
		verify(streamOperations, times(1)).createStream(
				eq("ticktock"), eq("time | log"), eq("demo stream"), eq(false));
		verify(streamOperations, times(1)).deploy(eq("ticktock"),
				anyMap());
		stream.scaleApplicationInstances(time, 3, Collections.emptyMap());

		stream.scaleApplicationInstances(log, 2, Collections.singletonMap("key", "value"));

		verify(streamOperations, times(1))
				.scaleApplicationInstances(eq("ticktock"), eq("time"), eq(3), isA(Map.class));

		verify(streamOperations, times(1))
				.scaleApplicationInstances(eq("ticktock"), eq("log"), eq(2), eq(Collections.singletonMap("key", "value")));
	}
}
