/*
 * Copyright 2016-2019 the original author or authors.
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
package org.springframework.cloud.dataflow.server.single;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.Filter;

import org.junit.rules.ExternalResource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.cloud.dataflow.core.Launcher;
import org.springframework.cloud.dataflow.server.EnableDataFlowServer;
import org.springframework.cloud.dataflow.server.config.features.FeaturesProperties;
import org.springframework.cloud.dataflow.server.job.LauncherRepository;
import org.springframework.cloud.deployer.spi.cloudfoundry.CloudFoundryDeployerAutoConfiguration;
import org.springframework.cloud.deployer.spi.kubernetes.KubernetesAutoConfiguration;
import org.springframework.cloud.deployer.spi.local.LocalDeployerAutoConfiguration;
import org.springframework.cloud.deployer.spi.local.LocalDeployerProperties;
import org.springframework.cloud.deployer.spi.local.LocalTaskLauncher;
import org.springframework.cloud.scheduler.spi.core.ScheduleInfo;
import org.springframework.cloud.scheduler.spi.core.ScheduleRequest;
import org.springframework.cloud.scheduler.spi.core.Scheduler;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.cloud.skipper.domain.AboutResource;
import org.springframework.cloud.skipper.domain.Dependency;
import org.springframework.cloud.skipper.domain.VersionInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.hateoas.Resources;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Marius Bogoevici
 * @author Gunnar Hillert
 */
public class LocalDataflowResource extends ExternalResource {

	final boolean streamsEnabled;

	final boolean tasksEnabled;

	final boolean schedulerEnabled;

	private String originalConfigLocation = null;

	private SpringApplication app;

	private MockMvc mockMvc;

	private String dataflowPort;

	private String skipperServerPort;

	private String configurationLocation;

	private WebApplicationContext configurableApplicationContext;

	private SkipperClient skipperClient;

	public SkipperClient getSkipperClient() {
		return skipperClient;
	}

	public LocalDataflowResource(String configurationLocation) {
		this.configurationLocation = configurationLocation;
		this.streamsEnabled = true;
		this.tasksEnabled = true;
		this.schedulerEnabled = false;
	}

	public LocalDataflowResource(String configurationLocation, boolean streamsEnabled, boolean tasksEnabled) {
		this.configurationLocation = configurationLocation;
		this.streamsEnabled = streamsEnabled;
		this.tasksEnabled = tasksEnabled;
		this.schedulerEnabled = false;
	}

	public LocalDataflowResource(String configurationLocation, boolean streamsEnabled, boolean tasksEnabled, boolean metricsEnabled) {
		this.configurationLocation = configurationLocation;
		this.streamsEnabled = streamsEnabled;
		this.tasksEnabled = tasksEnabled;
		this.schedulerEnabled = false;
	}

	public LocalDataflowResource(String configurationLocation, boolean streamsEnabled, boolean tasksEnabled, boolean metricsEnabled, boolean schedulerEnabled) {
		this.configurationLocation = configurationLocation;
		this.streamsEnabled = streamsEnabled;
		this.tasksEnabled = tasksEnabled;
		this.schedulerEnabled = schedulerEnabled;
	}

	public LocalDataflowResource(String configurationLocation, boolean streamsEnabled, boolean tasksEnabled,
			boolean metricsEnabled, boolean schedulerEnabled, String skipperServerPort) {
		this.configurationLocation = configurationLocation;
		this.streamsEnabled = streamsEnabled;
		this.tasksEnabled = tasksEnabled;
		this.schedulerEnabled = schedulerEnabled;
		this.skipperServerPort = skipperServerPort;
	}

