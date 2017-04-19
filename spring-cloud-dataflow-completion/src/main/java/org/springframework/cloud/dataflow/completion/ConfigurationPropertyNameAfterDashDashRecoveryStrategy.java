/*
 * Copyright 2015-2017 the original author or authors.
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.StreamAppDefinition;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.core.dsl.CheckPointedParseException;
import org.springframework.cloud.dataflow.registry.AppRegistration;
import org.springframework.cloud.dataflow.registry.AppRegistry;
import org.springframework.core.io.Resource;

import static org.springframework.cloud.dataflow.completion.CompletionProposal.expanding;


/**
 * Provides completion proposals when the user has typed the two dashes that
 * precede an app configuration property.
 * @author Eric Bottard
 * @author Mark Fisher
 */
class ConfigurationPropertyNameAfterDashDashRecoveryStrategy
		extends StacktraceFingerprintingRecoveryStrategy<CheckPointedParseException> {

	private final AppRegistry appRegistry;

	private final ApplicationConfigurationMetadataResolver metadataResolver;

	ConfigurationPropertyNameAfterDashDashRecoveryStrategy(AppRegistry appRegistry,
	                                                       ApplicationConfigurationMetadataResolver metadataResolver) {
		super(CheckPointedParseException.class, "file --", "file | foo --");
		this.appRegistry = appRegistry;
		this.metadataResolver = metadataResolver;
	}

	@Override
	public void addProposals(String dsl, CheckPointedParseException exception,
	                         int detailLevel, List<CompletionProposal> collector) {

		String safe = exception.getExpressionStringUntilCheckpoint();
		StreamDefinition streamDefinition = new StreamDefinition("__dummy", safe);
		StreamAppDefinition lastApp = streamDefinition.getDeploymentOrderIterator().next();

		String lastAppName = lastApp.getName();
		AppRegistration lastAppRegistration = null;
		for (ApplicationType appType : CompletionUtils.determinePotentialTypes(lastApp)) {
			lastAppRegistration = appRegistry.find(lastAppName, appType);
			if (lastAppRegistration != null) {
				break;
			}
		}
		if (lastAppRegistration == null) {
			// Not a valid app name, do nothing
			return;
		}
		Set<String> alreadyPresentOptions = new HashSet<>(lastApp.getProperties().keySet());

		Resource metadataResource = lastAppRegistration.getMetadataResource();

		CompletionProposal.Factory proposals = expanding(dsl);

		// For whitelisted properties, use their shortname
		for (ConfigurationMetadataProperty property : metadataResolver.listProperties(metadataResource)) {
			if (!alreadyPresentOptions.contains(property.getName())) {
				collector.add(proposals.withSuffix(property.getName() + "=", property.getShortDescription()));
			}
		}

		// For other properties, use their fully qualified name
		if (detailLevel > 1) {
			for (ConfigurationMetadataProperty property : metadataResolver.listProperties(metadataResource, true)) {
				if (!alreadyPresentOptions.contains(property.getId())) {
					collector.add(proposals.withSuffix(property.getId() + "=", property.getShortDescription()));
				}
			}
		}
	}
}
