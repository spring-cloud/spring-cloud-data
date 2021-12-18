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
package org.springframework.cloud.dataflow.shell.command;

import java.io.IOException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.springframework.cloud.dataflow.shell.AbstractShellIntegrationTest;

/**
 * @author Daniel Serleg
 */
public class TaskScheduleCommandsTest extends AbstractShellIntegrationTest {

	@BeforeClass
	public static void setUp() throws InterruptedException {
		Thread.sleep(2000);
	}

	@Test
	public void createScheduleWithProperties() {
		schedule().create("schedName", "def", "* * * * *", "", "");
	}

	@Test
	@Ignore
	public void createScheduleWithPropertiesFile() throws IOException {
		schedule().createWithPropertiesFile("schedName", "def", "* * * * *", "./src/test/resources/taskSchedulerWithPropertiesFile.properties", "");
	}

	@Test
	public void tryScheduleWithPropertiesAndPropertiesFile() throws IOException {
		Assert.assertThrows("You cannot specify both 'properties' and 'propertiesFile'", IllegalArgumentException.class,
				() -> schedule().createWithPropertiesAndPropertiesFile("schedName", "def", "* * * * *","app.tmp2.foo=bar",  "./src/test/resources/taskSchedulerWithPropertiesFile.properties", ""));
	}

	@Test
	public void unschedule() {
		schedule().unschedule("schedName");
	}

	@Test
	public void list() {
		schedule().list();
	}

	@Test
	public void listByTaskDefinition() {
		schedule().listByTaskDefinition("definition1");
	}
}
