package me.serce.bazillion

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.io.File

@State(
  name = "BazilLibManager",
  storages = [Storage("bazil_libs.xml")]
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
  private var librariesMeta: MutableMap<String, LibMetadata> = mutableMapOf()
  private val libraryMapping: MutableMap<String, LibraryData> = mutableMapOf()

  override fun getState(): State {
    return State(mapper.writeValueAsString(librariesMeta))
  }

  override fun loadState(state: State) {
    if (state.json.isNotEmpty()) {
      librariesMeta = mapper.readValue(state.json)
    }
  }

  fun getLibMapping(path: String) = libraryMapping[path]
  fun getActualLib(path: String) = actualLibraries[path]
  fun getLibMeta(path: String) = librariesMeta[path]
  fun getAllLibs(): Collection<LibraryData> = libraryMapping.values

  fun refresh() {
    actualLibraries.clear()
    librariesMeta.clear()
    libraryMapping.clear()

    val projectRoot = File(project.basePath)
    val workspaceFiles = projectRoot.walk()
      .onEnter { !isNonProjectDirectory(it) }
      .filter { it.name == "workspace.bzl" }

    val toDownload = arrayListOf<Pair<String, File>>()

    // Create a maps of libs
    for (workspaceFile in workspaceFiles) {
      for (line in workspaceFile.readLines()) {
        if (line.contains("""{"artifact": """")) {
          val lib = mapper.readValue(line.trim(','), LibMetadata::class.java)
          val (groupId, artifactId, version) = lib.artifact.split(":")

          val jarFile = toLocalRepository(lib.url, lib.repository)
          val unresolved = !jarFile.exists()
          if (unresolved) {
            toDownload.add(lib.url to jarFile)
          }

          val libraryData = LibraryData(SYSTEM_ID, lib.artifact).apply {
            setGroup(groupId)
            setArtifactId(artifactId)
            setVersion(version)
            addPath(LibraryPathType.BINARY, jarFile.absolutePath)
          }

          if (lib.source != null) {
            val sourceJarFile = toLocalRepository(lib.source.url, lib.source.repository)
            if (sourceJarFile.exists()) {
              libraryData.addPath(LibraryPathType.SOURCE, sourceJarFile.absolutePath)
            }
          }

          libraryMapping[lib.bind] = libraryData
          actualLibraries[lib.actual] = libraryData
          librariesMeta[lib.artifact] = lib
          println("adding library $groupId:$artifactId:$version")
        }
      }
    }

    if (!toDownload.isEmpty()) {
      for ((url, file) in toDownload) {
        HttpRequests.request(url).saveToFile(file, null)
      }
    }
  }

  private fun toLocalRepository(url: String, repository: String) = File(
    url.replace(
      repository,
      File(System.getProperty("user.home"), ".m2/repository/").absolutePath + "/"
    )
  )
}
