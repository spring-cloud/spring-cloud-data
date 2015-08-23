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

package org.springframework.cloud.data.admin.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.cloud.data.core.StreamDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * A redis implementation of {@link StreamDefinitionRepository}, storing each
 * definition as a {@literal name:dsl} mapping in a redis hash.
 *
 * @author Eric Bottard
 */
public class RedisStreamDefinitionRepository implements StreamDefinitionRepository {

	private final BoundHashOperations<String, String, String> hashOperations;

	/**
	 * Construct a new StreanDefinitionRepository backed by redis, storing definitions under the key
	 * specified by '{@literal <hashKey>}'.
	 */
	public RedisStreamDefinitionRepository(String hashKey, RedisConnectionFactory redisConnectionFactory) {
		StringRedisTemplate redisTemplate = new StringRedisTemplate(redisConnectionFactory);
		hashOperations= redisTemplate.boundHashOps(hashKey);
	}

	@Override
	public Iterable<StreamDefinition> findAll(Sort sort) {
		return findAll(new PageRequest(0, Integer.MAX_VALUE, sort));
	}

	@Override
	public Page<StreamDefinition> findAll(Pageable pageable) {

		TreeMap<String, String> sorted = new TreeMap<>(comparatorFor(pageable.getSort().getOrderFor("name").getDirection()));
		sorted.putAll(hashOperations.entries());
		int total = sorted.size();
		int start = pageable.getOffset();
		int end = Math.min(pageable.getOffset() + pageable.getPageSize(), total);
		@SuppressWarnings("unchecked")
		Map.Entry<String, String>[] entries = sorted.entrySet().toArray(new Map.Entry[total]);
		List<StreamDefinition> result = new ArrayList<>(Math.max(end - start, 0));
		for (int i = start; i < end ; i++) {
			result.add(new StreamDefinition(entries[i].getKey(), entries[i].getValue()));
		}
		return new PageImpl<>(result, pageable, total);
	}

	private Comparator<String> comparatorFor(final Sort.Direction order) {
		return new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return order == Sort.Direction.ASC ? o1.compareTo(o2) : - o1.compareTo(o2);
			}
		};
	}

	@Override
	public <S extends StreamDefinition> S save(S entity) {
		hashOperations.put(entity.getName(), entity.getDslText());
		return entity;
	}

	@Override
	public <S extends StreamDefinition> Iterable<S> save(Iterable<S> entities) {
		Map<String, String> asMap = new HashMap<>();
		for (StreamDefinition sd : entities) {
			asMap.put(sd.getName(), sd.getDslText());
		}
		hashOperations.putAll(asMap);
		return entities;
	}

	@Override
	public StreamDefinition findOne(String s) {
		String dsl = hashOperations.get(s);
		return dsl != null ? new StreamDefinition(s, dsl) : null;
	}

	@Override
	public boolean exists(String s) {
		return hashOperations.hasKey(s);
	}

	@Override
	public Iterable<StreamDefinition> findAll() {
		return findAll(new Sort("name"));
	}

	@Override
	public Iterable<StreamDefinition> findAll(Iterable<String> strings) {
		List<String> keys;
		if (strings instanceof List) {
			keys = (List<String>) strings;
		}
		else {
			keys = new ArrayList<>();
			for (String name : strings) {
				keys.add(name);
			}
		}
		List<String> dslTexts = hashOperations.multiGet(keys);
		return zipToStreamDefinitions(keys, dslTexts);
	}

	@Override
	public long count() {
		return hashOperations.size();
	}

	@Override
	public void delete(String s) {
		hashOperations.delete(s);
	}

	@Override
	public void delete(StreamDefinition entity) {
		delete(entity.getName());
	}

	@Override
	public void delete(Iterable<? extends StreamDefinition> entities) {
		Object[] keys;
		if (entities instanceof Collection) {
			keys = new Object[((Collection) entities).size()];
			int i = 0;
			for (StreamDefinition sd : entities) {
				keys[i++] = sd.getName();
			}
		}
		else {
			List<String> names = new ArrayList<>();
			for (StreamDefinition sd : entities) {
				names.add(sd.getName());
			}
			keys = names.toArray();
		}
		hashOperations.delete(keys);
	}

	@Override
	public void deleteAll() {
		hashOperations.getOperations().delete(hashOperations.getKey());
	}

	/**
	 * Return a list of StreamDefinitions, made by mapping a set of non null names and possibly null dsl texts.
	 * In case of null dsl text, a null element is added to the result list.
	 */
	private List<StreamDefinition> zipToStreamDefinitions(List<String> names, List<String> definitions) {
		Iterator<String> it = definitions.iterator();
		List<StreamDefinition> result = new ArrayList<>(definitions.size());
		for (String name : names) {
			String dsl = it.next();
			result.add(dsl != null ? new StreamDefinition(name, dsl) : null);
		}
		return result;
	}

}
