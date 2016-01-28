/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.dataflow.admin.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.dataflow.module.DeploymentState.deployed;
import static org.springframework.cloud.dataflow.module.DeploymentState.error;
import static org.springframework.cloud.dataflow.module.DeploymentState.failed;
import static org.springframework.cloud.dataflow.module.DeploymentState.partial;
import static org.springframework.cloud.dataflow.module.DeploymentState.undeployed;
import static org.springframework.cloud.dataflow.module.DeploymentState.unknown;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.dataflow.admin.configuration.TestDependencies;
import org.springframework.cloud.dataflow.admin.repository.InMemoryStreamDefinitionRepository;
import org.springframework.cloud.dataflow.admin.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.artifact.registry.InMemoryArtifactRegistry;
import org.springframework.cloud.dataflow.core.BindingProperties;
import org.springframework.cloud.dataflow.core.ModuleDefinition;
import org.springframework.cloud.dataflow.core.ModuleDeploymentId;
import org.springframework.cloud.dataflow.core.ModuleDeploymentRequest;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.module.DeploymentState;
import org.springframework.cloud.dataflow.module.ModuleStatus;
import org.springframework.cloud.dataflow.module.deployer.ModuleDeployer;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * @author Mark Fisher
 * @author Ilayaperumal Gopinathan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestDependencies.class)
