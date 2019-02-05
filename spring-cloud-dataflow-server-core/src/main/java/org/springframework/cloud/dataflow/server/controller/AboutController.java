/*
 * Copyright 2017-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.common.security.support.SecurityStateBean;
import org.springframework.cloud.dataflow.core.Launcher;
import org.springframework.cloud.dataflow.rest.resource.about.AboutResource;
import org.springframework.cloud.dataflow.rest.resource.about.Dependency;
import org.springframework.cloud.dataflow.rest.resource.about.FeatureInfo;
import org.springframework.cloud.dataflow.rest.resource.about.GrafanaInfo;
import org.springframework.cloud.dataflow.rest.resource.about.RuntimeEnvironment;
import org.springframework.cloud.dataflow.rest.resource.about.RuntimeEnvironmentDetails;
import org.springframework.cloud.dataflow.rest.resource.about.SecurityInfo;
import org.springframework.cloud.dataflow.rest.resource.about.VersionInfo;
import org.springframework.cloud.dataflow.server.config.GrafanaInfoProperties;
import org.springframework.cloud.dataflow.server.config.VersionInfoProperties;
import org.springframework.cloud.dataflow.server.config.features.FeaturesProperties;
import org.springframework.cloud.dataflow.server.job.LauncherRepository;
import org.springframework.cloud.dataflow.server.stream.StreamDeployer;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * REST controller that provides meta information regarding the dataflow server and its
 * deployers.
 *
 * @author Gunnar Hillert
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 */
@RestController
@RequestMapping("/about")
@ExposesResourceFor(AboutResource.class)
@EnableConfigurationProperties(GrafanaInfoProperties.class)
public class AboutController {

	private static final Logger logger = LoggerFactory.getLogger(AboutController.class);

	private final StreamDeployer streamDeployer;

	private final FeaturesProperties featuresProperties;

	private final VersionInfoProperties versionInfoProperties;

	private final SecurityStateBean securityStateBean;

	@Value("${security.oauth2.client.client-id:#{null}}")
	private String oauthClientId;

	@Value("${info.app.name:#{null}}")
	private String implementationName;

	@Value("${info.app.version:#{null}}")
	private String implementationVersion;

	private LauncherRepository launcherRepository;

	private GrafanaInfoProperties grafanaProperties;

	public AboutController(StreamDeployer streamDeployer, LauncherRepository launcherRepository, FeaturesProperties featuresProperties,
			VersionInfoProperties versionInfoProperties, SecurityStateBean securityStateBean, GrafanaInfoProperties grafanaInfoProperties) {
		this.streamDeployer = streamDeployer;
		this.launcherRepository = launcherRepository;
		this.featuresProperties = featuresProperties;
		this.versionInfoProperties = versionInfoProperties;
		this.securityStateBean = securityStateBean;
		this.grafanaProperties = grafanaInfoProperties;
	}

