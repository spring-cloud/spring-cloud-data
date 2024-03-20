/*
 * Copyright 2024 the original author or authors.
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

import org.springframework.cloud.dataflow.aggregate.task.AggregateTaskExplorer;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionThinResource;
import org.springframework.cloud.dataflow.schema.AggregateTaskExecution;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * This controller provides for retrieving a thin task execution resource that will satisfy UI paging with embedded links to more detail.
 * @author Corneil du Plessis
 */
@RestController
@RequestMapping("/tasks/thinexecutions")
@ExposesResourceFor(TaskExecutionThinResource.class)
public class TaskExecutionThinController {

	private final AggregateTaskExplorer explorer;
	private final TaskExecutionThinResourceAssembler resourceAssembler;

	public TaskExecutionThinController(AggregateTaskExplorer explorer) {
		this.explorer = explorer;
		this.resourceAssembler = new TaskExecutionThinResourceAssembler();
	}

	@GetMapping(produces = "application/json")
	@ResponseStatus(HttpStatus.OK)
	public PagedModel<TaskExecutionThinResource> listTasks(Pageable pageable, PagedResourcesAssembler<AggregateTaskExecution> pagedAssembler) {
		return pagedAssembler.toModel(explorer.findAll(pageable), resourceAssembler);
	}

	static class TaskExecutionThinResourceAssembler extends RepresentationModelAssemblerSupport<AggregateTaskExecution, TaskExecutionThinResource> {
		public TaskExecutionThinResourceAssembler() {
			super(TaskExecutionThinController.class, TaskExecutionThinResource.class);
		}
		@Override
		public TaskExecutionThinResource toModel(AggregateTaskExecution entity) {
			TaskExecutionThinResource resource = new TaskExecutionThinResource(entity);
			resource.add(linkTo(methodOn(TaskExecutionController.class).view(resource.getExecutionId(), resource.getSchemaTarget())).withSelfRel());
			resource.add(linkTo(methodOn(TaskDefinitionController.class).display(resource.getTaskName(), true)).withRel("tasks/definitions"));
			return resource;
		}
	}
}
