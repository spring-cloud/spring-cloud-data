/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.cloud.dataflow.core;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.data.annotation.Id;
import org.springframework.data.keyvalue.annotation.KeySpace;

/**
 * @author Mark Pollack
 */
@KeySpace("launcher")
public class Launcher {

	@Id
	private String id;

	private String name;

	private String type;

	private String description;

	@JsonIgnore
	private TaskLauncher taskLauncher;

	Launcher() {
	}

	public Launcher(String name, String type, TaskLauncher taskLauncher) {
		this.name = name;
		this.type = type;
		this.taskLauncher = taskLauncher;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TaskLauncher getTaskLauncher() {
		return taskLauncher;
	}

	public void setTaskLauncher(TaskLauncher taskLauncher) {
		this.taskLauncher = taskLauncher;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
