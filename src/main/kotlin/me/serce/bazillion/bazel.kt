package me.serce.bazillion

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import java.io.File

@State(
  name = "BazilLibManager",
  storages = [Storage(StoragePathMacros.CACHE_FILE)]
)
class LibManager(private val project: Project) : PersistentStateComponent<LibManager.State> {
  companion object {
    fun getInstance(project: Project): LibManager =
      ServiceManager.getService(project, LibManager::class.java)
  }

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

  override fun getState() = state

  override fun loadState(state: State) {
    this.state = state
  }

  class State(
    val actualLibraries: MutableMap<String, LibraryData> = mutableMapOf(),
    val libraryMapping: MutableMap<String, LibraryData> = mutableMapOf(),
    val librariesMeta: MutableMap<String, LibMetadata> = mutableMapOf()
  )

  private val mapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  private var state: State = State()

  fun getLibMapping(path: String) = state.libraryMapping[path]
  fun getActualLib(path: String) = state.actualLibraries[path]
  fun getLibMeta(path: String) = state.librariesMeta[path]
  fun getAllLibs(): Collection<LibraryData> = state.libraryMapping.values

  fun refresh() {
    state.actualLibraries.clear()
    state.librariesMeta.clear()
    state.libraryMapping.clear()

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

          state.libraryMapping[lib.bind] = libraryData
          state.actualLibraries[lib.actual] = libraryData
          state.librariesMeta[lib.artifact] = lib
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
