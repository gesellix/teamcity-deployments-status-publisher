package de.gesellix.teamcity.deployments.server

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.version.ServerVersionHolder
import org.apache.commons.beanutils.PropertyUtils
import org.apache.http.HttpHost
import org.apache.http.HttpMessage
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.HttpClientUtils
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContexts
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URISyntaxException
import javax.net.ssl.SSLSocket

/**
 * @author anton.zamolotskikh, 23/11/16.
 */
object HttpHelper {

  //  private val LOG = Logger.getInstance(HttpBasedCommitStatusPublisher::class.java.getName())
  private val LOG = Logger.getInstance(BaseCommitStatusPublisher::class.java.name)

  @Throws(IOException::class, HttpPublisherException::class)
  fun post(
    url: String, username: String?, password: String?,
    data: String?, contentType: ContentType?,
    headers: Map<String, String>?, timeout: Int,
    processor: HttpResponseProcessor?
  ) {
    val uri = getURI(url)
    val client = buildClient(uri, username, password)
    var post: HttpPost? = null
    var response: HttpResponse? = null
    try {
      post = HttpPost(url)
      post.config = makeRequestConfig(timeout)
      addHeaders(post, headers)
      if (null != data && null != contentType) {
        post.entity = StringEntity(data, contentType)
      }
      response = client.execute(post, makeHttpContext(uri))
      if (null != processor) {
        processor.processResponse(response)
      }
    } finally {
      HttpClientUtils.closeQuietly(response)
      if (post != null) {
        try {
          post.releaseConnection()
        } catch (e: Exception) {
          LOG.warn("Error releasing connection", e)
        }
      }
      HttpClientUtils.closeQuietly(client)
    }
  }

  @Throws(IOException::class, HttpPublisherException::class)
  operator fun get(
    url: String, username: String?, password: String?,
    headers: Map<String, String>?, timeout: Int,
    processor: HttpResponseProcessor?
  ) {
    val uri = getURI(url)
    val client = buildClient(uri, username, password)
    var get: HttpGet? = null
    var response: HttpResponse? = null
    try {
      get = HttpGet(url)
      get.config = makeRequestConfig(timeout)
      addHeaders(get, headers)
      response = client.execute(get, makeHttpContext(uri))
      processor?.processResponse(response)
    } finally {
      HttpClientUtils.closeQuietly(response)
      if (get != null) {
        try {
          get.releaseConnection()
        } catch (e: Exception) {
          LOG.warn("Error releasing connection", e)
        }
      }
      HttpClientUtils.closeQuietly(client)
    }
  }

  fun stripTrailingSlash(url: String): String {
    return if (url.endsWith("/")) url.substring(0, url.length - 1) else url
  }

  fun buildUserAgentString(): String {
    return "TeamCity Server " + ServerVersionHolder.getVersion().displayVersion
  }

  private fun addHeaders(request: HttpMessage, headers: Map<String, String>?) {
    if (null != headers) {
      for ((key, value) in headers) {
        request.addHeader(key, value)
      }
    }
  }

  private fun getURI(url: String): URI {
    return try {
      URI(url)
    } catch (ex: URISyntaxException) {
      throw IllegalArgumentException(String.format("Malformed URL '%s'", url), ex)
    }
  }

  private fun makeRequestConfig(timeout: Int): RequestConfig {
    return RequestConfig.custom()
      .setConnectionRequestTimeout(timeout)
      .setConnectTimeout(timeout)
      .setSocketTimeout(timeout)
      .build()
  }

  private fun makeHttpContext(uri: URI): BasicHttpContext {
    val authCache: AuthCache = BasicAuthCache()
    authCache.put(HttpHost(uri.host, uri.port, uri.scheme), BasicScheme())
    val ctx = BasicHttpContext()
    ctx.setAttribute(HttpClientContext.AUTH_CACHE, authCache)
    return ctx
  }

  private fun buildClient(uri: URI, username: String?, password: String?): CloseableHttpClient {
    val builder = createHttpClientBuilder()
    builder.setRedirectStrategy(LaxRedirectStrategy())
    if (null != username && null != password) {
      val credentials = BasicCredentialsProvider()
      credentials.setCredentials(AuthScope(uri.host, uri.port), UsernamePasswordCredentials(username, password))
      builder.setDefaultCredentialsProvider(credentials)
    }
    builder.setUserAgent(buildUserAgentString())
    return builder.useSystemProperties().build()
  }

  private fun createHttpClientBuilder(): HttpClientBuilder {
    val sslcontext = SSLContexts.createSystemDefault()
    val sslsf: SSLConnectionSocketFactory = object : SSLConnectionSocketFactory(sslcontext) {
      @Throws(IOException::class)
      override fun connectSocket(
        connectTimeout: Int,
        socket: Socket,
        host: HttpHost,
        remoteAddress: InetSocketAddress,
        localAddress: InetSocketAddress,
        context: HttpContext
      ): Socket {
        if (socket is SSLSocket) {
          try {
            PropertyUtils.setProperty(socket, "host", host.hostName)
          } catch (ex: NoSuchMethodException) {       // ignore all that stuff
          } catch (ex: IllegalAccessException) {     //
          } catch (ex: InvocationTargetException) { //
          }
        }
        return super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context)
      }
    }
    return HttpClients.custom().setSSLSocketFactory(sslsf)
  }
}
