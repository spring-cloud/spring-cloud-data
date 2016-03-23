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

package org.springframework.cloud.dataflow.server.configuration;

import static org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType.HAL;

import javax.sql.DataSource;

import org.springframework.batch.admin.service.JobService;
import org.springframework.batch.admin.service.SimpleJobServiceFactoryBean;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.boot.autoconfigure.batch.BatchDatabaseInitializer;
import org.springframework.cloud.dataflow.server.controller.JobExecutionController;
import org.springframework.cloud.dataflow.server.controller.JobInstanceController;
import org.springframework.cloud.dataflow.server.controller.JobStepExecutionController;
import org.springframework.cloud.dataflow.server.controller.JobStepExecutionProgressController;
import org.springframework.cloud.dataflow.server.controller.RestControllerAdvice;
import org.springframework.cloud.dataflow.server.controller.TaskExecutionController;
import org.springframework.cloud.dataflow.server.job.TaskJobRepository;
import org.springframework.cloud.task.batch.listener.TaskBatchDao;
import org.springframework.cloud.task.batch.listener.support.JdbcTaskBatchDao;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.support.SimpleTaskExplorer;
import org.springframework.cloud.task.repository.support.TaskExecutionDaoFactoryBean;
import org.springframework.cloud.task.repository.support.TaskRepositoryInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * @author Glenn Renfro
 */
@Configuration
@EnableSpringDataWebSupport
@EnableHypermediaSupport(type = HAL)
@EnableWebMvc
public class JobDependencies {

	@Bean
	public JobExecutionController jobExecutionController(TaskJobRepository repository) {
		return new JobExecutionController(repository);
	}

	@Bean
	public JobStepExecutionController jobStepExecutionController(JobService jobService) {
		return new JobStepExecutionController(jobService);
	}
	@Bean
	public JobStepExecutionProgressController jobStepExecutionProgressController(JobService jobService) {
		return new JobStepExecutionProgressController(jobService);
	}

	@Bean
	public JobInstanceController jobInstanceController(TaskJobRepository repository) {
		return new JobInstanceController(repository);
	}
	@Bean
	public TaskExecutionController taskExecutionController(TaskExplorer explorer) {
		return new TaskExecutionController(explorer);
	}

	@Bean
	public TaskRepositoryInitializer taskExecutionRepository(DataSource dataSource) {
		TaskRepositoryInitializer taskRepositoryInitializer = new TaskRepositoryInitializer();
		taskRepositoryInitializer.setDataSource(dataSource);
		return taskRepositoryInitializer;
	}

	@Bean
	public TaskJobRepository taskJobExecutionRepository(JobService jobService, TaskExplorer taskExplorer){
		return new TaskJobRepository(jobService, taskExplorer);
	}

	@Bean
	public TaskBatchDao taskBatchDao(DataSource dataSource){
		return new JdbcTaskBatchDao(dataSource);
	}

	@Bean
	public SimpleJobServiceFactoryBean simpleJobServiceFactoryBean(DataSource dataSource,
			JobRepositoryFactoryBean repositoryFactoryBean) {
		SimpleJobServiceFactoryBean factoryBean = new SimpleJobServiceFactoryBean();
		factoryBean.setDataSource(dataSource);
		try {
			factoryBean.setJobRepository(repositoryFactoryBean.getObject());
			factoryBean.setJobLocator(new MapJobRegistry());
			factoryBean.setJobLauncher(new SimpleJobLauncher());
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return factoryBean;
	}

	@Bean
	public JobRepositoryFactoryBean jobRepositoryFactoryBeanForServer(DataSource dataSource, DataSourceTransactionManager dataSourceTransactionManager){
		JobRepositoryFactoryBean repositoryFactoryBean = new JobRepositoryFactoryBean();
		repositoryFactoryBean.setDataSource(dataSource);
		repositoryFactoryBean.setTransactionManager(dataSourceTransactionManager);
		return repositoryFactoryBean;
	}

	@Bean
	public DataSourceTransactionManager transactionManagerForServer(DataSource dataSource){
		return new DataSourceTransactionManager(dataSource);
	}

	@Bean
	public JobExplorerFactoryBean jobExplorerFactoryBeanForServer(DataSource dataSource){
		JobExplorerFactoryBean jobExplorerFactoryBean = new JobExplorerFactoryBean();
		jobExplorerFactoryBean.setDataSource(dataSource);
		return jobExplorerFactoryBean;
	}

	@Bean
	public BatchDatabaseInitializer batchRepositoryInitializerForDefaultDBForServer(DataSource dataSource) {
		return new BatchDatabaseInitializer();
	}

	@Bean
	public TaskExplorer taskExplorer(TaskExecutionDaoFactoryBean daoFactoryBean){
		return new SimpleTaskExplorer(daoFactoryBean);
	}

	@Bean
	public TaskExecutionDaoFactoryBean taskExecutionDaoFactoryBean(DataSource dataSource){
		return new TaskExecutionDaoFactoryBean(dataSource);
	}

	@Bean
	public RestControllerAdvice restControllerAdvice() {
		return new RestControllerAdvice();
	}

}
