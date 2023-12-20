/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.cloud.dataflow.server.batch;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.dao.JdbcStepExecutionDao;
import org.springframework.batch.core.repository.dao.StepExecutionDao;
import org.testcontainers.containers.JdbcDatabaseContainer;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.NoSuchJobInstanceException;
import org.springframework.batch.item.database.support.DataFieldMaxValueIncrementerFactory;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.dataflow.core.database.support.DatabaseType;
import org.springframework.cloud.dataflow.core.database.support.MultiSchemaIncrementerFactory;
import org.springframework.cloud.dataflow.core.database.support.MultiSchemaTaskExecutionDaoFactoryBean;
import org.springframework.cloud.dataflow.schema.AppBootSchemaVersion;
import org.springframework.cloud.dataflow.schema.SchemaVersionTarget;
import org.springframework.cloud.dataflow.schema.service.SchemaService;
import org.springframework.cloud.dataflow.schema.service.impl.DefaultSchemaService;
import org.springframework.cloud.dataflow.server.repository.JobRepositoryContainer;
import org.springframework.cloud.dataflow.server.service.JobExplorerContainer;
import org.springframework.cloud.dataflow.server.service.JobServiceContainer;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.cloud.task.repository.support.SimpleTaskRepository;
import org.springframework.cloud.task.repository.support.TaskExecutionDaoFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;


public abstract class AbstractSimpleJobServiceTests extends AbstractDaoTests {

	private static final String SAVE_JOB_EXECUTION = "INSERT INTO %PREFIX%JOB_EXECUTION(JOB_EXECUTION_ID, " +
		"JOB_INSTANCE_ID, START_TIME, END_TIME, STATUS, EXIT_CODE, EXIT_MESSAGE, VERSION, CREATE_TIME, LAST_UPDATED) " +
		"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private static final String BASE_JOB_INST_NAME = "JOB_INST_";




	private final Map<AppBootSchemaVersion, JdbcSearchableJobInstanceDao> jdbcSearchableJobInstanceDaoContainer = new HashMap<>();
	private final Map<AppBootSchemaVersion,JdbcStepExecutionDao> stepExecutionDaoContainer = new HashMap<>();

	private DataFieldMaxValueIncrementerFactory incrementerFactory;

	@Autowired
	private JobServiceContainer jobServiceContainer;

	private DatabaseType databaseType;

	private final Map<AppBootSchemaVersion,TaskRepository> taskRepositoryContainer = new HashMap<>();

	@Autowired
	private SchemaService schemaService;
	protected void prepareForTest(JdbcDatabaseContainer<?> dbContainer, String schemaName, DatabaseType databaseType) throws Exception {
		this.databaseType = databaseType;
		super.prepareForTest(dbContainer, schemaName);
		for(SchemaVersionTarget schemaVersionTarget : schemaService.getTargets().getSchemas()) {
			JdbcSearchableJobInstanceDao jdbcSearchableJobInstanceDao = new JdbcSearchableJobInstanceDao();
			jdbcSearchableJobInstanceDao.setJdbcTemplate(getJdbcTemplate());
			jdbcSearchableJobInstanceDao.setTablePrefix(schemaVersionTarget.getBatchPrefix());
			incrementerFactory = new MultiSchemaIncrementerFactory(getDataSource());
			jdbcSearchableJobInstanceDao.setJobIncrementer(incrementerFactory.getIncrementer(databaseType.name(), schemaVersionTarget.getBatchPrefix() + "JOB_SEQ"));
			this.jdbcSearchableJobInstanceDaoContainer.put(schemaVersionTarget.getSchemaVersion(), jdbcSearchableJobInstanceDao);
			JdbcStepExecutionDao stepExecutionDao = new JdbcStepExecutionDao();
			stepExecutionDao.setJdbcTemplate(getJdbcTemplate());
			stepExecutionDao.setTablePrefix(schemaVersionTarget.getBatchPrefix());
			stepExecutionDao.setStepExecutionIncrementer(incrementerFactory.getIncrementer(databaseType.name(), schemaVersionTarget.getBatchPrefix() + "STEP_EXECUTION_SEQ"));
			stepExecutionDaoContainer.put(schemaVersionTarget.getSchemaVersion(), stepExecutionDao);
			TaskExecutionDaoFactoryBean teFactory = new MultiSchemaTaskExecutionDaoFactoryBean(getDataSource(), schemaVersionTarget.getTaskPrefix());
			TaskRepository taskRepository =  new SimpleTaskRepository(teFactory);
			taskRepositoryContainer.put(schemaVersionTarget.getSchemaVersion(), taskRepository);
		}
	}

