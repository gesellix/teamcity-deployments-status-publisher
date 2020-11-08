package de.gesellix.github.client

import retrofit2.Response

class HttpStatusException(val code: Int, message: String, val response: Response<*>?) : RuntimeException(message)
