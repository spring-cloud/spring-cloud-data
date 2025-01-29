/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.cloud.skipper.server.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.skipper.domain.CancelRequest;
import org.springframework.cloud.skipper.domain.CancelResponse;
import org.springframework.cloud.skipper.domain.InstallProperties;
import org.springframework.cloud.skipper.domain.InstallRequest;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
import org.springframework.cloud.skipper.domain.PackageMetadata;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.StatusCode;
import org.springframework.cloud.skipper.domain.UpgradeProperties;
import org.springframework.cloud.skipper.domain.UpgradeRequest;
import org.springframework.cloud.skipper.server.AbstractMockMvcTests;
import org.springframework.cloud.skipper.server.config.SkipperServerProperties;
import org.springframework.cloud.skipper.server.repository.jpa.PackageMetadataRepository;
import org.springframework.cloud.skipper.server.repository.jpa.ReleaseRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @author Corneil du Plessis
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public abstract class AbstractControllerTests extends AbstractMockMvcTests {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	protected PackageMetadataRepository packageMetadataRepository;

	@Autowired
	protected ReleaseRepository releaseRepository;

	@Autowired
	protected SkipperServerProperties skipperServerProperties;

	@BeforeEach
	public void cleanupReleaseRepository() {
		this.releaseRepository.deleteAll();
	}

	@AfterEach
	public void cleanupReleases() throws Exception {
		// Add a sleep for now to give the local deployer a chance to install the app.
		// This
		// should go away once we introduce spring state machine.
		logger.info("Test Clean up - deleting all releases.");
		Thread.sleep(5000);
		for (Release release : releaseRepository.findAll()) {
			if (release.getInfo().getStatus().getStatusCode() != StatusCode.DELETED) {
				try {
					mockMvc.perform(delete("/api/release/" + release.getName()))
							.andExpect(status().isOk()).andReturn();
				}
				catch (Throwable e) {
					logger.warn("Can not delete release {}-v{}, as it has not yet deployed.", release.getName(),
							release.getVersion());
				}
			}
		}
	}

	protected Release install(String packageName, String packageVersion, String releaseName) throws Exception {
		// Deploy
		InstallProperties installProperties = createInstallProperties(releaseName);
		PackageMetadata packageMetadata = this.packageMetadataRepository.findByNameAndVersionByMaxRepoOrder(packageName,
				packageVersion);
		assertThat(packageMetadata).isNotNull();
		MvcResult result = mockMvc.perform(post("/api/package/install/" + packageMetadata.getId())
				.content(convertObjectToJson(installProperties))).andExpect(status().isCreated()).andReturn();

		Release release = convertContentToRelease(result.getResponse().getContentAsString());
		assertReleaseIsDeployedSuccessfully(releaseName, release.getVersion());
		Release deployedRelease = this.releaseRepository.findByNameAndVersion(releaseName, release.getVersion());
		commonReleaseAssertions(releaseName, packageMetadata, deployedRelease);
		return deployedRelease;
	}

	protected Release installPackage(InstallRequest installRequest) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/package/install")
				.content(convertObjectToJson(installRequest))).andExpect(status().isCreated()).andReturn();
		Release release = convertContentToRelease(result.getResponse().getContentAsString());
		assertReleaseIsDeployedSuccessfully(release.getName(), release.getVersion());
		String releaseName = installRequest.getInstallProperties().getReleaseName();
		Release deployedRelease = this.releaseRepository.findByNameAndVersion(releaseName, release.getVersion());
		PackageMetadata packageMetadata = this.packageMetadataRepository.findByNameAndVersionByMaxRepoOrder(
				installRequest.getPackageIdentifier().getPackageName(),
				installRequest.getPackageIdentifier().getPackageVersion());
		commonReleaseAssertions(releaseName, packageMetadata, deployedRelease);
		return deployedRelease;
	}

	protected Release upgrade(String packageName, String packageVersion, String releaseName) throws Exception {
		return upgrade(packageName, packageVersion, releaseName, true);
	}

	protected Release upgrade(String packageName, String packageVersion, String releaseName, boolean wait)
			throws Exception {
		UpgradeRequest upgradeRequest = new UpgradeRequest();
		UpgradeProperties upgradeProperties = createUpdateProperties(releaseName);
		PackageIdentifier packageIdentifier = new PackageIdentifier();
		packageIdentifier.setPackageName(packageName);
		packageIdentifier.setPackageVersion(packageVersion);
		upgradeRequest.setPackageIdentifier(packageIdentifier);
		upgradeRequest.setUpgradeProperties(upgradeProperties);
		PackageMetadata updatePackageMetadata = this.packageMetadataRepository.findByNameAndVersionByMaxRepoOrder(
				packageName,
				packageVersion);
		assertThat(updatePackageMetadata).isNotNull();
		MvcResult result = mockMvc.perform(post("/api/release/upgrade")
				.content(convertObjectToJson(upgradeRequest))).andExpect(status().isCreated()).andReturn();
		Release release = convertContentToRelease(result.getResponse().getContentAsString());
		if (wait) {
			assertReleaseIsDeployedSuccessfully(releaseName, release.getVersion());
		}
		Release updatedRelease = this.releaseRepository.findByNameAndVersion(releaseName, release.getVersion());
		if (wait) {
			commonReleaseAssertions(releaseName, updatePackageMetadata, updatedRelease);
		}
		return updatedRelease;
	}

	protected Release rollback(String releaseName, int releaseVersion) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/release/rollback/" + releaseName + "/" + releaseVersion)).andExpect(status().isCreated()).andReturn();
		Release release = convertContentToRelease(result.getResponse().getContentAsString());
		assertReleaseIsDeployedSuccessfully(releaseName, release.getVersion());
		Release updatedRelease = this.releaseRepository.findByNameAndVersion(releaseName, release.getVersion());
		return updatedRelease;
	}

	protected void cancel(String releaseName, int expectStatus, boolean accepted) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/release/cancel").content(convertObjectToJson(new CancelRequest(releaseName)))).andExpect(status().is(expectStatus)).andReturn();
		CancelResponse response = convertContentToCancelResponse(result.getResponse().getContentAsString());
		assertThat(response.getAccepted()).isEqualTo(accepted);
	}

	protected void commonReleaseAssertions(String releaseName, PackageMetadata packageMetadata,
			Release deployedRelease) {
		assertThat(deployedRelease.getName()).isEqualTo(releaseName);
		assertThat(deployedRelease.getPlatformName()).isEqualTo("default");
		assertThat(deployedRelease.getPkg().getMetadata()).usingRecursiveAssertion().
			ignoringFields("id", "origin", "packageFile", "objectVersion").isEqualTo(packageMetadata);
		assertThat(deployedRelease.getPkg().getMetadata()).isEqualTo(packageMetadata);
		assertThat(deployedRelease.getInfo().getStatus().getStatusCode()).isEqualTo(StatusCode.DEPLOYED);
	}

}
