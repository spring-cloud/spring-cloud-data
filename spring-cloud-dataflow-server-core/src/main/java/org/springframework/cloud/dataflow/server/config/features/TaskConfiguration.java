/*
 * Copyright 2016-2019 the original author or authors.
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
package org.springframework.cloud.dataflow.server.config.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.batch.BatchDataSourceInitializer;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.Launcher;
import org.springframework.cloud.dataflow.core.TaskPlatform;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.server.DockerValidatorProperties;
import org.springframework.cloud.dataflow.server.batch.JobService;
import org.springframework.cloud.dataflow.server.batch.SimpleJobServiceFactoryBean;
import org.springframework.cloud.dataflow.server.config.OnLocalPlatform;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.job.LauncherRepository;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.LauncherInitializationService;
import org.springframework.cloud.dataflow.server.service.TaskDeleteService;
import org.springframework.cloud.dataflow.server.service.TaskExecutionInfoService;
import org.springframework.cloud.dataflow.server.service.TaskExecutionService;
import org.springframework.cloud.dataflow.server.service.TaskJobService;
import org.springframework.cloud.dataflow.server.service.TaskSaveService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskDeleteService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskExecutionInfoService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskExecutionService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskJobService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskSaveService;
import org.springframework.cloud.dataflow.server.service.impl.TaskConfigurationProperties;
import org.springframework.cloud.deployer.spi.local.LocalDeployerProperties;
import org.springframework.cloud.deployer.spi.local.LocalTaskLauncher;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.cloud.task.repository.support.TaskRepositoryInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.map.repository.config.EnableMapRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.StringUtils;

/**
 * @author Thomas Risberg
 * @author Janne Valkealahti
 * @author Glenn Renfro
 * @author Michael Minella
 * @author Ilayaperumal Gopinathan
 * @author Gunnar Hillert
 * @author Christian Tzolov
 */
@Configuration
@ConditionalOnTasksEnabled
@EnableConfigurationProperties({ TaskConfigurationProperties.class, CommonApplicationProperties.class,
		DockerValidatorProperties.class, LocalPlatformProperties.class
})
@EnableMapRepositories(basePackages = "org.springframework.cloud.dataflow.server.job")
@EnableTransactionManagement
public class TaskConfiguration {

	@Autowired
	DataSourceProperties dataSourceProperties;

	@Value("${spring.cloud.dataflow.server.uri:}")
	private String dataflowServerUri;

	@Bean
	public LauncherInitializationService launcherInitializationService(
			LauncherRepository launcherRepository,
			List<TaskPlatform> platforms) {
		return new LauncherInitializationService(launcherRepository, platforms);
	}

	@Bean
	@Conditional(OnLocalPlatform.class)
	public TaskPlatform localTaskPlatform(LocalPlatformProperties localPlatformProperties) {
		List<Launcher> launchers = new ArrayList<>();
		Map<String, LocalDeployerProperties> localDeployerPropertiesMap = localPlatformProperties.getAccounts();
		for (Map.Entry<String, LocalDeployerProperties> entry : localDeployerPropertiesMap
				.entrySet()) {
			LocalTaskLauncher localTaskLauncher = new LocalTaskLauncher(entry.getValue());
			Launcher launcher = new Launcher(entry.getKey(), "local", localTaskLauncher);
			launcher.setDescription(prettyPrintLocalDeployerProperties(entry.getValue()));
			launchers.add(launcher);
		}
		return new TaskPlatform("Local", launchers);
	}

	private String prettyPrintLocalDeployerProperties(LocalDeployerProperties localDeployerProperties) {
		StringBuilder builder = new StringBuilder();
		if (localDeployerProperties.getJavaOpts() != null) {
			builder.append("JavaOpts = [" + localDeployerProperties.getJavaOpts() + "], ");
		}
		builder.append("ShutdownTimeout = [" + localDeployerProperties.getShutdownTimeout() + "], ");
		builder.append("EnvVarsToInherit = ["
				+ StringUtils.arrayToCommaDelimitedString(localDeployerProperties.getEnvVarsToInherit()) + "], ");
		builder.append("JavaCmd = [" + localDeployerProperties.getJavaCmd() + "], ");
		builder.append("WorkingDirectoriesRoot = [" + localDeployerProperties.getWorkingDirectoriesRoot() + "], ");
		builder.append("DeleteFilesOnExit = [" + localDeployerProperties.isDeleteFilesOnExit() + "]");
		return builder.toString();
	}

