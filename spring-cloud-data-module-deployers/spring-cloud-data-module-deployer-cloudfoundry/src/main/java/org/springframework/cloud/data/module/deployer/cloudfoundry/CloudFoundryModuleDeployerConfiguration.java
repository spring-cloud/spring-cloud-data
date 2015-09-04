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

package org.springframework.cloud.data.module.deployer.cloudfoundry;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.data.module.deployer.ModuleDeployer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.security.oauth2.client.token.grant.password.ResourceOwnerPasswordResourceDetails;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestClientException;

/**
 * This class is present to make sure that Spring Authentication protocols for Cloud Foundry
 * controller API access are correctly wired.
 *
 * @author Ben Hale
 * @author Steve Powell
 * @author Eric Bottard
 */
@Configuration
@EnableConfigurationProperties(CloudFoundryModuleDeployerProperties.class)
public class CloudFoundryModuleDeployerConfiguration {

	@Autowired
	private CloudFoundryModuleDeployerProperties properties;

	@Bean
	public ModuleDeployer processModuleDeployer(
			CloudFoundryModuleDeploymentConverter converter,
			CloudFoundryApplicationOperations applicationOperations) {
		return new ApplicationModuleDeployer(properties, converter, applicationOperations);
	}

	@Bean
	public ModuleDeployer taskModuleDeployer(
			CloudFoundryModuleDeploymentConverter converter,
			CloudFoundryApplicationOperations applicationOperations) {
		// TODO: return appropriate task deployer
		return new ApplicationModuleDeployer(properties, converter, applicationOperations);
	}

	@Bean
	CloudControllerOperations cloudControllerOperations(ExtendedOAuth2RestOperations restOperations) {
		return new CloudControllerTemplate(properties.getApiEndpoint(), restOperations);
	}

	@Bean
	CloudFoundryApplicationOperations cloudFoundryApplicationOperations(
			CloudControllerOperations client) {
		return new CloudFoundryApplicationTemplate(client,
				properties.getOrganization(),
				properties.getSpace(),
				properties.getDomain());
	}

	@Bean
	CloudFoundryModuleDeploymentConverter cloudFoundryModuleDeploymentConverter() {
		return new CloudFoundryModuleDeploymentConverter();
	}

	@Bean
	ExtendedOAuth2RestOperations oauth2RestTemplate(
			OAuth2ClientContext clientContext,
			OAuth2ProtectedResourceDetails details) {
		return new ExtendedOAuth2RestTemplate(details, clientContext);
	}

	@Bean
	@ConfigurationProperties("security.oauth2.client")
	OAuth2ProtectedResourceDetails oAuth2ProtectedResourceDetails() {
		return new ResourceOwnerPasswordResourceDetails();
	}

	@Bean
	OAuth2ClientContext oauth2ClientContext() {
		return new DefaultOAuth2ClientContext(new DefaultAccessTokenRequest());
	}

	private static class ExtendedOAuth2RestTemplate extends OAuth2RestTemplate implements ExtendedOAuth2RestOperations {

		public ExtendedOAuth2RestTemplate(OAuth2ProtectedResourceDetails resource, OAuth2ClientContext context) {
			super(resource, context);
		}

		@Override
		public <T> T putForObject(URI uri, Object request, Class<T> responseType) throws RestClientException {
			RequestCallback requestCallback = httpEntityCallback(request, responseType);
			HttpMessageConverterExtractor<T> responseExtractor =
					new HttpMessageConverterExtractor<>(responseType, getMessageConverters());
			return this.execute(uri, HttpMethod.PUT, requestCallback, responseExtractor);
		}
	}
}
