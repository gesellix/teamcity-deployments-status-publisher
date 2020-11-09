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
package de.gesellix.teamcity.deployments.server.github.api

import de.gesellix.teamcity.deployments.server.PublisherException
import java.io.IOException

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 2:39
 */
interface GitHubApi {

  @Throws(PublisherException::class)
  fun testConnection(repoOwner: String, repositoryName: String)

  @Throws(IOException::class)
  fun setChangeStatus(
    repoOwner: String,
    repositoryName: String,
    hash: String,
    status: GitHubChangeState,
    targetUrl: String,
    description: String,
    context: String?
  )

  /**
   * checks if specified branch represents GitHub pull request merge branch,
   * i.e. /refs/pull/X/merge
   * @param branchName branch name
   * @return true if branch is pull's merge
   */
  fun isPullRequestMergeBranch(branchName: String): Boolean

  /**
   * this method parses branch name and attempts to detect
   * /refs/pull/X/head revision for given branch
   *
   * The main use-case for it is to resolve /refs/pull/X/merge branch
   * into head commit hash in order to call github status API
   *
   * @param repoOwner repository owner name (who owns repo where you see pull request)
   * @param repoName repository name (where you see pull request)
   * @param branchName detected branch name in TeamCity, i.e. /refs/pull/X/merge
   * @return found /refs/pull/X/head or null
   * @throws IOException on communication error
   */
  @Throws(IOException::class, PublisherException::class)
  fun findPullRequestCommit(
    repoOwner: String,
    repoName: String,
    branchName: String
  ): String?

  /**
   * return parent commits for given commit
   * @param repoOwner repo owner
   * @param repoName repo name
   * @param hash commit hash
   * @return colleciton of commit parents
   * @throws IOException
   */
  @Throws(IOException::class, PublisherException::class)
  fun getCommitParents(
    repoOwner: String,
    repoName: String,
    hash: String
  ): Collection<String?>

  /**
   * Post comment to pull request
   * @param repoName
   * @param hash
   * @param comment
   * @throws IOException
   */
  @Throws(IOException::class)
  fun postComment(
    ownerName: String,
    repoName: String,
    hash: String,
    comment: String
  )
}
