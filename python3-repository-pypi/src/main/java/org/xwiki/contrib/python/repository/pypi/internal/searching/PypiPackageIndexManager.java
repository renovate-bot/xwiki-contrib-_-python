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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.python.repository.pypi.internal.PypiConfiguration;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;
import org.xwiki.environment.Environment;
import org.xwiki.extension.repository.result.CollectionIterableResult;
import org.xwiki.extension.repository.result.IterableResult;

/**
 * Maintain the local index of the names of the packages published on PyPI (PyPI does not provide any search API): it
 * starts from an embedded copy and is regularly updated from the Simple API index.
 * <p>
 * Searching without any query (which is what the extension index does to list all the extensions of a repository)
 * only returns the most downloaded packages, since PyPI provides hundreds of thousands of packages.
 *
 * @version $Id$
 */
@Component(roles = PypiPackageIndexManager.class)
@Singleton
public class PypiPackageIndexManager implements Initializable, Disposable
{
    private static final String EMBEDDED_INDEX = "/pypiIndex/pypi-index-20260928.zip";

    private static final String INDEX_ENTRY = "index.txt";

    private static final String ZIP_EXTENSION = ".zip";

    /**
     * The delay between two updates of the index.
     */
    private static final long UPDATE_PERIOD = 1000L * 60L * 60L * 12L;

    @Inject
    private Environment environment;

    @Inject
    private PypiHttpClient httpClient;

    @Inject
    private PypiConfiguration configuration;

    @Inject
    private Logger logger;

    private Timer timer;

    private PypiPackageIndex packageIndex;

    private PypiPopularPackages popularPackages;

    @Override
    public void initialize() throws InitializationException
    {
        initializePackageIndex();

        this.popularPackages = new PypiPopularPackages(
            new File(this.environment.getPermanentDirectory(), "cache/pypi-popular/top-pypi-packages.txt"),
            this.logger);
        this.popularPackages.initialize();

        this.timer = new Timer("PyPI index update", true);
        // Run the first updates right away since there is a good chance the indexes are not up to date
        this.timer.schedule(new PypiPackageListIndexUpdateTask(this.packageIndex, this.httpClient, this.logger), 0,
            UPDATE_PERIOD);
        this.timer.schedule(new PypiPopularPackagesUpdateTask(this.popularPackages,
            this.configuration.getPopularPackagesURL(), this.httpClient, this.logger), 0, UPDATE_PERIOD);
    }

    private void initializePackageIndex() throws InitializationException
    {
        this.packageIndex =
            new PypiPackageIndex(new File(this.environment.getPermanentDirectory(), "cache/pypi-index"), this.logger);
        this.packageIndex.initialize();

        // If no index can be found use the default embedded one
        if (this.packageIndex.getFile() == null) {
            try (InputStream stream = getClass().getResourceAsStream(EMBEDDED_INDEX)) {
                importIndex(stream);
            } catch (IOException e) {
                throw new InitializationException("Could not read the embedded PyPI package index", e);
            }

            if (this.packageIndex.getFile() == null) {
                throw new InitializationException("Could not copy the embedded PyPI package index");
            }
        }
    }

    @Override
    public void dispose()
    {
        this.timer.cancel();
        this.timer.purge();
    }

    /**
     * @param query the text to search in the package names
     * @param offset the index of the first result to return
     * @param hitsPerPage the maximum number of results to return
     * @return the names of the matching packages
     * @throws IOException when failing to search the index
     */
    public IterableResult<String> search(String query, int offset, int hitsPerPage) throws IOException
    {
        int popularPackagesCount = this.configuration.getPopularPackagesCount();
        if (StringUtils.isBlank(query) && popularPackagesCount > 0) {
            return getPopularPackages(popularPackagesCount, offset, hitsPerPage);
        }

        PypiPackageSearcher searcher = this.packageIndex.getSearcher();
        if (searcher != null) {
            // Put the most downloaded packages first, like the PyPI search
            return searcher.search(query, offset, hitsPerPage, this.popularPackages.getRanks());
        }

        return new CollectionIterableResult<>(0, 0, Collections.emptyList());
    }

    private IterableResult<String> getPopularPackages(int count, int offset, int hitsPerPage)
    {
        List<String> names = this.popularPackages.getNames();
        names = names.subList(0, Math.min(count, names.size()));

        int from = Math.min(Math.max(offset, 0), names.size());
        int to = hitsPerPage < 0 ? names.size() : (int) Math.min((long) from + hitsPerPage, names.size());

        return new CollectionIterableResult<>(names.size(), offset, names.subList(from, to));
    }

    private String getIndexFileName()
    {
        return "pypi-index-" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ZIP_EXTENSION;
    }
    /**
     * Export the current index.
     *
     * @param output the zip file to create, or the directory where to create it, {@code null} to create it in the
     *            permanent directory
     * @throws IOException when failing to export the index
     */
    public void exportIndex(File output) throws IOException
    {
        File outputFile = output;
        if (outputFile == null) {
            outputFile = new File(this.environment.getPermanentDirectory(), getIndexFileName());
        } else if (outputFile.exists()) {
            if (outputFile.isDirectory()) {
                outputFile = new File(outputFile, getIndexFileName());
            }
        } else if (outputFile.getName().endsWith(ZIP_EXTENSION)) {
            outputFile.getParentFile().mkdirs();
        } else {
            outputFile.mkdirs();

            outputFile = new File(outputFile, getIndexFileName());
        }

        File indexFile = this.packageIndex.getFile();
        if (indexFile != null) {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile))) {
                zip.putNextEntry(new ZipEntry(INDEX_ENTRY));
                Files.copy(indexFile.toPath(), zip);
                zip.closeEntry();
            }
        }
    }
    /**
     * Replace the current index with the one contained in the provided zip. Each line of the index contains a package
     * name, possibly followed by a tab and the version of the package in indexes exported by older versions.
     *
     * @param inputFile the zip containing the index
     */
    public void importIndex(InputStream inputFile)
    {
        this.packageIndex.importZip(inputFile);
    }
}
