package me.serce.bazillion

import java.io.File
import java.util.concurrent.TimeUnit

private val environment = run {
  val processBuilder = ProcessBuilder("/bin/bash", "-c", ". \${HOME}/.nix-profile/etc/profile.d/nix.sh && printenv")
  processBuilder.environment()["PATH"] = run {
    val current = processBuilder.environment()["PATH"]?.split(':').orEmpty()
    val needed = listOf("/usr/bin", "/usr/local/bin")
    (needed + current).distinct().joinToString(":") { it }
  }

  val process = processBuilder.start()
  process.waitFor(60, TimeUnit.SECONDS)
  process.inputStream.bufferedReader().readLines().map {
    val (key, value) = it.split('=')
    key to value
  }
}

val bazelPath: String by lazy {
  val process = process(File("/"), "which", "bazel")
  process.inputStream.bufferedReader().readLine()
}

fun process(directory: File, vararg command: String): Process {
  val processBuilder = ProcessBuilder(*command).directory(directory)
  for ((key, value) in environment) {
    processBuilder.environment()[key] = value
  }
  return processBuilder.start()
}
