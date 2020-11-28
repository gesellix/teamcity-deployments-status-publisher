rootProject.name = "deployments-status-teamcity-plugin"

include("github-client")
include("deployment-status-publisher-common")
include("deployment-status-publisher-server")

buildCache {
  val isCiServer = System.getenv().containsKey("CI")
  local {
    isEnabled = !isCiServer
  }
}
