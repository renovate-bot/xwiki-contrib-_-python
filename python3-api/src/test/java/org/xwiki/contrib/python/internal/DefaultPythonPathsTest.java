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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.namespace.Namespace;
import org.xwiki.model.ModelContext;
import org.xwiki.model.namespace.UserNamespace;
import org.xwiki.model.namespace.WikiNamespace;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultPythonPaths}.
 * 
 * @version $Id$
 */
@ComponentTest
class DefaultPythonPathsTest
{
    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "User");

    @InjectMockComponents
    private DefaultPythonPaths paths;

    @MockComponent
    private ModelContext modelContext;

    @MockComponent
    private DocumentAccessBridge documentAccessBridge;

    @MockComponent
    private EntityReferenceSerializer<String> referenceSerializer;

    @BeforeEach
    void beforeEach()
    {
        when(this.referenceSerializer.serialize(USER)).thenReturn("xwiki:XWiki.User");
    }

    private List<String> getPaths()
    {
        return List.copyOf(this.paths.getPaths());
    }

    @Test
    void getPathsWithoutContext()
    {
        assertTrue(this.paths.getPaths().isEmpty());

        this.paths.addPath(Namespace.ROOT, "/root1");
        this.paths.addPath(Namespace.ROOT, "/root2");
        this.paths.addPath(Namespace.ROOT, "/root1");
        this.paths.addPath(new WikiNamespace("wiki1"), "/wiki");
        this.paths.addPath(new UserNamespace("xwiki:XWiki.User"), "/user");

        assertEquals(Arrays.asList("/root1", "/root2"), getPaths());

        this.paths.removePath(Namespace.ROOT, "/root1");
        this.paths.removePath(new WikiNamespace("unknown"), "/unknown");

        assertEquals(Arrays.asList("/root2"), getPaths());
    }

    @Test
    void getPathsInContext()
    {
        this.paths.addPath(Namespace.ROOT, "/root");
        this.paths.addPath(new WikiNamespace("wiki1"), "/wiki1");
        this.paths.addPath(new WikiNamespace("wiki2"), "/wiki2");
        this.paths.addPath(new UserNamespace("xwiki:XWiki.User"), "/user");
        this.paths.addPath(new UserNamespace("xwiki:XWiki.Other"), "/otheruser");

        when(this.modelContext.getCurrentEntityReference())
            .thenReturn(new DocumentReference("wiki1", "Space", "Page"));
        assertEquals(Arrays.asList("/wiki1", "/root"), getPaths());

        // The paths of the user come first, then the ones of the wiki, then the root ones
        when(this.documentAccessBridge.getCurrentUserReference()).thenReturn(USER);
        assertEquals(Arrays.asList("/user", "/wiki1", "/root"), getPaths());

        when(this.modelContext.getCurrentEntityReference()).thenReturn(new WikiReference("wiki2"));
        assertEquals(Arrays.asList("/user", "/wiki2", "/root"), getPaths());

        // A path removed from one namespace stays in the others
        this.paths.addPath(new WikiNamespace("wiki2"), "/shared");
        this.paths.addPath(Namespace.ROOT, "/shared");
        this.paths.removePath(new WikiNamespace("wiki2"), "/shared");
        assertEquals(Arrays.asList("/user", "/wiki2", "/root", "/shared"), getPaths());
    }

    @Test
    void getPathsIsReadOnly()
    {
        assertThrows(UnsupportedOperationException.class, () -> this.paths.getPaths().add("/path"));
    }
}
