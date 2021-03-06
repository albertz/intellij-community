// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.TOOLWINDOW_ID;

public abstract class VcsToolwindowDnDTarget extends DnDActivateOnHoldTarget {
  @NotNull private final Project myProject;
  @NotNull private final Content myContent;

  protected VcsToolwindowDnDTarget(@NotNull Project project, @NotNull Content content) {
    myProject = project;
    myContent = content;
  }

  @Override
  protected void activateContent() {
    ChangesViewContentManager.getInstance(myProject).setSelectedContent(myContent);
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID);
    if (toolWindow != null && !toolWindow.isVisible()) {
      toolWindow.activate(null);
    }
  }
}