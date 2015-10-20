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

package org.springframework.cloud.dataflow.rest.client;

/**
 * Interface the REST clients implement to interact with spring-cloud-dataflow REST API.
 *
 * @author Ilayaperumal Gopinathan
 */
public interface DataFlowOperations {

	/**
	 * Stream related operations.
	 */
	StreamOperations streamOperations();

	/**
	 * Counter related operations.
	 */
	CounterOperations counterOperations();

	/**
	 * Task related operations.
	 */
	TaskOperations taskOperations();

	/**
	 * Module related operations.
	 */
	ModuleOperations moduleOperations();

	/**
	 * DSL Completion related operations.
	 */
	CompletionOperations completionOperations();
}
