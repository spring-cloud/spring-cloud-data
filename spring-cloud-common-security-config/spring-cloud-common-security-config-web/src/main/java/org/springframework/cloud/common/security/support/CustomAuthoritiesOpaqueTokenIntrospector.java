/*
 * Copyright 2019-2021 the original author or authors.
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
package org.springframework.cloud.common.security.support;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 *
 * @author Gunnar Hillert
 * @since 1.3.0
 */
public class CustomAuthoritiesOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

	private static final Logger logger = LoggerFactory.getLogger(CustomAuthoritiesOpaqueTokenIntrospector.class);
	private final OpaqueTokenIntrospector delegate;
	private DefaultPrincipalExtractor principalExtractor;
	private AuthoritiesMapper authorityMapper;

	public CustomAuthoritiesOpaqueTokenIntrospector(
			String introspectionUri,
			String clientId,
			String clientSecret,
			AuthoritiesMapper authorityMapper) {
		this.delegate = new NimbusOpaqueTokenIntrospector(introspectionUri, clientId, clientSecret);
		this.principalExtractor = new DefaultPrincipalExtractor();
		this.authorityMapper = authorityMapper;
	}

	@Override
	public OAuth2AuthenticatedPrincipal introspect(String token) {
		logger.debug("Introspecting");
		OAuth2AuthenticatedPrincipal principal = this.delegate.introspect(token);
		Object principalName = principalExtractor.extractPrincipal(principal.getAttributes());
		return new DefaultOAuth2AuthenticatedPrincipal(
				principalName.toString(), principal.getAttributes(), extractAuthorities(principal, token));
	}

	private Collection<GrantedAuthority> extractAuthorities(OAuth2AuthenticatedPrincipal principal, String token) {
		final List<String> scopes = principal.getAttribute(OAuth2TokenIntrospectionClaimNames.SCOPE);
		final Set<String> scopesAsSet = new HashSet<>(scopes);
		final Set<GrantedAuthority> authorities = this.authorityMapper.mapScopesToAuthorities(null, scopesAsSet, token);

		List<String> roleClaims = principal.getAttribute("groups");
		if (roleClaims == null) {
			roleClaims = principal.getAttribute("roles");
		}
		if (roleClaims == null) {
			roleClaims = new ArrayList<>();
		}
		
		final Set<GrantedAuthority> authorities2 = this.authorityMapper.mapClaimsToAuthorities(null, roleClaims);
		authorities.addAll(authorities2);
		return authorities;
	}

	public void setPrincipalExtractor(DefaultPrincipalExtractor principalExtractor) {
		this.principalExtractor = principalExtractor;
	}

	public void setAuthorityMapper(AuthoritiesMapper authorityMapper) {
		this.authorityMapper = authorityMapper;
	}

}
