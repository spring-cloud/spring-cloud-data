/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.cloud.dataflow.completion;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

/**
 * Integration tests for TaskCompletionProvider.
 * <p>
 * <p>
 * These tests work hand in hand with a custom {@link org.springframework.cloud.dataflow.registry.service.AppRegistryService} and
 * {@link ApplicationConfigurationMetadataResolver} to provide completions for a fictional
 * set of well known apps.
 * </p>
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Andy Clement
 */
@SuppressWarnings("unchecked")
@SpringBootTest(classes = { CompletionConfiguration.class, CompletionTestsMocks.class }, properties = {
		"spring.main.allow-bean-definition-overriding=true" })
public class TaskCompletionProviderTests {

	@Autowired
	private TaskCompletionProvider completionProvider;

	@Test
	// <TAB> => basic,plum,etc
	public void testEmptyStartShouldProposeSourceApps() {
		MatcherAssert.assertThat(completionProvider.complete("", 1), hasItems(Proposals.proposalThat(is("basic")), Proposals.proposalThat(is("plum"))));
		MatcherAssert.assertThat(completionProvider.complete("", 1), not(hasItems(Proposals.proposalThat(is("log")))));
	}

	@Test
	// b<TAB> => basic
	public void testUnfinishedAppNameShouldReturnCompletions() {
		MatcherAssert.assertThat(completionProvider.complete("b", 1), hasItems(Proposals.proposalThat(is("basic"))));
		MatcherAssert.assertThat(completionProvider.complete("ba", 1), hasItems(Proposals.proposalThat(is("basic"))));
		MatcherAssert.assertThat(completionProvider.complete("pl", 1), not(hasItems(Proposals.proposalThat(is("basic")))));
	}

	@Test
	// basic<TAB> => basic --foo=, etc
	public void testValidTaskDefinitionShouldReturnAppOptions() {
		MatcherAssert.assertThat(completionProvider.complete("basic ", 1),
				hasItems(Proposals.proposalThat(is("basic --expression=")), Proposals.proposalThat(is("basic --expresso="))));
		// Same as above, no final space
		MatcherAssert.assertThat(completionProvider.complete("basic", 1),
				hasItems(Proposals.proposalThat(is("basic --expression=")), Proposals.proposalThat(is("basic --expresso="))));
	}

	@Test
	// file | filter -<TAB> => file | filter --foo,etc
	public void testOneDashShouldReturnTwoDashes() {
		MatcherAssert.assertThat(completionProvider.complete("basic -", 1),
				hasItems(Proposals.proposalThat(is("basic --expression=")), Proposals.proposalThat(is("basic --expresso="))));
	}

	@Test
	// basic --<TAB> => basic --foo,etc
	public void testTwoDashesShouldReturnOptions() {
		MatcherAssert.assertThat(completionProvider.complete("basic --", 1),
				hasItems(Proposals.proposalThat(is("basic --expression=")), Proposals.proposalThat(is("basic --expresso="))));
	}

	@Test
	// file --p<TAB> => file --preventDuplicates=, file --pattern=
	public void testUnfinishedOptionNameShouldComplete() {
		MatcherAssert.assertThat(completionProvider.complete("basic --foo", 1), hasItems(Proposals.proposalThat(is("basic --fooble="))));
	}

	@Test
	// file | counter --name=<TAB> => nothing
	public void testInGenericOptionValueCantProposeAnything() {
		MatcherAssert.assertThat(completionProvider.complete("basic --expression=", 1), empty());
	}

	@Test
	// plum --use-ssl=<TAB> => propose true|false
	public void testValueHintForBooleans() {
		MatcherAssert.assertThat(completionProvider.complete("plum --use-ssl=", 1),
				hasItems(Proposals.proposalThat(is("plum --use-ssl=true")), Proposals.proposalThat(is("plum --use-ssl=false"))));
	}

	@Test
	// basic --enum-value=<TAB> => propose enum values
	public void testValueHintForEnums() {
		MatcherAssert.assertThat(completionProvider.complete("basic --expresso=", 1),
				hasItems(Proposals.proposalThat(is("basic --expresso=SINGLE")), Proposals.proposalThat(is("basic --expresso=DOUBLE"))));
	}

	@Test
	public void testUnrecognizedPrefixesDontBlowUp() {
		MatcherAssert.assertThat(completionProvider.complete("foo", 1), empty());
		MatcherAssert.assertThat(completionProvider.complete("foo --", 1), empty());
		MatcherAssert.assertThat(completionProvider.complete("http --notavalidoption", 1), empty());
		MatcherAssert.assertThat(completionProvider.complete("http --notavalidoption=", 1), empty());
		MatcherAssert.assertThat(completionProvider.complete("foo --some-option", 1), empty());
		MatcherAssert.assertThat(completionProvider.complete("foo --some-option=", 1), empty());
		MatcherAssert.assertThat(completionProvider.complete("foo --some-option=prefix", 1), empty());
	}

	/*
	 * basic --expresso=s<TAB> => must be single or double, no need to present
	 * "--expresso=s --other.prop"
	 */
	@Test
	public void testClosedSetValuesShouldBeExclusive() {
		MatcherAssert.assertThat(completionProvider.complete("basic --expresso=s", 1),
				not(hasItems(Proposals.proposalThat(startsWith("basic --expresso=s --fooble")))));
	}
}
