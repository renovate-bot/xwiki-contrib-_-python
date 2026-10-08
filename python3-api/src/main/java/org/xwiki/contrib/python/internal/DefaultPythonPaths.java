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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.namespace.Namespace;
import org.xwiki.contrib.python.PythonPaths;
import org.xwiki.model.EntityType;
import org.xwiki.model.ModelContext;
import org.xwiki.model.namespace.UserNamespace;
import org.xwiki.model.namespace.WikiNamespace;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

/**
 * Default implementation of {@link PythonPaths}.
 * 
 * @version $Id$
 */
@Component
@Singleton
public class DefaultPythonPaths implements PythonPaths
{
    @Inject
    private ModelContext modelContext;

    @Inject
    private DocumentAccessBridge documentAccessBridge;

    @Inject
    private EntityReferenceSerializer<String> referenceSerializer;

    /**
     * The paths indexed by namespace, in the order they were added.
     */
    private final Map<Namespace, Set<String>> paths = new ConcurrentHashMap<>();

    @Override
    public Collection<String> getPaths()
    {
        Set<String> result = new LinkedHashSet<>();

        DocumentReference userReference = this.documentAccessBridge.getCurrentUserReference();
        if (userReference != null) {
            addPaths(new UserNamespace(this.referenceSerializer.serialize(userReference)), result);
        }

        EntityReference currentReference = this.modelContext.getCurrentEntityReference();
        EntityReference wikiReference =
            currentReference != null ? currentReference.extractReference(EntityType.WIKI) : null;
        if (wikiReference != null) {
            addPaths(new WikiNamespace(wikiReference.getName()), result);
        }

        addPaths(Namespace.ROOT, result);

        return Collections.unmodifiableSet(result);
    }

    private void addPaths(Namespace namespace, Set<String> result)
    {
        Set<String> namespacePaths = this.paths.get(namespace);
        if (namespacePaths != null) {
            result.addAll(namespacePaths);
        }
    }

    @Override
    public void addPath(Namespace namespace, String path)
    {
        this.paths.computeIfAbsent(namespace, key -> new CopyOnWriteArraySet<>()).add(path);
    }

    @Override
    public void removePath(Namespace namespace, String path)
    {
        Set<String> namespacePaths = this.paths.get(namespace);
        if (namespacePaths != null) {
            namespacePaths.remove(path);
        }
    }
}
