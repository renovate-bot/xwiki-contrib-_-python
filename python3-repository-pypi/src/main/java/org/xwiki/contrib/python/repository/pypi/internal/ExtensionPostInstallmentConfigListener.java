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
package org.xwiki.contrib.python.repository.pypi.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.extension.repository.DefaultExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.ExtensionRepository;
import org.xwiki.extension.repository.ExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.ExtensionRepositoryManager;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

/**
 * Register the PyPI repository when the extension is installed (or when XWiki starts), and unregister it when the
 * extension is uninstalled. It's a listener only because the listeners are initialized as soon as they are
 * registered.
 *
 * @version $Id$
 */
@Component
@Named("PypiRepositoryExtensionPostInstallmentConfigListener")
@Singleton
public class ExtensionPostInstallmentConfigListener extends AbstractEventListener implements Initializable, Disposable
{
    /**
     * The identifier of the PyPI repository.
     */
    public static final String REPOSITORY_ID = "pypi";

    @Inject
    private PypiExtensionRepository pypiRepository;

    @Inject
    private ExtensionRepositoryManager extensionRepositoryManager;

    @Inject
    private Logger logger;

    /**
     * Setup the listener.
     */
    public ExtensionPostInstallmentConfigListener()
    {
        super("PypiRepositoryExtensionPostInstallmentConfigListener", Collections.emptyList());
    }

    @Override
    public void initialize()
    {
        addPypiRepository();
    }

    private void addPypiRepository()
    {
        ExtensionRepository extensionRepository = createPypiRepository();
        extensionRepositoryManager.addRepository(extensionRepository);
        this.logger.info("PyPI repository registered successfully");
    }

    private ExtensionRepository createPypiRepository()
    {
        return pypiRepository.setUpRepository(obtainPypiRepositoryDescriptor());
    }

    private ExtensionRepositoryDescriptor obtainPypiRepositoryDescriptor()
    {
        try {
            return new DefaultExtensionRepositoryDescriptor(REPOSITORY_ID, REPOSITORY_ID,
                new URI(PypiParameters.API_URL));
        } catch (URISyntaxException e) {
            // Should never happen
            return null;
        }
    }

    @Override
    public void dispose()
    {
        this.extensionRepositoryManager.removeRepository(REPOSITORY_ID);
    }

    @Override
    public void onEvent(Event event, Object o, Object o1)
    {
    }
}
