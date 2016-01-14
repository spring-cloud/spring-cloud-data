/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.dataflow.shell.command;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.shell.AbstractShellIntegrationTest;

/**
 * @author Glenn Renfro
 */
public class TaskCommandTests extends AbstractShellIntegrationTest {

	private static final Logger logger = LoggerFactory.getLogger(TaskCommandTests.class);

	@Test
	public void testCreateTask() throws InterruptedException {
		logger.info("Create Task Test");
		String taskName = generateUniqueName();
		task().create(taskName, "foobar");
	}

	@Test
	public void testTaskExecutionList() throws InterruptedException {
		logger.info("Retrieve Task Execution List Test");
		task().taskExecutionList();
	}

	@Test
	public void testTaskExecutionListByName() throws InterruptedException {
		logger.info("Retrieve Task Execution List By Name Test");
		task().taskExecutionListByName();
	}
}
