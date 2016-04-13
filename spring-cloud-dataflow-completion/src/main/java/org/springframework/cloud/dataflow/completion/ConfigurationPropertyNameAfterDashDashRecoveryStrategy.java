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

package org.springframework.cloud.dataflow.completion;

import static org.springframework.cloud.dataflow.completion.CompletionProposal.expanding;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataGroup;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.cloud.dataflow.core.ArtifactType;
import org.springframework.cloud.dataflow.core.ModuleDefinition;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.core.dsl.CheckPointedParseException;
import org.springframework.cloud.dataflow.registry.AppRegistration;
import org.springframework.cloud.dataflow.registry.AppRegistry;
import org.springframework.cloud.stream.configuration.metadata.ModuleConfigurationMetadataResolver;
import org.springframework.core.io.Resource;

/**
 * Provides completion proposals when the user has typed the two dashes that
 * precede a module configuration property.
 *
 * @author Eric Bottard
 * @author Mark Fisher
 */
class ConfigurationPropertyNameAfterDashDashRecoveryStrategy
		extends StacktraceFingerprintingRecoveryStrategy<CheckPointedParseException> {

	private final AppRegistry appRegistry;

	private final ModuleConfigurationMetadataResolver metadataResolver;

	ConfigurationPropertyNameAfterDashDashRecoveryStrategy(AppRegistry appRegistry,
			ModuleConfigurationMetadataResolver metadataResolver) {
		super(CheckPointedParseException.class, "file --", "file | foo --");
		this.appRegistry = appRegistry;
		this.metadataResolver = metadataResolver;
	}

	@Override
	public void addProposals(String dsl, CheckPointedParseException exception,
	                         int detailLevel, List<CompletionProposal> collector) {

		String safe = exception.getExpressionStringUntilCheckpoint();
		StreamDefinition streamDefinition = new StreamDefinition("__dummy", safe);
		ModuleDefinition lastModule = streamDefinition.getDeploymentOrderIterator().next();

		String lastModuleName = lastModule.getName();
		AppRegistration lastAppRegistration = null;
		for (ArtifactType moduleType : CompletionUtils.determinePotentialTypes(lastModule)) {
			lastAppRegistration = appRegistry.find(lastModuleName, moduleType);
			if (lastAppRegistration != null) {
				break;
			}
		}
		if (lastAppRegistration == null) {
			// Not a valid module name, do nothing
			return;
		}
		Set<String> alreadyPresentOptions = new HashSet<>(lastModule.getParameters().keySet());

		Resource jarFile = lastAppRegistration.getResource();

		CompletionProposal.Factory proposals = expanding(dsl);
		Set<String> prefixes = new HashSet<>();
		for (ConfigurationMetadataGroup group : metadataResolver.listPropertyGroups(jarFile)) {
			String groupId = ConfigurationMetadataRepository.ROOT_GROUP.equals(group.getId()) ? "" : group.getId();
			if ("".equals(groupId)) {
				// For props that don't have a group id, add their bare names as proposals
				// Caveat: props can themselves have dots in their names. In that case, treat that as a prefix
				for (ConfigurationMetadataProperty property : group.getProperties().values()) {
					int dot = property.getId().indexOf('.', 0);
					if (dot > 0) {
						String prefix = property.getId().substring(0, dot);
						if (!prefixes.contains(prefix)) {
							prefixes.add(prefix);
							collector.add(proposals.withSuffix(prefix + ".", "Properties starting with '" + prefix + ".'"));
						}
					}
					else if (!alreadyPresentOptions.contains(property.getId())) {
						collector.add(proposals.withSuffix(property.getId()
								+ "=", property.getShortDescription()));
					}
				}
			}
			else {
				// Present group ids up to the first dot
				int dot = groupId.indexOf('.', 0);
				String prefix = dot > 0 ? groupId.substring(0, dot) : groupId;
				if (!prefixes.contains(prefix)) {
					prefixes.add(prefix);
					collector.add(proposals.withSuffix(prefix + ".", "Properties starting with '" + prefix + ".'"));
				}
			}
		}
	}
}
