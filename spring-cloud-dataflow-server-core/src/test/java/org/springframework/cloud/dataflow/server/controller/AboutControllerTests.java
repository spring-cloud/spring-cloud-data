/*
 * Copyright 2017-2023 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.server.configuration.TestDependencies;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Glenn Renfro
 * @author Felipe Gutierrez
 * @author Chris Bono
 * @author Corneil du Plessis
 */
@SpringBootTest(classes = TestDependencies.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = {
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-implementation.version=1.2.3.IMPLEMENTATION.TEST",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-core.version=1.2.3.CORE.TEST",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-dashboard.version=1.2.3.UI.TEST",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.SHELL.TEST",
		"spring.cloud.dataflow.version-info.dependency-fetch.enabled=true",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.url=https://repo.spring.io/milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.name=Spring Cloud Data Flow Shell Test",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG",
		"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1-url={repository}/org/springframework/cloud/spring-cloud-dataflow-shell/{version}/spring-cloud-dataflow-shell-{version}.jar.sha1"
})
@AutoConfigureTestDatabase(replace = Replace.ANY)
class AboutControllerTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext wac;

	@BeforeEach
	void setupMocks() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
				.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
	}

	@Test
	void listApplications() throws Exception {
		ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
		result.andDo(print()).andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
				.andExpect(jsonPath("$.versionInfo.implementation.name", is("${info.app.name}")))
				.andExpect(jsonPath("$.versionInfo.implementation.version", is("1.2.3.IMPLEMENTATION.TEST")))
				.andExpect(jsonPath("$.versionInfo.core.name", is("Spring Cloud Data Flow Core")))
				.andExpect(jsonPath("$.versionInfo.core.version", is("1.2.3.CORE.TEST")))
				.andExpect(jsonPath("$.versionInfo.dashboard.name", is("Spring Cloud Dataflow UI")))
				.andExpect(jsonPath("$.versionInfo.dashboard.version", is("1.2.3.UI.TEST")))
				.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell Test")))
				.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf")))
				.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.SHELL.TEST")))
				.andExpect(jsonPath("$.versionInfo.shell.checksumSha1", is("ABCDEFG")))
				.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist())
				.andExpect(jsonPath("$.securityInfo.authenticationEnabled", is(false)))
				.andExpect(jsonPath("$.securityInfo.authenticated", is(false)))
				.andExpect(jsonPath("$.securityInfo.username", is(emptyOrNullString())))
				.andExpect(jsonPath("$.featureInfo.streamsEnabled", is(true)))
				.andExpect(jsonPath("$.featureInfo.tasksEnabled", is(true)))
				.andExpect(jsonPath("$.featureInfo.schedulesEnabled", is(false)))
				.andExpect(jsonPath("$.runtimeEnvironment.appDeployer.deployerName", not(emptyOrNullString())));
	}

	@Nested
	@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.SHELL.TEST",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.url=https://repo.spring.io/milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	@AutoConfigureTestDatabase(replace = Replace.ANY)
	class ChecksumDisabledTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@BeforeEach
		void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		void checksumDisabled() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.SHELL.TEST")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@Nested
	@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=3.0.0-SNAPSHOT",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	@AutoConfigureTestDatabase(replace = Replace.ANY)
	class SnapshotUrlTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@BeforeEach
		void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		void snapshotVersionInfo() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/snapshot/org/springframework/cloud/spring-cloud-dataflow-shell/3.0.0-SNAPSHOT/spring-cloud-dataflow-shell-3.0.0-SNAPSHOT.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("3.0.0-SNAPSHOT")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@Nested
	@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.M1",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	@AutoConfigureTestDatabase(replace = Replace.ANY)
	class MilestoneUrlTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@BeforeEach
		void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		void milestone() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.2.3.M1/spring-cloud-dataflow-shell-1.2.3.M1.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.M1")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@Nested
	@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.RC1",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	@AutoConfigureTestDatabase(replace = Replace.ANY)
	class RCUrlTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@BeforeEach
		void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		void rc() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.2.3.RC1/spring-cloud-dataflow-shell-1.2.3.RC1.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.RC1")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@Nested
	@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.GA",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	@AutoConfigureTestDatabase(replace = Replace.ANY)
	class GAUrlTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@BeforeEach
		void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		void ga() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-dataflow-shell/1.2.3.GA/spring-cloud-dataflow-shell-1.2.3.GA.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3.GA")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@Nested
	@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=false",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG"
	})
	@AutoConfigureTestDatabase(replace = Replace.ANY)
	class ReleaseUrlTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@BeforeEach
		void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		void release() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-dataflow-shell/1.2.3/spring-cloud-dataflow-shell-1.2.3.jar")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("1.2.3")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@Nested
	@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(classes = TestDependencies.class)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=1.2.3.RELEASE",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=true",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.url=https://repo.spring.io/milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1-url=",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha256-url="
	})
	@AutoConfigureTestDatabase(replace = Replace.ANY)
	class ChecksumNoDefaultTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@BeforeEach
		void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		void checksumNoDefaults() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo.spring.io/milestone/org/springframework/cloud/spring-cloud-dataflow-shell/1.3.0.BUILD-SNAPSHOT/spring-cloud-dataflow-shell-1.3.0.BUILD-SNAPSHOT.jsdfasdf")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1").doesNotExist())
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist());
		}
	}

	@Nested
	@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
	@SpringBootTest(
			classes = { TestDependencies.class, ProjectInfoAutoConfiguration.class, InfoContributorAutoConfiguration.class }, 
			properties = { "management.info.git.enabled=true" }
	)
	@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
	@TestPropertySource(properties = {
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-implementation.version=1.2.3.IMPLEMENTATION.TEST",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-core.version=1.2.3.CORE.TEST",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-dashboard.version=1.2.3.UI.TEST",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.version=2.11.3",
			"spring.cloud.dataflow.version-info.dependency-fetch.enabled=true",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.url=https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dataflow-shell/2.11.3",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.name=Spring Cloud Data Flow Shell Test",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1=ABCDEFG",
			"spring.cloud.dataflow.version-info.dependencies.spring-cloud-dataflow-shell.checksum-sha1-url=https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dataflow-shell/2.11.3.jar.sha1",
			"spring.cloud.dataflow.metrics.dashboard.url=http://localhost:3001",
			"spring.cloud.dataflow.metrics.dashboard.type=GRAFANA",
			"spring.cloud.dataflow.metrics.dashboard.grafana.token=boza",
			"spring.cloud.dataflow.metrics.dashboard.grafana.refresh-interval=30"
	})
	@AutoConfigureTestDatabase(replace = Replace.ANY)
	class AboutTests {

		private MockMvc mockMvc;

		@Autowired
		private WebApplicationContext wac;

		@Autowired
		private SkipperClient skipperClient;

		@BeforeEach
		void setupMocks() {
			this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
					.defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).build();
		}

		@Test
		void about() throws Exception {
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andDo(print()).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.implementation.name", is("${info.app.name}")))
					.andExpect(jsonPath("$.versionInfo.implementation.version", is("1.2.3.IMPLEMENTATION.TEST")))
					.andExpect(jsonPath("$.versionInfo.core.name", is("Spring Cloud Data Flow Core")))
					.andExpect(jsonPath("$.versionInfo.core.version", is("1.2.3.CORE.TEST")))
					.andExpect(jsonPath("$.versionInfo.dashboard.name", is("Spring Cloud Dataflow UI")))
					.andExpect(jsonPath("$.versionInfo.dashboard.version", is("1.2.3.UI.TEST")))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell Test")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dataflow-shell/2.11.3")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("2.11.3")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1", is("ABCDEFG")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist())
					.andExpect(jsonPath("$.securityInfo.authenticationEnabled", is(false)))
					.andExpect(jsonPath("$.securityInfo.authenticated", is(false)))
					.andExpect(jsonPath("$.securityInfo.username", is(emptyOrNullString())))
					.andExpect(jsonPath("$.runtimeEnvironment.appDeployer.deployerName", is("skipper server")))
					.andExpect(jsonPath("$.monitoringDashboardInfo.url", is("http://localhost:3001")))
					.andExpect(jsonPath("$.monitoringDashboardInfo.dashboardType", is("GRAFANA")))
					.andExpect(jsonPath("$.gitAndBuildInfo.git.commit").isNotEmpty() )
					.andExpect(jsonPath("$.gitAndBuildInfo.build.version").isNotEmpty() )
					.andExpect(jsonPath("$.monitoringDashboardInfo.refreshInterval", is(30)));
		}

		@Test
		void aboutWithMissingSkipper() throws Exception {
			reset(this.skipperClient);
			Mockito.when(this.skipperClient.info()).thenThrow(new ResourceAccessException("Skipper Not There"));
			ResultActions result = mockMvc.perform(get("/about").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
			result.andExpect(jsonPath("$.featureInfo.analyticsEnabled", is(true)))
					.andExpect(jsonPath("$.versionInfo.implementation.name", is("${info.app.name}")))
					.andExpect(jsonPath("$.versionInfo.implementation.version", is("1.2.3.IMPLEMENTATION.TEST")))
					.andExpect(jsonPath("$.versionInfo.core.name", is("Spring Cloud Data Flow Core")))
					.andExpect(jsonPath("$.versionInfo.core.version", is("1.2.3.CORE.TEST")))
					.andExpect(jsonPath("$.versionInfo.dashboard.name", is("Spring Cloud Dataflow UI")))
					.andExpect(jsonPath("$.versionInfo.dashboard.version", is("1.2.3.UI.TEST")))
					.andExpect(jsonPath("$.versionInfo.shell.name", is("Spring Cloud Data Flow Shell Test")))
					.andExpect(jsonPath("$.versionInfo.shell.url", is("https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dataflow-shell/2.11.3")))
					.andExpect(jsonPath("$.versionInfo.shell.version", is("2.11.3")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha1", is("ABCDEFG")))
					.andExpect(jsonPath("$.versionInfo.shell.checksumSha256").doesNotExist())
					.andExpect(jsonPath("$.securityInfo.authenticationEnabled", is(false)))
					.andExpect(jsonPath("$.securityInfo.authenticated", is(false)))
					.andExpect(jsonPath("$.securityInfo.username", is(emptyOrNullString())))
					.andExpect(jsonPath("$.runtimeEnvironment.appDeployer.deployerName", is(emptyOrNullString()))); // Connection to Skipper is lost!
		}
	}
}
