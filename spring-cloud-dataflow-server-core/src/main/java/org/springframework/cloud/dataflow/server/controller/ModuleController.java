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

package org.springframework.cloud.dataflow.server.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.cloud.dataflow.core.ArtifactType;
import org.springframework.cloud.dataflow.registry.AppRegistration;
import org.springframework.cloud.dataflow.registry.AppRegistry;
import org.springframework.cloud.dataflow.rest.resource.DetailedModuleRegistrationResource;
import org.springframework.cloud.dataflow.rest.resource.ModuleRegistrationResource;
import org.springframework.cloud.stream.configuration.metadata.ModuleConfigurationMetadataResolver;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles all Module related interactions.
 *
 * @author Glenn Renfro
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Eric Bottard
 * @author Gary Russell
 * @author Patrick Peralta
 */
@RestController
@RequestMapping("/modules")
@ExposesResourceFor(ModuleRegistrationResource.class)
public class ModuleController {

	private final Assembler moduleAssembler = new Assembler();

	private final AppRegistry appRegistry;

	private ModuleConfigurationMetadataResolver metadataResolver;

	public ModuleController(AppRegistry appRegistry, ModuleConfigurationMetadataResolver metadataResolver) {
		this.appRegistry = appRegistry;
		this.metadataResolver = metadataResolver;
	}

	/**
	 * List module registrations.
	 */
	@RequestMapping(method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public PagedResources<? extends ModuleRegistrationResource> list(
			PagedResourcesAssembler<AppRegistration> assembler,
			@RequestParam(value = "type", required = false) ArtifactType type,
			@RequestParam(value = "detailed", defaultValue = "false") boolean detailed) {

		List<AppRegistration> list = new ArrayList<>(appRegistry.findAll());
		for (Iterator<AppRegistration> iterator = list.iterator(); iterator.hasNext(); ) {
			ArtifactType artifactType = iterator.next().getType();
			if ((type != null && artifactType != type) || artifactType == ArtifactType.library) {
				iterator.remove();
			}
		}
		Collections.sort(list);
		return assembler.toResource(new PageImpl<>(list), moduleAssembler);
	}

	/**
	 * Retrieve detailed information about a particular module.
	 * @param type module type
	 * @param name module name
	 * @return detailed module information
	 */
	@RequestMapping(value = "/{type}/{name}", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public DetailedModuleRegistrationResource info(
			@PathVariable("type") ArtifactType type,
			@PathVariable("name") String name) {
		Assert.isTrue(type != ArtifactType.library, "Only modules are supported by this endpoint");
		AppRegistration registration = appRegistry.find(name, type);
		if (registration == null) {
			return null;
		}
		DetailedModuleRegistrationResource result = new DetailedModuleRegistrationResource(moduleAssembler.toResource(registration));
		Resource resource = registration.getResource();

		List<ConfigurationMetadataProperty> properties = metadataResolver.listProperties(resource);
		for (ConfigurationMetadataProperty property : properties) {
			result.addOption(property);
		}
		return result;
	}

	/**
	 * Register a module name and type with its URI.
	 * @param type        module type
	 * @param name        module name
	 * @param uri         URI for the module artifact (e.g. {@literal maven://group:artifact:version})
	 * @param force       if {@code true}, overwrites a pre-existing registration
	 */
	@RequestMapping(value = "/{type}/{name}", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public void register(
			@PathVariable("type") ArtifactType type,
			@PathVariable("name") String name,
			@RequestParam("uri") String uri,
			@RequestParam(value = "force", defaultValue = "false") boolean force) {
		Assert.isTrue(type != ArtifactType.library, "Only modules are supported by this endpoint");
		AppRegistration previous = appRegistry.find(name, type);
		if (!force && previous != null) {
			throw new ModuleAlreadyRegisteredException(previous);
		}
		try {
			appRegistry.save(name, type, new URI(uri));
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Unregister a module name and type.
	 * @param type the module type
	 * @param name the module name
	 */
	@RequestMapping(value = "/{type}/{name}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void unregister(@PathVariable("type") ArtifactType type, @PathVariable("name") String name) {
		Assert.isTrue(type != ArtifactType.library, "Only modules are supported by this endpoint");
		appRegistry.delete(name, type);
	}

	/**
	 * Register all apps listed in a properties file or provided as key/value pairs.
	 * @param uri         URI for the properties file
	 * @param apps        key/value pairs representing apps, separated by newlines
	 * @param force       if {@code true}, overwrites any pre-existing registrations
	 */
	@RequestMapping(method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.CREATED)
	public PagedResources<? extends ModuleRegistrationResource> registerAll(
			PagedResourcesAssembler<AppRegistration> assembler,
			@RequestParam(value = "uri", required = false) String uri,
			@RequestParam(value = "apps", required = false) Properties apps,
			@RequestParam(value = "force", defaultValue = "false") boolean force) {
		List<AppRegistration> registrations = new ArrayList<>();
		if (StringUtils.hasText(uri)) {
			registrations.addAll(appRegistry.importAll(force, uri));
		}
		else if (!CollectionUtils.isEmpty(apps)) {
			for (String key : apps.stringPropertyNames()) {
				String[] tokens = key.split("\\.", 2);
				String name = tokens[1];
				ArtifactType type = ArtifactType.valueOf(tokens[0]);
				if (force || null == appRegistry.find(name, type)) {
					try {
						registrations.add(appRegistry.save(name, type, new URI(apps.getProperty(key))));
					}
					catch (URISyntaxException e) {
						throw new IllegalArgumentException(e);
					}
				}
			}
		}
		Collections.sort(registrations);
		return assembler.toResource(new PageImpl<>(registrations), moduleAssembler);
	}

	class Assembler extends ResourceAssemblerSupport<AppRegistration, ModuleRegistrationResource> {

		public Assembler() {
			super(ModuleController.class, ModuleRegistrationResource.class);
		}

		@Override
		public ModuleRegistrationResource toResource(AppRegistration registration) {
			return createResourceWithId(String.format("%s/%s", registration.getType(), registration.getName()), registration);
		}

		@Override
		protected ModuleRegistrationResource instantiateResource(AppRegistration registration) {
			return new ModuleRegistrationResource(registration.getName(),
					registration.getType().name(), registration.getUri().toString());
		}
	}

	@ResponseStatus(HttpStatus.CONFLICT)
	public static class ModuleAlreadyRegisteredException extends IllegalStateException {

		private final AppRegistration previous;

		public ModuleAlreadyRegisteredException(AppRegistration previous) {
			this.previous = previous;
		}

		@Override
		public String getMessage() {
			return String.format("The '%s:%s' module is already registered as %s", previous.getType(), previous.getName(), previous.getUri());
		}

		public AppRegistration getPrevious() {
			return previous;
		}
	}

}
