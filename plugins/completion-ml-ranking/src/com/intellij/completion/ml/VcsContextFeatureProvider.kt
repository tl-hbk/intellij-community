// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.completion.ml

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.openapi.vcs.changes.ChangeListManager

private class VcsContextFeatureProvider : ContextFeatureProvider {
  override fun getName(): String  = "vcs"

  override fun calculateFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue> {
    val project = environment.parameters.position.project
    if (project.isDefault) {
      return emptyMap()
    }
    val changeListManager = ChangeListManager.getInstance(project)
    val changesCount = changeListManager.allChanges.size
    environment.putUserData(changesCountKey, changesCount)
    return mapOf("changes_count" to MLFeatureValue.numerical(changesCount))
  }
}
