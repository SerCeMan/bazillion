package me.serce.bazillion

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.google.devtools.build.lib.syntax.*
import com.google.devtools.build.lib.vfs.PathFragment
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.externalSystem.JavaProjectData
import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.projectImport.ProjectOpenProcessorBase
import com.intellij.util.Function
import com.intellij.util.messages.Topic
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import java.io.File

val SYSTEM_ID = ProjectSystemId("BAZIL")
val TOPIC = Topic.create<BazilSettingsListener>(
  "Bazil-specific settings",
  BazilSettingsListener::class.java
)

private val LOG = Logger.getInstance("#me.serce.bazillion")

class BazilProjectSettings : ExternalProjectSettings() {
  override fun clone(): BazilProjectSettings {
    val clone = BazilProjectSettings()
    copyTo(clone)
    return clone
  }
}

val a = run {
  Registry.addKey("BAZIL.system.in.process", "", true, false)
}


interface BazilSettingsListener : ExternalSystemSettingsListener<BazilProjectSettings>


class BazilSettings(project: Project) :
  AbstractExternalSystemSettings<BazilSettings, BazilProjectSettings, BazilSettingsListener>(TOPIC, project) {
  companion object {
    fun getInstance(project: Project): BazilSettings {
      return ServiceManager.getService<BazilSettings>(project, BazilSettings::class.java)
    }
  }

  override fun checkSettings(old: BazilProjectSettings, current: BazilProjectSettings) {
    // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun copyExtraSettingsFrom(settings: BazilSettings) {
    // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun subscribe(listener: ExternalSystemSettingsListener<BazilProjectSettings>) {
    // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}

class ImportFromBazilControl :
  AbstractImportFromExternalSystemControl<BazilProjectSettings, BazilSettingsListener, BazilSettings>
    (SYSTEM_ID, BazilSettings(ProjectManager.getInstance().defaultProject), BazilProjectSettings(), true) {
  override fun createProjectSettingsControl(settings: BazilProjectSettings): ExternalSystemSettingsControl<BazilProjectSettings> {
    return BazilProjectSettingsControl(settings)
  }

  override fun onLinkedProjectPathChange(path: String) {
  }

  override fun createSystemSettingsControl(settings: BazilSettings): ExternalSystemSettingsControl<BazilSettings>? {
    return BazilSystemSettingsControl(settings)
  }
}

class BazilSystemSettingsControl(settings: BazilSettings) :
  ExternalSystemSettingsControl<BazilSettings> {
  override fun isModified(): Boolean {
    // TODO
    return false
  }

  override fun validate(settings: BazilSettings): Boolean {
    // TODO
    return true
  }

  override fun fillUi(canvas: PaintAwarePanel, indentLevel: Int) {
    // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun apply(settings: BazilSettings) {
    // TODO
  }

  override fun disposeUIResources() {
    // TODO
  }

  override fun showUi(show: Boolean) {
    // TODO
  }

  override fun reset() {
    // TODO
  }

}

class BazilProjectSettingsControl(settings: BazilProjectSettings) :
  AbstractExternalProjectSettingsControl<BazilProjectSettings>(null, settings, null) {
  override fun resetExtraSettings(isDefaultModuleCreation: Boolean) {
    // TODO
  }

  override fun applyExtraSettings(settings: BazilProjectSettings) {
    // TODO
  }

  override fun validate(settings: BazilProjectSettings): Boolean {
    // TODO
    return true
  }

  override fun fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {
    // TODO
  }

  override fun isExtraSettingModified(): Boolean {
    // TODO
    return false
  }

}

class BazilProjectImportBuilder(dataManager: ProjectDataManager) :
  AbstractExternalProjectImportBuilder<ImportFromBazilControl>(
    dataManager, { ImportFromBazilControl() }, SYSTEM_ID
  ) {
  override fun getName() = "Bazil"

  override fun beforeCommit(dataNode: DataNode<ProjectData>, project: Project) {
    val javaProjectNode = ExternalSystemApiUtil.find(dataNode, JavaProjectData.KEY) ?: return

    val externalLanguageLevel = javaProjectNode.data.languageLevel
    val languageLevelExtension = LanguageLevelProjectExtension.getInstance(project)
    if (externalLanguageLevel != languageLevelExtension.languageLevel) {
      languageLevelExtension.languageLevel = externalLanguageLevel
    }
  }

  override fun getExternalProjectConfigToUse(file: File): File = when {
    file.isDirectory -> file
    else -> file.parentFile
  }

  override fun applyExtraSettings(context: WizardContext) {
    val node = externalProjectNode
    if (node == null) {
      return
    }
    val javaProjectNode = ExternalSystemApiUtil.find(node, JavaProjectData.KEY)
    if (javaProjectNode != null) {
      val data = javaProjectNode.data
      context.compilerOutputDirectory = data.compileOutputPath
      val version = data.jdkVersion
      val jdk = JavaSdkVersionUtil.findJdkByVersion(version)
      if (jdk != null) {
        context.projectJdk = jdk
      }
    }
  }

  override fun getIcon() = AllIcons.Nodes.Plugin

  override fun doPrepare(context: WizardContext) {
    var pathToUse = fileToImport
    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(pathToUse)
    if (file != null && !file.isDirectory && file.parent != null) {
      pathToUse = file.parent.path
    }

    val importFromBazilControl = getControl(context.project)
    importFromBazilControl.setLinkedProjectPath(pathToUse)
  }
}

class BazilProjectOpenProcessor(importBuilder: BazilProjectImportBuilder) :
  ProjectOpenProcessorBase<BazilProjectImportBuilder>(importBuilder) {
  override fun getSupportedExtensions(): Array<String> {
    return arrayOf("BUILD", "WORKSPACE")
  }
}

class BazilProjectImportProvider(builder: BazilProjectImportBuilder) :
  AbstractExternalProjectImportProvider(builder, SYSTEM_ID) {
}

class BazilLocalSettings(project: Project) :
  AbstractExternalSystemLocalSettings<BazilLocalSettings.State>(
    SYSTEM_ID, project,
    State()
  ) {
  class State : AbstractExternalSystemLocalSettings.State() {
  }
}

class BazilExecutionSettings(val project: Project) : ExternalSystemExecutionSettings() {
}

// bazel structure

data class ModuleInfo(
  val data: DataNode<ModuleData>,
  val parsed: Parser.ParseResult
)

data class ModuleInfo2(
  val deps: List<String>,
  val runtimeDeps: List<String>
)

data class ModuleDeps(
  val compileLibDeps: Set<LibraryData>,
  val compileModuleDeps: Set<ModuleData>,
  val testLibDeps: Set<LibraryData>
)

val modules: MutableMap<String, ModuleInfo> = mutableMapOf()
val modules2: MutableMap<String, Map<String, ModuleInfo2>> = mutableMapOf()
val moduleDependencies: MutableMap<String, ModuleDeps> = mutableMapOf()

//


class BazilProjectResolver : ExternalSystemProjectResolver<BazilExecutionSettings> {
//  private val myCancellationMap = MultiMap.create<ExternalSystemTaskId, CancellationTokenSource>()

  override fun resolveProjectInfo(
    id: ExternalSystemTaskId,
    projectPath: String,
    isPreviewMode: Boolean,
    settings: BazilExecutionSettings?,
    listener: ExternalSystemTaskNotificationListener
  ): DataNode<ProjectData> {

    modules.clear()
    moduleDependencies.clear()


    if (isPreviewMode) {
      // Create project preview model w/o building the project model
      // * Slow project open
      // * Ability to open an invalid projects (e.g. with errors in build scripts)
      val projectName = File(projectPath).name
      val projectData = ProjectData(SYSTEM_ID, projectName, projectPath, projectPath)
      val projectDataNode = DataNode(ProjectKeys.PROJECT, projectData, null)

      projectDataNode
        .createChild(
          ProjectKeys.MODULE, ModuleData(
            projectName, SYSTEM_ID, EmptyModuleType.EMPTY_MODULE,
            projectName, projectPath, projectPath
          )
        )
        .createChild(ProjectKeys.CONTENT_ROOT, ContentRootData(SYSTEM_ID, projectPath))
      return projectDataNode
    } else {
      val projectRoot = File(projectPath)
      val projectName = projectRoot.name
      val projectData = ProjectData(SYSTEM_ID, projectName, projectPath, projectPath)
      val projectDataNode: DataNode<ProjectData> = DataNode(ProjectKeys.PROJECT, projectData, null)

      // let's assume that root doesn't have any code
      val root = projectDataNode
        .createChild(
          ProjectKeys.MODULE, ModuleData(
            projectName, SYSTEM_ID, JavaModuleType.getModuleType().id,
            projectName, projectPath, projectPath
          )
        )
        .createChild(ProjectKeys.CONTENT_ROOT, ContentRootData(SYSTEM_ID, projectPath))
      for (child in projectRoot.listFiles()) {
        collectProjects(projectRoot, child, root)
      }

      val libManager = BuildFileManager(settings!!.project, projectRoot, projectDataNode)


      // reprocess modules
      modules.forEach { id, (moduleNode: DataNode<ModuleData>, parsed) ->

        println("processing module $id")

        val deps = moduleDependencies.getOrPut(id, getModuleDeps(id, libManager))

        for (lib in deps.compileLibDeps) {
          moduleNode.createChild(
            ProjectKeys.LIBRARY_DEPENDENCY,
            LibraryDependencyData(moduleNode.data, lib, LibraryLevel.PROJECT)
          )
        }
        for (module in deps.compileModuleDeps) {
          moduleNode.createChild(
            ProjectKeys.MODULE_DEPENDENCY,
            ModuleDependencyData(moduleNode.data, module)
          )
        }
        // hack, junit needs everywhere
        for (testLib in deps.testLibDeps.plus(libManager.findDependency("//third_party/jvm/junit:junit"))) {
          moduleNode.createChild(
            ProjectKeys.LIBRARY_DEPENDENCY,
            LibraryDependencyData(moduleNode.data, testLib, LibraryLevel.PROJECT).apply {
              scope = DependencyScope.TEST
            }
          )
        }
      }

      println("DOOOONE")

      return projectDataNode
    }
  }

  private fun getModuleDeps(
    id: String,
    buildFileManager: BuildFileManager
  ): () -> ModuleDeps {
    return {
      val (moduleNode: DataNode<ModuleData>, parsed) = modules[id]!!
      println("processing $id")
      val funCallExpressions = findAllLibDefinitions(parsed)

      val moduleDeps = findAllAt(
        funCallExpressions,
        listOf("datanucleus_java_library", "java_library", "java_binary"),
        "deps"
      )
      val testDeps = findAllAt(funCallExpressions, listOf("junit_tests"), "deps")

      val compileLibDeps = hashSetOf<LibraryData>()
      val compileModuleDeps = hashSetOf<ModuleData>()
      val testLibDeps = hashSetOf<LibraryData>()
      val testModuleDeps = hashSetOf<ModuleData>()

      for (dep in testDeps) {
        if (!dep.startsWith("//third_party")) {
          println("bug test dep")
        } else {
          testLibDeps.addAll(buildFileManager.findDependency(dep))
        }
      }

      for (dep in moduleDeps) {
        if (!dep.startsWith("//third_party")) {
          val moduleDepNode = modules[dep]
          if (moduleDepNode != null) {
            compileModuleDeps.add(moduleDepNode.data.data)

            if (dep != id) {
              // we don't like infinite recursions
              val dps = moduleDependencies.getOrPut(dep, getModuleDeps(dep, buildFileManager))
              compileLibDeps.addAll(dps.compileLibDeps)
              compileModuleDeps.addAll(dps.compileModuleDeps)
              testLibDeps.addAll(dps.testLibDeps)
            }


          } else if (dep.startsWith(":")) {
            // ignore, in the same module
          } else if (dep.startsWith("@")) {
            val librariesData: LibraryData = buildFileManager.getActual(dep)
            compileLibDeps.add(librariesData)
          } else if (dep.endsWith(":test-lib")) {
            // todo
          } else {
            println("bug $dep")
          }
        } else {
          val librariesData = buildFileManager.findDependency(dep)
          compileLibDeps.addAll(librariesData)
        }
      }

      ModuleDeps(compileLibDeps, compileModuleDeps, testLibDeps)
    }
  }


  private fun collectProjects(
    projectRoot: File,
    root: File,
    node: DataNode<ContentRootData>
  ) {
    if (!root.isDirectory) {
      return
    }
    val projectName = root.name
    val projectPath = root.absolutePath
    val files = root.listFiles()
    var bazelRoot = false
    var srcFolder = false
    for (child in files) {
      if (child.name == "BUILD" && child.length() != 0L) {
        bazelRoot = true
      }
      if (child.name == "src" && child.isDirectory) {
        srcFolder = true
      }
    }
    if (bazelRoot && srcFolder) {
      val id = "//${File(projectPath).relativeTo(projectRoot)}"
      val module = node.createChild(
        ProjectKeys.MODULE, ModuleData(
          id, SYSTEM_ID, JavaModuleType.getModuleType().id,
          projectName, projectPath, projectPath
        )
      )

      val bazelFile = File("$projectPath/BUILD")

      val parsed: Parser.ParseResult =
        Parser.parseFile(
          ParserInputSource.create(bazelFile.readBytes(), PathFragment.create(bazelFile.absolutePath)),
          {})

      modules[id] = ModuleInfo(module, parsed)

      val content: DataNode<ContentRootData> = module
        .createChild(ProjectKeys.CONTENT_ROOT, ContentRootData(SYSTEM_ID, projectPath))

      content.data.storePath(ExternalSystemSourceType.SOURCE, "$projectPath/src/main/java")
      content.data.storePath(ExternalSystemSourceType.RESOURCE, "$projectPath/src/main/resources")
      content.data.storePath(ExternalSystemSourceType.RESOURCE, "$projectPath/src/main/webapp")
      content.data.storePath(ExternalSystemSourceType.TEST, "$projectPath/src/test/java")
      content.data.storePath(ExternalSystemSourceType.TEST_RESOURCE, "$projectPath/src/test/resources")
    } else {
      for (child in files) {
        collectProjects(projectRoot, child, node)
      }
    }
  }

  override fun cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
    return true
  }
}

private fun findAllAt(
  funCallExpressions: List<FuncallExpression>,
  funNames: List<String>,
  childName: String
): List<String> {
  val moduleDeps = funCallExpressions
    .filter { (it.function as? Identifier)?.name in funNames }
    .flatMap { it.arguments }
    .filterIsInstance<Argument.Keyword>()
    .filter { it.name == childName }
    .map { it.value }
    .filterIsInstance<ListLiteral>()
    .flatMap { it.elements }
    .filterIsInstance<StringLiteral>()
    .map { it.value }
  return moduleDeps
}

private fun findAllLibDefinitions(parsed: Parser.ParseResult): List<FuncallExpression> {
  return parsed.statements
    .filterIsInstance<ExpressionStatement>().map { it.expression }
    .filterIsInstance<FuncallExpression>()
}


class BuildFileManager(
  project: Project,
  projectRoot: File,
  root: DataNode<ProjectData>
) {
  private val actualLibraries: MutableMap<String, LibraryData> = mutableMapOf()
  private val libraryMapping: MutableMap<String, LibraryData> = mutableMapOf()

  private val buidFiles: Map<String, Map<String, Set<LibraryData>>>

  init {
    val workspaceFiles = projectRoot.walk()
      .filter { it.name == "workspace.bzl" }

    // Create a maps of libs
    val mapper = ObjectMapper()
    val factory = TypeFactory.defaultInstance()
    val type = factory.constructMapType(HashMap::class.java, String::class.java, Any::class.java)
    for (workspaceFile in workspaceFiles) {
      for (line in workspaceFile.readLines()) {
        if (line.contains("""{"artifact": """")) {
          val artifact = mapper.readValue<Map<String, Any>>(line.trim(','), type)
          val bind = artifact["bind"] as String
          val name = artifact["name"] as String
          val url = artifact["url"] as String
          val actual = artifact["actual"] as String
          val artifactCoordinates = artifact["artifact"] as String
          val (groupId, artifactId, version) = artifactCoordinates.split(":")

          val jarFile = File(url.replace("https://repo.maven.apache.org/maven2/", "/Users/sergey/.m2/repository/"))
          val unresolved = !jarFile.exists()
          if (unresolved) {
            val libraryProperties = RepositoryLibraryProperties(
              groupId,
              artifactId,
              version,
              false,
              emptyList()
            )
            JarRepositoryManager.loadDependenciesModal(
              project,
              libraryProperties,
              false,
              false,
              jarFile.parentFile.absolutePath,
              null
            )
          }

          val libraryData = LibraryData(SYSTEM_ID, artifactCoordinates)
          libraryData.setGroup(groupId)
          libraryData.artifactId = artifactId
          libraryData.version = version
          libraryData.addPath(LibraryPathType.BINARY, jarFile.absolutePath)
          val sourcePath = jarFile.absolutePath.substring(0, jarFile.absolutePath.length - 4) + "-sources.jar"
          libraryData.addPath(LibraryPathType.SOURCE, sourcePath)

          libraryMapping[bind] = libraryData
          actualLibraries[actual] = libraryData
          root.createChild(
            ProjectKeys.LIBRARY,
            libraryData
          )
          println("adding library $groupId:$artifactId:$version")
        }
      }
    }

    val thirdPartyMapping = projectRoot.walk()
      .filter { it.name == "BUILD" }
      .map { buildFile ->
        val parsedBuildFile: Parser.ParseResult =
          Parser.parseFile(
            ParserInputSource.create(buildFile.readBytes(), PathFragment.create(buildFile.absolutePath)),
            {})
        val funCallExpressions = findAllLibDefinitions(parsedBuildFile)

        // libname -> list of exports
        val libs: MutableMap<String, List<String>> = hashMapOf()
        val javaLibs: List<List<Argument.Keyword>> = funCallExpressions
          .filter { (it.function as? Identifier)?.name in listOf("java_library") }
          .map { it.arguments.filterIsInstance<Argument.Keyword>() }
        for (lib in javaLibs) {
          var name: String? = null
          var exports: List<String>? = null

          for (argument in lib) {
            if (argument.name == "name") {
              name = (argument.value as? StringLiteral)?.value
            } else if (argument.name == "exports") {
              val libList = argument.value
              if (libList is ListLiteral) {
                exports = libList.elements
                  .filterIsInstance<StringLiteral>()
                  .map { it.value }
              }
            }
          }

          if (name != null && exports != null) {
            libs[name] = exports
          } else {
            println("Failed to process $lib")
          }
        }
        "//${buildFile.parentFile.relativeTo(projectRoot).path}" to libs
      }
      .toMap()

    val thrirdPartyLibCache = mutableMapOf<String, MutableMap<String, Set<LibraryData>>>()

    fun findLibraryData(path: String, name: String): Set<LibraryData> {
      val cachedLibs = thrirdPartyLibCache[path]?.get(name)
      if (cachedLibs != null) {
        return cachedLibs
      }
      val exports: List<String> = thirdPartyMapping[path]?.get(name) ?: run {
        println("bug $name")
        emptyList<String>()
      }
      val libraries = hashSetOf<LibraryData>()
      for (export in exports) {
        when {
          export.startsWith(":") -> {
            libraries.addAll(findLibraryData(path, export.substring(1)))
          }
          export.startsWith("//external:") -> {
            val defined = libraryMapping[export.substring("//external:".length)]!!
            libraries.add(defined)
          }
          export.startsWith("//third_party/") -> {
            if (export.contains(":")) {
              val libPath = export.substring(0, export.lastIndexOf(':'))
              val libName = export.substringAfter(':')
              libraries.addAll(findLibraryData(libPath, libName))
            } else {
              val libPath = export
              val libName = export.substringAfterLast('/')
              libraries.addAll(findLibraryData(libPath, libName))
            }
          }
          else -> {
            println("What is $export?")
          }
        }
      }
      thrirdPartyLibCache
        .computeIfAbsent(path, { hashMapOf() })
        .put(name, libraries)
      return libraries
    }

    val result: MutableMap<String, MutableMap<String, Set<LibraryData>>> = hashMapOf()
    for ((path, libDir: Map<String, List<String>>) in thirdPartyMapping) {
      for ((name, _) in libDir) {
        val libs = findLibraryData(path, name)
        result
          .computeIfAbsent(path, { hashMapOf() })
          .put(name, libs)

      }
    }

    buidFiles = result
  }

  fun findDependency(dep: String): Set<LibraryData> = when {
    dep.contains(":") -> {
      val libPath = dep.substring(dep.lastIndexOf(':'))
      val libName = dep.substringAfter(':')
      buidFiles[libPath]?.get(libName) ?: run {
        println("missing dep $dep")
        emptySet<LibraryData>()
      }
    }
    else -> {
      val libPath = dep
      val libName = dep.substringAfterLast('/')
      buidFiles[libPath]!!.get(libName) ?: run {
        println("missing dep $dep")
        emptySet<LibraryData>()
      }
    }
  }

  fun getActual(dep: String): LibraryData {
    return actualLibraries[dep]!!
  }
}

class BazilTaskManager : ExternalSystemTaskManager<BazilExecutionSettings> {
  override fun cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
    return true
  }
}

class BazilManager :
  ExternalSystemManager<BazilProjectSettings, BazilSettingsListener, BazilSettings, BazilLocalSettings, BazilExecutionSettings> {
  override fun enhanceRemoteProcessing(parameters: SimpleJavaParameters) {
    // TODO //To change body of created functions use File | Settings | File Templates.
  }

  override fun getProjectResolverClass(): Class<out ExternalSystemProjectResolver<BazilExecutionSettings>> {
    return BazilProjectResolver::class.java
  }

  override fun getSettingsProvider() =
    Function { project: Project -> BazilSettings.getInstance(project) }

  override fun getExecutionSettingsProvider() =
    Function<Pair<Project, String>, BazilExecutionSettings> {
      val project = it.first
      BazilExecutionSettings(project)
    }

  override fun getExternalProjectDescriptor(): FileChooserDescriptor {
    return FileChooserDescriptorFactory.createSingleFileDescriptor()
  }

  override fun getSystemId() = SYSTEM_ID

  override fun getTaskManagerClass(): Class<out ExternalSystemTaskManager<BazilExecutionSettings>> {
    return BazilTaskManager::class.java
  }

  override fun getLocalSettingsProvider() = Function<Project, BazilLocalSettings> { project ->
    ServiceManager.getService<BazilLocalSettings>(
      project, BazilLocalSettings::class.java
    )
  }
}
