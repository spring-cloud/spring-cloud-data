/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.core.AppRegistration;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.registry.repository.AppRegistrationRepository;
import org.springframework.cloud.dataflow.server.configuration.TestDependencies;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.cloud.skipper.domain.Info;
import org.springframework.cloud.skipper.domain.Status;
import org.springframework.cloud.skipper.domain.StatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ilayaperumal Gopinathan
 * @author Christian Tzolov
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestDependencies.class)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureTestDatabase(replace = Replace.ANY)
public class RuntimeAppsControllerTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext wac;

	@Autowired
	private AppRegistrationRepository appRegistrationRepository;

	@Autowired
	private StreamDefinitionRepository streamDefinitionRepository;

	@Autowired
	private SkipperClient skipperClient;

	@Before
	public void setupMocks() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
				.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		for (AppRegistration appRegistration : this.appRegistrationRepository.findAll()) {
			this.appRegistrationRepository.deleteAll();
		}

		StreamDefinition streamDefinition3 = new StreamDefinition("ticktock3", "time|log");
		StreamDefinition streamDefinition4 = new StreamDefinition("ticktock4", "time|log");
		streamDefinitionRepository.save(streamDefinition3);
		streamDefinitionRepository.save(streamDefinition4);

		Info ticktock3Info = new Info();
		Status ticktock3Status = new Status();
		ticktock3Status.setStatusCode(StatusCode.DEPLOYED);
		ticktock3Status.setPlatformStatus("[{\"deploymentId\":\"ticktock3.log-v1\","
				+ "\"instances\":{\"ticktock3.log-v1-0\":{\"instanceNumber\":0,\"id\":\"ticktock3.log-v1-0\",\"state\":\"deployed\"}},\"state\":\"deployed\"},"
				+ "{\"deploymentId\":\"ticktock3.time-v1\",\"instances\":{\"ticktock3.time-v1-0\":{\"instanceNumber\":0,\"baseUrl\":\"http://192.168.1.100:32451\","
				+ "\"process\":{\"alive\":true,\"inputStream\":{},\"outputStream\":{},\"errorStream\":{}},"
				+ "\"attributes\":{\"guid\":\"32451\",\"pid\":\"53492\",\"port\":\"32451\"},"
				+ "\"id\":\"ticktock3.time-v1-0\",\"state\":\"deployed\"}},\"state\":\"deployed\"}]");
		ticktock3Info.setStatus(ticktock3Status);
		Info ticktock4Info = new Info();
		Status ticktock4Status = new Status();
		ticktock4Status.setStatusCode(StatusCode.DEPLOYED);
		ticktock4Status.setPlatformStatus("[{\"deploymentId\":\"ticktock4.log-v1\","
				+ "\"instances\":{\"ticktock4.log-v1-0\":{\"instanceNumber\":0,\"id\":\"ticktock4.log-v1-0\","
				+ "\"state\":\"deployed\"}},\"state\":\"deployed\"},"
				+ "{\"deploymentId\":\"ticktock4.time-v1\",\"instances\":{\"ticktock4.time-v1-0\":{\"instanceNumber\":0,"
				+ "\"baseUrl\":\"http://192.168.1.100:32451\","
				+ "\"process\":{\"alive\":true,\"inputStream\":{},\"outputStream\":{},\"errorStream\":{}},"
				+ "\"attributes\":{\"guid\":\"32451\",\"pid\":\"53492\",\"port\":\"32451\"},"
				+ "\"id\":\"ticktock4.time-v1-0\",\"state\":\"deployed\"}},\"state\":\"deployed\"}]");
		ticktock4Info.setStatus(ticktock4Status);
		when(this.skipperClient.status("ticktock3")).thenReturn(ticktock3Info);
		when(this.skipperClient.status("ticktock4")).thenReturn(ticktock4Info);
	}

	@Test
	public void testFindNonExistentApp() throws Exception {
		mockMvc.perform(get("/runtime/apps/foo").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().is4xxClientError())
				.andExpect(jsonPath("$[0].logref", is("NoSuchAppException")));
	}

	@Test
	public void testFindNonExistentAppUnknownState() throws Exception {
		Info info = new Info();
		info.setStatus(new Status());
		info.getStatus().setStatusCode(StatusCode.UNKNOWN);
		info.getStatus().setPlatformStatusAsAppStatusList(
				Arrays.asList(AppStatus.of("ticktock5.log2-v1").generalState(DeploymentState.unknown).build()));

		when(this.skipperClient.status("ticktock5")).thenReturn(info);
		streamDefinitionRepository.save(new StreamDefinition("ticktock5", "time2|log2"));

		mockMvc.perform(get("/runtime/apps/ticktock5.log2-v1.").accept(MediaType.APPLICATION_JSON)).andDo(print())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$[0].logref", is("NoSuchAppException")));
	}

	@Test
	public void testFindNonExistentAppInstance() throws Exception {
		Info info = new Info();
		info.setStatus(new Status());
		info.getStatus().setStatusCode(StatusCode.UNKNOWN);
		info.getStatus().setPlatformStatusAsAppStatusList(
				Arrays.asList(AppStatus.of("ticktock5.log2-v1").generalState(DeploymentState.unknown).build()));

		when(this.skipperClient.status("ticktock5")).thenReturn(info);
		streamDefinitionRepository.save(new StreamDefinition("ticktock5", "time2|log2"));

		mockMvc.perform(get("/runtime/apps/ticktock5.log2-v1/instances/log2-0").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().is4xxClientError())
				.andExpect(jsonPath("$[0].logref", is("NoSuchAppException")));

		info.getStatus().setPlatformStatusAsAppStatusList(
				Arrays.asList(AppStatus.of("ticktock5.log2-v1").generalState(DeploymentState.deployed).build()));

		mockMvc.perform(get("/runtime/apps/ticktock5.log2-v1/instances/log2-0").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().is4xxClientError())
				.andExpect(jsonPath("$[0].logref", is("NoSuchAppInstanceException")));
	}

	@Test
	public void testFindNonExistentAppInstance2() throws Exception {
		mockMvc.perform(
				get("/runtime/apps/ticktock4.log-v1/instances/ticktock4.log-v1-0.").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.instanceId", is("ticktock4.log-v1-0")))
				.andExpect(jsonPath("$.state", is("deployed")))
				.andExpect(jsonPath("$.attributes").value(nullValue()))
				.andExpect(jsonPath("$.links.*", hasSize(1)))
				.andExpect(jsonPath("$.links.[0].rel", is("self")))
				.andExpect(jsonPath("$.links.[0].href",
						is("http://localhost/runtime/apps/ticktock4.log-v1/instances/ticktock4.log-v1-0")));
	}

	@Test
	public void testListRuntimeApps() throws Exception {
		mockMvc.perform(get("/runtime/apps").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())

				.andExpect(jsonPath("$.content[0].deploymentId", is("ticktock3.log-v1")))
				.andExpect(jsonPath("$.content[1].deploymentId", is("ticktock3.time-v1")))
				.andExpect(jsonPath("$.content[2].deploymentId", is("ticktock4.log-v1")))
				.andExpect(jsonPath("$.content[3].deploymentId", is("ticktock4.time-v1")))
				.andExpect(jsonPath("$.content[0].instances.content[0].instanceId", is("ticktock3.log-v1-0")))
				.andExpect(jsonPath("$.content[1].instances.content[0].instanceId", is("ticktock3.time-v1-0")))
				.andExpect(jsonPath("$.content[2].instances.content[0].instanceId", is("ticktock4.log-v1-0")))
				.andExpect(jsonPath("$.content[3].instances.content[0].instanceId", is("ticktock4.time-v1-0")));
	}

	@Test
	public void testListRuntimeAppsPageSizes() throws Exception {
		mockMvc.perform(get("/runtime/apps?page=0&size=1").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())

				.andExpect(jsonPath("$.content.*", hasSize(1)))
				.andExpect(jsonPath("$.content[0].deploymentId", is("ticktock3.log-v1")));

		mockMvc.perform(get("/runtime/apps?page=0&size=2").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())

				.andExpect(jsonPath("$.content.*", hasSize(2)))
				.andExpect(jsonPath("$.content[0].deploymentId", is("ticktock3.log-v1")))
				.andExpect(jsonPath("$.content[1].deploymentId", is("ticktock3.time-v1")));

		mockMvc.perform(get("/runtime/apps?page=0&size=3").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())

				.andExpect(jsonPath("$.content.*", hasSize(3)))
				.andExpect(jsonPath("$.content[0].deploymentId", is("ticktock3.log-v1")))
				.andExpect(jsonPath("$.content[1].deploymentId", is("ticktock3.time-v1")))
				.andExpect(jsonPath("$.content[2].deploymentId", is("ticktock4.log-v1")));

		mockMvc.perform(get("/runtime/apps?page=0&size=4").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())

				.andExpect(jsonPath("$.content.*", hasSize(4)))
				.andExpect(jsonPath("$.content[0].deploymentId", is("ticktock3.log-v1")))
				.andExpect(jsonPath("$.content[1].deploymentId", is("ticktock3.time-v1")))
				.andExpect(jsonPath("$.content[2].deploymentId", is("ticktock4.log-v1")))
				.andExpect(jsonPath("$.content[3].deploymentId", is("ticktock4.time-v1")));

		mockMvc.perform(get("/runtime/apps?page=1&size=2").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())

				.andExpect(jsonPath("$.content.*", hasSize(2)))
				.andExpect(jsonPath("$.content[0].deploymentId", is("ticktock4.log-v1")))
				.andExpect(jsonPath("$.content[1].deploymentId", is("ticktock4.time-v1")));

		mockMvc.perform(get("/runtime/apps?page=1&size=4").accept(MediaType.APPLICATION_JSON))
				.andDo(print())
				.andExpect(status().isOk())

				.andExpect(jsonPath("$.content.*", hasSize(0)));
	}
}