	/**
	 * Return meta information about the dataflow server.
	 *
	 * @return Detailed information about the enabled features, versions of implementation
	 * libraries, and security configuration
	 */
	@RequestMapping(method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.OK)
	public AboutResource getAboutResource() {
		final AboutResource aboutResource = new AboutResource();
		final FeatureInfo featureInfo = new FeatureInfo();
		featureInfo.setStreamsEnabled(featuresProperties.isStreamsEnabled());
		featureInfo.setTasksEnabled(featuresProperties.isTasksEnabled());
		featureInfo.setSchedulerEnabled(featuresProperties.isSchedulesEnabled());
		featureInfo.setGrafanaEnabled(this.grafanaProperties.isGrafanaEnabled());

		final VersionInfo versionInfo = getVersionInfo();

		aboutResource.setFeatureInfo(featureInfo);
		aboutResource.setVersionInfo(versionInfo);

		final boolean authenticationEnabled = securityStateBean.isAuthenticationEnabled();

		final SecurityInfo securityInfo = new SecurityInfo();
		securityInfo.setAuthenticationEnabled(authenticationEnabled);

		if (authenticationEnabled && SecurityContextHolder.getContext() != null) {
			final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (!(authentication instanceof AnonymousAuthenticationToken)) {
				securityInfo.setAuthenticated(authentication.isAuthenticated());
				securityInfo.setUsername(authentication.getName());

				for (Object authority : authentication.getAuthorities()) {
					final GrantedAuthority grantedAuthority = (GrantedAuthority) authority;
					securityInfo.addRole(grantedAuthority.getAuthority());
				}
			}
		}

		aboutResource.setSecurityInfo(securityInfo);

		final RuntimeEnvironment runtimeEnvironment = new RuntimeEnvironment();

		if (!authenticationEnabled || (authenticationEnabled && SecurityContextHolder.getContext().getAuthentication() != null)) {
			if (this.streamDeployer != null) {
				try {
					final RuntimeEnvironmentInfo deployerEnvironmentInfo = this.streamDeployer.environmentInfo();
					final RuntimeEnvironmentDetails deployerInfo = new RuntimeEnvironmentDetails();

					deployerInfo.setDeployerImplementationVersion(deployerEnvironmentInfo.getImplementationVersion());
					deployerInfo.setDeployerName(deployerEnvironmentInfo.getImplementationName());
					deployerInfo.setDeployerSpiVersion(deployerEnvironmentInfo.getSpiVersion());
					deployerInfo.setJavaVersion(deployerEnvironmentInfo.getJavaVersion());
					deployerInfo.setPlatformApiVersion(deployerEnvironmentInfo.getPlatformApiVersion());
					deployerInfo.setPlatformClientVersion(deployerEnvironmentInfo.getPlatformClientVersion());
					deployerInfo.setPlatformHostVersion(deployerEnvironmentInfo.getPlatformHostVersion());
					deployerInfo.setPlatformSpecificInfo(deployerEnvironmentInfo.getPlatformSpecificInfo());
					deployerInfo.setPlatformHostVersion(deployerEnvironmentInfo.getPlatformHostVersion());
					deployerInfo.setPlatformType(deployerEnvironmentInfo.getPlatformType());
					deployerInfo.setSpringBootVersion(deployerEnvironmentInfo.getSpringBootVersion());
					deployerInfo.setSpringVersion(deployerEnvironmentInfo.getSpringVersion());

					runtimeEnvironment.setAppDeployer(deployerInfo);
				}
				catch (ResourceAccessException rae) {
					logger.warn("Skipper Server is not accessible", rae);
				}
			}
			if (this.launcherRepository != null) {
				final List<RuntimeEnvironmentDetails> taskLauncherInfoList = new ArrayList<RuntimeEnvironmentDetails>();
				for (Launcher launcher : this.launcherRepository.findAll()) {
					TaskLauncher taskLauncher = launcher.getTaskLauncher();
					RuntimeEnvironmentDetails taskLauncherInfo = new RuntimeEnvironmentDetails();
					final RuntimeEnvironmentInfo taskLauncherEnvironmentInfo = taskLauncher.environmentInfo();
					taskLauncherInfo.setDeployerImplementationVersion(taskLauncherEnvironmentInfo.getImplementationVersion());
					taskLauncherInfo.setDeployerName(taskLauncherEnvironmentInfo.getImplementationName());
					taskLauncherInfo.setDeployerSpiVersion(taskLauncherEnvironmentInfo.getSpiVersion());
					taskLauncherInfo.setJavaVersion(taskLauncherEnvironmentInfo.getJavaVersion());
					taskLauncherInfo.setPlatformApiVersion(taskLauncherEnvironmentInfo.getPlatformApiVersion());
					taskLauncherInfo.setPlatformClientVersion(taskLauncherEnvironmentInfo.getPlatformClientVersion());
					taskLauncherInfo.setPlatformHostVersion(taskLauncherEnvironmentInfo.getPlatformHostVersion());
					taskLauncherInfo.setPlatformSpecificInfo(taskLauncherEnvironmentInfo.getPlatformSpecificInfo());
					taskLauncherInfo.setPlatformHostVersion(taskLauncherEnvironmentInfo.getPlatformHostVersion());
					taskLauncherInfo.setPlatformType(taskLauncherEnvironmentInfo.getPlatformType());
					taskLauncherInfo.setSpringBootVersion(taskLauncherEnvironmentInfo.getSpringBootVersion());
					taskLauncherInfo.setSpringVersion(taskLauncherEnvironmentInfo.getSpringVersion());
					taskLauncherInfoList.add(taskLauncherInfo);
				}
				runtimeEnvironment.setTaskLaunchers(taskLauncherInfoList);
			}
		}
		aboutResource.setRuntimeEnvironment(runtimeEnvironment);

		if (this.grafanaProperties.isGrafanaEnabled()) {
			final GrafanaInfo grafanaInfo = new GrafanaInfo();
			grafanaInfo.setUrl(this.grafanaProperties.getUrl());
			grafanaInfo.setRefreshInterval(this.grafanaProperties.getRefreshInterval());
			aboutResource.setGrafanaInfo(grafanaInfo);
		}

		aboutResource.add(ControllerLinkBuilder.linkTo(AboutController.class).withSelfRel());

		return aboutResource;
	}

