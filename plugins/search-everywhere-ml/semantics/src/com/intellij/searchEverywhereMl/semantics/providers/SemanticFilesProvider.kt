package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.services.FileEmbeddingsStorage
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings

class SemanticFilesProvider(private val project: Project) : SemanticPsiItemsProvider() {
  override fun getEmbeddingsStorage() = FileEmbeddingsStorage.getInstance(project)

  override fun isEnabled() = SearchEverywhereSemanticSettings.getInstance().enabledInFilesTab
}