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
package org.xwiki.contrib.python.repository.pypi.internal.dto.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The JSON API page of a project ({@code https://pypi.org/pypi/<project>/json}), describing its latest release.
 * <p>
 * The JSON API is specific to PyPI and its metadata is not reliable enough to resolve dependencies (it comes from one
 * of the files of the release), so it's only used to list the packages found by a search.
 *
 * @version $Id$
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiJsonProjectDto
{
    private PypiJsonInfoDto info;

    private List<PypiJsonFileDto> urls;

    /**
     * @return the metadata of the latest release
     */
    public PypiJsonInfoDto getInfo()
    {
        return this.info;
    }

    /**
     * @param info the metadata of the latest release
     */
    public void setInfo(PypiJsonInfoDto info)
    {
        this.info = info;
    }

    /**
     * @return the files of the latest release
     */
    public List<PypiJsonFileDto> getUrls()
    {
        return this.urls;
    }

    /**
     * @param urls the files of the latest release
     */
    public void setUrls(List<PypiJsonFileDto> urls)
    {
        this.urls = urls;
    }
}
