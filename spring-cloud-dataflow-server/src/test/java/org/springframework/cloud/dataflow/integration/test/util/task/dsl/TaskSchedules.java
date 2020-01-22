/*
 * Copyright 2020 the original author or authors.
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

package org.springframework.cloud.dataflow.integration.test.util.task.dsl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cloud.dataflow.rest.client.SchedulerOperations;
import org.springframework.cloud.dataflow.rest.resource.ScheduleInfoResource;

/**
 * @author Christian Tzolov
 */
public class TaskSchedules {
	private final SchedulerOperations schedulerOperations;
	private Tasks tasks;

	public TaskSchedules(SchedulerOperations schedulerOperations, Tasks tasks) {
		this.schedulerOperations = schedulerOperations;
		this.tasks = tasks;
	}

	public TaskScheduleBuilder builder() {
		return new TaskScheduleBuilder(this.schedulerOperations);
	}

	public TaskSchedule findByScheduleName(String scheduleName) {
		if (isScheduled(scheduleName)) {
			ScheduleInfoResource s = this.schedulerOperations.getSchedule(scheduleName);
			Task task = this.tasks.get(s.getTaskDefinitionName());
			String prefix = scheduleName.replace("-scdf-" + task.getTaskName(), "");
			return new TaskSchedule(prefix, task, schedulerOperations);
		}
		return null;
	}

	public List<TaskSchedule> list(Task task) {
		return this.schedulerOperations.list(task.getTaskName()).getContent().stream()
				.map(s -> {
					String prefix = s.getScheduleName().replace("-scdf-" + task.getTaskName(), "");
					return new TaskSchedule(prefix, task, schedulerOperations);
				}).collect(Collectors.toList());
	}

	public List<TaskSchedule> list() {
		return this.schedulerOperations.list().getContent().stream()
				.map(s -> {
					Task task = this.tasks.get(s.getTaskDefinitionName());
					String prefix = s.getScheduleName().replace("-scdf-" + task.getTaskName(), "");
					return new TaskSchedule(prefix, task, schedulerOperations);
				}).collect(Collectors.toList());
	}

	private boolean isScheduled(String scheduleName) {
		return this.schedulerOperations.list().getContent().stream()
				.anyMatch(sr -> sr.getScheduleName().equals(scheduleName));
	}
}
