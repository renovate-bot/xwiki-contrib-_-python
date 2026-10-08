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
package org.xwiki.contrib.python.repository.pypi.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.packaging.PythonMetadata;
import org.xwiki.contrib.python.packaging.PythonPackaging;
import org.xwiki.contrib.python.packaging.PythonPackagingException;
import org.xwiki.contrib.python.repository.pypi.internal.dto.simple.PypiSimpleFileDto;
import org.xwiki.contrib.python.repository.pypi.internal.dto.simple.PypiSimpleProjectDto;
import org.xwiki.contrib.python.repository.pypi.internal.searching.PypiPackageIndexManager;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiUtils;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.AbstractExtensionRepository;
import org.xwiki.extension.repository.ExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.result.CollectionIterableResult;
import org.xwiki.extension.repository.result.IterableResult;
import org.xwiki.extension.repository.search.SearchException;
import org.xwiki.extension.repository.search.Searchable;
import org.xwiki.extension.version.Version;

/**
 * Provide the Python packages published on PyPI, using the standard Simple API (PEP 503, PEP 691, PEP 700) to find
 * the distribution files of a package, and the core metadata of the wheels (PEP 658, PEP 714) to know their
 * dependencies.
 *
 * @version $Id$
 */
@Component(roles = PypiExtensionRepository.class)
@Singleton
// Implementing the extension repository API involves many types, the actual work is delegated to dedicated components
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class PypiExtensionRepository extends AbstractExtensionRepository
    implements Searchable, Initializable, Disposable
{
    private static final String WHEEL_EXTENSION = ".whl";

    /**
     * The maximum number of wheels whose metadata is checked when looking for a version supporting the Python runtime
     * (the {@code Requires-Python} of the files listed by the Simple API is usually enough to select the right one).
     */
    private static final int MAX_RESOLVE_ATTEMPTS = 10;

    /**
     * The number of search results resolved in parallel.
     */
    private static final int SEARCH_THREADS = 4;

    @Inject
    private PythonPackaging pythonPackaging;

    @Inject
    private PypiSimpleApiClient simpleApiClient;

    @Inject
    private PypiExtensionFactory extensionFactory;

    @Inject
    private PypiVersionSelector versionSelector;

    @Inject
    private PypiPackageIndexManager indexManager;

    @Inject
    private Logger logger;

    private ExecutorService searchExecutor;

    /**
     * @param extensionRepositoryDescriptor the descriptor of the repository
     * @return this repository
     */
    public PypiExtensionRepository setUpRepository(ExtensionRepositoryDescriptor extensionRepositoryDescriptor)
    {
        setDescriptor(extensionRepositoryDescriptor);
        return this;
    }

    @Override
    public void initialize() throws InitializationException
    {
        this.searchExecutor = Executors.newFixedThreadPool(SEARCH_THREADS,
            new BasicThreadFactory.Builder().namingPattern("PyPI search %d").daemon(true).build());
    }

    @Override
    public void dispose() throws ComponentLifecycleException
    {
        this.searchExecutor.shutdownNow();
    }

    @Override
    public Extension resolve(ExtensionId extensionId) throws ResolveException
    {
        String packageName = PypiUtils.getPackageName(extensionId);
        PypiSimpleProjectDto project = this.simpleApiClient.getProject(packageName);

        Optional<String> version = PypiUtils.getVersion(extensionId);
        if (version.isPresent()) {
            // A yanked version should still be usable when explicitly requested (PEP 592)
            return resolve(project, List.of(version.get()), true, version.get());
        }

        return resolve(project, this.versionSelector.getBestVersions(project), false, "the best version");
    }

    @Override
    public Extension resolve(ExtensionDependency extensionDependency) throws ResolveException
    {
        String packageName = PypiUtils.getPackageName(extensionDependency.getId());
        PypiSimpleProjectDto project = this.simpleApiClient.getProject(packageName);

        List<String> candidates = this.versionSelector.getCandidateVersions(project, extensionDependency);
        if (candidates.isEmpty()) {
            throw new ExtensionNotFoundException(String.format("No version of package [%s] matches [%s]",
                packageName, getConstraintLabel(extensionDependency)));
        }

        return resolve(project, candidates, false, getConstraintLabel(extensionDependency));
    }

    /**
     * @param project the project page
     * @param candidates the versions to choose from, from the best to the worst
     * @param includeYanked true if the yanked files can be selected
     * @param label describe the requested versions (in error messages)
     * @return the best version having a wheel compatible with the Python runtime
     */
    private PypiExtension resolve(PypiSimpleProjectDto project, List<String> candidates, boolean includeYanked,
        String label) throws ResolveException
    {
        Map<String, PypiSimpleFileDto> files = getWheels(project, includeYanked);
        Map<String, String> wheels = new LinkedHashMap<>();
        files.forEach((filename, file) -> wheels.put(filename, file.getRequiresPython()));

        Map<String, String> selected;
        try {
            selected = this.pythonPackaging.selectWheels(candidates, wheels);
        } catch (PythonPackagingException e) {
            throw new ResolveException("Failed to select the wheels of package [" + project.getName() + "]", e);
        }

        ResolveException error = null;
        int attempts = 0;
        for (Map.Entry<String, String> entry : selected.entrySet()) {
            if (++attempts > MAX_RESOLVE_ATTEMPTS) {
                break;
            }

            PypiSimpleFileDto file = files.get(entry.getValue());
            PythonMetadata metadata = this.simpleApiClient.getMetadata(file);
            if (isPythonSupported(metadata)) {
                return this.extensionFactory.createExtension(this, project, entry.getKey(), file, metadata);
            }

            error = new ResolveException(String.format("Package [%s] in version [%s] requires Python [%s]",
                project.getName(), entry.getKey(), metadata.getRequiresPython()));
        }

        if (error != null) {
            throw error;
        }

        throw new ExtensionNotFoundException(String.format(
            "No compatible distribution of package [%s] matching [%s] (a wheel without native code is required)",
            project.getName(), label));
    }

    /**
     * @return the wheel files of the project, indexed by file name
     */
    private static Map<String, PypiSimpleFileDto> getWheels(PypiSimpleProjectDto project, boolean includeYanked)
    {
        Map<String, PypiSimpleFileDto> wheels = new LinkedHashMap<>();
        if (project.getFiles() != null) {
            for (PypiSimpleFileDto file : project.getFiles()) {
                if (StringUtils.endsWith(file.getFilename(), WHEEL_EXTENSION) && file.getUrl() != null
                    && (includeYanked || !file.isYanked())) {
                    wheels.put(file.getFilename(), file);
                }
            }
        }

        return wheels;
    }

    private boolean isPythonSupported(PythonMetadata metadata)
    {
        if (StringUtils.isNotBlank(metadata.getRequiresPython())) {
            try {
                return this.pythonPackaging.isPythonSupported(metadata.getRequiresPython());
            } catch (PythonPackagingException e) {
                // Don't block the installation because of an invalid metadata
            }
        }

        return true;
    }


    private static String getConstraintLabel(ExtensionDependency dependency)
    {
        Object specifiers = dependency.getProperty(PythonPackages.DEPENDENCY_SPECIFIERS);

        return specifiers != null ? specifiers.toString() : String.valueOf(dependency.getVersionConstraint());
    }

    @Override
    public IterableResult<Version> resolveVersions(String packageName, int offset, int nb) throws ResolveException
    {
        PypiSimpleProjectDto projectData = this.simpleApiClient.getProject(PypiUtils.getPackageName(packageName));
        List<Version> versions = PypiVersionSelector.getVersions(projectData).stream().map(PypiUtils::toVersion)
            .collect(Collectors.toList());

        if (versions.isEmpty()) {
            throw new ExtensionNotFoundException("No versions available for id [" + packageName + "]");
        }

        if (nb == 0 || offset >= versions.size()) {
            return new CollectionIterableResult<>(versions.size(), offset, Collections.<Version>emptyList());
        }

        int fromId = offset < 0 ? 0 : offset;
        int toId = offset + nb > versions.size() || nb < 0 ? versions.size() : offset + nb;

        return new CollectionIterableResult<>(versions.size(), offset, new ArrayList<>(versions.subList(fromId, toId)));
    }


    @Override
    public IterableResult<Extension> search(String searchQuery, int offset, int hitsPerPage) throws SearchException
    {
        try {
            return toExtensions(this.indexManager.search(searchQuery, offset, hitsPerPage));
        } catch (IOException e) {
            throw new SearchException("Failed to search the PyPI package index", e);
        }
    }

    private Extension resolveSearchResult(String packageName) throws ResolveException
    {
        try {
            return resolve(new ExtensionId(packageName));
        } catch (ExtensionNotFoundException e) {
            // Still list the packages which can't be installed (no compatible wheel): it allows the extension index to
            // know about them (and indicate they are not compatible), and to not stop listing the packages because of
            // an incomplete page of results
            PypiSimpleProjectDto project = this.simpleApiClient.getProject(packageName);
            List<String> versions = this.versionSelector.getBestVersions(project);
            if (versions.isEmpty()) {
                throw e;
            }

            return this.extensionFactory.createIncompatibleExtension(this, project, versions.get(0));
        }
    }

    private IterableResult<Extension> toExtensions(IterableResult<String> packageNames) throws SearchException
    {
        // Resolve the packages in parallel since each one requires a few requests
        Map<String, Future<Extension>> futures = new LinkedHashMap<>();
        packageNames.forEach(packageName -> futures.put(packageName,
            this.searchExecutor.submit(() -> resolveSearchResult(packageName))));

        List<Extension> extensions = new ArrayList<>(futures.size());
        for (Map.Entry<String, Future<Extension>> entry : futures.entrySet()) {
            try {
                extensions.add(entry.getValue().get());
            } catch (ExecutionException e) {
                // The packages which cannot be installed are still listed, so it's an unexpected error (PyPI not
                // available for example)
                this.logger.warn("Failed to resolve the package [{}] found in the PyPI index: {}", entry.getKey(),
                    ExceptionUtils.getRootCauseMessage(e));
                this.logger.debug("Full stack trace", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new SearchException("The search was interrupted", e);
            }
        }

        return new CollectionIterableResult<>(packageNames.getTotalHits(), packageNames.getOffset(), extensions);
    }


}