	@Bean
	public TaskExecutionInfoService taskDefinitionRetriever(AppRegistryService registry,
			TaskRepository taskExecutionRepository, TaskExplorer taskExplorer,
			TaskDefinitionRepository taskDefinitionRepository,
			TaskConfigurationProperties taskConfigurationProperties) {
		return new DefaultTaskExecutionInfoService(dataSourceProperties, registry, taskExecutionRepository,
				taskExplorer,
				taskDefinitionRepository, taskConfigurationProperties);
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
	public TaskExecutionService taskService(LauncherRepository launcherRepository,
			ApplicationConfigurationMetadataResolver metadataResolver,
			AuditRecordService auditRecordService, CommonApplicationProperties commonApplicationProperties,
			TaskRepository taskRepository,
			TaskExecutionInfoService taskExecutionInfoService) {
		return new DefaultTaskExecutionService(
				launcherRepository, metadataResolver, auditRecordService,
				dataflowServerUri, commonApplicationProperties,
				taskRepository,
				taskExecutionInfoService);
	}

	@Bean
	public TaskJobService taskJobExecutionRepository(JobService service, TaskExplorer taskExplorer,
			TaskDefinitionRepository taskDefinitionRepository, TaskExecutionService taskExecutionService) {
		return new DefaultTaskJobService(service, taskExplorer, taskDefinitionRepository, taskExecutionService);
	}

	@Bean
	public SimpleJobServiceFactoryBean simpleJobServiceFactoryBean(DataSource dataSource,
			JobRepositoryFactoryBean repositoryFactoryBean, JobExplorer jobExplorer,
			PlatformTransactionManager dataSourceTransactionManager) throws Exception {
		SimpleJobServiceFactoryBean factoryBean = new SimpleJobServiceFactoryBean();
		factoryBean.setDataSource(dataSource);
		factoryBean.setJobRepository(repositoryFactoryBean.getObject());
		factoryBean.setJobLocator(new MapJobRegistry());
		factoryBean.setJobLauncher(new SimpleJobLauncher());
		factoryBean.setDataSource(dataSource);
		factoryBean.setJobExplorer(jobExplorer);
		factoryBean.setTransactionManager(dataSourceTransactionManager);
		return factoryBean;
	}

	@Bean
	public JobExplorerFactoryBean jobExplorerFactoryBean(DataSource dataSource) {
		JobExplorerFactoryBean jobExplorerFactoryBean = new JobExplorerFactoryBean();
		jobExplorerFactoryBean.setDataSource(dataSource);
		return jobExplorerFactoryBean;
	}

	@Configuration
	@ConditionalOnProperty(name = "spring.dataflow.embedded.database.enabled", havingValue = "true", matchIfMissing = true)
	@ConditionalOnExpression("#{'${spring.datasource.url:}'.startsWith('jdbc:h2:tcp://localhost:')}")
	public static class H2ServerConfiguration {

		@Bean
		public JobRepositoryFactoryBean jobRepositoryFactoryBeanForServer(DataSource dataSource,
				PlatformTransactionManager dataSourceTransactionManager) {
			JobRepositoryFactoryBean repositoryFactoryBean = new JobRepositoryFactoryBean();
			repositoryFactoryBean.setDataSource(dataSource);
			repositoryFactoryBean.setTransactionManager(dataSourceTransactionManager);
			return repositoryFactoryBean;
		}

		@Bean
		public BatchDataSourceInitializer batchRepositoryInitializerForDefaultDBForServer(DataSource dataSource,
				ResourceLoader resourceLoader, BatchProperties properties) {
			return new BatchDataSourceInitializer(dataSource, resourceLoader, properties);
		}

		@Bean
		public TaskRepositoryInitializer taskRepositoryInitializerForDefaultDB(DataSource dataSource) {
			TaskRepositoryInitializer taskRepositoryInitializer = new TaskRepositoryInitializer();
			taskRepositoryInitializer.setDataSource(dataSource);
			return taskRepositoryInitializer;
		}
	}

	@Configuration
	@ConditionalOnExpression("#{!'${spring.datasource.url:}'.startsWith('jdbc:h2:tcp://localhost:') || "
			+ "('${spring.datasource.url:}'.startsWith('jdbc:h2:tcp://localhost:') &&"
			+ "'${spring.dataflow.embedded.database.enabled}'.equals('false'))}")
	public static class NoH2ServerConfiguration {

		@Bean
		public JobRepositoryFactoryBean jobRepositoryFactoryBean(DataSource dataSource,
				PlatformTransactionManager platformTransactionManager) {
			JobRepositoryFactoryBean repositoryFactoryBean = new JobRepositoryFactoryBean();
			repositoryFactoryBean.setDataSource(dataSource);
			repositoryFactoryBean.setTransactionManager(platformTransactionManager);
			return repositoryFactoryBean;
		}

		@Bean
		public BatchDataSourceInitializer batchRepositoryInitializerForDefaultDB(DataSource dataSource,
				ResourceLoader resourceLoader, BatchProperties properties) {
			return new BatchDataSourceInitializer(dataSource, resourceLoader, properties);
		}

		@Bean
		public TaskRepositoryInitializer taskRepositoryInitializerForDB(DataSource dataSource) {
			TaskRepositoryInitializer taskRepositoryInitializer = new TaskRepositoryInitializer();
			taskRepositoryInitializer.setDataSource(dataSource);
			return taskRepositoryInitializer;
		}
		// TODO: BOOT2 handle this custom ddl stuff
		// @Bean
		// @DependsOn({ "batchRepositoryInitializerForDefaultDB", "taskRepositoryInitializerForDB"
		// })
		// public AbstractDatabaseInitializer batchTaskIndexesDatabaseInitializer(DataSource
		// dataSource,
		// ResourceLoader resourceLoader) {
		// return new BatchTaskIndexesDatabaseInitializer(dataSource, resourceLoader);
		// }
	}

}
