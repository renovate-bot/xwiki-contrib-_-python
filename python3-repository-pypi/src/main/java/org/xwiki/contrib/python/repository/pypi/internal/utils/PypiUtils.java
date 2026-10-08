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
package org.xwiki.contrib.python.repository.pypi.internal.utils;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.version.Version;

/**
 * @version $Id$
 */
public final class PypiUtils
{
    private PypiUtils()
    {
    }

    /**
     * @param extensionId the identifier of the extension
     * @return the name of the package
     * @throws ResolveException when the identifier is not the one of a Python package
     */
    public static String getPackageName(ExtensionId extensionId) throws ResolveException
    {
        return getPackageName(extensionId.getId());
    }

    /**
     * The identifier of a Python package extension is the name of the package.
     *
     * @param extensionId the identifier of the extension
     * @return the name of the package
     * @throws ResolveException when the identifier is not the one of a Python package
     */
    public static String getPackageName(String extensionId) throws ResolveException
    {
        // Avoid asking PyPI about extensions coming from other repositories (like Maven ones), a Python package name
        // can only contain letters, digits, ".", "_" and "-"
        if (!PythonPackages.isValidName(extensionId)) {
            throw new ExtensionNotFoundException("[" + extensionId + "] is not the identifier of a Python package");
        }

        return extensionId;
    }

    /**
     * @param extensionId -
     * @return extracted version wrapped with Optional. If version is null or empty Optional is empty as well
     */
    public static Optional<String> getVersion(ExtensionId extensionId)
    {
        String version = extensionId.getVersion() != null ? extensionId.getVersion().getValue() : null;
        if (StringUtils.isEmpty(version)) {
            return Optional.empty();
        } else {
            return Optional.of(version);
        }
    }

    /**
     * @param value the version
     * @return the extension version
     */
    public static Version toVersion(String value)
    {
        // The extension identifier is the public way to get a version
        return new ExtensionId("", value).getVersion();
    }

}
