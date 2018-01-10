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
package org.springframework.cloud.dataflow.server.stream;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.StreamAppDefinition;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.core.StreamDeployment;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.registry.support.ResourceUtils;
import org.springframework.cloud.dataflow.server.controller.NoSuchAppException;
import org.springframework.cloud.dataflow.server.controller.StreamDefinitionController;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.skipper.ReleaseNotFoundException;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.cloud.skipper.domain.ConfigValues;
import org.springframework.cloud.skipper.domain.Deployer;
import org.springframework.cloud.skipper.domain.Info;
import org.springframework.cloud.skipper.domain.InstallProperties;
import org.springframework.cloud.skipper.domain.InstallRequest;
import org.springframework.cloud.skipper.domain.Package;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
import org.springframework.cloud.skipper.domain.PackageMetadata;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.SpringCloudDeployerApplicationManifest;
import org.springframework.cloud.skipper.domain.SpringCloudDeployerApplicationManifestReader;
import org.springframework.cloud.skipper.domain.SpringCloudDeployerApplicationSpec;
import org.springframework.cloud.skipper.domain.Template;
import org.springframework.cloud.skipper.domain.UpgradeProperties;
import org.springframework.cloud.skipper.domain.UpgradeRequest;
import org.springframework.cloud.skipper.domain.UploadRequest;
import org.springframework.cloud.skipper.io.DefaultPackageWriter;
import org.springframework.cloud.skipper.io.PackageWriter;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toList;
import static org.springframework.cloud.dataflow.rest.SkipperStream.SKIPPER_DEFAULT_API_VERSION;
import static org.springframework.cloud.dataflow.rest.SkipperStream.SKIPPER_DEFAULT_KIND;
import static org.springframework.cloud.dataflow.rest.SkipperStream.SKIPPER_DEFAULT_MAINTAINER;
import static org.springframework.cloud.dataflow.rest.SkipperStream.SKIPPER_PACKAGE_NAME;
import static org.springframework.cloud.dataflow.rest.SkipperStream.SKIPPER_PACKAGE_VERSION;
import static org.springframework.cloud.dataflow.rest.SkipperStream.SKIPPER_PLATFORM_NAME;
import static org.springframework.cloud.dataflow.rest.SkipperStream.SKIPPER_REPO_NAME;

/**
 * Delegates to Skipper to deploy the stream.
 *
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Soby Chacko
 * @author Glenn Renfro
 * @author Christian Tzolov
 */
public class SkipperStreamDeployer implements StreamDeployer {

	private static Log logger = LogFactory.getLog(SkipperStreamDeployer.class);

	public static final String SPRING_CLOUD_DATAFLOW_STREAM_APP_TYPE = "spring.cloud.dataflow.stream.app.type";

	private final SkipperClient skipperClient;

	private final StreamDefinitionRepository streamDefinitionRepository;

	private final AppRegistryService appRegistryService;

	private final ForkJoinPool forkJoinPool;

	public SkipperStreamDeployer(SkipperClient skipperClient, StreamDefinitionRepository streamDefinitionRepository,
			AppRegistryService appRegistryService, ForkJoinPool forkJoinPool) {
		Assert.notNull(skipperClient, "SkipperClient can not be null");
		Assert.notNull(streamDefinitionRepository, "StreamDefinitionRepository can not be null");
		Assert.notNull(appRegistryService, "StreamDefinitionRepository can not be null");
		Assert.notNull(forkJoinPool, "ForkJoinPool can not be null");
		this.skipperClient = skipperClient;
		this.streamDefinitionRepository = streamDefinitionRepository;
		this.appRegistryService = appRegistryService;
		this.forkJoinPool = forkJoinPool;
	}

