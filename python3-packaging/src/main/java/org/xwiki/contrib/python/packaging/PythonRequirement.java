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

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.extension.DefaultExtensionDependency;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.version.internal.DefaultVersionConstraint;

/**
 * A PEP 508 dependency on a Python package.
 *
 * @version $Id$
 */
public class PythonRequirement
{
    private final String name;

    private final String specifiers;

    private final String versionRange;

    /**
     * @param name the name of the required package
     * @param specifiers the PEP 440 version specifiers (like {@code <4,>=2}), empty for any version
     * @param versionRange the extension version range closest to the specifiers (like {@code [2,4)})
     */
    public PythonRequirement(String name, String specifiers, String versionRange)
    {
        this.name = name;
        this.specifiers = specifiers;
        this.versionRange = versionRange;
    }

    /**
     * @return the name of the required package
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @return the PEP 440 version specifiers (like {@code <4,>=2}), empty for any version
     */
    public String getSpecifiers()
    {
        return this.specifiers;
    }

    /**
     * @return the extension version range closest to the specifiers (exclusions like {@code !=2.5} cannot be expressed
     *         with a version range)
     */
    public String getVersionRange()
    {
        return this.versionRange;
    }

    /**
     * @return the extension dependency corresponding to the requirement, holding the exact specifiers in the
     *         {@link PythonPackages#DEPENDENCY_SPECIFIERS} property
     */
    public ExtensionDependency toExtensionDependency()
    {
        DefaultExtensionDependency dependency = new DefaultExtensionDependency(
            PythonPackages.normalizeName(this.name), new DefaultVersionConstraint(this.versionRange));
        if (StringUtils.isNotEmpty(this.specifiers)) {
            dependency.putProperty(PythonPackages.DEPENDENCY_SPECIFIERS, this.specifiers);
        }

        return dependency;
    }

    @Override
    public String toString()
    {
        return this.name + this.specifiers;
    }
}
