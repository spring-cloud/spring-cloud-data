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

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * @author Glenn Renfro
 */
/**
 * Intitializes the schema for the task database .
 *
 * @author Glenn Renfro.
 */

public class TaskDatabaseInitializer implements InitializingBean {

	private static final Logger logger = LoggerFactory.getLogger(TaskDatabaseInitializer.class);


	@Autowired
	DataSource dataSource;

	public TaskDatabaseInitializer() {
	}

	public void initializeDatabase() {
		org.springframework.cloud.task.repository.support.
				TaskDatabaseInitializer.initializeDatabase(dataSource, new DefaultResourceLoader());
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		initializeDatabase();
	}

}
