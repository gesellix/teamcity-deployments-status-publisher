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

import de.gesellix.teamcity.deployments.server.github.api.GitHubApi
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiFactory
import org.apache.http.HttpRequest
import org.apache.http.auth.AuthenticationException
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.protocol.BasicHttpContext

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 2:54
 */
class GitHubApiFactoryImpl(private val client: HttpClientWrapper) : GitHubApiFactory {

  override fun openGitHubForUser(
    url: String,
    username: String,
    password: String
  ): GitHubApi {
    return object : GitHubApiImpl(client, GitHubApiPaths(url)) {
      @Throws(AuthenticationException::class)
      override fun setAuthentication(request: HttpRequest) {
        request.addHeader(BasicScheme().authenticate(UsernamePasswordCredentials(username, password), request, BasicHttpContext()))
      }
    }
  }

  override fun openGitHubForToken(
    url: String,
    token: String
  ): GitHubApi {
    return object : GitHubApiImpl(client, GitHubApiPaths(url)) {
      @Throws(AuthenticationException::class)
      override fun setAuthentication(request: HttpRequest) {
        //NOTE: This auth could also be done via HTTP header
        request.addHeader(BasicScheme().authenticate(UsernamePasswordCredentials(token, "x-oauth-basic"), request, BasicHttpContext()))
      }
    }
  }
}
