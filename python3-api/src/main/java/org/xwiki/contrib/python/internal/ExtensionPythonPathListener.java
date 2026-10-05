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
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.python.PythonPaths;
import org.xwiki.extension.LocalExtension;
import org.xwiki.extension.event.ExtensionInstalledEvent;
import org.xwiki.extension.event.ExtensionUninstalledEvent;
import org.xwiki.extension.event.ExtensionUpgradedEvent;
import org.xwiki.observation.event.AbstractLocalEventListener;
import org.xwiki.observation.event.Event;

/**
 * Automatically add/remove extensions installed/uninstalled to the Python paths.
 * 
 * @version $Id$
 */
@Component
@Named(ExtensionPythonPathListener.NAME)
@Singleton
public class ExtensionPythonPathListener extends AbstractLocalEventListener
{
    /**
     * The name and role hint of the listener component.
     */
    public static final String NAME = "org.xwiki.contrib.python.internal.ExtensionPythonPathListener";

    @Inject
    private PythonPaths paths;

    /**
     * Setup event listener.
     */
    public ExtensionPythonPathListener()
    {
        super(NAME, new ExtensionInstalledEvent(), new ExtensionUninstalledEvent(), new ExtensionUpgradedEvent());
    }

    @Override
    public void processLocalEvent(Event event, Object source, Object data)
    {
        if (event instanceof ExtensionInstalledEvent) {
            uninstallExtension((LocalExtension) source);
        } else if (event instanceof ExtensionUninstalledEvent) {
            installExtension((LocalExtension) source);
        } else {
            installExtension((LocalExtension) source);
            ((Collection<LocalExtension>) data).forEach(this::uninstallExtension);
        }
    }

    private void uninstallExtension(LocalExtension extension)
    {
        this.paths.removePath(extension.getFile().getAbsolutePath());
    }

    private void installExtension(LocalExtension extension)
    {
        this.paths.addPath(extension.getFile().getAbsolutePath());
    }
}
