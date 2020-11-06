/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import de.gesellix.teamcity.deployments.server.HttpHelper

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 19.04.13 19:17
 */
class GitHubApiPaths(
  url: String,
  private val myUrl: String = HttpHelper.stripTrailingSlash(url)
) {

  fun getRepoInfo(
    repoOwner: String,
    repoName: String
  ): String {
    // /repos/:owner/:repo
    return "$myUrl/repos/$repoOwner/$repoName"
  }

  fun getCommitInfo(
    repoOwner: String,
    repoName: String,
    hash: String
  ): String {
    // /repos/:owner/:repo/git/commits/:sha
    return "$myUrl/repos/$repoOwner/$repoName/git/commits/$hash"
  }

  fun getStatusUrl(
    ownerName: String,
    repoName: String,
    hash: String
  ): String {
    return "$myUrl/repos/$ownerName/$repoName/statuses/$hash"
  }

  fun getPullRequestInfo(
    repoOwner: String,
    repoName: String,
    pullRequestId: String
  ): String {
    return "$myUrl/repos/$repoOwner/$repoName/pulls/$pullRequestId"
  }

  fun getAddCommentUrl(
    ownerName: String,
    repoName: String,
    hash: String
  ): String {
    ///repos/:owner/:repo/commits/:sha/comments
    return "$myUrl/repos/$ownerName/$repoName/commits/$hash/comments"
  }
}
