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

import static org.springframework.cloud.dataflow.core.ArtifactType.processor;
import static org.springframework.cloud.dataflow.core.ArtifactType.sink;

import java.util.List;

import org.springframework.cloud.dataflow.core.dsl.CheckPointedParseException;
import org.springframework.cloud.dataflow.registry.AppRegistration;
import org.springframework.cloud.dataflow.registry.AppRegistry;

/**
 * Proposes module names when the user has typed a destination redirection.
 *
 * @author Eric Bottard
 * @author Mark Fisher
 */
class DestinationNameYieldsModulesRecoveryStrategy extends
		StacktraceFingerprintingRecoveryStrategy<CheckPointedParseException> {

	private final AppRegistry appRegistry;

	public DestinationNameYieldsModulesRecoveryStrategy(AppRegistry appRegistry) {
		super(CheckPointedParseException.class, "queue:foo >", "queue:foo > ");
		this.appRegistry = appRegistry;
	}

	@Override
	public boolean shouldTrigger(String dslStart, Exception exception) {
		if( !super.shouldTrigger(dslStart, exception)) {
			return false;
		}
		// Cast is safe from call to super.
		// Backtracking would return even before the destination
		return ((CheckPointedParseException)exception).getExpressionStringUntilCheckpoint().trim().isEmpty();
	}

	@Override
	public void addProposals(String dsl, CheckPointedParseException exception,
			int detailLevel, List<CompletionProposal> proposals) {
		CompletionProposal.Factory completionFactory = CompletionProposal.expanding(dsl);
		for (AppRegistration appRegistration : appRegistry.findAll()) {
			if (appRegistration.getType() == processor || appRegistration.getType() == sink) {
				proposals.add(completionFactory.withSeparateTokens(appRegistration.getName(),
						"Wire destination into a " + appRegistration.getType() + " module"));
			}
		}
	}

}
