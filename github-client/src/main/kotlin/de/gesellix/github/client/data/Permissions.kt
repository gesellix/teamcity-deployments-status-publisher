package de.gesellix.github.client.data

data class Permissions(
  var admin: Boolean = false,
  var push: Boolean = false,
  var pull: Boolean = false,
)
