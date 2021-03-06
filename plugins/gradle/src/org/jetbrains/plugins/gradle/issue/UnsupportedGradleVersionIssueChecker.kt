// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.ReimportQuickFix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.util.*

/**
 * Provides the check for errors caused by dropped support in Gradle tooling API of the old Gradle versions
 *
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
class UnsupportedGradleVersionIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = getRootCauseAndLocation(issueData.error).first
    val rootCauseText = rootCause.toString()
    var gradleVersionUsed: GradleVersion? = null
    if (issueData.buildEnvironment != null) {
      gradleVersionUsed = GradleVersion.version(issueData.buildEnvironment.gradle.gradleVersion)
    }

    val errorMessagePrefix = "org.gradle.tooling.UnsupportedVersionException: Support for builds using Gradle versions older than "
    if (!rootCauseText.startsWith(errorMessagePrefix)) {
      return null
    }

    val minRequiredVersionCandidate = rootCauseText.substringAfter(errorMessagePrefix).substringBefore(" ", "")
    val gradleMinimumVersionRequired = try {
      GradleVersion.version(minRequiredVersionCandidate)
    }
    catch (e: IllegalArgumentException) {
      GradleVersion.current()
    }

    val quickFixes: MutableList<BuildIssueQuickFix>
    quickFixes = ArrayList()

    val issueDescription = StringBuilder(rootCause.message)
    issueDescription.append("\n\nPossible solution:\n")
    val wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(issueData.projectPath)
    if (wrapperPropertiesFile == null || gradleVersionUsed != null && gradleVersionUsed.baseVersion < gradleMinimumVersionRequired) {
      val gradleVersionFix = GradleVersionQuickFix(issueData.projectPath, gradleMinimumVersionRequired, true)
      issueDescription.append(
        " - <a href=\"${gradleVersionFix.id}\">Upgrade Gradle wrapper to ${gradleMinimumVersionRequired.version} version " +
        "and re-import the project</a>\n")
      quickFixes.add(gradleVersionFix)
    }
    else {
      val wrapperSettingsOpenQuickFix = GradleWrapperSettingsOpenQuickFix(issueData.projectPath, "distributionUrl")
      val reimportQuickFix = ReimportQuickFix(issueData.projectPath)
      issueDescription.append(" - <a href=\"${wrapperSettingsOpenQuickFix.id}\">Open Gradle wrapper settings</a>, " +
                              "upgrade version to ${gradleMinimumVersionRequired.version} or newer and <a href=\"${reimportQuickFix.id}\">reimport the project</a>\n")
      quickFixes.add(wrapperSettingsOpenQuickFix)
      quickFixes.add(reimportQuickFix)
    }

    return object : BuildIssue {
      override val description: String = issueDescription.toString()
      override val quickFixes = quickFixes
    }
  }
}
