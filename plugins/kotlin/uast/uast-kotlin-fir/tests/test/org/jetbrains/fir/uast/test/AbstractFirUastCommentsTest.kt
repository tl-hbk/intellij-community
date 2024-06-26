// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.fir.uast.test

import org.jetbrains.fir.uast.test.env.kotlin.AbstractFirUastTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.kotlin.UastCommentLogTestBase

abstract class AbstractFirUastCommentsTest : AbstractFirUastTest(), UastCommentLogTestBase {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun check(filePath: String, file: UFile) {
        super.check(filePath, file)
    }

    fun doTest(filePath: String) {
        doCheck(filePath)
    }
}
