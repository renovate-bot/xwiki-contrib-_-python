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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A file of the latest release, as listed by the JSON API page of a project.
 *
 * @version $Id$
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PypiJsonFileDto
{
    private String filename;

    private boolean yanked;

    /**
     * @return the name of the file
     */
    public String getFilename()
    {
        return this.filename;
    }

    /**
     * @param filename the name of the file
     */
    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    /**
     * @return true if the file was yanked
     */
    public boolean isYanked()
    {
        return this.yanked;
    }

    /**
     * @param yanked true if the file was yanked
     */
    public void setYanked(boolean yanked)
    {
        this.yanked = yanked;
    }
}
