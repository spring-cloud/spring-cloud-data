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

import java.util.List;

import org.springframework.web.client.RestClientException;

/**
 * Implementation of high-level operations on applications, limited to those
 * operations required by the {@link ApplicationModuleDeployer}.
 *
 * @author Steve Powell
 * @author Eric Bottard
 */
class CloudFoundryApplicationTemplate implements CloudFoundryApplicationOperations {

	private static final String DEFAULT_BUILDPACK = "https://github.com/cloudfoundry/java-buildpack.git#69abec6d2726f73a22339caa6ae7739f060002e4";

	private static final int DEFAULT_MEMORY = 1024; // megabytes

	private final CloudControllerOperations client;

	private final String spaceId;

	private final String domainId;

	CloudFoundryApplicationTemplate(CloudControllerOperations client, String organizationName, String spaceName, String domain) {
		this.client = client;
		this.spaceId = getSpaceId(organizationName, spaceName);
		this.domainId = getDomainId(domain);
	}

	@Override
	public DeleteApplicationResults deleteApplication(DeleteApplicationParameters parameters) {
		// Check that application actually exists
		ListApplicationsRequest listRequest = new ListApplicationsRequest()
				.withSpaceId(this.spaceId)
				.withName(parameters.getName());

		List<ResourceResponse<ApplicationEntity>> applications = this.client.listApplications(listRequest).getResources();

		if (applications.isEmpty()) {
			return new DeleteApplicationResults().withFound(false);
		}
		String appId = applications.get(0).getMetadata().getId();

		// Delete the associated route
		ListRoutesRequest listRoutesRequest = new ListRoutesRequest()
				.withDomainId(this.domainId)
				.withHost(parameters.getName().replaceAll("[^A-Za-z0-9]", "-"));
		ListRoutesResponse listRoutesResponse = this.client.listRoutes(listRoutesRequest);
		for (ResourceResponse<RouteEntity> resource : listRoutesResponse.getResources()) {
			String routeId = resource.getMetadata().getId();
			this.client.unmapRoute(new RouteMappingRequest()
							.withAppId(appId)
							.withRouteId(routeId)
			);
			this.client.deleteRoute(new DeleteRouteRequest().withId(routeId));
		}


		// Then, unbind any services
		ListServiceBindingsRequest listServiceBindingsRequest = new ListServiceBindingsRequest()
				.withAppId(appId);
		List<ResourceResponse<ServiceBindingEntity>> serviceBindings = this.client.listServiceBindings(listServiceBindingsRequest).getResources();
		for (ResourceResponse<ServiceBindingEntity> serviceBinding : serviceBindings) {
			this.client.removeServiceBinding(new RemoveServiceBindingRequest()
							.withAppId(appId)
							.withBindingId(serviceBinding.getMetadata().getId())
			);
		}

		// Then, perform the actual deletion
		DeleteApplicationRequest deleteRequest = new DeleteApplicationRequest()
				.withId(appId);

		DeleteApplicationResponse response = this.client.deleteApplication(deleteRequest);

		return new DeleteApplicationResults().withFound(true).withDeleted(response.isDeleted());
	}

	@Override
	public GetApplicationsStatusResults getApplicationsStatus(GetApplicationsStatusParameters parameters) {
		ListApplicationsRequest listRequest = new ListApplicationsRequest()
				.withSpaceId(this.spaceId);

		if (parameters.getName() != null) {
			listRequest.withName(parameters.getName());
		}

		List<ResourceResponse<ApplicationEntity>> applications = this.client.listApplications(listRequest).getResources();
		if (applications.isEmpty()) {
			return new GetApplicationsStatusResults();
		}

		GetApplicationsStatusResults response = new GetApplicationsStatusResults();
		for (ResourceResponse<ApplicationEntity> application : applications) {
			String applicationId = application.getMetadata().getId();
			String applicationName = application.getEntity().getName();
			String applicationState = application.getEntity().getState();

			// TODO: decide what to do here
			if (!"STARTED".equals(applicationState)) {
				response.withApplication(applicationName, new ApplicationStatus());
			}
			else {
				GetApplicationStatisticsRequest statsRequest = new GetApplicationStatisticsRequest()
						.withId(applicationId);

				GetApplicationStatisticsResponse statsResponse = this.client.getApplicationStatistics(statsRequest);
				response.withApplication(applicationName, new ApplicationStatus()
						.withId(applicationId)
						.withInstances(statsResponse));
			}
		}

		return response;
	}

