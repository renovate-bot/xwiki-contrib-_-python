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
package org.xwiki.contrib.python.engine;

import org.graalvm.polyglot.Context;
import org.xwiki.component.annotation.Role;

/**
 * The GraalPy engine shared by everything executing Python 3 code in the instance. Sharing the engine allows the
 * contexts to share the code they parse and compile (like the Python standard library modules), which makes creating
 * a context faster.
 *
 * @version $Id$
 */
@Role
public interface Python3Engine
{
    /**
     * The identifier of the Python language in GraalVM.
     */
    String LANGUAGE = "python";

    /**
     * Create the builder of a new context of the shared engine. The builder holds the configuration which must be the
     * same for all the contexts of the engine (all access is allowed, like for the other script macros), and can be
     * customized with context specific options (output streams, Python options, etc.).
     *
     * @return the builder of a new Python context
     */
    Context.Builder createContextBuilder();
}
