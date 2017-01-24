/*
 * Copyright 2015-2017 the original author or authors.
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

package org.springframework.cloud.dataflow.server.service.impl;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.h2.util.Task;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.core.TaskDefinition.TaskDefinitionBuilder;
import org.springframework.cloud.dataflow.rest.util.DeploymentPropertiesUtils;
import org.springframework.cloud.dataflow.server.controller.WhitelistProperties;
import org.springframework.cloud.dataflow.server.repository.NoSuchTaskDefinitionException;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.TaskService;
import org.springframework.cloud.deployer.resource.registry.UriRegistry;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link TaskService} interface. Provide service methods
 * for {@link Task}s.
 *
 * @author Michael Minella
 * @author Marius Bogoevici
 * @author Glenn Renfro
 * @author Mark Fisher
 * @author Janne Valkealahti
 * @author Gunnar Hillert
 */
public class DefaultTaskService implements TaskService {

	private final DataSourceProperties dataSourceProperties;

	/**
	 * Used to create TaskExecutions.
	 */
	private final TaskRepository taskExecutionRepository;

	/**
	 * Used to read TaskExecutions.
	 */
	private final TaskExplorer taskExplorer;

	/**
	 * Used to launch apps as tasks.
	 */
	private final TaskLauncher taskLauncher;

	/**
	 * The {@link UriRegistry} this service will use to look up task app URIs.
	 */
	private final UriRegistry registry;

	/**
	 * The {@link ResourceLoader} that will resolve URIs to {@link Resource}s.
	 */
	private final ResourceLoader resourceLoader;

	private final TaskDefinitionRepository taskDefinitionRepository;

	private final WhitelistProperties whitelistProperties;

	/**
	 * Initializes the {@link DefaultTaskService}.
	 *
	 * @param dataSourceProperties the data source properties.
	 * @param taskDefinitionRepository the {@link TaskDefinitionRepository} this service will use for
	 * task CRUD operations.
	 * @param taskExecutionRepository the repository this service will use for deployment
	 * IDs.
	 * @param taskExplorer the explorer this service will use to lookup task executions
	 * @param registry URI registry this service will use to look up app URIs.
	 * @param resourceLoader the {@link ResourceLoader} that will resolve URIs to
	 * {@link Resource}s.
	 * @param taskLauncher the launcher this service will use to launch task apps.
	 */
	public DefaultTaskService(DataSourceProperties dataSourceProperties,
			TaskDefinitionRepository taskDefinitionRepository,
			TaskExplorer taskExplorer,
			TaskRepository taskExecutionRepository, UriRegistry registry,
			ResourceLoader resourceLoader, TaskLauncher taskLauncher,
			ApplicationConfigurationMetadataResolver metaDataResolver) {
		Assert.notNull(dataSourceProperties, "DataSourceProperties must not be null");
		Assert.notNull(taskDefinitionRepository, "TaskDefinitionRepository must not be null");
		Assert.notNull(taskExecutionRepository, "TaskExecutionRepository must not be null");
		Assert.notNull(taskExplorer, "TaskExplorer must not be null");
		Assert.notNull(registry, "UriRegistry must not be null");
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		Assert.notNull(taskLauncher, "TaskLauncher must not be null");
		Assert.notNull(metaDataResolver, "metaDataResolver must not be null");
		this.dataSourceProperties = dataSourceProperties;
		this.taskDefinitionRepository = taskDefinitionRepository;
		this.taskExecutionRepository = taskExecutionRepository;
		this.taskExplorer = taskExplorer;
		this.registry = registry;
		this.taskLauncher = taskLauncher;
		this.resourceLoader = resourceLoader;
		this.whitelistProperties = new WhitelistProperties(metaDataResolver);
	}

	@Override
	public void executeTask(String taskName, Map<String, String> taskDeploymentProperties,
			List<String> commandLineArgs) {
		Assert.hasText(taskName, "The provided taskName must not be null or empty.");
		Assert.notNull(taskDeploymentProperties,
				"The provided runtimeProperties must not be null.");
		TaskDefinition taskDefinition = this.taskDefinitionRepository.findOne(taskName);
		if (taskDefinition == null) {
			throw new NoSuchTaskDefinitionException(taskName);
		}

		URI uri = this.registry.find(String.format("task.%s", taskDefinition.getRegisteredAppName()));
		Resource resource = this.resourceLoader.getResource(uri.toString());
		TaskExecution taskExecution = taskExecutionRepository.createTaskExecution();
		taskDefinition = this.updateTaskProperties(taskDefinition, taskExecution);

		Map<String, String> appDeploymentProperties = extractAppProperties(taskDefinition.getRegisteredAppName(), taskDeploymentProperties);
		Map<String, String> deployerDeploymentProperties = DeploymentPropertiesUtils.extractAndQualifyDeployerProperties(taskDeploymentProperties, taskDefinition.getRegisteredAppName());
		AppDefinition revisedDefinition = mergeAndExpandAppProperties(taskDefinition, resource, appDeploymentProperties);

		AppDeploymentRequest request = new AppDeploymentRequest(revisedDefinition, resource, deployerDeploymentProperties, commandLineArgs);

		String id = this.taskLauncher.launch(request);
		if (!StringUtils.hasText(id)) {
			throw new IllegalStateException("Deployment ID is null for the task:"
					+ taskName);
		}
		taskExecutionRepository.updateExternalExecutionId(taskExecution.getExecutionId(), id);
	}

	@Override
	public void cleanupExecution(long id) {
		TaskExecution taskExecution = taskExplorer.getTaskExecution(id);
		Assert.notNull(taskExecution, "There was no task execution with id " + id);
		String launchId = taskExecution.getExternalExecutionId();
		Assert.hasLength(launchId, "The TaskExecution for id " + id + " did not have an externalExecutionId");
		taskLauncher.cleanup(launchId);
	}

	private Map<String, String> extractAppProperties(String name, Map<String, String> taskDeploymentProperties) {
		final String prefix = "app." + name + ".";
		return taskDeploymentProperties.entrySet().stream()
			.filter(kv -> kv.getKey().startsWith(prefix))
			.collect(Collectors.toMap(
				kv -> kv.getKey().substring(prefix.length()),
				kv -> kv.getValue()
			));
	}

	/**
	 * Return a copy of a given task definition where short form parameters have been expanded to their long form
	 * (amongst the whitelisted supported properties of the app) if applicable.
	 */
	private AppDefinition mergeAndExpandAppProperties(TaskDefinition original, Resource resource, Map<String, String> appDeploymentProperties) {
		Map<String, String> merged = new HashMap<>(original.getProperties());
		merged.putAll(appDeploymentProperties);
		merged = whitelistProperties.qualifyProperties(merged, resource);
		return new AppDefinition(original.getName(), merged);
	}

	private TaskDefinition updateTaskProperties(TaskDefinition taskDefinition, TaskExecution taskExecution) {
		TaskDefinitionBuilder builder = TaskDefinitionBuilder.from(taskDefinition);
		builder.setProperty("spring.datasource.url", dataSourceProperties.getUrl());
		builder.setProperty("spring.datasource.username",
			dataSourceProperties.getUsername());
		// password may be empty
		if (StringUtils.hasText(dataSourceProperties.getPassword())) {
			builder.setProperty("spring.datasource.password",
				dataSourceProperties.getPassword());
		}
		builder.setProperty("spring.datasource.driverClassName",
			dataSourceProperties.getDriverClassName());

		builder.setProperty("spring.cloud.task.executionid", String.valueOf(taskExecution.getExecutionId()));
		return builder.build();
	}


}