@WebAppConfiguration
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class StreamControllerTests {

	@Autowired
	private StreamDefinitionRepository repository;

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext wac;

	@Autowired
	@Qualifier("processModuleDeployer")
	private ModuleDeployer moduleDeployer;

	@Before
	public void setupMockMVC() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).defaultRequest(
				get("/").accept(MediaType.APPLICATION_JSON)).build();
	}

	@After
	public void tearDown() {
		repository.deleteAll();
		assertEquals(0, repository.count());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorMissingRepository() {
		StreamDeploymentController deploymentController = new StreamDeploymentController(new InMemoryStreamDefinitionRepository(), new InMemoryArtifactRegistry(), moduleDeployer);
		new StreamDefinitionController(null, deploymentController, moduleDeployer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorMissingDeploymentController() {
		new StreamDefinitionController(new InMemoryStreamDefinitionRepository(), null, moduleDeployer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorMissingDeployer() {
		StreamDeploymentController deploymentController = new StreamDeploymentController(new InMemoryStreamDefinitionRepository(), new InMemoryArtifactRegistry(), moduleDeployer);
		new StreamDefinitionController(new InMemoryStreamDefinitionRepository(), deploymentController, null);
	}

	@Test
	public void testSave() throws Exception {
		assertEquals(0, repository.count());
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream").param("definition", "time | log")
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
						.andExpect(status().isCreated());
		assertEquals(1, repository.count());
		StreamDefinition myStream = repository.findOne("myStream");
		assertEquals("time | log", myStream.getDslText());
		assertEquals("myStream", myStream.getName());
		assertEquals(2, myStream.getModuleDefinitions().size());
		ModuleDefinition timeDefinition = myStream.getModuleDefinitions().get(0);
		ModuleDefinition logDefinition = myStream.getModuleDefinitions().get(1);
		assertEquals(1, timeDefinition.getParameters().size());
		assertEquals("myStream.0", timeDefinition.getParameters().get(BindingProperties.OUTPUT_BINDING_KEY));
		assertEquals(3, logDefinition.getParameters().size());
		assertEquals("myStream.0", logDefinition.getParameters().get(BindingProperties.INPUT_BINDING_KEY));
		assertEquals("default", logDefinition.getParameters().get(BindingProperties.INPUT_GROUP_KEY));
		assertEquals("true", logDefinition.getParameters().get(BindingProperties.INPUT_DURABLE_SUBSCRIPTION_KEY));
	}

	@Test
	public void testSaveDuplicate() throws Exception {
		repository.save(new StreamDefinition("myStream", "time | log"));
		assertEquals(1, repository.count());
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream").param("definition", "time | log")
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
						.andExpect(status().is5xxServerError());
		assertEquals(1, repository.count());
	}

	@Test
	public void testSaveWithParameters() throws Exception {
		assertEquals(0, repository.count());
		String definition = "time --fixedDelay=500 --timeUnit=milliseconds | log";
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream")
						.param("definition", definition)
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
				.andExpect(status().isCreated());
		assertEquals(1, repository.count());
		StreamDefinition myStream = repository.findOne("myStream");
		ModuleDefinition timeDefinition = myStream.getModuleDefinitions().get(0);
		ModuleDefinition logDefinition = myStream.getModuleDefinitions().get(1);
		assertEquals("time", timeDefinition.getName());
		assertEquals("log", logDefinition.getName());
		assertEquals("500", timeDefinition.getParameters().get("fixedDelay"));
		assertEquals("milliseconds", timeDefinition.getParameters().get("timeUnit"));
		assertEquals(definition, myStream.getDslText());
		assertEquals("myStream", myStream.getName());
	}

	@Test
	public void testStreamWithProcessor() throws Exception {
		assertEquals(0, repository.count());
		String definition = "time | filter | log";
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream").param("definition", definition)
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
						.andExpect(status().isCreated());
		assertEquals(1, repository.count());
		StreamDefinition myStream = repository.findOne("myStream");
		assertEquals(definition, myStream.getDslText());
		assertEquals("myStream", myStream.getName());
		assertEquals(3, myStream.getModuleDefinitions().size());
		ModuleDefinition timeDefinition = myStream.getModuleDefinitions().get(0);
		ModuleDefinition filterDefinition = myStream.getModuleDefinitions().get(1);
		ModuleDefinition logDefinition = myStream.getModuleDefinitions().get(2);
		assertEquals(1, timeDefinition.getParameters().size());
		assertEquals("myStream.0", timeDefinition.getParameters().get(BindingProperties.OUTPUT_BINDING_KEY));
		assertEquals(4, filterDefinition.getParameters().size());
		assertEquals("myStream.0", filterDefinition.getParameters().get(BindingProperties.INPUT_BINDING_KEY));
		assertEquals("default", filterDefinition.getParameters().get(BindingProperties.INPUT_GROUP_KEY));
		assertEquals("true", filterDefinition.getParameters().get(BindingProperties.INPUT_DURABLE_SUBSCRIPTION_KEY));
		assertEquals("myStream.1", filterDefinition.getParameters().get(BindingProperties.OUTPUT_BINDING_KEY));
		assertEquals(3, logDefinition.getParameters().size());
		assertEquals("myStream.1", logDefinition.getParameters().get(BindingProperties.INPUT_BINDING_KEY));
		assertEquals("default", logDefinition.getParameters().get(BindingProperties.INPUT_GROUP_KEY));
		assertEquals("true", logDefinition.getParameters().get(BindingProperties.INPUT_DURABLE_SUBSCRIPTION_KEY));
	}

	@Test
	public void testSourceChannelWithSingleModule() throws Exception {
		assertEquals(0, repository.count());
		String definition = "queue:foo > log";
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream").param("definition", definition)
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
						.andExpect(status().isCreated());
		assertEquals(1, repository.count());
		StreamDefinition myStream = repository.findOne("myStream");
		assertEquals(definition, myStream.getDslText());
		assertEquals("myStream", myStream.getName());
		assertEquals(1, myStream.getModuleDefinitions().size());
		ModuleDefinition logDefinition = myStream.getModuleDefinitions().get(0);
		assertEquals(3, logDefinition.getParameters().size());
		assertEquals("foo", logDefinition.getParameters().get(BindingProperties.INPUT_BINDING_KEY));
		assertEquals("myStream", logDefinition.getParameters().get(BindingProperties.INPUT_GROUP_KEY));
		assertEquals("true", logDefinition.getParameters().get(BindingProperties.INPUT_DURABLE_SUBSCRIPTION_KEY));
	}

	@Test
	public void testSourceChannelWithTwoModules() throws Exception {
		assertEquals(0, repository.count());
		String definition = "queue:foo > filter | log";
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream").param("definition", definition)
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
						.andExpect(status().isCreated());
		assertEquals(1, repository.count());
		StreamDefinition myStream = repository.findOne("myStream");
		assertEquals(definition, myStream.getDslText());
		assertEquals("myStream", myStream.getName());
		assertEquals(2, myStream.getModuleDefinitions().size());
		ModuleDefinition filterDefinition = myStream.getModuleDefinitions().get(0);
		assertEquals(4, filterDefinition.getParameters().size());
		assertEquals("foo", filterDefinition.getParameters().get(BindingProperties.INPUT_BINDING_KEY));
		assertEquals("myStream", filterDefinition.getParameters().get(BindingProperties.INPUT_GROUP_KEY));
		assertEquals("true", filterDefinition.getParameters().get(BindingProperties.INPUT_DURABLE_SUBSCRIPTION_KEY));
		assertEquals("myStream.0", filterDefinition.getParameters().get(BindingProperties.OUTPUT_BINDING_KEY));
		ModuleDefinition logDefinition = myStream.getModuleDefinitions().get(1);
		assertEquals(3, logDefinition.getParameters().size());
		assertEquals("myStream.0", logDefinition.getParameters().get(BindingProperties.INPUT_BINDING_KEY));
		assertEquals("default", logDefinition.getParameters().get(BindingProperties.INPUT_GROUP_KEY));
		assertEquals("true", logDefinition.getParameters().get(BindingProperties.INPUT_DURABLE_SUBSCRIPTION_KEY));
	}

	@Test
	public void testSinkChannelWithSingleModule() throws Exception {
		assertEquals(0, repository.count());
		String definition = "time > queue:foo";
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream").param("definition", definition)
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
						.andExpect(status().isCreated());
		assertEquals(1, repository.count());
		StreamDefinition myStream = repository.findOne("myStream");
		assertEquals(definition, myStream.getDslText());
		assertEquals("myStream", myStream.getName());
		assertEquals(1, myStream.getModuleDefinitions().size());
		ModuleDefinition timeDefinition = myStream.getModuleDefinitions().get(0);
		assertEquals(1, timeDefinition.getParameters().size());
		assertEquals("foo", timeDefinition.getParameters().get(BindingProperties.OUTPUT_BINDING_KEY));
	}

	@Test
	public void testSinkChannelWithTwoModules() throws Exception {
		assertEquals(0, repository.count());
		String definition = "time | filter > queue:foo";
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream").param("definition", definition)
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
						.andExpect(status().isCreated());
		assertEquals(1, repository.count());
		StreamDefinition myStream = repository.findOne("myStream");
		assertEquals(definition, myStream.getDslText());
		assertEquals("myStream", myStream.getName());
		assertEquals(2, myStream.getModuleDefinitions().size());
		ModuleDefinition timeDefinition = myStream.getModuleDefinitions().get(0);
		assertEquals(1, timeDefinition.getParameters().size());
		assertEquals("myStream.0", timeDefinition.getParameters().get(BindingProperties.OUTPUT_BINDING_KEY));
		ModuleDefinition filterDefinition = myStream.getModuleDefinitions().get(1);
		assertEquals(4, filterDefinition.getParameters().size());
		assertEquals("myStream.0", filterDefinition.getParameters().get(BindingProperties.INPUT_BINDING_KEY));
		assertEquals("default", filterDefinition.getParameters().get(BindingProperties.INPUT_GROUP_KEY));
		assertEquals("true", filterDefinition.getParameters().get(BindingProperties.INPUT_DURABLE_SUBSCRIPTION_KEY));
		assertEquals("foo", filterDefinition.getParameters().get(BindingProperties.OUTPUT_BINDING_KEY));
	}

	@Test
	public void testChannelsOnBothSides() throws Exception {
		assertEquals(0, repository.count());
		String definition = "queue:bar > filter > queue:foo";
		mockMvc.perform(
				post("/streams/definitions/").param("name", "myStream").param("definition", definition)
						.param("deploy", "true")
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
						.andExpect(status().isCreated());
		assertEquals(1, repository.count());
		StreamDefinition myStream = repository.findOne("myStream");
		assertEquals(definition, myStream.getDslText());
		assertEquals("myStream", myStream.getName());
		assertEquals(1, myStream.getModuleDefinitions().size());
		ModuleDefinition filterDefinition = myStream.getModuleDefinitions().get(0);
		assertEquals(4, filterDefinition.getParameters().size());
		assertEquals("bar", filterDefinition.getParameters().get(BindingProperties.INPUT_BINDING_KEY));
		assertEquals("myStream", filterDefinition.getParameters().get(BindingProperties.INPUT_GROUP_KEY));
		assertEquals("true", filterDefinition.getParameters().get(BindingProperties.INPUT_DURABLE_SUBSCRIPTION_KEY));
		assertEquals("foo", filterDefinition.getParameters().get(BindingProperties.OUTPUT_BINDING_KEY));

		ArgumentCaptor<ModuleDeploymentRequest> captor = ArgumentCaptor.forClass(ModuleDeploymentRequest.class);
		verify(moduleDeployer).deploy(captor.capture());
		ModuleDeploymentRequest request = captor.getValue();
		assertThat(request.getDefinition().getName(), is("filter"));
		assertThat(request.getCoordinates().getArtifactId(), is("filter-processor"));
	}

	@Test
	public void testDestroyStream() throws Exception {
		repository.save(new StreamDefinition("myStream", "time | log"));
		assertEquals(1, repository.count());
		ModuleStatus status = mock(ModuleStatus.class);
		when(status.getState()).thenReturn(DeploymentState.unknown);
		when(moduleDeployer.status(ModuleDeploymentId.parse("myStream.time"))).thenReturn(status);
		when(moduleDeployer.status(ModuleDeploymentId.parse("myStream.log"))).thenReturn(status);
		mockMvc.perform(
				delete("/streams/definitions/myStream").accept(MediaType.APPLICATION_JSON)).andDo(print())
				.andExpect(status().isOk());
		assertEquals(0, repository.count());
	}

	@Test
	public void testDestroySingleStream() throws Exception {
		repository.save(new StreamDefinition("myStream", "time | log"));
		repository.save(new StreamDefinition("myStream1", "time | log"));
		assertEquals(2, repository.count());
		ModuleStatus status = mock(ModuleStatus.class);
		when(status.getState()).thenReturn(DeploymentState.unknown);
		when(moduleDeployer.status(ModuleDeploymentId.parse("myStream.time"))).thenReturn(status);
		when(moduleDeployer.status(ModuleDeploymentId.parse("myStream.log"))).thenReturn(status);
		mockMvc.perform(
				delete("/streams/definitions/myStream").accept(MediaType.APPLICATION_JSON)).andDo(print())
				.andExpect(status().isOk());
		assertEquals(1, repository.count());
	}

	@Test
	public void testDisplaySingleStream() throws Exception {
		repository.save(new StreamDefinition("myStream", "time | log"));
		assertEquals(1, repository.count());
		ModuleStatus status = mock(ModuleStatus.class);
		when(status.getState()).thenReturn(DeploymentState.unknown);
		when(moduleDeployer.status(ModuleDeploymentId.parse("myStream.time"))).thenReturn(status);
		when(moduleDeployer.status(ModuleDeploymentId.parse("myStream.log"))).thenReturn(status);
		mockMvc.perform(
				get("/streams/definitions/myStream").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().json("{name: \"myStream\"}"))
				.andExpect(content().json("{dslText: \"time | log\"}"));
	}

	@Test
	public void testDestroyStreamNotFound() throws Exception {
		mockMvc.perform(
				delete("/streams/definitions/myStream").accept(MediaType.APPLICATION_JSON)).andDo(print())
				.andExpect(status().is5xxServerError());
		assertEquals(0, repository.count());
	}

	@Test
	public void testDeploy() throws Exception {
		repository.save(new StreamDefinition("myStream", "time | log"));
		mockMvc.perform(
				post("/streams/deployments/myStream").accept(MediaType.APPLICATION_JSON)).andDo(print())
				.andExpect(status().isCreated());
		ArgumentCaptor<ModuleDeploymentRequest> captor = ArgumentCaptor.forClass(ModuleDeploymentRequest.class);
		verify(moduleDeployer, times(2)).deploy(captor.capture());
		List<ModuleDeploymentRequest> requests = captor.getAllValues();
		assertEquals(2, requests.size());
		ModuleDeploymentRequest logRequest = requests.get(0);
		assertThat(logRequest.getDefinition().getName(), is("log"));
		ModuleDeploymentRequest timeRequest = requests.get(1);
		assertThat(timeRequest.getDefinition().getName(), is("time"));
	}

	@Test
	public void testDeployWithProperties() throws Exception {
		repository.save(new StreamDefinition("myStream", "time | log"));
		mockMvc.perform(
				post("/streams/deployments/myStream").param("properties",
						"module.time.producer.partitionKeyExpression=payload," +
								"module.time.producer.trackHistory=true," +
								"module.log.count=2," +
								"module.log.consumer.concurrency=3," +
								"module.log.consumer.trackHistory=true")
						.accept(MediaType.APPLICATION_JSON)).andDo(print())
				.andExpect(status().isCreated());
		ArgumentCaptor<ModuleDeploymentRequest> captor = ArgumentCaptor.forClass(ModuleDeploymentRequest.class);
		verify(moduleDeployer, times(2)).deploy(captor.capture());
		List<ModuleDeploymentRequest> requests = captor.getAllValues();
		assertEquals(2, requests.size());
		ModuleDeploymentRequest logRequest = requests.get(0);
		assertThat(logRequest.getDefinition().getName(), is("log"));
		Map<String, String> logDeploymentProps = logRequest.getDeploymentProperties();
		assertEquals(logDeploymentProps.get("spring.cloud.stream.bindings.input.trackHistory"), "true");
		assertEquals(logDeploymentProps.get("spring.cloud.stream.instanceCount"), "2");
		assertEquals(logDeploymentProps.get("spring.cloud.stream.bindings.input.partitioned"), "true");
		assertEquals(logDeploymentProps.get("spring.cloud.stream.bindings.input.concurrency"), "3");
		assertEquals(logDeploymentProps.get("count"), "2");
		ModuleDeploymentRequest timeRequest = requests.get(1);
		assertThat(timeRequest.getDefinition().getName(), is("time"));
		Map<String, String> timeDeploymentProps = timeRequest.getDeploymentProperties();
		assertEquals(timeDeploymentProps.get("spring.cloud.stream.bindings.output.trackHistory"), "true");
		assertEquals(timeDeploymentProps.get("spring.cloud.stream.bindings.output.partitionCount"), "2");
		assertEquals(timeDeploymentProps.get("spring.cloud.stream.bindings.output.partitionKeyExpression"), "payload");
	}

	@Test
	public void testAggregateState() {
		assertThat(StreamDefinitionController.aggregateState(EnumSet.of(deployed, failed)), is(failed));
		assertThat(StreamDefinitionController.aggregateState(EnumSet.of(unknown, failed)), is(failed));
		assertThat(StreamDefinitionController.aggregateState(EnumSet.of(deployed, failed, error)), is(error));
		assertThat(StreamDefinitionController.aggregateState(EnumSet.of(deployed, undeployed)), is(partial));
		assertThat(StreamDefinitionController.aggregateState(EnumSet.of(deployed, unknown)), is(partial));
		assertThat(StreamDefinitionController.aggregateState(EnumSet.of(undeployed, unknown)), is(partial));
		assertThat(StreamDefinitionController.aggregateState(EnumSet.of(unknown)), is(undeployed));
	}

}
