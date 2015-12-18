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

package org.springframework.cloud.dataflow.admin.controller;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.dataflow.admin.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.artifact.registry.ArtifactRegistration;
import org.springframework.cloud.dataflow.artifact.registry.ArtifactRegistry;
import org.springframework.cloud.dataflow.core.ArtifactCoordinates;
import org.springframework.cloud.dataflow.core.ArtifactType;
import org.springframework.cloud.dataflow.core.BindingProperties;
import org.springframework.cloud.dataflow.core.ModuleDefinition;
import org.springframework.cloud.dataflow.core.ModuleDeploymentId;
import org.springframework.cloud.dataflow.core.ModuleDeploymentRequest;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.module.DeploymentState;
import org.springframework.cloud.dataflow.module.ModuleStatus;
import org.springframework.cloud.dataflow.module.deployer.ModuleDeployer;
import org.springframework.cloud.dataflow.rest.resource.StreamDeploymentResource;
import org.springframework.cloud.dataflow.rest.util.DeploymentPropertiesUtils;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for deployment operations on {@link StreamDefinition}.
 *
 * @author Eric Bottard
 */
@RestController
@RequestMapping("/streams/deployments")
@ExposesResourceFor(StreamDeploymentResource.class)
public class StreamDeploymentController {

	private static final Logger logger = LoggerFactory.getLogger(StreamDeploymentController.class);

	/**
	 * The repository this controller will use for stream CRUD operations.
	 */
	private final StreamDefinitionRepository repository;

	/**
	 * The artifact registry this controller will use to look up modules and libraries.
	 */
	private final ArtifactRegistry registry;

	/**
	 * The deployer this controller will use to deploy stream modules.
	 */
	private final ModuleDeployer deployer;

	private static final String DEFAULT_PARTITION_KEY_EXPRESSION = "payload";

	/**
	 * Create a {@code StreamController} that delegates
	 * <ul>
	 *     <li>CRUD operations to the provided {@link StreamDefinitionRepository}</li>
	 *     <li>module coordinate retrieval to the provided {@link ArtifactRegistry}</li>
	 *     <li>deployment operations to the provided {@link ModuleDeployer}</li>
	 * </ul>
	 *
	 * @param repository  the repository this controller will use for stream CRUD operations
	 * @param registry    artifact registry this controller will use to look up modules
	 * @param deployer    the deployer this controller will use to deploy stream modules
	 */
	@Autowired
	public StreamDeploymentController(StreamDefinitionRepository repository, ArtifactRegistry registry,
	                                  @Qualifier("processModuleDeployer") ModuleDeployer deployer) {
		Assert.notNull(repository, "repository must not be null");
		Assert.notNull(registry, "registry must not be null");
		Assert.notNull(deployer, "deployer must not be null");
		this.repository = repository;
		this.registry = registry;
		this.deployer = deployer;
	}

	/**
	 * Request un-deployment of an existing stream.
	 *
	 * @param name the name of an existing stream (required)
	 */
	@RequestMapping(value = "/{name}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void undeploy(@PathVariable("name") String name) {
		StreamDefinition stream = this.repository.findOne(name);
		Assert.notNull(stream, String.format("no stream defined: %s", name));
		undeployStream(stream);
	}

