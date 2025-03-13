/*
 * Copyright (c) 2025 solonovamax <solonovamax@12oclockpoint.com>
 *
 * The file ConcurrencyUtil.kt is part of reposilite-maven-indexer
 * Last modified on 11-03-2025 12:35 p.m.
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * REPOSILITE-MAVEN-INDEXER IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.solostudios.reposilite.mvnindexer.util

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration


internal fun ScheduledExecutorService.scheduleAtFixedRate(
    initialDelay: Duration,
    period: Duration,
    command: () -> Unit,
): ScheduledFuture<*> {
    return scheduleAtFixedRate(command, initialDelay.inWholeMilliseconds, period.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}

internal fun ScheduledExecutorService.awaitTermination(duration: Duration): Boolean {
    return awaitTermination(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}
