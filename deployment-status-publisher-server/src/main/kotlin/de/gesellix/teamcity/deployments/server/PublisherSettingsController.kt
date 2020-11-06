package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.controllers.ActionErrors
import jetbrains.buildServer.controllers.AjaxRequestProcessor
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.XmlResponseUtil
import jetbrains.buildServer.controllers.admin.projects.EditBuildTypeFormFactory
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
import jetbrains.buildServer.serverSide.BuildTypeIdentity
import jetbrains.buildServer.serverSide.BuildTypeSettings
import jetbrains.buildServer.serverSide.ParametersSupport
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.vcs.SVcsRoot
import jetbrains.buildServer.vcs.VcsRoot
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import jetbrains.buildServer.web.util.SessionUser
import org.springframework.web.servlet.ModelAndView
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class PublisherSettingsController(
  controllerManager: WebControllerManager,
  descriptor: PluginDescriptor,
  private var publisherManager: PublisherManager,
  private var projectManager: ProjectManager
) : BaseController() {

  private val log by logger()

  private val resourceUrl: String = descriptor.getPluginResourcesPath("publisherSettings.html")

  val url: String
    get() {
      return resourceUrl
    }

  init {
    controllerManager.registerController(resourceUrl, this)
  }

  @Throws(Exception::class)
  override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
    val publisherId = request.getParameter(PUBLISHER_ID_PARAM) ?: return null
    val projectId = request.getParameter("projectId")
    val project: SProject? = projectManager.findProjectByExternalId(projectId)
    request.setAttribute("projectId", projectId)
    request.setAttribute("project", project)
    val settings: DeploymentsStatusPublisherSettings = publisherManager.findSettings(publisherId) ?: return null

    val settingsUrl: String? = settings.getEditSettingsUrl()
    val params = if (settings.getDefaultParameters() != null) settings.getDefaultParameters() else emptyMap()
    if (TEST_CONNECTION_YES.equals(request.getParameter(TEST_CONNECTION_PARAM))) {
      processTestConnectionRequest(request, response, settings, params)
      return null
    }

    request.setAttribute("propertiesBean", BasePropertiesBean(params))
    request.setAttribute("currentUser", SessionUser.getUser(request))
    request.setAttribute("testConnectionSupported", settings.isTestConnectionSupported())
    val user = SessionUser.getUser(request)
    request.setAttribute("oauthConnections", if (project != null) settings.getOAuthConnections(project, user) else null)
    if (settingsUrl != null) {
      request.getRequestDispatcher(settingsUrl).include(request, response)
    }
    return null
  }

  @Throws(PublisherException::class)
  private fun getBuildTypeOrTemplate(id: String?): BuildTypeIdentity {
    if (null == id) throw PublisherException("No build type/template has been submitted")
    val buildTypeOrTemplate: BuildTypeIdentity?
    if (id.startsWith(EditBuildTypeFormFactory.BT_PREFIX)) {
      val btId = id.substring(EditBuildTypeFormFactory.BT_PREFIX.length)
      buildTypeOrTemplate = projectManager.findBuildTypeByExternalId(btId)
      if (null == buildTypeOrTemplate) {
        throw PublisherException(String.format("No build type with id '%s' has been found", btId))
      }
    } else if (id.startsWith(EditBuildTypeFormFactory.TEMPLATE_PREFIX)) {
      val tplId = id.substring(EditBuildTypeFormFactory.TEMPLATE_PREFIX.length)
      buildTypeOrTemplate = projectManager.findBuildTypeTemplateByExternalId(tplId)
      if (null == buildTypeOrTemplate) {
        throw PublisherException(String.format("No template with id '%s' has been found", tplId))
      }
    } else {
      throw PublisherException(String.format("Malformed build type/teplate id parameter '%s'", id))
    }
    return buildTypeOrTemplate
  }

  private fun processTestConnectionRequest(req: HttpServletRequest, resp: HttpServletResponse, settings: DeploymentsStatusPublisherSettings, params: Map<String, String>?) {
    AjaxRequestProcessor().processRequest(req, resp) { request, _, xmlResponse ->
      XmlResponseUtil.writeTestResult(xmlResponse, "")
      val errors = ActionErrors()
      try {
        val propBean = BasePropertiesBean(params)
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propBean)
        val props = propBean.properties
        val processor: PropertiesProcessor? = settings.getParametersProcessor()
        if (null != processor) {
          val invalidProps = processor.process(props)
          if (invalidProps != null) {
            for (prop in invalidProps) {
              errors.addError("testConnectionFailed", prop.invalidReason)
            }
          }
        }
        if (!errors.hasErrors()) {
          testConnection(settings, props, request, errors)
        }
      } catch (ex: PublisherException) {
        reportTestConnectionFailure(ex, errors)
      }
      if (errors.hasErrors()) {
        XmlResponseUtil.writeErrors(xmlResponse, errors)
      }
    }
  }

  private fun reportTestConnectionFailure(ex: PublisherException, errors: ActionErrors) {
    val msgBuf = StringBuffer(ex.message)
    val cause: Throwable? = ex.cause
    if (null != cause) {
      msgBuf.append(String.format(": %s", cause.message))
    }
    val msg: String = msgBuf.toString()
    log.debug("Test connection failure", ex)
    errors.addError("testConnectionFailed", msg)
  }

  private fun resolveProperties(buildTypeOrTemplate: BuildTypeIdentity, params: Map<String, String>): Map<String, String> {
    if (buildTypeOrTemplate is ParametersSupport) {
      val valueResolver = (buildTypeOrTemplate as ParametersSupport).valueResolver
      return valueResolver.resolve(params)
    }
    return HashMap(params)
  }

  private fun getVcsRootInstanceIfPossible(buildTypeOrTemplate: BuildTypeIdentity, sVcsRoot: SVcsRoot?): VcsRoot? {
    return if (buildTypeOrTemplate is SBuildType) {
      buildTypeOrTemplate.getVcsRootInstanceForParent(sVcsRoot!!)
    } else {
      sVcsRoot
    }
  }

  @Throws(PublisherException::class)
  private fun testConnection(
    settings: DeploymentsStatusPublisherSettings, params: Map<String, String>, request: HttpServletRequest,
    errors: ActionErrors
  ) {
    val buildTypeOrTemplate = getBuildTypeOrTemplate(request.getParameter("id"))
    val resolvedProperties = resolveProperties(buildTypeOrTemplate, params)
    val vcsRootId = resolvedProperties[VCS_ROOT_ID_PARAM]
    if (null == vcsRootId || vcsRootId.isEmpty()) {
      var roots: List<SVcsRoot?>? = null
      if (buildTypeOrTemplate is BuildTypeSettings) {
        roots = (buildTypeOrTemplate as BuildTypeSettings).vcsRoots
      }
      if (null == roots || roots.isEmpty()) {
        throw PublisherException("No VCS roots attached")
      }
      var isTested = false
      for (sVcsRoot in roots) {
        try {
          val vcsRoot = getVcsRootInstanceIfPossible(buildTypeOrTemplate, sVcsRoot)
          if (vcsRoot != null && settings.isPublishingForVcsRoot(vcsRoot)) {
            isTested = true
            settings.testConnection(buildTypeOrTemplate, vcsRoot, resolvedProperties)
          }
        } catch (ex: PublisherException) {
          reportTestConnectionFailure(ex, errors)
        }
      }
      if (!isTested) {
        throw PublisherException("No relevant VCS roots attached")
      }
    } else {
      var sVcsRoot: SVcsRoot? = projectManager.findVcsRootByExternalId(vcsRootId)
      if (null == sVcsRoot) {
        sVcsRoot = try {
          val internalId = java.lang.Long.valueOf(vcsRootId)
          projectManager.findVcsRootById(internalId)
        } catch (ex: NumberFormatException) {
          throw PublisherException(String.format("Unknown VCS root id '%s'", vcsRootId))
        }
      }
      if (null == sVcsRoot) {
        throw PublisherException(String.format("VCS root not found for id '%s'", vcsRootId))
      }
      val vcsRoot = getVcsRootInstanceIfPossible(buildTypeOrTemplate, sVcsRoot)
      if (vcsRoot != null) settings.testConnection(buildTypeOrTemplate, vcsRoot, resolvedProperties)
    }
  }
}
