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
package de.gesellix.teamcity.deployments.server.github.api.impl

import de.gesellix.github.client.GitHubClient
import de.gesellix.github.client.Timeout
import de.gesellix.teamcity.deployments.server.github.api.GitHubApi
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiFactory
import jetbrains.buildServer.version.ServerVersionHolder

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 2:54
 */
class GitHubApiFactoryImpl() : GitHubApiFactory {

  override fun openGitHubForToken(
    url: String,
    token: String,
    timeout: Timeout
  ): GitHubApi {
    return object : GitHubApiImpl(
      GitHubClient(
        baseUrl = url,
        token = token,
        userAgentString = "TeamCity Server ${ServerVersionHolder.getVersion().displayVersion}",
        timeout = timeout
      )
    ) {
    }
  }
}
