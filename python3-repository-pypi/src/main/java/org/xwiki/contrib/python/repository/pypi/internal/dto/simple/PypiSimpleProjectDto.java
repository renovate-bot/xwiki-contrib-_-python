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
package org.xwiki.contrib.python.repository.pypi.internal.dto.simple;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The JSON form (PEP 691) of the Simple API page of a project ({@code https://pypi.org/simple/<project>/}).
 *
 * @version $Id$
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiSimpleProjectDto
{
    private String name;

    private List<String> versions;

    private List<PypiSimpleFileDto> files;

    /**
     * @return the normalized name of the project
     */
    public String getName()
    {
        return name;
    }

    /**
     * @param name the normalized name of the project
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return all the versions of the project (PEP 700)
     */
    public List<String> getVersions()
    {
        return versions;
    }

    /**
     * @param versions all the versions of the project (PEP 700)
     */
    public void setVersions(List<String> versions)
    {
        this.versions = versions;
    }

    /**
     * @return the distribution files of the project
     */
    public List<PypiSimpleFileDto> getFiles()
    {
        return files;
    }

    /**
     * @param files the distribution files of the project
     */
    public void setFiles(List<PypiSimpleFileDto> files)
    {
        this.files = files;
    }
}
