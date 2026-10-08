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
import org.xwiki.component.phase.Initializable;
import org.xwiki.extension.repository.DefaultExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.ExtensionRepository;
import org.xwiki.extension.repository.ExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.ExtensionRepositoryManager;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

/**
 * This listener is only for execution configuration logic on installment.
 * It's a bit of workaround. Every listener is initialized by Observation Manager after registering it.
 *
 * @version $Id$
 */
@Component
@Named("PypiRepositoryExtensionPostInstallmentConfigListener")
@Singleton
public class ExtensionPostInstallmentConfigListener extends AbstractEventListener implements Initializable
{
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
        logger.info(getName() + " registered");
        addPypiRepository();
    }

    private void addPypiRepository()
    {
        ExtensionRepository extensionRepository = createPypiRepository();
        extensionRepositoryManager.addRepository(extensionRepository);
        this.logger.info("Pypi repository registered successfully");
    }

    private ExtensionRepository createPypiRepository()
    {
        return pypiRepository.setUpRepository(obtainPypiRepositoryDescriptor());
    }

    private ExtensionRepositoryDescriptor obtainPypiRepositoryDescriptor()
    {
        try {
            return new DefaultExtensionRepositoryDescriptor("PyPi", "pypi", new URI(PypiParameters.API_URL));
        } catch (URISyntaxException e) {
            // Should never happen
            return null;
        }
    }

    @Override
    public void onEvent(Event event, Object o, Object o1)
    {
    }
}
