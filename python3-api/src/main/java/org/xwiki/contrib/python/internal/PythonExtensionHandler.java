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

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.namespace.Namespace;
import org.xwiki.component.namespace.NamespaceUtils;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.PythonPaths;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionException;
import org.xwiki.extension.InstallException;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.LocalExtension;
import org.xwiki.extension.UninstallException;
import org.xwiki.extension.handler.ExtensionHandler;
import org.xwiki.extension.handler.ExtensionValidator;
import org.xwiki.job.Request;

/**
 * Handle egg and wheel packages extensions by adding them to (and removing them from) the Python paths of the
 * namespace where they are installed.
 * 
 * @version $Id$
 */
@Component(hints = {PythonPackages.TYPE_EGG, PythonPackages.TYPE_WHEEL})
@Singleton
public class PythonExtensionHandler implements ExtensionHandler
{
    @Inject
    private Provider<ExtensionValidator> defaultValidatorProvider;

    @Inject
    private PythonPaths paths;

    @Override
    public void install(LocalExtension localExtension, String namespace, Request request) throws InstallException
    {
        addPath(localExtension, namespace);
    }

    @Override
    public void uninstall(LocalExtension localExtension, String namespace, Request request) throws UninstallException
    {
        removePath(localExtension, namespace);
    }

    @Override
    public void uninstall(InstalledExtension localExtension, String namespace, Request request)
        throws UninstallException
    {
        removePath(localExtension, namespace);
    }

    @Override
    public void upgrade(LocalExtension previousLocalExtension, LocalExtension newLocalExtension, String namespace,
        Request request) throws InstallException
    {
        addPath(newLocalExtension, namespace);
        removePath(previousLocalExtension, namespace);
    }

    @Override
    public void upgrade(Collection<InstalledExtension> previousLocalExtensions, LocalExtension newLocalExtension,
        String namespace, Request request) throws InstallException
    {
        addPath(newLocalExtension, namespace);
        previousLocalExtensions.forEach(previous -> removePath(previous, namespace));
    }

    @Override
    public void initialize(LocalExtension localExtension, String namespace) throws ExtensionException
    {
        addPath(localExtension, namespace);
    }

    @Override
    public void checkInstall(Extension extension, String namespace, Request request) throws InstallException
    {
        this.defaultValidatorProvider.get().checkInstall(extension, namespace, request);
    }

    @Override
    public void checkUninstall(InstalledExtension extension, String namespace, Request request)
        throws UninstallException
    {
        this.defaultValidatorProvider.get().checkUninstall(extension, namespace, request);
    }

    private void addPath(LocalExtension extension, String namespace)
    {
        this.paths.addPath(toNamespace(namespace), extension.getFile().getAbsolutePath());
    }

    private void removePath(LocalExtension extension, String namespace)
    {
        this.paths.removePath(toNamespace(namespace), extension.getFile().getAbsolutePath());
    }

    private static Namespace toNamespace(String namespace)
    {
        // The extensions installed on the root namespace are associated with a null namespace
        return namespace != null ? NamespaceUtils.toNamespace(namespace) : Namespace.ROOT;
    }
}
