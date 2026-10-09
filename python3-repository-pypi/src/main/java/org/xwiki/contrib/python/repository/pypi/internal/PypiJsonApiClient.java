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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.python.PythonPackages;
import org.xwiki.contrib.python.repository.pypi.internal.dto.json.PypiJsonProjectDto;
import org.xwiki.contrib.python.repository.pypi.internal.utils.PypiHttpClient;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Get the description of the latest release of a project from the PyPI JSON API, which provides everything needed to
 * list a package found by a search in a single request.
 *
 * @version $Id$
 */
@Component(roles = PypiJsonApiClient.class)
@Singleton
public class PypiJsonApiClient
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    private PypiHttpClient httpClient;

    /**
     * @param packageName the name of the package
     * @return the JSON API page of the package
     * @throws ResolveException when failing to get the project page
     */
    public PypiJsonProjectDto getProject(String packageName) throws ResolveException
    {
        // Use the normalized name to avoid a redirect
        String name = PythonPackages.normalizeName(packageName);

        try (InputStream stream = this.httpClient.openStream(
            new URI(PypiParameters.PACKAGE_JSON_API.replace(PypiParameters.PACKAGE_NAME_VARIABLE, name)), null)) {
            if (stream == null) {
                throw new ExtensionNotFoundException("Cannot find package [" + packageName + "] on PyPI");
            }

            // The (deprecated) list of all the releases, which can be big, is skipped without being loaded in memory
            return OBJECT_MAPPER.readValue(stream, PypiJsonProjectDto.class);
        } catch (IOException | URISyntaxException e) {
            throw new ResolveException("Failed to get the JSON API page of package [" + packageName + "]", e);
        }
    }
}
