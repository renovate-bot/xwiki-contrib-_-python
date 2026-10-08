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

import java.util.Collection;

import org.xwiki.component.annotation.Role;
import org.xwiki.component.namespace.Namespace;

/**
 * The locations (directories or zip files like wheels) where Python packages and modules are searched.
 * <p>
 * Each path is associated with a namespace, the same way extensions are installed in a namespace: a path registered
 * for a wiki or a user is only visible from this wiki or by this user, while a path registered for the root namespace
 * is visible from everywhere.
 *
 * @version $Id$
 */
@Role
public interface PythonPaths
{
    /**
     * @return the paths available in the current context: the ones registered for the current user, then the current
     *         wiki, then the root namespace (the first paths take precedence when looking for a Python module)
     */
    Collection<String> getPaths();

    /**
     * @param namespace the namespace where the path is available (like a wiki or a user), {@link Namespace#ROOT}
     *            for the root namespace (available everywhere)
     * @param path the local path to add
     */
    void addPath(Namespace namespace, String path);

    /**
     * @param namespace the namespace for which the path was registered, {@link Namespace#ROOT} for the root
     *            namespace
     * @param path the path to remove
     */
    void removePath(Namespace namespace, String path);
}
