// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.AbstractCollectionChildDescription;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.dom.index.PluginIdModuleIndex;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExtensionsDomExtender extends DomExtender<Extensions> {

  private static final DomExtender<Extension> EXTENSION_EXTENDER = new ExtensionDomExtender();

  @Override
  public void registerExtensions(@NotNull final Extensions extensions, @NotNull final DomExtensionsRegistrar registrar) {
    IdeaPlugin ideaPlugin = extensions.getParentOfType(IdeaPlugin.class, true);
    if (ideaPlugin == null) return;

    String epPrefix = extensions.getEpPrefix();
    for (IdeaPlugin plugin : getVisiblePlugins(ideaPlugin)) {
      AbstractCollectionChildDescription collectionChildDescription =
        (AbstractCollectionChildDescription)plugin.getGenericInfo().getCollectionChildDescription("extensionPoints");
      DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(plugin);
      assert handler != null;
      List<? extends DomElement> children = handler.getCollectionChildren(collectionChildDescription, false);
      if (!children.isEmpty()) {
        for (DomElement points : children) {
          for (ExtensionPoint point : ((ExtensionPoints)points).getExtensionPoints()) {
            registerExtensionPoint(registrar, point, epPrefix);
          }
        }
      }
    }
  }

  @Override
  public boolean supportsStubs() {
    return false;
  }

  private static Set<IdeaPlugin> getVisiblePlugins(IdeaPlugin ideaPlugin) {
    Set<IdeaPlugin> result = new HashSet<>();
    collectDependencies(ideaPlugin, result);
    result.addAll(PluginIdModuleIndex.findPlugins(ideaPlugin, ""));
    return result;
  }

  private static void collectDependencies(IdeaPlugin ideaPlugin, Set<IdeaPlugin> result) {
    ProgressManager.checkCanceled();
    if (!result.add(ideaPlugin)) {
      return;
    }

    for (String id : getDependencies(ideaPlugin)) {
      for (IdeaPlugin dep : PluginIdModuleIndex.findPlugins(ideaPlugin, id)) {
        collectDependencies(dep, result);
      }
    }
  }

  private static void registerExtensionPoint(final DomExtensionsRegistrar registrar,
                                             final ExtensionPoint extensionPoint,
                                             String epPrefix) {
    String epName = extensionPoint.getEffectiveQualifiedName();
    if (!StringUtil.startsWith(epName, epPrefix)) return;

    registrar.registerCollectionChildrenExtension(new XmlName(epName.substring(epPrefix.length())), Extension.class)
      .setDeclaringElement(extensionPoint)
      .addExtender(EXTENSION_EXTENDER);
  }

  static Collection<String> getDependencies(IdeaPlugin ideaPlugin) {
    Set<String> result = new HashSet<>();

    result.add(PluginManagerCore.CORE_PLUGIN_ID);

    for (Dependency dependency : ideaPlugin.getDependencies()) {
      ContainerUtil.addIfNotNull(result, dependency.getStringValue());
    }

    if (ideaPlugin.getPluginId() == null) {
      final VirtualFile file = DomUtil.getFile(ideaPlugin).getOriginalFile().getVirtualFile();
      if (file != null) {
        final String fileName = file.getName();
        if (!PluginManagerCore.PLUGIN_XML.equals(fileName)) {
          final VirtualFile mainPluginXml = file.findFileByRelativePath("../" + PluginManagerCore.PLUGIN_XML);
          if (mainPluginXml != null) {
            final PsiFile psiFile = PsiManager.getInstance(ideaPlugin.getManager().getProject()).findFile(mainPluginXml);
            if (psiFile instanceof XmlFile) {
              final XmlFile xmlFile = (XmlFile)psiFile;
              final IdeaPlugin mainPlugin = DescriptorUtil.getIdeaPlugin(xmlFile);
              if (mainPlugin != null) {
                ContainerUtil.addIfNotNull(result, mainPlugin.getPluginId());
                for (Dependency dependency : mainPlugin.getDependencies()) {
                  ContainerUtil.addIfNotNull(result, dependency.getStringValue());
                }
              }
            }
          }
        }
      }
    }

    return result;
  }
}
