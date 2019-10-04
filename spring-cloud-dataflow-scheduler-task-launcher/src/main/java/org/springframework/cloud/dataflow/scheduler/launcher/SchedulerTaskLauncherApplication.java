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

package org.springframework.cloud.dataflow.scheduler.launcher;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * This the purpose of this application is to be used by platform schedulers to
 * launch Spring Cloud Task Applications.  The way this application launches
 * Spring Cloud Task apps is to utilize the Restful API provided by Spring Cloud
 * Data Flow to launch the applications.
 *
 * @author Glenn Renfro
 */
@SpringBootApplication
public class SchedulerTaskLauncherApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder().sources(SchedulerTaskLauncherApplication.class).run(args);
	}
}
