package me.serce.bazillion

import com.fasterxml.jackson.annotation.JsonIgnore
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

  data class Sources(
    val url: String,
    val repository: String
  )

  data class LibMetadata(
    val name: String,
    val coords: String,
    val url: String,
    val repository: String,
    val source: Sources?,
    val dependenciesStr: List<String> = arrayListOf(),
    @JsonIgnore // not needed for source manager
    val allDependencies: MutableList<LibraryData> = arrayListOf()
  )

  private val mapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  private val actualLibraries: MutableMap<String, LibraryData> = mutableMapOf()
  private val librariesMeta: MutableMap<String, LibMetadata> = mutableMapOf()
  private val jarLibraries: MutableMap<String, LibraryData> = mutableMapOf()

  override fun getState() = State(mapper.writeValueAsString(librariesMeta))

  override fun loadState(state: State) {
    if (state.json.isNotEmpty()) {
      librariesMeta.clear()
      try {
        librariesMeta.putAll(mapper.readValue(state.json))
      } catch (e: Exception) {
        LOG.error("error loading state", e);
      }
    }
  }

  fun getActualLib(path: String) = actualLibraries[path]
  fun getLibMeta(path: String) = librariesMeta[path]
  fun getAllLibs(): Collection<LibraryData> = actualLibraries.values

  fun refresh(progress: ProgressIndicator) {
    actualLibraries.clear()
    librariesMeta.clear()
    jarLibraries.clear()

    val projectRoot = File(project.basePath)
    val toDownload = arrayListOf<Pair<LibMetadata, File>>()
    // Create a maps of libs
    progress.text = "updating third parties"

    collectMavenInstalls(projectRoot, toDownload)

    LOG.info("resolving libraries")
    progress.text = "resolving libraries"
    if (toDownload.isNotEmpty()) {
      for ((lib, file) in toDownload) {
        progress.text2 = "downloading ${lib.coords}"
        HttpRequests.request(lib.url).saveToFile(file, progress)
      }
    }
    LOG.info("libraries resolved")
  }

  private fun collectMavenInstalls(
    projectRoot: File,
    toDownload: ArrayList<Pair<LibMetadata, File>>
  ) {
    val mavenInstallFiles = projectRoot.walk()
      .onEnter { !isNonProjectDirectory(it) }
      .filter { it.name == "maven_install.json" }

    for (mavenInstall in mavenInstallFiles) {
      val depFile = mapper.readValue<Map<String, Map<String, Any>>>(mavenInstall)["dependency_tree"] as Map<String, Any>
      val dependencies = depFile["dependencies"] as List<Map<String, Any>>
      for (dep in dependencies) {
        val coords = dep["coord"] as String
        if (coords.contains(":sources:")) {
          val artifactCoords = coords.replace(":jar:sources", "")
          val lib = librariesMeta[artifactCoords]
          if (lib == null || dep["url"] == null) {
            LOG.warn("Can't attach sources for '$artifactCoords'")
            continue
          }
          val sources = Sources(
            dep["url"] as String,
            lib.repository
          )
          librariesMeta[artifactCoords] = lib.copy(
            source = sources
          )
          val localSourceJar = sources.localSourceJar()
          if (localSourceJar.exists()) {
            val libraryData = actualLibraries[lib.name]
            libraryData?.addPath(LibraryPathType.SOURCE, localSourceJar.absolutePath)
          }
          continue
        }

        val url = dep["url"] as String
        val (groupId, artifactId, version) = coords.split(":")
        val endIndex = url.indexOf("${groupId.replace('.', '/')}/")
        if (endIndex == -1) {
          LOG.warn("Corrupted dependency url doesn't contain artifact groupid: $url")
          continue
        }
        val repository = url.substring(0, endIndex)
        val name = coordsToName(coords)
        val lib = LibMetadata(
          name,
          coords,
          url,
          repository,
          null,
          dependenciesStr = dep["dependencies"] as List<String>
        )

        val jarFile = lib.localJar()
        val unresolved = !jarFile.exists()
        if (unresolved) {
          toDownload.add(lib to jarFile)
        }
        val libraryData = LibraryData(SYSTEM_ID, lib.coords).apply {
          setGroup(groupId)
          setArtifactId(artifactId)
          setVersion(version)
          addPath(LibraryPathType.BINARY, jarFile.absolutePath)
        }
        lib.allDependencies.add(libraryData)
        actualLibraries[lib.name] = libraryData
        // well, suddenly this is allowed too.
        actualLibraries["${lib.name}_${version.replace(".", "_")}"] = libraryData
        librariesMeta[lib.coords] = lib
      }
    }
    for ((_, lib) in librariesMeta) {
      for (dependencyCoord in lib.dependenciesStr) {
        val libraryData = actualLibraries[coordsToName(dependencyCoord)]
        if (libraryData == null) {
          LOG.warn("Can't find library data mapping: $dependencyCoord")
          continue
        }
        lib.allDependencies.add(libraryData)
      }
    }
  }

  fun getJarLibs(buildFileFolderPath: String, jars: List<String>): List<LibraryData> {
    return jars.map { libPath ->
      jarLibraries.getOrPut(libPath, {
        LibraryData(SYSTEM_ID, libPath).apply {
          addPath(LibraryPathType.BINARY, "${buildFileFolderPath}/${libPath}")
        }
      })
    }
  }

  private fun coordsToName(coords: String): String {
    val parts = coords.split(":")
    val groupId: String = parts[0]
    var artifactId: String = parts[1]
    if (parts.size == 5) {
      val classifier = parts[3]
      artifactId += "_$classifier"
    }
    return "${groupId}_$artifactId".replace('.', '_').replace('-', '_')
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
