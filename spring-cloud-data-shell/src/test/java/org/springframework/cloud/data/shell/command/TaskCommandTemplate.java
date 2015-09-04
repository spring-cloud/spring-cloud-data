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

package org.springframework.cloud.data.shell.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.springframework.shell.core.CommandResult;
import org.springframework.shell.core.JLineShellComponent;
import org.springframework.shell.support.table.Table;
import org.springframework.shell.support.table.TableRow;

/**
 * Helper methods for task commands to execute in the shell.
 * <p/>
 * It should mimic the client side API of TaskOperations as much as possible.
 *
 * @author Glenn Renfro
 */
public class TaskCommandTemplate {

	private final JLineShellComponent shell;

	private List<String> tasks = new ArrayList<String>();

	/**
	 * Construct a new TaskCommandTemplate, given a spring shell.
	 *
	 * @param shell the spring shell to execute commands against
	 */
	public TaskCommandTemplate(JLineShellComponent shell) {
		this.shell = shell;
	}

	/**
	 * Create a task.
	 *
	 * Note the name of the task will be stored so that when the method destroyCreatedTasks is called, the task
	 * will be destroyed.
	 *
	 * @param taskName the name of the task
	 * @param taskDefinition the task definition DSL
	 * @param values will be injected into taskdefinition according to {@link String#format(String, Object...)} syntax
	 */
	public void create(String taskName, String taskDefinition, Object... values) {
		doCreate(taskName, taskDefinition, true, values);
	}


	private void doCreate(String taskName, String taskDefinition, Object... values) {
		String actualDefinition = String.format(taskDefinition, values);
		// Shell parser expects quotes to be escaped by \
		String wholeCommand = String.format("task create %s --definition \"%s\"", taskName,
				actualDefinition.replaceAll("\"", "\\\\\""));
		CommandResult cr = shell.executeCommand(wholeCommand);
		//todo: Add launch and verifier

		// add the task name to the tasks list before assertion
		tasks.add(taskName);
		String createMsg = "Created";

		assertEquals(createMsg + " new task '" + taskName + "'", cr.getResult());

		verifyExists(taskName, actualDefinition);
	}

	/**
	 * Destroy all tasks that were created using the 'create' method. Commonly called in a @After annotated method.
	 */
	public void destroyCreatedTasks() {
		for (int s = tasks.size() - 1; s >= 0; s--) {
			String taskname = tasks.get(s);
			CommandResult cr = shell.executeCommand("task destroy --name " + taskname);
			//stateVerifier.waitForDestroy(taskname);
			assertTrue("Failure to destroy task " + taskname + ".  CommandResult = " + cr.toString(),
					cr.isSuccess());
		}
	}

	/**
	 * Destroy a specific task name.
	 *
	 * @param task The task to destroy
	 */
	public void destroyTask(String task) {
		CommandResult cr = shell.executeCommand("task destroy --name " + task);
		//stateVerifier.waitForDestroy(task);
		assertTrue("Failure to destroy task " + task + ".  CommandResult = " + cr.toString(),
				cr.isSuccess());
		tasks.remove(task);
	}

	/**
	 * Verify the task is listed in task list.
	 *
	 * @param taskName the name of the task
	 * @param definition definition of the task
	 */
	public void verifyExists(String taskName, String definition) {
		CommandResult cr = shell.executeCommand("task list");
		assertTrue("Failure.  CommandResult = " + cr.toString(), cr.isSuccess());
		Table t = (Table) cr.getResult();
		assertTrue(t.getRows().contains(
				new TableRow().addValue(1, taskName).
						addValue(2, definition.replace("\\\\", "\\")).addValue(
						3, "unknown")));
	}

}
