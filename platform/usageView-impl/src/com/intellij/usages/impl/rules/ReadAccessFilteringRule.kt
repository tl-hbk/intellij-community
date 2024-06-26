// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules

import com.intellij.usages.ReadWriteAccessUsage
import com.intellij.usages.Usage
import com.intellij.usages.UsageTarget
import com.intellij.usages.rules.UsageFilteringRule
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object ReadAccessFilteringRule : UsageFilteringRule {

  override fun getActionId(): String = "UsageFiltering.ReadAccess"

  override fun isVisible(usage: Usage, targets: Array<UsageTarget>): Boolean {
    return usage !is ReadWriteAccessUsage || usage.isAccessedForWriting
  }
}
