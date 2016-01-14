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

package org.springframework.cloud.dataflow.admin.repository;

import static org.junit.Assert.assertEquals;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.EmbeddedDataSourceConfiguration;
import org.springframework.cloud.dataflow.admin.configuration.TaskDependencies;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Glenn Renfro
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {TaskDependencies.class,
		EmbeddedDataSourceConfiguration.class,
		PropertyPlaceholderAutoConfiguration.class})
public class TaskExecutionExplorerTests {
	@Autowired
	private DataSource dataSource;

	@Autowired
	private TaskExplorer explorer;

	private JdbcTemplate template;

	@Before
	public void setup() throws Exception{
			template = new JdbcTemplate(dataSource);
			template.execute("TRUNCATE TABLE task_execution");
	}

	@Test
	public void testInitializer() throws Exception{
		int actual = template.queryForObject("SELECT COUNT(*) from TASK_EXECUTION", Integer.class);
		assertEquals("expected 0 entries returned from task_execution", 0, actual);
		actual = template.queryForObject("SELECT COUNT(*) from TASK_EXECUTION_PARAMS", Integer.class);
		assertEquals("expected 0 entries returned from task_execution_params", 0, actual);
	}


	@Test
	public void testExplorerFindAll() throws Exception{
		final int ENTRY_COUNT = 4;
		insertTestExecutionDataIntoRepo(template, 3l, "foo");
		insertTestExecutionDataIntoRepo(template, 2l, "foo");
		insertTestExecutionDataIntoRepo(template, 1l, "foo");
		insertTestExecutionDataIntoRepo(template, 0l, "foo");

		List<TaskExecution> resultList = explorer.
				findAll(new PageRequest(0, 10)).getContent();
		assertEquals(String.format("expected %s entries returned from task_execution",
				ENTRY_COUNT), ENTRY_COUNT, resultList.size());
		Map<Long, TaskExecution> actual= new HashMap<Long, TaskExecution>();
		for(int executionId = 0; executionId < ENTRY_COUNT; executionId++){
			TaskExecution taskExecution = resultList.get(executionId);
			actual.put(taskExecution.getExecutionId(), taskExecution);
		}
		for(long executionId = 0; executionId < ENTRY_COUNT; executionId++){
			TaskExecution taskExecution = actual.get(executionId);
			assertEquals("expected execution id does not match actual", executionId,
					taskExecution.getExecutionId());
			assertEquals("expected taskName does not match actual", "foo",
					taskExecution.getTaskName());
		}

	}

	@Test
	public void testExplorerFindByName() throws Exception {
		insertTestExecutionDataIntoRepo(template, 3l, "foo");
		insertTestExecutionDataIntoRepo(template, 2l, "bar");
		insertTestExecutionDataIntoRepo(template, 1l, "baz");
		insertTestExecutionDataIntoRepo(template, 0l, "fee");

		List<TaskExecution> resultList = explorer.
				findTaskExecutionsByName("fee", new PageRequest(0, 10)).getContent();
		assertEquals("expected 1 entries returned from task_execution", 1, resultList.size());
		TaskExecution taskExecution = resultList.get(0);
		assertEquals("expected execution id does not match actual", 0,
				taskExecution.getExecutionId());
		assertEquals("expected taskName does not match actual", "fee",
				taskExecution.getTaskName());
	}

	private void insertTestExecutionDataIntoRepo(JdbcTemplate template,
												 long id, String taskName){
		final String INSERT_STATEMENT = "INSERT INTO task_execution (task_execution_id,"
				+ "task_external_execution_id, start_time, end_time, task_name, "
				+ "exit_code,exit_message,last_updated,status_code) "
				+ "VALUES (?,?,?,?,?,?,?,?,?)";
		Object[] param = new Object[] {id, null, new Date(id), new Date(), taskName, 0, null,
				new Date(), null};
		template.update(INSERT_STATEMENT, param);
	}

	private final class TaskExecutionRowMapper implements RowMapper<TaskExecution> {

		public TaskExecutionRowMapper() {
		}

		@Override
		public TaskExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
			long  id = rs.getLong("TASK_EXECUTION_ID");
			TaskExecution taskExecution=new TaskExecution(id,
					rs.getInt("EXIT_CODE"),
					rs.getString("TASK_NAME"),
					rs.getTimestamp("START_TIME"),
					rs.getTimestamp("END_TIME"),
					rs.getString("STATUS_CODE"),
					rs.getString("EXIT_MESSAGE"),
					new ArrayList<String>(),
					rs.getString("TASK_EXTERNAL_EXECUTION_ID"));
			return taskExecution;
		}
	}

}
