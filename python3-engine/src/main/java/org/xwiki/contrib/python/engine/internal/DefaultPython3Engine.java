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
package org.xwiki.contrib.python.engine.internal;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Disposable;
import org.xwiki.contrib.python.engine.Python3Engine;

/**
 * Default implementation of {@link Python3Engine}.
 *
 * @version $Id$
 */
@Component
@Singleton
public class DefaultPython3Engine implements Python3Engine, Disposable
{
    @Inject
    private Logger logger;

    private Engine engine;

    private synchronized Engine getEngine()
    {
        if (this.engine == null) {
            this.engine = Engine.newBuilder(LANGUAGE).option("engine.WarnInterpreterOnly", "false")
                .logHandler(new LoggerHandler(this.logger)).build();
        }

        return this.engine;
    }

    @Override
    public Context.Builder createContextBuilder()
    {
        // The host access configuration must be the same for all the contexts of a shared engine
        return Context.newBuilder(LANGUAGE).engine(getEngine()).allowAllAccess(true);
    }

    @Override
    public synchronized void dispose()
    {
        if (this.engine != null) {
            // Also close the contexts still open (cancelling the ones being executed) since they can't be used anymore
            this.engine.close(true);
            this.engine = null;
        }
    }
}