	@Override
	protected void before() {
		originalConfigLocation = System.getProperty("spring.config.additional-locationn");

		if (!StringUtils.isEmpty(configurationLocation)) {
			final Resource resource = new PathMatchingResourcePatternResolver().getResource(configurationLocation);
			if (!resource.exists()) {
			  throw new IllegalArgumentException(String.format("Resource 'configurationLocation' ('%s') does not exist.", configurationLocation));
			}
			System.setProperty("spring.config.additional-location", configurationLocation);
		}

		app = new SpringApplication(TestConfig.class);

		configurableApplicationContext = (WebApplicationContext) app.run(new String[] {
				"--spring.cloud.kubernetes.enabled=false",
				"--server.port=0",
				"--" + FeaturesProperties.FEATURES_PREFIX + "." + FeaturesProperties.STREAMS_ENABLED + "="
						+ this.streamsEnabled,
				"--" + FeaturesProperties.FEATURES_PREFIX + "." + FeaturesProperties.TASKS_ENABLED + "="
						+ this.tasksEnabled,
				"--" + FeaturesProperties.FEATURES_PREFIX + "." + FeaturesProperties.SCHEDULES_ENABLED + "="
						+ this.schedulerEnabled,
				"--spring.cloud.skipper.client.serverUri=http://localhost:" + this.skipperServerPort + "/api"
		});
		skipperClient = configurableApplicationContext.getBean(SkipperClient.class);
		LauncherRepository launcherRepository = configurableApplicationContext.getBean(LauncherRepository.class);
		launcherRepository.save(new Launcher("default", "local", new LocalTaskLauncher(new LocalDeployerProperties())));
		Collection<Filter> filters = configurableApplicationContext.getBeansOfType(Filter.class).values();
		mockMvc = MockMvcBuilders.webAppContextSetup(configurableApplicationContext)
				.addFilters(filters.toArray(new Filter[filters.size()])).build();
		dataflowPort = configurableApplicationContext.getEnvironment().resolvePlaceholders("${server.port}");
	}

	@Override
	protected void after() {
		SpringApplication.exit(configurableApplicationContext);
		resetConfigLocation();
	}

	private void resetConfigLocation() {
		if (originalConfigLocation != null) {
			System.setProperty("spring.config.additional-location", originalConfigLocation);
		}
		else {
			System.clearProperty("spring.config.additional-location");
		}
	}

	public MockMvc getMockMvc() {
		return mockMvc;
	}

	public String getDataflowPort() {
		return dataflowPort;
	}

	public WebApplicationContext getWebApplicationContext() {
		return configurableApplicationContext;
	}

	public void mockSkipperAboutInfo() {
		AboutResource about = new AboutResource();
		about.setVersionInfo(new VersionInfo());
		about.getVersionInfo().setServer(new Dependency());
		about.getVersionInfo().getServer().setName("Test Server");
		about.getVersionInfo().getServer().setVersion("Test Version");
		when(this.skipperClient.info()).thenReturn(about);
		when(this.skipperClient.listDeployers()).thenReturn(new Resources<>(new ArrayList<>(), new ArrayList<>()));
	}

	@EnableAutoConfiguration(
		exclude = {
				SessionAutoConfiguration.class,
				ManagementWebSecurityAutoConfiguration.class,
				//SecurityAutoConfiguration.class,
				UserDetailsServiceAutoConfiguration.class,
				LocalDeployerAutoConfiguration.class,
				CloudFoundryDeployerAutoConfiguration.class,
				KubernetesAutoConfiguration.class
		},
		excludeName = "org.springframework.cloud.dataflow.rest.client.config.DataFlowClientAutoConfiguration")
	@EnableDataFlowServer
	@Configuration
	public static class TestConfig {

		@Bean
		@Primary
		public SkipperClient skipperClientMock() {
			SkipperClient skipperClient = mock(SkipperClient.class);
			AboutResource about = new AboutResource();
			about.setVersionInfo(new VersionInfo());
			about.getVersionInfo().setServer(new Dependency());
			about.getVersionInfo().getServer().setName("Test Server");
			about.getVersionInfo().getServer().setVersion("Test Version");
			when(skipperClient.info()).thenReturn(about);
			when(skipperClient.listDeployers()).thenReturn(new Resources<>(new ArrayList<>(), new ArrayList<>()));
			return skipperClient;
		}

		@Bean
		@ConditionalOnMissingBean
		public Scheduler localScheduler() {
			return new Scheduler() {
				@Override
				public void schedule(ScheduleRequest scheduleRequest) {
					throw new UnsupportedOperationException("Interface is not implemented for schedule method.");
				}

				@Override
				public void unschedule(String scheduleName) {
					throw new UnsupportedOperationException("Interface is not implemented for unschedule method.");
				}

				@Override
				public List<ScheduleInfo> list(String taskDefinitionName) {
					throw new UnsupportedOperationException("Interface is not implemented for list method.");
				}

				@Override
				public List<ScheduleInfo> list() {
					throw new UnsupportedOperationException("Interface is not implemented for list method.");
				}
			};
		}

	}

}
