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

import com.intellij.openapi.diagnostic.Logger
import de.gesellix.teamcity.deployments.server.HttpHelper.buildUserAgentString
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.StringUtil
import org.apache.commons.beanutils.PropertyUtils
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.NTCredentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.RequestAcceptEncoding
import org.apache.http.client.protocol.ResponseContentEncoding
import org.apache.http.conn.params.ConnRoutePNames
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.impl.conn.ProxySelectorRoutePlanner
import org.apache.http.impl.conn.SchemeRegistryFactory
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
import org.apache.http.params.BasicHttpParams
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.params.HttpProtocolParams
import org.apache.http.protocol.HttpContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 11.08.11 16:24
 */
class HttpClientWrapperImpl : HttpClientWrapper {

  private val LOG = Logger.getInstance(HttpClientWrapperImpl::class.java.name)
  private val myClient: HttpClient
  private fun setupProxy(httpclient: DefaultHttpClient) {
    val httpProxy = TeamCityProperties.getProperty("teamcity.github.http.proxy.host")
    if (StringUtil.isEmptyOrSpaces(httpProxy)) return
    val httpProxyPort = TeamCityProperties.getInteger("teamcity.github.http.proxy.port", -1)
    if (httpProxyPort <= 0) return
    LOG.info("TeamCity.GitHub will use proxy: $httpProxy, port $httpProxyPort")
    httpclient.params.setParameter(ConnRoutePNames.DEFAULT_PROXY, HttpHost(httpProxy, httpProxyPort))
    val httpProxyUser = TeamCityProperties.getProperty("teamcity.github.http.proxy.user")
    val httpProxyPassword = TeamCityProperties.getProperty("teamcity.github.http.proxy.password")
    val httpProxyDomain = TeamCityProperties.getProperty("teamcity.github.http.proxy.domain")
    val httpProxyWorkstation = TeamCityProperties.getProperty("teamcity.github.http.proxy.workstation")
    if (StringUtil.isEmptyOrSpaces(httpProxyUser) || StringUtil.isEmptyOrSpaces(httpProxyPassword)) return
    val creds: Credentials
    creds = if (StringUtil.isEmptyOrSpaces(httpProxyDomain) || StringUtil.isEmptyOrSpaces(httpProxyWorkstation)) {
      LOG.info("TeamCity.GitHub will use proxy credentials: $httpProxyUser")
      UsernamePasswordCredentials(httpProxyUser, httpProxyPassword)
    } else {
      LOG.info("TeamCity.GitHub will use proxy NT credentials: $httpProxyDomain/$httpProxyUser")
      NTCredentials(httpProxyUser, httpProxyPassword, httpProxyWorkstation, httpProxyDomain)
    }
    httpclient.credentialsProvider.setCredentials(
      AuthScope(httpProxy, httpProxyPort),
      creds
    )
  }

  @Throws(IOException::class)
  override fun execute(request: HttpUriRequest): HttpResponse {
    return myClient.execute(request)
  }

  fun dispose() {
    myClient.connectionManager.shutdown()
  }

  init {
    val ps: HttpParams = BasicHttpParams()
    DefaultHttpClient.setDefaultHttpParams(ps)
    val timeout = TeamCityProperties.getInteger("teamcity.github.http.timeout", 300 * 1000)
    HttpConnectionParams.setConnectionTimeout(ps, timeout)
    HttpConnectionParams.setSoTimeout(ps, timeout)
    HttpProtocolParams.setUserAgent(ps, buildUserAgentString())
    val schemaRegistry = SchemeRegistryFactory.createDefault()
    val sslSocketFactory: SSLSocketFactory = object : SSLSocketFactory(TrustStrategy { _, _ -> !TeamCityProperties.getBoolean("teamcity.github.verify.ssl.certificate") }) {
      @Throws(IOException::class)
      override fun connectSocket(
        connectTimeout: Int, socket: Socket?,
        host: HttpHost, remoteAddress: InetSocketAddress,
        localAddress: InetSocketAddress?, context: HttpContext?
      ): Socket {
        if (socket is SSLSocket) {
          try {
            PropertyUtils.setProperty(socket, "host", host.hostName)
          } catch (ex: Exception) {
            LOG.warn(String.format("A host name is not passed to SSL connection for the purpose of supporting SNI due to the following exception: %s", ex.toString()))
          }
        }
        return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context)
      }
    }
    schemaRegistry.register(Scheme("https", 443, sslSocketFactory))
    val httpclient = DefaultHttpClient(ThreadSafeClientConnManager(schemaRegistry), ps)
    setupProxy(httpclient)
    httpclient.routePlanner = ProxySelectorRoutePlanner(
      httpclient.connectionManager.schemeRegistry,
      ProxySelector.getDefault()
    )
    httpclient.addRequestInterceptor(RequestAcceptEncoding())
    httpclient.addResponseInterceptor(ResponseContentEncoding())
    httpclient.httpRequestRetryHandler = DefaultHttpRequestRetryHandler(3, true)
    httpclient.redirectStrategy = LaxRedirectStrategy()
    myClient = httpclient
  }
}
