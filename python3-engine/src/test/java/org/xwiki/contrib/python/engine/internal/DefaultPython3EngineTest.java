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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validate {@link DefaultPython3Engine}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultPython3EngineTest
{
    @InjectMockComponents
    private DefaultPython3Engine engine;

    @AfterEach
    void afterEach()
    {
        this.engine.dispose();
    }

    @Test
    void createContextBuilder()
    {
        // Contexts with different options can share the engine
        try (Context context1 = this.engine.createContextBuilder().option("python.PythonPath", "/path").build();
            Context context2 = this.engine.createContextBuilder().build()) {
            assertEquals(2, context1.eval("python", "1 + 1").asInt());
            assertEquals(3, context2.eval("python", "1 + 2").asInt());
            assertEquals(context1.getEngine(), context2.getEngine());
        }
    }

    @Test
    void dispose()
    {
        Context context = this.engine.createContextBuilder().build();

        this.engine.dispose();

        // The contexts still open are closed with the engine
        assertThrows(PolyglotException.class, () -> context.eval("python", "1 + 1"));

        // A new engine is created when needed
        try (Context newContext = this.engine.createContextBuilder().build()) {
            assertEquals(2, newContext.eval("python", "1 + 1").asInt());
        }

        // Disposing twice is fine
        this.engine.dispose();
        this.engine.dispose();
    }
}
