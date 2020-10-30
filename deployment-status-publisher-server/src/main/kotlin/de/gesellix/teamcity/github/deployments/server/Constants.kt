package de.gesellix.teamcity.github.deployments.server

const val PUBLISHER_ID_PARAM = "publisherId"
const val VCS_ROOT_ID_PARAM = "vcsRootId"

class Constants {

  val publisherIdParam: String
    get() = PUBLISHER_ID_PARAM

  val vcsRootIdParam: String
    get() = VCS_ROOT_ID_PARAM
}
