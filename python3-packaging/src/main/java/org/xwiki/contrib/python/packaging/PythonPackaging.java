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
package org.xwiki.contrib.python.packaging;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.xwiki.component.annotation.Role;

/**
 * Python packaging standards (PEP 440 versions and specifiers, PEP 508 requirements and environment markers, wheel
 * compatibility tags), evaluated for the Python implementation running the Python scripts (GraalPy).
 *
 * @version $Id$
 */
@Role
public interface PythonPackaging
{
    /**
     * @return the full version of the Python language implemented by the Python runtime (like {@code 3.13.14})
     * @throws PythonPackagingException when failing to get the version
     */
    String getPythonVersion() throws PythonPackagingException;

    /**
     * @param requiresPython the Python versions supported by a package (its {@code Requires-Python} metadata, like
     *            {@code >=3.8})
     * @return true if the Python runtime is supported by the package
     * @throws PythonPackagingException when the specifier is invalid
     */
    boolean isPythonSupported(String requiresPython) throws PythonPackagingException;

    /**
     * @param filename the name of a wheel file (like {@code requests-2.32.3-py3-none-any.whl})
     * @return true if the wheel can be used by the Python runtime (it must not contain any native code since the
     *         installed packages are loaded from zip files)
     * @throws PythonPackagingException when failing to check the wheel
     */
    boolean isCompatibleWheel(String filename) throws PythonPackagingException;

    /**
     * @param versions the candidate versions, from the best to the worst
     * @param wheels the names of the available wheel files, associated with their {@code Requires-Python} metadata
     *            ({@code null} when unknown)
     * @return for each version having at least one wheel compatible with the Python runtime, the best of these wheels
     *         (the one with the most specific compatibility tag), in the order of the versions
     * @throws PythonPackagingException when failing to select the wheels
     */
    Map<String, String> selectWheels(List<String> versions, Map<String, String> wheels)
        throws PythonPackagingException;

    /**
     * @param metadata the content of the core metadata file of a distribution (the {@code METADATA} file of a wheel)
     * @return the parsed metadata
     * @throws PythonPackagingException when failing to parse the metadata
     */
    PythonMetadata parseMetadata(String metadata) throws PythonPackagingException;

    /**
     * @param requiresDist the dependencies of a package (its {@code Requires-Dist} metadata, like
     *            {@code charset_normalizer<4,>=2})
     * @return the requirements which apply to the Python runtime and platform (optional dependencies, also known as
     *         extras, are never included)
     * @throws PythonPackagingException when a requirement is invalid
     */
    List<PythonRequirement> getRequirements(Collection<String> requiresDist) throws PythonPackagingException;

    /**
     * @param versions the versions to filter (the invalid PEP 440 versions are ignored)
     * @param specifiers the PEP 440 specifiers the versions must match (like {@code <4,>=2}), empty or {@code null} to
     *            match any version
     * @param preReleases true to include pre-releases, false to exclude them, {@code null} to apply the PEP 440 rule
     *            (pre-releases are only included when no final release matches or when the specifiers explicitly
     *            target a pre-release)
     * @return the matching versions, from the best to the worst
     * @throws PythonPackagingException when the specifiers are invalid
     */
    List<String> filterVersions(Collection<String> versions, String specifiers, Boolean preReleases)
        throws PythonPackagingException;
}
