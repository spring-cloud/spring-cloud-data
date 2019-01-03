/*
 * Copyright 2017-2018 the original author or authors.
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

package org.springframework.cloud.dataflow.server.service.impl;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import org.springframework.cloud.dataflow.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.configuration.metadata.BootApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.AuditActionType;
import org.springframework.cloud.dataflow.core.AuditOperationType;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.core.StreamDeployment;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.rest.SkipperStream;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.controller.support.InvalidStreamDefinitionException;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.impl.validation.DefaultStreamValidationService;
import org.springframework.cloud.dataflow.server.stream.SkipperStreamDeployer;
import org.springframework.cloud.dataflow.server.stream.StreamDeploymentRequest;
import org.springframework.cloud.dataflow.server.support.TestResourceUtils;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.skipper.domain.Deployer;
import org.springframework.cloud.skipper.domain.Manifest;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Ilayaperumal Gopinathan
 * @author Eric Bottard
 * @author Christian Tzolov
 * @author Gunnar Hillert
 */
@RunWith(SpringRunner.class)
public class DefaultStreamServiceTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private StreamDefinition streamDefinition1 = new StreamDefinition("test1", "time | log");
	private StreamDefinition streamDefinition2 = new StreamDefinition("test2", "time | log");
	private StreamDefinition streamDefinition3 = new StreamDefinition("test3", "time | log");
	private StreamDefinition streamDefinition4 = new StreamDefinition("test4", "time | log");

	private List<StreamDefinition> streamDefinitionList = new ArrayList<>();
	private List<StreamDefinition> streamDefinitions = new ArrayList<>();

	private StreamDefinitionRepository streamDefinitionRepository;
	private SkipperStreamDeployer skipperStreamDeployer;
	private AppDeploymentRequestCreator appDeploymentRequestCreator;

	private DefaultStreamService defaultStreamService;
	private AppRegistryService appRegistryService;
	private AuditRecordService auditRecordService;

	private DefaultStreamValidationService streamValidationService;

	@Before
	public void setupMock() {
		this.streamDefinitionRepository = mock(StreamDefinitionRepository.class);
		this.skipperStreamDeployer = mock(SkipperStreamDeployer.class);
		this.appRegistryService = mock(AppRegistryService.class);
		this.auditRecordService = mock(AuditRecordService.class); //FIXME
		this.appDeploymentRequestCreator = new AppDeploymentRequestCreator(this.appRegistryService,
				mock(CommonApplicationProperties.class),
				new BootApplicationConfigurationMetadataResolver());
		this.streamValidationService = mock(DefaultStreamValidationService.class);
		this.defaultStreamService = new DefaultStreamService(streamDefinitionRepository,
				this.skipperStreamDeployer, this.appDeploymentRequestCreator, this.streamValidationService,
				this.auditRecordService);
		this.streamDefinitionList.add(streamDefinition1);
		this.streamDefinitionList.add(streamDefinition2);
		this.streamDefinitionList.add(streamDefinition3);
		this.streamDefinitions.add(streamDefinition2);
		this.streamDefinitions.add(streamDefinition3);
		this.streamDefinitions.add(streamDefinition4);
		when(streamDefinitionRepository.findById("test2")).thenReturn(Optional.of(streamDefinition2));
	}

	@Test
	public void createStream() {
		when(this.streamValidationService.isRegistered("time", ApplicationType.source)).thenReturn(true);
		when(this.streamValidationService.isRegistered("log", ApplicationType.sink)).thenReturn(true);

		final StreamDefinition expectedStreamDefinition = new StreamDefinition("testStream", "time | log");
		when(streamDefinitionRepository.save(expectedStreamDefinition)).thenReturn(expectedStreamDefinition);

		this.defaultStreamService.createStream("testStream", "time | log", false);

		verify(this.streamValidationService).isRegistered("time", ApplicationType.source);
		verify(this.streamValidationService).isRegistered("log", ApplicationType.sink);
		verify(this.streamDefinitionRepository).save(expectedStreamDefinition);
		verify(this.auditRecordService).populateAndSaveAuditRecord(
				AuditOperationType.STREAM, AuditActionType.CREATE, "testStream", "time | log");

		verifyNoMoreInteractions(this.skipperStreamDeployer);
		verifyNoMoreInteractions(this.appRegistryService);
		verifyNoMoreInteractions(this.skipperStreamDeployer);
		verifyNoMoreInteractions(this.auditRecordService);
	}

	@Test
	public void createStreamWithMissingApps() {
		when(this.appRegistryService.appExist("time", ApplicationType.source)).thenReturn(false);
		when(this.appRegistryService.appExist("log", ApplicationType.sink)).thenReturn(false);

		thrown.expect(InvalidStreamDefinitionException.class);
		thrown.expectMessage("Application name 'time' with type 'source' does not exist in the app registry.\n" +
				"Application name 'log' with type 'sink' does not exist in the app registry.");

		this.defaultStreamService.createStream("testStream", "time | log", false);
	}

	@Test
	public void createStreamInvalidDsl() {
		when(this.appRegistryService.appExist("time", ApplicationType.source)).thenReturn(true);
		when(this.appRegistryService.appExist("log", ApplicationType.sink)).thenReturn(true);

		thrown.expect(InvalidStreamDefinitionException.class);
		thrown.expectMessage("Application name 'koza' with type 'app' does not exist in the app registry.");

		this.defaultStreamService.createStream("testStream", "koza", false);
	}

	@Test
	public void verifyUndeployStream() {
		StreamDefinition streamDefinition2 = new StreamDefinition("test2", "time | log");

		this.defaultStreamService.undeployStream(streamDefinition2.getName());
		verify(this.skipperStreamDeployer, times(1)).undeployStream(streamDefinition2.getName());
		verify(this.auditRecordService).populateAndSaveAuditRecord(
				AuditOperationType.STREAM, AuditActionType.UNDEPLOY, "test2", "time | log");
		verifyNoMoreInteractions(this.skipperStreamDeployer);
		verifyNoMoreInteractions(this.auditRecordService);
	}

	@Test
	public void verifyRollbackStream() throws Exception {
		StreamDefinition streamDefinition2 = new StreamDefinition("test2", "time | log");
		verifyNoMoreInteractions(this.skipperStreamDeployer);
		Release release = new Release();
		Manifest manifest = new Manifest();
		String rollbackReleaseManifestData = StreamUtils.copyToString(
				TestResourceUtils.qualifiedResource(getClass(), "rollbackManifest.yml").getInputStream(),
				Charset.defaultCharset());
		manifest.setData(rollbackReleaseManifestData);
		release.setManifest(manifest);
		when(this.skipperStreamDeployer.rollbackStream(streamDefinition2.getName(), 0)).thenReturn(release);
		this.defaultStreamService.rollbackStream(streamDefinition2.getName(), 0);
		verify(this.skipperStreamDeployer, times(1)).rollbackStream(streamDefinition2.getName(), 0);
	}

	@Test
	public void verifyStreamInfo() {
		StreamDefinition streamDefinition1 = new StreamDefinition("test1", "time | log");
		Map<String, String> deploymentProperties1 = new HashMap<>();
		deploymentProperties1.put("test1", "value1");
		Map<String, String> deploymentProperties2 = new HashMap<>();
		deploymentProperties2.put("test2", "value2");
		Map<String, Map<String, String>> streamDeploymentProperties = new HashMap<>();
		streamDeploymentProperties.put("time", deploymentProperties1);
		streamDeploymentProperties.put("log", deploymentProperties2);
		StreamDeployment streamDeployment1 = new StreamDeployment(streamDefinition1.getName(),
				new JSONObject(streamDeploymentProperties).toString());
		when(this.skipperStreamDeployer.getStreamInfo(streamDeployment1.getStreamName())).thenReturn(streamDeployment1);
		StreamDeployment streamDeployment = this.defaultStreamService.info("test1");
		Assert.assertTrue(streamDeployment.getStreamName().equals(streamDefinition1.getName()));
		Assert.assertTrue(streamDeployment.getDeploymentProperties().equals("{\"log\":{\"test2\":\"value2\"},\"time\":{\"test1\":\"value1\"}}"));
	}

	@Test
	public void verifyStreamState() {
		StreamDefinition streamDefinition = new StreamDefinition("myStream", "time|log");
		Map<StreamDefinition, DeploymentState> streamSates = new HashMap<>();
		streamSates.put(streamDefinition, DeploymentState.deployed);
		when(this.skipperStreamDeployer.streamsStates(eq(Arrays.asList(streamDefinition)))).thenReturn(streamSates);

		Map<StreamDefinition, DeploymentState> resultStates = this.defaultStreamService.state(Arrays.asList(streamDefinition));

		verify(this.skipperStreamDeployer, times(1)).streamsStates(any());

		Assert.assertNotNull(resultStates);
		Assert.assertEquals(1, resultStates.size());
		Assert.assertEquals(DeploymentState.deployed, resultStates.get(streamDefinition));
	}


	@Test
	public void verifyStreamHistory() {
		Release release = new Release();
		release.setName("RELEASE666");
		when(this.skipperStreamDeployer.history(eq("myStream"))).thenReturn(Arrays.asList(release));

		Collection<Release> releases = this.defaultStreamService.history("myStream");

		verify(this.skipperStreamDeployer, times(1)).history(eq("myStream"));

		Assert.assertNotNull(releases);
		Assert.assertEquals(1, releases.size());
		Assert.assertEquals("RELEASE666", releases.iterator().next().getName());
	}

	@Test
	public void verifyStreamPlatformList() {
		Deployer deployer = new Deployer("testDeployer", "testType", null);
		when(this.skipperStreamDeployer.platformList()).thenReturn(Arrays.asList(deployer));
		Collection<Deployer> deployers = this.defaultStreamService.platformList();

		verify(this.skipperStreamDeployer, times(1)).platformList();

		Assert.assertNotNull(deployers);
		Assert.assertEquals(1, deployers.size());
		Assert.assertEquals("testDeployer", deployers.iterator().next().getName());
	}

	@Test
	public void verifyStreamManifest() {
		when(this.skipperStreamDeployer.manifest(eq("myManifest"), eq(666))).thenReturn("MANIFEST666");

		String manifest = this.defaultStreamService.manifest("myManifest", 666);

		verify(this.skipperStreamDeployer, times(1)).manifest(anyString(), anyInt());
		Assert.assertEquals("MANIFEST666", manifest);
	}

	@Test
	public void testStreamDeployWithDefaultPackageVersion() {
		Map<String, String> deploymentProperties = new HashMap<>();

		ArgumentCaptor<StreamDeploymentRequest> argumentCaptor = this.testStreamDeploy(deploymentProperties);

		Assert.assertEquals(DefaultStreamService.DEFAULT_SKIPPER_PACKAGE_VERSION,
				argumentCaptor.getValue().getStreamDeployerProperties().get(SkipperStream.SKIPPER_PACKAGE_VERSION));
	}

	@Test
	public void testStreamDeployWithPreDefinedPackageVersion() {
		Map<String, String> deploymentProperties = new HashMap<>();
		deploymentProperties.put(SkipperStream.SKIPPER_PACKAGE_VERSION, "2.0.0");

		ArgumentCaptor<StreamDeploymentRequest> argumentCaptor = this.testStreamDeploy(deploymentProperties);

		Assert.assertEquals("2.0.0",
				argumentCaptor.getValue().getStreamDeployerProperties().get(SkipperStream.SKIPPER_PACKAGE_VERSION));
	}

	public ArgumentCaptor<StreamDeploymentRequest> testStreamDeploy(Map<String, String> deploymentProperties) {
		appDeploymentRequestCreator = mock(AppDeploymentRequestCreator.class);
		skipperStreamDeployer = mock(SkipperStreamDeployer.class);
		streamDefinitionRepository = mock(StreamDefinitionRepository.class);

		this.defaultStreamService = new DefaultStreamService(streamDefinitionRepository,
				this.skipperStreamDeployer, this.appDeploymentRequestCreator,
				this.streamValidationService, this.auditRecordService);

		StreamDefinition streamDefinition = new StreamDefinition("test1", "time | log");

		when(streamDefinitionRepository.findById(streamDefinition.getName())).thenReturn(Optional.of(streamDefinition));

		List<AppDeploymentRequest> appDeploymentRequests = Arrays.asList(mock(AppDeploymentRequest.class));
		when(appDeploymentRequestCreator.createRequests(streamDefinition, new HashMap<>()))
				.thenReturn(appDeploymentRequests);

		this.defaultStreamService.deployStream(streamDefinition1.getName(), deploymentProperties);

		ArgumentCaptor<StreamDeploymentRequest> argumentCaptor = ArgumentCaptor.forClass(StreamDeploymentRequest.class);
		verify(skipperStreamDeployer, times(1)).deployStream(argumentCaptor.capture());

		return argumentCaptor;
	}
}
