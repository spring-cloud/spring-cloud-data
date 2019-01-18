/*
 * Copyright 2015-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.audit.service.DefaultAuditRecordService;
import org.springframework.cloud.dataflow.completion.CompletionConfiguration;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.server.DockerValidatorProperties;
import org.springframework.cloud.dataflow.server.config.MetricsProperties;
import org.springframework.cloud.dataflow.server.config.VersionInfoProperties;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.config.features.FeaturesProperties;
import org.springframework.cloud.dataflow.server.job.LauncherRepository;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.SchedulerService;
import org.springframework.cloud.dataflow.server.service.SchedulerServiceProperties;
import org.springframework.cloud.dataflow.server.service.TaskDeleteService;
import org.springframework.cloud.dataflow.server.service.TaskExecutionInfoService;
import org.springframework.cloud.dataflow.server.service.TaskExecutionService;
import org.springframework.cloud.dataflow.server.service.TaskSaveService;
import org.springframework.cloud.dataflow.server.service.TaskValidationService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultSchedulerService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskDeleteService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskExecutionInfoService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskExecutionService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskSaveService;
import org.springframework.cloud.dataflow.server.service.impl.TaskConfigurationProperties;
import org.springframework.cloud.dataflow.server.service.impl.validation.DefaultTaskValidationService;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.scheduler.spi.core.CreateScheduleException;
import org.springframework.cloud.scheduler.spi.core.ScheduleInfo;
import org.springframework.cloud.scheduler.spi.core.ScheduleRequest;
import org.springframework.cloud.scheduler.spi.core.Scheduler;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.cloud.task.repository.support.SimpleTaskExplorer;
import org.springframework.cloud.task.repository.support.SimpleTaskRepository;
import org.springframework.cloud.task.repository.support.TaskExecutionDaoFactoryBean;
import org.springframework.cloud.task.repository.support.TaskRepositoryInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.map.repository.config.EnableMapRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Glenn Renfro
 * @author David Turanski
 * @author Gunnar Hillert
 */
@Configuration
@EnableSpringDataWebSupport
@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
@Import(CompletionConfiguration.class)
@ImportAutoConfiguration({ HibernateJpaAutoConfiguration.class, JacksonAutoConfiguration.class,
		FlywayAutoConfiguration.class })
@EnableWebMvc
@EnableConfigurationProperties({ CommonApplicationProperties.class,
		MetricsProperties.class,
		VersionInfoProperties.class,
		DockerValidatorProperties.class,
		TaskConfigurationProperties.class,
		DockerValidatorProperties.class })
@EntityScan({
		"org.springframework.cloud.dataflow.registry.domain",
		"org.springframework.cloud.dataflow.core"
})
@EnableMapRepositories("org.springframework.cloud.dataflow.server.job")
@EnableJpaRepositories(basePackages = {
		"org.springframework.cloud.dataflow.registry.repository",
		"org.springframework.cloud.dataflow.server.repository"
})
@EnableJpaAuditing
@EnableTransactionManagement
public class TaskServiceDependencies extends WebMvcConfigurationSupport {

	@Autowired
	DataSourceProperties dataSourceProperties;

	@Autowired
	TaskConfigurationProperties taskConfigurationProperties;

	@Autowired
	DockerValidatorProperties dockerValidatorProperties;

	@Bean
	public TaskRepositoryInitializer taskExecutionRepository(DataSource dataSource) {
		TaskRepositoryInitializer taskRepositoryInitializer = new TaskRepositoryInitializer();
		taskRepositoryInitializer.setDataSource(dataSource);
		return taskRepositoryInitializer;
	}

	@Bean
	public TaskValidationService taskValidationService(AppRegistryService appRegistry,
			DockerValidatorProperties dockerValidatorProperties, TaskDefinitionRepository taskDefinitionRepository,
			TaskConfigurationProperties taskConfigurationProperties) {
		return new DefaultTaskValidationService(appRegistry,
				dockerValidatorProperties,
				taskDefinitionRepository,
				taskConfigurationProperties.getComposedTaskRunnerName());
	}

	@Bean
	public TaskRepository taskRepository(TaskExecutionDaoFactoryBean daoFactoryBean) {
		return new SimpleTaskRepository(daoFactoryBean);
	}

	@Bean
	public AuditRecordService auditRecordService() {
		return mock(DefaultAuditRecordService.class);
	}

	@Bean
	public TaskExplorer taskExplorer(TaskExecutionDaoFactoryBean daoFactoryBean) {
		return new SimpleTaskExplorer(daoFactoryBean);
	}

	@Bean
	public TaskExecutionDaoFactoryBean taskExecutionDaoFactoryBean(DataSource dataSource) {
		return new TaskExecutionDaoFactoryBean(dataSource);
	}

	@Bean
	public AppRegistryService appRegistry() {
		return mock(AppRegistryService.class);
	}

