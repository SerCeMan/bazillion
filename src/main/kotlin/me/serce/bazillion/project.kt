package me.serce.bazillion

import com.google.devtools.build.lib.syntax.*
import com.google.devtools.build.lib.vfs.PathFragment
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.externalSystem.JavaProjectData
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.components.*
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
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.projectImport.ProjectOpenProcessorBase
import com.intellij.util.Function
import com.intellij.util.messages.Topic
import java.io.File
import java.util.*

val SYSTEM_ID = ProjectSystemId("BAZIL")
val TOPIC = Topic.create<BazilSettingsListener>(
  "Bazil-specific settings",
  BazilSettingsListener::class.java
)

val LOG = Logger.getInstance("#me.serce.bazillion")

class BazilProjectSettings : ExternalProjectSettings() {
  override fun clone(): BazilProjectSettings {
    val clone = BazilProjectSettings()
    copyTo(clone)
    return clone
  }
}

// Allows for running in the same process
val a = run {
  Registry.addKey("BAZIL.system.in.process", "", true, false)
}

interface BazilSettingsListener : ExternalSystemSettingsListener<BazilProjectSettings>

@State(
  name = "BazilSettings",
  storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class BazilSettings(project: Project) :
  AbstractExternalSystemSettings<BazilSettings, BazilProjectSettings, BazilSettingsListener>(TOPIC, project),
  PersistentStateComponent<BazilSettings.State> {

  companion object {
    fun getInstance(project: Project): BazilSettings =
      ServiceManager.getService<BazilSettings>(project, BazilSettings::class.java)
  }

  override fun getState(): BazilSettings.State {
    val state = State()
    fillState(state)
    return state
  }

  override fun loadState(state: BazilSettings.State) {
    super.loadState(state)
  }

  override fun checkSettings(old: BazilProjectSettings, current: BazilProjectSettings) {}
  override fun copyExtraSettingsFrom(settings: BazilSettings) {}
  override fun subscribe(listener: ExternalSystemSettingsListener<BazilProjectSettings>) {}

  data class State(
    var linkedSettings: Set<BazilProjectSettings> = TreeSet()
  ) : AbstractExternalSystemSettings.State<BazilProjectSettings> {
    override fun getLinkedExternalProjectsSettings() = linkedSettings

    override fun setLinkedExternalProjectsSettings(settings: Set<BazilProjectSettings>?) {
      if (settings != null) {
        linkedSettings = settings
      }
    }
  }
}

class ImportFromBazilControl :
  AbstractImportFromExternalSystemControl<BazilProjectSettings, BazilSettingsListener, BazilSettings>
    (SYSTEM_ID, BazilSettings(ProjectManager.getInstance().defaultProject), BazilProjectSettings(), true) {
  override fun createProjectSettingsControl(settings: BazilProjectSettings) = BazilProjectSettingsControl(settings)
  override fun onLinkedProjectPathChange(path: String) {}
  override fun createSystemSettingsControl(settings: BazilSettings) = BazilSystemSettingsControl()
}

class BazilSystemSettingsControl : ExternalSystemSettingsControl<BazilSettings> {
  override fun isModified() = false
  override fun validate(settings: BazilSettings) = true
  override fun fillUi(canvas: PaintAwarePanel, indentLevel: Int) {}
  override fun apply(settings: BazilSettings) {}
  override fun disposeUIResources() {}
  override fun showUi(show: Boolean) {}
  override fun reset() {}
}

class BazilProjectSettingsControl(settings: BazilProjectSettings) :
  AbstractExternalProjectSettingsControl<BazilProjectSettings>(null, settings, null) {
  override fun resetExtraSettings(isDefaultModuleCreation: Boolean) {}
  override fun applyExtraSettings(settings: BazilProjectSettings) {}
  override fun validate(settings: BazilProjectSettings) = true
  override fun fillExtraControls(content: PaintAwarePanel, indentLevel: Int) {}
  override fun isExtraSettingModified() = false
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

  override fun getIcon() = BazilIcons.Bazil

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
  override fun getSupportedExtensions() = arrayOf("BUILD", "WORKSPACE")
}

class BazilProjectImportProvider(builder: BazilProjectImportBuilder) :
  AbstractExternalProjectImportProvider(builder, SYSTEM_ID)

class BazilExecutionSettings(val project: Project) : ExternalSystemExecutionSettings()

class BazilProjectResolver : ExternalSystemProjectResolver<BazilExecutionSettings> {
  override fun resolveProjectInfo(
    id: ExternalSystemTaskId,
    projectPath: String,
    isPreviewMode: Boolean,
    settings: BazilExecutionSettings?,
    listener: ExternalSystemTaskNotificationListener
  ): DataNode<ProjectData> {

    val modules: MutableMap<String, DataNode<ModuleData>> = mutableMapOf()

    val projectRoot = File(projectPath)
    val projectName = projectRoot.name
    val projectData = ProjectData(SYSTEM_ID, projectName, projectPath, projectPath)
    val projectDataNode: DataNode<ProjectData> = DataNode(ProjectKeys.PROJECT, projectData, null)

    // let's assume that root doesn't have any code
    val root = projectDataNode
      .createChild(
        ProjectKeys.MODULE, ModuleData(
          projectName, SYSTEM_ID, ModuleTypeId.JAVA_MODULE,
          projectName, projectPath, projectPath
        )
      )
      .createChild(ProjectKeys.CONTENT_ROOT, ContentRootData(SYSTEM_ID, projectPath))

    for (bazelOut in listOf(
      "$projectPath/bazel-bin",
      "$projectPath/bazel-$projectName",
      "$projectPath/bazel-genfiles",
      "$projectPath/bazel-out",
      "$projectPath/bazel-testlogs"
    )) {
      root.data.storePath(ExternalSystemSourceType.EXCLUDED, bazelOut)
    }

    if (isPreviewMode) {
      return projectDataNode
    }

    val progress: ProgressIndicator = ProgressIndicatorProvider.getInstance().progressIndicator
    progress.text = "collecting projects"
    LOG.info("collecting projects")
    collectProjects(modules, projectRoot, projectRoot, root)

    val project = settings!!.project
    val libManager = LibManager.getInstance(project)
    libManager.refresh(progress)
    for (lib in libManager.getAllLibs()) {
      root.createChild(ProjectKeys.LIBRARY, lib)
    }
    val ruleManager = RuleManager(project, projectRoot, modules)

    // reprocess modules
    progress.text = "updating module dependencies"
    modules.forEach { moduleIdd, moduleNode: DataNode<ModuleData> ->

      LOG.info("processing module $moduleIdd")

      val rules = ruleManager.getRules(moduleIdd)
      if (rules != null) {
        for ((name, rule) in rules) {
          when {
            rule.kind == RuleKind.JUNIT_TESTS || name == "test-lib" -> {
              for (dep in sequenceOf(rule.deps, rule.exports, rule.runtimeDeps).flatten()) {
                when (dep) {
                  is LibraryData -> moduleNode.createChild(
                    ProjectKeys.LIBRARY_DEPENDENCY,
                    LibraryDependencyData(moduleNode.data, dep, LibraryLevel.PROJECT).apply {
                      scope = DependencyScope.TEST
                    }
                  )
                  is ModuleData -> moduleNode.createChild(
                    ProjectKeys.MODULE_DEPENDENCY,
                    ModuleDependencyData(moduleNode.data, dep).apply {
                      scope = DependencyScope.TEST
                    }
                  )
                  else -> {
                    LOG.error("Unsupported dependency $dep for unit tests")
                  }
                }
              }
            }
            rule.kind in listOf(RuleKind.JAVA_LIBRARY, RuleKind.JAVA_BINARY, RuleKind.DATANUCLEUS_JAVA_LIBRARY) -> {
              for (dep in sequenceOf(rule.deps, rule.exports, rule.runtimeDeps).flatten()) {
                when (dep) {
                  is LibraryData -> moduleNode.createChild(
                    ProjectKeys.LIBRARY_DEPENDENCY,
                    LibraryDependencyData(moduleNode.data, dep, LibraryLevel.PROJECT)
                  )
                  is ModuleData -> moduleNode.createChild(
                    ProjectKeys.MODULE_DEPENDENCY,
                    ModuleDependencyData(moduleNode.data, dep)
                  )
                  else -> {
                    LOG.error("Unsupported dependency $dep for java module")
                  }
                }
              }
            }
            rule.kind in listOf(RuleKind.GEN_RULE, RuleKind.DUMMY) -> {
              // do nothing
            }
            else -> {
              LOG.error("Unknown rule ${rule.kind}")
            }
          }
        }
      } else {
        LOG.warn("Missing rules in the module $moduleIdd. ")
      }
    }
    return projectDataNode
  }

  private fun collectProjects(
    modules: MutableMap<String, DataNode<ModuleData>>,
    projectRoot: File,
    root: File,
    node: DataNode<ContentRootData>
  ) {
    if (isNonProjectDirectory(root)) {
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
      if (bazelRoot && srcFolder) {
        break
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

      modules[id] = module

      module.data.apply {
        isInheritProjectCompileOutputPath = false
        setCompileOutputPath(ExternalSystemSourceType.SOURCE, "$projectPath/target/classes")
        setCompileOutputPath(ExternalSystemSourceType.TEST, "$projectPath/target/test-classes")
        setCompileOutputPath(ExternalSystemSourceType.RESOURCE, "$projectPath/target/classes")
        setCompileOutputPath(ExternalSystemSourceType.TEST_RESOURCE, "$projectPath/target/test-classes")
      }

      val content = module.createChild(ProjectKeys.CONTENT_ROOT, ContentRootData(SYSTEM_ID, projectPath))
      content.data.apply {
        storePath(ExternalSystemSourceType.SOURCE, "$projectPath/src/main/java")
        storePath(ExternalSystemSourceType.RESOURCE, "$projectPath/src/main/resources")
        storePath(ExternalSystemSourceType.RESOURCE, "$projectPath/src/main/webapp")
        storePath(ExternalSystemSourceType.TEST, "$projectPath/src/test/java")
        storePath(ExternalSystemSourceType.TEST_RESOURCE, "$projectPath/src/test/resources")
        storePath(ExternalSystemSourceType.EXCLUDED, "$projectPath/target")
      }

    } else {
      for (child in files) {
        if (child.isDirectory) {
          collectProjects(modules, projectRoot, child, node)
        }
      }
    }
  }

  override fun cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener) = true
}

@State(
  name = "BazilLocalSettings",
  storages = [Storage(StoragePathMacros.CACHE_FILE)]
)
class BazilLocalSettings(project: Project) :
  AbstractExternalSystemLocalSettings<AbstractExternalSystemLocalSettings.State>(SYSTEM_ID, project, State()),
  PersistentStateComponent<AbstractExternalSystemLocalSettings.State>


enum class RuleKind(val funName: String) {
  JAVA_LIBRARY("java_library"),
  JAVA_BINARY("java_binary"),
  DATANUCLEUS_JAVA_LIBRARY("datanucleus_java_library"),
  JUNIT_TESTS("junit_tests"),
  GEN_RULE("genrule"),
  DUMMY("dummy$");

  companion object {
    val index: Map<String, RuleKind> = values().map { it.funName to it }.toMap()
    fun byFunName(funName: String): RuleKind? = index[funName]
  }
}

data class Rule(
  val kind: RuleKind,
  val exports: Set<AbstractNamedData>,
  val deps: Set<AbstractNamedData>,
  val runtimeDeps: Set<AbstractNamedData>
)

fun isNonProjectDirectory(it: File) = it.isDirectory && (
  it.name.startsWith(".") ||
    it.name.startsWith("bazel-") ||
    it.name == "node_modules" ||
    it.name == "out" ||
    it.name == "target")


class RuleManager(
  project: Project,
  projectRoot: File,
  modules: MutableMap<String, DataNode<ModuleData>>
) {
  private val rules: Map<String, Map<String, Rule>>

  init {
    val libManager = LibManager.getInstance(project)

    data class RawRule(
      val kind: RuleKind,
      val exports: List<String>,
      val deps: List<String>,
      val runtimeDeps: List<String>
    )

    LOG.info("searching for BUILDs")
    val ruleMapping = projectRoot.walk()
      .onEnter { !isNonProjectDirectory(it) }
      .filter { it.name == "BUILD" }
      .map { buildFile ->
        val parsedBuildFile: Parser.ParseResult =
          Parser.parseFile(
            ParserInputSource.create(buildFile.readBytes(), PathFragment.create(buildFile.absolutePath)),
            {})
        val funCallExpressions = parsedBuildFile.statements
          .filterIsInstance<ExpressionStatement>().map { it.expression }
          .filterIsInstance<FuncallExpression>()

        // libname -> rule
        val allRules: MutableMap<String, RawRule> = hashMapOf()
        val javaRules = funCallExpressions
          .mapNotNull { funCall ->
            ((funCall.function as? Identifier)?.name)?.let { name ->
              RuleKind.byFunName(name)?.let { it to funCall }
            }
          }
          .map { (kind, funCall) -> kind to funCall.arguments.filterIsInstance<Argument.Keyword>() }
        for ((kind, funCall) in javaRules) {
          var name: String? = null
          val fields = mutableMapOf<String, List<String>>()

          for (argument in funCall) {
            val argName = argument.name
            if (argName == "name") {
              name = (argument.value as? StringLiteral)?.value
            } else if (argName != null && argName in listOf("exports", "deps", "runtimeDeps")) {
              val libList = argument.value
              if (libList is ListLiteral) {
                fields[argName] = libList.elements
                  .filterIsInstance<StringLiteral>()
                  .map { it.value }
              }
            }
          }

          if (name != null) {
            allRules[name] = RawRule(
              kind,
              fields["exports"] ?: emptyList(),
              fields["deps"] ?: emptyList(),
              fields["runtimeDeps"] ?: emptyList()
            )
          } else {
            println("Failed to process $funCall")
          }
        }
        "//${buildFile.parentFile.relativeTo(projectRoot).path}" to allRules
      }
      .toMap()

    val ruleCache = mutableMapOf<String, MutableMap<String, Rule>>()

    fun findRule(path: String, name: String): Rule {
      val cachedRule = ruleCache[path]?.get(name)
      if (cachedRule != null) {
        return cachedRule
      }
      val exports = hashSetOf<AbstractNamedData>()
      val deps = hashSetOf<AbstractNamedData>()
      val runtimeDeps = hashSetOf<AbstractNamedData>()
      val rawRule: RawRule = ruleMapping[path]?.get(name) ?: run {
        LOG.error("Unable to find the rule under '$path' with name '$name'")
        RawRule(RuleKind.DUMMY, emptyList(), emptyList(), emptyList())
      }

      // if I'm the module then I add myself everywhere
      val moduleData = modules[path]?.data
      if (moduleData != null) {
        deps.add(moduleData)
      }
      // add junit to all unit tests
      if (rawRule.kind == RuleKind.JUNIT_TESTS) {
        val junit = findRule("//third_party/jvm/junit", "junit")
        deps.addAll(junit.exports)
      }

      fun fillDeps(
        deps: MutableSet<AbstractNamedData>,
        rawList: List<String>,
        extractor: (Rule) -> Set<AbstractNamedData>
      ) {
        for (dep in rawList) {
          when {
            dep.startsWith("@") -> {
              val library = libManager.getActualLib(dep)
              if (library != null) {
                deps.add(library)
              } else {
                LOG.warn("Can't find dependency '$dep' in the list of libraries")
              }
            }
            dep.startsWith(":") -> {
              val depRule = findRule(path, dep.substring(1))
              deps.addAll(extractor(depRule))
            }
            dep.startsWith("//external:") -> {
              val library = libManager.getLibMapping(dep.substring("//external:".length))
              if (library != null) {
                deps.add(library)
              } else {
                LOG.warn("Can't find external dependency '$dep' in the list of libraries")
              }
            }
            else -> {
              val thirdPartyRule = if (dep.contains(":")) {
                val libPath = dep.substring(0, dep.lastIndexOf(':'))
                val libName = dep.substringAfter(':')
                findRule(libPath, libName)
              } else {
                val libPath = dep
                val libName = dep.substringAfterLast('/')
                findRule(libPath, libName)
              }
              deps.addAll(extractor(thirdPartyRule))
            }
          }
        }
      }
      fillDeps(deps, rawRule.deps + rawRule.exports, { it.deps + it.exports })
      fillDeps(runtimeDeps, rawRule.runtimeDeps, { it.runtimeDeps + it.exports })
      fillDeps(exports, rawRule.exports, { it.exports })

      val rule = Rule(rawRule.kind, exports, deps, runtimeDeps)
      LOG.info("Processed rule $path:$name")
      ruleCache
        .computeIfAbsent(path, { hashMapOf() })
        .put(name, rule)
      return rule
    }

    val result: MutableMap<String, MutableMap<String, Rule>> = hashMapOf()
    for ((path, libDir: Map<String, *>) in ruleMapping) {
      for ((name, _) in libDir) {
        val rule = findRule(path, name)
        result
          .computeIfAbsent(path, { hashMapOf() })
          .put(name, rule)
      }
    }
    rules = result
  }

  fun getRules(packagePath: String): Map<String, Rule>? = rules[packagePath]
}

class BazilTaskManager : ExternalSystemTaskManager<BazilExecutionSettings> {
  override fun cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener) = true
}

class BazilManager : StartupActivity,
  ExternalSystemManager<BazilProjectSettings, BazilSettingsListener, BazilSettings, BazilLocalSettings, BazilExecutionSettings> {
  override fun runActivity(project: Project) {}

  override fun enhanceRemoteProcessing(parameters: SimpleJavaParameters) {}
  override fun getProjectResolverClass() = BazilProjectResolver::class.java
  override fun getSettingsProvider() = Function { project: Project ->
    BazilSettings.getInstance(project)
  }

  override fun getExecutionSettingsProvider() =
    Function<com.intellij.openapi.util.Pair<Project, String>, BazilExecutionSettings> {
      val project = it.first
      BazilExecutionSettings(project)
    }

  override fun getExternalProjectDescriptor(): FileChooserDescriptor =
    FileChooserDescriptorFactory.createSingleFileDescriptor()

  override fun getSystemId() = SYSTEM_ID
  override fun getTaskManagerClass() = BazilTaskManager::class.java
  override fun getLocalSettingsProvider() = Function<Project, BazilLocalSettings> { project ->
    ServiceManager.getService<BazilLocalSettings>(project, BazilLocalSettings::class.java)
  }

}
