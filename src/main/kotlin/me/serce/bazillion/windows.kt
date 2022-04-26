package me.serce.bazillion

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory
import com.intellij.openapi.project.Project

class BazilToolWindowFactory : AbstractExternalSystemToolWindowFactory(SYSTEM_ID) {
    override fun getSettings(project: Project) = BazilSettings.getInstance(project)
}