	/**
	 * Request un-deployment of all streams.
	 */
	@RequestMapping(value = "", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void undeployAll() {
		for (StreamDefinition stream : this.repository.findAll()) {
			this.undeployStream(stream);
		}
	}

	/**
	 * Request deployment of an existing stream definition.
	 *
	 * @param name the name of an existing stream definition (required)
	 * @param properties the deployment properties for the stream as a comma-delimited list of key=value pairs
	 */
	@RequestMapping(value = "/{name}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public void deploy(@PathVariable("name") String name,
			@RequestParam(required = false) String properties) {
		StreamDefinition stream = this.repository.findOne(name);
		Assert.notNull(stream, String.format("no stream defined: %s", name));
		deployStream(stream, DeploymentPropertiesUtils.parse(properties));
	}

	/**
	 * Deploy a stream as defined by its {@link StreamDefinition} and optional deployment properties.
	 *
	 * @param stream the stream to deploy
	 * @param streamDeploymentProperties the deployment properties for the stream
	 */
	private void deployStream(StreamDefinition stream, Map<String, String> streamDeploymentProperties) {
		if (streamDeploymentProperties == null) {
			streamDeploymentProperties = Collections.emptyMap();
		}
		Iterator<ModuleDefinition> iterator = stream.getDeploymentOrderIterator();
		int nextModuleCount = 0;
		boolean isDownStreamModulePartitioned = false;
		while (iterator.hasNext()) {
			ModuleDefinition currentModule = iterator.next();
			ArtifactType type = determineModuleType(currentModule);
			ArtifactRegistration registration = this.registry.find(currentModule.getName(), type);
			if (registration == null) {
				throw new IllegalArgumentException(String.format(
						"Module %s of type %s not found in registry", currentModule.getName(), type));
			}
			ArtifactCoordinates coordinates = registration.getCoordinates();
			Map<String, String> moduleDeploymentProperties = extractModuleDeploymentProperties(currentModule, streamDeploymentProperties);
			boolean upstreamModuleSupportsPartition = upstreamModuleHasPartitionInfo(stream, currentModule, streamDeploymentProperties);
			// consumer module partition properties
			if (isPartitionedConsumer(currentModule, moduleDeploymentProperties, upstreamModuleSupportsPartition)) {
				updateConsumerPartitionProperties(moduleDeploymentProperties);
			}
			// producer module partition properties
			if (isDownStreamModulePartitioned) {
				updateProducerPartitionProperties(moduleDeploymentProperties, nextModuleCount);
			}
			nextModuleCount = getModuleCount(moduleDeploymentProperties);
			isDownStreamModulePartitioned = isPartitionedConsumer(currentModule, moduleDeploymentProperties,
					upstreamModuleSupportsPartition);

			currentModule = postProcessLibraryProperties(currentModule);

			this.deployer.deploy(new ModuleDeploymentRequest(currentModule, coordinates, moduleDeploymentProperties));
		}
	}

	/**
	 * Looks at parameters of a module that represent maven coordinates and, if a simple name has been used,
	 * resolve it from the {@link ArtifactRegistry}.
	 */
	private ModuleDefinition postProcessLibraryProperties(ModuleDefinition module) {
		String includes = module.getParameters().get("includes");
		if (includes == null) {
			return module;
		}
		String[] libs = StringUtils.delimitedListToStringArray(includes, ",", " \t");
		for (int i = 0; i < libs.length; i++) {
			ArtifactCoordinates coordinates;
			try {
				coordinates = ArtifactCoordinates.parse(libs[i]);
			}
			catch (IllegalArgumentException e) {
				ArtifactRegistration registration = registry.find(libs[i], ArtifactType.library);
				if (registration == null) {
					throw new IllegalArgumentException("'" + libs[i] + "' could not be parsed as maven coordinates and is not a registered library");
				}
				coordinates = registration.getCoordinates();
			}
			libs[i] = coordinates.toString();
		}
		return ModuleDefinition.Builder.from(module)
				.setParameter("includes", StringUtils.arrayToCommaDelimitedString(libs))
				.build();
	}

	/**
	 * Return the {@link ArtifactType} for a {@link ModuleDefinition} in the context
	 * of a defined stream.
	 *
	 * @param moduleDefinition the module for which to determine the type
	 * @return {@link ArtifactType} for the given module
	 */
	private ArtifactType determineModuleType(ModuleDefinition moduleDefinition) {
		// Parser has already taken care of source/sink named channels, etc
		boolean hasOutput = moduleDefinition.getParameters().containsKey(BindingProperties.OUTPUT_BINDING_KEY);
		boolean hasInput = moduleDefinition.getParameters().containsKey(BindingProperties.INPUT_BINDING_KEY);
		if (hasInput && hasOutput) {
			return ArtifactType.processor;
		}
		else if (hasInput) {
			return ArtifactType.sink;
		}
		else if (hasOutput) {
			return ArtifactType.source;
		}
		else {
			throw new IllegalStateException(moduleDefinition + " had neither input nor output set");
		}
	}

	/**
	 * Extract and return a map of properties for a specific module within the
	 * deployment properties of a stream.
	 *
	 * @param module module for which to return a map of properties
	 * @param streamDeploymentProperties deployment properties for the stream that the module is defined in
	 * @return map of properties for a module
	 */
	private Map<String, String> extractModuleDeploymentProperties(ModuleDefinition module,
			Map<String, String> streamDeploymentProperties) {
		Map<String, String> moduleDeploymentProperties = new HashMap<>();
		String wildCardPrefix = "module.*.";
		// first check for wild card prefix
		for (Map.Entry<String, String> entry : streamDeploymentProperties.entrySet()) {
			if (entry.getKey().startsWith(wildCardPrefix)) {
				moduleDeploymentProperties.put(entry.getKey().substring(wildCardPrefix.length()), entry.getValue());
			}
		}
		String modulePrefix = String.format("module.%s.", module.getLabel());
		for (Map.Entry<String, String> entry : streamDeploymentProperties.entrySet()) {
			if (entry.getKey().startsWith(modulePrefix)) {
				moduleDeploymentProperties.put(entry.getKey().substring(modulePrefix.length()), entry.getValue());
			}
		}
		return moduleDeploymentProperties;
	}

	/**
	 * Return {@code true} if the upstream module (the module that appears before
	 * the provided module) contains partition related properties.
	 *
	 * @param stream        stream for the module
	 * @param currentModule module for which to determine if the upstream module
	 *                      has partition properties
	 * @param streamDeploymentProperties deployment properties for the stream
	 * @return true if the upstream module has partition properties
	 */
	private boolean upstreamModuleHasPartitionInfo(StreamDefinition stream, ModuleDefinition currentModule,
			Map<String, String> streamDeploymentProperties) {
		Iterator<ModuleDefinition> iterator = stream.getDeploymentOrderIterator();
		while (iterator.hasNext()) {
			ModuleDefinition module = iterator.next();
			if (module.equals(currentModule) && iterator.hasNext()) {
				ModuleDefinition prevModule = iterator.next();
				Map<String, String> moduleDeploymentProperties = extractModuleDeploymentProperties(prevModule, streamDeploymentProperties);
				return moduleDeploymentProperties.containsKey(BindingProperties.PARTITION_KEY_EXPRESSION) ||
						moduleDeploymentProperties.containsKey(BindingProperties.PARTITION_KEY_EXTRACTOR_CLASS);
			}
		}
		return false;
	}

	/**
	 * Return {@code true} if the provided module is a consumer of partitioned data.
	 * This is determined either by the deployment properties for the module
	 * or whether the previous (upstream) module is publishing partitioned data.
	 *
	 * @param module module for which to determine if it is consuming partitioned data
	 * @param moduleDeploymentProperties deployment properties for the module
	 * @param upstreamModuleSupportsPartition if true, previous (upstream) module
	 * in the stream publishes partitioned data
	 * @return true if this module consumes partitioned data
	 */
	private boolean isPartitionedConsumer(ModuleDefinition module,
			Map<String, String> moduleDeploymentProperties,
			boolean upstreamModuleSupportsPartition) {
		return upstreamModuleSupportsPartition ||
				(module.getParameters().containsKey(BindingProperties.INPUT_BINDING_KEY) &&
						moduleDeploymentProperties.containsKey(BindingProperties.PARTITIONED_PROPERTY) &&
						moduleDeploymentProperties.get(BindingProperties.PARTITIONED_PROPERTY).equalsIgnoreCase("true"));
	}

	/**
	 * Add module properties for consuming partitioned data to the provided properties.
	 *
	 * @param properties properties to update
	 */
	private void updateConsumerPartitionProperties(Map<String, String> properties) {
		properties.put(BindingProperties.INPUT_PARTITIONED, "true");
		if (properties.containsKey(BindingProperties.COUNT_PROPERTY)) {
			properties.put(BindingProperties.INSTANCE_COUNT, properties.get(BindingProperties.COUNT_PROPERTY));
		}
	}

	/**
	 * Add module properties for producing partitioned data to the provided properties.
	 *
	 * @param properties properties to update
	 * @param nextModuleCount the number of module instances for the next (downstream) module in the stream
	 */
	private void updateProducerPartitionProperties(Map<String, String> properties, int nextModuleCount) {
		properties.put(BindingProperties.OUTPUT_PARTITION_COUNT, String.valueOf(nextModuleCount));
		if (properties.containsKey(BindingProperties.PARTITION_KEY_EXPRESSION)) {
			properties.put(BindingProperties.OUTPUT_PARTITION_KEY_EXPRESSION,
					properties.get(BindingProperties.PARTITION_KEY_EXPRESSION));
		}
		else {
			properties.put(BindingProperties.OUTPUT_PARTITION_KEY_EXPRESSION, DEFAULT_PARTITION_KEY_EXPRESSION);
		}
		if (properties.containsKey(BindingProperties.PARTITION_KEY_EXTRACTOR_CLASS)) {
			properties.put(BindingProperties.OUTPUT_PARTITION_KEY_EXTRACTOR_CLASS,
					properties.get(BindingProperties.PARTITION_KEY_EXTRACTOR_CLASS));
		}
		if (properties.containsKey(BindingProperties.PARTITION_SELECTOR_CLASS)) {
			properties.put(BindingProperties.OUTPUT_PARTITION_SELECTOR_CLASS,
					properties.get(BindingProperties.PARTITION_SELECTOR_CLASS));
		}
		if (properties.containsKey(BindingProperties.PARTITION_SELECTOR_EXPRESSION)) {
			properties.put(BindingProperties.OUTPUT_PARTITION_SELECTOR_EXPRESSION,
					properties.get(BindingProperties.PARTITION_SELECTOR_EXPRESSION));
		}
	}

	/**
	 * Return the module count indicated in the provided properties.
	 *
	 * @param properties properties for the module for which to determine the count
	 * @return module count indicated in the provided properties;
	 * if the properties do not contain a count a value of {@code 1} is returned
	 */
	private int getModuleCount(Map<String, String> properties) {
		return (properties.containsKey(BindingProperties.COUNT_PROPERTY)) ?
				Integer.valueOf(properties.get(BindingProperties.COUNT_PROPERTY)) : 1;
	}

	/**
	 * Undeploy the given stream.
	 *
	 * @param stream stream to undeploy
	 */
	private void undeployStream(StreamDefinition stream) {
		for (ModuleDefinition module : stream.getModuleDefinitions()) {
			ModuleDeploymentId id = ModuleDeploymentId.fromModuleDefinition(module);
			ModuleStatus status = this.deployer.status(id);
			if (!EnumSet.of(DeploymentState.unknown, DeploymentState.undeployed)
					.contains(status.getState())) {
				this.deployer.undeploy(id);
			}
		}
	}


}
