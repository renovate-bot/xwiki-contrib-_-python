/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.python.repository.pypi.internal.searching;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * The local index of the PyPI package names: a UTF-8 text file with one package name per line, stored in a
 * dedicated directory. An update writes a new file, which replaces the current one only once it's complete, so that
 * searches always see a complete index.
 *
 * @version $Id$
 */
public class PypiPackageIndex
{
    private static final String FILE_EXTENSION = ".txt";

    /**
     * Provide the package names to store in a new index.
     */
    @FunctionalInterface
    public interface PackageNamesProvider
    {
        /**
         * @param consumer called with each package name
         * @throws IOException when failing to get the package names
         */
        void provide(Consumer<String> consumer) throws IOException;
    }

    private final File directory;

    private final Logger logger;

    private final AtomicReference<File> file = new AtomicReference<>();

    /**
     * @param directory the directory where to store the index
     * @param logger the logger
     */
    public PypiPackageIndex(File directory, Logger logger)
    {
        this.directory = directory;
        this.logger = logger;
    }

    /**
     * Load the most recent index found in the index directory and delete everything else it contains (previous
     * indexes, or indexes in an unsupported format like the Lucene indexes of older versions of the extension).
     */
    public void initialize()
    {
        File[] children = this.directory.listFiles();
        if (children == null) {
            return;
        }

        File current = null;
        for (File child : children) {
            if (child.isFile() && child.getName().endsWith(FILE_EXTENSION)
                && (current == null || child.lastModified() > current.lastModified())) {
                current = child;
            }
        }
        this.file.set(current);

        for (File child : children) {
            if (!child.equals(current)) {
                delete(child);
            }
        }
    }

    /**
     * @return the file of the current index, or {@code null} if there is none
     */
    public File getFile()
    {
        return this.file.get();
    }

    /**
     * @return the searcher of the current index, or {@code null} if there is none
     */
    public PypiPackageSearcher getSearcher()
    {
        File currentFile = this.file.get();

        return currentFile != null ? new PypiPackageSearcher(currentFile) : null;
    }

    /**
     * Replace the current index with the one contained in the provided zip (as exported by
     * {@link PypiPackageIndexManager#exportIndex(File)}). Each line of the index contains a package name, possibly
     * followed by a tab and the version of the package in indexes exported by older versions.
     *
     * @param zipStream the zip containing the index
     * @return true if the index was replaced
     */
    public boolean importZip(InputStream zipStream)
    {
        return update(consumer -> {
            ZipInputStream zip = new ZipInputStream(zipStream);
            if (zip.getNextEntry() == null) {
                throw new IOException("The index zip is empty");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String packageName = StringUtils.substringBefore(line, "\t");

                if (!packageName.isEmpty()) {
                    consumer.accept(packageName);
                }
            }
        });
    }

    /**
     * Replace the current index with a new one containing the provided package names. The current index is kept if
     * the new one can't be created.
     *
     * @param provider provide the package names of the new index
     * @return true if the index was replaced
     */
    public boolean update(PackageNamesProvider provider)
    {
        File newFile = new File(this.directory, UUID.randomUUID() + FILE_EXTENSION);

        try {
            Files.createDirectories(this.directory.toPath());
            try (Writer writer = Files.newBufferedWriter(newFile.toPath(), StandardCharsets.UTF_8)) {
                provider.provide(packageName -> write(writer, packageName));
            }
        } catch (IOException | UncheckedIOException e) {
            this.logger.error("Failed to create the PyPI package index", e);
            delete(newFile);

            return false;
        }

        File previousFile = this.file.getAndSet(newFile);
        if (previousFile != null) {
            delete(previousFile);
        }

        return true;
    }

    private static void write(Writer writer, String packageName)
    {
        try {
            writer.write(packageName);
            writer.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void delete(File toDelete)
    {
        if (toDelete.exists()) {
            try {
                FileUtils.forceDelete(toDelete);
            } catch (IOException e) {
                this.logger.warn("Failed to delete [{}]", toDelete, e);
            }
        }
    }
}
