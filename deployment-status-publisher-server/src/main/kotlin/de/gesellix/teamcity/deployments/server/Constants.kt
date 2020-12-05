package de.gesellix.teamcity.deployments.server

const val PUBLISHER_ID_PARAM = "publisherId"
const val CREATE_DEPLOYMENT_PARAM = "createDeployment"
const val TARGET_ENVIRONMENT_PARAM = "targetEnvironment"
const val TARGET_ENVIRONMENT_DEFAULT = "production"
const val DEPLOYMENT_ID_PARAM_KEY = "deployments-status-publisher.deploymentId"
const val VCS_ROOT_ID_PARAM = "vcsRootId"
const val DEPLOYMENTS_STATUS_PUBLISHER_PROBLEM_TYPE = "DEPLOYMENTS_STATUS_PUBLISHER_PROBLEM"
const val TEST_CONNECTION_YES = "yes"
const val TEST_CONNECTION_PARAM = "testconnection"
const val GITHUB_PUBLISHER_ID = "githubDeploymentsStatusPublisher"
const val GITHUB_SERVER = "github_host"
const val GITHUB_AUTH_TYPE = "github_authentication_type"
const val GITHUB_TOKEN = "secure:github_access_token"
const val GITHUB_OAUTH_USER = "github_oauth_user"
const val GITHUB_OAUTH_PROVIDER_ID = "github_oauth_provider_id"
const val GITHUB_CUSTOM_CONTEXT_BUILD_PARAM = "teamcity.deploymentsStatusPublisher.githubContext"
const val GITHUB_CONTEXT = "github_context"

const val BUILD_ID_KEY = "buildIdAsString"

class Constants {

  val publisherIdParam: String
    get() = PUBLISHER_ID_PARAM

  val createDeploymentParam: String
    get() = CREATE_DEPLOYMENT_PARAM

  val targetEnvironmentParam: String
    get() = TARGET_ENVIRONMENT_PARAM

  val vcsRootIdParam: String
    get() = VCS_ROOT_ID_PARAM
}