	@Bean
	public ResourceLoader resourceLoader() {
		ResourceLoader resourceLoader = mock(ResourceLoader.class);
		when(resourceLoader.getResource(anyString())).thenReturn(mock(Resource.class));
		return resourceLoader;
	}

	@Bean
	TaskLauncher taskLauncher() {
		return mock(TaskLauncher.class);
	}

	@Bean
	ApplicationConfigurationMetadataResolver metadataResolver() {
		return mock(ApplicationConfigurationMetadataResolver.class);
	}

	@Bean
	public FeaturesProperties featuresProperties() {
		return new FeaturesProperties();
	}

	@Bean
	public SchedulerServiceProperties schedulerServiceProperties() {
		return new SchedulerServiceProperties();
	}

	@Bean
	public TaskDeleteService deleteTaskService(TaskExplorer taskExplorer, LauncherRepository launcherRepository,
			TaskDefinitionRepository taskDefinitionRepository, AuditRecordService auditRecordService) {
		return new DefaultTaskDeleteService(taskExplorer, launcherRepository, taskDefinitionRepository,
				auditRecordService);
	}

	@Bean
	public TaskSaveService saveTaskService(TaskDefinitionRepository taskDefinitionRepository,
			AuditRecordService auditRecordService, AppRegistryService registry) {
		return new DefaultTaskSaveService(taskDefinitionRepository, auditRecordService, registry);
	}

	@Bean
	public TaskExecutionService defaultTaskService(LauncherRepository launcherRepository,
			ApplicationConfigurationMetadataResolver metadataResolver,
			AuditRecordService auditRecordService, CommonApplicationProperties commonApplicationProperties,
			TaskRepository taskRepository,
			TaskExecutionInfoService taskExecutionInfoService) {
		return new DefaultTaskExecutionService(
				launcherRepository, metadataResolver, auditRecordService,
				null, commonApplicationProperties,
				taskRepository,
				taskExecutionInfoService);
	}

	@Bean
	public TaskExecutionInfoService taskDefinitionRetriever(AppRegistryService registry,
			TaskRepository taskExecutionRepository, TaskExplorer taskExplorer,
			TaskDefinitionRepository taskDefinitionRepository,
			TaskConfigurationProperties taskConfigurationProperties) {
		return new DefaultTaskExecutionInfoService(this.dataSourceProperties, registry, taskExecutionRepository,
				taskExplorer,
				taskDefinitionRepository, taskConfigurationProperties);
	}

	@Bean
	public SchedulerService schedulerService(CommonApplicationProperties commonApplicationProperties,
			Scheduler scheduler, TaskDefinitionRepository taskDefinitionRepository,
			AppRegistryService registry, ResourceLoader resourceLoader,
			DataSourceProperties dataSourceProperties,
			ApplicationConfigurationMetadataResolver metaDataResolver,
			SchedulerServiceProperties schedulerServiceProperties,
			AuditRecordService auditRecordService) {
		return new DefaultSchedulerService(commonApplicationProperties,
				scheduler, taskDefinitionRepository,
				registry, resourceLoader,
				new TaskConfigurationProperties(),
				dataSourceProperties, null,
				metaDataResolver, schedulerServiceProperties, auditRecordService);
	}

	@Bean
	Scheduler scheduler() {
		return new SimpleTestScheduler();
	}

	public static class SimpleTestScheduler implements Scheduler {
		List<ScheduleInfo> schedules = new ArrayList<>();

		@Override
		public void schedule(ScheduleRequest scheduleRequest) {
			ScheduleInfo schedule = new ScheduleInfo();
			schedule.setScheduleName(scheduleRequest.getScheduleName());
			schedule.setScheduleProperties(scheduleRequest.getSchedulerProperties());
			schedule.setTaskDefinitionName(scheduleRequest.getDefinition().getName());
			List<ScheduleInfo> scheduleInfos = schedules.stream()
					.filter(s -> s.getScheduleName().equals(scheduleRequest.getScheduleName()))
					.collect(Collectors.toList());
			if (scheduleInfos.size() > 0) {
				throw new CreateScheduleException(
						String.format("Schedule %s already exists",
								scheduleRequest.getScheduleName()),
						null);
			}
			schedules.add(schedule);

		}

		@Override
		public void unschedule(String scheduleName) {
			schedules = schedules.stream().filter(
					s -> !s.getScheduleName().equals(scheduleName)).collect(Collectors.toList());
		}

		@Override
		public List<ScheduleInfo> list(String taskDefinitionName) {
			return schedules.stream().filter(
					s -> s.getTaskDefinitionName().equals(taskDefinitionName)).collect(Collectors.toList());
		}

		@Override
		public List<ScheduleInfo> list() {
			return schedules;
		}

		public List<ScheduleInfo> getSchedules() {
			return schedules;
		}
	}
}
