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

import java.util.Map;

import org.springframework.cloud.dataflow.rest.resource.StreamDefinitionResource;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
import org.springframework.hateoas.PagedResources;

/**
 * Interface defining operations available against streams.
 *
 * @author Eric Bottard
 * @author Ilayaperumal Gopinathan
 * @author Mark Fisher
 */
public interface StreamOperations {

	/**
	 * @return the list streams known to the system.
	 */
	PagedResources<StreamDefinitionResource> list();

	/**
	 * Create a new stream, optionally deploying it.
	 *
	 * @param name the name of the stream
	 * @param definition the stream definition DSL
	 * @param deploy whether to deploy the stream after creating its definition
	 * @return the new stream definition
	 */
	StreamDefinitionResource createStream(String name, String definition, boolean deploy);

	/**
	 * Deploy an already created stream.
	 *
	 * @param name the name of the stream
	 * @param properties the deployment properties
	 */
	void deploy(String name, Map<String, String> properties);

	/**
	 * Undeploy a deployed stream, retaining its definition.
	 *
	 * @param name the name of the stream
	 */
	void undeploy(String name);

	/**
	 * Undeploy all currently deployed streams.
	 */
	void undeployAll();

	/**
	 * Destroy an existing stream.
	 *
	 * @param name the name of the stream
	 */
	void destroy(String name);

	/**
	 * Destroy all streams known to the system.
	 */
	void destroyAll();

	/**
	 * Update the stream given its corresponding releaseName in Skipper using the specified
	 * package and updated yaml config.
	 * @param streamName the name of the stream to update
	 * @param releaseName the corresponding release name of the stream in skipper
	 * @param packageIdentifier the package that corresponds to this stream
	 * @param updateProperties a map of properties to use for updating the stream
	 */
	void updateStream(String streamName, String releaseName, PackageIdentifier packageIdentifier,
			Map<String, String> updateProperties);

	/**
	 * Rollback the stream to the previous or a specific release version.
	 *
	 * @param streamName the name of the stream to rollback
	 * @param version the version to rollback to. If the version is 0, then rollback to the previous release.
	 * The version can not be less than zero.
	 */
	void rollbackStream(String streamName, int version);

	/**
	 * Queries the server for the stream definition.
	 * @param streamName the name of the stream to get status
	 * @return The current stream definition with updated status
	 */
	StreamDefinitionResource getStreamDefinition(String streamName);

}
