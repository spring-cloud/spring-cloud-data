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

package org.springframework.cloud.dataflow.admin.configuration;

import javax.sql.DataSource;

import org.springframework.cloud.dataflow.admin.repository.TaskDatabaseInitializer;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.support.JdbcTaskExplorerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/**
 * @author Glenn Renfro
 */
@Configuration
@EnableSpringDataWebSupport
public class TaskDependencies {

	@Bean
	public TaskDatabaseInitializer taskExecutionRepository(){
		return new TaskDatabaseInitializer();
	}

	@Bean
	public TaskExplorer taskExplorer(DataSource dataSource){
		JdbcTaskExplorerFactoryBean factoryBean =
				new JdbcTaskExplorerFactoryBean(dataSource);
		return factoryBean.getObject();
	}

}
