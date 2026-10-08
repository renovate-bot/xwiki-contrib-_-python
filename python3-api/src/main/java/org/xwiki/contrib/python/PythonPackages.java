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
package org.xwiki.contrib.python;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The conventions shared by everything manipulating Python packages as extensions.
 *
 * @version $Id$
 */
public final class PythonPackages
{
    /**
     * The type of the extensions packaged as an egg.
     */
    public static final String TYPE_EGG = "egg";

    /**
     * The type of the extensions packaged as a wheel.
     */
    public static final String TYPE_WHEEL = "wheel";

    /**
     * The name of the dependency property holding the PEP 440 version specifiers of a dependency on a Python package,
     * which are more accurate than the version constraint the extension framework works with.
     */
    public static final String DEPENDENCY_SPECIFIERS = "python.specifiers";

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$");

    private static final Pattern NAME_SEPARATORS = Pattern.compile("[-_.]+");

    private PythonPackages()
    {
    }

    /**
     * @param name the name to check
     * @return true if the name is a valid Python package name (as specified by PEP 508)
     */
    public static boolean isValidName(String name)
    {
        return NAME.matcher(name).matches();
    }

    /**
     * Normalize a package name as specified by PEP 503 so that all the variants of a name (like
     * {@code Typing_Extensions} and {@code typing-extensions}) lead to the same extension identifier. The identifier of
     * a Python package extension is the normalized name of the package.
     *
     * @param name the name of the package
     * @return the normalized name
     */
    public static String normalizeName(String name)
    {
        return NAME_SEPARATORS.matcher(name).replaceAll("-").toLowerCase(Locale.ROOT);
    }
}