	public static List<AppStatus> deserializeAppStatus(String platformStatus) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.addMixIn(AppStatus.class, AppStatusMixin.class);
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			SimpleModule module = new SimpleModule("CustomModel", Version.unknownVersion());
			SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
			resolver.addMapping(AppInstanceStatus.class, AppInstanceStatusImpl.class);
			module.setAbstractTypes(resolver);
			mapper.registerModule(module);
			TypeReference<List<AppStatus>> typeRef = new TypeReference<List<AppStatus>>() {
			};
			List<AppStatus> result = mapper.readValue(platformStatus, typeRef);
			return result;
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Could not parse Skipper Platform Status JSON:" + platformStatus, e);
		}
	}

	@Override
	public String calculateStreamState(String streamName) {
		// TODO call out to skipper for stream state.
		return DeploymentState.unknown.toString();
	}

	@Override
	public Map<StreamDefinition, DeploymentState> state(List<StreamDefinition> streamDefinitions) {
		Map<StreamDefinition, DeploymentState> states = new HashMap<>();
		for (StreamDefinition streamDefinition : streamDefinitions) {
			try {
				Info info = this.skipperClient.status(streamDefinition.getName());
				List<AppStatus> appStatusList = deserializeAppStatus(info.getStatus().getPlatformStatus());
				Set<DeploymentState> deploymentStateList = appStatusList.stream().map(appStatus -> appStatus.getState())
						.collect(Collectors.toSet());
				DeploymentState aggregateState = StreamDefinitionController.aggregateState(deploymentStateList);
				states.put(streamDefinition, aggregateState);
			}
			catch (ReleaseNotFoundException e) {
				// ignore
			}
		}
		return states;
	}

	public Release deployStream(StreamDeploymentRequest streamDeploymentRequest) {

		validateAllAppsRegistered(streamDeploymentRequest);

		Map<String, String> streamDeployerProperties = streamDeploymentRequest.getStreamDeployerProperties();
		String packageVersion = streamDeployerProperties.get(SKIPPER_PACKAGE_VERSION);
		Assert.isTrue(StringUtils.hasText(packageVersion), "Package Version must be set");
		logger.info("Deploying Stream " + streamDeploymentRequest.getStreamName() + " using skipper.");
		String repoName = streamDeployerProperties.get(SKIPPER_REPO_NAME);
		repoName = (StringUtils.hasText(repoName)) ? (repoName) : "local";
		String platformName = streamDeployerProperties.get(SKIPPER_PLATFORM_NAME);
		platformName = (StringUtils.hasText(platformName)) ? platformName : "default";
		String packageName = streamDeployerProperties.get(SKIPPER_PACKAGE_NAME);
		packageName = (StringUtils.hasText(packageName)) ? packageName : streamDeploymentRequest.getStreamName();
		// Create the package .zip file to upload
		File packageFile = createPackageForStream(packageName, packageVersion, streamDeploymentRequest);
		// Upload the package
		UploadRequest uploadRequest = new UploadRequest();
		uploadRequest.setName(packageName);
		uploadRequest.setVersion(packageVersion);
		uploadRequest.setExtension("zip");
		uploadRequest.setRepoName(repoName); // TODO use from skipperDeploymentProperties if set.
		try {
			uploadRequest.setPackageFileAsBytes(Files.readAllBytes(packageFile.toPath()));
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Can't read packageFile " + packageFile, e);
		}
		skipperClient.upload(uploadRequest);
		// Install the package
		String streamName = streamDeploymentRequest.getStreamName();
		InstallRequest installRequest = new InstallRequest();
		PackageIdentifier packageIdentifier = new PackageIdentifier();
		packageIdentifier.setPackageName(packageName);
		packageIdentifier.setPackageVersion(packageVersion);
		packageIdentifier.setRepositoryName(repoName);
		installRequest.setPackageIdentifier(packageIdentifier);
		InstallProperties installProperties = new InstallProperties();
		installProperties.setPlatformName(platformName);
		installProperties.setReleaseName(streamName);
		installProperties.setConfigValues(new ConfigValues());
		installRequest.setInstallProperties(installProperties);
		Release release = skipperClient.install(installRequest);
		// TODO store releasename in deploymentIdRepository...
		return release;
	}

	private void validateAllAppsRegistered(StreamDeploymentRequest streamDeploymentRequest) {
		for (AppDeploymentRequest adr : streamDeploymentRequest.getAppDeploymentRequests()) {
			String version = ResourceUtils.getResourceVersion(adr.getResource());
			validateAppVersionIsRegistered(adr, version);
		}
	}

	public void validateAppVersionIsRegistered(AppDeploymentRequest appDeploymentRequest, String appVersion) {
		String name = appDeploymentRequest.getDefinition().getName();
		String appTypeString = appDeploymentRequest.getDefinition().getProperties()
				.get(SPRING_CLOUD_DATAFLOW_STREAM_APP_TYPE);
		ApplicationType applicationType = ApplicationType.valueOf(appTypeString);
		if (!this.appRegistryService.appExist(name, applicationType, appVersion)) {
			throw new IllegalStateException(String.format("The %s:%s:%s app is not registered!",
					name, appTypeString, appVersion));
		}
	}

	public void updateAppVersionIfChanged(StreamAppDefinition appDefinition,
			SpringCloudDeployerApplicationManifest appManifest) {

		String version = appManifest.getSpec().getVersion();
		String name = appDefinition.getRegisteredAppName();
		ApplicationType type = ApplicationType.valueOf(
				appManifest.getSpec().getApplicationProperties().get(SPRING_CLOUD_DATAFLOW_STREAM_APP_TYPE));
		String resourceUri = appManifest.getSpec().getResource();

		if (!this.appRegistryService.appExist(name, type, version)) {
			URI metadataUri = null; // TODO Skipper should provide the metadata URI as well
			this.appRegistryService.save(name, type, version, URI.create(resourceUri), metadataUri);
		}
	}

	private File createPackageForStream(String packageName, String packageVersion,
			StreamDeploymentRequest streamDeploymentRequest) {
		PackageWriter packageWriter = new DefaultPackageWriter();
		Package pkgtoWrite = createPackage(packageName, packageVersion, streamDeploymentRequest);
		Path tempPath;
		try {
			tempPath = Files.createTempDirectory("streampackages");
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Can't create temp diroectory");
		}
		File outputDirectory = tempPath.toFile();

		File zipFile = packageWriter.write(pkgtoWrite, outputDirectory);
		return zipFile;
	}

	private Package createPackage(String packageName, String packageVersion,
			StreamDeploymentRequest streamDeploymentRequest) {
		Package pkg = new Package();
		PackageMetadata packageMetadata = new PackageMetadata();
		packageMetadata.setApiVersion(SKIPPER_DEFAULT_API_VERSION);
		packageMetadata.setKind(SKIPPER_DEFAULT_KIND);
		packageMetadata.setName(packageName);
		packageMetadata.setVersion(packageVersion);
		packageMetadata.setMaintainer(SKIPPER_DEFAULT_MAINTAINER);
		packageMetadata.setDescription(streamDeploymentRequest.getDslText());
		pkg.setMetadata(packageMetadata);
		pkg.setDependencies(createDependentPackages(packageVersion, streamDeploymentRequest));
		return pkg;
	}

	private List<Package> createDependentPackages(String packageVersion,
			StreamDeploymentRequest streamDeploymentRequest) {
		List<Package> packageList = new ArrayList<>();
		for (AppDeploymentRequest appDeploymentRequest : streamDeploymentRequest.getAppDeploymentRequests()) {
			packageList.add(createDependentPackage(packageVersion, appDeploymentRequest));
		}
		return packageList;
	}

	private Package createDependentPackage(String packageVersion, AppDeploymentRequest appDeploymentRequest) {
		Package pkg = new Package();
		String packageName = appDeploymentRequest.getDefinition().getName();

		PackageMetadata packageMetadata = new PackageMetadata();
		packageMetadata.setApiVersion(SKIPPER_DEFAULT_API_VERSION);
		packageMetadata.setKind(SKIPPER_DEFAULT_KIND);
		packageMetadata.setName(packageName);
		packageMetadata.setVersion(packageVersion);
		packageMetadata.setMaintainer(SKIPPER_DEFAULT_MAINTAINER);

		pkg.setMetadata(packageMetadata);

		ConfigValues configValues = new ConfigValues();
		Map<String, Object> configValueMap = new HashMap<>();
		Map<String, Object> metadataMap = new HashMap<>();
		Map<String, Object> specMap = new HashMap<>();

		// Add version, including possible override via deploymentProperties - hack to store
		// version in cmdline args
		String version = ResourceUtils.getResourceVersion(appDeploymentRequest.getResource());
		if (appDeploymentRequest.getCommandlineArguments().size() == 1) {
			configValueMap.put("version", appDeploymentRequest.getCommandlineArguments().get(0));
		}
		else {
			configValueMap.put("version", version);
		}

		// Add metadata
		metadataMap.put("name", packageName);

		// Add spec
		String resourceWithoutVersion = ResourceUtils.getResourceWithoutVersion(appDeploymentRequest.getResource());
		specMap.put("resource", resourceWithoutVersion);
		specMap.put("applicationProperties", appDeploymentRequest.getDefinition().getProperties());
		specMap.put("deploymentProperties", appDeploymentRequest.getDeploymentProperties());

		// Add metadata and spec to top level map
		configValueMap.put("metadata", metadataMap);
		configValueMap.put("spec", specMap);

		DumperOptions dumperOptions = new DumperOptions();
		dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		dumperOptions.setPrettyFlow(true);
		Yaml yaml = new Yaml(dumperOptions);
		configValues.setRaw(yaml.dump(configValueMap));

		pkg.setConfigValues(configValues);
		pkg.setTemplates(createGenericTemplate());
		return pkg;

	}

	private List<Template> createGenericTemplate() {
		Template template = this.skipperClient.getSpringCloudDeployerApplicationTemplate();
		List<Template> templateList = new ArrayList<>();
		templateList.add(template);
		return templateList;
	}

	@Override
	public void undeployStream(String streamName) {
		this.skipperClient.delete(streamName);
	}

	@Override
	public List<AppStatus> getAppStatuses(Pageable pageable) throws ExecutionException, InterruptedException {
		List<String> skipperStreams = new ArrayList<>();
		Iterable<StreamDefinition> streamDefinitions = this.streamDefinitionRepository.findAll();
		for (StreamDefinition streamDefinition : streamDefinitions) {
			skipperStreams.add(streamDefinition.getName());
		}
		List<AppStatus> statuses = new ArrayList<>();
		statuses.addAll(getSkipperStatuses(pageable, skipperStreams).stream().flatMap(List::stream).collect(toList()));
		return statuses;
	}

	@Override
	public AppStatus getAppStatus(String id) {
		Iterable<StreamDefinition> streamDefinitions = this.streamDefinitionRepository.findAll();
		for (StreamDefinition streamDefinition : streamDefinitions) {
			List<AppStatus> appStatuses = skipperStatus(streamDefinition.getName());
			for (AppStatus appStatus : appStatuses) {
				if (appStatus.getDeploymentId().equals(id)) {
					return appStatus;
				}
			}
		}
		throw new NoSuchAppException(id);
	}

	@Override
	public RuntimeEnvironmentInfo environmentInfo() {
		// TODO To fix when https://github.com/spring-cloud/spring-cloud-skipper/issues/392
		throw new UnsupportedOperationException("EnvironmentInfo is not supported for Skipper mode");
	}

	@Override
	public StreamDeployment getStreamInfo(String streamName) {
		try {
			String manifest = this.manifest(streamName);
			List<SpringCloudDeployerApplicationManifest> appManifests =
					new SpringCloudDeployerApplicationManifestReader().read(manifest);
			Map<String, Map<String, String>> streamPropertiesMap = new HashMap<>();
			for (SpringCloudDeployerApplicationManifest applicationManifest : appManifests) {
				Map<String, String> versionAndDeploymentProperties = new HashMap<>();
				SpringCloudDeployerApplicationSpec spec = applicationManifest.getSpec();
				String applicationName = applicationManifest.getApplicationName();
				versionAndDeploymentProperties.putAll(spec.getDeploymentProperties());
				versionAndDeploymentProperties.put(spec.getResource(), spec.getVersion());
				streamPropertiesMap.put(applicationName, versionAndDeploymentProperties);
			}
			return new StreamDeployment(streamName, new JSONObject(streamPropertiesMap).toString());
		}
		catch (ReleaseNotFoundException e) {
			return new StreamDeployment(streamName);
		}
	}

	private List<List<AppStatus>> getSkipperStatuses(Pageable pageable, List<String> skipperStreamNames)
			throws ExecutionException, InterruptedException {
		//todo: Pageable values correspond to the number apps while Skipper status is done at the stream level.
		//todo: Fix the mismatch with Skipper stream apps' pagination
		return this.forkJoinPool
				.submit(() -> skipperStreamNames.stream().skip(pageable.getPageNumber() * pageable.getPageSize())
						.limit(pageable.getPageSize()).parallel().map(this::skipperStatus).collect(toList())).get();
	}

	private List<AppStatus> skipperStatus(String streamName) {
		List<AppStatus> appStatuses = new ArrayList<>();
		try {
			Info info = this.skipperClient.status(streamName);
			appStatuses.addAll(SkipperStreamDeployer.deserializeAppStatus(info.getStatus().getPlatformStatus()));
		}
		catch (Exception e) {
			// ignore as we query status for all the streams.
		}
		return appStatuses;
	}

	/**
	 * Update the stream identified by the PackageIdentifier and runtime configuration values.
	 * @param streamName the name of the stream to upgrade
	 * @param packageIdentifier the name of the package in skipper
	 * @param configYml the YML formatted configuration values to use when upgrading
	 */
	public Release upgradeStream(String streamName, PackageIdentifier packageIdentifier, String configYml) {
		UpgradeRequest upgradeRequest = new UpgradeRequest();
		upgradeRequest.setPackageIdentifier(packageIdentifier);
		UpgradeProperties upgradeProperties = new UpgradeProperties();
		ConfigValues configValues = new ConfigValues();
		configValues.setRaw(configYml);
		upgradeProperties.setConfigValues(configValues);
		upgradeProperties.setReleaseName(streamName);
		upgradeRequest.setUpgradeProperties(upgradeProperties);
		return this.skipperClient.upgrade(upgradeRequest);
	}

	/**
	 * Rollback the stream to a specific version
	 * @param streamName the name of the stream to rollback
	 * @param releaseVersion the version of the stream to rollback to
	 */
	public void rollbackStream(String streamName, int releaseVersion) {
		this.skipperClient.rollback(streamName, releaseVersion);
	}

	public String manifest(String name, int version) {
		return this.skipperClient.manifest(name, version);
	}

	public String manifest(String name) {
		return this.skipperClient.manifest(name);
	}

	public Collection<Release> history(String releaseName, int maxRevisions) {
		if (maxRevisions > 0) {
			return this.skipperClient.history(releaseName, String.valueOf(maxRevisions));
		}
		else {
			return this.skipperClient.history(releaseName).getContent();
		}
	}

	public Collection<Deployer> platformList() {
		return this.skipperClient.listDeployers().getContent();
	}
}
