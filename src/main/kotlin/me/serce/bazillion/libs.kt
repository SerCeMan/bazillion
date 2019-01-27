package me.serce.bazillion

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import me.serce.bazillion.LibManager.LibMetadata
import me.serce.bazillion.LibManager.Sources
import java.io.File

@State(
  name = "BazilLibManager",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class LibManager(private val project: Project) : PersistentStateComponent<LibManager.State> {
  companion object {
    fun getInstance(project: Project): LibManager =
      ServiceManager.getService(project, LibManager::class.java)
  }

  data class State(var json: String = "")

  class Sources(
    val url: String,
    val repository: String
  )

  data class LibMetadata(
    val name: String,
    val bind: String,
    val actual: String,
    val artifact: String,
    val url: String,
    val repository: String,
    val source: Sources?
  )

  private val mapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  private val actualLibraries: MutableMap<String, LibraryData> = mutableMapOf()
  private val librariesMeta: MutableMap<String, LibMetadata> = mutableMapOf()
  private val libraryMapping: MutableMap<String, LibraryData> = mutableMapOf()

  override fun getState() = State(mapper.writeValueAsString(librariesMeta))

  override fun loadState(state: State) {
    if (state.json.isNotEmpty()) {
      librariesMeta.clear()
      librariesMeta.putAll(mapper.readValue(state.json))
    }
  }

  fun getLibMapping(path: String) = libraryMapping[path]
  fun getActualLib(path: String) = actualLibraries[path]
  fun getLibMeta(path: String) = librariesMeta[path]
  fun getAllLibs(): Collection<LibraryData> = libraryMapping.values

  fun refresh(progress: ProgressIndicator) {
    actualLibraries.clear()
    librariesMeta.clear()
    libraryMapping.clear()

    val projectRoot = File(project.basePath)
    val workspaceFiles = projectRoot.walk()
      .onEnter { !isNonProjectDirectory(it) }
      .filter { it.name == "workspace.bzl" }

    val toDownload = arrayListOf<Pair<LibMetadata, File>>()

    // Create a maps of libs
    progress.text = "updating third parties"
    for (workspaceFile in workspaceFiles) {
      for (line in workspaceFile.readLines()) {
        if (line.contains("""{"artifact": """")) {
          val lib = mapper.readValue(line.trim(','), LibMetadata::class.java)
          val (groupId, artifactId, version) = lib.artifact.split(":")

          val jarFile = lib.localJar()
          val unresolved = !jarFile.exists()
          if (unresolved) {
            toDownload.add(lib to jarFile)
          }

          val libraryData = LibraryData(SYSTEM_ID, lib.artifact).apply {
            setGroup(groupId)
            setArtifactId(artifactId)
            setVersion(version)
            addPath(LibraryPathType.BINARY, jarFile.absolutePath)
          }

          val sources = lib.source
          if (sources != null) {
            val localSourceJar = sources.localSourceJar()
            if (localSourceJar.exists()) {
              libraryData.addPath(LibraryPathType.SOURCE, localSourceJar.absolutePath)
            }
          }

          libraryMapping[lib.bind] = libraryData
          actualLibraries[lib.actual] = libraryData
          librariesMeta[lib.artifact] = lib
        }
      }
    }

    LOG.info("resolving libraries")
    progress.text = "resolving libraries"
    if (!toDownload.isEmpty()) {
      for ((lib, file) in toDownload) {
        progress.text2 = "downloading ${lib.artifact}"
        HttpRequests.request(lib.url).saveToFile(file, progress)
      }
    }
    LOG.info("libraries resolved")
  }
}

private fun toLocalRepository(url: String, repository: String) = File(
  url.replace(
    repository,
    File(System.getProperty("user.home"), ".m2/repository/").absolutePath + "/"
  )
)

fun LibMetadata.localJar(): File = toLocalRepository(url, repository)
fun Sources.localSourceJar(): File = toLocalRepository(url, repository)
