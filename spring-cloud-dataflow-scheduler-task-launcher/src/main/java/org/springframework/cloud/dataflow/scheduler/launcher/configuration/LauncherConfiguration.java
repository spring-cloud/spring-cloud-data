/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.cloud.dataflow.scheduler.launcher.configuration;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.rest.client.DataFlowOperations;
import org.springframework.cloud.dataflow.rest.client.TaskOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configure the SchedulerTaskLauncher application.
 *
 * @author Glenn Renfro
 */
@Configuration
@EnableConfigurationProperties({SchedulerTaskLauncherProperties.class})
public class LauncherConfiguration {

	@Bean
	public SchedulerTaskLauncher launchRequestConsumer(
			SchedulerTaskLauncherProperties schedulerTaskLauncherProperties,
			TaskOperations taskOperations, Environment environment) {
		return new SchedulerTaskLauncher(taskOperations, schedulerTaskLauncherProperties,
				environment);
	}

	@Bean
	public TaskOperations getTaskOperations(SchedulerTaskLauncherProperties schedulerTaskLauncherProperties,
			DataFlowOperations dataFlowOperations) {
		try {
			final URI dataFlowUri = new URI(schedulerTaskLauncherProperties.getDataflowServerUri());
			if (dataFlowOperations.taskOperations() == null) {
				throw new SchedulerTaskLauncherException("The task operations are not enabled in the Spring Cloud Data Flow server");
			}
			return dataFlowOperations.taskOperations();
		}
		catch (URISyntaxException e) {
			throw new SchedulerTaskLauncherException("Invalid Spring Cloud Data Flow URI", e);
		}
	}

	@Bean
	public CommandLineRunner commandLineRunner(SchedulerTaskLauncher schedulerTaskLauncher) {
		return args -> {
			schedulerTaskLauncher.launchTask(args);
		};
	}

}
