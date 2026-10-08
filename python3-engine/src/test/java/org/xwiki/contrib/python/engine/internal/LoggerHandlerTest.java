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

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Validate {@link LoggerHandler}.
 *
 * @version $Id$
 */
class LoggerHandlerTest
{
    private final Logger logger = mock(Logger.class);

    private final LoggerHandler handler = new LoggerHandler(this.logger);

    private static LogRecord logRecord(Level level, String message)
    {
        LogRecord logRecord = new LogRecord(level, message);
        logRecord.setLoggerName("python");

        return logRecord;
    }

    @Test
    void publish()
    {
        this.handler.publish(logRecord(Level.SEVERE, "severe"));
        this.handler.publish(logRecord(Level.WARNING, "warning"));
        this.handler.publish(logRecord(Level.INFO, "info"));
        this.handler.flush();
        this.handler.close();

        verify(this.logger).warn("[{}] {}", "python", "severe");
        verify(this.logger).warn("[{}] {}", "python", "warning");
        verify(this.logger).debug("[{}] {}", "python", "info");
        verifyNoMoreInteractions(this.logger);
    }
}
