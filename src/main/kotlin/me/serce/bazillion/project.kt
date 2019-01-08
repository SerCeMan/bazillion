package me.serce.bazillion

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.google.devtools.build.lib.syntax.*
import com.google.devtools.build.lib.vfs.PathFragment
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.externalSystem.JavaProjectData
import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.WizardContext
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

class BazilExecutionSettings : ExternalSystemExecutionSettings() {
}

// bazel structure

data class ModuleInfo(
  val data: DataNode<ModuleData>,
  val parsed: Parser.ParseResult
)

data class ModuleDeps(
  val compileLibDeps: List<LibraryDependencyData>,
  val compileModuleDeps: List<ModuleDependencyData>,
  val testLibDeps: List<LibraryDependencyData>
)

val modules: MutableMap<String, ModuleInfo> = mutableMapOf()
val moduleDeps: MutableMap<String, ModuleInfo> = mutableMapOf()

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

    if (isPreviewMode) {
      // Create project preview model w/o request to gradle, there are two main reasons for the it:
      // * Slow project open - even the simplest project info provided by gradle can be gathered too long (mostly because of new gradle distribution download and downloading buildscript dependencies)
      // * Ability to open  an invalid projects (e.g. with errors in build scripts)
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

      val libManager = LibManager(projectRoot, projectDataNode)


      // reprocess modules
      modules.forEach { id, (moduleNode: DataNode<ModuleData>, parsed) ->

        val funCallExpressions = findAllLibDefinitions(parsed)

        val moduleDeps = findAllAt(funCallExpressions, "java_library", "deps")
        val testDeps = findAllAt(funCallExpressions, "junit_tests", "deps")

        val compileLibDeps = mutableListOf<LibraryDependencyData>()
        val compileModuleDeps = mutableListOf<ModuleDependencyData>()
        val testLibDeps = mutableListOf<LibraryDependencyData>()

        for (dep in testDeps) {
          if (!dep.startsWith("//third_party")) {
            println("bug")
          } else {
            val librariesData: List<LibraryData> = libManager.getLib(dep)
            for (libData in librariesData) {
              testLibDeps.add(
                LibraryDependencyData(moduleNode.data, libData, LibraryLevel.PROJECT).apply {
                  scope = DependencyScope.TEST
                }
              )
            }
          }

        }

        for (dep in moduleDeps) {
          if (!dep.startsWith("//third_party")) {
            val moduleDepNode = modules[dep]
            if (moduleDepNode != null) {
              compileModuleDeps.add(ModuleDependencyData(moduleNode.data, moduleDepNode.data.data))
            } else {
              println("bug")
            }
          } else {
            val librariesData: List<LibraryData> = libManager.getLib(dep)
            for (libData in librariesData) {
              testLibDeps.add(LibraryDependencyData(moduleNode.data, libData, LibraryLevel.PROJECT))
            }
          }
        }
        val deps = ModuleDeps(compileLibDeps, compileModuleDeps, testLibDeps)


        for (lib in deps.compileLibDeps) {
          moduleNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, lib)
        }
        for(module in deps.compileModuleDeps) {
          moduleNode.createChild(ProjectKeys.MODULE_DEPENDENCY, module)
        }
        for(testLib in deps.testLibDeps) {
          moduleNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, testLib)
        }


      }

      return projectDataNode
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
      if (child.name == "BUILD") {
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
  funName: String,
  childName: String
): List<String> {
  val moduleDeps = funCallExpressions
    .filter { (it.function as? Identifier)?.name == funName }
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


class LibManager(
  val projectRoot: File,
  root: DataNode<ProjectData>
) {
  private val cached: MutableMap<String, List<LibraryData>> = mutableMapOf()
  private val libraryMapping: MutableMap<String, LibraryData> = mutableMapOf()

  init {
    val mapper = ObjectMapper()
    val factory = TypeFactory.defaultInstance()
    val type = factory.constructMapType(HashMap::class.java, String::class.java, Any::class.java)
    val workspaceFile = File("${projectRoot.absolutePath}/third_party/workspace.bzl")
    for (line in workspaceFile.readLines()) {
      if (line.contains("""{"artifact": """")) {
        val artifact = mapper.readValue<Map<String, Any>>(line.trim(','), type)
        val bind = artifact["bind"] as String
        val name = artifact["name"] as String
        val url = artifact["url"] as String

        val (groupId, artifactId, version) = (artifact["artifact"] as String).split(":")
        val libraryData = LibraryData(SYSTEM_ID, name)
        libraryData.setGroup(groupId)
        libraryData.artifactId = artifactId
        libraryData.version = version
        libraryData.addPath(
          LibraryPathType.BINARY,
          File(url.replace("https://repo.maven.apache.org/maven2/", "/Users/sergey/.m2/repository/")).absolutePath
        )
        libraryMapping[bind] = libraryData
        root.createChild(
          ProjectKeys.LIBRARY,
          libraryData
        )
      }
    }
  }

  fun getLib(dep: String): List<LibraryData> {
    return cached.getOrPut(dep) {
      val depPath = when (dep.contains(":")) {
        true -> dep.substring(0, dep.lastIndexOf(':'))
        else -> dep
      }
      val buildFile = File("${projectRoot.absoluteFile}$depPath/BUILD")

      val parsed: Parser.ParseResult =
        Parser.parseFile(
          ParserInputSource.create(buildFile.readBytes(), PathFragment.create(buildFile.absolutePath)),
          {})

      val funCallExpressions = findAllLibDefinitions(parsed)
      val libs = findAllAt(funCallExpressions, "java_library", "exports")

      val result = mutableListOf<LibraryData>()
      for (lib in libs) {
        if (lib.startsWith("//external:")) {
          val defined = libraryMapping[lib.substring("//external:".length)]
          if (defined != null) {
            result.add(defined)
          } else {
            println("oooopsie $lib")
          }
        }
      }

      return result
    }
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
    Function<Pair<Project, String>, BazilExecutionSettings> { pair ->
      val project = pair.first

      val result = BazilExecutionSettings()
      result
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
