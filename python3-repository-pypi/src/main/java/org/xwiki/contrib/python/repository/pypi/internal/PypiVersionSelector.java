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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.packaging.PythonPackaging;
import org.xwiki.contrib.python.packaging.PythonPackagingException;
import org.xwiki.contrib.python.repository.pypi.internal.dto.simple.PypiSimpleProjectDto;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiUtils;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.version.Version;
import org.xwiki.extension.version.VersionConstraint;

/**
 * Select the versions of a package which can be used, from the best to the worst, following the Python packaging
 * standards (PEP 440 ordering and pre-releases handling).
 *
 * @version $Id$
 */
@Component(roles = PypiVersionSelector.class)
@Singleton
public class PypiVersionSelector
{
    @Inject
    private PythonPackaging pythonPackaging;

    @Inject
    private Logger logger;

    /**
     * @param project the project page of the package
     * @return the versions of the package, from the best to the worst (pre-releases are only included when there is
     *         no final release)
     * @throws ResolveException when failing to filter the versions
     */
    public List<String> getBestVersions(PypiSimpleProjectDto project) throws ResolveException
    {
        return filterVersions(project, null, null);
    }

    /**
     * @param project the project page of the package
     * @param dependency the dependency on the package
     * @return the versions of the package matching the dependency, starting with the best one
     * @throws ResolveException when failing to filter the versions
     */
    public List<String> getCandidateVersions(PypiSimpleProjectDto project, ExtensionDependency dependency)
        throws ResolveException
    {
        // Prefer the exact PEP 440 specifiers when available
        Object specifiers = dependency.getProperty(PythonPackages.DEPENDENCY_SPECIFIERS);
        List<String> candidates = null;
        if (specifiers != null) {
            try {
                candidates = new ArrayList<>(this.pythonPackaging.filterVersions(getVersions(project),
                    specifiers.toString(), null));
            } catch (PythonPackagingException e) {
                this.logger.debug("Invalid specifiers [{}], falling back on the version constraint", specifiers, e);
            }
        }

        if (candidates == null) {
            VersionConstraint constraint = dependency.getVersionConstraint();
            // Pre-releases are only selected when no final release matches the dependency (as specified by PEP 440)
            candidates = filterVersions(filterVersions(project, null, false), constraint);
            if (candidates.isEmpty()) {
                candidates = filterVersions(filterVersions(project, null, true), constraint);
            }
        }

        // Try the recommended version first
        VersionConstraint constraint = dependency.getVersionConstraint();
        if (constraint != null && constraint.getVersion() != null
            && candidates.remove(constraint.getVersion().getValue())) {
            candidates.add(0, constraint.getVersion().getValue());
        }

        return candidates;
    }

    private List<String> filterVersions(PypiSimpleProjectDto project, String specifiers, Boolean preReleases)
        throws ResolveException
    {
        try {
            return new ArrayList<>(this.pythonPackaging.filterVersions(getVersions(project), specifiers, preReleases));
        } catch (PythonPackagingException e) {
            throw new ResolveException("Failed to filter the versions of package [" + project.getName() + "]", e);
        }
    }

    /**
     * @param project the project page of the package
     * @return all the versions of the package
     */
    public static List<String> getVersions(PypiSimpleProjectDto project)
    {
        return project.getVersions() != null ? project.getVersions() : Collections.emptyList();
    }

    private static List<String> filterVersions(List<String> versions, VersionConstraint constraint)
    {
        if (constraint == null) {
            return new ArrayList<>(versions);
        }

        // A constraint without ranges only indicates the minimum version
        return versions.stream().filter(version -> {
            Version xwikiVersion = PypiUtils.toVersion(version);
            return constraint.getRanges().isEmpty() ? constraint.isCompatible(xwikiVersion)
                : constraint.containsVersion(xwikiVersion);
        }).collect(Collectors.toCollection(ArrayList::new));
    }
}
