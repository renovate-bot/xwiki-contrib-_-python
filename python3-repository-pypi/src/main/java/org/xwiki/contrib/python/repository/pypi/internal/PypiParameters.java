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

/**
 * The constants used to access PyPI.
 *
 * @version $Id$
 */
public interface PypiParameters
{
    /**
     * The placeholder of the package name in the URLs.
     */
    String PACKAGE_NAME_VARIABLE = "{package_name}";

    /**
     * The separator of the URL path segments.
     */
    String PATH_SEPARATOR = "/";

    /**
     * The URL of the Simple API, used as the URL of the repository.
     */
    String API_URL = "https://pypi.org/simple/";

    /**
     * The Simple API index, listing all the packages.
     */
    String PACKAGE_LIST_SIMPLE_API = API_URL;

    /**
     * The Simple API page of a package, listing its distribution files.
     */
    String PACKAGE_SIMPLE_API = API_URL + PACKAGE_NAME_VARIABLE + PATH_SEPARATOR;

    /**
     * The JSON API page of a package, describing its latest release.
     */
    String PACKAGE_JSON_API = "https://pypi.org/pypi/" + PACKAGE_NAME_VARIABLE + "/json";

    /**
     * The page of a project on PyPI.
     */
    String PROJECT_PAGE = "https://pypi.org/project/" + PACKAGE_NAME_VARIABLE + PATH_SEPARATOR;

    /**
     * The media type to ask the Simple API for its JSON form (PEP 691) instead of the default HTML one.
     */
    String SIMPLE_API_JSON_MEDIA_TYPE = "application/vnd.pypi.simple.v1+json";

    /**
     * The identifier of the extension handling the Python packages (the Python 3 API).
     */
    String PYTHON_API_ID = "org.xwiki.contrib.python:python3-api";
}
