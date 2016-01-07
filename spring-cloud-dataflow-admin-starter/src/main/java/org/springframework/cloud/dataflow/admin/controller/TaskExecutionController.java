/*
 * Copyright 2016 the original author or authors.
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.dataflow.admin.repository.TaskExecutionRepository;
import org.springframework.cloud.dataflow.rest.resource.TaskDefinitionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for operations on {@link org.springframework.cloud.task.repository.TaskExecution}.
 * This includes obtaining task execution information from the repository.
 *
 * @author Glenn Renfro
 */
@RestController
@RequestMapping("/tasks")
@ExposesResourceFor(TaskDefinitionResource.class)
public class TaskExecutionController {

	private final Assembler taskAssembler = new Assembler();

	@Autowired
	private TaskExecutionRepository repository;


	/**
	 * Creates a {@code TaskExecutionController} that retrieves Task Execution information
	 * from a the {@link TaskExecutionRepository}
	 *
	 * @param repository the repository this controller will use for retrieving
	 *                   task execution information.
	 */
	@Autowired
	public TaskExecutionController(TaskExecutionRepository repository) {
		Assert.notNull(repository, "repository must not be null");
		this.repository = repository;
	}

	/**
	 * Return a page-able list of {@link TaskExecutionResource} defined tasks.
	 *
	 * @param pageable  page-able collection of {@code TaskExecution}s.
	 * @param assembler for the {@link TaskExecution}s
	 * @return a list of task executions
	 */
	@RequestMapping(value = "/executions", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public PagedResources<TaskExecutionResource> list(Pageable pageable,
													  PagedResourcesAssembler<TaskExecution> assembler) {
		Page page = repository.getTaskExplorer().findAll(pageable);
		return assembler.toResource(page, taskAssembler);
	}

	/**
	 * Retrieve all task executions with the task name specified
	 *
	 * @param taskName name of the task
	 * @param pageable  page-able collection of {@code TaskExecution}s.
	 * @param assembler for the {@link TaskExecution}s
	 */
	@RequestMapping(value = "/executions/{taskName}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public PagedResources<TaskExecutionResource> retrieveTasksByName(@PathVariable("taskName") String taskName, Pageable pageable,
															 PagedResourcesAssembler<TaskExecution> assembler) {
		Page<TaskExecution> result =
				repository.getTaskExplorer().findTaskExecutionsByName(taskName, pageable);
		return assembler.toResource(result, taskAssembler);
	}

	/**
	 * {@link org.springframework.hateoas.ResourceAssembler} implementation
	 * that converts {@link TaskExecution}s to {@link TaskExecutionResource}s.
	 */
	class Assembler extends ResourceAssemblerSupport<TaskExecution, TaskExecutionResource> {

		public Assembler() {
			super(TaskExecutionController.class, TaskExecutionResource.class);
		}

		@Override
		public TaskExecutionResource toResource(TaskExecution taskExecution) {
			return createResourceWithId(taskExecution.getExecutionId(), taskExecution);
		}

		@Override
		public TaskExecutionResource instantiateResource(TaskExecution taskExecution) {
			TaskExecutionResource taskExecutionResource = new TaskExecutionResource(
					taskExecution.getExecutionId(), taskExecution.getExitCode(),
					taskExecution.getTaskName(), taskExecution.getStartTime(),
					taskExecution.getEndTime(),taskExecution.getStatusCode(),
					taskExecution.getExitMessage(), taskExecution.getParameters(),
					taskExecution.getExternalExecutionID());
			return taskExecutionResource;
		}
	}
}
