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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.component.namespace.Namespace;
import org.xwiki.contrib.python.PythonPaths;
import org.xwiki.extension.Extension;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.LocalExtension;
import org.xwiki.extension.LocalExtensionFile;
import org.xwiki.extension.handler.ExtensionValidator;
import org.xwiki.job.Request;
import org.xwiki.model.namespace.UserNamespace;
import org.xwiki.model.namespace.WikiNamespace;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PythonExtensionHandler}.
 * 
 * @version $Id$
 */
@ComponentTest
class PythonExtensionHandlerTest
{
    private static final String WIKI = "wiki:wiki1";

    private static final Namespace WIKI_NAMESPACE = new WikiNamespace("wiki1");

    @InjectMockComponents
    private PythonExtensionHandler handler;

    @MockComponent
    private PythonPaths paths;

    @MockComponent
    private ExtensionValidator validator;

    private <T extends LocalExtension> T mockExtension(Class<T> type, String path)
    {
        T extension = mock(type);
        LocalExtensionFile file = mock(LocalExtensionFile.class);
        when(file.getAbsolutePath()).thenReturn(path);
        when(extension.getFile()).thenReturn(file);

        return extension;
    }

    @Test
    void installAndInitialize() throws Exception
    {
        this.handler.install(mockExtension(LocalExtension.class, "/installed"), WIKI, null);
        this.handler.initialize(mockExtension(LocalExtension.class, "/initialized"), null);

        verify(this.paths).addPath(WIKI_NAMESPACE, "/installed");
        verify(this.paths).addPath(Namespace.ROOT, "/initialized");
    }

    @Test
    void installInUserNamespace() throws Exception
    {
        this.handler.install(mockExtension(LocalExtension.class, "/user"), "user:xwiki:XWiki.User", null);

        verify(this.paths).addPath(new UserNamespace("xwiki:XWiki.User"), "/user");
    }

    @Test
    void uninstall() throws Exception
    {
        this.handler.uninstall(mockExtension(LocalExtension.class, "/local"), null, null);
        this.handler.uninstall(mockExtension(InstalledExtension.class, "/installed"), WIKI, null);

        verify(this.paths).removePath(Namespace.ROOT, "/local");
        verify(this.paths).removePath(WIKI_NAMESPACE, "/installed");
    }

    @Test
    void upgradeLocalExtension() throws Exception
    {
        this.handler.upgrade(mockExtension(LocalExtension.class, "/previous"),
            mockExtension(LocalExtension.class, "/new"), WIKI, null);

        verify(this.paths).addPath(WIKI_NAMESPACE, "/new");
        verify(this.paths).removePath(WIKI_NAMESPACE, "/previous");
    }

    @Test
    void upgradeInstalledExtensions() throws Exception
    {
        this.handler.upgrade(
            List.of(mockExtension(InstalledExtension.class, "/previous1"),
                mockExtension(InstalledExtension.class, "/previous2")),
            mockExtension(LocalExtension.class, "/new"), WIKI, null);

        verify(this.paths).addPath(WIKI_NAMESPACE, "/new");
        verify(this.paths).removePath(WIKI_NAMESPACE, "/previous1");
        verify(this.paths).removePath(WIKI_NAMESPACE, "/previous2");
    }

    @Test
    void checkInstallAndUninstall() throws Exception
    {
        Extension extension = mock(Extension.class);
        InstalledExtension installedExtension = mock(InstalledExtension.class);
        Request request = mock(Request.class);

        this.handler.checkInstall(extension, WIKI, request);
        this.handler.checkUninstall(installedExtension, WIKI, request);

        verify(this.validator).checkInstall(extension, WIKI, request);
        verify(this.validator).checkUninstall(installedExtension, WIKI, request);
    }
}
