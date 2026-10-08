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
package org.xwiki.contrib.python.repository.pypi.internal.searching;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;

/**
 * Update the list of the most downloaded PyPI packages.
 *
 * @version $Id$
 */
public class PypiPopularPackagesUpdateTask extends TimerTask
{
    private final PypiPopularPackages popularPackages;

    private final String url;

    private final PypiHttpClient httpClient;

    private final Logger logger;

    /**
     * @param popularPackages the list to update
     * @param url the URL of the up to date list
     * @param httpClient the client used to download the list
     * @param logger the logger used to report the errors
     */
    public PypiPopularPackagesUpdateTask(PypiPopularPackages popularPackages, String url, PypiHttpClient httpClient,
        Logger logger)
    {
        this.popularPackages = popularPackages;
        this.url = url;
        this.httpClient = httpClient;
        this.logger = logger;
    }

    @Override
    public void run()
    {
        try (InputStream stream = this.httpClient.openStream(new URI(this.url), null)) {
            if (stream == null) {
                throw new IOException("The list does not exist");
            }

            this.popularPackages.update(stream);
        } catch (IOException | URISyntaxException e) {
            // The current list is kept
            this.logger.warn("Failed to update the list of the most downloaded PyPI packages from [{}]: {}", this.url,
                e.getMessage());
        }
    }
}
