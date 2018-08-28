/*
 * Copyright 2015-2016 the original author or authors.
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
package org.springframework.cloud.dataflow.server.repository;

import org.springframework.cloud.dataflow.core.TaskDefinition;
import org.springframework.cloud.dataflow.server.repository.support.SearchPageable;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Repository to access {@link TaskDefinition}s.
 *
 * @author Michael Minella
 * @author Gunnar Hillert
 */
public interface TaskDefinitionRepository extends PagingAndSortingRepository<TaskDefinition, String> {
	Page<TaskDefinition> findByNameLike(SearchPageable searchPageable);

	default TaskDefinition findOne(String id) {
		return (TaskDefinition) findById(id).orElse(null);
	}

	default void delete(String id) {
		deleteById(id);
	}

	default boolean exists(String id) {
		return existsById(id);
	}

	default Iterable<TaskDefinition> save(Iterable<TaskDefinition> entities) {
		return saveAll(entities);
	}

	default Iterable<TaskDefinition> findAll(Iterable<String> ids) {
		return findAllById(ids);
	}

	default void delete(Iterable<TaskDefinition> entities) {
		deleteAll(entities);
	}
}
