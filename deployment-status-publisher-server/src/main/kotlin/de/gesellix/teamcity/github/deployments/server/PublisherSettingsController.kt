package de.gesellix.teamcity.github.deployments.server

import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.web.openapi.PluginDescriptor
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class PublisherSettingsController(descriptor: PluginDescriptor) : BaseController() {

  private val resourceUrl: String = descriptor.getPluginResourcesPath("publisherSettings.html")

  val url: String
    get() {
      return resourceUrl
    }

  override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
    TODO("Not yet implemented")
  }
}
