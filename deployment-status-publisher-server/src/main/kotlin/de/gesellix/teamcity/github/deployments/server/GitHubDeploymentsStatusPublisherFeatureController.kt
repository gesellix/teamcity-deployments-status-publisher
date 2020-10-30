package de.gesellix.teamcity.github.deployments.server

import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.vcs.SVcsRoot
import jetbrains.buildServer.vcs.VcsRoot
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import jetbrains.buildServer.web.util.SessionUser
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GitHubDeploymentsStatusPublisherFeatureController(controllerManager: WebControllerManager,
                                                        private val projectManager: ProjectManager,
                                                        private val descriptor: PluginDescriptor,
                                                        private val publisherManager: PublisherManager,
                                                        private val publisherSettingsController: PublisherSettingsController
                                                        ) : BaseController() {

  private val resourceUrl: String = descriptor.getPluginResourcesPath("gitHubDeploymentsStatusPublisherFeature.html")

  val url: String
    get() {
      return resourceUrl
    }

  init {
    controllerManager.registerController(resourceUrl, this)
  }

  override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
    val props = request.getAttribute("propertiesBean") as BasePropertiesBean
    val publisherId = props.properties[PUBLISHER_ID_PARAM]
    val mv = publisherId?.let { createEditPublisherModel(it) } ?: createAddPublisherModel()
    var settings: GitHubDeploymentsStatusPublisherSettings? = null
    if (publisherId != null) {
      settings = publisherManager.findSettings(publisherId)
      transformParameters(props, publisherId, mv)
    }
    mv.addObject("publisherSettingsUrl", publisherSettingsController.url)
    mv.addObject("showMode", "popup")
    val vcsRoots = getVcsRoots(request)
    mv.addObject("vcsRoots", vcsRoots)
    val params = props.properties
    if (params.containsKey(VCS_ROOT_ID_PARAM)) {
      val internalId: Long?
      val vcsRootId = params[VCS_ROOT_ID_PARAM]
      internalId = try {
        java.lang.Long.valueOf(vcsRootId)
      } catch (ex: NumberFormatException) {
        null
      }
      var vcsRoot: SVcsRoot? = null
      for (vcs in vcsRoots) {
        if (vcs !is SVcsRoot) {
          continue
        }
        val candidate = vcs
        if (candidate.externalId == vcsRootId) {
          vcsRoot = candidate
          break
        }
        if (null != internalId && internalId == candidate.id) {
          props.setProperty(VCS_ROOT_ID_PARAM, candidate.externalId)
          vcsRoot = candidate
          break
        }
      }
      if (null == vcsRoot) {
        mv.addObject("hasMissingVcsRoot", true)
        vcsRoot = if (null != internalId) {
          projectManager!!.findVcsRootById(internalId)
        } else {
          projectManager!!.findVcsRootByExternalId(vcsRootId!!)
        }
        if (null != vcsRoot) {
          mv.addObject("missingVcsRoot", vcsRoot)
        }
      }
    }
    val project = getProject(request)
    mv.addObject("project", project)
    mv.addObject("projectId", project.externalId)
    val user = SessionUser.getUser(request)
    mv.addObject("oauthConnections", settings?.getOAuthConnections(project, user))
    return mv
  }

  private fun transformParameters(props: BasePropertiesBean, publisherId: String, mv: ModelAndView) {
    val publisherSettings = publisherManager.findSettings(publisherId) ?: return
    val transformed: Map<String, String>? = publisherSettings.transformParameters(props.properties)
    if (transformed != null) {
      mv.addObject("propertiesBean", BasePropertiesBean(transformed))
    }
  }

  private fun createAddPublisherModel(): ModelAndView {
    val mv = ModelAndView(descriptor!!.getPluginResourcesPath("addPublisher.jsp"))
    mv.addObject("publishers", getPublisherSettings(true))
    return mv
  }

  private fun createEditPublisherModel(publisherId: String): ModelAndView {
    val mv = ModelAndView(descriptor!!.getPluginResourcesPath("editPublisher.jsp"))
    mv.addObject("publishers", getPublisherSettings(false))
    val publisherSettings: GitHubDeploymentsStatusPublisherSettings? = publisherManager.findSettings(publisherId)
    if (publisherSettings != null) {
      mv.addObject("editedPublisherUrl", publisherSettings.getEditSettingsUrl())
      mv.addObject("testConnectionSupported", publisherSettings.isTestConnectionSupported())
    }
    return mv
  }

  private fun getVcsRoots(request: HttpServletRequest): List<VcsRoot> {
    val roots: MutableList<VcsRoot> = ArrayList()
    val buildTypeForm = request.getAttribute("buildForm") as BuildTypeForm
    for (entry in buildTypeForm.vcsRootsBean.vcsRoots) {
      roots.add(entry.vcsRoot)
    }
    return roots
  }

  private fun getProject(request: HttpServletRequest): SProject {
    val buildTypeForm = request.getAttribute("buildForm") as BuildTypeForm
    return buildTypeForm.project
  }

  private fun getPublisherSettings(newPublisher: Boolean): List<GitHubDeploymentsStatusPublisherSettings>? {
    val publishers: MutableList<GitHubDeploymentsStatusPublisherSettings> = ArrayList(publisherManager.allPublisherSettings)
    if (newPublisher) {
      publishers.add(0, DummyPublisherSettings())
    }
    return publishers
  }
}