	private VersionInfo getVersionInfo() {
		final VersionInfo versionInfo = new VersionInfo();

		updateDependency(versionInfo.getDashboard(),
				versionInfoProperties.getDependencies().getSpringCloudDataflowDashboard());
		updateDependency(versionInfo.getImplementation(),
				versionInfoProperties.getDependencies().getSpringCloudDataflowImplementation());
		updateDependency(versionInfo.getCore(),
				versionInfoProperties.getDependencies().getSpringCloudDataflowCore());
		updateDependency(versionInfo.getShell(),
				versionInfoProperties.getDependencies().getSpringCloudDataflowShell());

		if (versionInfoProperties.getDependencyFetch().isEnabled()) {
			versionInfo.getShell().setChecksumSha1(getChecksum(
					versionInfoProperties.getDependencies().getSpringCloudDataflowShell().getChecksumSha1(),
					versionInfoProperties.getDependencies().getSpringCloudDataflowShell().getChecksumSha1Url(),
					versionInfoProperties.getDependencies().getSpringCloudDataflowShell().getVersion()));
			versionInfo.getShell().setChecksumSha256(getChecksum(
					versionInfoProperties.getDependencies().getSpringCloudDataflowShell().getChecksumSha256(),
					versionInfoProperties.getDependencies().getSpringCloudDataflowShell().getChecksumSha256Url(),
					versionInfoProperties.getDependencies().getSpringCloudDataflowShell().getVersion()));
		}
		return versionInfo;
	}

	private String getChecksum(String defaultValue, String url,
			String version) {
		String result = defaultValue;
		if (result == null && StringUtils.hasText(url)) {
			CloseableHttpClient httpClient = HttpClients.custom()
					.setSSLHostnameVerifier(new NoopHostnameVerifier())
					.build();
			HttpComponentsClientHttpRequestFactory requestFactory
					= new HttpComponentsClientHttpRequestFactory();
			requestFactory.setHttpClient(httpClient);
			url = constructUrl(url, version);
			try {
				ResponseEntity<String> response
						= new RestTemplate(requestFactory).exchange(
						url, HttpMethod.GET, null, String.class);
				if (response.getStatusCode().equals(HttpStatus.OK)) {
					result = response.getBody();
				}
			}
			catch (HttpClientErrorException httpException) {
				// no action necessary set result to undefined
				logger.debug("Didn't retrieve checksum because", httpException);
			}
		}
		return result;
	}

	private void updateDependency(Dependency dependency, VersionInfoProperties.DependencyAboutInfo dependencyAboutInfo) {
		dependency.setName(dependencyAboutInfo.getName());
		if (dependencyAboutInfo.getUrl() != null) {
			dependency.setUrl(constructUrl(dependencyAboutInfo.getUrl(),
					dependencyAboutInfo.getVersion()));
		}
		dependency.setVersion(dependencyAboutInfo.getVersion());
	}

	private String constructUrl(String url, String version) {
		final String VERSION_TAG = "{version}";
		final String REPOSITORY_TAG = "{repository}";
		if (url.contains(VERSION_TAG)) {
			url = StringUtils.replace(url, VERSION_TAG, version);
			url = StringUtils.replace(url, REPOSITORY_TAG, repoSelector(version));
		}
		return url;
	}

	private String repoSelector(String version) {
		final String REPO_SNAPSHOT_ROOT = "https://repo.spring.io/libs-snapshot";
		final String REPO_MILESTONE_ROOT = "https://repo.spring.io/libs-milestone";
		final String REPO_RELEASE_ROOT = "https://repo.spring.io/libs-release";
		final String MAVEN_ROOT = "https://repo1.maven.org/maven2";

		String result = MAVEN_ROOT;
		if (version.endsWith("BUILD-SNAPSHOT")) {
			result = REPO_SNAPSHOT_ROOT;
		}
		else if (version.contains(".M")) {
			result = REPO_MILESTONE_ROOT;
		}
		else if (version.contains(".RC")) {
			result = REPO_MILESTONE_ROOT;
		}
		else if (version.contains(".RELEASE")) {
			result = REPO_RELEASE_ROOT;
		}
		return result;
	}
}
