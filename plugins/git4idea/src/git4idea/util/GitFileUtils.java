/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitBinaryHandler;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static git4idea.config.GitVersionSpecialty.CAT_FILE_SUPPORTS_FILTERS;
import static git4idea.config.GitVersionSpecialty.CAT_FILE_SUPPORTS_TEXTCONV;

public class GitFileUtils {

  private static final Logger LOG = Logger.getInstance(GitFileUtils.class);

  private GitFileUtils() {
  }

  /**
   * @deprecated Use {@link #deletePaths}
   */
  @Deprecated
  public static void delete(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends FilePath> files,
                            @NotNull String... additionalOptions) throws VcsException {
    deletePaths(project, root, files, additionalOptions);
  }

  public static void deletePaths(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends FilePath> files,
                                 @NotNull String... additionalOptions) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      doDelete(project, root, paths, additionalOptions);
    }
  }

  public static void deleteFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files,
                                 @NotNull String... additionalOptions) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkFiles(root, files)) {
      doDelete(project, root, paths, additionalOptions);
    }
  }

  public static void deleteFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull VirtualFile... files) throws VcsException {
    deleteFiles(project, root, Arrays.asList(files));
  }

  private static void doDelete(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<String> paths,
                               @NotNull String... additionalOptions) throws VcsException {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RM);
    handler.addParameters(additionalOptions);
    handler.endOptions();
    handler.addParameters(paths);
    Git.getInstance().runCommand(handler).throwOnError();
  }

  public static void deleteFilesFromCache(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files)
    throws VcsException {
    deleteFiles(project, root, files, "--cached");
    updateUntrackedFilesHolderOnFileRemove(project, root, files);
  }

  public static void addFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files)
    throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkFiles(root, files)) {
      addPaths(project, root, paths, false);
    }
    updateUntrackedFilesHolderOnFileAdd(project, root, files);
  }

  public static void addFilesForce(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends VirtualFile> files)
    throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkFiles(root, files)) {
      addPaths(project, root, paths, true);
    }
    updateUntrackedFilesHolderOnFileAdd(project, root, files);
  }

  private static void updateUntrackedFilesHolderOnFileAdd(@NotNull Project project, @NotNull VirtualFile root,
                                                          @NotNull Collection<? extends VirtualFile> addedFiles) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root.getPresentableUrl());
      return;
    }
    repository.getUntrackedFilesHolder().remove(ContainerUtil.map(addedFiles, VcsUtil::getFilePath));
  }

  private static void updateUntrackedFilesHolderOnFileRemove(@NotNull Project project, @NotNull VirtualFile root,
                                                             @NotNull Collection<? extends VirtualFile> removedFiles) {
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root.getPresentableUrl());
      return;
    }
    repository.getUntrackedFilesHolder().add(ContainerUtil.map(removedFiles, VcsUtil::getFilePath));
  }

  public static void addFiles(@NotNull Project project, @NotNull VirtualFile root, @NotNull VirtualFile... files) throws VcsException {
    addFiles(project, root, Arrays.asList(files));
  }

  public static void addPaths(@NotNull Project project, @NotNull VirtualFile root,
                              @NotNull Collection<? extends FilePath> files) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      addPaths(project, root, paths, false);
    }
    updateUntrackedFilesHolderOnFileAdd(project, root, getVirtualFilesFromFilePaths(files));
  }

  public static void addPathsForce(@NotNull Project project, @NotNull VirtualFile root,
                                   @NotNull Collection<? extends FilePath> files) throws VcsException {
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      addPaths(project, root, paths, true);
    }
    updateUntrackedFilesHolderOnFileAdd(project, root, getVirtualFilesFromFilePaths(files));
  }

  @NotNull
  private static Collection<VirtualFile> getVirtualFilesFromFilePaths(@NotNull Collection<? extends FilePath> paths) {
    Collection<VirtualFile> files = new ArrayList<>(paths.size());
    for (FilePath path : paths) {
      VirtualFile file = path.getVirtualFile();
      if (file != null) {
        files.add(file);
      }
    }
    return files;
  }

  private static void addPaths(@NotNull Project project, @NotNull VirtualFile root,
                               @NotNull List<String> paths, boolean force) throws VcsException {
    if (!force) {
      paths = excludeIgnoredFiles(project, root, paths);
      if (paths.isEmpty()) return;
    }

    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.ADD);
    handler.addParameters("--ignore-errors", "-A");
    if (force) handler.addParameters("-f");
    handler.endOptions();
    handler.addParameters(paths);
    Git.getInstance().runCommand(handler).throwOnError();
  }

  @NotNull
  private static List<String> excludeIgnoredFiles(@NotNull Project project, @NotNull VirtualFile root,
                                                  @NotNull List<String> paths) throws VcsException {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.LS_FILES);
    handler.setSilent(true);
    handler.addParameters("--ignored", "--others", "--exclude-standard");
    handler.endOptions();
    handler.addParameters(paths);
    String output = Git.getInstance().runCommand(handler).getOutputOrThrow();

    List<String> nonIgnoredFiles = new ArrayList<>(paths.size());
    Set<String> ignoredPaths = new HashSet<>(Arrays.asList(StringUtil.splitByLines(output)));
    for (String pathToCheck : paths) {
      if (!ignoredPaths.contains(pathToCheck)) {
        nonIgnoredFiles.add(pathToCheck);
      }
    }
    return nonIgnoredFiles;
  }

  /**
   * Get file content for the specific revision
   *
   * @param project      the project
   * @param root         the vcs root
   * @param revisionOrBranch     the revision to find path in or branch 
   * @return the content of file if file is found, null if the file is missing in the revision
   * @throws VcsException if there is a problem with running git
   */
  @NotNull
  public static byte[] getFileContent(@NotNull Project project,
                                      @NotNull VirtualFile root,
                                      @NotNull String revisionOrBranch,
                                      @NotNull String relativePath) throws VcsException {
    GitBinaryHandler h = new GitBinaryHandler(project, root, GitCommand.CAT_FILE);
    h.setSilent(true);
    if (CAT_FILE_SUPPORTS_TEXTCONV.existsIn(project) &&
        Registry.is("git.read.content.with.textconv")) {
      h.addParameters("--textconv");
    }
    else if (CAT_FILE_SUPPORTS_FILTERS.existsIn(project) &&
             Registry.is("git.read.content.with.filters")) {
      h.addParameters("--filters");
    }
    else {
      h.addParameters("-p");
    }
    h.addParameters(revisionOrBranch + ":" + relativePath);
    return h.run();
  }
}