	@Test
	void getJobInstancesThatExist() throws Exception {
		createJobInstance(BASE_JOB_INST_NAME+"BOOT2", AppBootSchemaVersion.BOOT2);
		createJobInstance(BASE_JOB_INST_NAME+"BOOT3", AppBootSchemaVersion.BOOT3);
		verifyJobInstance(1, "boot2", BASE_JOB_INST_NAME+"BOOT2");
		verifyJobInstance(1, "boot3", BASE_JOB_INST_NAME+"BOOT3");
	}

	@Test
	void getJobExecutionsThatExist() throws Exception {
		createJobExecution(BASE_JOB_INST_NAME+"BOOT2", AppBootSchemaVersion.BOOT2);
		verifyJobExecution(1, "boot2", BASE_JOB_INST_NAME+"BOOT2");
		createJobExecution(BASE_JOB_INST_NAME+"BOOT3", AppBootSchemaVersion.BOOT3);
		createJobExecution(BASE_JOB_INST_NAME+"BOOT3A", AppBootSchemaVersion.BOOT3);
		verifyJobExecution(2, "boot3", BASE_JOB_INST_NAME+"BOOT3A");
	}

	@Test
	void exceptionsShouldBeThrownIfRequestForNonExistingJobInstance() {
		assertThatThrownBy(() -> {
			this.jobServiceContainer.get("boot2").getJobInstance(1);
		}).isInstanceOf(NoSuchJobInstanceException.class)
			.hasMessageContaining("JobInstance with id=1 does not exist");
		assertThatThrownBy(() -> {
			this.jobServiceContainer.get("boot3").getJobInstance(1);
		}).isInstanceOf(NoSuchJobInstanceException.class)
			.hasMessageContaining("JobInstance with id=1 does not exist");
	}

	@Test
	void stoppingJobExecutionShouldLeaveJobExecutionWithStatusOfStopping() throws Exception{
		JobExecution jobExecution = createJobExecution(BASE_JOB_INST_NAME+"BOOT3", AppBootSchemaVersion.BOOT3, true);
		jobExecution = this.jobServiceContainer.get("boot3").getJobExecution(jobExecution.getId());
		assertThat(jobExecution.isRunning()).isTrue();
		assertThat(jobExecution.getStatus()).isNotEqualTo(BatchStatus.STOPPING);
		this.jobServiceContainer.get("boot3").stop(jobExecution.getId());
		jobExecution = this.jobServiceContainer.get("boot3").getJobExecution(jobExecution.getId());
		assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.STOPPING);

