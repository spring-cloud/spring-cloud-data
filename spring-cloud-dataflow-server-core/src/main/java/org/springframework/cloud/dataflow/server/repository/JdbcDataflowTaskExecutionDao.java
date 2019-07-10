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

package org.springframework.cloud.dataflow.server.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import javax.sql.DataSource;

import org.springframework.cloud.task.repository.dao.JdbcTaskExecutionDao;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Manages Task Execution information using a JDBC DataSource. Mirrors the {@link JdbcTaskExecutionDao}
 * but contains Spring Cloud Data Flow specific operations. This functionality might
 * be migrated to Spring Cloud Task itself eventually.
 *
 * @author Gunnar Hillert
 */
public class JdbcDataflowTaskExecutionDao implements DataflowTaskExecutionDao {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	private final String tablePrefix;

	private static final String DELETE_TASK_EXECUTIONS = "DELETE FROM %PREFIX%EXECUTION "
			+ "WHERE task_execution_id in (:taskExecutionIds)";

	private static final String DELETE_TASK_EXECUTION_PARAMS = "DELETE FROM %PREFIX%EXECUTION_PARAMS "
			+ "WHERE task_execution_id in (:taskExecutionIds)";

	private static final String DELETE_TASK_TASK_BATCH = "DELETE FROM %PREFIX%TASK_BATCH "
			+ "WHERE task_execution_id in (:taskExecutionIds)";

	private static final String SELECT_CHILD_TASK_EXECUTION_IDS = "SELECT task_execution_id FROM %PREFIX%EXECUTION "
			+ "WHERE parent_execution_id in (:parentTaskExecutionIds)";

	/**
	 * Initializes the JdbcTaskExecutionDao.
	 *
	 * @param dataSource used by the dao to execute queries and updates the tables. Must not be null.
	 * @param tablePrefix Must not be null or empty.
	 */
	public JdbcDataflowTaskExecutionDao(DataSource dataSource, String tablePrefix) {
		Assert.hasText(tablePrefix, "tablePrefix must not be null nor empty.");
		Assert.notNull(dataSource, "The dataSource must not be null.");
		this.tablePrefix = tablePrefix;
		this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
	}

	@Override
	public int deleteTaskExecutionsByTaskExecutionIds(Set<Long> taskExecutionIds) {
		final MapSqlParameterSource queryParameters = new MapSqlParameterSource()
				.addValue("taskExecutionIds", taskExecutionIds);
		final String query = getQuery(DELETE_TASK_EXECUTIONS);
		return this.jdbcTemplate.update(query, queryParameters);
	}

	@Override
	public int deleteTaskExecutionParamsByTaskExecutionIds(Set<Long> taskExecutionIds) {
		final MapSqlParameterSource queryParameters = new MapSqlParameterSource()
				.addValue("taskExecutionIds", taskExecutionIds);
		final String query = getQuery(DELETE_TASK_EXECUTION_PARAMS);
		return this.jdbcTemplate.update(query, queryParameters);
	}

	@Override
	public int deleteTaskTaskBatchRelationshipsByTaskExecutionIds(Set<Long> taskExecutionIds) {
		final MapSqlParameterSource queryParameters = new MapSqlParameterSource()
				.addValue("taskExecutionIds", taskExecutionIds);
		final String query = getQuery(DELETE_TASK_TASK_BATCH);
		return this.jdbcTemplate.update(query, queryParameters);
	}

	private String getQuery(String base) {
		return StringUtils.replace(base, "%PREFIX%", this.tablePrefix);
	}

	@Override
	public Set<Long> findChildTaskExecutionIds(Set<Long> taskExecutionIds) {
		final MapSqlParameterSource queryParameters = new MapSqlParameterSource()
				.addValue("parentTaskExecutionIds", taskExecutionIds);

		Set<Long> childTaskExecutionIds;
		try {
			childTaskExecutionIds = this.jdbcTemplate.query(
					getQuery(SELECT_CHILD_TASK_EXECUTION_IDS), queryParameters,
					new ResultSetExtractor<Set<Long>>() {
						@Override
						public Set<Long> extractData(ResultSet resultSet)
								throws SQLException, DataAccessException {
							final Set<Long> taskExecutionIds = new TreeSet<>();

							while (resultSet.next()) {
								taskExecutionIds
										.add(resultSet.getLong("TASK_EXECUTION_ID"));
							}

							return taskExecutionIds;
						}
					});
		}
		catch (DataAccessException e) {
			childTaskExecutionIds = Collections.emptySet();
		}

		if (!childTaskExecutionIds.isEmpty()) {
			childTaskExecutionIds.addAll(this.findChildTaskExecutionIds(childTaskExecutionIds));
		}

		return childTaskExecutionIds;
	}
}
