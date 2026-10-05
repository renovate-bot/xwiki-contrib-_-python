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
package org.xwiki.contrib.python.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.python.PythonPaths;

/**
 * Default implementation of {@link PythonPaths}.
 * 
 * @version $Id$
 */
@Component
@Singleton
public class DefaultPythonPaths implements PythonPaths
{
    private final Set<String> writePythonPaths = ConcurrentHashMap.newKeySet();

    private final Set<String> readPythonPaths = Collections.unmodifiableSet(this.writePythonPaths);

    @Override
    public Collection<String> getPaths()
    {
        return this.readPythonPaths;
    }

    @Override
    public void addPath(String path)
    {
        this.writePythonPaths.add(path);
    }

    @Override
    public void removePath(String path)
    {
        this.writePythonPaths.remove(path);
    }
}
