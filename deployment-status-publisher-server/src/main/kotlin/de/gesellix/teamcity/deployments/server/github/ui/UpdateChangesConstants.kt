/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gesellix.teamcity.deployments.server.github.ui

import de.gesellix.teamcity.deployments.server.GITHUB_AUTH_TYPE
import de.gesellix.teamcity.deployments.server.GITHUB_OAUTH_PROVIDER_ID
import de.gesellix.teamcity.deployments.server.GITHUB_OAUTH_USER
import de.gesellix.teamcity.deployments.server.GITHUB_PASSWORD
import de.gesellix.teamcity.deployments.server.GITHUB_SERVER
import de.gesellix.teamcity.deployments.server.GITHUB_TOKEN
import de.gesellix.teamcity.deployments.server.GITHUB_USERNAME
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiAuthenticationType

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 05.09.12 23:26
 */
class UpdateChangesConstants() {

  fun getServerKey(): String = GITHUB_SERVER
  fun getUserNameKey(): String = GITHUB_USERNAME
  fun getPasswordKey(): String = GITHUB_PASSWORD
  fun getAccessTokenKey(): String = GITHUB_TOKEN
  fun getOAuthUserKey(): String = GITHUB_OAUTH_USER
  fun getOAuthProviderIdKey(): String = GITHUB_OAUTH_PROVIDER_ID
  fun getAuthenticationTypeKey(): String = GITHUB_AUTH_TYPE
  fun getAuthenticationTypePasswordValue(): String = GitHubApiAuthenticationType.PASSWORD_AUTH.value
  fun getAuthenticationTypeTokenValue(): String = GitHubApiAuthenticationType.TOKEN_AUTH.value
}
