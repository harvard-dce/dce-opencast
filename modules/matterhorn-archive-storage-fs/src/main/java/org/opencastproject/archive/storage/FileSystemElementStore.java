/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.archive.storage;

import static org.apache.commons.io.FilenameUtils.EXTENSION_SEPARATOR;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.lang.exception.ExceptionUtils.getMessage;
import static org.opencastproject.util.FileSupport.link;
import static org.opencastproject.util.IoSupport.file;
import static org.opencastproject.util.PathSupport.path;
import static org.opencastproject.util.data.Option.none;
import static org.opencastproject.util.data.Option.option;
import static org.opencastproject.util.data.Option.some;
import static org.opencastproject.util.data.functions.Strings.trimToNone;

import org.opencastproject.archive.api.Version;
import org.opencastproject.archive.base.StoragePath;
import org.opencastproject.archive.base.storage.DeletionSelector;
import org.opencastproject.archive.base.storage.ElementStore;
import org.opencastproject.archive.base.storage.ElementStoreException;
import org.opencastproject.archive.base.storage.Source;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.PathSupport;
import org.opencastproject.util.data.Function;
import org.opencastproject.util.data.Option;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

public class FileSystemElementStore implements ElementStore {

  /** Log facility */
  private static final Logger logger = LoggerFactory.getLogger(FileSystemElementStore.class);

  /** Configuration key for the storage directory */
  public static final String CONFIG_STORAGE_DIR = "org.opencastproject.storage.dir";

  /** Configuration key for the archive root directory */
  public static final String CONFIG_ARCHIVE_ROOT_DIR = "org.opencastproject.episode.rootdir";

  /** The default archive directory name */
  private static final String DEFAULT_ARCHIVE_DIRECTORY = "archive";

  /** The root directory for storing files */
  private String rootDirectory = null;

  private Workspace workspace;

  /** OSGi Di */
  void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * Service activator, called via declarative services configuration.
   *
   * @param cc
   *          the component context
   */
  public void activate(final ComponentContext cc) throws IllegalStateException, IOException {
    rootDirectory = StringUtils.trimToNull(cc.getBundleContext().getProperty(CONFIG_ARCHIVE_ROOT_DIR));

    if (rootDirectory == null) {
      final String storageDir = StringUtils.trimToNull(cc.getBundleContext().getProperty(CONFIG_STORAGE_DIR));
      if (storageDir == null)
        throw new IllegalArgumentException("Storage directory must be set");
      rootDirectory = PathSupport.concat(storageDir, DEFAULT_ARCHIVE_DIRECTORY);
    }

    mkDirs(file(rootDirectory));
  }

  @Override
  public void put(StoragePath storagePath, Source source) throws ElementStoreException {
    // Retrieving the file from the workspace has the advantage that in most cases the file already exists in the local
    // working file repository. In the very few cases where the file is not in the working file repository,
    // this strategy leads to a minor overhead because the file not only gets downloaded and stored in the file system
    // but also a hard link needs to be created (or if that's not possible, a copy of the file.
    final File origin = getFileFromWorkspace(source);
    final File destination = createFile(storagePath, source);
    try {
      mkParent(destination);
      link(origin, destination);
    } catch (IOException e) {
      logger.error("Error while linking/copying file {} to {}: {}", new Object[] { origin, destination, getMessage(e) });
      throw new ElementStoreException(e);
    }
  }

  private File getFileFromWorkspace(Source source) {
    try {
      return workspace.get(source.getUri());
    } catch (NotFoundException e) {
      logger.error("Source file '{}' does not exist", source.getUri());
      throw new ElementStoreException(e);
    } catch (IOException e) {
      logger.error("Error while getting file '{}' from workspace: {}", source.getUri(), getMessage(e));
      throw new ElementStoreException(e);
    }
  }

  @Override
  public boolean copy(final StoragePath from, final StoragePath to) throws ElementStoreException {
    return findStoragePathFile(from).map(new Function<File, Boolean>() {
      @Override
      public Boolean apply(File f) {
        final File t = createFile(to, f);
        mkParent(t);
        logger.debug("Copying {} to {}", f.getAbsolutePath(), t.getAbsolutePath());
        try {
          link(f, t, true);
        } catch (IOException e) {
          logger.error("Error copying archive file {} to {}", f, t);
          throw new ElementStoreException(e);
        }
        return true;
      }
    }).getOrElse(false);
  }

