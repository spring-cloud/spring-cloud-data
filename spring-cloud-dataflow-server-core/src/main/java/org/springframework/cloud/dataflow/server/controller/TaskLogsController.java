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

package org.springframework.cloud.dataflow.server.controller;

import org.springframework.cloud.dataflow.server.service.TaskExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieves logs of task applications.
 *
 * @author Ilayaperumal Gopinathan
 */
@RestController
@RequestMapping("/tasks/logs")
public class TaskLogsController {

	private final TaskExecutionService taskExecutionService;

	/**
	 * Construct Task logs controller.
	 *
	 * @param taskExecutionService the task execution service that this controller uses to get the logs of
	 * launched task applications.
	 */
	public TaskLogsController(TaskExecutionService taskExecutionService) {
		Assert.notNull(taskExecutionService, "TaskExecutionService must not be null");
		this.taskExecutionService = taskExecutionService;
	}

	@RequestMapping(value = "", method = RequestMethod.GET, params = "name")
	@ResponseStatus(HttpStatus.OK)
	public String getLog(@RequestParam(required = false, defaultValue = "default") String platformName,
			@RequestParam("name") String taskName) {
		return this.taskExecutionService.getLog(platformName, taskName);
	}
}