		jobExecution = createJobExecution(BASE_JOB_INST_NAME+"BOOT2", AppBootSchemaVersion.BOOT2, true);
		jobExecution = this.jobServiceContainer.get("boot2").getJobExecution(jobExecution.getId());
		assertThat(jobExecution.isRunning()).isTrue();
		assertThat(jobExecution.getStatus()).isNotEqualTo(BatchStatus.STOPPING);
		this.jobServiceContainer.get("boot2").stop(jobExecution.getId());
		jobExecution = this.jobServiceContainer.get("boot2").getJobExecution(jobExecution.getId());
		assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.STOPPING);
	}

	private void verifyJobInstance(long id, String schemaTarget, String name) throws Exception {
		JobInstance jobInstance = this.jobServiceContainer.get(schemaTarget).getJobInstance(id);
		assertThat(jobInstance).isNotNull();
		assertThat(jobInstance.getJobName()).isEqualTo(name);
	}

	private void verifyJobExecution(long id, String schemaTarget, String name) throws Exception {
		JobExecution jobExecution = this.jobServiceContainer.get(schemaTarget).getJobExecution(id);
		assertThat(jobExecution).isNotNull();
		assertThat(jobExecution.getId()).isEqualTo(id);
		assertThat(jobExecution.getJobInstance().getJobName()).isEqualTo(name);
	}

	private JobInstance createJobInstance(String name, AppBootSchemaVersion appBootSchemaVersion) throws Exception {
		JdbcSearchableJobInstanceDao jdbcSearchableJobInstanceDao = this.jdbcSearchableJobInstanceDaoContainer.get(appBootSchemaVersion);
		assertThat(jdbcSearchableJobInstanceDao).isNotNull();

		return jdbcSearchableJobInstanceDao.createJobInstance(name, new JobParameters());
	}
	private JobExecution createJobExecution(String name, AppBootSchemaVersion appBootSchemaVersion) throws Exception{
		return createJobExecution(name, BatchStatus.STARTING, appBootSchemaVersion,false);
	}
	private JobExecution createJobExecution(String name, AppBootSchemaVersion appBootSchemaVersion, boolean isRunning) throws Exception {
		return createJobExecution(name, BatchStatus.STARTING, appBootSchemaVersion, isRunning);
	}
	private JobExecution createJobExecution(String name, BatchStatus batchStatus, AppBootSchemaVersion appBootSchemaVersion, boolean isRunning) throws Exception{
		return createJobExecutions(name, batchStatus, appBootSchemaVersion, isRunning, 1).stream().findFirst().orElse(null);
	}
	private List<JobExecution> createJobExecutions(String name, BatchStatus batchStatus, AppBootSchemaVersion appBootSchemaVersion, boolean isRunning, int numberOfJobs)
		throws Exception {
		SchemaVersionTarget schemaVersionTarget = schemaService.getTargets().getSchemas().stream().filter(svt -> svt.getSchemaVersion().equals(appBootSchemaVersion)).findFirst().orElseThrow(() -> new RuntimeException("Cannot find SchemaTarget for " + appBootSchemaVersion));
		String prefix = schemaVersionTarget.getBatchPrefix();
		StepExecutionDao stepExecutionDao = this.stepExecutionDaoContainer.get(appBootSchemaVersion);
		assertThat(stepExecutionDao).isNotNull();
		List<JobExecution> result = new ArrayList<>();
		JobInstance jobInstance = createJobInstance(name, appBootSchemaVersion);
		DataFieldMaxValueIncrementer jobExecutionIncrementer = incrementerFactory.getIncrementer(databaseType.name(),
			prefix+ "JOB_EXECUTION_SEQ");
		for(int i = 0; i < numberOfJobs;i++) {
			JobExecution jobExecution = new JobExecution(jobInstance, null, "foo");
			result.add(jobExecution);
			jobExecution.setId(jobExecutionIncrementer.nextLongValue());
			jobExecution.setStartTime(new Date());
			if (!isRunning) {
				jobExecution.setEndTime(new Date());
			}
			jobExecution.setVersion(3);
			Timestamp startTime = jobExecution.getStartTime() == null ? null : Timestamp.valueOf(jobExecution.getStartTime().toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDateTime());
			Timestamp endTime = jobExecution.getEndTime() == null ? null : Timestamp.valueOf(jobExecution.getEndTime().toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDateTime());
			Timestamp createTime = jobExecution.getCreateTime() == null ? null : Timestamp.valueOf(jobExecution.getCreateTime().toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDateTime());
			Timestamp lastUpdated = jobExecution.getLastUpdated() == null ? null : Timestamp.valueOf(jobExecution.getLastUpdated().toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDateTime());
			Object[] parameters = new Object[]{
				jobExecution.getId(),
				jobExecution.getJobId(),
				startTime,
				endTime,
				batchStatus,
				jobExecution.getExitStatus().getExitCode(),
				jobExecution.getExitStatus().getExitDescription(),
				jobExecution.getVersion(),
				createTime,
				lastUpdated
			};
			getJdbcTemplate().update(getQuery(SAVE_JOB_EXECUTION, appBootSchemaVersion), parameters,
				new int[]{Types.BIGINT, Types.BIGINT, Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR,
					Types.VARCHAR, Types.INTEGER, Types.TIMESTAMP, Types.TIMESTAMP});
			// TODO remove if when save of Batch 5 version can be saved.
			if(appBootSchemaVersion.equals(AppBootSchemaVersion.BOOT2)) {
				StepExecution stepExecution = new StepExecution("StepOne", jobExecution);
				// TODO we need a save step sql that works across both.
				stepExecutionDao.saveStepExecution(stepExecution);
				stepExecution = new StepExecution("StepTwo", jobExecution);
				stepExecutionDao.saveStepExecution(stepExecution);
				stepExecution = new StepExecution("StepThree", jobExecution);
				stepExecutionDao.saveStepExecution(stepExecution);
			}
			createTaskExecution(appBootSchemaVersion, jobExecution);
		}
		return result;
	}

	private TaskExecution createTaskExecution(AppBootSchemaVersion appBootSchemaVersion, JobExecution jobExecution) {
		SchemaVersionTarget schemaVersionTarget = schemaService.getTargets().getSchemas().stream().filter(svt -> svt.getSchemaVersion().equals(appBootSchemaVersion)).findFirst().orElseThrow(() -> new RuntimeException("Cannot find SchemaTarget for " + appBootSchemaVersion));

		String taskPrefix = schemaVersionTarget.getTaskPrefix();
		TaskRepository taskRepository = taskRepositoryContainer.get(appBootSchemaVersion);

		TaskExecution taskExecution = new TaskExecution();
		taskExecution.setStartTime(new Date());
		taskExecution = taskRepository.createTaskExecution(taskExecution);
		getJdbcTemplate().execute("INSERT INTO " + taskPrefix + "TASK_BATCH (TASK_EXECUTION_ID, JOB_EXECUTION_ID) VALUES (" +
			taskExecution.getExecutionId() + ", " + jobExecution.getJobId() + ")");
		return taskExecution;
	}

	private String getQuery(String base, AppBootSchemaVersion appBootSchemaVersion) {
		SchemaVersionTarget schemaVersionTarget = schemaService.getTargets().getSchemas().stream().filter(svt -> svt.getSchemaVersion().equals(appBootSchemaVersion)).findFirst().orElseThrow(() -> new RuntimeException("Cannot find SchemaTarget for " + appBootSchemaVersion));
		String tablePrefix = schemaVersionTarget.getBatchPrefix();
		return StringUtils.replace(base, "%PREFIX%", tablePrefix);
	}

	protected static class SimpleJobTestConfiguration {

		@Bean
		public JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		public PlatformTransactionManager platformTransactionManager() {
			return new ResourcelessTransactionManager();
		}

		@Bean
		public SchemaService schemaService() {
			return new DefaultSchemaService();
		}

		@Bean
		public JobRepositoryContainer jobRepositoryContainer(DataSource dataSource, PlatformTransactionManager transactionManager,
										 SchemaService schemaService) {
			return new JobRepositoryContainer(dataSource, transactionManager, schemaService);
		}

		@Bean
		public JobExplorerContainer jobExplorerContainer(DataSource dataSource, SchemaService schemaService) {
			return new JobExplorerContainer(dataSource, schemaService);
		}

		@Bean
		public JobServiceContainer jobServiceContainer(DataSource dataSource,
				PlatformTransactionManager platformTransactionManager,
				SchemaService schemaService,
				JobRepositoryContainer jobRepositoryContainer,
				JobExplorerContainer jobExplorerContainer,
				Environment environment) {
			return new JobServiceContainer(dataSource, platformTransactionManager, schemaService, jobRepositoryContainer,
					jobExplorerContainer, environment);
		}
	}
}
