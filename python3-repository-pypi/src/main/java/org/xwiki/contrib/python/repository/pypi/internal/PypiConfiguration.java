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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;

/**
 * The configuration of the PyPI repository, in {@code xwiki.properties}.
 *
 * @version $Id$
 */
@Component(roles = PypiConfiguration.class)
@Singleton
public class PypiConfiguration
{
    /**
     * The default number of most downloaded packages listed to the extension index.
     */
    public static final int DEFAULT_POPULAR_PACKAGES_COUNT = 500;

    /**
     * The default location of the list of the most downloaded packages (updated monthly from the PyPI download
     * statistics, see https://hugovk.dev/top-pypi-packages/).
     */
    public static final String DEFAULT_POPULAR_PACKAGES_URL =
        "https://raw.githubusercontent.com/hugovk/top-pypi-packages/main/top-pypi-packages.min.json";

    private static final String PREFIX = "pypi.";

    @Inject
    @Named("xwikiproperties")
    private ConfigurationSource configuration;

    /**
     * PyPI does not provide any way to know which packages are worth being listed, and listing all of them would mean
     * resolving hundreds of thousands of packages, so only the most downloaded ones are listed to the extension
     * index (when searching without any query).
     *
     * @return the number of most downloaded packages to list, 0 to list all the packages
     */
    public int getPopularPackagesCount()
    {
        return this.configuration.getProperty(PREFIX + "popularPackages.count", DEFAULT_POPULAR_PACKAGES_COUNT);
    }

    /**
     * @return the URL of the list of the most downloaded packages (in the format of
     *         https://hugovk.dev/top-pypi-packages/)
     */
    public String getPopularPackagesURL()
    {
        return this.configuration.getProperty(PREFIX + "popularPackages.url", DEFAULT_POPULAR_PACKAGES_URL);
    }
}
