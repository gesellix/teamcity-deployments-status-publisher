package de.gesellix.teamcity.deployments.server

import org.apache.http.HttpResponse
import java.io.IOException

/**
 * @author anton.zamolotskikh, 23/11/16.
 */
interface HttpResponseProcessor {

  @Throws(HttpPublisherException::class, IOException::class)
  fun processResponse(response: HttpResponse?)
}