	@Override
	public PushBindAndStartApplicationResults pushBindAndStartApplication(PushBindAndStartApplicationParameters parameters) {
		PushBindAndStartApplicationResults pushResults = new PushBindAndStartApplicationResults();

		// Create app
		CreateApplicationRequest createRequest = new CreateApplicationRequest()
				.withSpaceId(this.spaceId)
				.withName(parameters.getName())
				.withInstances(1) // TODO: use the correct instances value
				.withBuildpack(DEFAULT_BUILDPACK)
				.withMemory(DEFAULT_MEMORY)
				.withState("STOPPED")
				.withEnvironment(parameters.getEnvironment());

		CreateApplicationResponse createResponse;
		try {
			createResponse = this.client.createApplication(createRequest);
		}
		catch (RestClientException rce) {
			return pushResults.withCreateSucceeded(false);
		}

		// Bind service instances
		String appId = createResponse.getMetadata().getId();
		for (String serviceInstanceName : parameters.getServiceInstanceNames()) {
			ListServiceInstancesRequest listServiceInstancesRequest = new ListServiceInstancesRequest()
					.withName(serviceInstanceName)
					.withSpaceId(this.spaceId);
			List<ResourceResponse<NamedEntity>> listServiceInstances = this.client.listServiceInstances(listServiceInstancesRequest).getResources();
			for (ResourceResponse<NamedEntity> serviceInstanceResource : listServiceInstances) {
				CreateServiceBindingRequest createServiceBindingRequest = new CreateServiceBindingRequest()
						.withAppId(appId)
						.withServiceInstanceId(serviceInstanceResource.getMetadata().getId());
				this.client.createServiceBinding(createServiceBindingRequest);
			}
		}

		// Create and map route
		CreateRouteRequest createRouteRequest = new CreateRouteRequest()
				.withDomainId(this.domainId)
				.withSpaceId(this.spaceId)
				.withHost(parameters.getName().replaceAll("[^A-Za-z0-9]", "-"));
		CreateRouteResponse createRouteResponse = this.client.createRoute(createRouteRequest);

		RouteMappingRequest mapRouteRequest = new RouteMappingRequest()
				.withRouteId(createRouteResponse.getMetadata().getId())
				.withAppId(appId);
		this.client.mapRoute(mapRouteRequest);


		// Upload the bits for the app
		UploadBitsRequest uploadBitsRequest = new UploadBitsRequest()
				.withId(appId)
				.withResource(parameters.getResource());

		UploadBitsResponse uploadBitsResponse = this.client.uploadBits(uploadBitsRequest);

		// Start the app
		UpdateApplicationRequest updateRequest = new UpdateApplicationRequest()
				.withId(appId)
				.withState("STARTED");
		UpdateApplicationResponse updateResponse = this.client.updateApplication(updateRequest);

		return pushResults.withCreateSucceeded(true);
	}

	private String getSpaceId(String organizationName, String spaceName) {
		ListOrganizationsRequest organizationsRequest = new ListOrganizationsRequest()
				.withName(organizationName);

		List<ResourceResponse<NamedEntity>> orgs = this.client.listOrganizations(organizationsRequest).getResources();
		if (orgs.size() != 1) {
			return null;
		}
		String orgId = orgs.get(0).getMetadata().getId();

		ListSpacesRequest spacesRequest = new ListSpacesRequest()
				.withOrgId(orgId)
				.withName(spaceName);

		List<ResourceResponse<NamedEntity>> spaces = this.client.listSpaces(spacesRequest).getResources();
		if (spaces.size() != 1) {
			return null;
		}
		return spaces.get(0).getMetadata().getId();
	}

	private String getDomainId(String domain) {
		ListSharedDomainsRequest sharedDomainsRequest = new ListSharedDomainsRequest()
				.withName(domain);

		ListSharedDomainsResponse listSharedDomainsResponse = this.client.listSharedDomains(sharedDomainsRequest);
		List<ResourceResponse<NamedEntity>> domains = listSharedDomainsResponse.getResources();
		if (domains.size() != 1) {
			return null;
		}
		return domains.get(0).getMetadata().getId();
	}

}
