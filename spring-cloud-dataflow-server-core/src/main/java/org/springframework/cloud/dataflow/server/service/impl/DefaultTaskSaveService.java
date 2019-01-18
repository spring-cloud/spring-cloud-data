/*
 * Copyright 2016-2018 the original author or authors.
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

import java.util.stream.Collectors;

import org.springframework.cloud.dataflow.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.AuditActionType;
import org.springframework.cloud.dataflow.core.AuditOperationType;
import org.springframework.cloud.dataflow.core.DefinitionUtils;
import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.core.dsl.TaskNode;
import org.springframework.cloud.dataflow.core.dsl.TaskParser;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.registry.support.NoSuchAppRegistrationException;
import org.springframework.cloud.dataflow.rest.support.ArgumentSanitizer;
import org.springframework.cloud.dataflow.server.repository.DuplicateTaskException;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.TaskSaveService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Default implementation of the {@link DefaultTaskSaveService} interface. Provide service
 * methods for Task saving.
 *
 * @author Michael Minella
 * @author Marius Bogoevici
 * @author Glenn Renfro
 * @author Mark Fisher
 * @author Janne Valkealahti
 * @author Gunnar Hillert
 * @author Thomas Risberg
 * @author Ilayaperumal Gopinathan
 * @author Michael Wirth
 * @author David Turanski
 * @author Daniel Serleg
 */
public class DefaultTaskSaveService implements TaskSaveService {

	private final TaskDefinitionRepository taskDefinitionRepository;

	protected final AuditRecordService auditRecordService;

	/**
	 * The {@link AppRegistryService} this service will use to look up task app URIs.
	 */
	private final AppRegistryService registry;

	private final ArgumentSanitizer argumentSanitizer = new ArgumentSanitizer();

	public DefaultTaskSaveService(TaskDefinitionRepository taskDefinitionRepository,
			AuditRecordService auditRecordService, AppRegistryService registry) {
		Assert.notNull(taskDefinitionRepository, "TaskDefinitionRepository must not be null");
		Assert.notNull(auditRecordService, "AuditRecordService must not be null");
		Assert.notNull(registry, "AppRegistryService must not be null");

		this.taskDefinitionRepository = taskDefinitionRepository;
		this.auditRecordService = auditRecordService;
		this.registry = registry;
	}

	@Override
	@Transactional
	public void saveTaskDefinition(String name, String dsl) {
		TaskParser taskParser = new TaskParser(name, dsl, true, true);
		TaskNode taskNode = taskParser.parse();
		TaskDefinition taskDefinition = new TaskDefinition(name, dsl);
		if (taskDefinitionRepository.existsById(name)) {
			throw new DuplicateTaskException(String.format(
					"Cannot register task %s because another one has already " + "been registered with the same name",
					name));
		}
		if (taskNode.isComposed()) {
			// Create the child task definitions needed for the composed task
			taskNode.getTaskApps().forEach(task -> {
				// Add arguments to child task definitions
				String generatedTaskDSL = task.getName() + task.getArguments().entrySet().stream()
						.map(argument -> String.format(" --%s=%s", argument.getKey(),
								DefinitionUtils.autoQuotes(argument.getValue())))
						.collect(Collectors.joining());
				TaskDefinition composedTaskDefinition = new TaskDefinition(task.getExecutableDSLName(),
						generatedTaskDSL);
				saveStandardTaskDefinition(composedTaskDefinition);
			});
			taskDefinitionRepository.save(taskDefinition);
		}
		else {
			saveStandardTaskDefinition(taskDefinition);
		}
		auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.TASK, AuditActionType.CREATE,
				name, argumentSanitizer.sanitizeTaskDsl(taskDefinition));
	}

	private void saveStandardTaskDefinition(TaskDefinition taskDefinition) {
		String appName = taskDefinition.getRegisteredAppName();
		if (registry.find(appName, ApplicationType.task) == null) {
			throw new NoSuchAppRegistrationException(appName, ApplicationType.task);
		}
		if (taskDefinitionRepository.existsById(taskDefinition.getTaskName())) {
			throw new DuplicateTaskException(String.format(
					"Cannot register task %s because another one has already " + "been registered with the same name",
					taskDefinition.getTaskName()));
		}
		taskDefinitionRepository.save(taskDefinition);
	}
}