  @Override
  public Option<InputStream> get(final StoragePath path) throws ElementStoreException {
    return findStoragePathFile(path).map(new Function<File, InputStream>() {
      @Override
      public InputStream apply(File file) {
        try {
          return new FileInputStream(file);
        } catch (FileNotFoundException e) {
          logger.error("Error getting archiv file {}", file);
          throw new ElementStoreException(e);
        }
      }
    });
  }

  @Override
  public boolean contains(StoragePath path) throws ElementStoreException {
    return findStoragePathFile(path).isSome();
  }

  @Override
  public boolean delete(DeletionSelector sel) throws ElementStoreException {
    File dir = getDeletionSelectorDir(sel);
    try {
      FileUtils.deleteDirectory(dir);
      return true;
    } catch (IOException e) {
      logger.error("Error deleting directory from archive {}", dir);
      throw new ElementStoreException(e);
    }
  }

  /**
   * Returns the directory file from a deletion selector
   *
   * @param sel
   *          the deletion selector
   * @return the directory file
   */
  private File getDeletionSelectorDir(DeletionSelector sel) {
    final String basePath = path(rootDirectory, sel.getOrganizationId(), sel.getMediaPackageId());
    for (Version v : sel.getVersion())
      return file(basePath, v.toString());
    return file(basePath);
  }

  /** Create all parent directories of a file. */
  private void mkParent(File f) {
    mkDirs(f.getParentFile());
  }

  /** Create this directory and all of its parents. */
  private void mkDirs(File d) {
    if (d != null && !d.exists() && !d.mkdirs()) {
      final String msg = "Cannot create directory " + d;
      logger.error(msg);
      throw new ElementStoreException(msg);
    }
  }

  /** Return the extension of a file. */
  private Option<String> extension(File f) {
    return trimToNone(getExtension(f.getAbsolutePath()));
  }

  /** Return the extension of a URI, i.e. the extentension of its path. */
  private Option<String> extension(URI uri) {
    try {
      return trimToNone(getExtension(uri.toURL().getPath()));
    } catch (MalformedURLException e) {
      throw new Error(e);
    }
  }

  /** Create a file from a storage path and the extension of file <code>f</code>. */
  private File createFile(StoragePath p, File f) {
    return createFile(p, extension(f));
  }

  /** Create a file from a storage path and the extension of the URI of <code>s</code>. */
  private File createFile(StoragePath p, Source s) {
    return createFile(p, extension(s.getUri()));
  }

  /** Create a file from a storage path and an optional extension. */
  private File createFile(StoragePath p, Option<String> extension) {
    return file(rootDirectory, p.getOrganizationId(), p.getMediaPackageId(), p.getVersion().toString(),
            extension.isSome() ? p.getAssetId() + EXTENSION_SEPARATOR + extension.get() : p.getAssetId());
  }

  /**
   * Returns a file {@link Option} from a storage path if one is found or an empty {@link Option}
   *
   * @param storagePath
   *          the storage path
   * @return the file {@link Option}
   */
  private Option<File> findStoragePathFile(final StoragePath storagePath) {
    final FilenameFilter filter = new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return FilenameUtils.getBaseName(name).equals(storagePath.getAssetId());
      }
    };
    final File containerDir = createFile(storagePath, none("")).getParentFile();
    return option(containerDir.listFiles(filter)).bind(new Function<File[], Option<File>>() {
      @Override
      public Option<File> apply(File[] files) {
        switch (files.length) {
          case 0:
            return none();
          case 1:
            return some(files[0]);
          default:
            throw new ElementStoreException("Storage path " + files[0].getParent()
                    + "contains multiple files with the same element id!: " + storagePath.getAssetId());
        }
      }
    });
  }

  @Override
  public Option<Long> getUsedSpace() {
    return Option.some(FileUtils.sizeOfDirectory(new File(rootDirectory)));
  }

  @Override
  public Option<Long> getUsableSpace() {
    return Option.some(new File(rootDirectory).getUsableSpace());
  }

  @Override
  public Option<Long> getTotalSpace() {
    return Option.some(new File(rootDirectory).getTotalSpace());
  }

}
